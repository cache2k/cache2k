package org.cache2k.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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
import org.cache2k.configuration.CacheConfiguration;
import org.cache2k.core.operation.ExaminationEntry;
import org.cache2k.core.operation.ReadOnlyCacheEntry;
import org.cache2k.core.operation.Semantic;
import org.cache2k.core.operation.Specification;
import org.cache2k.core.threading.DefaultThreadFactoryProvider;
import org.cache2k.core.threading.Futures;
import org.cache2k.core.threading.ThreadFactoryProvider;

import org.cache2k.core.util.Log;
import org.cache2k.core.util.TunableConstants;
import org.cache2k.core.util.TunableFactory;
import org.cache2k.integration.AdvancedCacheLoader;
import org.cache2k.integration.CacheLoader;
import org.cache2k.integration.ExceptionPropagator;
import org.cache2k.integration.LoadCompletedListener;
import org.cache2k.integration.CacheLoaderException;
import org.cache2k.integration.LoadExceptionInformation;
import org.cache2k.processor.CacheEntryProcessor;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
@SuppressWarnings({"unchecked", "SynchronizationOnLocalVariableOrMethodParameter"})
public abstract class HeapCache<K, V>
  extends AbstractCache<K, V> {

  static final LoadCompletedListener DUMMY_LOAD_COMPLETED_LISTENER = new LoadCompletedListener() {
    @Override
    public void loadCompleted() {

    }

    @Override
    public void loadException(final Throwable _exception) {

    }
  };

  static final Random SEED_RANDOM = new Random(new SecureRandom().nextLong());
  static int cacheCnt = 0;

  protected static final Tunable TUNABLE = TunableFactory.get(Tunable.class);

  final static ExceptionPropagator DEFAULT_EXCEPTION_PROPAGATOR = new ExceptionPropagator() {
    @Override
    public void propagateException(Object key, final LoadExceptionInformation exceptionInformation) {
      long _expiry = exceptionInformation.getUntil();
      if (_expiry > 0) {
        if (_expiry == Long.MAX_VALUE) {
          throw new CacheLoaderException("(expiry=ETERNAL) " + exceptionInformation.getException(), exceptionInformation.getException());
        }
        throw new CacheLoaderException("(expiry=" + formatMillis(_expiry) + ") " + exceptionInformation.getException(), exceptionInformation.getException());
      } else {
        throw new CacheLoaderException("propagate previous loader exception", exceptionInformation.getException());
      }
    }
  };

  protected int hashSeed;

  {
    if (TUNABLE.disableHashRandomization) {
      hashSeed = TUNABLE.hashSeed;
    } else {
      hashSeed = SEED_RANDOM.nextInt();
    }
  }

  HeapCacheListener<K,V> listener = HeapCacheListener.NO_OPERATION;

  /** Maximum amount of elements in cache */
  protected long maxSize = 5000;

  protected String name;
  public CacheManagerImpl manager;
  protected AdvancedCacheLoader<K,V> loader;
  protected TimingHandler<K,V> timing = TimingHandler.ETERNAL;

  /** Statistics */

  protected CacheBaseInfo info;

  protected long clearedTime = 0;
  protected long startedTime;
  protected long touchedTime;

  protected long keyMutationCount = 0;
  protected long putButExpiredCnt = 0;
  protected long putNewEntryCnt = 0;
  protected long removedCnt = 0;
  protected long virginRemovedCnt = 0;
  /** Number of entries removed by clear. */
  protected long clearedCnt = 0;
  protected long clearCnt = 0;
  protected long expiredKeptCnt = 0;
  protected long expiredRemoveCnt = 0;
  protected long evictedCnt = 0;
  protected long refreshCnt = 0;
  protected long suppressedExceptionCnt = 0;
  protected long loadExceptionCnt = 0;
  /* that is a miss, but a hit was already counted. */
  protected long peekHitNotFreshCnt = 0;
  /* no heap hash hit */
  protected long peekMissCnt = 0;

  protected long loadWoRefreshCnt = 0;
  protected long loadButHitCnt = 0;
  protected long fetchMillis = 0;
  protected long refreshHitCnt = 0;
  protected long newEntryCnt = 0;

  protected long refreshSubmitFailedCnt = 0;

  CommonMetrics.Updater metrics = new StandardCommonMetrics();

  /**
   * Structure lock of the cache. Every operation that needs a consistent structure
   * of the cache or modifies it needs to synchronize on this. Since this is a global
   * lock, locking on it should be avoided and any operation under the lock should be
   * quick.
   */
  public final Object lock = new Object();

  protected volatile Executor loaderExecutor = new DummyExecutor(this);

  static class DummyExecutor implements Executor {
    HeapCache cache;

    public DummyExecutor(final HeapCache _cache) {
      cache = _cache;
    }

    @Override
    public synchronized void execute(final Runnable _command) {
      cache.loaderExecutor = cache.provideDefaultLoaderExecutor(0);
      cache.loaderExecutor.execute(_command);
    }
  }

  public Hash<Entry<K, V>> mainHashCtrl;
  protected Entry<K, V>[] mainHash;

  protected Hash<Entry<K, V>> refreshHashCtrl;
  protected Entry<K, V>[] refreshHash;

  /** Stuff that we need to wait for before shutdown may complete */
  protected Futures.WaitForAllFuture<?> shutdownWaitFuture;

  public boolean shutdownInitiated = true;

  /**
   * Flag during operation that indicates, that the cache is full and eviction needs
   * to be done. Eviction is only allowed to happen after an entry is fetched, so
   * at the end of an cache operation that increased the entry count we check whether
   * something needs to be evicted.
   */
  protected boolean evictionNeeded = false;

  protected Class keyType;

  protected Class valueType;

  protected ExceptionPropagator exceptionPropagator = DEFAULT_EXCEPTION_PROPAGATOR;

  private int featureBits = 0;

  private static final int SHARP_EXPIRY_FEATURE = 1;
  private static final int KEEP_AFTER_EXPIRED = 2;
  private static final int SUPPRESS_EXCEPTIONS = 4;
  private static final int NULL_VALUE_SUPPORT = 8;
  private static final int BACKGROUND_REFRESH = 16;

  protected final boolean hasSharpTimeout() {
    return (featureBits & SHARP_EXPIRY_FEATURE) > 0;
  }

  protected final boolean hasKeepAfterExpired() {
    return (featureBits & KEEP_AFTER_EXPIRED) > 0;
  }

  protected final boolean hasNullValueSupport() {
    return (featureBits & NULL_VALUE_SUPPORT) > 0;
  }

  protected final boolean hasSuppressExceptions() {
    return (featureBits & SUPPRESS_EXCEPTIONS) > 0;
  }

  protected final boolean hasBackgroundRefresh() { return (featureBits & BACKGROUND_REFRESH) > 0; }

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
  public void setCacheConfig(CacheConfiguration c) {
    valueType = c.getValueType().getType();
    keyType = c.getKeyType().getType();
    if (name != null) {
      throw new IllegalStateException("already configured");
    }
    setName(c.getName());
    maxSize = c.getEntryCapacity();
    if (c.getHeapEntryCapacity() >= 0) {
      maxSize = c.getHeapEntryCapacity();
    }
    if (c.isRefreshAhead()) {
      setFeatureBit(BACKGROUND_REFRESH, true);
    }
    if (c.getLoaderThreadCount() > 0) {
      loaderExecutor = provideDefaultLoaderExecutor(c.getLoaderThreadCount());
    }
    setFeatureBit(KEEP_AFTER_EXPIRED, c.isKeepDataAfterExpired());
    setFeatureBit(SHARP_EXPIRY_FEATURE, c.isSharpExpiry());
    setFeatureBit(SUPPRESS_EXCEPTIONS, c.isSuppressExceptions());
  }

  String getThreadNamePrefix() {
    String _prefix = "cache2k-loader-";
    if (manager != null &&
      !Cache2kManagerProviderImpl.DEFAULT_MANAGER_NAME.equals(manager.getName())) {
      _prefix = _prefix + manager.getName() + ":";
    }
    return _prefix + name;
  }

  Executor provideDefaultLoaderExecutor(int _threadCount) {
    if (_threadCount <= 0) {
      _threadCount = Runtime.getRuntime().availableProcessors() * TUNABLE.loaderThreadCountCpuFactor;
    }
    return
      new ThreadPoolExecutor(_threadCount, _threadCount,
        21, TimeUnit.SECONDS,
        new LinkedBlockingDeque<Runnable>(),
        TUNABLE.threadFactoryProvider.newThreadFactory(getThreadNamePrefix()),
        new ThreadPoolExecutor.AbortPolicy());
  }

  public void setTiming(final TimingHandler<K,V> rh) {
    timing = rh;
  }

  public void setExceptionPropagator(ExceptionPropagator ep) {
    exceptionPropagator = ep;
  }

  public void setLoader(final CacheLoader<K,V> l) {
    loader = new AdvancedCacheLoader<K, V>() {
      @Override
      public V load(final K key, final long currentTime, final CacheEntry<K, V> previousEntry) throws Exception {
        return l.load(key);
      }
    };
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
  public Class<?> getKeyType() { return keyType; }

  @Override
  public Class<?> getValueType() { return valueType; }

  /**
   * Registers the cache in a global set for the clearAllCaches function and
   * registers it with the resource monitor.
   */
  public void init() {
    synchronized (lock) {
      if (name == null) {
        name = String.valueOf(cacheCnt++);
      }
      timing.init(this);
      initializeHeapCache();
      if (hasBackgroundRefresh() &&
        loader == null) {
        throw new CacheMisconfigurationException("backgroundRefresh, but no loader defined");
      }
      shutdownInitiated = false;
    }
  }

  public void updateShutdownWaitFuture(Future<?> f) {
    synchronized (lock) {
      if (shutdownWaitFuture == null || shutdownWaitFuture.isDone()) {
        shutdownWaitFuture = new Futures.WaitForAllFuture(f);
      } else {
        shutdownWaitFuture.add((Future) f);
      }
    }
  }

  public void checkClosed() {
    if (isClosed()) {
      throw new CacheClosedException();
    }
  }

  public final void clear() {
    synchronized (lock) {
      checkClosed();
      clearLocalCache();
    }
  }

  public final void clearLocalCache() {
    iterateAllEntriesAndRemoveFromReplacementList();
    clearedCnt += getLocalSize();
    clearCnt++;
    initializeHeapCache();
    clearedTime = System.currentTimeMillis();
    touchedTime = clearedTime;
  }

  /**
   * Possible race with clear() and entryRemove. We need to
   * mark every entry as removed, otherwise an already
   * cleared entry might be removed again.
   */
  protected void iterateAllEntriesAndRemoveFromReplacementList() {
    Iterator<Entry<K,V>> it = iterateAllHeapEntries();
    int _count = 0;
    while (it.hasNext()) {
      org.cache2k.core.Entry e = it.next();
      e.removedFromList();
      _count++;
    }
  }

  protected void initializeHeapCache() {
    if (mainHashCtrl != null) {
      mainHashCtrl.cleared();
      refreshHashCtrl.cleared();
    }
    mainHashCtrl = new Hash<Entry<K, V>>();
    refreshHashCtrl = new Hash<Entry<K, V>>();
    mainHash = mainHashCtrl.init((Class<Entry<K, V>>) newEntry().getClass());
    refreshHash = refreshHashCtrl.init((Class<Entry<K, V>>) newEntry().getClass());
    if (startedTime == 0) {
      startedTime = System.currentTimeMillis();
    }
    timing.reset();
  }

  @Override
  public void clearTimingStatistics() {
    synchronized (lock) {
      loadWoRefreshCnt = 0;
      fetchMillis = 0;
    }
  }

  /**
   * Preparation for shutdown. Cancel all pending timer jobs e.g. for
   * expiry/refresh or flushing the storage.
   */
  @Override
  public void cancelTimerJobs() {
    timing.shutdown();
  }

  @Override
  public boolean isClosed() {
    return shutdownInitiated;
  }

  @Override
  public void destroy() {
    close();
  }

  public void closePart1() {
    synchronized (lock) {
      if (shutdownInitiated) {
        return;
      }
      shutdownInitiated = true;
    }
    if (loaderExecutor instanceof ExecutorService) {
      ((ExecutorService) loaderExecutor).shutdown();
    }
    cancelTimerJobs();
    synchronized (lock) {
      mainHashCtrl.close();
      refreshHashCtrl.close();
    }
    try {
      Future<?> _future = shutdownWaitFuture;
      if (_future != null) {
        _future.get();
      }
    } catch (Exception ex) {
      throw new CacheException(ex);
    }
  }

  @Override
  public void close() {
    closePart1();
    closePart2();
  }

  public void closePart2() {
    synchronized (lock) {
      timing.shutdown();
      mainHash = refreshHash = null;
      if (manager != null) {
        manager.cacheDestroyed(this);
        manager = null;
      }
    }
  }

  @Override
  public Iterator<CacheEntry<K, V>> iterator() {
    synchronized (lock) {
      return new IteratorFilterEntry2Entry(this, iterateAllHeapEntries(), true);
    }
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
      cache.checkClosed();
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
        throw new IllegalStateException("hasNext() / next() not called or end of iteration reached");
      }
      cache.remove((K) lastEntry.getKey());
    }
  }

  protected static void removeFromList(final Entry e) {
    e.prev.next = e.next;
    e.next.prev = e.prev;
    e.removedFromList();
  }

  protected static void insertInList(final Entry _head, final Entry e) {
    e.prev = _head;
    e.next = _head.next;
    e.next.prev = e;
    _head.next = e;
  }

  protected static final int getListEntryCount(final Entry _head) {
    org.cache2k.core.Entry e = _head.next;
    int cnt = 0;
    while (e != _head) {
      cnt++;
      if (e == null) {
        return -cnt;
      }
      e = e.next;
    }
    return cnt;
  }

  protected static final <E extends org.cache2k.core.Entry> void moveToFront(final E _head, final E e) {
    removeFromList(e);
    insertInList(_head, e);
  }

  protected static final <E extends org.cache2k.core.Entry> E insertIntoTailCyclicList(final E _head, final E e) {
    if (_head == null) {
      return (E) e.shortCircuit();
    }
    e.next = _head;
    e.prev = _head.prev;
    _head.prev = e;
    e.prev.next = e;
    return _head;
  }

  /**
   * Insert X into A B C, yields: A X B C.
   */
  protected static final <E extends org.cache2k.core.Entry> E insertAfterHeadCyclicList(final E _head, final E e) {
    if (_head == null) {
      return (E) e.shortCircuit();
    }
    e.prev = _head;
    e.next = _head.next;
    _head.next.prev = e;
    _head.next = e;
    return _head;
  }

  /** Insert element at the head of the list */
  protected static final <E extends org.cache2k.core.Entry> E insertIntoHeadCyclicList(final E _head, final E e) {
    if (_head == null) {
      return (E) e.shortCircuit();
    }
    e.next = _head;
    e.prev = _head.prev;
    _head.prev.next = e;
    _head.prev = e;
    return e;
  }

  protected static <E extends org.cache2k.core.Entry> E removeFromCyclicList(final E _head, E e) {
    if (e.next == e) {
      e.removedFromList();
      return null;
    }
    org.cache2k.core.Entry _eNext = e.next;
    e.prev.next = _eNext;
    e.next.prev = e.prev;
    e.removedFromList();
    return e == _head ? (E) _eNext : _head;
  }

  protected static org.cache2k.core.Entry removeFromCyclicList(final org.cache2k.core.Entry e) {
    org.cache2k.core.Entry _eNext = e.next;
    e.prev.next = _eNext;
    e.next.prev = e.prev;
    e.removedFromList();
    return _eNext == e ? null : _eNext;
  }

  protected static int getCyclicListEntryCount(org.cache2k.core.Entry e) {
    if (e == null) { return 0; }
    final org.cache2k.core.Entry _head = e;
    int cnt = 0;
    do {
      cnt++;
      e = e.next;
      if (e == null) {
        return -cnt;
      }
    } while (e != _head);
    return cnt;
  }

  protected static boolean checkCyclicListIntegrity(org.cache2k.core.Entry e) {
    if (e == null) { return true; }
    org.cache2k.core.Entry _head = e;
    do {
      if (e.next == null) {
        return false;
      }
      if (e.next.prev == null) {
        return false;
      }
      if (e.next.prev != e) {
        return false;
      }
      e = e.next;
    } while (e != _head);
    return true;
  }

  /**
   * Record an entry hit.
   */
  protected abstract void recordHit(Entry e);

  /**
   * New cache entry, put it in the replacement algorithm structure
   */
  protected abstract void insertIntoReplacementList(Entry e);

  /**
   * Entry object factory. Return an entry of the proper entry subtype for
   * the replacement/eviction algorithm.
   */
  protected abstract Entry newEntry();


  /**
   * Find an entry that should be evicted. Called within structure lock.
   * After doing some checks the cache will call {@link #removeEntryFromReplacementList(org.cache2k.core.Entry)}
   * if this entry will be really evicted. Pinned entries may be skipped. A
   * good eviction algorithm returns another candidate on sequential calls, even
   * if the candidate was not removed.
   *
   * <p/>Rationale: Within the structure lock we can check for an eviction candidate
   * and may remove it from the list. However, we cannot process additional operations or
   * events which affect the entry. For this, we need to acquire the lock on the entry
   * first.
   */
  protected abstract Entry findEvictionCandidate();

  /**
   * Evict entry from the cache. Almost identical to {@link #removeEntryFromReplacementList(Entry)},
   * but for eviction we might track additional history information. If the entry is
   * expired or removed we don't track history.
   */
  protected void evictEntry(Entry e) {
    removeEntryFromReplacementList(e);
  }


  /**
   *
   */
  protected void removeEntryFromReplacementList(Entry e) {
    removeFromList(e);
  }

  /**
   * Check whether we have an entry in the ghost table
   * remove it from ghost and insert it into the replacement list.
   * null if nothing there. This may also do an optional eviction
   * if the size limit of the cache is reached, because some replacement
   * algorithms (ARC) do this together.
   */
  protected Entry checkForGhost(K key, int hc) { return null; }

  /**
   * Implement unsynchronized lookup if it is supported by the eviction.
   * If a null is returned the lookup is redone synchronized.
   */
  protected Entry<K, V> lookupEntryUnsynchronized(K key, int hc) { return null; }

  protected Entry lookupEntryUnsynchronizedNoHitRecord(K key, int hc) { return null; }

  protected void recordHitLocked(Entry e) {
    synchronized (lock) {
      recordHit(e);
    }
  }

  @Override
  public V get(K key) {
    return (V) returnValue(getEntryInternal(key));
  }

  /**
   * Wrap entry in a separate object instance. We can return the entry directly, however we lock on
   * the entry object.
   */
  protected CacheEntry<K, V> returnEntry(final Entry<K, V> e) {
    if (e == null) {
      return null;
    }
    synchronized (e) {
      final K _key = e.getKey();
      final V _value = e.getValue();
      final Throwable _exception = e.getException();
      final long _lastModification = e.getLastModification();
      return returnCacheEntry(_key, _value, _exception, _lastModification);
    }
  }

  @Override
  public String getEntryState(K key) {
    Entry e = lookupEntrySynchronized(key);
    if (e == null) {
      return null;
    }
    synchronized (e) {
      return e.toString(this);
    }
  }

  private CacheEntry<K, V> returnCacheEntry(final K _key, final V _value, final Throwable _exception, final long _lastModification) {
    CacheEntry<K, V> ce = new CacheEntry<K, V>() {
      @Override
      public K getKey() {
        return _key;
      }

      @Override
      public V getValue() {
        return _value;
      }

      @Override
      public Throwable getException() {
        return _exception;
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
            ", updated=" + formatMillis(getLastModification());
      }

    };
    return ce;
  }

  @Override
  public CacheEntry<K, V> getEntry(K key) {
    return returnEntry(getEntryInternal(key));
  }

  protected Entry getEntryInternal(K key) {
    Entry e;
    for (;;) {
      e = lookupOrNewEntrySynchronized(key);
      if (e.hasFreshData()) {
        return e;
      }
      synchronized (e) {
        e.waitForProcessing();
        if (e.hasFreshData()) {
          return e;
        }
        if (e.isGone()) {
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
    evictEventually();
    return e;
  }

  protected void finishLoadOrEviction(Entry e, long _nextRefreshTime) {
    e.notifyAll();
    e.processingDone();
    restartTimer(e, _nextRefreshTime);
  }

  private void restartTimer(final Entry e, final long _nextRefreshTime) {
    e.setNextRefreshTime(timing.stopStartTimer(_nextRefreshTime, e));
    checkForImmediateExpiry(e);
  }

  private void checkForImmediateExpiry(final Entry e) {
    if (e.isExpired()) {
      expireAndRemoveEventually(e);
    }
  }

  /**
   * Loop: Request an eviction candidate, check whether processing is going on
   * and evict it. Continue, if still above capacity limit.
   */
  protected final void evictEventually() {
    int _spinCount = TUNABLE.maximumEvictSpins;
    Entry _previousCandidate = null;
    while (evictionNeeded) {
      if (_spinCount-- <= 0) { return; }
      Entry e;
      synchronized (lock) {
        checkClosed();
        if (getLocalSize() <= maxSize) {
          evictionNeeded = false;
          return;
        }
        e = findEvictionCandidate();
      }
      synchronized (e) {
        if (e.isGone()) {
          continue;
        }
        if (e.isProcessing()) {
          if (e != _previousCandidate) {
            _previousCandidate = e;
            continue;
          } else {
            return;
          }
        }
        e.startProcessing(Entry.ProcessingState.EVICT);
      }
      listener.onEvictionFromHeap(e);
      synchronized (e) {
        finishLoadOrEviction(e, org.cache2k.core.Entry.ABORTED);
        evictEntryFromHeap(e);
      }
    }
  }

  private void evictEntryFromHeap(Entry e) {
    synchronized (lock) {
      if (e.isRemovedFromReplacementList()) {
        if (removeEntryFromHash(e)) {
          evictedCnt++;
        }
      } else {
        evictEntry(e);
        if (removeEntryFromHash(e)) {
          evictedCnt++;
        }
      }
      evictionNeeded = getLocalSize() > maxSize;
    }
  }

  /**
   * Remove the entry from the hash and the replacement list.
   * There is a race condition to catch: The eviction may run
   * in a parallel thread and may have already selected this
   * entry.
   */
  protected boolean removeEntry(Entry e) {
    if (e.isRemovedFromReplacementList()) {
      if (removeEntryFromHash(e)) {
        return true;
      }
      return false;
    } else {
      removeEntryFromReplacementList(e);
    }
    return removeEntryFromHash(e);
  }

  @Override
  public V peekAndPut(K key, V _value) {
    final int hc = modifiedHash(key.hashCode());
    boolean _hasFreshData;
    V _previousValue = null;
    Entry e;
    for (;;) {
      e = lookupOrNewEntrySynchronized(key, hc);
      synchronized (e) {
        e.waitForProcessing();
        if (e.isGone()) {
          continue;
        }
        _hasFreshData = e.hasFreshData();
        if (_hasFreshData) {
          _previousValue = (V) e.getValueOrException();
          metrics.heapHitButNoRead();
        } else {
          peekMissCnt++;
        }
        putValue(e, _value);
        break;
      }
    }
    if (_hasFreshData) {
      recordHitLocked(e);
    }
    return returnValue(_previousValue);
  }

  @Override
  public V peekAndReplace(K key, V _value) {
    Entry e;
    for (;;) {
      e = lookupEntrySynchronized(key);
      if (e == null) { break; }
      synchronized (e) {
        e.waitForProcessing();
        if (e.isGone()) {
          continue;
        }
        if (e.hasFreshData()) {
          V _previousValue = (V) e.getValueOrException();
          putValue(e, _value);
          return returnValue(_previousValue);
        }
      }
      break;
    }
    synchronized (lock) {
      peekMissCnt++;
    }
    return null;
  }

  /**
   * Update the value directly within entry lock. Since we did not start
   * entry processing we do not need to notify any waiting threads.
   */
  private void putValue(final Entry e, final V _value) {
    long t = System.currentTimeMillis();
    insertOnPut(e, _value, t, t);
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

  final Entry DUMMY_ENTRY_NO_REPLACE = new org.cache2k.core.Entry();

  /**
   * replace if value matches. if value not matches, return the existing entry or the dummy entry.
   */
  protected Entry<K, V> replace(final K key, final boolean _compare, final V _oldValue, final V _newValue) {
    Entry e = lookupEntrySynchronized(key);
    if (e == null) {
      synchronized (lock) {
        peekMissCnt++;
      }
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
    Entry e = lookupEntrySynchronized(key);
    if (e == null) {
      peekMissCnt++;
      return null;
    }
    if (e.hasFreshData()) {
      return e;
    }
    peekHitNotFreshCnt++;
    return null;
  }

  @Override
  public boolean containsKey(K key) {
    Entry e = lookupEntrySynchronized(key);
    if (e != null) {
      metrics.containsButHit();
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

  @Override
  public boolean putIfAbsent(K key, V value) {
    for (;;) {
      Entry e = lookupOrNewEntrySynchronized(key);
      synchronized (e) {
        e.waitForProcessing();
        if (e.isGone()) {
          continue;
        }
        if (e.hasFreshData()) {
          return false;
        }
        peekMissCnt++;
        putValue(e, value);
        return true;
      }
    }
  }

  @Override
  public void put(K key, V value) {
    for (;;) {
      Entry e = lookupOrNewEntrySynchronized(key);
      synchronized (e) {
        e.waitForProcessing();
        if (e.isGone()) {
          continue;
        }
        if (!e.isVirgin()) {
          metrics.heapHitButNoRead();
        }
        putValue(e, value);
      }
      evictEventually();
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
    Entry e = lookupEntrySynchronizedNoHitRecord(key);
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
      synchronized (lock) {
        if (removeEntry(e)) {
          removedCnt++;
          metrics.remove();
        }
        return f;
      }
    }
  }

  @Override
  public void remove(K key) {
    containsAndRemove(key);
  }

  public V peekAndRemove(K key) {
    Entry e = lookupEntrySynchronized(key);
    if (e == null) {
      synchronized (lock) {
        peekMissCnt++;
      }
      return null;
    }
    synchronized (e) {
      e.waitForProcessing();
      if (e.isGone()) {
        synchronized (lock) {
          peekMissCnt++;
        }
        return null;
      }
      synchronized (lock) {
        V _value = null;
        boolean f = e.hasFreshData();
        if (f) {
          _value = (V) e.getValueOrException();
          recordHit(e);
        } else {
          peekMissCnt++;
        }
        if (removeEntry(e)) {
          removedCnt++;
          metrics.remove();
        }
        return returnValue(_value);
      }
    }
  }

  /**
   * True if we have spare threads in the thread pool that we can use for
   * prefetching. If we get an instruction to prefetch, only do so if there
   * are enough resources available, since we don't want to block out potentially
   * more important refresh tasks from executing.
   */
  boolean isLoaderThreadAvailableForPrefetching() {
    Executor _loaderExecutor = loaderExecutor;
    if (_loaderExecutor instanceof ThreadPoolExecutor) {
      ThreadPoolExecutor ex = (ThreadPoolExecutor) _loaderExecutor;
      return ex.getQueue().size() == 0;
    }
    if (_loaderExecutor instanceof DummyExecutor) {
      return true;
    }
    return false;
  }

  @Override
  public void prefetch(final K key) {
    if (loader == null) {
      return;
    }
    Entry<K,V> e = lookupEntrySynchronizedNoHitRecord(key);
    if (e != null && e.hasFreshData()) {
      return;
    }
    if (isLoaderThreadAvailableForPrefetching()) {
      loaderExecutor.execute(new RunWithCatch(this) {
        @Override
        public void action() {
          get(key);
        }
      });
    }
  }

  /**
   *
   */
  @Override
  public void prefetchAll(final Iterable<? extends K> _keys) {
    if (loader == null) {
      return;
    }
    Set<K> _keysToLoad = checkAllPresent(_keys);
    for (K k : _keysToLoad) {
      final K key = k;
      if (!isLoaderThreadAvailableForPrefetching()) {
        return;
      }
      Runnable r = new RunWithCatch(this) {
        @Override
        public void action() {
          getEntryInternal(key);
        }
      };
      loaderExecutor.execute(r);
    }
  }

  @Override
  public void loadAll(final Iterable<? extends K> _keys, final LoadCompletedListener l) {
    checkLoaderPresent();
    final LoadCompletedListener _listener= l != null ? l : DUMMY_LOAD_COMPLETED_LISTENER;
    Set<K> _keysToLoad = checkAllPresent(_keys);
    if (_keysToLoad.isEmpty()) {
      _listener.loadCompleted();
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
              _listener.loadCompleted();
            }
          }
        }
      };
      loaderExecutor.execute(r);
    }
  }

  @Override
  public void reloadAll(final Iterable<? extends K> _keys, final LoadCompletedListener l) {
    checkLoaderPresent();
    final LoadCompletedListener _listener= l != null ? l : DUMMY_LOAD_COMPLETED_LISTENER;
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
              _listener.loadCompleted();
            }
          }
        }
      };
      loaderExecutor.execute(r);
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
        cache.getLog().warn("Loader thread exception (" + Thread.currentThread().getName() + ")", t);
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
      Entry<K,V> e = lookupEntrySynchronizedNoHitRecord(k);
      if (e == null || !e.hasFreshData()) {
        _keysToLoad.add(k);
      }
    }
    return _keysToLoad;
  }

  public Entry<K, V> lookupEntryUnsynchronized(final K k) {
    return lookupEntryUnsynchronized(k, modifiedHash(k.hashCode()));
  }

  /**
   * Always fetch the value from the source. That is a copy of getEntryInternal
   * without freshness checks.
   */
  protected void loadAndReplace(K key) {
    Entry e;
    for (;;) {
      e = lookupOrNewEntrySynchronized(key);
      synchronized (e) {
        e.waitForProcessing();
        if (e.isGone()) {
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
    evictEventually();
  }

  /**
   * Lookup or create a new entry. The new entry is created, because we need
   * it for locking within the data fetch.
   */
  protected Entry<K, V> lookupOrNewEntrySynchronized(K key) {
    int hc = modifiedHash(key.hashCode());
    return lookupOrNewEntrySynchronized(key, hc);
  }

  protected Entry<K, V> lookupOrNewEntrySynchronized(K key, int hc) {
    Entry e = lookupEntryUnsynchronized(key, hc);
    if (e == null) {
      synchronized (lock) {
        checkClosed();
        e = lookupEntry(key, hc);
        if (e == null) {
          e = newEntry(key, hc);
        }
      }
    }
    return e;
  }

  protected Entry<K, V> lookupOrNewEntrySynchronizedNoHitRecord(K key) {
    int hc = modifiedHash(key.hashCode());
    Entry e = lookupEntryUnsynchronizedNoHitRecord(key, hc);
    if (e == null) {
      synchronized (lock) {
        checkClosed();
        e = lookupEntryNoHitRecord(key, hc);
        if (e == null) {
          e = newEntry(key, hc);
        }
      }
    }
    return e;
  }

  protected V returnValue(V v) {
    if (v instanceof ExceptionWrapper) {
      ExceptionWrapper w = (ExceptionWrapper) v;
      exceptionPropagator.propagateException(w.getKey(), w);
    }
    return v;
  }

  protected V returnValue(Entry<K, V> e) {
    V v = e.getValueOrException();
    if (v instanceof ExceptionWrapper) {
      ExceptionWrapper w = (ExceptionWrapper) v;
      exceptionPropagator.propagateException(w.getKey(), w);
    }
    return v;
  }

  protected Entry<K, V> lookupEntrySynchronized(K key) {
    int hc = modifiedHash(key.hashCode());
    Entry e = lookupEntryUnsynchronized(key, hc);
    if (e == null) {
      synchronized (lock) {
        checkClosed();
        e = lookupEntry(key, hc);
      }
    }
    return e;
  }

  protected Entry lookupEntrySynchronizedNoHitRecord(K key) {
    int hc = modifiedHash(key.hashCode());
    Entry e = lookupEntryUnsynchronizedNoHitRecord(key, hc);
    if (e == null) {
      synchronized (lock) {
        checkClosed();
        e = lookupEntryNoHitRecord(key, hc);
      }
    }
    return e;
  }

  protected final Entry<K, V> lookupEntry(K key, int hc) {
    Entry e = Hash.lookup(mainHash, key, hc);
    if (e != null) {
      recordHit(e);
      return e;
    }
    e = refreshHashCtrl.remove(refreshHash, key, hc);
    if (e != null) {
      refreshHitCnt++;
      mainHash = mainHashCtrl.insert(mainHash, e);
      recordHit(e);
      return e;
    }
    return null;
  }

  protected final Entry lookupEntryNoHitRecord(K key, int hc) {
    Entry e = Hash.lookup(mainHash, key, hc);
    if (e != null) {
      return e;
    }
    e = refreshHashCtrl.remove(refreshHash, key, hc);
    if (e != null) {
      refreshHitCnt++;
      mainHash = mainHashCtrl.insert(mainHash, e);
      return e;
    }
    return null;
  }


  /**
   * Insert new entry in all structures (hash and replacement list). May evict an
   * entry if the maximum capacity is reached.
   */
  protected Entry<K, V> newEntry(K key, int hc) {
    if (getLocalSize() >= maxSize) {
      evictionNeeded = true;
    }
    Entry e = checkForGhost(key, hc);
    if (e == null) {
      e = newEntry();
      e.key = key;
      e.hashCode = hc;
      insertIntoReplacementList(e);
    }
    mainHash = mainHashCtrl.insert(mainHash, e);
    newEntryCnt++;
    return e;
  }

  /**
   * Remove the entry from the hash table. The entry is already removed from the replacement list.
   * stop to timer, if needed. Called under big lock. The remove races with a clear. The clear
   * is not updating each entry state to e.isGone() but just drops the whole hash table instead.
   * This is why we return a flag whether the entry was really present or not at this time.
   *
   * <p>With completion of the method the entry content is no more visible. "Nulling" out the key
   * or value of the entry incorrect, since there can be another thread which is just about to
   * return the entries contents.
   *
   * @return True, if the entry was present in the hash table.
   */
  private boolean removeEntryFromHash(Entry<K, V> e) {
    boolean f = mainHashCtrl.remove(mainHash, e) || refreshHashCtrl.remove(refreshHash, e);
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
      if (keyMutationCount ==  0) {
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
      keyMutationCount++;
    }
  }

  protected void load(Entry<K, V> e) {
    V v;
    long t0 = System.currentTimeMillis();
    try {
      checkLoaderPresent();
      if (e.isVirgin()) {
        v = loader.load(e.key, t0, null);
      } else {
        v = loader.load(e.key, t0, e);
      }
    } catch (Throwable _ouch) {
      long t = System.currentTimeMillis();
      loadGotException(e, new ExceptionWrapper(e.key, _ouch, t0, e), t0, t);
      return;
    }
    long t = System.currentTimeMillis();
    insertOrUpdateAndCalculateExpiry(e, v, t0, t, INSERT_STAT_LOAD);
  }

  protected void loadGotException(Entry<K, V> e, ExceptionWrapper<K> _value, long t0, long t) {
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
      try {
        updateStatistics(e, (V) _value, t0, t, INSERT_STAT_LOAD, false);
      } catch (Throwable ignore) { }
      throw new ExpiryCalculationException(ex);
    }
    synchronized (e) {
      insertUpdateStats(e, (V) _value, t0, t, INSERT_STAT_LOAD, _nextRefreshTime, _suppressException);
      if (_suppressException) {
        e.setSuppressedLoadExceptionInformation(_value);
      } else {
        e.setValueOrException((V) _value);
      }
      _value.until = Math.abs(_nextRefreshTime);
      finishLoadOrEviction(e, _nextRefreshTime);
    }
  }

  private void checkLoaderPresent() {
    if (loader == null) {
      throw new UnsupportedOperationException("loader not set");
    }
  }

  protected final void insertOnPut(Entry<K, V> e, V v, long t0, long t) {
    insertOrUpdateAndCalculateExpiry(e, v, t0, t, INSERT_STAT_PUT);
  }

  /**
   * Calculate the next refresh time if a timer / expiry is needed and call insert.
   */
  protected final void insertOrUpdateAndCalculateExpiry(Entry<K, V> e, V v, long t0, long t, byte _updateStatistics) {
    long _nextRefreshTime;
    try {
      _nextRefreshTime = timing.calculateNextRefreshTime(e, v, t0);
    } catch (Exception ex) {
      try {
        updateStatistics(e, v, t0, t, _updateStatistics, false);
      } catch (Throwable ignore) { }
      throw new CacheException("exception in expiry calculation", ex);
    }
    insert(e, v, t0, t, _updateStatistics, _nextRefreshTime);
  }

  final static byte INSERT_STAT_LOAD = 1;
  final static byte INSERT_STAT_PUT = 2;

  protected final void insert(Entry<K, V> e, V _value, long t0, long t, byte _updateStatistics, long _nextRefreshTime) {
    if (_updateStatistics == INSERT_STAT_LOAD) {
      synchronized (e) {
        e.setLastModification(t0);
        insertUpdateStats(e, _value, t0, t, _updateStatistics, _nextRefreshTime, false);
        e.setValueOrException(_value);
        e.resetSuppressedLoadExceptionInformation();
        finishLoadOrEviction(e, _nextRefreshTime);
      }
    } else {
      e.setLastModification(t0);
      e.setValueOrException(_value);
      e.resetSuppressedLoadExceptionInformation();
      insertUpdateStats(e, _value, t0, t, _updateStatistics, _nextRefreshTime, false);
      restartTimer(e, _nextRefreshTime);
    }
  }

  private void insertUpdateStats(final Entry<K, V> e, final V _value, final long t0, final long t, final byte _updateStatistics, final long _nextRefreshTime, final boolean _suppressException) {
    synchronized (lock) {
      checkClosed();
      updateStatisticsNeedsLock(e, _value, t0, t, _updateStatistics, _suppressException);
      if (_updateStatistics == INSERT_STAT_PUT && !e.hasFreshData(t, _nextRefreshTime)) {
        putButExpiredCnt++;
      }
    } // synchronized (lock)
  }

  private void updateStatistics(Entry e, V _value, long t0, long t, byte _updateStatistics, boolean _suppressException) {
    synchronized (lock) {
      checkClosed();
      updateStatisticsNeedsLock(e, _value, t0, t, _updateStatistics, _suppressException);
    }
  }

  private void updateStatisticsNeedsLock(Entry e, V _value, long t0, long t, byte _updateStatistics, boolean _suppressException) {
    touchedTime = t;
    if (_updateStatistics == INSERT_STAT_LOAD) {
      if (_suppressException) {
        suppressedExceptionCnt++;
        loadExceptionCnt++;
      } else {
        if (_value instanceof ExceptionWrapper) {
          loadExceptionCnt++;
        }
      }
      fetchMillis += t - t0;
      if (e.isGettingRefresh()) {
        refreshCnt++;
      } else {
        loadWoRefreshCnt++;
        if (!e.isVirgin()) {
          loadButHitCnt++;
        }
      }
    } else if (_updateStatistics == INSERT_STAT_PUT) {
      metrics.putNewEntry();
      eventuallyAdjustPutNewEntryCount(e);
    }
  }

  private void eventuallyAdjustPutNewEntryCount(Entry e) {
    if (e.isVirgin()) {
      putNewEntryCnt++;
    }
  }

  /**
   * Move entry to a separate hash for the entries that got a refresh.
   * True, if successful. False, if the entry had a refresh already.
   */
  public boolean moveToRefreshHash(Entry e) {
    synchronized (lock) {
      if (isClosed()) {
        return false;
      }
      if (mainHashCtrl.remove(mainHash, e)) {
        refreshHash = refreshHashCtrl.insert(refreshHash, e);
        if (e.hashCode != modifiedHash(e.key.hashCode())) {
          if (removeEntryFromHash(e)) {
            expiredRemoveCnt++;
          }
          return false;
        }
        return true;
      }
    }
    return false;
  }

  @Override
  public void timerEventRefresh(final Entry<K, V> e) {
    metrics.timerEvent();
    synchronized (e) {
      if (e.isGone()) {
        return;
      }
      if (moveToRefreshHash(e)) {
        Runnable r = new Runnable() {
          @Override
          public void run() {
            synchronized (e) {
              e.waitForProcessing();
              if (e.isGone()) {
                return;
              }
              e.startProcessing(Entry.ProcessingState.REFRESH);
            }
            try {
              load(e);
            } catch (CacheClosedException ignore) {
            } catch (Throwable ex) {
              e.ensureAbort(false);
              logAndCountInternalException("Refresh exception", ex);
              try {
                synchronized (e) {
                  expireEntry(e);
                }
              } catch (CacheClosedException ignore) {
              }
            }
          }
        };
        try {
          loaderExecutor.execute(r);
          return;
        } catch (RejectedExecutionException ignore) {
        }
        refreshSubmitFailedCnt++;
      } else { // if (mainHashCtrl.remove(mainHash, e)) ...
      }
      expireOrScheduleFinalExpireEvent(e);
    }
  }

  @Override
  public void logAndCountInternalException(final String _text, final Throwable _exception) {
    metrics.internalException();
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
    if (nrt >= 0 && nrt < Entry.DATA_VALID) {
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
      timing.scheduleFinalExpiryTimer(e);
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
   */
  private void expireAndRemoveEventually(final Entry e) {
    synchronized (lock) {
      checkClosed();
      if (hasKeepAfterExpired() || e.isProcessing()) {
        expiredKeptCnt++;
      } else {
        if (removeEntry(e)) {
          expiredRemoveCnt++;
        }
      }
    }
  }

  /**
   * Returns all cache entries within the heap cache. Entries that
   * are expired or contain no valid data are not filtered out.
   */
  public final ConcurrentEntryIterator<K,V> iterateAllHeapEntries() {
    return new ConcurrentEntryIterator<K,V>(this);
  }

  @Override
  public void removeAllAtOnce(Set<K> _keys) {
    throw new UnsupportedOperationException();
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

  Specification<K,V> spec() { return Specification.SINGLETON; }

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
  public <R> R invoke(K key, CacheEntryProcessor<K, V, R> entryProcessor, Object... args) {
    return execute(key, spec().invoke(key, loader != null, entryProcessor, args));
  }

  @Override
  public void expire(final K key, final long _millis) {
    execute(key, spec().expire(key, _millis));
  }

  public abstract long getHitCnt();

  protected final int calculateHashEntryCount() {
    return Hash.calcEntryCount(mainHash) + Hash.calcEntryCount(refreshHash);
  }

  public final int getLocalSize() {
    return mainHashCtrl.size + refreshHashCtrl.size;
  }

  public final int getTotalEntryCount() {
    synchronized (lock) {
      checkClosed();
      return getLocalSize();
    }
  }

  public long getExpiredCnt() {
    return expiredRemoveCnt + expiredKeptCnt;
  }

  protected IntegrityState getIntegrityState() {
    synchronized (lock) {
      checkClosed();
      return new IntegrityState()
        .checkEquals("newEntryCnt == getSize() + evictedCnt + expiredRemoveCnt + removeCnt + clearedCnt + virginRemovedCnt", newEntryCnt, getLocalSize() + evictedCnt + expiredRemoveCnt + removedCnt + clearedCnt + virginRemovedCnt)
        .checkEquals("newEntryCnt == getSize() + evictedCnt + getExpiredCnt() - expiredKeptCnt + removeCnt + clearedCnt + virginRemovedCnt", newEntryCnt, getLocalSize() + evictedCnt + getExpiredCnt() - expiredKeptCnt + removedCnt + clearedCnt + virginRemovedCnt)
        .checkEquals("mainHashCtrl.size == Hash.calcEntryCount(mainHash)", mainHashCtrl.size, Hash.calcEntryCount(mainHash))
        .checkEquals("refreshHashCtrl.size == Hash.calcEntryCount(refreshHash)", refreshHashCtrl.size, Hash.calcEntryCount(refreshHash))
        .check("!!evictionNeeded | (getSize() <= maxSize)", !!evictionNeeded | (getLocalSize() <= maxSize));
    }
  }

  /** Check internal data structures and throw and exception if something is wrong, used for unit testing */
  public final void checkIntegrity() {
    synchronized (lock) {
      checkClosed();
      IntegrityState is = getIntegrityState();
      if (is.getStateFlags() > 0) {
        throw new CacheIntegrityError(is.getStateDescriptor(), is.getFailingChecks(), toString());
      }
    }
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
      checkClosed();
      long t = System.currentTimeMillis();
      if (info != null &&
        (info.creationTime + info.creationDeltaMs * TUNABLE.minimumStatisticsCreationTimeDeltaFactor + TUNABLE.minimumStatisticsCreationDeltaMillis > t)) {
        return info;
      }
      info = generateInfo(_userCache, t);
    }
    return info;
  }

  public final InternalCacheInfo getLatestInfo(InternalCache _userCache) {
    return generateInfo(_userCache, System.currentTimeMillis());
  }

  private CacheBaseInfo generateInfo(InternalCache _userCache, long t) {
    synchronized (lock) {
      checkClosed();
      info = new CacheBaseInfo(this, _userCache);
      info.creationTime = t;
      info.creationDeltaMs = (int) (System.currentTimeMillis() - t);
      return info;
    }
  }

  protected String getExtraStatistics() { return ""; }

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
      InternalCacheInfo fo = getLatestInfo();
      return fo.toString();
    }
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

    /**
     * Implementation class to use by default.
     */
    public Class<? extends InternalCache> defaultImplementation =
            "64".equals(System.getProperty("sun.arch.data.model"))
                    ? ClockProPlus64Cache.class : ClockProPlusCache.class;

    /**
     * Limits the number of spins until an entry lock is expected to
     * succeed. The limit is to detect deadlock issues during development
     * and testing. It is set to an arbitrary high value to result in
     * an exception after about one second of spinning.
     */
    public int maximumEntryLockSpins = 333333;

    /**
     * Maximum number of tries to find an entry for eviction if maximum size
     * is reached.
     */
    public int maximumEvictSpins = 5;

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
    public int loaderThreadCountCpuFactor = 2;


  }

}
