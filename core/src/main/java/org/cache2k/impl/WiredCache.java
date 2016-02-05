package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.CacheEntry;
import org.cache2k.CacheEntryCreatedListener;
import org.cache2k.CacheEntryProcessor;
import org.cache2k.CacheEntryRemovedListener;
import org.cache2k.CacheEntryUpdatedListener;
import org.cache2k.CacheManager;
import org.cache2k.CacheSourceWithMetaInfo;
import org.cache2k.CacheWriter;
import org.cache2k.CacheWriterException;
import org.cache2k.ClosableIterator;
import org.cache2k.EntryProcessingResult;
import org.cache2k.FetchCompletedListener;
import org.cache2k.WrappedAttachmentException;
import org.cache2k.experimentalApi.AsyncCacheLoader;
import org.cache2k.experimentalApi.AsyncCacheWriter;
import org.cache2k.impl.operation.ExaminationEntry;
import org.cache2k.impl.operation.Progress;
import org.cache2k.impl.operation.Semantic;
import org.cache2k.impl.operation.Specification;
import org.cache2k.impl.util.Log;
import org.cache2k.storage.StorageCallback;
import org.cache2k.storage.StorageEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Jens Wilke
 */
public class WiredCache<K, V> extends AbstractCache<K, V> implements  StorageAdapter.Parent {

  final Specification<K, V> SPEC = new Specification<K,V>();

  BaseCache<K,V> heapCache;
  StorageAdapter storage;
  CacheSourceWithMetaInfo<K, V> source;
  CacheWriter<K, V> writer;
  boolean readThrough;
  Executor listenerExecutor = new ThreadPoolExecutor(0, Runtime.getRuntime().availableProcessors() * 2,
                                      60L, TimeUnit.SECONDS,
                                      new SynchronousQueue<Runnable>());
  CacheEntryRemovedListener<K,V>[] syncEntryRemovedListeners;
  CacheEntryCreatedListener<K,V>[] syncEntryCreatedListeners;
  CacheEntryUpdatedListener<K,V>[] syncEntryUpdatedListeners;
  CacheEntryRemovedListener<K,V>[] asyncEntryRemovedListeners;
  CacheEntryCreatedListener<K,V>[] asyncEntryCreatedListeners;
  CacheEntryUpdatedListener<K,V>[] asyncEntryUpdatedListeners;

  @Override
  public Future<Void> cancelTimerJobs() {
    return heapCache.cancelTimerJobs();
  }

  @Override
  public Log getLog() {
    return heapCache.getLog();
  }

  /** For testing */
  public BaseCache getHeapCache() {
    return heapCache;
  }

  @Override
  public void resetStorage(final StorageAdapter _current, final StorageAdapter _new) {
    heapCache.resetStorage(_current, _new);
  }

  @Override
  public String getName() {
    return heapCache.getName();
  }

  @Override
  public StorageAdapter getStorage() {
    return heapCache.getStorage();
  }

  @Override
  public Class<?> getKeyType() {
    return heapCache.getKeyType();
  }

  @Override
  public Class<?> getValueType() {
    return heapCache.getValueType();
  }

  @Override
  public CacheManager getCacheManager() {
    return heapCache.getCacheManager();
  }

  @Override
  public V peekAndPut(K key, V value) {
    return returnValue(execute(key, SPEC.peekAndPut(key, value)));
  }

  @Override
  public V peekAndRemove(K key) {
    return returnValue(execute(key, SPEC.peekAndRemove(key)));
  }

  @Override
  public V peekAndReplace(K key, V value) {
    return returnValue(execute(key, SPEC.peekAndReplace(key, value)));
  }

  @Override
  public CacheEntry<K, V> peekEntry(K key) {
    return execute(key, SPEC.peekEntry(key));
  }

  @Override
  public void prefetch(K key) {
  }

  @Override
  public void prefetch(List<? extends K> keys, int _startIndex, int _afterEndIndex) {
  }

  @Override
  public void prefetch(Set<? extends K> keys) {
  }

  @Override
  public boolean contains(K key) {
    return execute(key, SPEC.contains(key));
  }

  @Override
  public boolean putIfAbsent(K key, V value) {
    return execute(key, SPEC.putIfAbsent(key, value));
  }

  @Override
  public void put(K key, V value) {
    execute(key, SPEC.put(key, value));
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
      put(e.getKey(), e.getValue());
    }
  }

  @Override
  public void remove(K key) {
    execute(key, SPEC.remove(key));
  }

  @Override
  public boolean removeIfEquals(K key, V value) {
    return execute(key, SPEC.remove(key, value));
  }

  @Override
  public boolean containsAndRemove(K key) {
    return execute(key, SPEC.containsAndRemove(key));
  }

  @Override
  public boolean replace(K key, V _newValue) {
    return execute(key, SPEC.replace(key, _newValue));
  }

  @Override
  public boolean replaceIfEquals(K key, V _oldValue, V _newValue) {
    return execute(key, SPEC.replace(key, _oldValue, _newValue));
  }

  @Override
  public CacheEntry<K, V> replaceOrGet(K key, V _oldValue, V _newValue, CacheEntry<K, V> _dummyEntry) {
    return execute(key, SPEC.replaceOrGet(key, _oldValue, _newValue, _dummyEntry));
  }

  @Override
  public void fetchAll(Set<? extends K> keys, boolean replaceExistingValues, FetchCompletedListener l) {
    heapCache.fetchAll(keys, replaceExistingValues, l);
  }

  V returnValue(V v) {
    return heapCache.returnValue(v);
  }

  V returnValue(Entry<K,V> e) {
    return returnValue(e.getValueOrException());
  }

  Entry<K, V> lookup(K key) {
    final int hc =  heapCache.modifiedHash(key.hashCode());
    return heapCache.lookupEntryUnsynchronized(key, hc);
  }

  Entry<K, V> lookupNoHit(K key) {
    final int hc =  heapCache.modifiedHash(key.hashCode());
    return heapCache.lookupEntryUnsynchronizedNoHitRecord(key, hc);
  }

  @Override
  public V get(K key) {
    Entry<K, V> e = lookup(key);
    if (e != null && e.hasFreshData()) {
      return returnValue(e);
    }
    return returnValue(execute(key, e, SPEC.get(key)));
   }

  /**
   * We need to deal with possible null values and exceptions. This is
   * a simple placeholder implementation that covers it all by working
   * on the entry.
   */
  @Override
  public Map<K, V> getAll(final Set<? extends K> keys) {
    prefetch(keys);
    Map<K, ExaminationEntry<K, V>> map = new HashMap<K, ExaminationEntry<K, V>>();
    for (K k : keys) {
      ExaminationEntry<K, V> e = execute(k, SPEC.getEntry(k));
      if (e != null) {
        map.put(k, e);
      }
    }
    return convertValueMap(map);
  }

  @Override
  public CacheEntry<K, V> getEntry(K key) {
    return execute(key, SPEC.getEntry(key));
  }

  @Override
  public int getTotalEntryCount() {
    return heapCache.getTotalEntryCount();
  }

  @Override
  public <R> R invoke(K key, CacheEntryProcessor<K, V, R> entryProcessor, Object... _args) {
    return execute(key, SPEC.invoke(key, readThrough, entryProcessor, _args));
  }

  @Override
  public <R> Map<K, EntryProcessingResult<R>> invokeAll(Set<? extends K> keys, CacheEntryProcessor<K, V, R> p, Object... _objs) {
    Map<K, EntryProcessingResult<R>> m = new HashMap<K, EntryProcessingResult<R>>();
    for (K k : keys) {
      try {
        final R _result = invoke(k, p, _objs);
        if (_result == null) {
          continue;
        }
        m.put(k, new EntryProcessingResult<R>() {
          @Override
          public R getResult() {
            return _result;
          }

          @Override
          public Throwable getException() {
            return null;
          }
        });
      } catch (final Throwable t) {
        m.put(k, new EntryProcessingResult<R>() {
          @Override
          public R getResult() {
            return null;
          }

          @Override
          public Throwable getException() {
            return t;
          }
        });
      }
    }
    return m;
  }

  @Override
  public ClosableIterator<CacheEntry<K, V>> iterator() {
    final ClosableIterator<CacheEntry<K, V>> it = heapCache.iterator();
    ClosableIterator<CacheEntry<K, V>> _adapted = new ClosableIterator<CacheEntry<K, V>>() {

      CacheEntry<K, V> entry;

      @Override
      public void close() {
        it.close();
      }

      @Override
      public boolean hasNext() {
        return it.hasNext();
      }

      @Override
      public CacheEntry<K, V> next() {
        return entry = it.next();
      }

      @Override
      public void remove() {
        if (entry == null) {
          throw new IllegalStateException("call next first");
        }
        WiredCache.this.remove(entry.getKey());
      }
    };
    return _adapted;
  }

  @Override
  public V peek(K key) {
    Entry<K, V> e = lookup(key);
    if (e != null && e.hasFreshData()) {
      return returnValue(e);
    }
    return returnValue(execute(key, SPEC.peek(key)));
  }

  /**
   * We need to deal with possible null values and exceptions. This is
   * a simple placeholder implementation that covers it all by working
   * on the entry.
   */
  @Override
  public Map<K, V> peekAll(final Set<? extends K> keys) {
    Map<K, ExaminationEntry<K, V>> map = new HashMap<K, ExaminationEntry<K, V>>();
    for (K k : keys) {
      ExaminationEntry<K, V> e = execute(k, SPEC.peekEntry(k));
      if (e != null) {
        map.put(k, e);
      }
    }
    return convertValueMap(map);
  }

  private Map<K, V> convertValueMap(final Map<K, ExaminationEntry<K, V>> _map) {
    return new MapValueConverterProxy<K, V, ExaminationEntry<K, V>>(_map) {
      @Override
      protected V convert(final ExaminationEntry<K, V> v) {
        return returnValue(v.getValueOrException());
      }
    };
  }

  @Override
  public InternalCacheInfo getLatestInfo() {
    return heapCache.getLatestInfo();
  }

  @Override
  public InternalCacheInfo getInfo() {
    return heapCache.getInfo();
  }

  @Override
  public void clearTimingStatistics() {
    heapCache.clearTimingStatistics();
  }

  @Override
  public void checkIntegrity() {
    heapCache.checkIntegrity();
  }

  @Override
  public void purge() {
    heapCache.purge();
  }

  @Override
  public void flush() {
    heapCache.flush();
  }

  @Override
  public void clear() {
    heapCache.clear();
  }

  @Override
  public void destroy() {
    heapCache.destroy();
  }

  @Override
  public boolean isClosed() {
    return heapCache.isClosed();
  }

  @Override
  public void close() {
    heapCache.close();
  }

  @Override
  public String toString() {
    return heapCache.toString();
  }

  <R> R execute(K key, Entry<K, V> e, Semantic<K, V, R> op) {
    EntryAction<R> _action = new EntryAction<R>(op, key, e);
    op.start(_action);
    if (_action.entryLocked) {
      throw new CacheInternalError("entry not unlocked?");
    }
    WrappedAttachmentException t = _action.exceptionToPropagate;
    if (t != null) {
      t.fillInStackTrace();
      throw t;
    }
    return _action.result;
  }

  <R> R execute(K key, Semantic<K, V, R> op) {
    return execute(key, null, op);
  }

  static final Entry NON_FRESH_DUMMY = new Entry();

  @SuppressWarnings("SynchronizeOnNonFinalField")
  class EntryAction<R> implements StorageCallback, AsyncCacheLoader.Callback<V>, AsyncCacheWriter.Callback, Progress<V, R> {

    K key;
    Semantic<K,V,R> operation;
    Entry<K, V> entry;
    V newValueOrException;
    V oldValueOrException;
    R result;
    long lastModificationTime;
    long loadStartedTime;
    long loadCompletedTime;
    WrappedAttachmentException exceptionToPropagate;
    boolean remove;
    long expiry = 0;
    /** We locked the entry, don't lock it again. */
    boolean entryLocked = false;
    /** True if entry had some data after locking. Expiry is not checked. */
    boolean heapDataValid = false;
    boolean storageDataValid = false;
    boolean needsFinish = true;

    boolean storageRead = false;
    boolean storageMiss = false;

    boolean heapMiss = false;

    boolean wantData = false;
    boolean countMiss = false;
    boolean heapHit = false;
    boolean doNotCountAccess = false;

    boolean loadAndMutate = false;
    boolean load = false;

    /**
     * Fresh load in first round with {@link #loadAndMutate}.
     * Triggers that we always say it is present.
     */
    boolean successfulLoad = false;

    @Override
    public boolean isPresent() {
      doNotCountAccess = true;
      return successfulLoad || entry.hasFreshData();
    }

    @Override
    public boolean isPresentOrMiss() {
      if (successfulLoad || entry.hasFreshData()) {
        return true;
      }
      countMiss = true;
      return false;
    }

    public EntryAction(Semantic<K, V, R> op, K k, Entry<K,V> e) {
      operation = op;
      key = k;
      if (e != null) {
        entry = e;
      } else {
        entry = NON_FRESH_DUMMY;
      }
    }

    @Override
    public void wantData() {
      wantData = true;
      if (storage == null) {
        retrieveDataFromHeap();
        return;
      }
      lockEntryForStorageRead();
    }

    public void retrieveDataFromHeap() {
      Entry<K, V> e = entry;
      if (e == NON_FRESH_DUMMY) {
        e = heapCache.lookupEntrySynchronized(key);
        if (e == null) {
          heapMiss();
          return;
        }
      }
      heapHit(e);
    }

    public void lockEntryForStorageRead() {
      int _spinCount = BaseCache.TUNABLE.maximumEntryLockSpins;
      Entry<K,V> e = entry;
      boolean _needStorageRead = false;
      if (e == NON_FRESH_DUMMY) {
        e = heapCache.lookupOrNewEntrySynchronized(key);
      }
      for (;;) {
        if (_spinCount-- <= 0) {
          throw new CacheLockSpinsExceededError();
        }
        synchronized (e) {
          e.waitForFetch();
          if (!e.isRemovedState()) {
            if (e.isVirgin()) {
              storageRead = _needStorageRead = true;
              e.startFetch(Entry.ProcessingState.READ);
              entryLocked = true;
              heapDataValid = e.isDataValidState();
            }
            break;
          }
        }
        e = heapCache.lookupOrNewEntrySynchronized(key);
      }
      if (_needStorageRead) {
        entry = e;
        storageRead();
        return;
      }
      heapHit(e);
    }

    public void storageRead() {
      StorageEntry se;
      try {
         se = storage.get(entry.key);
      } catch (Throwable ex) {
        onReadFailure(ex);
        return;
      }
      onReadSuccess(se);
    }

    @Override
    public void onReadFailure(Throwable t) {
      examinationAbort(new StorageReadException(t));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onReadSuccess(StorageEntry se) {
      if (se == null) {
        storageReadMiss();
        return;
      }
      storageReadHit(se);
    }

    public void storageReadMiss() {
      Entry<K, V> e = entry;
      e.nextRefreshTime = Entry.READ_NON_VALID;
      storageMiss = true;
      examine();
    }

    public void storageReadHit(StorageEntry se) {
      Entry<K, V> e = entry;
      e.setLastModificationFromStorage(se.getCreatedOrUpdated());
      long now = System.currentTimeMillis();
      V v = (V) se.getValueOrException();
      e.value = v;
      long _nextRefreshTime = heapCache.maxLinger == 0 ? 0 : Long.MAX_VALUE;
      long _expiryTimeFromStorage = se.getValueExpiryTime();
      boolean _expired = _expiryTimeFromStorage != 0 && _expiryTimeFromStorage <= now;
      if (!_expired) {
        _nextRefreshTime = heapCache.calcNextRefreshTime((K) se.getKey(), v, se.getCreatedOrUpdated(), null);
        expiry = _nextRefreshTime;
        if (_nextRefreshTime == -1 || _nextRefreshTime == Long.MAX_VALUE) {
          e.nextRefreshTime = Entry.FETCHED_STATE;
          storageDataValid = true;
        } else if (_nextRefreshTime == 0) {
          e.nextRefreshTime = Entry.READ_NON_VALID;
        } else {
          if (_nextRefreshTime < 0) {
            _nextRefreshTime = -_nextRefreshTime;
          }
          if (_nextRefreshTime <= now) {
            e.nextRefreshTime = Entry.READ_NON_VALID;
          } else {
            e.nextRefreshTime = - _nextRefreshTime;
            storageDataValid = true;
          }
        }
      } else {
        e.nextRefreshTime = Entry.READ_NON_VALID;
      }
      e.nextProcessingStep(Entry.ProcessingState.READ_COMPLETE);
      examine();
    }

    public void heapMiss() {
      heapMiss = true;
      examine();
    }

    public void heapHit(Entry<K, V> e) {
      heapHit = true;
      entry = e;
      examine();
    }

    public void examine() {
      operation.examine(this, entry);
      if (needsFinish) {
        finish();
      }
    }

    @Override
    public void wantMutation() {
      if (!entryLocked && wantData) {
        lockFor(Entry.ProcessingState.MUTATE);
        countMiss = false;
        operation.examine(this, entry);
        if (needsFinish) {
          finish();
        }
        return;
      }
      operation.update(this, entry);
      if (needsFinish) {
        finish();
      }
    }

    public void finish() {
      needsFinish = false;
      noMutationRequested();
    }

    @Override
    public void loadAndMutation() {
      loadAndMutate = true;
      load();
    }

    @Override
    public void load() {
      if (!entry.isVirgin() && !storageRead) {
        synchronized (heapCache.lock) {
          heapCache.loadButHitCnt++;
        }
      }
      if (source == null) {
        exceptionToPropagate = new WrappedAttachmentException(new CacheUsageExcpetion("source not set"));
        synchronized (heapCache.lock) {
          if (entry.isVirgin()) {
            heapCache.loadFailedCnt++;
          }
        }
        return;
      }
      needsFinish = false;
      load = true;
      entry.nextProcessingStep(Entry.ProcessingState.LOAD);
      Entry<K, V> e = entry;
      long t0 = lastModificationTime = loadStartedTime = System.currentTimeMillis();
      V v;
      try {
        if (e.isVirgin() || e.hasException()) {
          v = source.get(e.key, t0, null, e.getLastModification());
        } else {
          v = source.get(e.key, t0, e.getValue(), e.getLastModification());
        }
      } catch (Throwable _ouch) {
        onLoadFailure(_ouch);
        return;
      }
      onLoadSuccess(v);
    }

    void lockFor(Entry.ProcessingState ps) {
      if (entryLocked) {
        entry.nextProcessingStep(ps);
        return;
      }
      Entry<K, V> e = entry;
      if (e == NON_FRESH_DUMMY) {
        e = heapCache.lookupOrNewEntrySynchronized(key);
      }
      int _spinCount = BaseCache.TUNABLE.maximumEntryLockSpins;
      for (;;) {
        if (_spinCount-- <= 0) {
          throw new CacheLockSpinsExceededError();
        }
        synchronized (e) {
          e.waitForFetch();
          if (!e.isRemovedState()) {
            e.startFetch(ps);
            entryLocked = true;
            heapDataValid = e.isDataValidState();
            heapHit = !e.isVirgin();
            entry = e;
            return;
          }
        }
        e = heapCache.lookupOrNewEntrySynchronized(key);
      }
    }

    void lockForNoHit(Entry.ProcessingState ps) {
      if (entryLocked) {
        entry.nextProcessingStep(ps);
        return;
      }
      Entry<K, V> e = entry;
      if (e == NON_FRESH_DUMMY) {
        e = heapCache.lookupOrNewEntrySynchronizedNoHitRecord(key);
      }
      int _spinCount = BaseCache.TUNABLE.maximumEntryLockSpins;
      for (;;) {
        if (_spinCount-- <= 0) {
          throw new CacheLockSpinsExceededError();
        }
        synchronized (e) {
          e.waitForFetch();
          if (!e.isRemovedState()) {
            e.startFetch(ps);
            entryLocked = true;
            heapDataValid = e.isDataValidState();
            heapHit = !e.isVirgin();
            entry = e;
            return;
          }
        }
        e = heapCache.lookupOrNewEntrySynchronizedNoHitRecord(key);
      }
    }

    @Override
    public void onLoadSuccess(V value) {
      newValueOrException = value;
      loadCompleted();
    }

    @Override
    public void onLoadFailure(Throwable t) {
      synchronized (heapCache.lock) {
        heapCache.loadExceptionCnt++;
      }
      newValueOrException = (V) new ExceptionWrapper(t);
      loadCompleted();
    }

    public void loadCompleted() {
      entry.nextProcessingStep(Entry.ProcessingState.LOAD_COMPLETE);
      loadCompletedTime = System.currentTimeMillis();
      mutationCalculateExpiry();
    }

    @Override
    public void result(R r) {
      result = r;
    }

    @Override
    public void put(V value) {
      lockFor(Entry.ProcessingState.MUTATE);
      needsFinish = false;
      newValueOrException = value;
      lastModificationTime = System.currentTimeMillis();
      mutationCalculateExpiry();
    }

    @Override
    public void remove() {
      lockForNoHit(Entry.ProcessingState.MUTATE);
      needsFinish = false;
      remove = true;
      lastModificationTime = System.currentTimeMillis();
      mutationMayCallWriter();
    }

    public void mutationCalculateExpiry() {
      try {
        entry.nextProcessingStep(Entry.ProcessingState.EXPIRY);
        if (heapCache.timer != null) {
          expiry = heapCache.calculateNextRefreshTime(
            entry, newValueOrException,
            lastModificationTime, entry.nextRefreshTime);
        } else {
          expiry = heapCache.maxLinger == 0 ? 0 : Long.MAX_VALUE;
        }
      } catch (Exception ex) {
        expiryCalculationException(ex);
        return;
      }
      expiryCalculated();
    }

    public void expiryCalculationException(Throwable t) {
      mutationAbort(new ExpiryCalculationException(t));
    }

    public void expiryCalculated() {
      entry.nextProcessingStep(Entry.ProcessingState.EXPIRY_COMPLETE);
      if (load) {
        boolean _suppressException = heapCache.needsSuppress(entry, newValueOrException);
        if (_suppressException) {
          newValueOrException = entry.getValue();
          lastModificationTime = entry.getLastModification();
          synchronized (heapCache.lock) {
            heapCache.suppressedExceptionCnt++;
          }
        } else {
          if (newValueOrException instanceof ExceptionWrapper) {
            ExceptionWrapper ew = (ExceptionWrapper) newValueOrException;
            if (expiry < 0) {
              ew.until = -expiry;
            } else if (expiry > Entry.EXPIRY_TIME_MIN && expiry != Long.MAX_VALUE){
              ew.until = expiry;
            }
          }
        }
        if (loadAndMutate) {
          loadAndExpiryCalculatedMutateAgain();
          return;
        }
        checkKeepOrRemove();
        return;
      } else {
        if (entry.isVirgin()) {
          metrics().putNewEntry();
        } else {
          if (!wantData) {
            metrics().putNoReadHit();
          } else {
            if (expiry > 0) {
              metrics().putHit();
            }
          }
        }
      }
      mutationMayCallWriter();
    }

    public void loadAndExpiryCalculatedMutateAgain() {
      load = loadAndMutate = false;
      needsFinish = successfulLoad = true;
      ExaminationEntry ee = new ExaminationEntry() {
        @Override
        public Object getKey() {
          return entry.getKey();
        }

        @Override
        public Object getValueOrException() {
          return newValueOrException;
        }

        @Override
        public long getLastModification() {
          return lastModificationTime;
        }
      };
      operation.update(this, ee);
      if (needsFinish) {
        updateDidNotTriggerDifferentMutationStoreLoadedValue();
      }
    }

    public void updateDidNotTriggerDifferentMutationStoreLoadedValue() {
      checkKeepOrRemove();
    }

    /**
     * Entry mutation, call writer if needed or skip to {@link #mutationMayStore()}
     */
    public void mutationMayCallWriter() {
      if (writer == null) {
        skipWritingNoWriter();
        return;
      }
      if (remove) {
        try {
          entry.nextProcessingStep(Entry.ProcessingState.WRITE);
          writer.delete(key);
        } catch (Throwable t) {
          onWriteFailure(t);
          return;
        }
        onWriteSuccess();
        return;
      }
      if (newValueOrException instanceof ExceptionWrapper) {
        skipWritingForException();
        return;
      }
      entry.nextProcessingStep(Entry.ProcessingState.WRITE);
      try {
        writer.write(key, newValueOrException);
      } catch (Throwable t) {
        onWriteFailure(t);
        return;
      }
      onWriteSuccess();
    }

    @Override
    public void onWriteSuccess() {
      entry.nextProcessingStep(Entry.ProcessingState.WRITE_COMPLETE);
      checkKeepOrRemove();
    }

    @Override
    public void onWriteFailure(Throwable t) {
      mutationAbort(new CacheWriterException(t));
    }

    public void skipWritingForException() {
      checkKeepOrRemove();
    }

    public void skipWritingNoWriter() {
      checkKeepOrRemove();
    }

    /**
     * In case we have a expiry of 0, this means that the entry should
     *  not be cached. If there is a valid entry, we remove it if we do not
     *  keep the data.
     */
    public void checkKeepOrRemove() {
      boolean _hasKeepAfterExpired = heapCache.hasKeepAfterExpired();
      if (expiry != 0 || remove || _hasKeepAfterExpired) {
        mutationUpdateHeap();
        return;
      }
      if (entry.isDataValidState()) {
        expiredImmediatelyDoRemove();
        return;
      }
      expiredImmediatelySkipMutation();
    }

    public void expiredImmediatelySkipMutation() {
      synchronized (heapCache.lock) {
        heapCache.putButExpiredCnt++;
      }
      noMutationRequested();
    }


    public void expiredImmediatelyDoRemove() {
      remove = true;
      mutationUpdateHeap();
    }

    public void mutationUpdateHeap() {
      entry.setLastModification(lastModificationTime);
      if (remove) {
        entry.nextRefreshTime = Entry.REMOVE_PENDING;
      } else {
        oldValueOrException = entry.value;
        entry.value = newValueOrException;
      }
      mutationMayStore();
    }

    /**
     * Entry mutation, call storage if needed
     */
    public void mutationMayStore() {
      if (storage == null) {
        skipStore();
        return;
      }
      mutationStore();
    }

    public void mutationStore() {
      entry.nextProcessingStep(Entry.ProcessingState.STORE);
      boolean _entryRemoved = false;
      try {
        if (remove) {
          _entryRemoved = storage.remove(key);
        } else {
          storage.put(entry, expiry);
        }
      } catch (Throwable t) {
        onStoreFailure(t);
        return;
      }
      onStoreSuccess(_entryRemoved);
    }

    @Override
    public void onStoreSuccess(boolean _entryRemoved) {
      entry.nextProcessingStep(Entry.ProcessingState.STORE_COMPLETE);
      callListeners();
    }

    @Override
    public void onStoreFailure(Throwable t) {
      mutationAbort(new StorageWriteException(t));
    }

    public void skipStore() {
      callListeners();
    }

    public void callListeners() {
      if (remove) {
        if (storageDataValid || heapDataValid) {
          if (syncEntryRemovedListeners != null) {
            for (CacheEntryRemovedListener l : syncEntryRemovedListeners) {
              try {
                l.onEntryRemoved(WiredCache.this, entry);
              } catch (Throwable t) {
                exceptionToPropagate = new ListenerException(t);
              }
            }
          }
          if (asyncEntryRemovedListeners != null) {
            final CacheEntry e = Specification.returnStableEntry(entry);
            for (final CacheEntryRemovedListener l : asyncEntryRemovedListeners) {
              try {
                listenerExecutor.execute(new Runnable() {
                  @Override
                  public void run() {
                    l.onEntryRemoved(WiredCache.this, e);
                  }
                });
              } catch (Throwable t) {
                exceptionToPropagate = new ListenerException(t);
              }
            }
          }
        }
      } else {
        if (storageDataValid || heapDataValid) {
          if (syncEntryUpdatedListeners != null) {
            for (CacheEntryUpdatedListener l : syncEntryUpdatedListeners) {
              try {
                l.onEntryUpdated(WiredCache.this, oldValueOrException, entry);
              } catch (Throwable t) {
                exceptionToPropagate = new ListenerException(t);
              }
            }
          }
          if (asyncEntryUpdatedListeners != null) {
            final CacheEntry e = Specification.returnStableEntry(entry);
            for (final CacheEntryUpdatedListener l : asyncEntryUpdatedListeners) {
              try {
                listenerExecutor.execute(new Runnable() {
                  @Override
                  public void run() {
                    l.onEntryUpdated(WiredCache.this, oldValueOrException, e);
                  }
                });
              } catch (Throwable t) {
                exceptionToPropagate = new ListenerException(t);
              }
            }
          }
        } else {
          if (syncEntryCreatedListeners != null) {
            for (CacheEntryCreatedListener l : syncEntryCreatedListeners) {
              try {
                l.onEntryCreated(WiredCache.this, entry);
              } catch (Throwable t) {
                exceptionToPropagate = new ListenerException(t);
              }
            }
          }
          if (asyncEntryCreatedListeners != null) {
            final CacheEntry e = Specification.returnStableEntry(entry);
            for (final CacheEntryCreatedListener l : syncEntryCreatedListeners) {
              try {
                listenerExecutor.execute(new Runnable() {
                  @Override
                  public void run() {
                    l.onEntryCreated(WiredCache.this, e);
                  }
                });
              } catch (Throwable t) {
                exceptionToPropagate = new ListenerException(t);
              }
            }
          }
        }
      }
      mutationReleaseLock();
    }

    public void mutationReleaseLock() {
      if (load) {
        operation.loaded(this, entry);
      }
      synchronized (entry) {
        entry.processingDone();
        entryLocked = false;
        entry.notifyAll();
        if (remove) {
          synchronized (heapCache.lock) {
            if (heapCache.removeEntry(entry)) {
              heapCache.removedCnt++;
            }
          }
        } else {
          long _nextRefreshTime = expiry;
          if (_nextRefreshTime == 0) {
            entry.nextRefreshTime = org.cache2k.impl.Entry.FETCH_NEXT_TIME_STATE;
            if (entry.task != null) {
              entry.task.cancel();
            }
          } else if (_nextRefreshTime == Long.MAX_VALUE) {
            entry.nextRefreshTime = org.cache2k.impl.Entry.FETCHED_STATE;
            if (entry.task != null) {
              entry.task.cancel();
            }
          } else {
            entry.nextRefreshTime =
              heapCache.stopStartTimer(expiry, entry, System.currentTimeMillis());
          }
        }
      }
      synchronized (heapCache.lock) {
        updateMutationStatisticsInsideLock();
      }
      mutationDone();
    }

    public void updateOnlyReadStatisticsInsideLock() {
      if (countMiss) {
        if (heapHit) {
          heapCache.peekHitNotFreshCnt++;
        }
        if (heapMiss) {
          heapCache.peekMissCnt++;
        }
        if (storageRead && storageMiss) {
          heapCache.readNonFreshCnt++;
          heapCache.peekHitNotFreshCnt++;
        }
      } else if (doNotCountAccess && heapHit) {
        metrics().containsButHit();
      }
      if (storageRead && !storageMiss) {
        heapCache.readHitCnt++;
      }
    }

    public void updateMutationStatisticsInsideLock() {
      if (loadCompletedTime > 0) {
        heapCache.loadCnt++;
        heapCache.fetchMillis += loadCompletedTime - loadStartedTime;
        if (storageRead && storageMiss) {
          heapCache.readMissCnt++;
        }
      } else {
        updateOnlyReadStatisticsInsideLock();
        if (wantData) {
          metrics().casOperation();
        }
        if (remove) {
          if (heapDataValid) {
            metrics().remove();
          } else if (heapHit) {
          }
        }
      }
    }

    /**
     * Failure call on Progress from Semantic.
     */
    public void failure(Throwable t) {
      mutationAbort(new ProcessingFailureException(t));
    }

    public void examinationAbort(WrappedAttachmentException t) {
      exceptionToPropagate = t;
      if (entryLocked) {
        synchronized (entry) {
          entry.processingDone();
          entry.notifyAll();
          entryLocked = false;
        }
      }
      ready();
    }

    public void mutationAbort(WrappedAttachmentException t) {
      exceptionToPropagate = t;
      synchronized (entry) {
        entry.processingDone();
        entry.notifyAll();
        entryLocked = false;
      }
      ready();
    }

    /**
     *
     */
    public void mutationDone() {
      evictEventuallyAfterMutation();
    }

    /**
     * Standard behavior specifies, we always check whether something is to
     * evict after each cache operation that may have created a new entry.
     */
    public void evictEventuallyAfterMutation() {
      heapCache.evictEventually();
      ready();
    }

    public void noMutationRequested() {
      if (entryLocked) {
        synchronized (entry) {
          if (entry.isVirgin()) {
            entry.nextRefreshTime = Entry.FETCH_ABORT;
          }
          entry.processingDone();
          entry.notifyAll();
        }
        entryLocked = false;
      }
      synchronized (heapCache.lock) {
        updateOnlyReadStatisticsInsideLock();
      }
      if (!heapHit) {
        evictEventuallyAfterExamine();
        return;
      }
      ready();
    }

    public void evictEventuallyAfterExamine() {
      heapCache.evictEventually();
      ready();
    }

    public void ready() {
    }

    protected CommonMetrics.Updater metrics() {
      return heapCache.metrics;
    }

  }

  static class StorageReadException extends WrappedAttachmentException {
    public StorageReadException(final Throwable cause) {
      super(cause);
    }
  }
  static class StorageWriteException extends WrappedAttachmentException {
    public StorageWriteException(final Throwable cause) {
      super(cause);
    }
  }
  static class ExpiryCalculationException extends WrappedAttachmentException {
    public ExpiryCalculationException(final Throwable cause) {
      super(cause);
    }
  }
  static class ProcessingFailureException extends WrappedAttachmentException {
    public ProcessingFailureException(final Throwable cause) {
      super(cause);
    }
  }
  public static class ListenerException extends WrappedAttachmentException {
    public ListenerException(final Throwable cause) {
      super(cause);
    }
  }
}
