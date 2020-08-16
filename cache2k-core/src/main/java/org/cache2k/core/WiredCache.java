package org.cache2k.core;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.cache2k.configuration.CacheType;
import org.cache2k.core.util.InternalClock;
import org.cache2k.event.CacheEntryEvictedListener;
import org.cache2k.event.CacheEntryExpiredListener;
import org.cache2k.integration.AdvancedCacheLoader;
import org.cache2k.CacheEntry;
import org.cache2k.event.CacheEntryCreatedListener;
import org.cache2k.integration.AsyncCacheLoader;
import org.cache2k.integration.ExceptionPropagator;
import org.cache2k.processor.EntryProcessor;
import org.cache2k.event.CacheEntryRemovedListener;
import org.cache2k.event.CacheEntryUpdatedListener;
import org.cache2k.CacheManager;
import org.cache2k.integration.CacheWriter;
import org.cache2k.CacheOperationCompletionListener;
import org.cache2k.core.operation.ExaminationEntry;
import org.cache2k.core.operation.Progress;
import org.cache2k.core.operation.Semantic;
import org.cache2k.core.operation.Operations;
import org.cache2k.core.util.Log;
import org.cache2k.core.storageApi.PurgeableStorage;
import org.cache2k.core.storageApi.StorageAdapter;
import org.cache2k.core.storageApi.StorageEntry;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A cache implementation that builds on a heap cache and coordinates with additional
 * attachments like storage, listeners and a writer.
 *
 * @author Jens Wilke
 */
public class WiredCache<K, V> extends BaseCache<K, V>
  implements StorageAdapter.Parent, HeapCacheListener<K,V> {

  @SuppressWarnings("unchecked")
  final Operations<K, V> OPS = Operations.SINGLETON;

  HeapCache<K,V> heapCache;
  StorageAdapter storage;
  AdvancedCacheLoader<K,V> loader;
  AsyncCacheLoader<K,V> asyncLoader;
  CacheWriter<K, V> writer;
  CacheEntryRemovedListener<K,V>[] syncEntryRemovedListeners;
  CacheEntryCreatedListener<K,V>[] syncEntryCreatedListeners;
  CacheEntryUpdatedListener<K,V>[] syncEntryUpdatedListeners;
  CacheEntryExpiredListener<K,V>[] syncEntryExpiredListeners;
  CacheEntryEvictedListener<K,V>[] syncEntryEvictedListeners;

  private CommonMetrics.Updater metrics() {
    return heapCache.metrics;
  }

  @Override
  public Log getLog() {
    return heapCache.getLog();
  }

  /** For testing */
  public HeapCache getHeapCache() {
    return heapCache;
  }

  @Override
  public InternalClock getClock() {
    return heapCache.getClock();
  }

  @Override
  public boolean isNullValuePermitted() {
    return heapCache.isNullValuePermitted();
  }

  @Override
  public String getName() {
    return heapCache.getName();
  }

  @Override
  public CacheType getKeyType() {
    return heapCache.getKeyType();
  }

  @Override
  public CacheType getValueType() {
    return heapCache.getValueType();
  }

  @Override
  public CacheManager getCacheManager() {
    return heapCache.getCacheManager();
  }

  @Override
  public V computeIfAbsent(final K key, final Callable<V> callable) {
    return returnValue(execute(key, OPS.computeIfAbsent(key, callable)));
  }

  @Override
  public V peekAndPut(K key, V value) {
    return returnValue(execute(key, OPS.peekAndPut(key, value)));
  }

  @Override
  public V peekAndRemove(K key) {
    return returnValue(execute(key, OPS.peekAndRemove(key)));
  }

  @Override
  public V peekAndReplace(K key, V value) {
    return returnValue(execute(key, OPS.peekAndReplace(key, value)));
  }

  @Override
  public CacheEntry<K, V> peekEntry(K key) {
    return execute(key, OPS.peekEntry(key));
  }

  @Override
  public void prefetch(final K key) {
    if (!isLoaderPresent()) {
      return;
    }
    Entry<K,V> e = heapCache.lookupEntryNoHitRecord(key);
    if (e != null && e.hasFreshData(getClock())) {
      return;
    }
    try {
      heapCache.getPrefetchExecutor().execute(new HeapCache.RunWithCatch(this) {
        @Override
        public void action() {
          load(key);
        }
      });
    } catch (RejectedExecutionException ignore) { }
  }

  @Override
  public void prefetchAll(final Iterable<? extends K> _keys, final CacheOperationCompletionListener l) {
    final CacheOperationCompletionListener _listener= l != null ? l : HeapCache.DUMMY_LOAD_COMPLETED_LISTENER;
    if (!isLoaderPresent()) {
      _listener.onCompleted();
      return;
    }
    final AtomicInteger _count = new AtomicInteger(2);
    try {
      Set<K> _keysToLoad = heapCache.checkAllPresent(_keys);
      for (K k : _keysToLoad) {
        final K key = k;
        Runnable r = new HeapCache.RunWithCatch(this) {
          @Override
          public void action() {
            try {
              load(key);
            } finally {
              if (_count.decrementAndGet() == 0) {
                _listener.onCompleted();
              }
            }
          }
        };
        try {
          heapCache.getPrefetchExecutor().execute(r);
          _count.incrementAndGet();
        } catch (RejectedExecutionException ignore) { }
      }
    } finally {
      if (_count.addAndGet(-2) == 0) {
        _listener.onCompleted();
      }
    }
  }

  private void load(final K key) {
    Entry<K, V> e = lookupQuick(key);
    if (e != null && e.hasFreshData(getClock())) {
      return;
    }
    load(key, e);
  }

  private void load(final K key, final Entry<K, V> _e) {
    execute(key, _e, OPS.get(key));
  }

  @Override
  public boolean containsKey(K key) {
    return execute(key, OPS.contains(key));
  }

  @Override
  public boolean putIfAbsent(K key, V value) {
    return execute(key, OPS.putIfAbsent(key, value));
  }

  @Override
  public void put(K key, V value) {
    execute(key, OPS.put(key, value));
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
      put(e.getKey(), e.getValue());
    }
  }

  @Override
  public void remove(K key) {
    execute(key, OPS.remove(key));
  }

  @Override
  public boolean removeIfEquals(K key, V value) {
    return execute(key, OPS.remove(key, value));
  }

  @Override
  public boolean containsAndRemove(K key) {
    return execute(key, OPS.containsAndRemove(key));
  }

  @Override
  public boolean replace(K key, V _newValue) {
    return execute(key, OPS.replace(key, _newValue));
  }

  @Override
  public boolean replaceIfEquals(K key, V _oldValue, V _newValue) {
    return execute(key, OPS.replace(key, _oldValue, _newValue));
  }

  @Override
  public void loadAll(final Iterable<? extends K> _keys, final CacheOperationCompletionListener l) {
    checkLoaderPresent();
    final CacheOperationCompletionListener _listener= l != null ? l : HeapCache.DUMMY_LOAD_COMPLETED_LISTENER;
    Set<K> _keysToLoad = heapCache.checkAllPresent(_keys);
    if (_keysToLoad.isEmpty()) {
      _listener.onCompleted();
      return;
    }
    if (asyncLoader != null) {
      loadAllWithAsyncLoader(_listener, _keysToLoad);
    } else {
      loadAllWithSyncLoader(_listener, _keysToLoad);
    }
  }

  /**
   * Load the keys into the cache via the async path. The key set must always be non empty.
   * The completion listener is called when all keys are loaded.
   */
  private void loadAllWithAsyncLoader(final CacheOperationCompletionListener _listener, final Set<K> _keysToLoad) {
    final AtomicInteger _countDown = new AtomicInteger(_keysToLoad.size());
    EntryAction.CompletedCallback cb = new EntryAction.CompletedCallback() {
      @Override
      public void entryActionCompleted(final EntryAction ea) {
        int v = _countDown.decrementAndGet();
        if (v == 0) {
          _listener.onCompleted();
          return;
        }
      }
    };
    for (K k : _keysToLoad) {
      final K key = k;
      executeAsyncLoadOrRefresh(key, null, OPS.GET, cb);
    }
  }

  private void loadAllWithSyncLoader(final CacheOperationCompletionListener _listener, final Set<K> _keysToLoad) {
    final AtomicInteger _countDown = new AtomicInteger(_keysToLoad.size());
    for (K k : _keysToLoad) {
      final K key = k;
      Runnable r = new HeapCache.RunWithCatch(this) {
        @Override
        public void action() {
          try {
            load(key);
          } finally {
            int v = _countDown.decrementAndGet();
            if (v == 0) {
              _listener.onCompleted();
              return;
            }
          }
        }
      };
      try {
        heapCache.loaderExecutor.execute(r);
      } catch (RejectedExecutionException ex) {
        r.run();
      }
    }
  }

  @Override
  public void reloadAll(final Iterable<? extends K> _keys, final CacheOperationCompletionListener l) {
    checkLoaderPresent();
    final CacheOperationCompletionListener _listener= l != null ? l : HeapCache.DUMMY_LOAD_COMPLETED_LISTENER;
    Set<K> _keySet = heapCache.generateKeySet(_keys);
    if (asyncLoader != null) {
      reloadAllWithAsyncLoader(_listener, _keySet);
    } else {
      reloadAllWithSyncLoader(_listener, _keySet);
    }
  }

  private void reloadAllWithAsyncLoader(final CacheOperationCompletionListener _listener, final Set<K> _keySet) {
    final AtomicInteger _countDown = new AtomicInteger(_keySet.size());
    EntryAction.CompletedCallback cb = new EntryAction.CompletedCallback() {
      @Override
      public void entryActionCompleted(final EntryAction ea) {
        if (_countDown.decrementAndGet() == 0) {
          _listener.onCompleted();
        }
      }
    };
    for (K k : _keySet) {
      final K key = k;
      executeAsyncLoadOrRefresh(key, null, OPS.UNCONDITIONAL_LOAD, cb);
    }
  }

  /**
   * Execute asynchronously, returns immediately and uses callback to notify on
   * operation completion. Call does not block.
   *
   * @param key the key
   * @param e the entry, optional, may be {@code null}
   */
  private <R> void executeAsyncLoadOrRefresh(K key, Entry<K,V> e, Semantic<K,V,R> op,
                                             EntryAction.CompletedCallback<K,V,R> cb) {
    EntryAction<K,V,R> _action = new MyEntryAction<R>(op, key, e, cb);
    _action.start();
  }

  protected <R> MyEntryAction<R> createFireAndForgetAction(final Entry<K, V> e, final Semantic<K, V, R> op) {
    return new MyEntryAction<R>(op, e.getKey(), e, EntryAction.NOOP_CALLBACK);
  }

  @Override
  public Executor getExecutor() {
    return heapCache.getExecutor();
  }

  private void reloadAllWithSyncLoader(final CacheOperationCompletionListener _listener, final Set<K> _keySet) {
    final AtomicInteger _countDown = new AtomicInteger(_keySet.size());
    for (K k : _keySet) {
      final K key = k;
      Runnable r = new HeapCache.RunWithCatch(this) {
        @SuppressWarnings("unchecked")
        @Override
        public void action() {
          try {
            execute(key, (Semantic<K, V, Void>) OPS.UNCONDITIONAL_LOAD);
          } finally {
            if (_countDown.decrementAndGet() == 0) {
              _listener.onCompleted();
            }
          }
        }
      };
      try {
        heapCache.loaderExecutor.execute(r);
      } catch (RejectedExecutionException ex) {
        r.run();
      }
    }
  }

  private void checkLoaderPresent() {
    if (!isLoaderPresent()) {
      throw new UnsupportedOperationException("loader not set");
    }
  }

  @Override
  public boolean isWeigherPresent() {
    return heapCache.eviction.isWeigherPresent();
  }

  @Override
  public boolean isLoaderPresent() {
    return loader != null || asyncLoader != null;
  }

  V returnValue(V v) {
    return heapCache.returnValue(v);
  }

  V returnValue(Entry<K,V> e) {
    return returnValue(e.getValueOrException());
  }

  Entry<K, V> lookupQuick(K key) {
    return heapCache.lookupEntry(key);
  }


  @Override
  public V get(K key) {
    Entry<K, V> e = lookupQuick(key);
    if (e != null && e.hasFreshData(getClock())) {
      return returnValue(e);
    }
    return returnValue(execute(key, e, OPS.get(key)));
   }

  /**
   * Just a simple loop at the moment. We need to deal with possible null values
   * and exceptions. This is a simple placeholder implementation that covers it
   * all by working on the entry.
   */
  @Override
  public Map<K, V> getAll(final Iterable<? extends K> keys) {
    Map<K, CacheEntry<K, V>> map = new HashMap<K, CacheEntry<K, V>>();
    for (K k : keys) {
      CacheEntry<K, V> e = execute(k, OPS.getEntry(k));
      if (e != null) {
        map.put(k, e);
      }
    }
    return heapCache.convertCacheEntry2ValueMap(map);
  }

  @Override
  public CacheEntry<K, V> getEntry(K key) {
    return execute(key, OPS.getEntry(key));
  }

  @Override
  public int getTotalEntryCount() {
    if (storage != null) {
      return (int) storage.getTotalEntryCount();
    }
    return heapCache.getTotalEntryCount();
  }

  @Override
  public <R> R invoke(K key, EntryProcessor<K, V, R> entryProcessor) {
    if (key == null) {
      throw new NullPointerException();
    }
    return execute(key, OPS.invoke(key, entryProcessor));
  }

  @SuppressWarnings("unchecked")
  @Override
  public Iterator<CacheEntry<K, V>> iterator() {
    Iterator<CacheEntry<K, V>> tor;
    if (storage == null) {
      tor = new HeapCache.IteratorFilterEntry2Entry(heapCache, heapCache.iterateAllHeapEntries(), true);
    } else {
      tor = new HeapCache.IteratorFilterEntry2Entry(heapCache, storage.iterateAll(), false);
    }
    final Iterator<CacheEntry<K, V>> it = tor;
    Iterator<CacheEntry<K, V>> _adapted = new Iterator<CacheEntry<K, V>>() {

      CacheEntry<K, V> entry;

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
    Entry<K, V> e = lookupQuick(key);
    if (e != null && e.hasFreshData(getClock())) {
      return returnValue(e);
    }
    return returnValue(execute(key, OPS.peek(key)));
  }

  /**
   * We need to deal with possible null values and exceptions. This is
   * a simple placeholder implementation that covers it all by working
   * on the entry.
   */
  @Override
  public Map<K, V> peekAll(final Iterable<? extends K> keys) {
    Map<K, CacheEntry<K, V>> map = new HashMap<K, CacheEntry<K, V>>();
    for (K k : keys) {
      CacheEntry<K, V> e = execute(k, OPS.peekEntry(k));
      if (e != null) {
        map.put(k, e);
      }
    }
    return heapCache.convertCacheEntry2ValueMap(map);
  }

  @Override
  public InternalCacheInfo getLatestInfo() {
    return heapCache.getLatestInfo(this);
  }

  @Override
  public InternalCacheInfo getInfo() {
    return heapCache.getInfo(this);
  }

  @Override
  public CommonMetrics getCommonMetrics() {
    return heapCache.getCommonMetrics();
  }

  @Override
  public void logAndCountInternalException(final String s, final Throwable t) {
    heapCache.logAndCountInternalException(s, t);
  }

  @Override
  public void checkIntegrity() {
    heapCache.checkIntegrity();
  }

  @Override
  public boolean isClosed() {
    return heapCache.isClosed();
  }

  public void init() {
    if (storage == null  && heapCache.eviction.getMetrics().getMaxSize() == 0) {
      throw new IllegalArgumentException("maxElements must be >0");
    }
    if (storage != null) {
      storage.open();
    }

    heapCache.timing.init(this);
    heapCache.initWithoutTimerHandler();
  }

  @Override
  public void cancelTimerJobs() {
    synchronized (lockObject()) {
      heapCache.cancelTimerJobs();
      if (storage != null) {
        storage.cancelTimerJobs();
      }
    }
  }

  @Override
  public void clear() {
    if (storage != null) {
      storage.clear();
      return;
    }
    heapCache.clear();
  }

  @Override
  public void close() {
    try {
      heapCache.closePart1();
    } catch (CacheClosedException ex) {
      return;
    }
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
    heapCache.closePart2(this);
    closeCustomization(writer, "writer");
    if (syncEntryCreatedListeners != null) {
      for (Object l : syncEntryCreatedListeners) {
        closeCustomization(l, "entryCreatedListener");
      }
    }
    if (syncEntryUpdatedListeners != null) {
      for (Object l : syncEntryUpdatedListeners) {
        closeCustomization(l, "entryUpdatedListener");
      }
    }
    if (syncEntryRemovedListeners != null) {
      for (Object l : syncEntryRemovedListeners) {
        closeCustomization(l, "entryRemovedListener");
      }
    }
    if (syncEntryExpiredListeners != null) {
      for (Object l : syncEntryExpiredListeners) {
        closeCustomization(l, "entryExpiredListener");
      }
    }
  }

  @Override
  public CacheEntry<K, V> returnCacheEntry(final ExaminationEntry<K, V> e) {
    return heapCache.returnCacheEntry(e);
  }

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
  public Eviction getEviction() {
    return heapCache.getEviction();
  }

  @Override
  public StorageAdapter getStorage() {
    return storage;
  }

  /**
   * Insert a cache entry for the given key and run action under the entry
   * lock. If the cache entry has fresh data, we do not run the action.
   * Called from storage. The entry referenced by the key is expired and
   * will be purged.
   */
  public void lockAndRunForPurge(K key, PurgeableStorage.PurgeAction _action) {
    throw new UnsupportedOperationException();
  }

  /**
   * Calls eviction listeners.
   */
  @Override
  public void onEvictionFromHeap(final Entry<K, V> e) {
    CacheEntry<K,V> _currentEntry = heapCache.returnCacheEntry(e);
    if (syncEntryEvictedListeners != null) {
      for (CacheEntryEvictedListener<K, V> l : syncEntryEvictedListeners) {
        l.onEntryEvicted(this, _currentEntry);
      }
    }
  }


  @Override
  protected <R> EntryAction<K, V, R> createEntryAction(final K key, final Entry<K, V> e, final Semantic<K, V, R> op) {
    return new MyEntryAction<R>(op, key, e);
  }

  @Override
  public String getEntryState(final K key) {
    return heapCache.getEntryState(key);
  }

  /**
   * If not expired yet, negate time to enforce time checks, schedule task for expiry
   * otherwise.
   *
   * Semantics double with {@link HeapCache#timerEventExpireEntry(Entry, Object)}
   */
  @Override
  public void timerEventExpireEntry(final Entry<K, V> e, final Object task) {
    metrics().timerEvent();
    synchronized (e) {
      if (e.getTask() != task) { return; }
      long nrt = e.getNextRefreshTime();
      long now = heapCache.clock.millis();
      if (now < Math.abs(nrt)) {
        if (nrt > 0) {
          heapCache.timing.scheduleFinalTimerForSharpExpiry(e);
          e.setNextRefreshTime(-nrt);
        }
        return;
      }
    }
    enqueueTimerAction(e, OPS.EXPIRE_EVENT);
  }

  private <R> void enqueueTimerAction(Entry<K,V> e, Semantic<K,V,R> op) {
    EntryAction<K,V,R> _action = createFireAndForgetAction(e, op);
    getExecutor().execute(_action);
  }

  /**
   * Starts a refresh operation or expires if no threads in the loader thread pool are available.
   * If no async loader is available we execute the synchronous loader via the loader
   * thread pool.
   */
  @Override
  public void timerEventRefresh(final Entry<K, V> e, final Object task) {
    metrics().timerEvent();
    synchronized (e) {
      if (e.getTask() != task) { return; }
      if (asyncLoader != null) {
        enqueueTimerAction(e, OPS.REFRESH);
        return;
      }
      try {
        heapCache.prefetchExecutor.execute(createFireAndForgetAction(e, OPS.REFRESH));
        return;
      } catch (RejectedExecutionException ignore) {
      }
      metrics().refreshFailed();
      enqueueTimerAction(e, OPS.EXPIRE_EVENT);
    }
  }

  @Override
  public void timerEventProbationTerminated(final Entry<K, V> e, final Object task) {
    metrics().timerEvent();
    synchronized (e) {
      if (e.getTask() != task) { return; }
    }
    enqueueTimerAction(e, OPS.EXPIRE_EVENT);
  }

  /**
   * Wire the entry action to the resources of this cache.
   */
  class MyEntryAction<R> extends EntryAction<K,V, R> {

    public MyEntryAction(final Semantic<K, V, R> op, final K _k, final Entry<K, V> e) {
      super(WiredCache.this.heapCache, WiredCache.this, op, _k, e);
    }

    public MyEntryAction(final Semantic<K, V, R> op, final K _k,
                         final Entry<K, V> e, CompletedCallback cb) {
      super(WiredCache.this.heapCache, WiredCache.this, op, _k, e, cb);
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
    protected CacheEntryExpiredListener<K, V>[] entryExpiredListeners() {
      return syncEntryExpiredListeners;
    }

    @Override
    protected CacheWriter<K, V> writer() {
      return writer;
    }

    @Override
    protected TimingHandler<K, V> timing() {
      return heapCache.timing;
    }

    /**
     * Provides async loader context
     *
     * @see AsyncCacheLoader.Context
     */
    @Override
    public Executor getLoaderExecutor() {
      return heapCache.loaderExecutor;
    }

    @Override
    protected AsyncCacheLoader<K, V> asyncLoader() {
      return asyncLoader;
    }

    @Override
    protected Executor executor() { return heapCache.getExecutor(); }

    /**
     * Provides async loader context
     *
     * @see AsyncCacheLoader.Context
     */
    @Override
    public Executor getExecutor() {
      return executor();
    }

    @Override
    public ExceptionPropagator getExceptionPropagator() {
      return heapCache.exceptionPropagator;
    }

  }

}
