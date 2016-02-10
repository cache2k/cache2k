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

import org.cache2k.BulkCacheSource;
import org.cache2k.CacheEntry;
import org.cache2k.CacheException;
import org.cache2k.CacheMisconfigurationException;
import org.cache2k.ClosableIterator;
import org.cache2k.EntryExpiryCalculator;
import org.cache2k.ExceptionExpiryCalculator;
import org.cache2k.ExperimentalBulkCacheSource;
import org.cache2k.Cache;
import org.cache2k.CacheConfig;
import org.cache2k.RefreshController;
import org.cache2k.StorageConfiguration;
import org.cache2k.ValueWithExpiryTime;
import org.cache2k.impl.threading.Futures;
import org.cache2k.impl.threading.LimitedPooledExecutor;
import org.cache2k.impl.timer.GlobalTimerService;
import org.cache2k.impl.timer.TimerService;
import org.cache2k.impl.util.ThreadDump;
import org.cache2k.CacheSourceWithMetaInfo;
import org.cache2k.CacheSource;
import org.cache2k.PropagatedCacheException;

import org.cache2k.storage.PurgeableStorage;
import org.cache2k.storage.StorageEntry;
import org.cache2k.impl.util.Log;
import org.cache2k.impl.util.TunableConstants;
import org.cache2k.impl.util.TunableFactory;

import java.lang.reflect.Array;
import java.security.SecureRandom;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
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
public abstract class BaseCache<E extends Entry, K, T>
  implements Cache<K, T>, CanCheckIntegrity, Iterable<CacheEntry<K, T>>, StorageAdapter.Parent {

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

  protected int hashSeed;

  {
    if (TUNABLE.disableHashRandomization) {
      hashSeed = TUNABLE.hashSeed;
    } else {
      hashSeed = SEED_RANDOM.nextInt();
    }
  }

  /** Maximum amount of elements in cache */
  protected int maxSize = 5000;

  protected String name;
  protected CacheManagerImpl manager;
  protected CacheSourceWithMetaInfo<K, T> source;
  /** Statistics */

  /** Time in milliseconds we keep an element */
  protected long maxLinger = 10 * 60 * 1000;

  protected long exceptionMaxLinger = 1 * 60 * 1000;

  protected EntryExpiryCalculator<K, T> entryExpiryCalculator;

  protected ExceptionExpiryCalculator<K> exceptionExpiryCalculator;

  protected Info info;

  protected long clearedTime = 0;
  protected long startedTime;
  protected long touchedTime;
  protected int timerCancelCount = 0;

  protected long keyMutationCount = 0;
  protected long putCnt = 0;
  protected long putNewEntryCnt = 0;
  protected long removedCnt = 0;
  protected long expiredKeptCnt = 0;
  protected long expiredRemoveCnt = 0;
  protected long evictedCnt = 0;
  protected long refreshCnt = 0;
  protected long suppressedExceptionCnt = 0;
  protected long fetchExceptionCnt = 0;
  /* that is a miss, but a hit was already counted. */
  protected long peekHitNotFreshCnt = 0;
  /* no heap hash hit */
  protected long peekMissCnt = 0;

  protected long fetchCnt = 0;

  protected long fetchButHitCnt = 0;
  protected long bulkGetCnt = 0;
  protected long fetchMillis = 0;
  protected long refreshHitCnt = 0;
  protected long newEntryCnt = 0;

  /**
   * Loaded from storage, but the entry was not fresh and cannot be returned.
   */
  protected long loadNonFreshCnt = 0;

  /**
   * Entry was loaded from storage and fresh.
   */
  protected long loadHitCnt = 0;

  /**
   * Separate counter for loaded entries that needed a fetch.
   */
  protected long loadNonFreshAndFetchedCnt;

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
   * Storage did not contain the requested entry.
   */
  protected long loadMissCnt = 0;

  /**
   * A newly inserted entry was removed by the eviction without the fetch to complete.
   */
  protected long virginEvictCnt = 0;

  protected int maximumBulkFetchSize = 100;

  /**
   * Structure lock of the cache. Every operation that needs a consistent structure
   * of the cache or modifies it needs to synchronize on this. Since this is a global
   * lock, locking on it should be avoided and any operation under the lock should be
   * quick.
   */
  protected final Object lock = new Object();

  protected CacheRefreshThreadPool refreshPool;

  protected Hash<E> mainHashCtrl;
  protected E[] mainHash;

  protected Hash<E> refreshHashCtrl;
  protected E[] refreshHash;

  protected ExperimentalBulkCacheSource<K, T> experimentalBulkCacheSource;

  protected BulkCacheSource<K, T> bulkCacheSource;

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

  protected StorageAdapter storage;

  protected TimerService timerService = GlobalTimerService.getInstance();

  /**
   * Stops creation of new entries when clear is ongoing.
   */
  protected boolean waitForClear = false;

  private int featureBits = 0;

  private static final int SHARP_TIMEOUT_FEATURE = 1;
  private static final int KEEP_AFTER_EXPIRED = 2;
  private static final int SUPPRESS_EXCEPTIONS = 4;

  protected final boolean hasSharpTimeout() {
    return (featureBits & SHARP_TIMEOUT_FEATURE) > 0;
  }

  protected final boolean hasKeepAfterExpired() {
    return (featureBits & KEEP_AFTER_EXPIRED) > 0;
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
  protected Log getLog() {
    return
      Log.getLog(Cache.class.getName() + '/' + getCompleteName());
  }

  @Override
  public void resetStorage(StorageAdapter _from, StorageAdapter to) {
    synchronized (lock) {
      storage = to;
    }
  }

  /** called via reflection from CacheBuilder */
  public void setCacheConfig(CacheConfig c) {
    valueType = c.getValueType();
    keyType = c.getKeyType();
    if (name != null) {
      throw new IllegalStateException("already configured");
    }
    setName(c.getName());
    maxSize = c.getMaxSize();
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
    /*
    if (c.isPersistent()) {
      storage = new PassingStorageAdapter();
    }
    -*/
    List<StorageConfiguration> _stores = c.getStorageModules();
    if (_stores.size() == 1) {
      StorageConfiguration cfg = _stores.get(0);
      if (cfg.getEntryCapacity() < 0) {
        cfg.setEntryCapacity(c.getMaxSize());
      }
      storage = new PassingStorageAdapter(this, c, _stores.get(0));
    } else if (_stores.size() > 1) {
      throw new UnsupportedOperationException("no aggregation support yet");
    }
    if (ValueWithExpiryTime.class.isAssignableFrom(c.getValueType()) &&
        entryExpiryCalculator == null)  {
      entryExpiryCalculator =
        (EntryExpiryCalculator<K, T>)
        ENTRY_EXPIRY_CALCULATOR_FROM_VALUE;
    }
  }

  public void setEntryExpiryCalculator(EntryExpiryCalculator<K, T> v) {
    entryExpiryCalculator = v;
  }

  public void setExceptionExpiryCalculator(ExceptionExpiryCalculator<K> v) {
    exceptionExpiryCalculator = v;
  }

  /** called via reflection from CacheBuilder */
  public void setRefreshController(final RefreshController<T> lc) {
    entryExpiryCalculator = new EntryExpiryCalculator<K, T>() {
      @Override
      public long calculateExpiryTime(K _key, T _value, long _fetchTime, CacheEntry<K, T> _oldEntry) {
        if (_oldEntry != null) {
          return lc.calculateNextRefreshTime(_oldEntry.getValue(), _value, _oldEntry.getLastModification(), _fetchTime);
        } else {
          return lc.calculateNextRefreshTime(null, _value, 0L, _fetchTime);
        }
      }
    };
  }

  @SuppressWarnings("unused")
  public void setSource(CacheSourceWithMetaInfo<K, T> eg) {
    source = eg;
  }

  @SuppressWarnings("unused")
  public void setSource(final CacheSource<K, T> g) {
    if (g != null) {
      source = new CacheSourceWithMetaInfo<K, T>() {
        @Override
        public T get(K key, long _currentTime, T _previousValue, long _timeLastFetched) throws Throwable {
          return g.get(key);
        }
      };
    }
  }

  @SuppressWarnings("unused")
  public void setExperimentalBulkCacheSource(ExperimentalBulkCacheSource<K, T> g) {
    experimentalBulkCacheSource = g;
    if (source == null) {
      source = new CacheSourceWithMetaInfo<K, T>() {
        @Override
        public T get(K key, long _currentTime, T _previousValue, long _timeLastFetched) throws Throwable {
          K[] ka = (K[]) Array.newInstance(keyType, 1);
          ka[0] = key;
          T[] ra = (T[]) Array.newInstance(valueType, 1);
          BitSet _fetched = new BitSet(1);
          experimentalBulkCacheSource.getBulk(ka, ra, _fetched, 0, 1);
          return ra[0];
        }
      };
    }
  }

  public void setBulkCacheSource(BulkCacheSource<K, T> s) {
    bulkCacheSource = s;
    if (source == null) {
      source = new CacheSourceWithMetaInfo<K, T>() {
        @Override
        public T get(final K key, final long _currentTime, final T _previousValue, final long _timeLastFetched) throws Throwable {
          final CacheEntry<K, T> entry = new CacheEntry<K, T>() {
            @Override
            public K getKey() {
              return key;
            }

            @Override
            public T getValue() {
              return _previousValue;
            }

            @Override
            public Throwable getException() {
              return null;
            }

            @Override
            public long getLastModification() {
              return _timeLastFetched;
            }
          };
          List<CacheEntry<K, T>> _entryList = new AbstractList<CacheEntry<K, T>>() {
            @Override
            public CacheEntry<K, T> get(int index) {
              return entry;
            }

            @Override
            public int size() {
              return 1;
            }
          };
          return bulkCacheSource.getValues(_entryList, _currentTime).get(0);
        }
      };
    }
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

  /**
   * Set the time in seconds after which the cache does an refresh of the
   * element. -1 means the element will be hold forever.
   * 0 means the element will not be cached at all.
   */
  public void setExpirySeconds(int s) {
    if (s < 0 || s == Integer.MAX_VALUE) {
      maxLinger = -1;
      return;
    }
    maxLinger = s * 1000;
  }

  public String getName() {
    return name;
  }

  public void setCacheManager(CacheManagerImpl cm) {
    manager = cm;
  }

  public StorageAdapter getStorage() { return storage; }

  /**
   * Registers the cache in a global set for the clearAllCaches function and
   * registers it with the resource monitor.
   */
  public void init() {
    synchronized (lock) {
      if (name == null) {
        name = "" + cacheCnt++;
      }

      if (storage == null && maxSize == 0) {
        throw new IllegalArgumentException("maxElements must be >0");
      }
      if (storage != null) {
        bulkCacheSource = null;
        storage.open();
      }
      initializeHeapCache();
      initTimer();
      if (refreshPool != null &&
        source == null) {
        throw new CacheMisconfigurationException("backgroundRefresh, but no source");
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

  /**
   * Clear may be called during operation, e.g. to reset all the cache content. We must make sure
   * that there is no ongoing operation when we send the clear to the storage. That is because the
   * storage implementation has a guarantee that there is only one storage operation ongoing for
   * one entry or key at any time. Clear, of course, affects all entries.
   */
  private void processClearWithStorage() {
    StorageClearTask t = new StorageClearTask();
    boolean _untouchedHeapCache;
    synchronized (lock) {
      checkClosed();
      waitForClear = true;
      _untouchedHeapCache = touchedTime == clearedTime && getLocalSize() == 0;
      if (!storage.checkStorageStillDisconnectedForClear()) {
        t.allLocalEntries = iterateAllHeapEntries();
        t.allLocalEntries.setStopOnClear(false);
      }
      t.storage = storage;
      t.storage.disconnectStorageForClear();
    }
    try {
      if (_untouchedHeapCache) {
        FutureTask<Void> f = new FutureTask<Void>(t);
        updateShutdownWaitFuture(f);
        f.run();
      } else {
        updateShutdownWaitFuture(manager.getThreadPool().execute(t));
      }
    } catch (Exception ex) {
      throw new CacheStorageException(ex);
    }
    synchronized (lock) {
      if (isClosed()) { throw new CacheClosedException(); }
      clearLocalCache();
      waitForClear = false;
      lock.notifyAll();
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

  class StorageClearTask implements LimitedPooledExecutor.NeverRunInCallingTask<Void> {

    ClosableConcurrentHashEntryIterator<Entry> allLocalEntries;
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
        Entry e = it.next();
        synchronized (e) { }
      }
    }
  }

  protected void checkClosed() {
    if (isClosed()) {
      throw new CacheClosedException();
    }
    boolean _interrupted = false;
    while (waitForClear) {
      try {
        lock.wait();
      } catch (InterruptedException ignore) {
        _interrupted = true;
      }
    }
    if (_interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  public final void clear() {
    if (storage != null) {
      processClearWithStorage();
      return;
    }
    synchronized (lock) {
      checkClosed();
      clearLocalCache();
    }
  }

  protected final void clearLocalCache() {
    iterateAllEntriesRemoveAndCancelTimer();
    removedCnt += getLocalSize();
    initializeHeapCache();
    clearedTime = System.currentTimeMillis();
    touchedTime = clearedTime;
  }

  protected void iterateAllEntriesRemoveAndCancelTimer() {
    Iterator<Entry> it = iterateAllHeapEntries();
    int _count = 0;
    while (it.hasNext()) {
      Entry e = it.next();
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
    mainHashCtrl = new Hash<E>();
    refreshHashCtrl = new Hash<E>();
    mainHash = mainHashCtrl.init((Class<E>) newEntry().getClass());
    refreshHash = refreshHashCtrl.init((Class<E>) newEntry().getClass());
    if (startedTime == 0) {
      startedTime = System.currentTimeMillis();
    }
    if (timer != null) {
      timer.cancel();
      timer = null;
      initTimer();
    }
  }

  public void clearTimingStatistics() {
    synchronized (lock) {
      fetchCnt = 0;
      fetchMillis = 0;
    }
  }

  /**
   * Preparation for shutdown. Cancel all pending timer jobs e.g. for
   * expiry/refresh or flushing the storage.
   */
  Future<Void> cancelTimerJobs() {
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
      if (storage != null) {
        Future<Void> f = storage.cancelTimerJobs();
        if (f != null) {
          _waitFuture = new Futures.WaitForAllFuture(_waitFuture, f);
        }
      }
      return _waitFuture;
    }
  }

  public void purge() {
    if (storage != null) {
      storage.purge();
    }
  }

  public void flush() {
    if (storage != null) {
      storage.flush();
    }
  }

  protected boolean isClosed() {
    return shutdownInitiated;
  }

  @Override
  public void destroy() {
    close();
  }

  @Override
  public void close() {
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
      getLog().warn("State: " + toString());
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
    synchronized (lock) {
      storage = null;
    }
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
      source = null;
      if (manager != null) {
        manager.cacheDestroyed(this);
        manager = null;
      }
    }
  }

  /**
   * Complete iteration of all entries in the cache, including
   * storage / persisted entries. The iteration may include expired
   * entries or entries with no valid data.
   */
  protected ClosableIterator<Entry> iterateLocalAndStorage() {
    if (storage == null) {
      synchronized (lock) {
        return iterateAllHeapEntries();
      }
    } else {
      return storage.iterateAll();
    }
  }

  @Override
  public ClosableIterator<CacheEntry<K, T>> iterator() {
    return new IteratorFilterEntry2Entry(iterateLocalAndStorage());
  }

  /**
   * Filter out non valid entries and wrap each entry with a cache
   * entry object.
   */
  class IteratorFilterEntry2Entry implements ClosableIterator<CacheEntry<K, T>> {

    ClosableIterator<Entry> iterator;
    Entry entry;

    IteratorFilterEntry2Entry(ClosableIterator<Entry> it) { iterator = it; }

    /**
     * Between hasNext() and next() an entry may be evicted or expired.
     * In practise we have to deliver a next entry if we return hasNext() with
     * true, furthermore, there should be no big gap between the calls to
     * hasNext() and next().
     */
    @Override
    public boolean hasNext() {
      while (iterator.hasNext()) {
        entry = iterator.next();
        if (entry.hasFreshData()) {
          return true;
        }
      }
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
    public CacheEntry<K, T> next() { return returnEntry(entry); }

    @Override
    public void remove() {
      BaseCache.this.remove((K) entry.getKey());
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
    Entry e = _head.next;
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

  protected static final <E extends Entry> void moveToFront(final E _head, final E e) {
    removeFromList(e);
    insertInList(_head, e);
  }

  protected static final <E extends Entry> E insertIntoTailCyclicList(final E _head, final E e) {
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
  protected static final <E extends Entry> E insertAfterHeadCyclicList(final E _head, final E e) {
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
  protected static final <E extends Entry> E insertIntoHeadCyclicList(final E _head, final E e) {
    if (_head == null) {
      return (E) e.shortCircuit();
    }
    e.next = _head;
    e.prev = _head.prev;
    _head.prev.next = e;
    _head.prev = e;
    return e;
  }

  protected static <E extends Entry> E removeFromCyclicList(final E _head, E e) {
    if (e.next == e) {
      e.removedFromList();
      return null;
    }
    Entry _eNext = e.next;
    e.prev.next = _eNext;
    e.next.prev = e.prev;
    e.removedFromList();
    return e == _head ? (E) _eNext : _head;
  }

  protected static Entry removeFromCyclicList(final Entry e) {
    Entry _eNext = e.next;
    e.prev.next = _eNext;
    e.next.prev = e.prev;
    e.removedFromList();
    return _eNext == e ? null : _eNext;
  }

  protected static int getCyclicListEntryCount(Entry e) {
    if (e == null) { return 0; }
    final Entry _head = e;
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

  protected static boolean checkCyclicListIntegrity(Entry e) {
    if (e == null) { return true; }
    Entry _head = e;
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
  protected abstract void recordHit(E e);

  /**
   * New cache entry, put it in the replacement algorithm structure
   */
  protected abstract void insertIntoReplacementList(E e);

  /**
   * Entry object factory. Return an entry of the proper entry subtype for
   * the replacement/eviction algorithm.
   */
  protected abstract E newEntry();


  /**
   * Find an entry that should be evicted. Called within structure lock.
   * After doing some checks the cache will call {@link #removeEntryFromReplacementList(Entry)}
   * if this entry will be really evicted. Pinned entries may be skipped. A
   * good eviction algorithm returns another candidate on sequential calls, even
   * if the candidate was not removed.
   *
   * <p/>Rationale: Within the structure lock we can check for an eviction candidate
   * and may remove it from the list. However, we cannot process additional operations or
   * events which affect the entry. For this, we need to acquire the lock on the entry
   * first.
   */
  protected E findEvictionCandidate() {
    return null;
  }


  /**
   *
   */
  protected void removeEntryFromReplacementList(E e) {
    removeFromList(e);
  }

  /**
   * Check whether we have an entry in the ghost table
   * remove it from ghost and insert it into the replacement list.
   * null if nothing there. This may also do an optional eviction
   * if the size limit of the cache is reached, because some replacement
   * algorithms (ARC) do this together.
   */
  protected E checkForGhost(K key, int hc) { return null; }

  /**
   * Implement unsynchronized lookup if it is supported by the eviction.
   * If a null is returned the lookup is redone synchronized.
   */
  protected E lookupEntryUnsynchronized(K key, int hc) { return null; }

  @Override
  public T get(K key) {
    return (T) returnValue(getEntryInternal(key));
  }

  /**
   * Wrap entry in a separate object instance. We can return the entry directly, however we lock on
   * the entry object.
   */
  protected CacheEntry<K, T> returnEntry(final Entry<E, K, T> e) {
    if (e == null) {
      return null;
    }
    synchronized (e) {
      final K _key = e.getKey();
      final T _value = e.getValue();
      final Throwable _exception = e.getException();
      final long _lastModification = e.getLastModification();
      CacheEntry<K, T> ce = new CacheEntry<K, T>() {
        @Override
        public K getKey() {
          return _key;
        }

        @Override
        public T getValue() {
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
          long _expiry = e.getValueExpiryTime();
          return "CacheEntry(" +
              "key=" + getKey() + ", " +
              "value=" + getValue() + ", " +
              ((getException() != null) ? "exception=" + e.getException() + ", " : "") +
              "updated=" + formatMillis(getLastModification()) + ", " +
              "expiry=" + (_expiry != 0 ? (formatMillis(_expiry)) : "-") + ", " +
              "flags=" + (_expiry == 0 ? e.nextRefreshTime : "-") + ")";
        }

      };
      return ce;
    }
  }

  @Override
  public CacheEntry<K, T> getEntry(K key) {
    return returnEntry(getEntryInternal(key));
  }

  protected E getEntryInternal(K key) {
    for (;;) {
      E e = lookupOrNewEntrySynchronized(key);
      if (e.hasFreshData()) { return e; }
      synchronized (e) {
        e.waitForFetch();
        if (e.hasFreshData()) {
          return e;
        }
        if (e.isRemovedState()) {
          continue;
        }
        e.startFetch();
      }
      boolean _finished = false;
      try {
        finishFetch(e, fetch(e));
        _finished = true;
      } finally {
        e.ensureFetchAbort(_finished);
      }
      evictEventually();
      return e;
    }
  }

  protected void finishFetch(E e, long _nextRefreshTime) {
    synchronized (e) {
      e.nextRefreshTime = stopStartTimer(_nextRefreshTime, e, System.currentTimeMillis());
      e.notifyAll();
    }
  }

  /**
   * Insert the storage entry in the heap cache and return it. Used when storage entries
   * are queried. We need to check whether the entry is present meanwhile, get the entry lock
   * and maybe fetch it from the source. Doubles with {@link #getEntryInternal(Object)}
   * except we do not need to retrieve the data from the storage again.
   *
   * @param _needsFetch if true the entry is fetched from CacheSource when expired.
   * @return a cache entry is always returned, however, it may be outdated
   */
  protected Entry insertEntryFromStorage(StorageEntry se, boolean _needsFetch) {
    int _spinCount = TUNABLE.maximumEntryLockSpins;
    for (;;) {
      if (_spinCount-- <= 0) { throw new CacheLockSpinsExceededError(); }
      E e = lookupOrNewEntrySynchronized((K) se.getKey());
      if (e.hasFreshData()) { return e; }
      synchronized (e) {
        if (!e.isDataValidState()) {
          if (e.isRemovedState()) {
            continue;
          }
          if (e.isFetchInProgress()) {
            e.waitForFetch();
            return e;
          }
          e.startFetch();
        }
      }
      boolean _finished = false;
      try {
        finishFetch(e, insertEntryFromStorage(se, e, _needsFetch));
        _finished = true;
      } finally {
        e.ensureFetchAbort(_finished);
      }
      evictEventually();
      return e;
    }
  }

  /**
   * Insert a cache entry for the given key and run action under the entry
   * lock. If the cache entry has fresh data, we do not run the action.
   * Called from storage. The entry referenced by the key is expired and
   * will be purged.
   */
  protected void lockAndRunForPurge(Object key, PurgeableStorage.PurgeAction _action) {
    int _spinCount = TUNABLE.maximumEntryLockSpins;
    E e;
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
        if (e.isRemovedState()) {
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
          finishFetch(e, Entry.LOADED_NON_VALID);
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
    E _previousCandidate = null;
    while (evictionNeeded) {
      if (_spinCount-- <= 0) { return; }
      E e;
      synchronized (lock) {
        checkClosed();
        if (getLocalSize() <= maxSize) {
          evictionNeeded = false;
          return;
        }
        e = findEvictionCandidate();
      }
      boolean _shouldStore;
      synchronized (e) {
        if (e.isRemovedState()) {
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

        boolean _storeEvenImmediatelyExpired = hasKeepAfterExpired() && (e.isDataValidState() || e.isExpiredState() || e.nextRefreshTime == Entry.FETCH_NEXT_TIME_STATE);
        _shouldStore =
            (storage != null) && (_storeEvenImmediatelyExpired || e.hasFreshData());
        if (_shouldStore) {
          e.startFetch();
        } else {
          evictEntryFromHeap(e);
        }
      }
      if (_shouldStore) {
        try {
          storage.evict(e);
        } finally {
          synchronized (e) {
            finishFetch(e, Entry.FETCH_ABORT);
            evictEntryFromHeap(e);
          }
        }
      }
    }
  }

  private void evictEntryFromHeap(E e) {
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
  protected boolean removeEntry(E e) {
    if (!e.isRemovedFromReplacementList()) {
      removeEntryFromReplacementList(e);
    }
    return removeEntryFromHash(e);
  }

  /**
   * Return the entry, if it is in the cache, without invoking the
   * cache source.
   *
   * <p>The cache storage is asked whether the entry is present.
   * If the entry is not present, this result is cached in the local
   * cache.
   */
  protected E peekEntryInternal(K key) {
    final int hc = modifiedHash(key.hashCode());
    int _spinCount = TUNABLE.maximumEntryLockSpins;
    for (;;) {
      if (_spinCount-- <= 0) { throw new CacheLockSpinsExceededError(); }
      E e = lookupEntryUnsynchronized(key, hc);
      if (e == null) {
        synchronized (lock) {
          e = lookupEntry(key, hc);
          if (e == null && storage != null) {
            e = newEntry(key, hc);
          }
        }
      }
      if (e == null) {
        peekMissCnt++;
        return null;
      }
      if (e.hasFreshData()) { return e; }
      boolean _hasFreshData = false;
      if (storage != null) {
        boolean _needsLoad;
        synchronized (e) {
          e.waitForFetch();
          if (e.isRemovedState()) {
            continue;
          }
          if (e.hasFreshData()) {
            return e;
          }
          _needsLoad = conditionallyStartProcess(e);
        }
        if (_needsLoad) {
          boolean _finished = false;
          try {
            long t = fetchWithStorage(e, false);
            finishFetch(e, t);
            _hasFreshData = e.hasFreshData(System.currentTimeMillis(), t);
            _finished = true;
          } finally {
            e.ensureFetchAbort(_finished);
          }
        }

      }
      evictEventually();
      if (_hasFreshData) {
        return e;
      }
      peekHitNotFreshCnt++;
      return null;
    }
  }

  @Override
  public boolean contains(K key) {
    E e = peekEntryInternal(key);
    if (e != null) {
      return true;
    }
    return false;
  }

  @Override
  public T peek(K key) {
    E e = peekEntryInternal(key);
    if (e != null) {
      return (T) returnValue(e);
    }
    return null;
  }

  @Override
  public CacheEntry<K, T> peekEntry(K key) {
    return returnEntry(peekEntryInternal(key));
  }

  @Override
  public boolean putIfAbsent(K key, T value) {
    if (storage == null) {
       int _spinCount = TUNABLE.maximumEntryLockSpins;
      E e;
      for (;;) {
        if (_spinCount-- <= 0) {
          throw new CacheLockSpinsExceededError();
        }
        e = lookupOrNewEntrySynchronized(key);
        synchronized (e) {
          if (e.isRemovedState()) {
            continue;
          }
          long t = System.currentTimeMillis();
          if (e.hasFreshData(t)) {
            return false;
          }
          synchronized (lock) {
            peekHitNotFreshCnt++;
          }
          e.nextRefreshTime = insertOnPut(e, value, t, t);
          return true;
        }
      }
    }
    int _spinCount = TUNABLE.maximumEntryLockSpins;
    E e; long t;
    for (;;) {
      if (_spinCount-- <= 0) { throw new CacheLockSpinsExceededError(); }
      e = lookupOrNewEntrySynchronized(key);
      synchronized (e) {
        e.waitForFetch();
        if (e.isRemovedState()) {
          continue;
        }
        t = System.currentTimeMillis();
        if (e.hasFreshData(t)) {
          return false;
        }
        e.startFetch();
        break;
      }
    }
    if (e.isVirgin()) {
      long _result = fetchWithStorage(e, false);
      long now = System.currentTimeMillis();
      if (e.hasFreshData(now, _result)) {
        finishFetch(e, _result);
        return false;
      }
      e.nextRefreshTime = Entry.LOADED_NON_VALID_AND_PUT;
    }
    finishFetch(e, insertOnPut(e, value, t, t));
    evictEventually();
    return true;
  }

  @Override
  public void put(K key, T value) {
    int _spinCount = TUNABLE.maximumEntryLockSpins;
    E e;
    if (storage == null) {
      for (;;) {
        if (_spinCount-- <= 0) {
          throw new CacheLockSpinsExceededError();
        }
        e = lookupOrNewEntrySynchronized(key);
        synchronized (e) {
          if (e.isRemovedState()) {
            continue;
          }
          if (e.isFetchInProgress()) {
            e.waitForFetch();
            continue;
          }
          long t = System.currentTimeMillis();
          long _nextRefreshTime = e.nextRefreshTime;
          if (e.hasFreshData(t)) {
            e.nextRefreshTime = Entry.REPUT_STATE;
          }
          _nextRefreshTime = insertOnPut(e, value, t, t);
          e.nextRefreshTime = stopStartTimer(_nextRefreshTime, e, System.currentTimeMillis());
        }
        evictEventually();
        return;
      }
    }
    for (;;) {
      if (_spinCount-- <= 0) { throw new CacheLockSpinsExceededError(); }
      e = lookupOrNewEntrySynchronized(key);
      synchronized (e) {
        if (e.isRemovedState()) {
          continue;
        }
        if (e.isFetchInProgress()) {
          e.waitForFetch();
          continue;
        } else {
          e.startFetch();
          break;
        }
      }
    }
    boolean _finished = false;
    try {
      long t = System.currentTimeMillis();
      finishFetch(e, insertOnPut(e, value, t, t));
      _finished = true;
    } finally {
      e.ensureFetchAbort(_finished);
    }
    evictEventually();
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
  public boolean removeWithFlag(K key) {
    if (storage == null) {
      E e = lookupEntrySynchronized(key);
      if (e != null) {
        synchronized (e) {
          e.waitForFetch();
          if (!e.isRemovedState()) {
            synchronized (lock) {
              boolean f = e.hasFreshData();
              if (removeEntry(e)) {
                removedCnt++;
                return f;
              }
              return false;
            }
          }
        }
      }
      return false;
    }
    int _spinCount = TUNABLE.maximumEntryLockSpins;
    E e;
    boolean _hasFreshData;
    for (;;) {
      if (_spinCount-- <= 0) {
        throw new CacheLockSpinsExceededError();
      }
      e = lookupOrNewEntrySynchronized(key);
      synchronized (e) {
        e.waitForFetch();
        if (e.isRemovedState()) {
          continue;
        }
        _hasFreshData = e.hasFreshData();
        e.startFetch();
        break;
      }
    }
    boolean _finished = false;
    try {
      if (!_hasFreshData && e.isVirgin()) {
        long t = fetchWithStorage(e, false);
        _hasFreshData = e.hasFreshData(System.currentTimeMillis(), t);
      }
      if (_hasFreshData) {
        storage.remove(key);
      }
      synchronized (e) {
        finishFetch(e, Entry.LOADED_NON_VALID);
        if (_hasFreshData) {
          synchronized (lock) {
            if (removeEntry(e)) {
              removedCnt++;
            }
          }
        }
      }
      _finished = true;
    } finally {
      e.ensureFetchAbort(_finished);
    }
    return _hasFreshData;
  }

  @Override
  public void remove(K key) {
    removeWithFlag(key);
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

  public void prefetch(final List<K> keys, final int _startIndex, final int _endIndexExclusive) {
    if (keys.size() == 0 || _startIndex == _endIndexExclusive) {
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
  public void prefetch(final Set<K> keys) {
    if (refreshPool == null) {
      getAll(keys);
      return;
    }
    boolean _complete = true;
    for (K k : keys) {
      if (lookupEntryUnsynchronized(k, modifiedHash(k.hashCode())) == null) {
        _complete = false; break;
      }
    }
    if (_complete) {
      return;
    }
    Runnable r = new Runnable() {
      @Override
      public void run() {
        getAll(keys);
      }
    };
    refreshPool.submit(r);
  }

  /**
   * Lookup or create a new entry. The new entry is created, because we need
   * it for locking within the data fetch.
   */
  protected E lookupOrNewEntrySynchronized(K key) {
    int hc = modifiedHash(key.hashCode());
    E e = lookupEntryUnsynchronized(key, hc);
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

  protected T returnValue(Entry<E, K,T> e) {
    T v = e.value;
    if (v instanceof ExceptionWrapper) {
      ExceptionWrapper w = (ExceptionWrapper) v;
      if (w.additionalExceptionMessage == null) {
        synchronized (e) {
          long t = e.getValueExpiryTime();
          w.additionalExceptionMessage = "(expiry=" + (t > 0 ? formatMillis(t) : "none") + ") " + w.getException();
        }
      }
      throw new PropagatedCacheException(w.additionalExceptionMessage, w.getException());
    }
    return v;
  }

  protected E lookupEntrySynchronized(K key) {
    int hc = modifiedHash(key.hashCode());
    E e = lookupEntryUnsynchronized(key, hc);
    if (e == null) {
      synchronized (lock) {
        e = lookupEntry(key, hc);
      }
    }
    return e;
  }

  protected E lookupEntry(K key, int hc) {
    E e = Hash.lookup(mainHash, key, hc);
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


  /**
   * Insert new entry in all structures (hash and replacement list). May evict an
   * entry if the maximum capacity is reached.
   */
  protected E newEntry(K key, int hc) {
    if (getLocalSize() >= maxSize) {
      evictionNeeded = true;
    }
    E e = checkForGhost(key, hc);
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
  private boolean removeEntryFromHash(E e) {
    boolean f = mainHashCtrl.remove(mainHash, e) || refreshHashCtrl.remove(refreshHash, e);
    checkForHashCodeChange(e);
    cancelExpiryTimer(e);
    if (e.isVirgin()) {
      virginEvictCnt++;
    }
    e.setRemovedState();
    return f;
  }

  protected void cancelExpiryTimer(Entry e) {
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
  private void checkForHashCodeChange(Entry e) {
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
      K _key, T _newObject, long now, Entry _entry,
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
      long t = _exceptionEc.calculateExpiryTime(_key, ((ExceptionWrapper) _newObject).getException(), now);
      return limitExpiryToMaxLinger(now, _exceptionMaxLinger, t);
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

  protected long calcNextRefreshTime(K _key, T _newObject, long now, Entry _entry) {
    return calcNextRefreshTime(
        _key, _newObject, now, _entry,
        entryExpiryCalculator, maxLinger,
        exceptionExpiryCalculator, exceptionMaxLinger);
  }

  protected long fetch(final E e) {
    if (storage != null) {
      return fetchWithStorage(e, true);
    } else {
      return fetchFromSource(e);
    }
  }

  protected boolean conditionallyStartProcess(E e) {
    if (!e.isVirgin()) {
      return false;
    }
    e.startFetch();
    return true;
  }

  /**
   *
   * @param e
   * @param _needsFetch true if value needs to be fetched from the cache source.
   *                   This is false, when the we only need to peek for an value already mapped.
   */
  protected long fetchWithStorage(E e, boolean _needsFetch) {
    if (!e.isVirgin()) {
      if (_needsFetch) {
        return fetchFromSource(e);
      }
      return Entry.LOADED_NON_VALID;
    }
    StorageEntry se = storage.get(e.key);
    if (se == null) {
      if (_needsFetch) {
        synchronized (lock) {
          loadMissCnt++;
        }
        return fetchFromSource(e);
      }
      synchronized (lock) {
        touchedTime = System.currentTimeMillis();
        loadNonFreshCnt++;
      }
      return Entry.LOADED_NON_VALID;
    }
    return insertEntryFromStorage(se, e, _needsFetch);
  }

  protected long insertEntryFromStorage(StorageEntry se, E e, boolean _needsFetch) {
    e.setLastModificationFromStorage(se.getCreatedOrUpdated());
    long now = System.currentTimeMillis();
    T v = (T) se.getValueOrException();
    long _nextRefreshTime = maxLinger == 0 ? 0 : Long.MAX_VALUE;
    long _expiryTimeFromStorage = se.getValueExpiryTime();
    boolean _expired = _expiryTimeFromStorage != 0 && _expiryTimeFromStorage <= now;
    if (!_expired && timer != null) {
      _nextRefreshTime = calcNextRefreshTime((K) se.getKey(), v, se.getCreatedOrUpdated(), null);
      _expired = _nextRefreshTime > Entry.EXPIRY_TIME_MIN && _nextRefreshTime <= now;
    }
    boolean _fetchAlways = timer == null && maxLinger == 0;
    if (_expired || _fetchAlways) {
      if (_needsFetch) {
        e.value = se.getValueOrException();
        e.setLoadedNonValidAndFetch();
        return fetchFromSource(e);
      } else {
        synchronized (lock) {
          touchedTime = now;
          loadNonFreshCnt++;
        }
        return Entry.LOADED_NON_VALID;
      }
    }
    return insert(e, (T) se.getValueOrException(), 0, now, INSERT_STAT_UPDATE, _nextRefreshTime);
  }

  protected long fetchFromSource(E e) {
    T v;
    long t0 = System.currentTimeMillis();
    try {
      if (source == null) {
        throw new CacheUsageExcpetion("source not set");
      }
      if (e.isVirgin() || e.hasException()) {
        v = source.get((K) e.key, t0, null, e.getLastModification());
      } else {
        v = source.get((K) e.key, t0, (T) e.getValue(), e.getLastModification());
      }
      e.setLastModification(t0);
    } catch (Throwable _ouch) {
      v = (T) new ExceptionWrapper(_ouch);
    }
    long t = System.currentTimeMillis();
    return insertFetched(e, v, t0, t);
  }

  protected final long insertFetched(E e, T v, long t0, long t) {
    return insert(e, v, t0, t, INSERT_STAT_UPDATE);
  }

  protected final long insertOnPut(E e, T v, long t0, long t) {
    e.setLastModification(t0);
    return insert(e, v, t0, t, INSERT_STAT_PUT);
  }

  /**
   * Calculate the next refresh time if a timer / expiry is needed and call insert.
   */
  protected final long insert(E e, T v, long t0, long t, byte _updateStatistics) {
    long _nextRefreshTime = maxLinger == 0 ? 0 : Long.MAX_VALUE;
    if (timer != null) {
      if (e.isVirgin() || e.hasException()) {
        _nextRefreshTime = calcNextRefreshTime((K) e.getKey(), v, t0, null);
      } else {
        _nextRefreshTime = calcNextRefreshTime((K) e.getKey(), v, t0, e);
      }
    }
    return insert(e,v,t0,t, _updateStatistics, _nextRefreshTime);
  }

  final static byte INSERT_STAT_NO_UPDATE = 0;
  final static byte INSERT_STAT_UPDATE = 1;
  final static byte INSERT_STAT_PUT = 2;

  /**
   * @param _nextRefreshTime -1/MAXVAL: eternal, 0: expires immediately
   */
  protected final long insert(E e, T _value, long t0, long t, byte _updateStatistics, long _nextRefreshTime) {
    if (_nextRefreshTime == -1) {
      _nextRefreshTime = Long.MAX_VALUE;
    }
    boolean _suppressException =
      _value instanceof ExceptionWrapper && hasSuppressExceptions() && e.getValue() != Entry.INITIAL_VALUE && !e.hasException();
    if (!_suppressException) {
      e.value = _value;
    }

    CacheStorageException _storageException = null;
    if (storage != null && e.isDirty() && (_nextRefreshTime != 0 || hasKeepAfterExpired())) {
      try {
        storage.put(e, _nextRefreshTime);
      } catch (CacheStorageException ex) {
        _storageException = ex;
      } catch (Throwable ex) {
        _storageException = new CacheStorageException(ex);
      }
    }

    synchronized (lock) {
      checkClosed();
      touchedTime = t;
      if (_updateStatistics == INSERT_STAT_UPDATE) {
        if (t0 == 0) {
          loadHitCnt++;
        } else {
          if (_suppressException) {
            suppressedExceptionCnt++;
            fetchExceptionCnt++;
          } else {
            if (_value instanceof ExceptionWrapper) {
              Log log = getLog();
              if (log.isDebugEnabled()) {
                log.debug(
                    "caught exception, expires at: " + formatMillis(_nextRefreshTime),
                    ((ExceptionWrapper) _value).getException());
              }
              fetchExceptionCnt++;
            }
          }
          fetchCnt++;
          fetchMillis += t - t0;
          if (e.isGettingRefresh()) {
            refreshCnt++;
          }
          if (e.isLoadedNonValidAndFetch()) {
            loadNonFreshAndFetchedCnt++;
          } else if (!e.isVirgin()) {
            fetchButHitCnt++;
          }
        }
      } else if (_updateStatistics == INSERT_STAT_PUT) {
        putCnt++;
        if (e.isVirgin()) {
          putNewEntryCnt++;
        }
        if (e.nextRefreshTime == Entry.LOADED_NON_VALID_AND_PUT) {
          peekHitNotFreshCnt++;
        }
      }
      if (_storageException != null) {
        throw _storageException;
      }
    } // synchronized (lock)

    if (_nextRefreshTime == 0) {
      _nextRefreshTime = Entry.FETCH_NEXT_TIME_STATE;
    } else if (_nextRefreshTime == Long.MAX_VALUE) {
       _nextRefreshTime = Entry.FETCHED_STATE;
    }
    return _nextRefreshTime;
  }

  protected long stopStartTimer(long _nextRefreshTime, E e, long now) {
    if (e.task != null) {
      e.task.cancel();
    }
    if ((_nextRefreshTime > Entry.EXPIRY_TIME_MIN && _nextRefreshTime <= now) &&
      (_nextRefreshTime < -1 && (now >= -_nextRefreshTime))) {
      return Entry.EXPIRED_STATE;
    }
    if (hasSharpTimeout() && _nextRefreshTime > Entry.EXPIRY_TIME_MIN) {
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
  protected void timerEvent(final E e, long _executionTime) {
    if (refreshPool != null) {
      synchronized (lock) {
        if (isClosed()) { return; }
        if (e.task == null) {
          return;
        }
        if (e.isRemovedFromReplacementList()) {
          return;
        }
        if (mainHashCtrl.remove(mainHash, e)) {
          refreshHash = refreshHashCtrl.insert(refreshHash, e);
          if (e.hashCode != modifiedHash(e.key.hashCode())) {
            synchronized (lock) {
              synchronized (e) {
                if (!e.isRemovedState() && removeEntryFromHash(e)) {
                  expiredRemoveCnt++;
                }
              }
            }
            return;
          }
          Runnable r = new Runnable() {
            @Override
            public void run() {
              synchronized (e) {
                if (e.isRemovedFromReplacementList() || e.isRemovedState() || e.isFetchInProgress()) {
                  return;
                }
                e.setGettingRefresh();
              }
              try {
                long t = fetch(e);
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
                } catch (CacheClosedException ignore) { }
              }
             }
          };
          boolean _submitOkay = refreshPool.submit(r);
          if (_submitOkay) {
            return;
          }
          refreshSubmitFailedCnt++;
        }
      }
    }
    synchronized (e) {
      long nrt = e.nextRefreshTime;
      if (nrt < Entry.EXPIRY_TIME_MIN) {
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

  protected void expireEntry(E e) {
    synchronized (e) {
      if (e.isRemovedState() || e.isExpiredState()) {
        return;
      }
      if (e.isFetchInProgress()) {
        e.nextRefreshTime = Entry.FETCH_IN_PROGRESS_NON_VALID;
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
  final protected ClosableConcurrentHashEntryIterator<Entry> iterateAllHeapEntries() {
    return
      new ClosableConcurrentHashEntryIterator(
        mainHashCtrl, mainHash, refreshHashCtrl, refreshHash);
  }

  @Override
  public void removeAllAtOnce(Set<K> _keys) {
  }


  /** JSR107 convenience getAll from array */
  public Map<K, T> getAll(K[] _keys) {
    return getAll(new HashSet<K>(Arrays.asList(_keys)));
  }

  /**
   * JSR107 bulk interface
   */
  public Map<K, T> getAll(final Set<? extends K> _keys) {
    K[] ka = (K[]) new Object[_keys.size()];
    int i = 0;
    for (K k : _keys) {
      ka[i++] = k;
    }
    T[] va = (T[]) new Object[ka.length];
    getBulk(ka, va, new BitSet(), 0, ka.length);
    return new AbstractMap<K, T>() {
      @Override
      public T get(Object key) {
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
      public Set<Entry<K, T>> entrySet() {
        return new AbstractSet<Entry<K, T>>() {
          @Override
          public Iterator<Entry<K, T>> iterator() {
            return new Iterator<Entry<K, T>>() {
              Iterator<? extends K> it = _keys.iterator();
              @Override
              public boolean hasNext() {
                return it.hasNext();
              }

              @Override
              public Entry<K, T> next() {
                final K k = it.next();
                final T t = BaseCache.this.get(k);
                return new Entry<K, T>() {
                  @Override
                  public K getKey() {
                    return k;
                  }

                  @Override
                  public T getValue() {
                    return t;
                  }

                  @Override
                  public T setValue(T value) {
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
      }
    };
  }

  /**
   * Retrieve
   */
  @SuppressWarnings("unused")
  public void getBulk(K[] _keys, T[] _result, BitSet _fetched, int s, int e) {
    sequentialGetFallBack(_keys, _result, _fetched, s, e);
  }


  final void sequentialFetch(E[] ea, K[] _keys, T[] _result, BitSet _fetched, int s, int end) {
    for (int i = s; i < end; i++) {
      E e = ea[i];
      if (e == null) { continue; }
      if (!e.isDataValidState()) {
        synchronized (e) {
          if (!e.isDataValidState()) {
            fetch(e);
          }
          _result[i] = (T) e.getValue();
        }
      }
    }
  }

  final void sequentialGetFallBack(K[] _keys, T[] _result, BitSet _fetched, int s, int e) {
    for (int i = s; i < e; i++) {
      if (!_fetched.get(i)) {
        try {
          _result[i] = get(_keys[i]);
        } catch (Exception ignore) {
        }
      }
    }
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
      if (storage != null) {
        return storage.getTotalEntryCount();
      }
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
    return fetchCnt - fetchButHitCnt;
  }

  protected int getFetchesInFlight() {
    long _fetchesBecauseOfNoEntries = getFetchesBecauseOfNewEntries();
    return (int) (newEntryCnt - putNewEntryCnt - virginEvictCnt
        - loadNonFreshCnt
        - loadHitCnt
        - _fetchesBecauseOfNoEntries);
  }

  protected IntegrityState getIntegrityState() {
    synchronized (lock) {
      return new IntegrityState()
        .checkEquals(
            "newEntryCnt - virginEvictCnt == " +
                "getFetchesBecauseOfNewEntries() + getFetchesInFlight() + putNewEntryCnt + loadNonFreshCnt + loadHitCnt",
            newEntryCnt - virginEvictCnt,
            getFetchesBecauseOfNewEntries() + getFetchesInFlight() + putNewEntryCnt + loadNonFreshCnt + loadHitCnt)
        .checkLessOrEquals("getFetchesInFlight() <= 100", getFetchesInFlight(), 100)
        .checkEquals("newEntryCnt == getSize() + evictedCnt + expiredRemoveCnt + removeCnt", newEntryCnt, getLocalSize() + evictedCnt + expiredRemoveCnt + removedCnt)
        .checkEquals("newEntryCnt == getSize() + evictedCnt + getExpiredCnt() - expiredKeptCnt + removeCnt", newEntryCnt, getLocalSize() + evictedCnt + getExpiredCnt() - expiredKeptCnt + removedCnt)
        .checkEquals("mainHashCtrl.size == Hash.calcEntryCount(mainHash)", mainHashCtrl.size, Hash.calcEntryCount(mainHash))
        .checkEquals("refreshHashCtrl.size == Hash.calcEntryCount(refreshHash)", refreshHashCtrl.size, Hash.calcEntryCount(refreshHash))
        .check("!!evictionNeeded | (getSize() <= maxSize)", !!evictionNeeded | (getLocalSize() <= maxSize))
        .check("storage => storage.getAlert() < 2", storage == null || storage.getAlert() < 2);
    }
  }

  /** Check internal data structures and throw and exception if something is wrong, used for unit testing */
  public final void checkIntegrity() {
    synchronized (lock) {
      IntegrityState is = getIntegrityState();
      if (is.getStateFlags() > 0) {
        throw new CacheIntegrityError(is.getStateDescriptor(), is.getFailingChecks(), toString());
      }
    }
  }


  public final Info getInfo() {
    synchronized (lock) {
      long t = System.currentTimeMillis();
      if (info != null &&
          (info.creationTime + info.creationDeltaMs * 17 + 333 > t)) {
        return info;
      }
      info = getLatestInfo(t);
    }
    return info;
  }

  public final Info getLatestInfo() {
    return getLatestInfo(System.currentTimeMillis());
  }

  private Info getLatestInfo(long t) {
    synchronized (lock) {
      info = new Info();
      info.creationTime = t;
      info.creationDeltaMs = (int) (System.currentTimeMillis() - t);
      return info;
    }
  }

  protected String getExtraStatistics() { return ""; }

  static String timestampToString(long t) {
    if (t == 0) {
      return "-";
    }
    return formatMillis(t);
  }

  /**
   * Return status information. The status collection is time consuming, so this
   * is an expensive operation.
   */
  @Override
  public String toString() {
    synchronized (lock) {
      Info fo = getLatestInfo();
      return "Cache{" + name + "}"
        + "("
        + "size=" + fo.getSize() + ", "
        + "maxSize=" + fo.getMaxSize() + ", "
        + "usageCnt=" + fo.getUsageCnt() + ", "
        + "missCnt=" + fo.getMissCnt() + ", "
        + "fetchCnt=" + fo.getFetchCnt() + ", "
        + "fetchButHitCnt=" + fetchButHitCnt + ", "
        + "heapHitCnt=" + fo.hitCnt + ", "
        + "virginEvictCnt=" + virginEvictCnt + ", "
        + "fetchesInFlightCnt=" + fo.getFetchesInFlightCnt() + ", "
        + "newEntryCnt=" + fo.getNewEntryCnt() + ", "
        + "bulkGetCnt=" + fo.getBulkGetCnt() + ", "
        + "refreshCnt=" + fo.getRefreshCnt() + ", "
        + "refreshSubmitFailedCnt=" + fo.getRefreshSubmitFailedCnt() + ", "
        + "refreshHitCnt=" + fo.getRefreshHitCnt() + ", "
        + "putCnt=" + fo.getPutCnt() + ", "
        + "putNewEntryCnt=" + fo.getPutNewEntryCnt() + ", "
        + "expiredCnt=" + fo.getExpiredCnt() + ", "
        + "evictedCnt=" + fo.getEvictedCnt() + ", "
        + "removedCnt=" + fo.getRemovedCnt() + ", "
        + "storageLoadCnt=" + fo.getStorageLoadCnt() + ", "
        + "storageMissCnt=" + fo.getStorageMissCnt() + ", "
        + "storageHitCnt=" + fo.getStorageHitCnt() + ", "
        + "hitRate=" + fo.getDataHitString() + ", "
        + "collisionCnt=" + fo.getCollisionCnt() + ", "
        + "collisionSlotCnt=" + fo.getCollisionSlotCnt() + ", "
        + "longestCollisionSize=" + fo.getLongestCollisionSize() + ", "
        + "hashQuality=" + fo.getHashQualityInteger() + ", "
        + "msecs/fetch=" + (fo.getMillisPerFetch() >= 0 ? fo.getMillisPerFetch() : "-")  + ", "
        + "created=" + timestampToString(fo.getStarted()) + ", "
        + "cleared=" + timestampToString(fo.getCleared()) + ", "
        + "touched=" + timestampToString(fo.getTouched()) + ", "
        + "fetchExceptionCnt=" + fo.getFetchExceptionCnt() + ", "
        + "suppressedExceptionCnt=" + fo.getSuppressedExceptionCnt() + ", "
        + "internalExceptionCnt=" + fo.getInternalExceptionCnt() + ", "
        + "keyMutationCnt=" + fo.getKeyMutationCnt() + ", "
        + "infoCreated=" + timestampToString(fo.getInfoCreated()) + ", "
        + "infoCreationDeltaMs=" + fo.getInfoCreationDeltaMs() + ", "
        + "impl=\"" + getClass().getSimpleName() + "\""
        + getExtraStatistics() + ", "
        + "integrityState=" + fo.getIntegrityDescriptor() + ")";
    }
  }

  /**
   * Stable interface to request information from the cache, the object
   * safes values that need a longer calculation time, other values are
   * requested directly.
   */
  public class Info {

    int size = BaseCache.this.getLocalSize();
    long creationTime;
    int creationDeltaMs;
    long missCnt = fetchCnt - refreshCnt + peekHitNotFreshCnt + peekMissCnt;
    long storageMissCnt = loadMissCnt + loadNonFreshCnt + loadNonFreshAndFetchedCnt;
    long storageLoadCnt = storageMissCnt + loadHitCnt;
    long newEntryCnt = BaseCache.this.newEntryCnt - virginEvictCnt;
    long hitCnt = getHitCnt();
    long usageCnt =
        hitCnt + newEntryCnt + peekMissCnt;
    CollisionInfo collisionInfo;
    String extraStatistics;
    int fetchesInFlight = BaseCache.this.getFetchesInFlight();

    {
      collisionInfo = new CollisionInfo();
      Hash.calcHashCollisionInfo(collisionInfo, mainHash);
      Hash.calcHashCollisionInfo(collisionInfo, refreshHash);
      extraStatistics = BaseCache.this.getExtraStatistics();
      if (extraStatistics.startsWith(", ")) {
        extraStatistics = extraStatistics.substring(2);
      }
    }

    IntegrityState integrityState = getIntegrityState();

    String percentString(double d) {
      String s = Double.toString(d);
      return (s.length() > 5 ? s.substring(0, 5) : s) + "%";
    }

    public String getName() { return name; }
    public String getImplementation() { return BaseCache.this.getClass().getSimpleName(); }
    public int getSize() { return size; }
    public int getMaxSize() { return maxSize; }
    public long getStorageHitCnt() { return loadHitCnt; }
    public long getStorageLoadCnt() { return storageLoadCnt; }
    public long getStorageMissCnt() { return storageMissCnt; }
    public long getReadUsageCnt() { return usageCnt - putCnt; }
    public long getUsageCnt() { return usageCnt; }
    public long getMissCnt() { return missCnt; }
    public long getNewEntryCnt() { return newEntryCnt; }
    public long getFetchCnt() { return fetchCnt; }
    public int getFetchesInFlightCnt() { return fetchesInFlight; }
    public long getBulkGetCnt() { return bulkGetCnt; }
    public long getRefreshCnt() { return refreshCnt; }
    public long getInternalExceptionCnt() { return internalExceptionCnt; }
    public long getRefreshSubmitFailedCnt() { return refreshSubmitFailedCnt; }
    public long getSuppressedExceptionCnt() { return suppressedExceptionCnt; }
    public long getFetchExceptionCnt() { return fetchExceptionCnt; }
    public long getRefreshHitCnt() { return refreshHitCnt; }
    public long getExpiredCnt() { return BaseCache.this.getExpiredCnt(); }
    public long getEvictedCnt() { return evictedCnt - virginEvictCnt; }
    public long getRemovedCnt() { return BaseCache.this.removedCnt; }
    public long getPutNewEntryCnt() { return putNewEntryCnt; }
    public long getPutCnt() { return putCnt; }
    public long getKeyMutationCnt() { return keyMutationCount; }
    public double getDataHitRate() {
      long cnt = getReadUsageCnt();
      return cnt == 0 ? 100 : (cnt - missCnt) * 100D / cnt;
    }
    public String getDataHitString() { return percentString(getDataHitRate()); }
    public double getEntryHitRate() { return usageCnt == 0 ? 100 : (usageCnt - newEntryCnt + putCnt) * 100D / usageCnt; }
    public String getEntryHitString() { return percentString(getEntryHitRate()); }
    /** How many items will be accessed with collision */
    public int getCollisionPercentage() {
      return
        (size - collisionInfo.collisionCnt) * 100 / size;
    }
    /** 100 means each collision has its own slot */
    public int getSlotsPercentage() {
      return collisionInfo.collisionSlotCnt * 100 / collisionInfo.collisionCnt;
    }
    public int getHq0() {
      return Math.max(0, 105 - collisionInfo.longestCollisionSize * 5) ;
    }
    public int getHq1() {
      final int _metricPercentageBase = 60;
      int m =
        getCollisionPercentage() * ( 100 - _metricPercentageBase) / 100 + _metricPercentageBase;
      m = Math.min(100, m);
      m = Math.max(0, m);
      return m;
    }
    public int getHq2() {
      final int _metricPercentageBase = 80;
      int m =
        getSlotsPercentage() * ( 100 - _metricPercentageBase) / 100 + _metricPercentageBase;
      m = Math.min(100, m);
      m = Math.max(0, m);
      return m;
    }
    public int getHashQualityInteger() {
      if (size == 0 || collisionInfo.collisionSlotCnt == 0) {
        return 100;
      }
      int _metric0 = getHq0();
      int _metric1 = getHq1();
      int _metric2 = getHq2();
      if (_metric1 < _metric0) {
        int v = _metric0;
        _metric0 = _metric1;
        _metric1 = v;
      }
      if (_metric2 < _metric0) {
        int v = _metric0;
        _metric0 = _metric2;
        _metric2 = v;
      }
      if (_metric2 < _metric1) {
        int v = _metric1;
        _metric1 = _metric2;
        _metric2 = v;
      }
      if (_metric0 <= 0) {
        return 0;
      }
      _metric0 = _metric0 + ((_metric1 - 50) * 5 / _metric0);
      _metric0 = _metric0 + ((_metric2 - 50) * 2 / _metric0);
      _metric0 = Math.max(0, _metric0);
      _metric0 = Math.min(100, _metric0);
      return _metric0;
    }
    public double getMillisPerFetch() { return fetchCnt == 0 ? -1 : (fetchMillis * 1D / fetchCnt); }
    public long getFetchMillis() { return fetchMillis; }
    public int getCollisionCnt() { return collisionInfo.collisionCnt; }
    public int getCollisionSlotCnt() { return collisionInfo.collisionSlotCnt; }
    public int getLongestCollisionSize() { return collisionInfo.longestCollisionSize; }
    public String getIntegrityDescriptor() { return integrityState.getStateDescriptor(); }
    public long getStarted() { return startedTime; }
    public long getCleared() { return clearedTime; }
    public long getTouched() { return touchedTime; }
    public long getInfoCreated() { return creationTime; }
    public int getInfoCreationDeltaMs() { return creationDeltaMs; }
    public int getHealth() {
      if (storage != null && storage.getAlert() == 2) {
        return 2;
      }
      if (integrityState.getStateFlags() > 0 ||
          getHashQualityInteger() < 5) {
        return 2;
      }
      if (storage != null && storage.getAlert() == 1) {
        return 1;
      }
      if (getHashQualityInteger() < 30 ||
        getKeyMutationCnt() > 0 ||
        getInternalExceptionCnt() > 0) {
        return 1;
      }
      return 0;
    }
    public String getExtraStatistics() {
      return extraStatistics;
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
    E entry;

    public void run() {
      timerEvent(entry, scheduledExecutionTime());
    }
  }

  public static class Tunable extends TunableConstants {

    /**
     * Implementation class to use by default.
     */
    public Class<? extends BaseCache> defaultImplementation = ClockProPlusCache.class;

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


  }

}
