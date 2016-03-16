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

import org.cache2k.*;
import org.cache2k.impl.operation.ExaminationEntry;
import org.cache2k.impl.operation.Semantic;
import org.cache2k.impl.operation.Specification;
import org.cache2k.impl.threading.Futures;
import org.cache2k.impl.util.ThreadDump;

import org.cache2k.impl.util.Log;
import org.cache2k.impl.util.TunableConstants;
import org.cache2k.impl.util.TunableFactory;
import org.cache2k.storage.PurgeableStorage;
import org.cache2k.storage.StorageEntry;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.BitSet;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.cache2k.impl.util.Util.*;

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
public abstract class BaseCache<K, V>
  extends AbstractCache<K, V> {

  static final Random SEED_RANDOM = new Random(new SecureRandom().nextLong());
  static int cacheCnt = 0;

  protected static final Tunable TUNABLE = TunableFactory.get(Tunable.class);

  /**
   * Instance of expiry calculator that extracts the expiry time from the value.
   */
  final static EntryExpiryCalculator<?, ValueWithExpiryTime> ENTRY_EXPIRY_CALCULATOR_FROM_VALUE = new
    EntryExpiryCalculator<Object, ValueWithExpiryTime>() {
      @Override
      public long calculateExpiryTime(
          Object _key, ValueWithExpiryTime _value, long _fetchTime,
          CacheEntry<Object, ValueWithExpiryTime> _oldEntry) {
        return _value.getCacheExpiryTime();
      }
    };

  final static ExceptionPropagator DEFAULT_EXCEPTION_PROPAGATOR = new ExceptionPropagator() {
    @Override
    public void propagateException(String _additionalMessage, Throwable _originalException) {
      throw new PropagatedCacheException(_additionalMessage, _originalException);
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
  protected int maxSize = 5000;

  protected String name;
  protected CacheManagerImpl manager;
  protected AdvancedCacheLoader<K,V> loader;

  /** Statistics */

  /** Time in milliseconds we keep an element */
  protected long maxLinger = 10 * 60 * 1000;

  protected long exceptionMaxLinger = 1 * 60 * 1000;

  protected EntryExpiryCalculator<K, V> entryExpiryCalculator;

  protected ExceptionExpiryCalculator<K> exceptionExpiryCalculator;

  protected CacheBaseInfo info;

  protected long clearedTime = 0;
  protected long startedTime;
  protected long touchedTime;
  protected int timerCancelCount = 0;

  protected long keyMutationCount = 0;
  protected long putButExpiredCnt = 0;
  protected long putNewEntryCnt = 0;
  protected long removedCnt = 0;
  /** Number of entries removed by clear. */
  protected long clearedCnt = 0;
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

  protected long loadCnt = 0;

  protected long loadFailedCnt = 0;

  protected long loadButHitCnt = 0;

  protected long bulkGetCnt = 0;
  protected long fetchMillis = 0;
  protected long refreshHitCnt = 0;
  protected long newEntryCnt = 0;

  /**
   * Entries created for processing via invoke or replace, but no operation happened on it.
   * The entry processor may just have checked the entry state or an exception happened.
   */
  protected long atomicOpNewEntryCnt = 0;

  /**
   * Read from storage, but the entry was not fresh and cannot be returned.
   */
  protected long readNonFreshCnt = 0;

  /**
   * Entry was read from storage and fresh.
   */
  protected long readHitCnt = 0;

  /**
   * Separate counter for read entries that needed a fetch.
   */
  protected long readNonFreshAndFetchedCnt;

  /**
   * Storage did not contain the requested entry.
   */
  protected long readMissCnt = 0;

  protected long refreshSubmitFailedCnt = 0;

  /**
   * An exception that should not have happened and was not thrown to the
   * application. Only used for the refresh thread yet.
   */
  protected long internalExceptionCnt = 0;

  /**
   * Needed to correct the counter invariants, because during eviction the entry
   * might be removed from the replacement list, but still in the hash.
   */
  protected int evictedButInHashCnt = 0;

  /**
   * A newly inserted entry was removed by the eviction without the fetch to complete.
   */
  protected long virginEvictCnt = 0;

  protected long timerEvents = 0;

  protected int maximumBulkFetchSize = 100;

  CommonMetrics.Updater metrics = new StandardCommonMetrics();

  /**
   * Structure lock of the cache. Every operation that needs a consistent structure
   * of the cache or modifies it needs to synchronize on this. Since this is a global
   * lock, locking on it should be avoided and any operation under the lock should be
   * quick.
   */
  protected final Object lock = new Object();

  protected CacheRefreshThreadPool refreshPool;

  protected Hash<Entry<K, V>> mainHashCtrl;
  protected Entry<K, V>[] mainHash;

  protected Hash<Entry<K, V>> refreshHashCtrl;
  protected Entry<K, V>[] refreshHash;

  protected Timer timer;

  /** Stuff that we need to wait for before shutdown may complete */
  protected Futures.WaitForAllFuture<?> shutdownWaitFuture;

  protected boolean shutdownInitiated = false;

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

  private static final int SHARP_TIMEOUT_FEATURE = 1;
  private static final int KEEP_AFTER_EXPIRED = 2;
  private static final int SUPPRESS_EXCEPTIONS = 4;
  private static final int NULL_VALUE_SUPPORT = 8;

  protected final boolean hasSharpTimeout() {
    return (featureBits & SHARP_TIMEOUT_FEATURE) > 0;
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

  protected final void setFeatureBit(int _bitmask, boolean _flag) {
    if (_flag) {
      featureBits |= _bitmask;
    } else {
      featureBits &= ~_bitmask;
    }
  }

  /**
   * Enabling background refresh means also serving expired values.
   */
  protected final boolean hasBackgroundRefreshAndServesExpiredValues() {
    return refreshPool != null;
  }

  /**
   * Returns name of the cache with manager name.
   */
  protected String getCompleteName() {
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

  /** called via reflection from CacheBuilder */
  public void setCacheConfig(CacheConfig c) {
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
    if (c.isBackgroundRefresh()) {
      refreshPool = CacheRefreshThreadPool.getInstance();
    }
    long _expiryMillis  = c.getExpiryMillis();
    if (_expiryMillis == Long.MAX_VALUE || _expiryMillis < 0) {
      maxLinger = -1;
    } else if (_expiryMillis >= 0) {
      maxLinger = _expiryMillis;
    }
    long _exceptionExpiryMillis = c.getExceptionExpiryMillis();
    if (_exceptionExpiryMillis == -1) {
      if (maxLinger == -1) {
        exceptionMaxLinger = -1;
      } else {
        exceptionMaxLinger = maxLinger / 10;
      }
    } else {
      exceptionMaxLinger = _exceptionExpiryMillis;
    }
    setFeatureBit(KEEP_AFTER_EXPIRED, c.isKeepDataAfterExpired());
    setFeatureBit(SHARP_TIMEOUT_FEATURE, c.isSharpExpiry());
    setFeatureBit(SUPPRESS_EXCEPTIONS, c.isSuppressExceptions());
    if (ValueWithExpiryTime.class.isAssignableFrom(c.getValueType().getType()) &&
        entryExpiryCalculator == null)  {
      entryExpiryCalculator =
        (EntryExpiryCalculator<K, V>)
        ENTRY_EXPIRY_CALCULATOR_FROM_VALUE;
    }
  }

  public void setEntryExpiryCalculator(EntryExpiryCalculator<K, V> v) {
    entryExpiryCalculator = v;
  }

  public void setExceptionExpiryCalculator(ExceptionExpiryCalculator<K> v) {
    exceptionExpiryCalculator = v;
  }

  /** called via reflection from CacheBuilder */
  public void setRefreshController(final RefreshController<V> lc) {
    entryExpiryCalculator = new EntryExpiryCalculator<K, V>() {
      @Override
      public long calculateExpiryTime(K _key, V _value, long _fetchTime, CacheEntry<K, V> _oldEntry) {
        if (_oldEntry != null) {
          return lc.calculateNextRefreshTime(_oldEntry.getValue(), _value, _oldEntry.getLastModification(), _fetchTime);
        } else {
          return lc.calculateNextRefreshTime(null, _value, 0L, _fetchTime);
        }
      }
    };
  }

  public void setExceptionPropagator(ExceptionPropagator ep) {
    exceptionPropagator = ep;
  }

  @SuppressWarnings("unused")
  public void setSource(final CacheSourceWithMetaInfo<K, V> eg) {
    if (eg == null) {
      return;
    }
    loader = new AdvancedCacheLoader<K, V>() {
      @Override
      public V load(final K key, final long currentTime, final CacheEntry<K, V> previousEntry) throws Exception {
        try {
          if (previousEntry != null && previousEntry.getException() == null) {
            return eg.get(key, currentTime, previousEntry.getValue(), previousEntry.getLastModification());
          } else {
            return eg.get(key, currentTime, null, 0);
          }
        } catch (Exception e) {
          throw e;
        } catch (Throwable t) {
          throw new RuntimeException("rethrow throwable", t);
        }
      }
    };
  }

  @SuppressWarnings("unused")
  public void setSource(final CacheSource<K, V> g) {
    if (g == null) {
      return;
    }
    loader = new AdvancedCacheLoader<K, V>() {
      @Override
      public V load(final K key, final long currentTime, final CacheEntry<K, V> previousEntry) throws Exception {
        try {
          return g.get(key);
        } catch (Exception e) {
          throw e;
        } catch (Throwable t) {
          throw new RuntimeException("rethrow throwable", t);
        }
      }
    };
  }

  @SuppressWarnings("unused")
  public void setExperimentalBulkCacheSource(final ExperimentalBulkCacheSource<K, V> g) {
    if (loader == null) {
      loader = new AdvancedCacheLoader<K, V>() {
        @Override
        public V load(final K key, final long currentTime, final CacheEntry<K, V> previousEntry) throws Exception {
          K[] ka = (K[]) Array.newInstance(keyType, 1);
          ka[0] = key;
          V[] ra = (V[]) Array.newInstance(valueType, 1);
          BitSet _fetched = new BitSet(1);
          g.getBulk(ka, ra, _fetched, 0, 1);
          return ra[0];
        }
      };
    }
  }

  public void setBulkCacheSource(final BulkCacheSource<K, V> s) {
    if (loader == null) {
      loader = new AdvancedCacheLoader<K, V>() {
        @Override
        public V load(final K key, final long currentTime, final CacheEntry<K, V> previousEntry) throws Exception {
          CacheEntry<K, V> entry = previousEntry;
          if (previousEntry == null)
            entry = new CacheEntry<K, V>() {
            @Override
            public K getKey() {
              return key;
            }

            @Override
            public V getValue() {
              return null;
            }

            @Override
            public Throwable getException() {
              return null;
            }

            @Override
            public long getLastModification() {
              return 0;
            }
          };
          try {
            return s.getValues(Collections.singletonList(entry), currentTime).get(0);
          } catch (Exception e) {
            throw e;
          } catch (Throwable t) {
            throw new RuntimeException("rethrow throwable", t);
          }
        }
      };
    }
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

      initializeHeapCache();
      initTimer();
      if (refreshPool != null &&
        loader == null) {
        throw new CacheMisconfigurationException("backgroundRefresh, but no loader defined");
      }
      if (refreshPool != null && timer == null) {
        if (maxLinger == 0) {
          getLog().warn("Background refresh is enabled, but elements are fetched always. Disable background refresh!");
        } else {
          getLog().warn("Background refresh is enabled, but elements are eternal. Disable background refresh!");
        }
        refreshPool.destroy();
        refreshPool = null;
      }
    }
  }

  boolean isNeedingTimer() {
    return
        maxLinger > 0 || entryExpiryCalculator != null ||
        exceptionMaxLinger > 0 || exceptionExpiryCalculator != null;
  }

  /**
   * Either add a timer or remove the timer if needed or not needed.
   */
  private void initTimer() {
    if (isNeedingTimer()) {
      if (timer == null) {
        timer = new Timer(name, true);
      }
    } else {
      if (timer != null) {
        timer.cancel();
        timer = null;
      }
    }
  }

  protected void updateShutdownWaitFuture(Future<?> f) {
    synchronized (lock) {
      if (shutdownWaitFuture == null || shutdownWaitFuture.isDone()) {
        shutdownWaitFuture = new Futures.WaitForAllFuture(f);
      } else {
        shutdownWaitFuture.add((Future) f);
      }
    }
  }

  protected void checkClosed() {
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

  protected final void clearLocalCache() {
    iterateAllEntriesRemoveAndCancelTimer();
    clearedCnt += getLocalSize();
    initializeHeapCache();
    clearedTime = System.currentTimeMillis();
    touchedTime = clearedTime;
  }

  protected void iterateAllEntriesRemoveAndCancelTimer() {
    Iterator<org.cache2k.impl.Entry> it = iterateAllHeapEntries();
    int _count = 0;
    while (it.hasNext()) {
      org.cache2k.impl.Entry e = it.next();
      e.removedFromList();
      cancelExpiryTimer(e);
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
    if (timer != null) {
      timer.cancel();
      timer = null;
      initTimer();
    }
  }

  @Override
  public void clearTimingStatistics() {
    synchronized (lock) {
      loadCnt = 0;
      fetchMillis = 0;
    }
  }

  /**
   * Preparation for shutdown. Cancel all pending timer jobs e.g. for
   * expiry/refresh or flushing the storage.
   */
  @Override
  public Future<Void> cancelTimerJobs() {
    synchronized (lock) {
      if (timer != null) {
        timer.cancel();
      }
      Future<Void> _waitFuture = new Futures.BusyWaitFuture<Void>() {
        @Override
        public boolean isDone() {
          synchronized (lock) {
            return getFetchesInFlight() == 0;
          }
        }
      };
      return _waitFuture;
    }
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
    Future<Void> _await = cancelTimerJobs();
    try {
      _await.get(TUNABLE.waitForTimerJobsSeconds, TimeUnit.SECONDS);
    } catch (TimeoutException ex) {
      int _fetchesInFlight;
      synchronized (lock) {
        _fetchesInFlight = getFetchesInFlight();
      }
      if (_fetchesInFlight > 0) {
        getLog().warn(
          "Fetches still in progress after " +
            TUNABLE.waitForTimerJobsSeconds + " seconds. " +
            "fetchesInFlight=" + _fetchesInFlight);
      } else {
        getLog().warn(
          "timeout waiting for timer jobs termination" +
            " (" + TUNABLE.waitForTimerJobsSeconds + " seconds)", ex);
        getLog().warn("Thread dump:\n" + ThreadDump.generateThredDump());
      }
    } catch (Exception ex) {
      getLog().warn("exception waiting for timer jobs termination", ex);
    }
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
      if (timer != null) {
        timer.cancel();
        timer = null;
      }
      if (refreshPool != null) {
        refreshPool.destroy();
        refreshPool = null;
      }
      mainHash = refreshHash = null;
      loader = null;
      if (manager != null) {
        manager.cacheDestroyed(this);
        manager = null;
      }
    }
  }

  @Override
  public ClosableIterator<CacheEntry<K, V>> iterator() {
    synchronized (lock) {
      return new IteratorFilterEntry2Entry(this, (ClosableIterator<Entry>) iterateAllHeapEntries(), true);
    }
  }

  /**
   * Filter out non valid entries and wrap each entry with a cache
   * entry object.
   */
  static class IteratorFilterEntry2Entry<K,V> implements ClosableIterator<CacheEntry<K, V>> {

    BaseCache<K,V> cache;
    ClosableIterator<Entry> iterator;
    Entry entry;
    CacheEntry<K, V> lastEntry;
    boolean filter = true;

    IteratorFilterEntry2Entry(BaseCache<K,V> c, ClosableIterator<Entry> it, boolean _filter) {
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
      close();
      return false;
    }

    @Override
    public void close() {
      if (iterator != null) {
        iterator.close();
        iterator = null;
      }
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
    org.cache2k.impl.Entry e = _head.next;
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

  protected static final <E extends org.cache2k.impl.Entry> void moveToFront(final E _head, final E e) {
    removeFromList(e);
    insertInList(_head, e);
  }

  protected static final <E extends org.cache2k.impl.Entry> E insertIntoTailCyclicList(final E _head, final E e) {
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
  protected static final <E extends org.cache2k.impl.Entry> E insertAfterHeadCyclicList(final E _head, final E e) {
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
  protected static final <E extends org.cache2k.impl.Entry> E insertIntoHeadCyclicList(final E _head, final E e) {
    if (_head == null) {
      return (E) e.shortCircuit();
    }
    e.next = _head;
    e.prev = _head.prev;
    _head.prev.next = e;
    _head.prev = e;
    return e;
  }

  protected static <E extends org.cache2k.impl.Entry> E removeFromCyclicList(final E _head, E e) {
    if (e.next == e) {
      e.removedFromList();
      return null;
    }
    org.cache2k.impl.Entry _eNext = e.next;
    e.prev.next = _eNext;
    e.next.prev = e.prev;
    e.removedFromList();
    return e == _head ? (E) _eNext : _head;
  }

  protected static org.cache2k.impl.Entry removeFromCyclicList(final org.cache2k.impl.Entry e) {
    org.cache2k.impl.Entry _eNext = e.next;
    e.prev.next = _eNext;
    e.next.prev = e.prev;
    e.removedFromList();
    return _eNext == e ? null : _eNext;
  }

  protected static int getCyclicListEntryCount(org.cache2k.impl.Entry e) {
    if (e == null) { return 0; }
    final org.cache2k.impl.Entry _head = e;
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

  protected static boolean checkCyclicListIntegrity(org.cache2k.impl.Entry e) {
    if (e == null) { return true; }
    org.cache2k.impl.Entry _head = e;
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
   * After doing some checks the cache will call {@link #removeEntryFromReplacementList(org.cache2k.impl.Entry)}
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

  public String getEntryState(K key) {
    Entry e = getEntryInternal(key);
    return generateEntryStateString(e);
  }

  private String generateEntryStateString(Entry<K, V> e) {
    synchronized (e) {
      String _timerState = "n/a";
      if (e.task != null) {
        _timerState = "<unavailable>";
        try {
          Field f = TimerTask.class.getDeclaredField("state");
          f.setAccessible(true);
          int _state = f.getInt(e.task);
          _timerState = String.valueOf(_state);
        } catch (Exception x) {
          _timerState = x.toString();
        }
      }
      return
          "Entry{" + System.identityHashCode(e) + "}, " +
          "keyIdentityHashCode=" + System.identityHashCode(e.key) + ", " +
          "valueIdentityHashCode=" + System.identityHashCode(e.value) + ", " +
          "keyHashCode" + e.key.hashCode() + ", " +
          "keyModifiedHashCode=" + e.hashCode + ", " +
          "keyMutation=" + (modifiedHash(e.key.hashCode()) != e.hashCode) + ", " +
          "modified=" + e.getLastModification() + ", " +
          "nextRefreshTime(with state)=" + e.nextRefreshTime + ", " +
          "hasTimer=" + (e.task != null ? "true" : "false") + ", " +
          "timerState=" + _timerState;
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
    long _previousNextRefreshTime;
    Entry e;
    for (;;) {
      e = lookupOrNewEntrySynchronized(key);
      if (e.hasFreshData()) {
        return e;
      }
      synchronized (e) {
        e.waitForFetch();
        if (e.hasFreshData()) {
          return e;
        }
        if (e.isGone()) {
          continue;
        }
        _previousNextRefreshTime = e.startFetch();
        break;
      }
    }
    boolean _finished = false;
    try {
      finishFetch(e, fetch(e, _previousNextRefreshTime));
      _finished = true;
    } finally {
      e.ensureFetchAbort(_finished, _previousNextRefreshTime);
    }
    evictEventually();
    return e;
  }

  protected void finishFetch(Entry e, long _nextRefreshTime) {
    synchronized (e) {
      e.nextRefreshTime = stopStartTimer(_nextRefreshTime, e, System.currentTimeMillis());
      e.processingDone();
      e.notifyAll();
    }
  }

  /** Always fetch the value from the source. That is a copy of getEntryInternal without fresh checks. */
  protected void fetchAndReplace(K key) {
    long _previousNextRefreshTime;
    Entry e;
    for (;;) {
      e = lookupOrNewEntrySynchronized(key);
      synchronized (e) {
        e.waitForFetch();
        if (e.isGone()) {
          continue;
        }
        _previousNextRefreshTime = e.startFetch();
        break;
      }
    }
    boolean _finished = false;
    try {
      finishFetch(e, fetch(e, _previousNextRefreshTime));
      _finished = true;
    } finally {
      e.ensureFetchAbort(_finished, _previousNextRefreshTime);
    }
    evictEventually();
  }

  /**
   * Insert a cache entry for the given key and run action under the entry
   * lock. If the cache entry has fresh data, we do not run the action.
   * Called from storage. The entry referenced by the key is expired and
   * will be purged.
   */
  protected void lockAndRunForPurge(Object key, PurgeableStorage.PurgeAction _action) {
    int _spinCount = TUNABLE.maximumEntryLockSpins;
    Entry e;
    boolean _virgin;
    for (;;) {
      if (_spinCount-- <= 0) { throw new CacheLockSpinsExceededError(); }
      e = lookupOrNewEntrySynchronized((K) key);
      if (e.hasFreshData()) { return; }
      synchronized (e) {
        e.waitForFetch();
        if (e.isDataValidState()) {
          return;
        }
        if (e.isGone()) {
          continue;
        }
        _virgin = e.isVirgin();
        e.startFetch();
        break;
      }
    }
    boolean _finished = false;
    try {
      StorageEntry se = _action.checkAndPurge(key);
      synchronized (e) {
        if (_virgin) {
          finishFetch(e, Entry.VIRGIN_STATE);
          evictEntryFromHeap(e);
        } else {
          finishFetch(e, Entry.READ_NON_VALID);
          evictEntryFromHeap(e);
        }
      }
      _finished = true;
    } finally {
      e.ensureFetchAbort(_finished);
    }
  }

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
        if (e.isPinned()) {
          if (e != _previousCandidate) {
            _previousCandidate = e;
            continue;
          } else {
            return;
          }
        }
        e.startFetch(Entry.ProcessingState.EVICT);
      }
      listener.onEvictionFromHeap(e);
      synchronized (e) {
        finishFetch(e, org.cache2k.impl.Entry.FETCH_ABORT);
        evictEntryFromHeap(e);
      }
    }
  }

  private void evictEntryFromHeap(Entry e) {
    synchronized (lock) {
      if (e.isRemovedFromReplacementList()) {
        if (removeEntryFromHash(e)) {
          evictedButInHashCnt--;
          evictedCnt++;
        }
      } else {
        if (removeEntry(e)) {
          evictedCnt++;
        }
      }
      evictionNeeded = getLocalSize() > maxSize;
    }
    e.notifyAll();
  }

  /**
   * Remove the entry from the hash and the replacement list.
   * There is a race condition to catch: The eviction may run
   * in a parallel thread and may have already selected this
   * entry.
   */
  protected boolean removeEntry(Entry e) {
    if (!e.isRemovedFromReplacementList()) {
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
        e.waitForFetch();
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
        e.waitForFetch();
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

  private void putValue(final Entry _e, final V _value) {
    long t = System.currentTimeMillis();
    long _newNrt = insertOnPut(_e, _value, t, t, _e.nextRefreshTime);
    _e.nextRefreshTime = stopStartTimer(_newNrt, _e, System.currentTimeMillis());
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

  final Entry DUMMY_ENTRY_NO_REPLACE = new org.cache2k.impl.Entry();

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
      e.waitForFetch();
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
  public boolean contains(K key) {
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
        e.waitForFetch();
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
        e.waitForFetch();
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
   * Remove the object mapped to a key from the cache.
   *
   * <p>Operation with storage: If there is no entry within the cache there may
   * be one in the storage, so we need to send the remove to the storage. However,
   * if a remove() and a get() is going on in parallel it may happen that the entry
   * gets removed from the storage and added again by the tail part of get(). To
   * keep the cache and the storage consistent it must be ensured that this thread
   * is the only one working on the entry.
   */
  public boolean removeWithFlag(K key, boolean _checkValue, V _value) {
    Entry e = lookupEntrySynchronizedNoHitRecord(key);
    if (e == null) {
      return false;
    }
    synchronized (e) {
      e.waitForFetch();
      if (e.isGone()) {
        return false;
      }
      synchronized (lock) {
        boolean f = e.hasFreshData();
        if (_checkValue) {
          if (!f || !e.equalsValue(_value)) {
            return false;
          }
        }
        if (removeEntry(e)) {
          removedCnt++;
          metrics.remove();
          return f;
        }
        return false;
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
      e.waitForFetch();
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

  @Override
  public void prefetch(final K key) {
    if (refreshPool == null ||
        lookupEntrySynchronized(key) != null) {
      return;
    }
    Runnable r = new Runnable() {
      @Override
      public void run() {
        get(key);
      }
    };
    refreshPool.submit(r);
  }

  public void prefetch(final List<? extends K> keys, final int _startIndex, final int _endIndexExclusive) {
    if (keys.isEmpty() || _startIndex == _endIndexExclusive) {
      return;
    }
    if (keys.size() <= _endIndexExclusive) {
      throw new IndexOutOfBoundsException("end > size");
    }
    if (_startIndex > _endIndexExclusive) {
      throw new IndexOutOfBoundsException("start > end");
    }
    if (_startIndex > 0) {
      throw new IndexOutOfBoundsException("end < 0");
    }
    Set<K> ks = new AbstractSet<K>() {
      @Override
      public Iterator<K> iterator() {
        return new Iterator<K>() {
          int idx = _startIndex;
          @Override
          public boolean hasNext() {
            return idx < _endIndexExclusive;
          }

          @Override
          public K next() {
            return keys.get(idx++);
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }

      @Override
      public int size() {
        return _endIndexExclusive - _startIndex;
      }
    };
    prefetch(ks);
  }

  @Override
  public void fetchAll(Set<? extends K> _keys, boolean replaceExistingValues, FetchCompletedListener l) {
    if (replaceExistingValues) {
      for (K k : _keys) {
        fetchAndReplace(k);
      }
      if (l != null) {
        l.fetchCompleted();
      }
    } else {
      prefetch(_keys, l);
    }
  }

  @Override
  public void prefetch(final Set<? extends K> keys) {
    prefetch(keys, null);
  }

  void prefetch(final Set<? extends K> keys, final FetchCompletedListener l) {
    if (refreshPool == null) {
      getAll(keys);
      if (l != null) {
        l.fetchCompleted();
      }
      return;
    }
    boolean _complete = true;
    for (K k : keys) {
      if (lookupEntryUnsynchronized(k, modifiedHash(k.hashCode())) == null) {
        _complete = false; break;
      }
    }
    if (_complete) {
      if (l != null) {
        l.fetchCompleted();
      }
      return;
    }
    Runnable r = new Runnable() {
      @Override
      public void run() {
        getAll(keys);
        if (l != null) {
          l.fetchCompleted();
        }
      }
    };
    refreshPool.submit(r);
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

  protected Entry<K, V> lookupOrOptionalNewEntrySynchronized(K key, boolean _newEntry) {
    int hc = modifiedHash(key.hashCode());
    Entry e = lookupEntryUnsynchronized(key, hc);
    if (e == null) {
      synchronized (lock) {
        checkClosed();
        e = lookupEntry(key, hc);
        if (e == null && _newEntry) {
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
      if (w.additionalExceptionMessage == null) {
        long t = w.until;
        if (t > 0) {
          w.additionalExceptionMessage = "(expiry=" + (t > 0 ? formatMillis(t) : "none") + ") " + w.getException();
        } else {
          w.additionalExceptionMessage = w.getException() + "";
        }
      }
      exceptionPropagator.propagateException(w.additionalExceptionMessage, w.getException());
    }
    return v;
  }

  protected V returnValue(Entry<K, V> e) {
    V v = e.value;
    if (v instanceof ExceptionWrapper) {
      ExceptionWrapper w = (ExceptionWrapper) v;
      if (w.additionalExceptionMessage == null) {
        synchronized (e) {
          long t = e.getValueExpiryTime();
          w.additionalExceptionMessage = "(expiry=" + (t > 0 ? formatMillis(t) : "none") + ") " + w.getException();
        }
      }
      exceptionPropagator.propagateException(w.additionalExceptionMessage, w.getException());
    }
    return v;
  }

  protected Entry<K, V> lookupEntrySynchronized(K key) {
    int hc = modifiedHash(key.hashCode());
    Entry e = lookupEntryUnsynchronized(key, hc);
    if (e == null) {
      synchronized (lock) {
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
   * Called when expiry of an entry happens. Remove it from the
   * main cache, refresh cache and from the (lru) list. Also cancel the timer.
   * Called under big lock.
   */

  /**
   * The entry is already removed from the replacement list. stop/reset timer, if needed.
   * Called under big lock.
   */
  private boolean removeEntryFromHash(Entry<K, V> e) {
    boolean f = mainHashCtrl.remove(mainHash, e) || refreshHashCtrl.remove(refreshHash, e);
    checkForHashCodeChange(e);
    cancelExpiryTimer(e);
    if (e.isVirgin()) {
      virginEvictCnt++;
    }
    e.setGone();
    return f;
  }

  protected final void cancelExpiryTimer(Entry<K, V> e) {
    if (e.task != null) {
      e.task.cancel();
      timerCancelCount++;
      if (timerCancelCount >= 10000) {
        timer.purge();
        timerCancelCount = 0;
      }
      e.task = null;
    }
  }

  /**
   * Check whether the key was modified during the stay of the entry in the cache.
   * We only need to check this when the entry is removed, since we expect that if
   * the key has changed, the stored hash code in the cache will not match any more and
   * the item is evicted very fast.
   */
  private void checkForHashCodeChange(Entry<K, V> e) {
    if (modifiedHash(e.key.hashCode()) != e.hashCode && !e.isStale()) {
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

  /**
   * Time when the element should be fetched again from the underlying storage.
   * If 0 then the object should not be cached at all. -1 means no expiry.
   *
   * @param _newObject might be a fetched value or an exception wrapped into the {@link ExceptionWrapper}
   */
  static <K, T>  long calcNextRefreshTime(
      K _key, T _newObject, long now, org.cache2k.impl.Entry _entry,
      EntryExpiryCalculator<K, T> ec, long _maxLinger,
      ExceptionExpiryCalculator<K> _exceptionEc, long _exceptionMaxLinger) {
    if (!(_newObject instanceof ExceptionWrapper)) {
      if (_maxLinger == 0) {
        return 0;
      }
      if (ec != null) {
        long t = ec.calculateExpiryTime(_key, _newObject, now, _entry);
        return limitExpiryToMaxLinger(now, _maxLinger, t);
      }
      if (_maxLinger > 0) {
        return _maxLinger + now;
      }
      return -1;
    }
    if (_exceptionMaxLinger == 0) {
      return 0;
    }
    if (_exceptionEc != null) {
      ExceptionWrapper _wrapper = (ExceptionWrapper) _newObject;
      long t = _exceptionEc.calculateExpiryTime(_key, _wrapper.getException(), now);
      t = limitExpiryToMaxLinger(now, _exceptionMaxLinger, t);
      return t;
    }
    if (_exceptionMaxLinger > 0) {
      return _exceptionMaxLinger + now;
    } else {
      return _exceptionMaxLinger;
    }
  }

  static long limitExpiryToMaxLinger(long now, long _maxLinger, long t) {
    if (_maxLinger > 0) {
      long _tMaximum = _maxLinger + now;
      if (t > _tMaximum) {
        return _tMaximum;
      }
      if (t < -1 && -t > _tMaximum) {
        return -_tMaximum;
      }
    }
    return t;
  }

  protected long calcNextRefreshTime(K _key, V _newObject, long now, Entry _entry) {
    return calcNextRefreshTime(
        _key, _newObject, now, _entry,
        entryExpiryCalculator, maxLinger,
        exceptionExpiryCalculator, exceptionMaxLinger);
  }

  protected long fetch(final Entry e, long _previousNextRefreshTime) {
    return fetchFromSource(e, _previousNextRefreshTime);
  }

  protected long fetchFromSource(Entry<K, V> e, long _previousNextRefreshValue) {
    V v;
    long t0 = System.currentTimeMillis();
    try {
      if (loader == null) {
        throw new CacheUsageExcpetion("loader not set");
      }
      if (e.isVirgin()) {
        v = loader.load((K) e.key, t0, null);
      } else {
        v = loader.load((K) e.key, t0, e);
      }
      e.setLastModification(t0);
    } catch (Throwable _ouch) {
      v = (V) new ExceptionWrapper(_ouch);
    }
    long t = System.currentTimeMillis();
    return insertOrUpdateAndCalculateExpiry(e, v, t0, t, INSERT_STAT_UPDATE, _previousNextRefreshValue);
  }

  protected final long insertOnPut(Entry<K, V> e, V v, long t0, long t, long _previousNextRefreshValue) {
    e.setLastModification(t0);
    return insertOrUpdateAndCalculateExpiry(e, v, t0, t, INSERT_STAT_PUT, _previousNextRefreshValue);
  }

  /**
   * Calculate the next refresh time if a timer / expiry is needed and call insert.
   */
  protected final long insertOrUpdateAndCalculateExpiry(Entry<K, V> e, V v, long t0, long t, byte _updateStatistics, long _previousNextRefreshTime) {
    long _nextRefreshTime = maxLinger == 0 ? 0 : Long.MAX_VALUE;
    if (timer != null) {
      try {
        _nextRefreshTime = calculateNextRefreshTime(e, v, t0, _previousNextRefreshTime);
      } catch (Exception ex) {
        updateStatistics(e, v, t0, t, _updateStatistics, false);
        throw new CacheException("exception in expiry calculation", ex);
      }
    }
    return insert(e, v, t0, t, _updateStatistics, _nextRefreshTime);
  }

  /**
   * @throws Exception any exception from the ExpiryCalculator
   */
  long calculateNextRefreshTime(Entry<K, V> _entry, V _newValue, long t0, long _previousNextRefreshTime) {
    long _nextRefreshTime;
    if (Entry.isDataValidState(_previousNextRefreshTime) || Entry.isExpiredState(_previousNextRefreshTime)) {
      _nextRefreshTime = calcNextRefreshTime(_entry.getKey(), _newValue, t0, _entry);
    } else {
      _nextRefreshTime = calcNextRefreshTime(_entry.getKey(), _newValue, t0, null);
    }
    return _nextRefreshTime;
  }

  final static byte INSERT_STAT_NO_UPDATE = 0;
  final static byte INSERT_STAT_UPDATE = 1;
  final static byte INSERT_STAT_PUT = 2;

  /**
   * @param _nextRefreshTime -1/MAXVAL: eternal, 0: expires immediately
   */
  protected final long insert(Entry<K, V> e, V _value, long t0, long t, byte _updateStatistics, long _nextRefreshTime) {
    if (_nextRefreshTime == -1) {
      _nextRefreshTime = Long.MAX_VALUE;
    }
    final boolean _suppressException =
      needsSuppress(e, _value);

    if (!_suppressException) {
      e.value = _value;
    }
    if (_value instanceof ExceptionWrapper && !_suppressException) {
      Log log = getLog();
      if (log.isDebugEnabled()) {
        log.debug(
            "source caught exception, expires at: " + formatMillis(_nextRefreshTime),
            ((ExceptionWrapper) _value).getException());
      }
    }

    synchronized (lock) {
      checkClosed();
      updateStatisticsNeedsLock(e, _value, t0, t, _updateStatistics, _suppressException);
      if (_nextRefreshTime == 0) {
        _nextRefreshTime = Entry.FETCH_NEXT_TIME_STATE;
      } else {
        if (_nextRefreshTime == Long.MAX_VALUE) {
          _nextRefreshTime = Entry.FETCHED_STATE;
        }
      }
      if (_updateStatistics == INSERT_STAT_PUT && !e.hasFreshData(t, _nextRefreshTime)) {
        putButExpiredCnt++;
      }
    } // synchronized (lock)

    return _nextRefreshTime;
  }

  boolean needsSuppress(final Entry<K, V> e, final V _value) {
    return _value instanceof ExceptionWrapper && hasSuppressExceptions() && e.getValue() != Entry.INITIAL_VALUE && !e.hasException();
  }

  private void updateStatistics(Entry e, V _value, long t0, long t, byte _updateStatistics, boolean _suppressException) {
    synchronized (lock) {
      updateStatisticsNeedsLock(e, _value, t0, t, _updateStatistics, _suppressException);
    }
  }

  private void updateStatisticsNeedsLock(Entry e, V _value, long t0, long t, byte _updateStatistics, boolean _suppressException) {
    touchedTime = t;
    if (_updateStatistics == INSERT_STAT_UPDATE) {
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
        loadCnt++;
        if (e.isLoadedNonValidAndFetch()) {
          readNonFreshAndFetchedCnt++;
        } else if (!e.isVirgin()) {
          loadButHitCnt++;
        }
      }
    } else if (_updateStatistics == INSERT_STAT_PUT) {
      metrics.putNewEntry();
      eventuallyAdjustPutNewEntryCount(e);
      if (e.nextRefreshTime == Entry.LOADED_NON_VALID_AND_PUT) {
        peekHitNotFreshCnt++;
      }
    }
  }

  private void eventuallyAdjustPutNewEntryCount(Entry e) {
    if (e.isVirgin()) {
      putNewEntryCnt++;
    }
  }

  protected long stopStartTimer(long _nextRefreshTime, Entry e, long now) {
    if (e.task != null) {
      e.task.cancel();
    }
    if ((_nextRefreshTime > Entry.EXPIRY_TIME_MIN && _nextRefreshTime <= now) &&
        (_nextRefreshTime < -1 && (now >= -_nextRefreshTime))) {
      return Entry.EXPIRED_STATE;
    }
    if (hasSharpTimeout() && _nextRefreshTime > Entry.EXPIRY_TIME_MIN && _nextRefreshTime != Long.MAX_VALUE) {
      _nextRefreshTime = -_nextRefreshTime;
    }
    if (timer != null &&
      (_nextRefreshTime > Entry.EXPIRY_TIME_MIN || _nextRefreshTime < -1)) {
      if (_nextRefreshTime < -1) {
        long _timerTime =
          -_nextRefreshTime - TUNABLE.sharpExpirySafetyGapMillis;
        if (_timerTime >= now) {
          MyTimerTask tt = new MyTimerTask();
          tt.entry = e;
          timer.schedule(tt, new Date(_timerTime));
          e.task = tt;
          _nextRefreshTime = -_nextRefreshTime;
        }
      } else {
        MyTimerTask tt = new MyTimerTask();
        tt.entry = e;
        timer.schedule(tt, new Date(_nextRefreshTime));
        e.task = tt;
      }
    } else {
    }
    return _nextRefreshTime;
  }

  /**
   * When the time has come remove the entry from the cache.
   */
  protected void timerEvent(final Entry e, long _executionTime) {
    /* checked below, if we do not go through a synchronized clause, we may see old data
    if (e.isRemovedFromReplacementList()) {
      return;
    }
    */
    if (refreshPool != null) {
      synchronized (e) {
        synchronized (lock) {
          timerEvents++;
          if (isClosed()) {
            return;
          }
          touchedTime = _executionTime;
          if (e.isGone()) {
            return;
          }
          if (mainHashCtrl.remove(mainHash, e)) {
            refreshHash = refreshHashCtrl.insert(refreshHash, e);
            if (e.hashCode != modifiedHash(e.key.hashCode())) {
              if (!e.isGone() && removeEntryFromHash(e)) {
                expiredRemoveCnt++;
              }
              return;
            }
            Runnable r = new Runnable() {
              @Override
              public void run() {
                long _previousNextRefreshTime;
                synchronized (e) {

                  if (e.isRemovedFromReplacementList() || e.isGone() || e.isFetchInProgress()) {
                    return;
                  }
                  _previousNextRefreshTime = e.nextRefreshTime;
                  e.setGettingRefresh();
                }
                try {
                  long t = fetch(e, _previousNextRefreshTime);
                  finishFetch(e, t);
                } catch (CacheClosedException ignore) {
                } catch (Throwable ex) {
                  e.ensureFetchAbort(false);
                  synchronized (lock) {
                    internalExceptionCnt++;
                  }
                  getLog().warn("Refresh exception", ex);
                  try {
                    expireEntry(e);
                  } catch (CacheClosedException ignore) {
                  }
                }
              }
            };
            boolean _submitOkay = refreshPool.submit(r);
            if (_submitOkay) {
              return;
            }
            refreshSubmitFailedCnt++;
          } else { // if (mainHashCtrl.remove(mainHash, e)) ...
          }
        }
      }

    } else {
      synchronized (lock) {
        timerEvents++;
      }
    }
    synchronized (e) {
      long nrt = e.nextRefreshTime;
      if (nrt < org.cache2k.impl.Entry.EXPIRY_TIME_MIN) {
        return;
      }
      long t = System.currentTimeMillis();
      if (t >= e.nextRefreshTime) {
        try {
          expireEntry(e);
        } catch (CacheClosedException ignore) { }
      } else {
        e.nextRefreshTime = -e.nextRefreshTime;
      }
    }
  }

  protected void expireEntry(Entry e) {
    synchronized (e) {
      if (e.isGone() || e.isExpiredState()) {
        return;
      }
      if (e.isFetchInProgress()) {
        e.nextRefreshTime = org.cache2k.impl.Entry.FETCH_IN_PROGRESS_NON_VALID;
        return;
      }
      e.setExpiredState();
      synchronized (lock) {
        checkClosed();
        if (hasKeepAfterExpired()) {
          expiredKeptCnt++;
        } else {
          if (removeEntry(e)) {
            expiredRemoveCnt++;
          }
        }
      }
    }
  }

  /**
   * Returns all cache entries within the heap cache. Entries that
   * are expired or contain no valid data are not filtered out.
   */
  final protected ClosableConcurrentHashEntryIterator<org.cache2k.impl.Entry> iterateAllHeapEntries() {
    return
      new ClosableConcurrentHashEntryIterator(
        mainHashCtrl, mainHash, refreshHashCtrl, refreshHash);
  }

  @Override
  public void removeAllAtOnce(Set<K> _keys) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeAll(final Set<? extends K> keys) {
    for (K k : keys) {
      remove(k);
    }
  }


  /**
   * JSR107 bulk interface. The behaviour is compatible to the JSR107 TCK. We also need
   * to be compatible to the exception handling policy, which says that the exception
   * is to be thrown when most specific. So exceptions are only thrown, when the value
   * which has produced an exception is requested from the map.
   */
  public Map<K, V> getAll(final Set<? extends K> _inputKeys) {
    final Set<K> _keys = new HashSet<K>();
    for (K k : _inputKeys) {
      Entry e = getEntryInternal(k);
      if (e != null) {
        _keys.add(k);
      }
    }
    final Set<Map.Entry<K, V>> set =
      new AbstractSet<Map.Entry<K, V>>() {
        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
          return new Iterator<Map.Entry<K, V>>() {
            Iterator<? extends K> keyIterator = _keys.iterator();
            @Override
            public boolean hasNext() {
              return keyIterator.hasNext();
            }

            @Override
            public Map.Entry<K, V> next() {
              final K key = keyIterator.next();
              return new Map.Entry<K, V>(){
                @Override
                public K getKey() {
                  return key;
                }

                @Override
                public V getValue() {
                  return get(key);
                }

                @Override
                public V setValue(V value) {
                  throw new UnsupportedOperationException();
                }
              };
            }

            @Override
            public void remove() {
              throw new UnsupportedOperationException();
            }
          };
        }
        @Override
        public int size() {
          return _keys.size();
        }
      };
    return new AbstractMap<K, V>() {
      @Override
      public V get(Object key) {
        if (containsKey(key)) {
          return BaseCache.this.get((K) key);
        }
        return null;
      }

      @Override
      public boolean containsKey(Object key) {
        return _keys.contains(key);
      }

      @Override
      public Set<Entry<K, V>> entrySet() {
        return set;
      }
    };
  }

  public Map<K, V> convertValueMap(final Map<K, ExaminationEntry<K, V>> _map) {
    return new MapValueConverterProxy<K, V, ExaminationEntry<K, V>>(_map) {
      @Override
      protected V convert(final ExaminationEntry<K, V> v) {
        return returnValue(v.getValueOrException());
      }
    };
  }

  public Map<K, V> peekAll(final Set<? extends K> _inputKeys) {
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
    return new EntryAction<K, V, R>(this, this, op, key, e);
  }

  /**
   * Simply the {@link EntryAction} based code to provide the entry processor. If we code it directly this
   * might be a little bit more efficient, but it gives quite a code bloat which has lots of
   * corner cases for loader and exception handling.
   */
  @Override
  public <R> R invoke(K key, CacheEntryProcessor<K, V, R> entryProcessor, Object... _args) {
    return execute(key, spec().invoke(key, loader != null, entryProcessor, _args));
  }

  public abstract long getHitCnt();

  protected final int calculateHashEntryCount() {
    return Hash.calcEntryCount(mainHash) + Hash.calcEntryCount(refreshHash);
  }

  protected final int getLocalSize() {
    return mainHashCtrl.size + refreshHashCtrl.size;
  }

  public final int getTotalEntryCount() {
    synchronized (lock) {
      return getLocalSize();
    }
  }

  public long getExpiredCnt() {
    return expiredRemoveCnt + expiredKeptCnt;
  }

  /**
   * For peek no fetch is counted if there is a storage miss, hence the extra counter.
   */
  public long getFetchesBecauseOfNewEntries() {
    return loadCnt - loadButHitCnt + loadFailedCnt;
  }

  protected int getFetchesInFlight() {
    return 0;
  }

  protected IntegrityState getIntegrityState() {
    synchronized (lock) {
      return new IntegrityState()
        .checkEquals("newEntryCnt == getSize() + evictedCnt + expiredRemoveCnt + removeCnt + clearedCnt", newEntryCnt, getLocalSize() + evictedCnt + expiredRemoveCnt + removedCnt + clearedCnt)
        .checkEquals("newEntryCnt == getSize() + evictedCnt + getExpiredCnt() - expiredKeptCnt + removeCnt + clearedCnt", newEntryCnt, getLocalSize() + evictedCnt + getExpiredCnt() - expiredKeptCnt + removedCnt + clearedCnt)
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
    synchronized (lock) {
      checkClosed();
      long t = System.currentTimeMillis();
      if (info != null &&
          (info.creationTime + info.creationDeltaMs * TUNABLE.minimumStatisticsCreationTimeDeltaFactor + TUNABLE.minimumStatisticsCreationDeltaMillis > t)) {
        return info;
      }
      info = generateInfo(t);
    }
    return info;
  }

  @Override
  public final InternalCacheInfo getLatestInfo() {
    return generateInfo(System.currentTimeMillis());
  }

  private CacheBaseInfo generateInfo(long t) {
    synchronized (lock) {
      checkClosed();
      info = new CacheBaseInfo(this);
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
      InternalCacheInfo fo = getLatestInfo();
      return "Cache{" + name + "}"
              + "(" + fo.toString() + ")";
    }
  }

  static class CollisionInfo {
    int collisionCnt; int collisionSlotCnt; int longestCollisionSize;
  }

  /**
   * This function calculates a modified hash code. The intention is to
   * "rehash" the incoming integer hash codes to overcome weak hash code
   * implementations. We expect good results for integers also.
   * Also add a random seed to the hash to protect against attacks on hashes.
   * This is actually a slightly reduced version of the java.util.HashMap
   * hash modification.
   */
  protected final int modifiedHash(int h) {
    h ^= hashSeed;
    h ^= h >>> 7;
    h ^= h >>> 15;
    return h;

  }

  protected class MyTimerTask extends java.util.TimerTask {
    Entry entry;

    public void run() {
      timerEvent(entry, scheduledExecutionTime());
    }
  }

  public static class Tunable extends TunableConstants {

    /**
     * Implementation class to use by default.
     */
    public Class<? extends InternalCache> defaultImplementation =
            "64".equals(System.getProperty("sun.arch.data.model"))
                    ? ClockProPlus64Cache.class : ClockProPlusCache.class;

    /**
     * Log exceptions from the source just as they happen. The log goes to the debug output
     * of the cache log, debug level of the cache log must be enabled also.
     */
    public boolean logSourceExceptions = false;

    public int waitForTimerJobsSeconds = 5;

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
     * scheme when the get method is invoked. This prevents
     * that an expired value gets served by the cache if the time
     * is too late. A recent GC should not produce more then 200
     * milliseconds stall. If longer GC stalls are expected, this
     * value needs to be changed. A value of LONG.MaxValue
     * suppresses the timer usage completely.
     */
    public long sharpExpirySafetyGapMillis = 666;

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


  }

}
