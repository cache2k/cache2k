package org.cache2k.core;

/*
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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
import org.cache2k.CacheEntry;
import org.cache2k.CacheManager;
import org.cache2k.config.Cache2kConfig;
import org.cache2k.config.CacheType;
import org.cache2k.core.api.InternalCacheBuildContext;
import org.cache2k.core.api.CommonMetrics;
import org.cache2k.core.api.InternalCache;
import org.cache2k.core.api.InternalCacheInfo;
import org.cache2k.core.eviction.Eviction;
import org.cache2k.core.eviction.EvictionMetrics;
import org.cache2k.core.eviction.HeapCacheForEviction;
import org.cache2k.core.operation.ExaminationEntry;
import org.cache2k.core.operation.Semantic;
import org.cache2k.core.operation.Operations;
import org.cache2k.core.concurrency.DefaultThreadFactoryProvider;
import org.cache2k.core.concurrency.ThreadFactoryProvider;

import org.cache2k.core.timing.TimeAgnosticTiming;
import org.cache2k.core.timing.Timing;
import org.cache2k.io.CacheLoader;
import org.cache2k.operation.TimeReference;
import org.cache2k.core.log.Log;
import org.cache2k.core.util.TunableConstants;
import org.cache2k.core.util.TunableFactory;
import org.cache2k.event.CacheClosedListener;
import org.cache2k.io.AdvancedCacheLoader;
import org.cache2k.io.ExceptionPropagator;
import org.cache2k.io.LoadExceptionInfo;
import org.cache2k.processor.EntryProcessor;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.cache2k.core.util.Util.*;

/**
 * Foundation for all cache variants. All common functionality is in here.
 * For a (in-memory) cache we need three things: a fast hash table implementation, an
 * LRU list (a simple double linked list), and a fast timer.
 * The variants implement different eviction strategies.
 *
 * <p>Locking: The cache has a single structure lock obtained via {@link #lock} and also
 * locks on each entry for operations on it. Though, mutation operations that happen on a
 * single entry get serialized.
 *
 * @author Jens Wilke
 */
@SuppressWarnings({"rawtypes", "SynchronizationOnLocalVariableOrMethodParameter"})
public class HeapCache<K, V> extends BaseCache<K, V> implements HeapCacheForEviction<K, V> {

  public static final Tunable TUNABLE = TunableFactory.get(Tunable.class);

  public static final ExceptionPropagator DEFAULT_EXCEPTION_PROPAGATOR =
    TUNABLE.exceptionPropagator;

  protected final String name;
  public final CacheManagerImpl manager;
  protected final AdvancedCacheLoader<K, V> loader;
  protected TimeReference clock;
  @SuppressWarnings("unchecked")
  protected Timing<K, V> timing = TimeAgnosticTiming.ETERNAL;

  /** Used for tests */
  public Timing<K, V> getTiming() {
    return timing;
  }

  /**
   * Structure lock of the cache. Every operation that needs a consistent structure
   * of the cache or modifies it needs to synchronize on this. Since this is a global
   * lock, locking on it should be avoided and any operation under the lock should be
   * quick.
   */
  public final Object lock = new Object();

  /** Statistics */

  protected CacheBaseInfo info;
  final CommonMetrics.Updater metrics;

  /**
   * Counts the number of key mutations. The count is not guarded and racy, but does not need
   * to be exact. We don't put it to the metrics, because we do not want to have this disabled.
   */
  protected volatile long keyMutationCnt = 0;

  protected long clearedTime = 0;
  protected long startedTime;

  Eviction eviction;

  /** Number of entries removed by clear. Guarded by: lock */
  protected long clearRemovedCnt = 0;

  /** Number of clear operations. Guarded by: lock */
  protected long clearCnt = 0;

  /**
   * Number of internal exceptions. Guarded by: lock.
   *
   *  @see InternalCacheInfo#getInternalExceptionCount()
   */
  protected long internalExceptionCnt = 0;

  private final Executor executor;

  private volatile Executor loaderExecutor = new LazyLoaderExecutor();

  public Executor getLoaderExecutor() {
    return loaderExecutor;
  }

  /**
   * Create executor only if needed.
   */
  private class LazyLoaderExecutor implements Executor {
    @Override
    public void execute(Runnable command) {
      synchronized (lock) {
        checkClosed();
        if (loaderExecutor == this) {
          int threadCount = Runtime.getRuntime().availableProcessors();
          loaderExecutor = provideDefaultLoaderExecutor(threadCount);
        }
        loaderExecutor.execute(command);
      }
    }
  }

  private volatile Executor refreshExecutor;

  /**
   * Create executor only if needed.
   */
  private class LazyRefreshExecutor implements Executor {
    @Override
    public void execute(Runnable command) {
      synchronized (lock) {
        checkClosed();
        loaderExecutor.execute(command);
        refreshExecutor = loaderExecutor;
      }
    }
  }

  protected final Hash2<K, V> hash = createHashTable();

  private volatile boolean closing = true;

  protected CacheType keyType;

  protected CacheType valueType;

  @SuppressWarnings("unchecked")
  protected final ExceptionPropagator<K, V> exceptionPropagator;

  Collection<CacheClosedListener> cacheClosedListeners = Collections.emptyList();

  private int featureBits;

  private static final int KEEP_AFTER_EXPIRED = 2;
  private static final int REJECT_NULL_VALUES = 8;
  private static final int BACKGROUND_REFRESH = 16;
  private static final int UPDATE_TIME_NEEDED = 32;
  private static final int RECORD_REFRESH_TIME = 64;

  protected final boolean isKeepAfterExpired() {
    return (featureBits & KEEP_AFTER_EXPIRED) > 0;
  }

  protected final boolean isRejectNullValues() {
    return (featureBits & REJECT_NULL_VALUES) > 0;
  }

  public final boolean isNullValuePermitted() { return !isRejectNullValues(); }

  public final boolean isRefreshAhead() { return (featureBits & BACKGROUND_REFRESH) > 0; }

  /**
   * No need to update the entry last modification time.
   * False, if no time dependent expiry calculations are done.
   */
  protected final boolean isUpdateTimeNeeded() { return (featureBits & UPDATE_TIME_NEEDED) > 0; }

  protected final boolean isRecordRefreshTime() { return (featureBits & RECORD_REFRESH_TIME) > 0; }

  private static int featureBit(int bitmask, boolean flag) {
    return flag ? bitmask : 0;
  }

  /**
   * Returns name of the cache with manager name.
   *
   * @see BaseCache#nameQualifier(Cache) similar
   */
  public String getCompleteName() {
    return manager.getName() + ":" + name;
  }

  /**
   * Normally a cache itself logs nothing, so just construct when needed.
   * Not requesting the log at startup means that the potential log target
   * is not showing up in some management interfaces. This is intentional,
   * since if caches are created and closed dynamically, we would have a
   * memory leak, since logs can not be closed.
   */
  @Override
  public Log getLog() {
    return
      Log.getLog(Cache.class.getName() + '/' + getCompleteName());
  }

  /** called from CacheBuilder */
  @SuppressWarnings("unchecked")
  public HeapCache(InternalCacheBuildContext<K, V> ctx) {
    Cache2kConfig<K, V> cfg = ctx.getConfig();
    valueType = cfg.getValueType();
    keyType = cfg.getKeyType();
    name = cfg.getName();
    manager = (CacheManagerImpl) ctx.getCacheManager();
    clock = ctx.getTimeReference();
    featureBits =
      featureBit(KEEP_AFTER_EXPIRED, cfg.isKeepDataAfterExpired()) |
      featureBit(REJECT_NULL_VALUES, !cfg.isPermitNullValues()) |
      featureBit(BACKGROUND_REFRESH, cfg.isRefreshAhead()) |
      featureBit(UPDATE_TIME_NEEDED, cfg.isRecordModificationTime()) |
      featureBit(RECORD_REFRESH_TIME, cfg.isRecordModificationTime());
    if (cfg.getLoader() != null) {
      Object obj = ctx.createCustomization(cfg.getLoader());
      CacheLoader<K, V> simpleLoader = (CacheLoader) obj;
      loader = (key, startTime, currentEntry) -> simpleLoader.load(key);
    } else if (cfg.getAdvancedLoader() != null) {
      AdvancedCacheLoader<K, V> advanceLoader = ctx.createCustomization(cfg.getAdvancedLoader());
      loader =
        new InternalCache2kBuilder.WrappedAdvancedCacheLoader<K, V>(this, advanceLoader);
    } else {
      loader = null;
    }
    exceptionPropagator =
      ctx.createCustomization(cfg.getExceptionPropagator(), DEFAULT_EXCEPTION_PROPAGATOR);
    metrics = TUNABLE.commonMetricsFactory.create(new CommonMetricsFactory.Parameters() {
      @Override
      public boolean isDisabled() {
        return cfg.isDisableStatistics();
      }

      @Override
      public boolean isPrecise() {
        return false;
      }
    });

    if (cfg.getLoaderExecutor() != null) {
      loaderExecutor = ctx.createCustomization(cfg.getLoaderExecutor());
    } else {
      if (cfg.getLoaderThreadCount() > 0) {
        loaderExecutor = provideDefaultLoaderExecutor(cfg.getLoaderThreadCount());
      }
    }
    refreshExecutor =
      ctx.createCustomization(cfg.getRefreshExecutor(), new LazyRefreshExecutor());
    executor = ctx.getExecutor();
  }

  String getThreadNamePrefix() {
    return "cache2k-loader-" + compactFullName(manager, name);
  }

  Executor provideDefaultLoaderExecutor(int threadCount) {
    int corePoolThreadSize = 0;
    return new ThreadPoolExecutor(corePoolThreadSize, threadCount,
      21, TimeUnit.SECONDS,
      new SynchronousQueue<Runnable>(),
      HeapCache.TUNABLE.threadFactoryProvider.newThreadFactory(getThreadNamePrefix()),
      new ThreadPoolExecutor.AbortPolicy());
  }

  public void setTiming(Timing<K, V> rh) {
    timing = rh;
    if (!(rh instanceof TimeAgnosticTiming)) {
      featureBits = featureBits | featureBit(UPDATE_TIME_NEEDED, true);
    }
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public CacheType getKeyType() { return keyType; }

  @Override
  public CacheType getValueType() { return valueType; }

  public void init() {
    timing.setTarget(this);
    initWithoutTimerHandler();
  }

  public void initWithoutTimerHandler() {
    startedTime = clock.millis();
    if (isRefreshAhead() && timing instanceof TimeAgnosticTiming) {
      throw new IllegalArgumentException("refresh ahead enabled, but no expiry variant defined");
    }
    closing = false;
  }

  public void checkClosed() {
    if (closing) {
      throw new CacheClosedException(this);
    }
  }

  public final void clear() {
    executeWithGlobalLock(new Supplier<Void>() {
      @Override
      public Void get() {
        clearLocalCache();
        return null;
      }
    });
  }

  public final void clearLocalCache() {
    long removed = eviction.removeAll();
    clearRemovedCnt += removed;
    clearCnt++;
    timing.cancelAll();
    hash.clearWhenLocked();
    clearedTime = clock.millis();
  }

  /**
   * Preparation for shutdown. Cancel all pending timer jobs e.g. for
   * expiry/refresh or flushing the storage.
   */
  @Override
  public void cancelTimerJobs() {
    timing.cancelAll();
  }

  @Override
  public boolean isClosed() {
    return closing;
  }

  /**
   *
   * @throws CacheClosedException if cache is closed or closing is initiated by another thread.
   */
  public void closePart1() throws CacheClosedException {
    executeWithGlobalLock((Supplier<Void>) () -> {
      closing = true;
      return null;
    });
    closeCustomization(loaderExecutor, "loaderExecutor");
    cancelTimerJobs();
  }

  @Override
  public void close() {
    try {
      closePart1();
    } catch (CacheClosedException ex) {
      return;
    }
    closePart2(this);
  }

  public void closePart2(InternalCache userCache) {
    executeWithGlobalLock((Supplier<Void>) () -> {
      eviction.close();
      timing.close(HeapCache.this);
      hash.close();
      closeCustomization(loader, "loader");
      for (CacheClosedListener s : cacheClosedListeners) {
        s.onCacheClosed(userCache);
      }
      manager.sendClosedEvent(userCache, userCache);
      manager.cacheClosed(userCache);
      return null;
    }, false);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Iterator<CacheEntry<K, V>> iterator() {
    return new IteratorFilterEntry2Entry(this, iterateAllHeapEntries(), true);
  }

  /**
   * Filter out non valid entries and wrap each entry with a cache
   * entry object.
   */
  static class IteratorFilterEntry2Entry<K, V> implements Iterator<CacheEntry<K, V>> {

    final HeapCache<K, V> cache;
    final Iterator<Entry<K, V>> iterator;
    Entry entry;
    CacheEntry<K, V> lastEntry;
    final boolean filter;

    IteratorFilterEntry2Entry(HeapCache<K, V> c, Iterator<Entry<K, V>> it, boolean filter) {
      cache = c;
      iterator = it;
      this.filter = filter;
    }

    /**
     * Between hasNext() and next() an entry may be evicted or expired.
     * In practise we have to deliver a next entry if we return hasNext() with
     * true, furthermore, there should be no big gap between the calls to
     * hasNext() and next().
     */
    @Override
    public boolean hasNext() {
      if (entry != null) {
        return true;
      }
      if (iterator == null) {
        return false;
      }
      while (iterator.hasNext()) {
        Entry e = iterator.next();
        if (filter) {
          if (e.hasFreshData(cache.getClock())) {
            entry = e;
            return true;
          }
        } else {
          entry = e;
          return true;
        }
      }
      entry = null;
      return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public CacheEntry<K, V> next() {
      if (entry == null && !hasNext()) {
        throw new NoSuchElementException("not available");
      }
      lastEntry = cache.returnEntry(entry);
      entry = null;
      return lastEntry;
    }

    @Override
    public void remove() {
      if (lastEntry == null) {
        throw new IllegalStateException(
          "Unable to remove, hasNext() not called or previously removed");
      }
      cache.remove(lastEntry.getKey());
      lastEntry = null;
    }
  }

  /**
   * Increment the hit counter, because entry was accessed.
   *
   * <p>The hit counter is a dirty counter. In case of multiple CPU cores incrementing the
   * same entry counter, increments will be lost. For the functionality of the eviction algorithm
   * this is not a real loss, since still the most accessed entries will have more counts then the
   * others. On 32 bit systems word tearing may occur. This will also have no real observable
   * negative impact on the eviction, so we do not compensate for it.
   *
   * <p>The hit count is also used for access statistics. The dirty counting will effect
   * the exact correctness of the access statistics.
   *
   * <p>Using a 64 bit counter per entry is basically a big waste of memory. When reducing
   * to a 32 bit value is has approximately a negative performance impact of 30%.
   */
  protected void recordHit(Entry e) {
    e.hitCnt++;
    metrics.heapHit();
  }

  @Override
  public V get(K key) {
    Entry<K, V> e = getEntryInternal(key);
    if (e == null) {
      return null;
    }
    return returnValue(e);
  }

  /**
   * Wrap entry in a separate object instance. We can return the entry directly, however we lock on
   * the entry object.
   */
  protected CacheEntry<K, V> returnEntry(ExaminationEntry<K, V> e) {
    if (e == null) {
      return null;
    }
    return returnCacheEntry(e);
  }

  @Override
  public String getEntryState(K key) {
    Entry e = lookupEntry(key);
    if (e == null) {
      return null;
    }
    synchronized (e) {
      return e.toString(this);
    }
  }

  @Override
  public CacheEntry<K, V> returnCacheEntry(ExaminationEntry<K, V> entry) {
    return returnCacheEntry(entry.getKey(), entry.getValueOrException());
  }


  @SuppressWarnings("unchecked")
  public CacheEntry<K, V> returnCacheEntry(K key, V valueOrException) {
    if (valueOrException instanceof ExceptionWrapper) {
      return (ExceptionWrapper<K, V>) valueOrException;
    }
    return new BaseCacheEntry<K, V>() {
      @Override public K getKey() {
        return key;
      }
      @Override public V getValue() { return valueOrException; }
    };
  }

  abstract static class BaseCacheEntry<K, V> extends AbstractCacheEntry<K, V> {
    @Override
    public Throwable getException() {
      return null;
    }
    @Override
    public LoadExceptionInfo getExceptionInfo() { return null; }
  }

  @Override
  public CacheEntry<K, V> getEntry(K key) {
    return returnEntry(getEntryInternal(key));
  }

  protected Entry<K, V> getEntryInternal(K key) {
    int hc = modifiedHash(key.hashCode());
    return getEntryInternal(key, hc, extractIntKeyValue(key, hc));
  }

  protected Entry<K, V> getEntryInternal(K key, int hc, int val) {
    if (loader == null) {
      return peekEntryInternal(key, hc, val);
    }
    Entry<K, V> e;
    for (;;) {
      e = lookupOrNewEntry(key, hc, val);
      if (e.hasFreshData(clock)) {
        return e;
      }
      synchronized (e) {
        e.waitForProcessing();
        if (e.hasFreshData(clock)) {
          return e;
        }
        if (e.isGone()) {
          metrics.heapHitButNoRead();
          metrics.goneSpin();
          continue;
        }
        e.startProcessing(Entry.ProcessingState.LOAD, null);
        break;
      }
    }
    boolean finished = false;
    try {
      load(e);
      finished = true;
    } finally {
      e.ensureAbort(finished);
    }
    if (e.getValueOrException() == null && isRejectNullValues()) {
      return null;
    }
    return e;
  }

  protected void finishLoadOrEviction(Entry<K, V> e, long nextRefreshTime) {
    if (e.getProcessingState() != Entry.ProcessingState.REFRESH) {
      restartTimer(e, nextRefreshTime);
    } else {
      startRefreshProbationTimer(e, nextRefreshTime);
    }
    e.processingDone();
  }

  private void restartTimer(Entry<K, V> e, long nextRefreshTime) {
    e.setNextRefreshTime(timing.stopStartTimer(nextRefreshTime, e));
    checkIfImmediatelyExpired(e);
  }

  private void checkIfImmediatelyExpired(Entry<K, V> e) {
    if (e.isExpiredState()) {
      expireAndRemoveEventuallyAfterProcessing(e);
    }
  }

  /**
   * Remove the entry from the hash and the replacement list.
   * There is a race condition to catch: The eviction may run
   * in a parallel thread and may have already selected this
   * entry.
   */
  protected boolean removeEntry(Entry<K, V> e) {
    int hc = extractModifiedHash(e);
    boolean removed;
    StampedLock l = hash.getSegmentLock(hc);
    long stamp = l.writeLock();
    try {
      removed = hash.removeWithinLock(e, hc);
      e.setGone();
      if (removed) {
        eviction.submitWithoutTriggeringEviction(e);
      }
    } finally {
      l.unlockWrite(stamp);
    }
    checkForHashCodeChange(e);
    timing.cancelExpiryTimer(e);
    return removed;
  }

  @Override
  public V peekAndPut(K key, V value) {
    int hc = modifiedHash(key.hashCode());
    int val = extractIntKeyValue(key, hc);
    boolean hasFreshData;
    V previousValue = null;
    Entry<K, V> e;
    for (;;) {
      e = lookupOrNewEntry(key, hc, val);
      synchronized (e) {
        e.waitForProcessing();
        if (e.isGone()) {
          metrics.goneSpin();
          continue;
        }
        hasFreshData = e.hasFreshData(clock);
        if (hasFreshData) {
          previousValue = e.getValueOrException();
        } else {
          if (e.isVirgin()) {
            metrics.peekMiss();
          } else {
            metrics.peekHitNotFresh();
          }
        }
        putValue(e, value);
      }
      return returnValue(previousValue);
    }
  }

  @Override
  public V peekAndReplace(K key, V value) {
    Entry<K, V> e;
    for (;;) {
      e = lookupEntry(key);
      if (e == null) { break; }
      synchronized (e) {
        e.waitForProcessing();
        if (e.isGone()) {
          metrics.goneSpin();
          continue;
        }
        if (e.hasFreshData(clock)) {
          V previousValue = e.getValueOrException();
          putValue(e, value);
          return returnValue(previousValue);
        }
        break;
      }
    }
    metrics.peekMiss();
    return null;
  }

  /**
   * Update the value directly within entry lock. Since we did not start
   * entry processing we do not need to notify any waiting threads.
   */
  protected final void putValue(Entry<K, V> e, V value) {
    if (!isUpdateTimeNeeded()) {
      insertOrUpdateAndCalculateExpiry(e, value, 0, 0, 0, INSERT_STAT_PUT);
    } else {
      long t = clock.millis();
      insertOrUpdateAndCalculateExpiry(e, value, t, t, t, INSERT_STAT_PUT);
    }
  }

  @Override
  public boolean replace(K key, V newValue) {
    return replace(key, false, null, newValue);
  }

  @Override
  public boolean replaceIfEquals(K key, V oldValue, V newValue) {
    return replace(key, true, oldValue, newValue);
  }

  /**
   * replace if value matches. if value not matches, return the existing entry or the dummy entry.
   */
  protected boolean replace(K key, boolean compare, V oldValue, V newValue) {
    Entry<K, V> e = lookupEntry(key);
    if (e == null) {
      metrics.peekMiss();
      return false;
    }
    synchronized (e) {
      e.waitForProcessing();
      if (e.isGone() || !e.hasFreshData(clock)) {
        return false;
      }
      if (compare && !e.equalsValue(oldValue)) {
        return false;
      }
      putValue(e, newValue);
    }
    return true;
  }

  /**
   * Return the entry, if it is in the cache, without invoking the
   * cache source.
   *
   * <p>The cache storage is asked whether the entry is present.
   * If the entry is not present, this result is cached in the local
   * cache.
   */
  protected final Entry<K, V> peekEntryInternal(K key) {
    int hc = modifiedHash(key.hashCode());
    return peekEntryInternal(key, hc, extractIntKeyValue(key, hc));
  }

  protected final Entry<K, V> peekEntryInternal(K key, int hc, int val) {
    Entry<K, V> e = lookupEntry(key, hc, val);
    if (e == null) {
      metrics.peekMiss();
      return null;
    }
    if (e.hasFreshData(clock)) {
      return e;
    }
    metrics.peekHitNotFresh();
    return null;
  }

  @Override
  public boolean containsKey(K key) {
    Entry e = lookupEntry(key);
    if (e != null) {
      metrics.heapHitButNoRead();
      return e.hasFreshData(clock);
    }
    return false;
  }

  @Override
  public V peek(K key) {
    Entry<K, V> e = peekEntryInternal(key);
    if (e != null) {
      return returnValue(e);
    }
    return null;
  }

  @Override
  public CacheEntry<K, V> peekEntry(K key) {
    return returnEntry(peekEntryInternal(key));
  }

  /**
   * Code duplicates with {@link Cache#get(Object)}
   */
  @Override
  public V computeIfAbsent(K key, Function<? super K, ? extends V> function) {
    Entry<K, V> e;
    for (;;) {
      e = lookupOrNewEntry(key);
      if (e.hasFreshData(clock)) {
        return returnValue(e);
      }
      synchronized (e) {
        e.waitForProcessing();
        if (e.hasFreshData(clock)) {
          return returnValue(e);
        }
        if (e.isGone()) {
          metrics.goneSpin();
          continue;
        }
        e.startProcessing(Entry.ProcessingState.COMPUTE, null);
        break;
      }
    }
    metrics.peekMiss();
    boolean finished = false;
    long t = 0, t0 = 0;
    V value;
    try {
      if (!isUpdateTimeNeeded()) {
        value = function.apply(key);
      } else {
        t0 = clock.millis();
        value = function.apply(key);
        if (!metrics.isDisabled()) {
          t = clock.millis();
        }
      }
      synchronized (e) {
        insertOrUpdateAndCalculateExpiry(e, value, t0, t, t0, INSERT_STAT_PUT);
        e.processingDone();
      }
      finished = true;
    } finally {
      e.ensureAbort(finished);
    }
    return returnValue(e);
  }

  @Override
  public boolean putIfAbsent(K key, V value) {
    for (;;) {
      Entry<K, V> e = lookupOrNewEntry(key);
      synchronized (e) {
        e.waitForProcessing();
        if (e.isGone()) {
          metrics.goneSpin();
          continue;
        }
        if (e.hasFreshData(clock)) {
          return false;
        }
        metrics.peekMiss();
        putValue(e, value);
        return true;
      }
    }
  }

  @Override
  public void put(K key, V value) {
    for (;;) {
      Entry<K, V> e = lookupOrNewEntry(key);
      synchronized (e) {
        e.waitForProcessing();
        if (e.isGone()) {
          metrics.goneSpin();
          continue;
        }
        if (!e.isVirgin()) {
          metrics.heapHitButNoRead();
        }
        putValue(e, value);
      }
      return;
    }
  }

  @Override
  public boolean containsAndRemove(K key) {
    Entry<K, V> e = lookupEntryNoHitRecord(key);
    if (e == null) {
      return false;
    }
    synchronized (e) {
      e.waitForProcessing();
      if (e.isGone()) {
        return false;
      }
      boolean f = e.hasFreshData(clock);
      removeEntry(e);
      return f;
    }
  }

  /**
   * Remove the object from the cache.
   */
  @Override
  public boolean removeIfEquals(K key, V value) {
    Entry<K, V> e = lookupEntry(key);
    if (e == null) {
      metrics.peekMiss();
      return false;
    }
    synchronized (e) {
      e.waitForProcessing();
      if (e.isGone()) {
        metrics.peekMiss();
        return false;
      }
      boolean f = e.hasFreshData(clock);
      if (f) {
        if (!e.equalsValue(value)) {
          return false;
        }
      } else {
        metrics.peekHitNotFresh();
        return false;
      }
      removeEntry(e);
      return true;
    }
  }

  @Override
  public void remove(K key) {
    containsAndRemove(key);
  }

  public V peekAndRemove(K key) {
    Entry<K, V> e = lookupEntry(key);
    if (e == null) {
      metrics.peekMiss();
      return null;
    }
    synchronized (e) {
      e.waitForProcessing();
      if (e.isGone()) {
        metrics.peekMiss();
        return null;
      }
      V value = null;
      boolean f = e.hasFreshData(clock);
      if (f) {
        value = e.getValueOrException();
      } else {
        metrics.peekHitNotFresh();
      }
      removeEntry(e);
      return returnValue(value);
    }
  }

  public Executor getRefreshExecutor() {
    return refreshExecutor;
  }

  public CompletableFuture<Void> loadAll(Iterable<? extends K> keys) {
    checkLoaderPresent();
    Set<K> keysToLoad = checkAllPresent(keys);
    if (keysToLoad.isEmpty()) { return CompletableFuture.completedFuture(null); }
    OperationCompletion completion = new OperationCompletion(keysToLoad);
    for (K k : keysToLoad) {
      executeLoader(completion, k, () -> extractException(getEntryInternal(k)));
    }
    return completion.getFuture();
  }

  @Override
  public CompletableFuture<Void> reloadAll(Iterable<? extends K> keys) {
    checkLoaderPresent();
    Set<K> keysToLoad = generateKeySet(keys);
    OperationCompletion completion = new OperationCompletion(keysToLoad);
    for (K k : keysToLoad) {
      executeLoader(completion, k, () -> extractException(loadAndReplace(k)));
    }
    return completion.getFuture();
  }

  /** null is legal, if loaded value is immediate*/
  Throwable extractException(Entry<K, V> e) {
    if (e != null) {
      return e.getException();
    }
    return null;
  }

  void executeLoader(OperationCompletion<K> completion, K key, Callable<Throwable> action) {
    Runnable r = () -> {
      Throwable exception;
      try {
        exception = action.call();
      } catch (CacheClosedException ignore) {
        exception = ignore;
      } catch (Throwable internalException) {
        getLog().warn("Loader exception", internalException);
        internalExceptionCnt++;
        exception = internalException;
      }
      completion.complete(key, exception);
    };
    executeLoader(r);
  }

  /**
   * Execute with loader executor and back pressure.
   * In case the execution is rejected because there are not enough threads available, the
   * task is executed in the calling thread to produce back pressure. If callers should never
   * block upon a {@code loadAll} the executor must have a unbound queue.
   */
  public void executeLoader(Runnable r) {
    try {
      loaderExecutor.execute(r);
    } catch (RejectedExecutionException ex) {
      r.run();
    }
  }

  /**
   * Generate a set of unique keys from the iterable. Optimize if its already a
   * set or an collection.
   */
  public static <K> Set<K> generateKeySet(Iterable<? extends K> keys) {
     if (keys instanceof Collection) {
      if (keys instanceof Set) {
        return (Set<K>) keys;
      }
      return new HashSet<K>(((Collection<? extends K>) keys));
    }
    Set<K> keySet = new HashSet<K>();
    for (K k : keys) {
      keySet.add(k);
    }
    return keySet;
  }

  /**
   * Checks for entries being present and fresh. Used by {@code loadAll} and {@code prefetchAll}
   *
   * @param keys keys to check for
   * @return keys not present in the cache
   */
  public Set<K> checkAllPresent(Iterable<? extends K> keys) {
    Set<K> keysToLoad = new HashSet<K>();
    for (K k : keys) {
      Entry<K, V> e = lookupEntryNoHitRecord(k);
      if (e == null || !e.hasFreshData(clock)) {
        keysToLoad.add(k);
      }
    }
    return keysToLoad;
  }

  /**
   * Always fetch the value from the source. That is a copy of getEntryInternal
   * without freshness checks.
   */
  protected Entry<K, V> loadAndReplace(K key) {
    Entry<K, V> e;
    for (;;) {
      e = lookupOrNewEntry(key);
      synchronized (e) {
        e.waitForProcessing();
        if (e.isGone()) {
          metrics.goneSpin();
          continue;
        }
        e.startProcessing(Entry.ProcessingState.LOAD, null);
        break;
      }
    }
    boolean finished = false;
    try {
      load(e);
      finished = true;
    } finally {
      e.ensureAbort(finished);
    }
    return e;
  }

  /**
   * Lookup or create a new entry. The new entry is created, because we need
   * it for locking within the data fetch.
   */
  protected Entry<K, V> lookupOrNewEntry(K key) {
    int hc = modifiedHash(key.hashCode());
    return lookupOrNewEntry(key, hc, extractIntKeyValue(key, hc));
  }

  protected Entry<K, V> lookupOrNewEntry(K key, int hc, int val) {
    Entry<K, V> e = lookupEntry(key, hc, val);
    if (e == null) {
      return insertNewEntry(key, hc, val);
    }
    return e;
  }

  protected Entry<K, V> lookupOrNewEntryNoHitRecord(K key) {
    int hc = modifiedHash(key.hashCode());
    Entry<K, V> e = lookupEntryNoHitRecord(key, hc, extractIntKeyValue(key, hc));
    if (e == null) {
      e = insertNewEntry(key, hc, extractIntKeyValue(key, hc));
    }
    return e;
  }

  @SuppressWarnings("unchecked")
  public static <V> V returnValue(V v) {
    if (v instanceof ExceptionWrapper) {
      throw ((ExceptionWrapper<?, V>) v).generateExceptionToPropagate();
    }
    return v;
  }

  @SuppressWarnings("unchecked")
  protected V returnValue(Entry<K, V> e) {
    V v = e.getValueOrException();
    if (v instanceof ExceptionWrapper) {
      throw ((ExceptionWrapper<K, V>) v).generateExceptionToPropagate();
    }
    return v;
  }

  protected Entry<K, V> lookupEntry(K key) {
    int hc = modifiedHash(key.hashCode());
    return lookupEntry(key, hc, extractIntKeyValue(key, hc));
  }

  protected Entry<K, V> lookupEntryNoHitRecord(K key) {
    int hc = modifiedHash(key.hashCode());
    return lookupEntryNoHitRecord(key, hc, extractIntKeyValue(key, hc));
  }

  protected final Entry<K, V> lookupEntry(K key, int hc, int val) {
    Entry<K, V> e = lookupEntryNoHitRecord(key, hc, val);
    if (e != null) {
      recordHit(e);
      return e;
    }
    return null;
  }

  protected final Entry<K, V> lookupEntryNoHitRecord(K key, int hc, int val) {
    return hash.lookup(extractIntKeyObj(key), hc, val);
  }

  /**
   * Insert new entry in all structures (hash and eviction). The insert at the eviction
   * needs to be done under the same lock, to allow a check of the consistency.
   */
  protected Entry<K, V> insertNewEntry(K key, int hc, int val) {
    Entry<K, V> e = new Entry<K, V>(extractIntKeyObj(key), val);
    Entry<K, V> e2;
    eviction.evictEventuallyBeforeInsertOnSegment(hc);
    StampedLock l = hash.getSegmentLock(hc);
    long stamp = l.writeLock();
    try {
      e2 = hash.insertWithinLock(e, hc, val);
      if (e == e2) {
        eviction.submitWithoutTriggeringEviction(e);
      }
    } finally {
      l.unlockWrite(stamp);
    }
    hash.checkExpand(hc);
    return e2;
  }

  @Override
  public Entry<K, V>[] getHashEntries() {
    return hash.getEntries();
  }

  /**
   * Remove the entry from the hash table. The entry is already removed from the replacement list.
   * Stop the timer, if needed. The remove races with a clear. The clear
   * is not updating each entry state to e.isGone() but just drops the whole hash table instead.
   *
   * <p>With completion of the method the entry content is no more visible. "Nulling" out the key
   * or value of the entry is incorrect, since there can be another thread which is just about to
   * return the entry contents.
   */
  public void removeEntryForEviction(Entry<K, V> e) {
    boolean f = hash.remove(e);
    checkForHashCodeChange(e);
    timing.cancelExpiryTimer(e);
    e.setGone();
  }

  /**
   * Check whether the key was modified during the stay of the entry in the cache.
   * We only need to check this when the entry is removed, since we expect that if
   * the key has changed, the stored hash code in the cache will not match any more and
   * the item is evicted very fast.
   */
  private void checkForHashCodeChange(Entry<K, V> e) {
    K key = extractKeyObj(e);
    if (extractIntKeyValue(key, modifiedHash(key.hashCode())) != e.hashCode) {
      if (keyMutationCnt ==  0) {
        getLog().warn("Key mismatch! Key hashcode changed! keyClass=" +
          e.getKey().getClass().getName());
        String s;
        try {
          s = e.getKey().toString();
          if (s != null) {
            getLog().warn("Key mismatch! key.toString(): " + s);
          }
        } catch (Throwable t) {
          getLog().warn("Key mismatch! key.toString() threw exception", t);
        }
      }
      keyMutationCnt++;
    }
  }

  @SuppressWarnings("unchecked")
  protected void load(Entry<K, V> e) {
    V v;
    long t0 = !isUpdateTimeNeeded() ? 0 : clock.millis();
    long refreshTime = t0;
    if (e.getNextRefreshTime() == Entry.EXPIRED_REFRESHED) {
      if (entryInRefreshProbationAccessed(e, t0)) {
        return;
      }
    }
    try {
      checkLoaderPresent();
      if (e.isVirgin()) {
        v = loader.load(extractKeyObj(e), t0, null);
      } else {
        v = loader.load(extractKeyObj(e), t0, e);
      }
    } catch (Throwable ouch) {
      long t = t0;
      if (!metrics.isDisabled() && isUpdateTimeNeeded()) {
        t = clock.millis();
      }
      loadGotException(e, t0, t, ouch);
      return;
    }
    long t = t0;
    if (!metrics.isDisabled() && isUpdateTimeNeeded()) {
      t = clock.millis();
    }
    insertOrUpdateAndCalculateExpiry(e, v, t0, t, refreshTime, INSERT_STAT_LOAD);
  }

  /**
   * Entry was refreshed before, reset timer and make entry visible again.
   */
  private boolean entryInRefreshProbationAccessed(Entry<K, V> e, long now) {
    long  nrt = e.getRefreshProbationNextRefreshTime();
    if (nrt > now) {
      reviveRefreshedEntry(e, nrt);
      return true;
    }
    return false;
  }

  private void reviveRefreshedEntry(Entry<K, V> e, long nrt) {
    synchronized (e) {
      metrics.refreshedHit();
      finishLoadOrEviction(e, nrt);
    }
  }

  @SuppressWarnings("unchecked")
  private void loadGotException(Entry<K, V> e, long t0, long t, Throwable wrappedException) {
    ExceptionWrapper<K, V> value =
      new ExceptionWrapper(extractKeyObj(e), wrappedException, t0, e, exceptionPropagator);
    long nextRefreshTime = 0;
    boolean suppressException = false;
    try {
      if (e.isValidOrExpiredAndNoException()) {
        nextRefreshTime = timing.suppressExceptionUntil(e, value);
      }
      if (nextRefreshTime > t0) {
        suppressException = true;
      } else {
        nextRefreshTime = timing.cacheExceptionUntil(e, value);
      }
    } catch (Exception ex) {
      resiliencePolicyException(e, t0, t, new ResiliencePolicyException(ex));
      return;
    }
    value = new ExceptionWrapper<K, V>(value, Math.abs(nextRefreshTime));
    synchronized (e) {
      insertUpdateStats(e, (V) value, t0, t, INSERT_STAT_LOAD, nextRefreshTime, suppressException);
      if (suppressException) {
        e.setSuppressedLoadExceptionInformation(value);
      } else {
        if (isRecordRefreshTime()) {
          e.setRefreshTime(t0);
        }
        e.setValueOrException((V) value);
      }
      finishLoadOrEviction(e, nextRefreshTime);
    }
  }

  /**
   * Exception from the resilience policy. We have two exceptions now. One from the loader or
   * the expiry policy, one from the resilience policy. We propagate the more severe one from
   * the resilience policy.
   */
  @SuppressWarnings("unchecked")
  private void resiliencePolicyException(Entry<K, V> e, long t0, long t, Throwable exception) {
    ExceptionWrapper<K, V> value =
      new ExceptionWrapper(extractKeyObj(e), exception, t0, e, exceptionPropagator);
    insert(e, (V) value, t0, t, t0, INSERT_STAT_LOAD, 0);
  }

  private void checkLoaderPresent() {
    if (!isLoaderPresent()) {
      throw new UnsupportedOperationException("loader not set");
    }
  }

  @Override
  public boolean isLoaderPresent() {
    return loader != null;
  }

  @Override
  public boolean isWeigherPresent() {
    return eviction.isWeigherPresent();
  }

  /**
   * Calculate the next refresh time if a timer / expiry is needed and call insert.
   */
  protected final void insertOrUpdateAndCalculateExpiry(Entry<K, V> e, V v, long t0, long t,
                                                        long refreshTime, byte updateStatistics) {
    long nextRefreshTime;
    try {
      nextRefreshTime = timing.calculateNextRefreshTime(e, v, refreshTime);
    } catch (Exception ex) {
      RuntimeException wrappedException = new ExpiryPolicyException(ex);
      if (updateStatistics == INSERT_STAT_LOAD) {
        loadGotException(e, t0, t, wrappedException);
        return;
      }
      insertUpdateStats(e, v, t0, t, updateStatistics, Long.MAX_VALUE, false);
      throw wrappedException;
    }
    insert(e, v, t0, t, refreshTime, updateStatistics, nextRefreshTime);
  }

  static final byte INSERT_STAT_LOAD = 1;
  static final byte INSERT_STAT_PUT = 2;

  public RuntimeException returnNullValueDetectedException() {
    return new NullPointerException("Null values in the cache are not permitted.");
  }

  protected final void insert(Entry<K, V> e, V value, long t0, long t, long refreshTime,
                              byte updateStatistics, long nextRefreshTime) {
    if (updateStatistics == INSERT_STAT_LOAD) {
      if (value == null && isRejectNullValues() && nextRefreshTime != 0) {
        loadGotException(e, t0, t, returnNullValueDetectedException());
        return;
      }
      synchronized (e) {
        if (isRecordRefreshTime()) {
          e.setRefreshTime(refreshTime);
        }
        insertUpdateStats(e, value, t0, t, updateStatistics, nextRefreshTime, false);
        e.setValueOrException(value);
        e.resetSuppressedLoadExceptionInformation();
        finishLoadOrEviction(e, nextRefreshTime);
      }
    } else {
      if (value == null && isRejectNullValues()) {
        throw returnNullValueDetectedException();
      }
      if (isRecordRefreshTime()) {
        e.setRefreshTime(refreshTime);
      }
      e.setValueOrException(value);
      e.resetSuppressedLoadExceptionInformation();
      insertUpdateStats(e, value, t0, t, updateStatistics, nextRefreshTime, false);
      restartTimer(e, nextRefreshTime);
    }
  }

  private void insertUpdateStats(Entry<K, V> e, V value, long t0, long t, byte updateStatistics,
                                 long nextRefreshTime, boolean suppressException) {
    if (updateStatistics == INSERT_STAT_LOAD) {
      if (suppressException) {
        metrics.suppressedException();
      } else {
        if (value instanceof ExceptionWrapper) {
          metrics.loadException();
        }
      }
      long millis = t - t0;
      if (e.isGettingRefresh()) {
        metrics.refresh(millis);
      } else {
        if (e.isVirgin()) {
          metrics.readThrough(millis);
        } else {
          metrics.explicitLoad(millis);
        }
      }
    } else {
      if (nextRefreshTime != 0) {
        metrics.putNewEntry();
      }
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public void timerEventRefresh(Entry<K, V> e, Object task) {
    metrics.timerEvent();
    synchronized (e) {
      if (e.getTask() != task) { return; }
      try {
        refreshExecutor.execute(createFireAndForgetAction(e, Operations.SINGLETON.refresh));
      } catch (RejectedExecutionException ex) {
        metrics.refreshRejected();
        expireOrScheduleFinalExpireEvent(e);
      }
    }
  }

  public void startRefreshProbationTimer(Entry<K, V> e, long nextRefreshTime) {
    boolean expired = timing.startRefreshProbationTimer(e, nextRefreshTime);
    if (expired) {
      expireAndRemoveEventually(e);
    }
  }

  @Override
  public void timerEventProbationTerminated(Entry<K, V> e, Object task) {
    metrics.timerEvent();
    synchronized (e) {
      if (e.getTask() != task) { return; }
      expireEntry(e);
    }
  }

  @Override
  public void logAndCountInternalException(String text, Throwable exception) {
    synchronized (lock) {
      internalExceptionCnt++;
    }
    getLog().warn(text, exception);
  }

  @Override
  public void timerEventExpireEntry(Entry<K, V> e, Object task) {
    metrics.timerEvent();
    synchronized (e) {
      if (e.getTask() != task) { return; }
      expireOrScheduleFinalExpireEvent(e);
    }
  }

  private void expireOrScheduleFinalExpireEvent(Entry<K, V> e) {
    long nrt = e.getNextRefreshTime();
    long t = clock.millis();
    if (t >= Math.abs(nrt)) {
      try {
        expireEntry(e);
      } catch (CacheClosedException ignore) { }
    } else {
      if (nrt < 0) {
        return;
      }
      timing.scheduleFinalTimerForSharpExpiry(e);
      e.setNextRefreshTime(-nrt);
    }
  }

  protected void expireEntry(Entry<K, V> e) {
    if (e.isGone() || e.isExpiredState()) {
      return;
    }
    e.setNextRefreshTime(Entry.EXPIRED);
    expireAndRemoveEventually(e);
  }

  /**
   * Remove expired from heap and increment statistics. The entry is not removed when
   * there is processing going on in parallel.
   *
   * @see #expireAndRemoveEventuallyAfterProcessing(Entry)
   */
  private void expireAndRemoveEventually(Entry<K, V> e) {
    if (isKeepAfterExpired() || e.isProcessing()) {
      metrics.expiredKept();
    } else {
      removeEntry(e);
    }
  }

  /**
   * Remove expired from heap and increment statistics. This is at the end of processing,
   * if entry is immediately expired.
   *
   * @see #expireAndRemoveEventually
   */
  private void expireAndRemoveEventuallyAfterProcessing(Entry<K, V> e) {
    if (isKeepAfterExpired()) {
      metrics.expiredKept();
    } else {
      removeEntry(e);
    }
  }

  /**
   * Returns all cache entries within the heap cache. Entries that
   * are expired or contain no valid data are not filtered out.
   */
  public final ConcurrentEntryIterator<K, V> iterateAllHeapEntries() {
    return new ConcurrentEntryIterator<K, V>(this);
  }

  /**
   * JSR107 bulk interface. The behaviour is compatible to the JSR107 TCK. We also need
   * to be compatible to the exception handling policy, which says that the exception
   * is to be thrown when most specific. So exceptions are only thrown, when the value
   * which has produced an exception is requested from the map.
   */
  public Map<K, V> getAll(Iterable<? extends K> inputKeys) {
    Map<K, V> map = new HashMap<>();
    for (K k : inputKeys) {
      Entry<K, V> e = getEntryInternal(k);
      if (e != null) {
        map.put(extractKeyObj(e), e.getValueOrException());
      }
    }
    return convertValueMap(map);
  }

  public Map<K, V> convertValueMap(Map<K, V> map) {
    return new MapValueConverterProxy<K, V, V>(map) {
      @Override
      protected V convert(V v) {
        return returnValue(v);
      }
    };
  }

  public Map<K, V> convertCacheEntry2ValueMap(Map<K, CacheEntry<K, V>> map) {
    return new MapValueConverterProxy<K, V, CacheEntry<K, V>>(map) {
      @Override
      protected V convert(CacheEntry<K, V> v) {
        return v.getValue();
      }
    };
  }

  public Map<K, V> peekAll(Iterable<? extends K> inputKeys) {
    Map<K, V> map = new HashMap<>();
    for (K k : inputKeys) {
      ExaminationEntry<K, V> e = peekEntryInternal(k);
      if (e != null) {
        map.put(k, e.getValueOrException());
      }
    }
    return convertValueMap(map);
  }

  public void putAll(Map<? extends K, ? extends V> valueMap) {
    for (Map.Entry<? extends K, ? extends V> e : valueMap.entrySet()) {
      put(e.getKey(), e.getValue());
    }
  }

  @SuppressWarnings("unchecked")
  Operations<K, V> spec() { return Operations.SINGLETON; }

  @Override
  protected <R> EntryAction<K, V, R> createEntryAction(K key, Entry<K, V> e, Semantic<K, V, R> op) {
    return new MyEntryAction<R>(op, key, e);
  }

  @Override
  protected <R> MyEntryAction<R> createFireAndForgetAction(Entry<K, V> e, Semantic<K, V, R> op) {
    return new MyEntryAction<R>(op, e.getKey(), e, EntryAction.NOOP_CALLBACK);
  }

  @Override
  public Executor getExecutor() {
    return executor;
  }

  class MyEntryAction<R> extends EntryAction<K, V, R> {

    MyEntryAction(Semantic<K, V, R> op, K k, Entry<K, V> e) {
      super(HeapCache.this, HeapCache.this, op, k, e);
    }

    MyEntryAction(Semantic<K, V, R> op, K k,
                         Entry<K, V> e, CompletedCallback cb) {
      super(HeapCache.this, HeapCache.this, op, k, e, cb);
    }

    @Override
    protected Timing<K, V> timing() {
      return timing;
    }

    @Override
    public Executor getLoaderExecutor() {
      return null;
    }

    @Override
    protected Executor executor() { return executor; }

    @Override
    public ExceptionPropagator getExceptionPropagator() {
      return exceptionPropagator;
    }

  }

  /**
   * Simply the {@link EntryAction} based code to provide the entry processor. If we code it
   * directly this might be a little bit more efficient, but it gives quite a code bloat which has
   * lots of corner cases for loader and exception handling.
   */
  @Override
  public <R> R invoke(K key, EntryProcessor<K, V, R> processor) {
    if (key == null) {
      throw new NullPointerException();
    }
    return execute(key, spec().invoke(processor));
  }

  public final long getLocalSize() {
    return hash.getSize();
  }

  public final int getTotalEntryCount() {
    return executeWithGlobalLock(() -> (int) getLocalSize());
  }

  protected IntegrityState getIntegrityState() {
    EvictionMetrics em = eviction.getMetrics();
    IntegrityState is = new IntegrityState()
      .checkEquals("hash.getSize() == hash.calcEntryCount()",
        hash.getSize(),
        hash.calcEntryCount());
    if (em.getEvictionRunningCount() > 0) {
      is.check("eviction running: hash.getSize() == eviction.getSize()", true)
        .check("eviction running: newEntryCnt == hash.getSize() + evictedCnt ....", true);
    } else {
      is.checkEquals("hash.getSize() == eviction.getSize()", getLocalSize(), em.getSize())
        .checkEquals("newEntryCnt == hash.getSize() + evictedCnt + expiredRemoveCnt + " +
            "removeCnt + clearedCnt + virginRemovedCnt",
          em.getNewEntryCount(), getLocalSize() + em.getEvictedCount() +
            em.getExpiredRemovedCount() + em.getRemovedCount() + clearRemovedCnt +
            em.getVirginRemovedCount());
    }
    eviction.checkIntegrity(is);
    return is;
  }

  /**
   * Check internal data structures and throw and exception if something is wrong, used for unit
   * testing
   */
  public final void checkIntegrity() {
    executeWithGlobalLock((Supplier<Void>) () -> {
      IntegrityState is = getIntegrityState();
      if (is.getStateFlags() > 0) {
        throw new Error(
          "cache2k integrity error: " +
          is.getStateDescriptor() + ", " + is.getFailingChecks() + ", " +
            generateInfoUnderLock(HeapCache.this, clock.millis()).toString());
      }
      return null;
    });
  }

  @Override
  public final InternalCacheInfo getInfo() {
    return getInfo(this);
  }

  @Override
  public final InternalCacheInfo getLatestInfo() {
    return getLatestInfo(this);
  }

  public final InternalCacheInfo getInfo(InternalCache userCache) {
    synchronized (lock) {
      long t = clock.millis();
      if (info != null &&
        (info.getInfoCreatedTime() + info.getInfoCreationDeltaMs() *
          TUNABLE.minimumStatisticsCreationTimeDeltaFactor +
          TUNABLE.minimumStatisticsCreationDeltaMillis > t)) {
        return info;
      }
      info = generateInfo(userCache, t);
    }
    return info;
  }

  public final InternalCacheInfo getLatestInfo(InternalCache userCache) {
    return generateInfo(userCache, clock.millis());
  }

  private CacheBaseInfo generateInfo(InternalCache userCache, long t) {
    return executeWithGlobalLock(() -> generateInfoUnderLock(userCache, t));
  }

  private CacheBaseInfo generateInfoUnderLock(InternalCache userCache, long t) {
    info = new CacheBaseInfo(HeapCache.this, userCache, t);
    info.setInfoCreationDeltaMs((int) (clock.millis() - t));
    return info;
  }

  @Override
  public CommonMetrics getCommonMetrics() {
    return metrics;
  }

  /** Internal marker object */
  private static final Object RESTART_AFTER_EVICTION = new Object();

  /**
   * Execute job while making sure that no other operations are going on.
   * In case the eviction is connected via a queue we need to stop the queue processing.
   * On the other hand we needs to make sure that the queue is drained because this
   * method is used to access the recent statistics or check integrity. Draining the queue
   * is a two phase job: The draining may not do eviction since we hold the locks, after
   * lifting the lock with do eviction and lock again. This ensures that all
   * queued entries are processed up to the point when the method was called.
   */
  public <T> T executeWithGlobalLock(Supplier<T> job) {
    return executeWithGlobalLock(job, true);
  }

  /**
   * Execute job while making sure that no other operations are going on.
   * In case the eviction is connected via a queue we need to stop the queue processing.
   * On the other hand we needs to make sure that the queue is drained because this
   * method is used to access the recent statistics or check integrity. Draining the queue
   * is a two phase job: The draining may not do eviction since we hold the locks, after
   * lifting the lock with do eviction and lock again. This ensures that all
   * queued entries are processed up to the point when the method was called.
   *
   * @param checkClosed variant, this method is needed once without check during the close itself
   */
  private <T> T executeWithGlobalLock(Supplier<T> job, boolean checkClosed) {
    synchronized (lock) {
      if (checkClosed) { checkClosed(); }
      eviction.stop();
      try {
        T result = hash.runTotalLocked(() -> {
          if (checkClosed) { checkClosed(); }
          boolean f = eviction.drain();
          if (f) {
            return (T) RESTART_AFTER_EVICTION;
          }
          return eviction.runLocked(() -> job.get());
        });
        if (result == RESTART_AFTER_EVICTION) {
          eviction.evictEventuallyBeforeInsert();
          result = hash.runTotalLocked(() -> {
            if (checkClosed) { checkClosed(); }
            eviction.drain();
            return eviction.runLocked(() -> job.get());
          });
        }
        return result;
      } finally {
        eviction.start();
      }
    }
  }

  @Override
  public final TimeReference getClock() {
    return clock;
  }

  @Override
  public final Eviction getEviction() { return eviction; }

  @Override
  public CacheManager getCacheManager() {
    return manager;
  }

  /**
   * This function calculates a modified hash code. The intention is to
   * "rehash" the incoming integer hash codes to overcome weak hash code
   * implementations. Identical to latest Java VMs.
   *
   * <p>The mapping needs to be unique, so we can skip equals() check for
   * integer key optimizations.
   *
   * @see java.util.HashMap#hash
   * @see java.util.concurrent.ConcurrentHashMap#spread
   */
  @SuppressWarnings("JavadocReference")
  public static int modifiedHash(int h) {
    return h ^ h >>> 16;
  }

  /**
   * Modified hash code or integer value for integer keyed caches
   */
  public int extractIntKeyValue(K key, int hc) {
    return hc;
  }

  /**
   * The key object or null, for integer keyed caches
   */
  public K extractIntKeyObj(K key) {
    return key;
  }

  public int extractModifiedHash(Entry e) {
    return e.hashCode;
  }

  public K extractKeyObj(Entry<K, V> e) { return e.getKeyObj(); }

  public Hash2<K, V> createHashTable() {
    return new Hash2<K, V>(this);
  }

  public static class Tunable extends TunableConstants {

    /**
     * When sharp expiry is enabled, the expiry timer goes
     * before the actual expiry to switch back to a time checking
     * scheme when the cache is accessed. This prevents
     * that an expired value gets served by the cache when the time
     * is too late. Experiments showed that a value of one second is
     * usually sufficient.
     *
     * <p>OS scheduling is not reliable on virtual servers (e.g. KVM)
     * to give the expiry task compute time on a busy server. To be safe in
     * extreme cases, this parameter is set to a high value.
     */
    public long sharpExpirySafetyGapMillis = 27 * 1000 + 127;

     /**
     * Some statistic values need processing time to gather and compute it. This is a safety
     * time delta, to ensure that the machine is not busy due to statistics generation.
      * Default: 333.
     */
    public int minimumStatisticsCreationDeltaMillis = 333;

    /**
     *  Factor of the statistics creation time, that determines the time difference when new
     *  statistics are generated.
     */
    public int minimumStatisticsCreationTimeDeltaFactor = 123;

    public ThreadFactoryProvider threadFactoryProvider = new DefaultThreadFactoryProvider();

    public StandardCommonMetricsFactory commonMetricsFactory =
      new StandardCommonMetricsFactory();

    public ExceptionPropagator exceptionPropagator = new StandardExceptionPropagator();

    /**
     * Override parameter for segment count. Has to be power of two, e.g. 2, 4, 8, etc.
     * Invalid numbers will be replaced by the next higher power of two. Default is 0, no override.
     */
    public int segmentCountOverride = 0;

    public long timerLagMillis = 1003;
  }

}
