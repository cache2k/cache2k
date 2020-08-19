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
  implements StorageAdapter.Parent, HeapCacheListener<K, V> {

  @SuppressWarnings("unchecked")
  final Operations<K, V> ops = Operations.SINGLETON;

  HeapCache<K, V> heapCache;
  StorageAdapter storage;
  AdvancedCacheLoader<K, V> loader;
  AsyncCacheLoader<K, V> asyncLoader;
  CacheWriter<K, V> writer;
  CacheEntryRemovedListener<K, V>[] syncEntryRemovedListeners;
  CacheEntryCreatedListener<K, V>[] syncEntryCreatedListeners;
  CacheEntryUpdatedListener<K, V>[] syncEntryUpdatedListeners;
  CacheEntryExpiredListener<K, V>[] syncEntryExpiredListeners;
  CacheEntryEvictedListener<K, V>[] syncEntryEvictedListeners;

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
  public V computeIfAbsent(K key, Callable<V> callable) {
    return returnValue(execute(key, ops.computeIfAbsent(key, callable)));
  }

  @Override
  public V peekAndPut(K key, V value) {
    return returnValue(execute(key, ops.peekAndPut(key, value)));
  }

  @Override
  public V peekAndRemove(K key) {
    return returnValue(execute(key, ops.peekAndRemove(key)));
  }

  @Override
  public V peekAndReplace(K key, V value) {
    return returnValue(execute(key, ops.peekAndReplace(key, value)));
  }

  @Override
  public CacheEntry<K, V> peekEntry(K key) {
    return execute(key, ops.peekEntry(key));
  }

  @Override
  public void prefetch(final K key) {
    if (!isLoaderPresent()) {
      return;
    }
    Entry<K, V> e = heapCache.lookupEntryNoHitRecord(key);
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
  public void prefetchAll(Iterable<? extends K> keys, CacheOperationCompletionListener l) {
    final CacheOperationCompletionListener listener =
      l != null ? l : HeapCache.DUMMY_LOAD_COMPLETED_LISTENER;
    if (!isLoaderPresent()) {
      listener.onCompleted();
      return;
    }
    final AtomicInteger count = new AtomicInteger(2);
    try {
      Set<K> keysToLoad = heapCache.checkAllPresent(keys);
      for (K k : keysToLoad) {
        final K key = k;
        Runnable r = new HeapCache.RunWithCatch(this) {
          @Override
          public void action() {
            try {
              load(key);
            } finally {
              if (count.decrementAndGet() == 0) {
                listener.onCompleted();
              }
            }
          }
        };
        try {
          heapCache.getPrefetchExecutor().execute(r);
          count.incrementAndGet();
        } catch (RejectedExecutionException ignore) { }
      }
    } finally {
      if (count.addAndGet(-2) == 0) {
        listener.onCompleted();
      }
    }
  }

  private void load(K key) {
    Entry<K, V> e = lookupQuick(key);
    if (e != null && e.hasFreshData(getClock())) {
      return;
    }
    load(key, e);
  }

  private void load(K key, Entry<K, V> e) {
    execute(key, e, ops.get(key));
  }

  @Override
  public boolean containsKey(K key) {
    return execute(key, ops.contains(key));
  }

  @Override
  public boolean putIfAbsent(K key, V value) {
    return execute(key, ops.putIfAbsent(key, value));
  }

  @Override
  public void put(K key, V value) {
    execute(key, ops.put(key, value));
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
      put(e.getKey(), e.getValue());
    }
  }

  @Override
  public void remove(K key) {
    execute(key, ops.remove(key));
  }

  @Override
  public boolean removeIfEquals(K key, V value) {
    return execute(key, ops.remove(key, value));
  }

  @Override
  public boolean containsAndRemove(K key) {
    return execute(key, ops.containsAndRemove(key));
  }

  @Override
  public boolean replace(K key, V newValue) {
    return execute(key, ops.replace(key, newValue));
  }

  @Override
  public boolean replaceIfEquals(K key, V oldValue, V newValue) {
    return execute(key, ops.replace(key, oldValue, newValue));
  }

  @Override
  public void loadAll(Iterable<? extends K> keys, CacheOperationCompletionListener l) {
    checkLoaderPresent();
    CacheOperationCompletionListener listener =
      l != null ? l : HeapCache.DUMMY_LOAD_COMPLETED_LISTENER;
    Set<K> keysToLoad = heapCache.checkAllPresent(keys);
    if (keysToLoad.isEmpty()) {
      listener.onCompleted();
      return;
    }
    if (asyncLoader != null) {
      loadAllWithAsyncLoader(listener, keysToLoad);
    } else {
      loadAllWithSyncLoader(listener, keysToLoad);
    }
  }

  /**
   * Load the keys into the cache via the async path. The key set must always be non empty.
   * The completion listener is called when all keys are loaded.
   */
  private void loadAllWithAsyncLoader(final CacheOperationCompletionListener listener,
                                      Set<K> keysToLoad) {
    final AtomicInteger countDown = new AtomicInteger(keysToLoad.size());
    EntryAction.CompletedCallback cb = new EntryAction.CompletedCallback() {
      @Override
      public void entryActionCompleted(EntryAction ea) {
        int v = countDown.decrementAndGet();
        if (v == 0) {
          listener.onCompleted();
          return;
        }
      }
    };
    for (K k : keysToLoad) {
      K key = k;
      executeAsyncLoadOrRefresh(key, null, Operations.GET, cb);
    }
  }

  private void loadAllWithSyncLoader(final CacheOperationCompletionListener listener,
                                     Set<K> keysToLoad) {
    final AtomicInteger countDown = new AtomicInteger(keysToLoad.size());
    for (K k : keysToLoad) {
      final K key = k;
      Runnable r = new HeapCache.RunWithCatch(this) {
        @Override
        public void action() {
          try {
            load(key);
          } finally {
            int v = countDown.decrementAndGet();
            if (v == 0) {
              listener.onCompleted();
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
  public void reloadAll(Iterable<? extends K> keys, CacheOperationCompletionListener l) {
    checkLoaderPresent();
    CacheOperationCompletionListener listener =
      l != null ? l : HeapCache.DUMMY_LOAD_COMPLETED_LISTENER;
    Set<K> keySet = heapCache.generateKeySet(keys);
    if (asyncLoader != null) {
      reloadAllWithAsyncLoader(listener, keySet);
    } else {
      reloadAllWithSyncLoader(listener, keySet);
    }
  }

  private void reloadAllWithAsyncLoader(final CacheOperationCompletionListener listener,
                                        Set<K> keySet) {
    final AtomicInteger countDown = new AtomicInteger(keySet.size());
    EntryAction.CompletedCallback cb = new EntryAction.CompletedCallback() {
      @Override
      public void entryActionCompleted(EntryAction ea) {
        if (countDown.decrementAndGet() == 0) {
          listener.onCompleted();
        }
      }
    };
    for (K k : keySet) {
      K key = k;
      executeAsyncLoadOrRefresh(key, null, ops.unconditionalLoad, cb);
    }
  }

  /**
   * Execute asynchronously, returns immediately and uses callback to notify on
   * operation completion. Call does not block.
   *
   * @param key the key
   * @param e the entry, optional, may be {@code null}
   */
  private <R> void executeAsyncLoadOrRefresh(K key, Entry<K, V> e, Semantic<K, V, R> op,
                                             EntryAction.CompletedCallback<K, V, R> cb) {
    EntryAction<K, V, R> action = new MyEntryAction<R>(op, key, e, cb);
    action.start();
  }

  protected <R> MyEntryAction<R> createFireAndForgetAction(Entry<K, V> e, Semantic<K, V, R> op) {
    return new MyEntryAction<R>(op, e.getKey(), e, EntryAction.NOOP_CALLBACK);
  }

  @Override
  public Executor getExecutor() {
    return heapCache.getExecutor();
  }

  private void reloadAllWithSyncLoader(final CacheOperationCompletionListener listener,
                                       Set<K> keySet) {
    final AtomicInteger countDown = new AtomicInteger(keySet.size());
    for (K k : keySet) {
      final K key = k;
      Runnable r = new HeapCache.RunWithCatch(this) {
        @SuppressWarnings("unchecked")
        @Override
        public void action() {
          try {
            execute(key, (Semantic<K, V, Void>) ops.unconditionalLoad);
          } finally {
            if (countDown.decrementAndGet() == 0) {
              listener.onCompleted();
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

  V returnValue(Entry<K, V> e) {
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
    return returnValue(execute(key, e, ops.get(key)));
   }

  /**
   * Just a simple loop at the moment. We need to deal with possible null values
   * and exceptions. This is a simple placeholder implementation that covers it
   * all by working on the entry.
   */
  @Override
  public Map<K, V> getAll(Iterable<? extends K> keys) {
    Map<K, CacheEntry<K, V>> map = new HashMap<K, CacheEntry<K, V>>();
    for (K k : keys) {
      CacheEntry<K, V> e = execute(k, ops.getEntry(k));
      if (e != null) {
        map.put(k, e);
      }
    }
    return heapCache.convertCacheEntry2ValueMap(map);
  }

  @Override
  public CacheEntry<K, V> getEntry(K key) {
    return execute(key, ops.getEntry(key));
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
    return execute(key, ops.invoke(key, entryProcessor));
  }

  @SuppressWarnings("unchecked")
  @Override
  public Iterator<CacheEntry<K, V>> iterator() {
    Iterator<CacheEntry<K, V>> tor;
    if (storage == null) {
      tor = new HeapCache.IteratorFilterEntry2Entry(
        heapCache, heapCache.iterateAllHeapEntries(), true);
    } else {
      tor = new HeapCache.IteratorFilterEntry2Entry(
        heapCache, storage.iterateAll(), false);
    }
    final Iterator<CacheEntry<K, V>> it = tor;
    Iterator<CacheEntry<K, V>> adapted = new Iterator<CacheEntry<K, V>>() {

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
    return adapted;
  }

  @Override
  public V peek(K key) {
    Entry<K, V> e = lookupQuick(key);
    if (e != null && e.hasFreshData(getClock())) {
      return returnValue(e);
    }
    return returnValue(execute(key, ops.peek(key)));
  }

  /**
   * We need to deal with possible null values and exceptions. This is
   * a simple placeholder implementation that covers it all by working
   * on the entry.
   */
  @Override
  public Map<K, V> peekAll(Iterable<? extends K> keys) {
    Map<K, CacheEntry<K, V>> map = new HashMap<K, CacheEntry<K, V>>();
    for (K k : keys) {
      CacheEntry<K, V> e = execute(k, ops.peekEntry(k));
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
  public void logAndCountInternalException(String s, Throwable t) {
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
    Future<Void> waitForStorage = null;
    if (storage != null) {
      waitForStorage = storage.shutdown();
    }
    if (waitForStorage != null) {
      try {
        waitForStorage.get();
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
  public CacheEntry<K, V> returnCacheEntry(ExaminationEntry<K, V> e) {
    return heapCache.returnCacheEntry(e);
  }

  private Object lockObject() {
    return heapCache.lock;
  }

  @Override
  public void resetStorage(StorageAdapter current, StorageAdapter replacement) {
    synchronized (lockObject()) {
      storage = replacement;
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
  public void lockAndRunForPurge(K key, PurgeableStorage.PurgeAction action) {
    throw new UnsupportedOperationException();
  }

  /**
   * Calls eviction listeners.
   */
  @Override
  public void onEvictionFromHeap(Entry<K, V> e) {
    CacheEntry<K, V> currentEntry = heapCache.returnCacheEntry(e);
    if (syncEntryEvictedListeners != null) {
      for (CacheEntryEvictedListener<K, V> l : syncEntryEvictedListeners) {
        l.onEntryEvicted(this, currentEntry);
      }
    }
  }


  @Override
  protected <R> EntryAction<K, V, R> createEntryAction(K key, Entry<K, V> e, Semantic<K, V, R> op) {
    return new MyEntryAction<R>(op, key, e);
  }

  @Override
  public String getEntryState(K key) {
    return heapCache.getEntryState(key);
  }

  /**
   * If not expired yet, negate time to enforce time checks, schedule task for expiry
   * otherwise.
   *
   * Semantics double with {@link HeapCache#timerEventExpireEntry(Entry, Object)}
   */
  @Override
  public void timerEventExpireEntry(Entry<K, V> e, Object task) {
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
    enqueueTimerAction(e, ops.expireEvent);
  }

  private <R> void enqueueTimerAction(Entry<K, V> e, Semantic<K, V, R> op) {
    EntryAction<K, V, R> action = createFireAndForgetAction(e, op);
    getExecutor().execute(action);
  }

  /**
   * Starts a refresh operation or expires if no threads in the loader thread pool are available.
   * If no async loader is available we execute the synchronous loader via the loader
   * thread pool.
   */
  @Override
  public void timerEventRefresh(Entry<K, V> e, Object task) {
    metrics().timerEvent();
    synchronized (e) {
      if (e.getTask() != task) { return; }
      if (asyncLoader != null) {
        enqueueTimerAction(e, ops.refresh);
        return;
      }
      try {
        heapCache.prefetchExecutor.execute(createFireAndForgetAction(e, ops.refresh));
        return;
      } catch (RejectedExecutionException ignore) {
      }
      metrics().refreshFailed();
      enqueueTimerAction(e, ops.expireEvent);
    }
  }

  @Override
  public void timerEventProbationTerminated(Entry<K, V> e, Object task) {
    metrics().timerEvent();
    synchronized (e) {
      if (e.getTask() != task) { return; }
    }
    enqueueTimerAction(e, ops.expireEvent);
  }

  /**
   * Wire the entry action to the resources of this cache.
   */
  class MyEntryAction<R> extends EntryAction<K, V, R> {

    MyEntryAction(Semantic<K, V, R> op, K k, Entry<K, V> e) {
      super(WiredCache.this.heapCache, WiredCache.this, op, k, e);
    }

    MyEntryAction(Semantic<K, V, R> op, K k,
                  Entry<K, V> e, CompletedCallback cb) {
      super(WiredCache.this.heapCache, WiredCache.this, op, k, e, cb);
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
