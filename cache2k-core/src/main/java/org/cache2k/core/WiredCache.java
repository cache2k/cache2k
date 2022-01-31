package org.cache2k.core;

/*-
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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

import org.cache2k.Cache;
import org.cache2k.CacheClosedException;
import org.cache2k.CacheException;
import org.cache2k.annotation.Nullable;
import org.cache2k.config.CacheType;
import org.cache2k.core.api.CommonMetrics;
import org.cache2k.core.api.InternalCacheInfo;
import org.cache2k.core.eviction.Eviction;
import org.cache2k.core.eviction.InternalEvictionListener;
import org.cache2k.core.timing.Timing;
import org.cache2k.io.AsyncBulkCacheLoader;
import org.cache2k.io.BulkCacheLoader;
import org.cache2k.io.CacheLoaderException;
import org.cache2k.operation.TimeReference;
import org.cache2k.event.CacheEntryEvictedListener;
import org.cache2k.event.CacheEntryExpiredListener;
import org.cache2k.io.AdvancedCacheLoader;
import org.cache2k.CacheEntry;
import org.cache2k.event.CacheEntryCreatedListener;
import org.cache2k.io.AsyncCacheLoader;
import org.cache2k.io.ExceptionPropagator;
import org.cache2k.processor.EntryProcessingException;
import org.cache2k.processor.EntryProcessingResult;
import org.cache2k.processor.EntryProcessor;
import org.cache2k.event.CacheEntryRemovedListener;
import org.cache2k.event.CacheEntryUpdatedListener;
import org.cache2k.CacheManager;
import org.cache2k.io.CacheWriter;
import org.cache2k.core.operation.ExaminationEntry;
import org.cache2k.core.operation.Semantic;
import org.cache2k.core.operation.Operations;
import org.cache2k.core.log.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;

/**
 * A cache implementation that builds on a heap cache and coordinates with additional
 * attachments like storage, listeners and a writer.
 *
 * @author Jens Wilke
 */
public class WiredCache<K, V> extends BaseCache<K, V>
  implements InternalEvictionListener<K, V> {

  @SuppressWarnings("unchecked")
  final Operations<K, V> ops = Operations.SINGLETON;

  /** Either ourselves or a wrapped cache used for events */
  Cache<K, V> userCache;
  HeapCache<K, V> heapCache;
  AdvancedCacheLoader<K, V> loader;
  AsyncCacheLoader<K, V> asyncLoader;
  BulkCacheLoader<K, V> bulkCacheLoader;
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
  public TimeReference getClock() {
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
  public V computeIfAbsent(K key, Function<? super K, ? extends V> function) {
    return returnValue(execute(key, ops.computeIfAbsent(key, function)));
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
    return execute(key, ops.peekEntry());
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

  public CompletableFuture<Void> loadAll(Iterable<? extends K> keys) {
    checkLoaderPresent();
    BulkResultCollector<K, V> collect = new BulkResultCollector<>();
    Set<K> keysToLoad = getAllPrescreen(keys, collect);
    CacheLoaderException prescreenException = collect.getAnyLoaderException();
    if (keysToLoad.isEmpty()) {
      if (prescreenException != null) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        f.completeExceptionally(prescreenException);
        return f;
      }
      return CompletableFuture.completedFuture(null);
    }
    if (asyncLoader != null) {
      return failAnyway(prescreenException,
        completeWithVoid(asyncBulkOp(Operations.GET, keysToLoad, true)));
    } else {
      if (bulkCacheLoader == null) {
        return failAnyway(prescreenException, loadAllWithSyncLoader(keysToLoad));
      } else {
        return failAnyway(prescreenException,
          executeSyncBulkOp(Operations.GET, keysToLoad, true));
      }
    }
  }

  private CompletableFuture<Void> failAnyway(CacheLoaderException ex, CompletableFuture<Void> f) {
    if (ex != null) {
      CompletableFuture<Void> other = new CompletableFuture<>();
      f.handle((unused, throwable) -> { other.completeExceptionally(ex); return null; });
      return other;
    }
    return f;
  }

  private CompletableFuture<Void> executeSyncBulkOp(Semantic operation, Set<K> keys,
                                                    boolean propagateLoadException) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    Runnable runnable = () -> {
      Throwable t;
      try {
        BulkAction<K, V, V> result = syncBulkOp(operation, keys);
        t = result.getExceptionToPropagate();
        if (propagateLoadException && t == null) {
          t = result.getLoaderException();
        }
      } catch (Throwable maybeCacheClosedException) {
        t = maybeCacheClosedException;
      }
      if (t != null) {
        future.completeExceptionally(t);
      } else {
        future.complete(null);
      }
    };
    heapCache.executeLoader(runnable);
    return future;
  }

  /**
   * Process operation synchronously and use the bulk loader, if possible.
   * Expects bulk loader is available.
   */
  private <R> BulkAction<K, V, R> syncBulkOp(Semantic<K, V, R> op, Set<K> keys) {
    AsyncBulkCacheLoader<K, V> myLoader = (keySet, contexts, callback) -> {
      try {
        callback.onLoadSuccess(bulkCacheLoader.loadAll(keySet));
      } catch (Throwable ouch) {
        callback.onLoadFailure(ouch);
      }
    };
    BulkAction<K, V, R> bulkAction = new BulkAction<K, V, R>(heapCache, this, myLoader, keys) {
      @Override
      protected boolean isSyncMode() {
        return true;
      }

      @Override
      protected EntryAction<K, V, R> createEntryAction(K key, BulkAction<K, V, R> bulkAction) {
        return new MyEntryAction<R>(op, key, null, null /* no callback */) {
          @Override
          protected AsyncCacheLoader<K, V> asyncLoader() {
            return bulkAction;
          }
        };
      }
    };
    bulkAction.start();
    return bulkAction;
  }

  /**
   * Process operation asynchronously and use the bulk loader, if possible and available.
   * Expects an async loader is available, with optional bulk capabilities.
   *
   * @param completeExceptionally exceptions might be handled differently,
   *                              true for getAll, false for invokeAll
   */
  private <R> CompletableFuture<BulkAction<K, V, R>> asyncBulkOp(
    Semantic<K, V, R> op, Set<K> keys, boolean completeExceptionally) {
    CompletableFuture<BulkAction<K, V, R>> future = new CompletableFuture<>();
    BulkAction<?, ?, ?> action =
      new BulkAction<K, V, R>(heapCache, this, asyncLoader, keys) {
      @Override
      protected EntryAction<K, V, R> createEntryAction(K key, BulkAction<K, V, R> bulkAction) {
        return new MyEntryAction<R>(op, key, null, bulkAction) {
          @Override
          protected AsyncCacheLoader<K, V> asyncLoader() {
            return bulkAction;
          }
        };
      }
      @Override
      protected void bulkOperationCompleted() {
        Throwable exception = completeExceptionally ? getException() : null;
        if (exception != null) {
          future.completeExceptionally(exception);
        } else {
          future.complete(this);
        }
      }
    };
    action.start();
    return future;
  }

  /**
   * Only sync loader present. Use loader executor to run things in parallel.
   */
  private CompletableFuture<Void> loadAllWithSyncLoader(Set<K> keysToLoad) {
    OperationCompletion<K> completion = new OperationCompletion<>(keysToLoad);
    for (K key : keysToLoad) {
      heapCache.executeLoader(completion, key, () -> {
        EntryAction<K, V, V> action = createEntryAction(key, null, ops.get(key));
        action.start();
        return action.getException();
      });
    }
    return completion.getFuture();
  }

  private CompletableFuture<Void> completeWithVoid(CompletableFuture<BulkAction<K, V, Void>> future) {
    return future.thenApply(action -> null);
  }

  @Override
  public CompletableFuture<Void> reloadAll(Iterable<? extends K> keys) {
    checkLoaderPresent();
    Set<K> keySet = HeapCache.generateKeySet(keys);
    if (asyncLoader != null) {
      return completeWithVoid(asyncBulkOp((Semantic<K, V, Void>) ops.unconditionalLoad, keySet, true));
    } else {
      if (bulkCacheLoader == null) {
        return reloadAllWithSyncLoader(keySet);
      } else {
        return executeSyncBulkOp(ops.unconditionalLoad, keySet, true);
      }
    }
  }

  private CompletableFuture<Void> reloadAllWithSyncLoader(Set<K> keysToLoad) {
    OperationCompletion<K> completion = new OperationCompletion<>(keysToLoad);
    for (K key : keysToLoad) {
      heapCache.executeLoader(completion, key, () -> {
        EntryAction<K, V, V> action = createEntryAction(key, null, ops.unconditionalLoad);
        action.start();
        return action.getException();
      });
    }
    return completion.getFuture();
  }

  protected <R> MyEntryAction<R> createFireAndForgetAction(Entry<K, V> e, Semantic<K, V, R> op) {
    return new MyEntryAction<>(op, e.getKey(), e, EntryAction.NOOP_CALLBACK);
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

  @Override
  public boolean isDisabled() {
    return heapCache.isDisabled();
  }

  @Override
  public void setDisabled(boolean f) {
    heapCache.setDisabled(f);
  }

  V returnValue(Object v) {
    return HeapCache.returnValue(v);
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
   * This takes four different execution paths depending on cache setup and
   * state: no loader and/or all data present in heap, async or async bulk, parallel single load,
   * bulk load.
   */
  @Override
  public Map<K, V> getAll(Iterable<? extends K> requestedKeys) {
    BulkResultCollector<K, V> collect = new BulkResultCollector<>();
    Set<K> keysMissing = getAllPrescreen(requestedKeys, collect);
    if (!keysMissing.isEmpty()) {
      if (asyncLoader != null) {
        getAllAsyncLoad(collect, keysMissing);
      } else {
        if (loader != null) {
          if (bulkCacheLoader == null) {
            getAllConcurrentLoad(collect, keysMissing);
          } else {
            getAllBulkLoad(collect, keysMissing);
          }
        }
      }
    }
    return collect.mapOrThrowIfAllFaulty();
  }

  private void getAllBulkLoad(BulkResultCollector<K, V> collect, Set<K> keysMissing) {
    BulkAction<K, V, V> bulkAction = syncBulkOp(Operations.GET, keysMissing);
    Throwable t = bulkAction.getExceptionToPropagate();
    if (t != null) {
      if (t instanceof RuntimeException) {
        throw (RuntimeException) t;
      } else if (t instanceof Error) {
        throw (Error) t;
      }
      throw new CacheException(t);
    }
    collect.putAll(bulkAction.getActions());
  }

  private void getAllAsyncLoad(BulkResultCollector<K, V> collect, Set<K> keysMissing) {
    try {
      BulkAction<K, V, V> bulkAction =
        asyncBulkOp((Semantic<K, V, V>) Operations.GET, keysMissing, true).get();
      collect.putAll(bulkAction.getActions());
    } catch (InterruptedException ex) {
      CacheOperationInterruptedException.propagate(ex);
    } catch (ExecutionException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof CacheLoaderException) {
        cause.fillInStackTrace();
        throw (CacheLoaderException) cause;
      }
      throw new CacheException(ex.getCause());
    }
  }

  /**
   * Check for fresh data of the requested keys is available in the heap and collect it.
   * The requested keys might contains duplicates, so keep track of already processed
   * keys.
   *
   * @return missing keys that need further processing
   */
  private Set<K> getAllPrescreen(
    Iterable<? extends K> requestedKeys, BulkResultCollector<K, V> collect) {
    Set<K> missingKeys = new HashSet<>();
    Set<K> processedKeys = new HashSet<>();
    for (K key : requestedKeys) {
      if (processedKeys.contains(key)) { continue; }
      Entry<K, V> e = lookupQuick(key);
      if (e != null) {
        if (e.hasFreshData(getClock())) {
          collect.put(key, e.getValueOrException());
        } else {
          metrics().heapHitButNoRead();
          missingKeys.add(key);
        }
      } else {
        missingKeys.add(key);
      }
      processedKeys.add(key);
    }
    return missingKeys;
  }

  /**
   * Improve getAll performance by running multiple loads in parallel as long
   * we have sufficient loader threads.
   */
  private void getAllConcurrentLoad(BulkResultCollector<K, V> collect,
                                    Set<? extends K> keysMissing) {
    Set<EntryAction<K, V, V>> actions = new HashSet<>(keysMissing.size());
    CountDownLatch completion = new CountDownLatch(keysMissing.size());
    EntryAction.CompletedCallback cb = ea -> completion.countDown();
    for (K key : keysMissing) {
      EntryAction<K, V, V> action = new MyEntryAction<>(Operations.GET, key, null, cb);
      actions.add(action);
      heapCache.executeLoader(action);
    }
    try {
      completion.await();
    } catch (InterruptedException ex) {
      CacheOperationInterruptedException.propagate(ex);
    }
    collect.putAll(actions);
  }

  @Override
  public CacheEntry<K, V> getEntry(K key) {
    return execute(key, ops.getEntry(key));
  }

  @Override
  public long getTotalEntryCount() {
    return heapCache.getTotalEntryCount();
  }

  @Override
  public <@Nullable R> Map<K, EntryProcessingResult<R>> invokeAll(
    Iterable<? extends K> keys, EntryProcessor<K, V, R> entryProcessor) {
    if (bulkCacheLoader == null && asyncLoader == null) {
      return super.invokeAll(keys, entryProcessor);
    }
    Set<K> keySet = HeapCache.generateKeySet(keys);
    if (asyncLoader != null) {
      return invokeAllAsyncLoader(entryProcessor, keySet);
    }
    BulkAction<K, V, R> actionResult = syncBulkOp(ops.invoke(entryProcessor), keySet);
    Map<K, EntryProcessingResult<R>> resultMap = new HashMap<>();
    for (EntryAction<K, V, R> action : actionResult.getActions()) {
      EntryProcessingResult<R> singleResult;
      Throwable exception = action.getException();
      if (exception == null) {
        singleResult = EntryProcessingResultFactory.result((R) action.getResult());
      } else {
        singleResult = EntryProcessingResultFactory.exception(exception);
      }
      resultMap.put(action.getKey(), singleResult);
    }
    return resultMap;
  }

  private <@Nullable R> Map<K, EntryProcessingResult<R>> invokeAllAsyncLoader(EntryProcessor<K, V, R> entryProcessor, Set<K> keySet) {
    try {
      Map<K, EntryProcessingResult<R>> resultMap = new HashMap<>();
      BulkAction<K, V, R> bulkAction =
        asyncBulkOp(ops.invoke(entryProcessor), keySet, false).get();
      for (EntryAction<K, V, R> action : bulkAction.getActions()) {
        Throwable ex = action.getException();
        EntryProcessingResult<R> result = null;
        if (ex != null) {
          result = EntryProcessingResultFactory.exception(ex);
        } else if (action.isResultAvailable()) {
          result = EntryProcessingResultFactory.result((R) action.getResult());
        }
        if (result != null) {
          resultMap.put(action.getKey(), result);
        }
      }
      return resultMap;
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new CacheOperationInterruptedException(ex);
    } catch (ExecutionException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof CacheLoaderException) {
        cause.fillInStackTrace();
        throw (CacheLoaderException) cause;
      }
      throw new CacheException(ex.getCause());
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public Iterator<CacheEntry<K, V>> iterator() {
    Iterator<CacheEntry<K, V>> it = new HeapCache.IteratorFilterEntry2Entry(
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
          throw new IllegalStateException("call to next() missing or double remove()");
        }
        WiredCache.this.remove(entry.getKey());
        entry = null;
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
    Map<K, CacheEntry<K, V>> map = new HashMap<>();
    for (K k : keys) {
      CacheEntry<K, V> e = execute(k, ops.peekEntry());
      if (e != null) {
        map.put(k, e);
      }
    }
    return heapCache.convertCacheEntry2ValueMap(map);
  }

  @Override
  public InternalCacheInfo getConsistentInfo() {
    return heapCache.getConsistentInfo(this);
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
    heapCache.checkIntegrity(this);
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
    heapCache.closePart2(this);
    closeCustomization(asyncLoader, "asyncLoader");
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
        try {
          l.onEntryEvicted(getUserCache(), currentEntry);
        } catch (Throwable t) {
          getLog().warn("Exception from eviction listener", t);
        }
      }
    }
  }

  @Override
  protected <R> EntryAction<K, V, R> createEntryAction(K key, Entry<K, V> e, Semantic<K, V, R> op) {
    return new MyEntryAction<>(op, key, e);
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
      long now = heapCache.clock.ticks();
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
        heapCache.getRefreshExecutor().execute(createFireAndForgetAction(e, ops.refresh));
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

  @Override
  public Cache<K, V> getUserCache() {
    return userCache;
  }

  @Override
  public Timing getTiming() {
    return heapCache.getTiming();
  }

  @Override
  public TimeReference getTimeReference() {
    return heapCache.getTimeReference();
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
      return heapCache.getLoaderExecutor();
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
