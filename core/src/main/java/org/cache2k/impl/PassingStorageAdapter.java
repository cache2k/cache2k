package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2014 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import org.apache.commons.logging.Log;
import org.cache2k.CacheConfig;
import org.cache2k.StorageConfiguration;
import org.cache2k.impl.timer.TimerPayloadListener;
import org.cache2k.impl.timer.TimerService;
import org.cache2k.storage.CacheStorage;
import org.cache2k.storage.CacheStorageContext;
import org.cache2k.storage.ImageFileStorage;
import org.cache2k.storage.MarshallerFactory;
import org.cache2k.storage.Marshallers;
import org.cache2k.storage.StorageEntry;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Passes cache operation to the storage layer. Implements common
 * services for the storage layer, just as timing.
 *
 * @author Jens Wilke; created: 2014-05-08
 */
@SuppressWarnings({"unchecked", "SynchronizeOnNonFinalField"})
class PassingStorageAdapter extends StorageAdapter {

  private BaseCache cache;
  CacheStorage storage;
  CacheStorage copyForClearing;
  boolean passivation = false;
  long storageErrorCount = 0;
  Set<Object> deletedKeys = null;
  StorageContext context;
  StorageConfiguration config;
  ExecutorService executor = Executors.newCachedThreadPool();
  TimerService.CancelHandle flushTimerHandle;
  boolean needsFlush;
  Future<Void> executingFlush;

  public PassingStorageAdapter(BaseCache _cache, CacheConfig _cacheConfig,
                               StorageConfiguration _storageConfig) {
    cache = _cache;
    context = new StorageContext(_cache);
    context.keyType = _cacheConfig.getKeyType();
    context.valueType = _cacheConfig.getValueType();
    config = _storageConfig;
  }

  public void open() {
   try {
      ImageFileStorage s = new ImageFileStorage();
      s.open(context, config);
      storage = s;
      if (config.isPassivation()) {
        deletedKeys = new HashSet<>();
        passivation = true;
      }
     cache.getLog().info("open " + storage);
    } catch (Exception ex) {
      cache.getLog().warn("error initializing storage, running in-memory", ex);
    }
  }

  /**
   * Store entry on cache put. Entry must be locked, since we use the
   * entry directly for handing it over to the storage, it is not
   * allowed to change. If storeAlways is switched on the entry will
   * be in memory and in the storage after this operation.
   */
  public void put(BaseCache.Entry e) {
    if (deletedKeys != null) {
      synchronized (deletedKeys) {
        deletedKeys.remove(e.getKey());
      }
      return;
    }
    try {
      storage.put(e);
      checkStartFlushTimer();
    } catch (Exception ex) {
      storageErrorCount++;
      throw new CacheStorageException("cache put", ex);
    }
  }

  void checkStartFlushTimer() {
    needsFlush = true;
    if (config.getSyncInterval() <= 0) {
      return;
    }
    if (flushTimerHandle != null) {
      return;
    }
    synchronized (this) {
      if (flushTimerHandle != null) {
        return;
      }
      scheduleTimer();
    }
  }

  private void scheduleTimer() {
    if (flushTimerHandle != null) {
      flushTimerHandle.cancel();
    }
    TimerPayloadListener<Void> l = new TimerPayloadListener<Void>() {
      @Override
      public void fire(Void _payload, long _time) {
        flush();
      }
    };
    long _fireTime = System.currentTimeMillis() + config.getSyncInterval();
    flushTimerHandle = cache.timerService.add(l, null, _fireTime);
  }

  public StorageEntry get(Object k) {
    if (deletedKeys != null) {
      synchronized (deletedKeys) {
        if (deletedKeys.contains(k)) {
          return null;
        }
      }
    }
    try {
      return storage.get(k);
    } catch (Exception ex) {
      storageErrorCount++;
      throw new CacheStorageException("cache get", ex);
    }
  }

  public void evict(BaseCache.Entry e) {
    if (passivation) {
      putIfDirty(e);
    }
  }

  /**
   * Entry is evicted from memory cache either because of an expiry or an
   * eviction.
   */
  public void expire(BaseCache.Entry e) {
    remove(e.getKey());
  }

  private void putIfDirty(BaseCache.Entry e) {
    try {
      if (e.isDirty()) {
        storage.put(e);
        checkStartFlushTimer();
      }
    } catch (Exception ex) {
      storageErrorCount++;
      throw new CacheStorageException("cache put", ex);
    }
  }

  public void remove(Object key) {
    if (deletedKeys != null) {
      synchronized (deletedKeys) {
        deletedKeys.remove(key);
      }
      return;
    }
    try {
      storage.remove(key);
      checkStartFlushTimer();
    } catch (Exception ex) {
      storageErrorCount++;
      throw new CacheStorageException("cache remove", ex);
    }
  }

  @Override
  public Iterator<BaseCache.Entry> iterateAll() {
    final CompleteIterator it = new CompleteIterator();
    synchronized (cache.lock) {
      it.localIteration = cache.iterateAllLocalEntries();
      if (!passivation) {
        it.totalEntryCount = storage.getEntryCount();
      } else {
        it.totalEntryCount = -1;
      }

    }
    it.executor = executor;
    it.runnable = new Runnable() {
      @Override
      public void run() {
        final BlockingDeque<BaseCache.Entry> _queue = it.queue;
        CacheStorage.EntryVisitor v = new CacheStorage.EntryVisitor() {
          @Override
          public void visit(StorageEntry se) {
            BaseCache.Entry e = cache.insertEntryFromStorage(se, true);
            _queue.addFirst(e);
          }
        };
        CacheStorage.EntryFilter f = new CacheStorage.EntryFilter() {
          @Override
          public boolean shouldInclude(Object _key) {
            boolean b = !it.keysIterated.contains(_key);
            System.err.println("shouldInclude: " + _key + " -> " +b);
            return b;
          }
        };
        final CacheStorage.VisitContext ctx = new CacheStorage.VisitContext() {
          @Override
          public boolean needMetaData() {
            return true;
          }

          @Override
          public boolean needValue() {
            return true;
          }

          @Override
          public ExecutorService getExecutorService() {
            return createOperationExecutor();
          }

          @Override
          public boolean shouldStop() {
            return false;
          }
        };
        try {
          storage.visit(v, f, ctx);
        } catch (Exception ex) {
          ex.printStackTrace();
        }
        _queue.addFirst(LAST_ENTRY);
      }
    };
    return it;
  }

  static final BaseCache.Entry LAST_ENTRY = new BaseCache.Entry();

  static class CompleteIterator implements Iterator<BaseCache.Entry> {

    HashSet<Object> keysIterated = new HashSet<>();
    Iterator<BaseCache.Entry> localIteration;
    int totalEntryCount;
    BaseCache.Entry entry;
    BlockingDeque<BaseCache.Entry> queue = new LinkedBlockingDeque<>();
    Runnable runnable;
    ExecutorService executor;

    @Override
    public boolean hasNext() {
      if (localIteration != null) {
        boolean b = localIteration.hasNext();
        if (b) {
          entry = localIteration.next();
          keysIterated.add(entry.getKey());
          return true;
        }
        localIteration = null;
        if (keysIterated.size() >= totalEntryCount) {
          queue = null;
        } else {
          executor.submit(runnable);
        }
      }
      if (queue != null) {
        try {
          entry = queue.takeFirst();
          System.err.println(entry);
          if (entry != LAST_ENTRY) {
            return true;
          }
        } catch (InterruptedException ex) {
        }
        queue = null;
      }
      return false;
    }

    @Override
    public BaseCache.Entry next() {
      return entry;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  public Future<Void> flush() {
    synchronized (this) {
      final Future<Void> _previousFlush = executingFlush;
        Callable<Void> c=  new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            if (_previousFlush != null) {
              _previousFlush.get();
            }
            storage.flush(System.currentTimeMillis(), CacheStorage.DEFAULT_FLUSH_CONTEXT);
            getLog().info("flush " + storage);
            executingFlush = null;
            synchronized (this) {
              if (needsFlush) {
                scheduleTimer();
              } else {
                if (flushTimerHandle != null) {
                  flushTimerHandle.cancel();
                  flushTimerHandle = null;
                }
              }
            }
            return null;
          }
        };

      return executingFlush = executor.submit(c);
    }
  }

  private Log getLog() {
    return cache.getLog();
  }

  public void shutdown() {
    if (storage == null) {
      return;
    }
    try {
      if (passivation) {
        Iterator<BaseCache.Entry> it;
        synchronized (cache.lock) {
          it = cache.iterateAllLocalEntries();
        }
        while (it.hasNext()) {
          BaseCache.Entry e = it.next();
          putIfDirty(e);
        }
        if (deletedKeys != null) {
          for (Object k : deletedKeys) {
            storage.remove(k);
          }
        }
      }
      synchronized (this) {
        final CacheStorage _storage = storage;
        storage = null;
        final Future<Void> _previousFlush = executingFlush;
        Callable<Void> c=  new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            if (_previousFlush != null) {
              try {
                _previousFlush.cancel(true);
                _previousFlush.get();
              } catch (Exception ex) {
              }
            }
            _storage.flush(System.currentTimeMillis(), CacheStorage.DEFAULT_FLUSH_CONTEXT);
            getLog().info("close " + storage);
            _storage.close();
            return null;
          }
        };
        Future<Void> f = executor.submit(c);
        f.get();
      }
    } catch (Exception ex) {
      storageErrorCount++;
    }
  }

  public boolean clearPrepare() {
    if (copyForClearing != null) {
      return false;
    }
    copyForClearing = storage;
    storage = new CacheStorageBuffer();
    return true;
  }

  public void clearProceed() {
    try {
      copyForClearing.clear();
      ((CacheStorageBuffer) storage).transfer(copyForClearing);
    } catch (Exception ex) {
      ex.printStackTrace();
      storageErrorCount++;
    } finally {
      synchronized (cache.lock) {
        storage = copyForClearing;
        copyForClearing = null;
      }
    }
  }

  /**
   * Calculates the cache size, depending on the persistence configuration
   */
  @Override
  public int getTotalEntryCount() {
    if (!passivation) {
      return storage.getEntryCount();
    }
    return storage.getEntryCount() + cache.getLocalSize();
  }

  static class StorageContext implements CacheStorageContext {

    BaseCache cache;
    Class<?> keyType;
    Class<?> valueType;

    StorageContext(BaseCache cache) {
      this.cache = cache;
    }

    @Override
    public String getManagerName() {
      return cache.manager.getName();
    }

    @Override
    public String getCacheName() {
      return cache.getName();
    }

    @Override
    public Class<?> getKeyType() {
      return keyType;
    }

    @Override
    public Class<?> getValueType() {
      return valueType;
    }

    @Override
    public MarshallerFactory getMarshallerFactory() {
      return Marshallers.getInstance();
    }

    @Override
    public void requestMaintenanceCall(int _intervalMillis) {
    }

    @Override
    public void notifyEvicted(StorageEntry e) {
    }

    @Override
    public void notifyExpired(StorageEntry e) {
    }

  }

  ExecutorService createOperationExecutor() {
    return
      new ThreadPoolExecutor(0, Runtime.getRuntime().availableProcessors() * 123 / 100,
        21, TimeUnit.SECONDS,
        new SynchronousQueue<Runnable>(),
        THREAD_FACTORY,
        new ThreadPoolExecutor.AbortPolicy());
  }

  static final ThreadFactory THREAD_FACTORY = new MyThreadFactory();

  @SuppressWarnings("NullableProblems")
  static class MyThreadFactory implements ThreadFactory {

    AtomicInteger count = new AtomicInteger();

    @Override
    public synchronized Thread newThread(Runnable r) {
      Thread t = new Thread(r, "cache2k-storage#" + count.incrementAndGet());
      t.setDaemon(true);
      return t;
    }

  }

}
