package org.cache2k.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2017 headissue GmbH, Munich
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

import org.cache2k.*;
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.configuration.CacheType;
import org.cache2k.configuration.CustomizationSupplier;
import org.cache2k.core.operation.ExaminationEntry;
import org.cache2k.core.operation.ReadOnlyCacheEntry;
import org.cache2k.core.operation.Semantic;
import org.cache2k.core.operation.Operations;
import org.cache2k.core.concurrency.DefaultThreadFactoryProvider;
import org.cache2k.core.concurrency.Job;
import org.cache2k.core.concurrency.OptimisticLock;
import org.cache2k.core.concurrency.ThreadFactoryProvider;

import org.cache2k.core.util.Log;
import org.cache2k.core.util.TunableConstants;
import org.cache2k.core.util.TunableFactory;
import org.cache2k.event.CacheClosedListener;
import org.cache2k.integration.AdvancedCacheLoader;
import org.cache2k.integration.CacheLoaderException;
import org.cache2k.integration.ExceptionPropagator;
import org.cache2k.processor.EntryProcessor;

import java.io.Closeable;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

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
 * @author Jens Wilke; created: 2013-07-09
 */
@SuppressWarnings({"unchecked", "SynchronizationOnLocalVariableOrMethodParameter", "WeakerAccess"})
public class HeapCache<K, V>
  extends BaseCache<K, V> {

  static final CacheOperationCompletionListener DUMMY_LOAD_COMPLETED_LISTENER = new CacheOperationCompletionListener() {
    @Override
    public void onCompleted() {

    }

    @Override
    public void onException(final Throwable _exception) {

    }
  };

  static final Random SEED_RANDOM = new Random(new SecureRandom().nextLong());
  static int cacheCnt = 0;

  public static final Tunable TUNABLE = TunableFactory.get(Tunable.class);

  final static ExceptionPropagator DEFAULT_EXCEPTION_PROPAGATOR = TUNABLE.exceptionPropagator;

  protected final int hashSeed;

  {
    if (TUNABLE.disableHashRandomization) {
      hashSeed = TUNABLE.hashSeed;
    } else {
      hashSeed = SEED_RANDOM.nextInt();
    }
  }

  protected String name;
  public CacheManagerImpl manager;
  protected AdvancedCacheLoader<K,V> loader;
  protected TimingHandler<K,V> timing = TimingHandler.ETERNAL;

  /**
   * Structure lock of the cache. Every operation that needs a consistent structure
   * of the cache or modifies it needs to synchronize on this. Since this is a global
   * lock, locking on it should be avoided and any operation under the lock should be
   * quick.
   */
  public final Object lock = new Object();

  /** Statistics */

  protected CacheBaseInfo info;
  CommonMetrics.Updater metrics;

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

  protected volatile Executor loaderExecutor = new LazyLoaderExecutor();

  /**
   * Create executor only if needed.
   */
  private class LazyLoaderExecutor implements Executor {
    @Override
    public void execute(final Runnable _command) {
      synchronized (lock) {
        checkClosed();
        if (loaderExecutor == this) {
          int _threadCount = Runtime.getRuntime().availableProcessors() * HeapCache.TUNABLE.loaderThreadCountCpuFactor;
          loaderExecutor = provideDefaultLoaderExecutor(_threadCount);
        }
        loaderExecutor.execute(_command);
      }
    }
  }

  protected volatile Executor prefetchExecutor = new LazyPrefetchExecutor();

  /**
   * Create executor only if needed.
   */
  private class LazyPrefetchExecutor implements Executor {
    @Override
    public void execute(final Runnable _command) {
      synchronized (lock) {
        checkClosed();
        loaderExecutor.execute(_command);
        prefetchExecutor = loaderExecutor;
      }
    }
  }

  protected final Hash2<K,V> hash = new Hash2<K,V>();

  private volatile boolean closing = true;

  protected CacheType keyType;

  protected CacheType valueType;

  protected ExceptionPropagator exceptionPropagator = DEFAULT_EXCEPTION_PROPAGATOR;

  private Collection<CustomizationSupplier<CacheClosedListener>> cacheClosedListeners = Collections.EMPTY_LIST;

  private int featureBits = 0;

  private static final int KEEP_AFTER_EXPIRED = 2;
  private static final int REJECT_NULL_VALUES = 8;
  private static final int BACKGROUND_REFRESH = 16;
  private static final int NO_LAST_MODIFICATION_TIME = 32;

  protected final boolean hasKeepAfterExpired() {
    return (featureBits & KEEP_AFTER_EXPIRED) > 0;
  }

  protected final boolean hasRejectNullValues() {
    return (featureBits & REJECT_NULL_VALUES) > 0;
  }

  public final boolean isNullValuePermitted() { return !hasRejectNullValues(); }

  protected final boolean hasBackgroundRefresh() { return (featureBits & BACKGROUND_REFRESH) > 0; }

  protected final boolean isNoLastModificationTime() { return (featureBits & NO_LAST_MODIFICATION_TIME) > 0; }

  protected final void setFeatureBit(int _bitmask, boolean _flag) {
    if (_flag) {
      featureBits |= _bitmask;
    } else {
      featureBits &= ~_bitmask;
    }
  }

  /**
   * Returns name of the cache with manager name.
   */
  public String getCompleteName() {
    if (manager != null) {
      return manager.getName() + ":" + name;
    }
    return name;
  }

  /**
   * Normally a cache itself logs nothing, so just construct when needed.
   */
  @Override
  public Log getLog() {
    return
      Log.getLog(Cache.class.getName() + '/' + getCompleteName());
  }

  /** called from CacheBuilder */
  public void setCacheConfig(final Cache2kConfiguration c) {
    valueType = c.getValueType();
    keyType = c.getKeyType();
    if (name != null) {
      throw new IllegalStateException("already configured");
    }
    setName(c.getName());
    setFeatureBit(KEEP_AFTER_EXPIRED, c.isKeepDataAfterExpired());
    setFeatureBit(REJECT_NULL_VALUES, !c.isPermitNullValues());
    setFeatureBit(BACKGROUND_REFRESH, c.isRefreshAhead());
    setFeatureBit(NO_LAST_MODIFICATION_TIME, c.isDisableLastModificationTime());

    metrics = TUNABLE.commonMetricsFactory.create(new CommonMetricsFactory.Parameters() {
      @Override
      public boolean isDisabled() {
        return c.isDisableStatistics();
      }

      @Override
      public boolean isPrecise() {
        return false;
      }
    });

    if (c.getLoaderExecutor() != null) {
      loaderExecutor = createCustomization((CustomizationSupplier<Executor>) c.getLoaderExecutor());
    } else {
      if (c.getLoaderThreadCount() > 0) {
        loaderExecutor = provideDefaultLoaderExecutor(c.getLoaderThreadCount());
      }
    }
    if (c.getPrefetchExecutor() != null) {
      prefetchExecutor = createCustomization((CustomizationSupplier<Executor>) c.getPrefetchExecutor());
    }
  }

  String getThreadNamePrefix() {
    return "cache2k-loader-" + compactFullName(manager, name);
  }

  Executor provideDefaultLoaderExecutor(int _threadCount) {
    return new ExclusiveExecutor(_threadCount, getThreadNamePrefix());
  }

  public void setTiming(final TimingHandler<K,V> rh) {
    timing = rh;
    if (!(rh instanceof TimingHandler.EternalImmediate)) {
      setFeatureBit(NO_LAST_MODIFICATION_TIME, false);
    }
  }

  public void setExceptionPropagator(ExceptionPropagator ep) {
    exceptionPropagator = ep;
  }

  public void setAdvancedLoader(final AdvancedCacheLoader<K,V> al) {
    loader = al;
  }

  /**
   * Set the name and configure a logging, used within cache construction.
   */
  public void setName(String n) {
    if (n == null) {
      n = this.getClass().getSimpleName() + "#" + cacheCnt++;
    }
    name = n;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setCacheManager(CacheManagerImpl cm) {
    manager = cm;
  }

  @Override
  public CacheType getKeyType() { return keyType; }

  @Override
  public CacheType getValueType() { return valueType; }

  public void init() {
    synchronized (lock) {
      if (name == null) {
        name = String.valueOf(cacheCnt++);
      }
      timing.init(this);
      initializeHeapCache();
      if (hasBackgroundRefresh() &&
        loader == null) {
        throw new IllegalArgumentException("backgroundRefresh, but no loader defined");
      }
      closing = false;
    }
  }

  public void checkClosed() {
    if (closing) {
      throw new CacheClosedException();
    }
  }

  public final void clear() {
    executeWithGlobalLock(new Job<Void>() {
      @Override
      public Void call() {
        clearLocalCache();
        return null;
      }
    });
  }

  public final void clearLocalCache() {
    long _removed = eviction.removeAll();
    clearRemovedCnt += _removed;
    clearCnt++;
    initializeHeapCache();
    hash.clearWhenLocked();
    clearedTime = System.currentTimeMillis();
  }

  protected void initializeHeapCache() {
    if (startedTime == 0) {
      startedTime = System.currentTimeMillis();
    }
    timing.reset();
  }

  /**
   * Preparation for shutdown. Cancel all pending timer jobs e.g. for
   * expiry/refresh or flushing the storage.
   */
  @Override
  public void cancelTimerJobs() {
    timing.close();
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
    executeWithGlobalLock(new Job<Void>() {
      @Override
      public Void call() {
        closing = true;
        return null;
      }
    });
    closeIfNeeded(loaderExecutor, "loaderExecutor");
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

  public void closePart2(final InternalCache _userCache) {
    executeWithGlobalLock(new Job<Void>() {
      @Override
      public Void call() {
        eviction.close();
        timing.shutdown();
        hash.close();
        closeCustomization(loader);
        for (CustomizationSupplier<CacheClosedListener> s : cacheClosedListeners) {
          createCustomization(s).onCacheClosed(_userCache);
        }
        manager.cacheDestroyed(_userCache);
        return null;
      }
    }, false);
  }

  public void setCacheClosedListeners(final Collection<CustomizationSupplier<CacheClosedListener>> l) {
    cacheClosedListeners = l;
  }

  private void closeIfNeeded(Object obj, String _name) {
    if (obj instanceof Closeable) {
      try {
        ((Closeable) obj).close();
      } catch (Throwable t) {
        getLog().warn("Exception when closing " + _name, t);
      }
    }
  }

  @Override
  public Iterator<CacheEntry<K, V>> iterator() {
    return new IteratorFilterEntry2Entry(this, iterateAllHeapEntries(), true);
  }

  /**
   * Filter out non valid entries and wrap each entry with a cache
   * entry object.
   */
  static class IteratorFilterEntry2Entry<K,V> implements Iterator<CacheEntry<K, V>> {

    HeapCache<K,V> cache;
    Iterator<Entry<K,V>> iterator;
    Entry entry;
    CacheEntry<K, V> lastEntry;
    boolean filter = true;

    IteratorFilterEntry2Entry(HeapCache<K,V> c, Iterator<Entry<K,V>> it, boolean _filter) {
      cache = c;
      iterator = it;
      filter = _filter;
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
          if (e.hasFreshData()) {
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
        throw new IllegalStateException("Unable to remove, hasNext() / next() not called or end of iteration reached");
      }
      cache.remove(lastEntry.getKey());
    }
  }

  /**
   * Increment the hit counter, because entry was accessed.
   *
   * <p>The hit counter is a dirty counter. In case of multiple CPU cores incrementing the
   * same entry counter, increments will be lost. For the functionality of the eviction algorithm this
   * is not a real loss, since still the most accessed entries will have more counts then the
   * others. On 32 bit systems word tearing may occur. This will also have no real observable negative
   * impact on the eviction, so we do not compensate for it.
   *
   * <p>The hit count is also used for access statistics. The dirty counting will effect
   * the exact correctness of the access statistics.
   *
   * <p>Using a 64 bit counter per entry is basically a big waste of memory. When reducing
   * to a 32 bit value is has approximately a negative performance impact of 30%.
   */
  protected void recordHit(Entry e) {
    e.hitCnt++;
  }

  @Override
  public V get(K key) {
    Entry<K,V> e = getEntryInternal(key);
    if (e == null) {
      return null;
    }
    return (V) returnValue(e);
  }

  /**
   * Wrap entry in a separate object instance. We can return the entry directly, however we lock on
   * the entry object.
   */
  protected CacheEntry<K, V> returnEntry(final ExaminationEntry<K, V> e) {
    if (e == null) {
      return null;
    }
    synchronized (e) {
      return returnCacheEntry(e.getKey(), e.getValueOrException(), e.getLastModification());
    }
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

  protected CacheEntry<K, V> returnCacheEntry(final K _key, final V _valueOrException, final long _lastModification) {
    CacheEntry<K, V> ce = new CacheEntry<K, V>() {
      @Override
      public K getKey() {
        return _key;
      }

      @Override
      public V getValue() {
        if (_valueOrException instanceof ExceptionWrapper) {
          throw exceptionPropagator.propagateException(_key, (ExceptionWrapper) _valueOrException);
        }
        return _valueOrException;
      }

      @Override
      public Throwable getException() {
        if (_valueOrException instanceof ExceptionWrapper) {
          return ((ExceptionWrapper) _valueOrException).getException();
        }
        return null;
      }

      @Override
      public long getLastModification() {
        return _lastModification;
      }

      @Override
      public String toString() {
        return "CacheEntry(" +
            "key=" + getKey() +
            ((getException() != null) ? ", exception=" + getException() + ", " : ", value=" + getValue()) +
            ", modified=" + formatMillis(getLastModification());
      }

    };
    return ce;
  }

  @Override
  public CacheEntry<K, V> getEntry(K key) {
    return returnEntry(getEntryInternal(key));
  }

  protected Entry getEntryInternal(K key) {
    if (loader == null) {
      return peekEntryInternal(key);
    }
    Entry e;
    for (;;) {
      e = lookupOrNewEntry(key);
      if (e.hasFreshData()) {
        return e;
      }
      synchronized (e) {
        e.waitForProcessing();
        if (e.hasFreshData()) {
          return e;
        }
        if (e.isGone()) {
          metrics.goneSpin();
          continue;
        }
        e.startProcessing();
        break;
      }
    }
    boolean _finished = false;
    try {
      load(e);
      _finished = true;
    } finally {
      e.ensureAbort(_finished);
    }
    if (e.getValueOrException() == null && hasRejectNullValues()) {
      return null;
    }
    return e;
  }

  protected void finishLoadOrEviction(Entry e, long _nextRefreshTime) {
    if (e.getProcessingState() != Entry.ProcessingState.REFRESH) {
      restartTimer(e, _nextRefreshTime);
    } else {
      startRefreshProbationTimer(e, _nextRefreshTime);
    }
    e.processingDone();
  }

  private void restartTimer(final Entry e, final long _nextRefreshTime) {
    e.setNextRefreshTime(timing.stopStartTimer(_nextRefreshTime, e));
    checkIfImmediatelyExpired(e);
  }

  private void checkIfImmediatelyExpired(final Entry e) {
    if (e.isExpired()) {
      expireAndRemoveEventuallyAfterProcessing(e);
    }
  }

  /**
   * Remove the entry from the hash and the replacement list.
   * There is a race condition to catch: The eviction may run
   * in a parallel thread and may have already selected this
   * entry.
   */
  protected boolean removeEntry(Entry e) {
    int hc = e.hashCode;
    boolean _removed;
    OptimisticLock l = hash.getSegmentLock(hc);
    long _stamp = l.writeLock();
    try {
      _removed = hash.removeWithinLock(e, hc);
      e.setGone();
      if (_removed) {
        eviction.submitWithoutEviction(e);
      }
    } finally {
      l.unlockWrite(_stamp);
    }
    checkForHashCodeChange(e);
    timing.cancelExpiryTimer(e);
    return _removed;
  }

  @Override
  public V peekAndPut(K key, V _value) {
    final int hc = modifiedHash(key.hashCode());
    boolean _hasFreshData;
    V _previousValue = null;
    Entry e;
    for (;;) {
      e = lookupOrNewEntry(key, hc);
      synchronized (e) {
        e.waitForProcessing();
        if (e.isGone()) {
          metrics.goneSpin();
          continue;
        }
        _hasFreshData = e.hasFreshData();
        if (_hasFreshData) {
          _previousValue = (V) e.getValueOrException();
        } else {
          if (e.isVirgin()) {
            metrics.peekMiss();
          } else {
            metrics.peekHitNotFresh();
          }
        }
        putValue(e, _value);
      }
      return returnValue(_previousValue);
    }
  }

  @Override
  public V peekAndReplace(K key, V _value) {
    Entry e;
    for (;;) {
      e = lookupEntry(key);
      if (e == null) { break; }
      synchronized (e) {
        e.waitForProcessing();
        if (e.isGone()) {
          metrics.goneSpin();
          continue;
        }
        if (e.hasFreshData()) {
          V _previousValue = (V) e.getValueOrException();
          putValue(e, _value);
          return returnValue(_previousValue);
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
  private void putValue(final Entry e, final V _value) {
    if (isNoLastModificationTime()) {
      insertOrUpdateAndCalculateExpiry(e, _value, 0, 0, INSERT_STAT_PUT);
    } else {
      long t = System.currentTimeMillis();
      insertOrUpdateAndCalculateExpiry(e, _value, t, t, INSERT_STAT_PUT);
    }
  }

  @Override
  public boolean replace(K key, V _newValue) {
    return replace(key, false, null, _newValue) == null;
  }

  @Override
  public boolean replaceIfEquals(K key, V _oldValue, V _newValue) {
    return replace(key, true, _oldValue, _newValue) == null;
  }

  @Override
  public CacheEntry<K, V> replaceOrGet(K key, V _oldValue, V _newValue, CacheEntry<K, V> _dummyEntry) {
    Entry e = replace(key, true, _oldValue, _newValue);
    if (e == DUMMY_ENTRY_NO_REPLACE) {
      return _dummyEntry;
    } else if (e != null) {
      return returnEntry(e);
    }
    return null;
  }

  final Entry DUMMY_ENTRY_NO_REPLACE = new org.cache2k.core.Entry(null, 4711);

  /**
   * replace if value matches. if value not matches, return the existing entry or the dummy entry.
   */
  protected Entry<K, V> replace(final K key, final boolean _compare, final V _oldValue, final V _newValue) {
    Entry e = lookupEntry(key);
    if (e == null) {
      metrics.peekMiss();
      return DUMMY_ENTRY_NO_REPLACE;
    }
    synchronized (e) {
      e.waitForProcessing();
      if (e.isGone() || !e.hasFreshData()) {
        return (Entry) DUMMY_ENTRY_NO_REPLACE;
      }
      if (_compare && !e.equalsValue(_oldValue)) {
        return e;
      }
      putValue(e, _newValue);
    }
    return null;
  }

  /**
   * Return the entry, if it is in the cache, without invoking the
   * cache source.
   *
   * <p>The cache storage is asked whether the entry is present.
   * If the entry is not present, this result is cached in the local
   * cache.
   */
  final protected Entry<K, V> peekEntryInternal(K key) {
    Entry e = lookupEntry(key);
    if (e == null) {
      metrics.peekMiss();
      return null;
    }
    if (e.hasFreshData()) {
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
      if (e.hasFreshData()) {
        return true;
      }
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
  public V computeIfAbsent(final K key, final Callable<V> callable) {
    Entry<K,V> e;
    for (;;) {
      e = lookupOrNewEntry(key);
      if (e.hasFreshData()) {
        return returnValue(e);
      }
      synchronized (e) {
        e.waitForProcessing();
        if (e.hasFreshData()) {
          return returnValue(e);
        }
        if (e.isGone()) {
          metrics.goneSpin();
          continue;
        }
        e.startProcessing();
        break;
      }
    }
    metrics.peekMiss();
    boolean _finished = false;
    long t = 0, t0 = 0;
    V _value;
    try {
      if (isNoLastModificationTime()) {
        _value = callable.call();
      } else {
        t0 = System.currentTimeMillis();
        _value = callable.call();
        t = System.currentTimeMillis();
      }
      synchronized (e) {
        insertOrUpdateAndCalculateExpiry(e, _value, t0, t, INSERT_STAT_PUT);
        e.processingDone();
      }
      _finished = true;
    } catch (Exception ex) {
      throw new CacheLoaderException(ex);
    } finally {
      e.ensureAbort(_finished);
    }
    return returnValue(e);
  }

  @Override
  public boolean putIfAbsent(K key, V value) {
    for (;;) {
      Entry e = lookupOrNewEntry(key);
      synchronized (e) {
        e.waitForProcessing();
        if (e.isGone()) {
          metrics.goneSpin();
          continue;
        }
        if (e.hasFreshData()) {
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
      Entry e = lookupOrNewEntry(key);
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
  public boolean removeIfEquals(K key, V _value) {
    return removeWithFlag(key, true, _value);
  }

  @Override
  public boolean containsAndRemove(K key) {
    return removeWithFlag(key, false, null);
  }

  /**
   * Remove the object from the cache.
   */
  public boolean removeWithFlag(K key, boolean _checkValue, V _value) {
    Entry e = lookupEntryNoHitRecord(key);
    if (e == null) {
      return false;
    }
    synchronized (e) {
      e.waitForProcessing();
      if (e.isGone()) {
        return false;
      }
      boolean f = e.hasFreshData();
      if (_checkValue) {
        if (!f || !e.equalsValue(_value)) {
          return false;
        }
      }
      removeEntry(e);
      return f;
    }
}

  @Override
  public void remove(K key) {
    containsAndRemove(key);
  }

  public V peekAndRemove(K key) {
    Entry e = lookupEntry(key);
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
      V _value = null;
      boolean f = e.hasFreshData();
      if (f) {
        _value = (V) e.getValueOrException();
      } else {
        metrics.peekHitNotFresh();
      }
      removeEntry(e);
      return returnValue(_value);
    }
  }

  public Executor getPrefetchExecutor() {
    return prefetchExecutor;
  }

  @Override
  public void prefetch(final K key) {
    if (loader == null) {
      return;
    }
    Entry<K,V> e = lookupEntryNoHitRecord(key);
    if (e != null && e.hasFreshData()) {
      return;
    }
    try {
      getPrefetchExecutor().execute(new RunWithCatch(this) {
        @Override
        public void action() {
          getEntryInternal(key);
        }
      });
    } catch (RejectedExecutionException ignore) {
    }
  }

  @Override
  public void prefetchAll(final Iterable<? extends K> _keys, final CacheOperationCompletionListener l) {
    final CacheOperationCompletionListener _listener= l != null ? l : DUMMY_LOAD_COMPLETED_LISTENER;
    if (loader == null) {
      _listener.onCompleted();
      return;
    }
    Set<K> _keysToLoad = checkAllPresent(_keys);
    final AtomicInteger _count = new AtomicInteger(2);
    try {
      for (K k : _keysToLoad) {
        final K key = k;
        Runnable r = new RunWithCatch(this) {
          @Override
          public void action() {
            try {
              getEntryInternal(key);
            } finally {
              if (_count.decrementAndGet() == 0) {
                _listener.onCompleted();
              }
            }
          }
        };
        try {
          getPrefetchExecutor().execute(r);
          _count.incrementAndGet();
        } catch (RejectedExecutionException ignore) { }
      }
    } finally {
      if (_count.addAndGet(-2) == 0) {
        _listener.onCompleted();
      }
    }
  }

  @Override
  public void loadAll(final Iterable<? extends K> _keys, final CacheOperationCompletionListener l) {
    checkLoaderPresent();
    final CacheOperationCompletionListener _listener= l != null ? l : DUMMY_LOAD_COMPLETED_LISTENER;
    Set<K> _keysToLoad = checkAllPresent(_keys);
    if (_keysToLoad.isEmpty()) {
      _listener.onCompleted();
      return;
    }
    final AtomicInteger _countDown = new AtomicInteger(_keysToLoad.size());
    for (K k : _keysToLoad) {
      final K key = k;
      Runnable r = new RunWithCatch(this) {
        @Override
        public void action() {
          try {
            getEntryInternal(key);
          } finally {
            if (_countDown.decrementAndGet() == 0) {
              _listener.onCompleted();
            }
          }
        }
      };
      try {
        loaderExecutor.execute(r);
      } catch (RejectedExecutionException ex) {
        r.run();
      }
    }
  }

  @Override
  public void reloadAll(final Iterable<? extends K> _keys, final CacheOperationCompletionListener l) {
    checkLoaderPresent();
    final CacheOperationCompletionListener _listener= l != null ? l : DUMMY_LOAD_COMPLETED_LISTENER;
    Set<K> _keySet = generateKeySet(_keys);
    final AtomicInteger _countDown = new AtomicInteger(_keySet.size());
    for (K k : _keySet) {
      final K key = k;
      Runnable r = new RunWithCatch(this) {
        @Override
        public void action() {
          try {
            loadAndReplace(key);
          } finally {
            if (_countDown.decrementAndGet() == 0) {
              _listener.onCompleted();
            }
          }
        }
      };
      try {
        loaderExecutor.execute(r);
      } catch (RejectedExecutionException ex) {
        r.run();
      }

    }
  }

  public static abstract class RunWithCatch implements Runnable {

    InternalCache cache;

    public RunWithCatch(final InternalCache _cache) {
      cache = _cache;
    }

    protected abstract void action();

    @Override
    public final void run() {
      if (cache.isClosed()) {
        return;
      }
      try {
        action();
      } catch (CacheClosedException ignore) {
      } catch (Throwable t) {
        cache.logAndCountInternalException("Loader thread exception (" + Thread.currentThread().getName() + ")", t);
      }
    }
  }

  public Set<K> generateKeySet(final Iterable<? extends K> _keys) {
    Set<K> _keySet = new HashSet<K>();
    for (K k : _keys) {
      _keySet.add(k);
    }
    return _keySet;
  }

  public Set<K> checkAllPresent(final Iterable<? extends K> keys) {
    Set<K> _keysToLoad = new HashSet<K>();
    for (K k : keys) {
      Entry<K,V> e = lookupEntryNoHitRecord(k);
      if (e == null || !e.hasFreshData()) {
        _keysToLoad.add(k);
      }
    }
    return _keysToLoad;
  }

  /**
   * Always fetch the value from the source. That is a copy of getEntryInternal
   * without freshness checks.
   */
  protected void loadAndReplace(K key) {
    Entry e;
    for (;;) {
      e = lookupOrNewEntry(key);
      synchronized (e) {
        e.waitForProcessing();
        if (e.isGone()) {
          metrics.goneSpin();
          continue;
        }
        e.startProcessing();
        break;
      }
    }
    boolean _finished = false;
    try {
      load(e);
      _finished = true;
    } finally {
      e.ensureAbort(_finished);
    }
  }

  /**
   * Lookup or create a new entry. The new entry is created, because we need
   * it for locking within the data fetch.
   */
  protected Entry<K, V> lookupOrNewEntry(K key) {
    int hc = modifiedHash(key.hashCode());
    return lookupOrNewEntry(key, hc);
  }

  protected Entry<K, V> lookupOrNewEntry(K key, int hc) {
    Entry e = lookupEntry(key, hc);
    if (e == null) {
      return insertNewEntry(key, hc);
    }
    return e;
  }

  protected Entry<K, V> lookupOrNewEntryNoHitRecord(K key) {
    int hc = modifiedHash(key.hashCode());
    Entry e = lookupEntryNoHitRecord(key, hc);
    if (e == null) {
      e = insertNewEntry(key, hc);
    }
    return e;
  }

  protected V returnValue(V v) {
    if (v instanceof ExceptionWrapper) {
      ExceptionWrapper w = (ExceptionWrapper) v;
      throw exceptionPropagator.propagateException(w.getKey(), w);
    }
    return v;
  }

  protected V returnValue(Entry<K, V> e) {
    V v = e.getValueOrException();
    if (v instanceof ExceptionWrapper) {
      ExceptionWrapper w = (ExceptionWrapper) v;
      throw exceptionPropagator.propagateException(w.getKey(), w);
    }
    return v;
  }

  protected Entry<K, V> lookupEntry(K key) {
    int hc = modifiedHash(key.hashCode());
    return lookupEntry(key, hc);
  }

  protected Entry lookupEntryNoHitRecord(K key) {
    int hc = modifiedHash(key.hashCode());
    return lookupEntryNoHitRecord(key, hc);
  }

  protected final Entry<K, V> lookupEntry(K key, int hc) {
    Entry e = hash.lookup(key, hc);
    if (e != null) {
      recordHit(e);
      return e;
    }
    return null;
  }

  protected final Entry lookupEntryNoHitRecord(K key, int hc) {
    return hash.lookup(key, hc);
  }

  /**
   * Insert new entry in all structures (hash and eviction). The insert at the eviction
   * needs to be done under the same lock, to allow a check of the consistency.
   */
  protected Entry<K, V> insertNewEntry(K key, int hc) {
    Entry<K,V> e = new Entry<K,V>(key, hc);
    Entry<K, V> e2;
    final OptimisticLock l = hash.getSegmentLock(hc);
    final long _stamp = l.writeLock();
    boolean _needsEviction = false;
    try {
      e2 = hash.insertWithinLock(e, hc);
      if (e == e2) {
        _needsEviction = eviction.submitWithoutEviction(e);
      }
    } finally {
      l.unlockWrite(_stamp);
    }
    if (_needsEviction) {
      eviction.evictEventually(hc);
    }
    hash.checkExpand(hc);
    return e2;
  }

  /**
   * Remove the entry from the hash table. The entry is already removed from the replacement list.
   * Stop the timer, if needed. The remove races with a clear. The clear
   * is not updating each entry state to e.isGone() but just drops the whole hash table instead.
   * This is why we return a flag whether the entry was really present or not at this time.
   *
   * <p>With completion of the method the entry content is no more visible. "Nulling" out the key
   * or value of the entry is incorrect, since there can be another thread which is just about to
   * return the entry contents.
   *
   * @return True, if the entry was present in the hash table.
   */
  public boolean removeEntryForEviction(Entry<K, V> e) {
    boolean f = hash.remove(e);
    checkForHashCodeChange(e);
    timing.cancelExpiryTimer(e);
    e.setGone();
    return f;
  }

  /**
   * Check whether the key was modified during the stay of the entry in the cache.
   * We only need to check this when the entry is removed, since we expect that if
   * the key has changed, the stored hash code in the cache will not match any more and
   * the item is evicted very fast.
   */
  private void checkForHashCodeChange(Entry<K, V> e) {
    if (modifiedHash(e.key.hashCode()) != e.hashCode) {
      if (keyMutationCnt ==  0) {
        getLog().warn("Key mismatch! Key hashcode changed! keyClass=" + e.key.getClass().getName());
        String s;
        try {
          s = e.key.toString();
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

  protected void load(Entry<K, V> e) {
    V v;
    long t0 = isNoLastModificationTime() ? 0 : System.currentTimeMillis();
    if (e.getNextRefreshTime() == Entry.EXPIRED_REFRESHED) {
      if (entryInRefreshProbationAccessed(e, t0)) {
        return;
      }
    }
    try {
      checkLoaderPresent();
      if (e.isVirgin()) {
        v = loader.load(e.key, t0, null);
      } else {
        v = loader.load(e.key, t0, e);
      }
    } catch (Throwable _ouch) {
      long t = t0;
      if (!metrics.isDisabled()) {
        t = System.currentTimeMillis();
      }
      loadGotException(e, t0, t, _ouch);
      return;
    }
    long t = t0;
    if (!metrics.isDisabled()) {
      t = System.currentTimeMillis();
    }
    insertOrUpdateAndCalculateExpiry(e, v, t0, t, INSERT_STAT_LOAD);
  }

  /**
   * Entry was refreshed before, reset timer and make entry visible again.
   */
  private boolean entryInRefreshProbationAccessed(final Entry<K, V> e, final long now) {
    long  nrt = e.getRefreshProbationNextRefreshTime();
    if (nrt > now) {
      reviveRefreshedEntry(e, nrt);
      return true;
    }
    return false;
  }

  private void reviveRefreshedEntry(final Entry<K, V> e, final long _nrt) {
    synchronized (e) {
      metrics.refreshedHit();
      finishLoadOrEviction(e, _nrt);
    }
  }

  private void loadGotException(final Entry<K, V> e, final long t0, final long t, final Throwable _wrappedException) {
    ExceptionWrapper<K> _value = new ExceptionWrapper(e.key, _wrappedException, t0, e);
    long _nextRefreshTime = 0;
    boolean _suppressException = false;
    try {
      if ((e.isDataValid() || e.isExpired()) && e.getException() == null) {
        _nextRefreshTime = timing.suppressExceptionUntil(e, _value);
      }
      if (_nextRefreshTime > t0) {
        _suppressException = true;
      } else {
        _nextRefreshTime = timing.cacheExceptionUntil(e, _value);
      }
    } catch (Exception ex) {
      resiliencePolicyException(e, t0, t, new ResiliencePolicyException(ex));
      return;
    }
    synchronized (e) {
      insertUpdateStats(e, (V) _value, t0, t, INSERT_STAT_LOAD, _nextRefreshTime, _suppressException);
      if (_suppressException) {
        e.setSuppressedLoadExceptionInformation(_value);
      } else {
        e.setLastModification(t0);
        e.setValueOrException((V) _value);
      }
      _value.setUntil(Math.abs(_nextRefreshTime));
      finishLoadOrEviction(e, _nextRefreshTime);
    }
  }

  /**
   * Exception from the resilience policy. We have two exceptions now. One from the loader or the expiry policy,
   * one from the resilience policy. We propagate the more severe one from the resilience policy.
   */
  private void resiliencePolicyException(final Entry<K, V> e, final long t0, final long t, Throwable _exception) {
    ExceptionWrapper<K> _value = new ExceptionWrapper(e.key, _exception, t0, e);
    insert(e, (V) _value, t0, t, INSERT_STAT_LOAD, 0);
  }

  private void checkLoaderPresent() {
    if (loader == null) {
      throw new UnsupportedOperationException("loader not set");
    }
  }

  /**
   * Calculate the next refresh time if a timer / expiry is needed and call insert.
   */
  protected final void insertOrUpdateAndCalculateExpiry(Entry<K, V> e, V v, long t0, long t, byte _updateStatistics) {
    long _nextRefreshTime;
    try {
      _nextRefreshTime = timing.calculateNextRefreshTime(e, v, t0);
    } catch (Throwable ex) {
      RuntimeException _wrappedException = new ExpiryPolicyException(ex);
      if (_updateStatistics == INSERT_STAT_LOAD) {
        loadGotException(e, t0, t, _wrappedException);
        return;
      }
      insertUpdateStats(e, v, t0, t, _updateStatistics, Long.MAX_VALUE, false);
      throw _wrappedException;
    }
    insert(e, v, t0, t, _updateStatistics, _nextRefreshTime);
  }

  final static byte INSERT_STAT_LOAD = 1;
  final static byte INSERT_STAT_PUT = 2;

  public RuntimeException returnNullValueDetectedException() {
    return new NullPointerException("null value not allowed");
  }

  protected final void insert(Entry<K, V> e, V _value, long t0, long t, byte _updateStatistics, long _nextRefreshTime) {
    if (_updateStatistics == INSERT_STAT_LOAD) {
      if (_value == null && hasRejectNullValues() && _nextRefreshTime != 0) {
        loadGotException(e, t0, t, returnNullValueDetectedException());
        return;
      }
      synchronized (e) {
        e.setLastModification(t0);
        insertUpdateStats(e, _value, t0, t, _updateStatistics, _nextRefreshTime, false);
        e.setValueOrException(_value);
        e.resetSuppressedLoadExceptionInformation();
        finishLoadOrEviction(e, _nextRefreshTime);
      }
    } else {
      if (_value == null && hasRejectNullValues()) {
        throw returnNullValueDetectedException();
      }
      e.setLastModification(t0);
      e.setValueOrException(_value);
      e.resetSuppressedLoadExceptionInformation();
      insertUpdateStats(e, _value, t0, t, _updateStatistics, _nextRefreshTime, false);
      restartTimer(e, _nextRefreshTime);
    }
  }

  private void insertUpdateStats(final Entry<K, V> e, final V _value, final long t0, final long t, final byte _updateStatistics, final long _nextRefreshTime, final boolean _suppressException) {
    if (_updateStatistics == INSERT_STAT_LOAD) {
      if (_suppressException) {
        metrics.suppressedException();
      } else {
        if (_value instanceof ExceptionWrapper) {
          metrics.loadException();
        }
      }
      if (!isNoLastModificationTime()) {
        long _millis = t - t0;
        if (e.isGettingRefresh()) {
          metrics.refresh(_millis);
        } else {
          if (e.isVirgin()) {
            metrics.load(_millis);
          } else {
            metrics.reload(_millis);
          }
        }
      }
    } else {
      if (e.hasFreshData(t, _nextRefreshTime)) {
        metrics.putNewEntry();
      }
    }
  }

  @Override
  public void timerEventRefresh(final Entry<K, V> e) {
    metrics.timerEvent();
    synchronized (e) {
      if (e.isGone()) {
        return;
      }
      Runnable r = new Runnable() {
        @Override
        public void run() {
          refreshEntry(e);
        }
      };
      try {
        loaderExecutor.execute(r);
        return;
      } catch (RejectedExecutionException ignore) {
      }
      metrics.refreshFailed();
      expireOrScheduleFinalExpireEvent(e);
    }
  }

  /**
   * Executed in loader thread. Load the entry again. After the load we copy the entry to the
   * refresh hash and expire it in the main hash. The entry needs to stay in the main hash
   * during the load, to block out concurrent reads.
   */
  private void refreshEntry(final Entry<K, V> e) {
    synchronized (e) {
      e.waitForProcessing();
      if (e.isGone()) {
        return;
      }
      e.startProcessing(Entry.ProcessingState.REFRESH);
    }
    boolean _finished = false;
    try {
      load(e);
      _finished = true;
    } catch (CacheClosedException ignore) {
    } catch (Throwable ex) {
      logAndCountInternalException("Refresh exception", ex);
      try {
        synchronized (e) {
          expireEntry(e);
        }
      } catch (CacheClosedException ignore) {
      }
    } finally {
      e.ensureAbort(_finished);
    }
  }

  public void startRefreshProbationTimer(final Entry<K, V> e, long _nextRefreshTime) {
    boolean _expired = timing.startRefreshProbationTimer(e, _nextRefreshTime);
    if (_expired) {
      expireAndRemoveEventually(e);
    }
  }

  @Override
  public void timerEventProbationTerminated(final Entry<K, V> e) {
    metrics.timerEvent();
    synchronized (e) {
      expireEntry(e);
    }
  }

  @Override
  public void logAndCountInternalException(final String _text, final Throwable _exception) {
    synchronized (lock) {
      internalExceptionCnt++;
    }
    getLog().warn(_text, _exception);
  }

  @Override
  public void timerEventExpireEntry(Entry<K, V> e) {
    metrics.timerEvent();
    synchronized (e) {
      expireOrScheduleFinalExpireEvent(e);
    }
  }

  @Override
  public void expireOrScheduleFinalExpireEvent(final Entry<K, V> e) {
    long nrt = e.getNextRefreshTime();
    if (e.isGone() || e.isExpired()) {
      return;
    }
    long t = System.currentTimeMillis();
    if (t >= Math.abs(nrt)) {
      try {
        expireEntry(e);
      } catch (CacheClosedException ignore) { }
    } else {
      if (nrt <= 0) {
        return;
      }
      timing.scheduleFinalTimerForSharpExpiry(e);
      e.setNextRefreshTime(-nrt);
    }
  }

  protected void expireEntry(Entry e) {
    if (e.isGone() || e.isExpired()) {
      return;
    }
    e.setExpiredState();
    expireAndRemoveEventually(e);
  }

  /**
   * Remove expired from heap and increment statistics. The entry is not removed when
   * there is processing going on in parallel.
   *
   * @see #expireAndRemoveEventuallyAfterProcessing(Entry)
   */
  private void expireAndRemoveEventually(final Entry e) {
    if (hasKeepAfterExpired() || e.isProcessing()) {
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
  private void expireAndRemoveEventuallyAfterProcessing(final Entry e) {
    if (hasKeepAfterExpired()) {
      metrics.expiredKept();
    } else {
      removeEntry(e);
    }
  }

  /**
   * Returns all cache entries within the heap cache. Entries that
   * are expired or contain no valid data are not filtered out.
   */
  public final ConcurrentEntryIterator<K,V> iterateAllHeapEntries() {
    return new ConcurrentEntryIterator<K,V>(this);
  }

  /**
   * JSR107 bulk interface. The behaviour is compatible to the JSR107 TCK. We also need
   * to be compatible to the exception handling policy, which says that the exception
   * is to be thrown when most specific. So exceptions are only thrown, when the value
   * which has produced an exception is requested from the map.
   */
  public Map<K, V> getAll(final Iterable<? extends K> _inputKeys) {
    Map<K, ExaminationEntry<K, V>> map = new HashMap<K, ExaminationEntry<K, V>>();
    for (K k : _inputKeys) {
      Entry<K,V> e = getEntryInternal(k);
      if (e != null) {
        map.put(e.getKey(), ReadOnlyCacheEntry.of(e));
      }
    }
    return convertValueMap(map);
  }

  public Map<K, V> convertValueMap(final Map<K, ExaminationEntry<K, V>> _map) {
    return new MapValueConverterProxy<K, V, ExaminationEntry<K, V>>(_map) {
      @Override
      protected V convert(final ExaminationEntry<K, V> v) {
        return returnValue(v.getValueOrException());
      }
    };
  }

  public Map<K, V> convertCacheEntry2ValueMap(final Map<K, CacheEntry<K, V>> _map) {
    return new MapValueConverterProxy<K, V, CacheEntry<K, V>>(_map) {
      @Override
      protected V convert(final CacheEntry<K, V> v) {
        return v.getValue();
      }
    };
  }

  public Map<K, V> peekAll(final Iterable<? extends K> _inputKeys) {
    Map<K, ExaminationEntry<K, V>> map = new HashMap<K, ExaminationEntry<K, V>>();
    for (K k : _inputKeys) {
      ExaminationEntry<K, V> e = peekEntryInternal(k);
      if (e != null) {
        map.put(k, e);
      }
    }
    return convertValueMap(map);
  }

  public void putAll(Map<? extends K, ? extends V> valueMap) {
    if (valueMap.containsKey(null)) {
      throw new NullPointerException("map contains null key");
    }
    for (Map.Entry<? extends K, ? extends V> e : valueMap.entrySet()) {
      put(e.getKey(), e.getValue());
    }
  }

  Operations<K,V> spec() { return Operations.SINGLETON; }

  @Override
  protected <R> EntryAction<K, V, R> createEntryAction(final K key, final Entry<K, V> e, final Semantic<K, V, R> op) {
    return new EntryAction<K, V, R>(this, this, op, key, e) {
      @Override
      protected TimingHandler<K, V> timing() {
        return timing;
      }
    };
  }

  /**
   * Simply the {@link EntryAction} based code to provide the entry processor. If we code it directly this
   * might be a little bit more efficient, but it gives quite a code bloat which has lots of
   * corner cases for loader and exception handling.
   */
  @Override
  public <R> R invoke(K key, EntryProcessor<K, V, R> entryProcessor) {
    return execute(key, spec().invoke(key, loader != null, entryProcessor));
  }

  @Override
  public void expireAt(final K key, final long _millis) {
    execute(key, spec().expire(key, _millis));
  }

  public final long getLocalSize() {
    return hash.getSize();
  }

  public final int getTotalEntryCount() {
    return executeWithGlobalLock(new Job<Integer>() {
      @Override
      public Integer call() {
        return (int) getLocalSize();
      }
    });
  }

  protected IntegrityState getIntegrityState() {
    EvictionMetrics em = eviction.getMetrics();
    IntegrityState is = new IntegrityState()
      .checkEquals("hash.getSize() == hash.calcEntryCount()", hash.getSize(), hash.calcEntryCount());
    if (em.getEvictionRunningCount() > 0) {
      is.check("eviction running: hash.getSize() == eviction.getSize()", true)
        .check("eviction running: newEntryCnt == hash.getSize() + evictedCnt ....", true);
    } else {
      is.checkEquals("hash.getSize() == eviction.getSize()", getLocalSize(), em.getSize())
        .checkEquals("newEntryCnt == hash.getSize() + evictedCnt + expiredRemoveCnt + removeCnt + clearedCnt + virginRemovedCnt",
          em.getNewEntryCount(), getLocalSize() + em.getEvictedCount() +
            em.getExpiredRemovedCount() + em.getRemovedCount() + clearRemovedCnt + em.getVirginRemovedCount());
    }
    eviction.checkIntegrity(is);
    return is;
  }

  /** Check internal data structures and throw and exception if something is wrong, used for unit testing */
  public final void checkIntegrity() {
    executeWithGlobalLock(new Job<Void>() {
      @Override
      public Void call() {
        IntegrityState is = getIntegrityState();
        if (is.getStateFlags() > 0) {
          throw new CacheIntegrityError(is.getStateDescriptor(), is.getFailingChecks(), generateInfoUnderLock(HeapCache.this, System.currentTimeMillis()).toString());
        }
        return null;
      }
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

  public final InternalCacheInfo getInfo(InternalCache _userCache) {
    synchronized (lock) {
      long t = System.currentTimeMillis();
      if (info != null &&
        (info.getInfoCreatedTime() + info.getInfoCreationDeltaMs() * TUNABLE.minimumStatisticsCreationTimeDeltaFactor + TUNABLE.minimumStatisticsCreationDeltaMillis > t)) {
        return info;
      }
      info = generateInfo(_userCache, t);
    }
    return info;
  }

  public final InternalCacheInfo getLatestInfo(InternalCache _userCache) {
    return generateInfo(_userCache, System.currentTimeMillis());
  }

  private CacheBaseInfo generateInfo(final InternalCache _userCache, final long t) {
    return executeWithGlobalLock(new Job<CacheBaseInfo>() {
      @Override
      public CacheBaseInfo call() {
        return generateInfoUnderLock(_userCache, t);
      }
    });
  }

  private CacheBaseInfo generateInfoUnderLock(final InternalCache _userCache, final long t) {
    info = new CacheBaseInfo(HeapCache.this, _userCache, t);
    info.setInfoCreationDeltaMs((int) (System.currentTimeMillis() - t));
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
  public <T> T executeWithGlobalLock(final Job<T> job) {
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
   * @param _checkClosed variant, this method is needed once without check during the close itself
   */
  private <T> T executeWithGlobalLock(final Job<T> job, final boolean _checkClosed) {
    synchronized (lock) {
      if (_checkClosed) { checkClosed(); }
      eviction.stop();
      try {
        T _result = hash.runTotalLocked(new Job<T>() {
          @Override
          public T call() {
            if (_checkClosed) { checkClosed(); }
            boolean f = eviction.drain();
            if (f) {
              return (T) RESTART_AFTER_EVICTION;
            }
            return eviction.runLocked(new Job<T>() {
              @Override
              public T call() {
                return job.call();
              }
            });
          }
        });
        if (_result == RESTART_AFTER_EVICTION) {
          eviction.evictEventually();
          _result = hash.runTotalLocked(new Job<T>() {
            @Override
            public T call() {
              if (_checkClosed) { checkClosed(); }
              eviction.drain();
              return eviction.runLocked(new Job<T>() {
                @Override
                public T call() {
                  return job.call();
                }
              });
            }
          });
        }
        return _result;
      } finally {
        eviction.start();
      }
    }
  }

  @Override
  public CacheManager getCacheManager() {
    return manager;
  }

  /**
   * Return status information. The status collection is time consuming, so this
   * is an expensive operation.
   */
  @Override
  public String toString() {
    synchronized (lock) {
      if (isClosed()) {
        return "Cache{" + name + "}(closed)";
      }
    }
    InternalCacheInfo fo = getLatestInfo();
    return fo.toString();
  }

  /**
   * This function calculates a modified hash code. The intention is to
   * "rehash" the incoming integer hash codes to overcome weak hash code
   * implementations. We expect good results for integers also.
   * Also add a random seed to the hash to protect against attacks on hashes.
   * This is actually a slightly reduced version of the java.util.HashMap
   * hash modification.
   */
  public final int modifiedHash(int h) {
    h ^= hashSeed;
    h ^= h >>> 7;
    h ^= h >>> 15;
    return h;

  }

  public static class Tunable extends TunableConstants {

    public Class<? extends InternalCache> defaultImplementation = HeapCache.class;

    /**
     * Limits the number of spins until an entry lock is expected to
     * succeed. The limit is to detect deadlock issues during development
     * and testing. It is set to an arbitrary high value to result in
     * an exception after about one second of spinning.
     */
    public int maximumEntryLockSpins = 333333;

    /**
     * Size of the hash table before inserting the first entry. Must be power
     * of two. Default: 64.
     */
    public int initialHashSize = 64;

    /**
     * Fill percentage limit. When this is reached the hash table will get
     * expanded. Default: 64.
     */
    public int hashLoadPercent = 64;

    /**
     * The hash code will randomized by default. This is a countermeasure
     * against from outside that know the hash function.
     */
    public boolean disableHashRandomization = false;

    /**
     * Seed used when randomization is disabled. Default: 0.
     */
    public int hashSeed = 0;

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
     * time delta, to ensure that the machine is not busy due to statistics generation. Default: 333.
     */
    public int minimumStatisticsCreationDeltaMillis = 333;

    /**
     *  Factor of the statistics creation time, that determines the time difference when new
     *  statistics are generated.
     */
    public int minimumStatisticsCreationTimeDeltaFactor = 123;

    public ThreadFactoryProvider threadFactoryProvider = new DefaultThreadFactoryProvider();

    /**
     * Number of maximum loader threads, depending on the CPUs.
     */
    public int loaderThreadCountCpuFactor = 1;

    public StandardCommonMetricsFactory commonMetricsFactory = new StandardCommonMetricsFactory();

    public ExceptionPropagator exceptionPropagator = new StandardExceptionPropagator();

    /**
     * Alert level error, when hash quality is below this threshold. Default 5.
     */
    public int hashQualityWarningThreshold = 20;

    /**
     * Alert level error, when hash quality is below this threshold. Default 5.
     */
    public int hashQualityErrorThreshold = 5;

    /**
     * Override parameter for segment count. Has to be power of two, e.g. 2, 4, 8, etc.
     * Invalid numbers will be replaced by the next higher power of two. Default is 0, no override.
     */
    public int segmentCountOverride = 0;


  }

}
