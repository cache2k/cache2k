package org.cache2k.core;

/*
 * #%L
 * cache2k core implementation
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

import org.cache2k.config.CacheType;
import org.cache2k.core.api.CommonMetrics;
import org.cache2k.core.api.InternalCacheInfo;
import org.cache2k.core.eviction.Eviction;
import org.cache2k.core.timing.Timing;
import org.cache2k.core.api.InternalClock;
import org.cache2k.event.CacheEntryEvictedListener;
import org.cache2k.event.CacheEntryExpiredListener;
import org.cache2k.io.AdvancedCacheLoader;
import org.cache2k.CacheEntry;
import org.cache2k.event.CacheEntryCreatedListener;
import org.cache2k.io.AsyncCacheLoader;
import org.cache2k.io.ExceptionPropagator;
import org.cache2k.processor.EntryProcessor;
import org.cache2k.event.CacheEntryRemovedListener;
import org.cache2k.event.CacheEntryUpdatedListener;
import org.cache2k.CacheManager;
import org.cache2k.io.CacheWriter;
import org.cache2k.CacheOperationCompletionListener;
import org.cache2k.core.operation.ExaminationEntry;
import org.cache2k.core.operation.Semantic;
import org.cache2k.core.operation.Operations;
import org.cache2k.core.log.Log;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/**
 * A cache implementation that builds on a heap cache and coordinates with additional
 * attachments like storage, listeners and a writer.
 *
 * @author Jens Wilke
 */
public class WiredCache<K, V> extends BaseCache<K, V>
  implements HeapCacheListener<K, V> {

  @SuppressWarnings("unchecked")
  final Operations<K, V> ops = Operations.SINGLETON;

  HeapCache<K, V> heapCache;
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
    OperationCompletion<K> completion = new OperationCompletion<>(keysToLoad, listener);
    EntryAction.CompletedCallback<K, V, Void> cb = new EntryAction.CompletedCallback<K, V, Void>() {
      @Override
      public void entryActionCompleted(EntryAction<K, V, Void> ea) {
        completion.complete(ea.getKey(), extractException(ea));
      }
    };
    for (K k : keysToLoad) {
      K key = k;
      executeAsyncLoadOrRefresh(key, null, Operations.GET, cb);
    }
  }

  private void loadAllWithSyncLoader(final CacheOperationCompletionListener listener,
                                     Set<K> keysToLoad) {
    final OperationCompletion<K> completion = new OperationCompletion<K>(keysToLoad, listener);
    for (K key : keysToLoad) {
      heapCache.executeLoader(completion, key, () -> {
        execute(key, null, ops.get(key));
        EntryAction<K, V, V> action = createEntryAction(key, null, ops.get(key));
        action.start();
        return extractException(action);
      });
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
                                      Set<K> keysToLoad) {
    OperationCompletion<K> completion = new OperationCompletion<>(keysToLoad, listener);
    EntryAction.CompletedCallback<K, V, Void> cb = new EntryAction.CompletedCallback<K, V, Void>() {
      @Override
      public void entryActionCompleted(EntryAction<K, V, Void> ea) {
        completion.complete(ea.getKey(), extractException(ea));
      }
    };
    for (K k : keysToLoad) {
      K key = k;
      executeAsyncLoadOrRefresh(key, null, ops.unconditionalLoad, cb);
    }
  }

  private void reloadAllWithSyncLoader(final CacheOperationCompletionListener listener,
                                     Set<K> keysToLoad) {
    final OperationCompletion<K> completion = new OperationCompletion<>(keysToLoad, listener);
    for (K key : keysToLoad) {
      heapCache.executeLoader(completion, key, () -> {
        EntryAction<K, V, V> action = createEntryAction(key, null, ops.unconditionalLoad);
        action.start();
        return extractException(action);
      });
    }
  }

  private Throwable extractException(EntryAction<K, V, ?> action) {
    if (action.exceptionToPropagate != null) {
      return action.exceptionToPropagate;
    }
    if (action.result instanceof ExceptionWrapper) {
      return ((ExceptionWrapper<?>) action.result).getException();
    }
    return null;
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
    final Iterator<CacheEntry<K, V>> it = new HeapCache.IteratorFilterEntry2Entry(
      heapCache, heapCache.iterateAllHeapEntries(), true);
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
    heapCache.timing.setTarget(this);
    heapCache.initWithoutTimerHandler();
  }

  @Override
  public void cancelTimerJobs() {
    synchronized (lockObject()) {
      heapCache.cancelTimerJobs();
    }
  }

  @Override
  public void clear() {
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
  public Eviction getEviction() {
    return heapCache.getEviction();
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
        heapCache.refreshExecutor.execute(createFireAndForgetAction(e, ops.refresh));
      } catch (RejectedExecutionException ex) {
        metrics().refreshRejected();
        enqueueTimerAction(e, ops.expireEvent);
      }
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
    protected Timing<K, V> timing() {
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
