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

import org.cache2k.AdvancedCacheLoader;
import org.cache2k.CacheEntry;
import org.cache2k.CacheEntryCreatedListener;
import org.cache2k.CacheEntryProcessor;
import org.cache2k.CacheEntryRemovedListener;
import org.cache2k.CacheEntryUpdatedListener;
import org.cache2k.CacheManager;
import org.cache2k.CacheWriter;
import org.cache2k.ClosableIterator;
import org.cache2k.FetchCompletedListener;
import org.cache2k.impl.operation.ExaminationEntry;
import org.cache2k.impl.operation.Progress;
import org.cache2k.impl.operation.Semantic;
import org.cache2k.impl.operation.Specification;
import org.cache2k.impl.threading.Futures;
import org.cache2k.impl.threading.LimitedPooledExecutor;
import org.cache2k.impl.util.Log;
import org.cache2k.storage.StorageEntry;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * A cache implementation that builds on a heap cache and coordinates with additional
 * attachments like storage, listeners and a writer.
 *
 * @author Jens Wilke
 */
public class WiredCache<K, V> extends AbstractCache<K, V>
  implements  StorageAdapter.Parent, HeapCacheListener<K,V> {

  final Specification<K, V> SPEC = new Specification<K,V>();

  BaseCache<K,V> heapCache;
  StorageAdapter storage;
  AdvancedCacheLoader<K,V> loader;
  CacheWriter<K, V> writer;
  boolean readThrough;
  CacheEntryRemovedListener<K,V>[] syncEntryRemovedListeners;
  CacheEntryCreatedListener<K,V>[] syncEntryCreatedListeners;
  CacheEntryUpdatedListener<K,V>[] syncEntryUpdatedListeners;

  @Override
  public Log getLog() {
    return heapCache.getLog();
  }

  /** For testing */
  public BaseCache getHeapCache() {
    return heapCache;
  }

  @Override
  public String getName() {
    return heapCache.getName();
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
  public void prefetch(Iterable<? extends K> keys) {
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
  public Map<K, V> getAll(final Iterable<? extends K> keys) {
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
    synchronized (lockObject()) {
      if (storage != null) {
        return storage.getTotalEntryCount();
      }
      return heapCache.getLocalSize();
    }
  }

  @Override
  public <R> R invoke(K key, CacheEntryProcessor<K, V, R> entryProcessor, Object... args) {
    return execute(key, SPEC.invoke(key, readThrough, entryProcessor, args));
  }

  @Override
  public ClosableIterator<CacheEntry<K, V>> iterator() {
    ClosableIterator<CacheEntry<K, V>> tor;
    if (storage == null) {
      synchronized (lockObject()) {
         tor = new BaseCache.IteratorFilterEntry2Entry(heapCache, (ClosableIterator<Entry>) heapCache.iterateAllHeapEntries(), true);
      }
    } else {
      tor = new BaseCache.IteratorFilterEntry2Entry(heapCache, (ClosableIterator<Entry>) storage.iterateAll(), false);
    }
    final ClosableIterator<CacheEntry<K, V>> it = tor;
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
    return heapCache.convertValueMap(_map);
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
  public void destroy() {
    close();
  }

  @Override
  public boolean isClosed() {
    return heapCache.isClosed();
  }

  @Override
  public String toString() {
    return heapCache.toString();
  }

  public void init() {
    if (storage == null && heapCache.maxSize == 0) {
      throw new IllegalArgumentException("maxElements must be >0");
    }
    if (storage != null) {
      storage.open();
    }
    heapCache.init();
  }

  @Override
  public Future<Void> cancelTimerJobs() {
    synchronized (lockObject()) {
      Future<Void> _waitFuture = heapCache.cancelTimerJobs();
      if (storage != null) {
        Future<Void> f = storage.cancelTimerJobs();
        if (f != null) {
          _waitFuture = new Futures.WaitForAllFuture(_waitFuture, f);
        }
      }
      return _waitFuture;
    }
  }

  @Override
  public void purge() {
    if (storage != null) {
      storage.purge();
    }
  }

  @Override
  public void flush() {
    if (storage != null) {
      storage.flush();
    }
  }

  @Override
  public void clear() {
    if (storage != null) {
      processClearWithStorage();
      return;
    }
    heapCache.clear();
  }

  @Override
  public void close() {
    heapCache.closePart1();
    Future<Void> _waitForStorage = null;
    if (storage != null) {
      _waitForStorage = storage.shutdown();
    }
    if (_waitForStorage != null) {
      try {
        _waitForStorage.get();
      } catch (Exception ex) {
        StorageAdapter.rethrow("shutdown", ex);
      }
    }
    synchronized (lockObject()) {
      storage = null;
    }
    heapCache.closePart2();
  }

  /**
   * Stops creation of new entries when clear is ongoing.
   */
  protected boolean waitForClear = false;

  private Object lockObject() {
    return heapCache.lock;
  }

  @Override
  public void resetStorage(StorageAdapter _from, StorageAdapter to) {
    synchronized (lockObject()) {
      storage = to;
    }
  }

  @Override
  public StorageAdapter getStorage() {
    return storage;
  }

  /**
   * Clear may be called during operation, e.g. to reset all the cache content. We must make sure
   * that there is no ongoing operation when we send the clear to the storage. That is because the
   * storage implementation has a guarantee that there is only one storage operation ongoing for
   * one entry or key at any time. Clear, of course, affects all entries.
   */
  private void processClearWithStorage() {
    StorageClearTask t = new StorageClearTask();
    boolean _untouchedHeapCache;
    synchronized (lockObject()) {
      heapCache.checkClosed();
      waitForClear = true;
      _untouchedHeapCache = heapCache.touchedTime ==
        heapCache.clearedTime && heapCache.getLocalSize() == 0;
      if (!storage.checkStorageStillDisconnectedForClear()) {
        t.allLocalEntries = heapCache.iterateAllHeapEntries();
        t.allLocalEntries.setStopOnClear(false);
      }
      t.storage = storage;
      t.storage.disconnectStorageForClear();
    }
    try {
      if (_untouchedHeapCache) {
        FutureTask<Void> f = new FutureTask<Void>(t);
        heapCache.updateShutdownWaitFuture(f);
        f.run();
      } else {
        heapCache.updateShutdownWaitFuture(heapCache.manager.getThreadPool().execute(t));
      }
    } catch (Exception ex) {
      throw new CacheStorageException(ex);
    }
    synchronized (lockObject()) {
      heapCache.checkClosed();
      heapCache.clearLocalCache();
      waitForClear = false;
      lockObject().notifyAll();
    }
  }

  class StorageClearTask implements LimitedPooledExecutor.NeverRunInCallingTask<Void> {

    ClosableConcurrentHashEntryIterator<org.cache2k.impl.Entry> allLocalEntries;
    StorageAdapter storage;

    @Override
    public Void call() {
      try {
        if (allLocalEntries != null) {
          waitForEntryOperations();
        }
        storage.clearAndReconnect();
        storage = null;
        return null;
      } catch (Throwable t) {
        if (allLocalEntries != null) {
          allLocalEntries.close();
        }
        getLog().warn("clear exception, when signalling storage", t);
        storage.disable(t);
        throw new CacheStorageException(t);
      }
    }

    private void waitForEntryOperations() {
      Iterator<Entry> it = allLocalEntries;
      while (it.hasNext()) {
        org.cache2k.impl.Entry e = it.next();
        synchronized (e) { }
      }
    }
  }

  @Override
  public void onEvictionFromHeap(final Entry<K, V> e) {
    boolean _storeEvenImmediatelyExpired =
      heapCache.hasKeepAfterExpired() && (e.isDataValidState() || e.isExpiredState() ||
      e.nextRefreshTime == org.cache2k.impl.Entry.FETCH_NEXT_TIME_STATE);
    boolean _shouldStore =
      (storage != null) && (_storeEvenImmediatelyExpired || e.hasFreshData());
    if (_shouldStore) {
      storage.evict(e);
    }
  }

  /**
   * Used by the storage iterator to insert the entry in the cache.
   */
  public Entry<K,V> insertEntryFromStorage(final StorageEntry se) {
    Semantic<K,V, Entry<K,V>> op = new Semantic.Read<K, V, Entry<K, V>>() {
      @Override
      public void examine(final Progress<V, Entry<K, V>> c, final ExaminationEntry<K, V> e) {
        c.result((Entry<K, V>) e);
      }
    };
    EntryAction<K, V, Entry<K,V>> _action = new MyEntryAction<Entry<K,V>>(op, (K) se.getKey(), null) {
      @Override
      public void storageRead() {
        onReadSuccess(se);
      }
    };
    Entry<K,V> e = execute(op, _action);
    return e;
  }

  @Override
  protected <R> EntryAction<K, V, R> createEntryAction(final K key, final Entry<K, V> e, final Semantic<K, V, R> op) {
    return new MyEntryAction<R>(op, key, e);
  }

  /**
   * Wire the entry action to the resources of this cache.
   */
  class MyEntryAction<R> extends EntryAction<K,V, R> {

    public MyEntryAction(final Semantic<K, V, R> op, final K _k, final Entry<K, V> e) {
      super(WiredCache.this.heapCache, WiredCache.this, op, _k, e);
    }

    @Override
    protected boolean mightHaveListeners() {
      return true;
    }

    @Override
    protected CacheEntryCreatedListener<K, V>[] entryCreatedListeners() {
      return syncEntryCreatedListeners;
    }

    @Override
    protected CacheEntryRemovedListener<K, V>[] entryRemovedListeners() {
      return syncEntryRemovedListeners;
    }

    @Override
    protected CacheEntryUpdatedListener<K, V>[] entryUpdatedListeners() {
      return syncEntryUpdatedListeners;
    }

    @Override
    protected StorageAdapter storage() {
      return storage;
    }

    @Override
    protected CacheWriter<K, V> writer() {
      return writer;
    }

  }

}
