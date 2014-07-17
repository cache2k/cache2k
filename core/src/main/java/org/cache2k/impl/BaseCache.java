package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2014 headissue GmbH, Munich
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
import org.cache2k.ClosableIterator;
import org.cache2k.ExperimentalBulkCacheSource;
import org.cache2k.Cache;
import org.cache2k.CacheConfig;
import org.cache2k.MutableCacheEntry;
import org.cache2k.StorageConfiguration;
import org.cache2k.impl.threading.Futures;
import org.cache2k.impl.threading.LimitedPooledExecutor;
import org.cache2k.impl.timer.GlobalTimerService;
import org.cache2k.impl.timer.TimerService;
import org.cache2k.jmx.CacheMXBean;
import org.cache2k.CacheSourceWithMetaInfo;
import org.cache2k.CacheSource;
import org.cache2k.RefreshController;
import org.cache2k.PropagatedCacheException;

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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * Foundation for all cache variants. All common functionality is in here.
 * For a (in-memory) cache we need three things: a fast hash table implementation, an
 * LRU list (a simple double linked list), and a fast timer.
 * The variants implement different eviction strategies.
 *
 * <p/>Locking: The cache has a single structure lock obtained via {@link #lock} and also
 * locks on each entry for operations on it. Though, operations that happen on a
 * single entry get serialized.
 *
 * @author Jens Wilke; created: 2013-07-09
 */
@SuppressWarnings({"unchecked", "SynchronizationOnLocalVariableOrMethodParameter"})
public abstract class BaseCache<E extends BaseCache.Entry, K, T>
  implements Cache<K, T>, CanCheckIntegrity, Iterable<CacheEntry<K, T>>, StorageAdapter.Parent {

  static final Random SEED_RANDOM = new Random(new SecureRandom().nextLong());
  static int cacheCnt = 0;

  protected static final Tunable TUNABLE = TunableFactory.get(Tunable.class);

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

  /** Time in milliseconds we keep an element */
  protected int maxLinger = 10 * 60 * 1000;
  protected String name;
  protected CacheManagerImpl manager;
  protected CacheSourceWithMetaInfo<K, T> source;
  /** Statistics */

  protected RefreshController<T> refreshController;

  protected Info info;

  protected long clearedTime = 0;
  protected long startedTime;
  protected long touchedTime;
  protected int timerCancelCount = 0;

  protected long keyMutationCount = 0;
  protected long putCnt = 0;
  protected long putNewEntryCnt = 0;
  protected long removeCnt = 0;
  protected long expiredKeptCnt = 0;
  protected long expiredRemoveCnt = 0;
  protected long evictedCnt = 0;
  protected long refreshCnt = 0;
  protected long peekMissCnt = 0;

  protected long fetchCnt = 0;

  protected long fetchedExpiredCnt = 0;
  protected long bulkGetCnt = 0;
  protected long fetchMillis = 0;
  protected long refreshHitCnt = 0;
  protected long newEntryCnt = 0;

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
   * peek() initiates a storage read which returns null. => newEntryCnt++, storageMissCnt++
   */
  protected long storageMissCnt = 0;

  /**
   * A newly inserted entry was removed by the eviction without the fetch to complete.
   */
  protected long virginEvictCnt = 0;

  /**
   * New inserted but evicted before fetch could be started
   */
  protected long virginForFetchLostCnt = 0;

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

  protected Hash<E> txHashCtrl;
  protected E[] txHash;

  protected ExperimentalBulkCacheSource<K, T> experimentalBulkCacheSource;

  protected BulkCacheSource<K, T> bulkCacheSource;

  protected HashSet<K> bulkKeysCurrentlyRetrieved;

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

  protected StorageAdapter storage;

  protected TimerService timerService = GlobalTimerService.getInstance();

  private int featureBits = 0;

  private static final int SHARP_TIMEOUT_FEATURE = 1 << 0;
  private static final int KEEP_AFTER_EXPIRED = 1 << 1;
  protected static final int FEATURE_BIT_NUMBER_FOR_SUBCLASS = 8;

  protected final boolean hasSharpTimeout() {
    return (featureBits & SHARP_TIMEOUT_FEATURE) > 0;
  }

  protected final boolean hasKeepAfterExpired() {
    return (featureBits & KEEP_AFTER_EXPIRED) > 0;
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
    if (c.getExpiryMillis() > 0) {
      maxLinger = c.getExpirySeconds();
    } else {
      setExpirySeconds(c.getExpirySeconds());
    }
    setFeatureBit(KEEP_AFTER_EXPIRED, c.isKeepDataAfterExpired());
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
    } else {
    }
  }

  /** called via reflection from CacheBuilder */
  public void setRefreshController(RefreshController<T> lc) {
    refreshController = lc;
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

  private boolean isNeedingTimer() {
    return maxLinger > 0 || refreshController != null;
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
   * one entry or key at any time. Clear, of course affects all entries.
   */
  private final void processClearWithStorage() {
    Future<?> _waitFuture = null;
    StorageClearTask t = new StorageClearTask();
    boolean _untouchedHeapCache;
    synchronized (lock) {
      _untouchedHeapCache = touchedTime == clearedTime && getLocalSize() == 0;
      if (!storage.checkStorageStillDisconnectedForClear()) {
        t.allLocalEntries = iterateAllLocalEntries();
        t.allLocalEntries.setStopOnClear(false);
      }
      t.storage = storage;
      t.storage.disconnectStorageForClear();
    }
    try {
      if (_untouchedHeapCache) {
        FutureTask<Future<Void>> f = new FutureTask<>(t);
        updateShutdownWaitFuture(f);
        f.run();
        _waitFuture = f.get();
        updateShutdownWaitFuture(_waitFuture);
      } else {
        updateShutdownWaitFuture(manager.getThreadPool().execute(t));
      }
    } catch (Exception ex) {
      throw new CacheStorageException(ex);
    }
    clearLocalCache();
    if (_waitFuture != null) {
      try {
        _waitFuture.get();
      } catch (InterruptedException e) {
      } catch (ExecutionException e) {
        throw new CacheStorageException(e);
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

  class StorageClearTask implements LimitedPooledExecutor.NeverRunInCallingTask<Future<Void>> {

    ClosableConcurrentHashEntryIterator<Entry> allLocalEntries;
    StorageAdapter storage;

    @Override
    public Future<Void> call() {
      try {
        if (allLocalEntries != null) {
          waitForEntryOperations();
        }
        Future<Void> f = storage.startClearingAndReconnection();
        updateShutdownWaitFuture(f);
        storage = null;
        return f;
      } catch (Throwable t) {
        if (allLocalEntries != null) {
          allLocalEntries.close();
        }
        getLog().warn("clear exception, when signalling storage", t);
        storage.disable(t);
        throw t;
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
    if (shutdownInitiated) {
      throw new CacheClosedException();
    }
  }

  public final void clear() {
    checkClosed();
    if (storage != null) {
      processClearWithStorage();
      return;
    }
    clearLocalCache();
  }

  protected final void clearLocalCache() {
    synchronized (lock) {
      checkClosed();
      Iterator<Entry> it = iterateAllLocalEntries();
      while (it.hasNext()) {
        Entry e = it.next();
        e.removedFromList();
        cancelExpiryTimer(e);
      }
      removeCnt += getLocalSize();
      initializeHeapCache();
      clearedTime = System.currentTimeMillis();
      touchedTime = clearedTime;
    }
  }

  protected void initializeHeapCache() {
    if (mainHashCtrl != null) {
      mainHashCtrl.cleared();
      refreshHashCtrl.cleared();
    }
    mainHashCtrl = new Hash<>();
    refreshHashCtrl = new Hash<>();
    txHashCtrl = new Hash<>();
    mainHash = mainHashCtrl.init((Class<E>) newEntry().getClass());
    refreshHash = refreshHashCtrl.init((Class<E>) newEntry().getClass());
    txHash = txHashCtrl.init((Class<E>) newEntry().getClass());
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

  void destroyCancelTimer() {
    synchronized (lock) {
      if (timer != null) {
        timer.cancel();
      }
    }
  }

  boolean destroyRefreshOngoing() {
    synchronized (lock) {
      return getFetchesInFlight() > 0;
    }
  }

  /**
   * Free all resources.
   */
  public void destroy() {
    synchronized (lock) {
      if (shutdownInitiated) {
        return;
      }
      shutdownInitiated = true;
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
    if (storage != null) {
      storage.shutdown();
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
        return iterateAllLocalEntries();
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
    public void close() throws Exception {
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
  protected abstract void insertIntoReplcamentList(E e);

  /**
   * Entry object factory. Return an entry of the proper entry subtype for
   * the replacement/eviction algorithm.
   */
  protected abstract E newEntry();


  /**
   * Find an entry that should be evicted. Called within structure lock.
   * The replacement algorithm can actually remove the entry from the replacement
   * list or keep it in the replacement list, this is detected via
   * {@link org.cache2k.impl.BaseCache.Entry#isRemovedFromReplacementList()} by the
   * basic cache implementation. The entry is finally removed from the cache hash
   * later in time.
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
   * Check whether we have an entry in an the ghost table
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
    return returnValue(getEntryInternal(key));
  }

  /**
   * Wrap entry in a separate object instance. We can return the entry directly, however we need
   * lock on the entry object.
   */
  protected CacheEntry<K, T> returnEntry(final Entry<E, K, T> e) {
    CacheEntry<K,T> ce = new CacheEntry<K, T>() {
      @Override
      public K getKey() { return e.getKey(); }

      @Override
      public T getValue() { return e.getValue(); }

      @Override
      public Throwable getException() { return e.getException(); }

      @Override
      public long getLastModification() { return e.getLastModification(); }

      @Override
      public String toString() {
        return "CacheEntry(" +
          "key=" + getKey() + ", " +
          "value=" + getValue() + ", " +
          ((getException() != null) ? "exception=" + e.getException() + ", " : "") +
          "lastModification='" + (new java.sql.Timestamp(getLastModification())) + "')";
      }

    };
    return ce;
  }

  public CacheEntry<K, T> getEntry(K key) {
    return returnEntry(getEntryInternal(key));
  }

  protected E getEntryInternal(K key) {
    for (;;) {
      E e = lookupOrNewEntrySynchronized(key);
      if (e.hasFreshData()) { return e; }
      synchronized (e) {
        if (!e.isDataValidState()) {
          if (e.isRemovedNonValidState()) {
            continue;
          }
          fetch(e);
        }
      }
      evictEventually();
      return e;
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
    for (;;) {
      E e = lookupOrNewEntrySynchronized((K) se.getKey());
      if (e.hasFreshData()) { return e; }
      synchronized (e) {
        if (!e.isDataValidState()) {
          if (e.isRemovedNonValidState()) {
            continue;
          }
          insertEntryFromStorage(se, e, _needsFetch);
        }
      }
      evictEventually();
      return e;
    }
  }

  protected final void evictEventually() {
    while (evictionNeeded) {
      E e;
      synchronized (lock) {
        if (getLocalSize() <= maxSize) {
          evictionNeeded = false;
          return;
        }
        e = findEvictionCandidate();
        if (e.isRemovedFromReplacementList()) {
          evictedButInHashCnt++;
        }
      }
      synchronized (e) {
        if (e.isRemovedState() || e.isRemovedNonValidState()) {
          continue;
        }
        synchronized (lock) {
          if (e.isRemovedFromReplacementList()) {
            removeEntryFromHash(e);
            evictedButInHashCnt--;
          } else {
            removeEntry(e);
          }
          evictedCnt++;
          evictionNeeded = getLocalSize() > maxSize;
        }
        if (storage != null && e.isDataValidState()) {
          storage.evict(e);
        }
      }
    }
  }

  protected void removeEntry(E e) {
    removeEntryFromReplacementList(e);
    removeEntryFromHash(e);
  }

  /**
   * Return the entry, if it is in the cache, without invoking the
   * cache source.
   *
   * <p>The cache storage is asked whether the entry is present.
   * If the entry is not present, this result is cached in the local
   * cache.
   */
  @Override
  public T peek(K key) {
    int hc = modifiedHash(key.hashCode());
    for (;;) {
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
      if (e.isVirgin() && storage != null) {
        synchronized (e) {
          if (e.isRemovedState() || e.isRemovedNonValidState()) {
            continue;
          }
          fetchWithStorage(e, false);
        }
      }
      evictEventually();
      if (e.hasFreshData()) {
        return returnValue(e);
      }
      peekMissCnt++;
      return null;
    }
  }


  @Override
  public void put(K key, T value) {
    E e;
    for (;;) {
      synchronized (lock) {
        putCnt++;
        int hc = modifiedHash(key.hashCode());
        e = lookupEntry(key, hc);
        if (e == null) {
          e = newEntry(key, hc);
          putNewEntryCnt++;
        } else {
          if (e.isDataValidState()) {
            e.setReputState();
          }
        }
      }
      long t = System.currentTimeMillis();
      synchronized (e) {
        if (e.isRemovedState() || e.isRemovedNonValidState()) {
          continue;
        }
        insertOnPut(e, value, t, t);
      }
      break;
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
  @Override
  public void remove(K key) {
    int hc = modifiedHash(key.hashCode());
    E e = lookupEntryUnsynchronized(key, hc);
    if (e == null) {
      synchronized (lock) {
        e = lookupEntry(key, hc);
        if (e == null && storage != null) {
          e = newEntry(key, hc);
        }
      }
    }
    if (e != null) {
      synchronized (e) {
        if (!e.isRemovedFromReplacementList()) {
          synchronized (lock) {
            removeEntry(e);
            removeCnt++;
          }
          if (storage != null) {
            storage.remove(key);
          }
        }
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
        e = lookupEntry(key, hc);
        if (e == null) {
          e = newEntry(key, hc);
          e.setVirginForFetchState();
        }
      }
    }
    return e;
  }

  protected T returnValue(Entry<E, K,T> e) {
    T v = e.value;
    if (v instanceof ExceptionWrapper) {
      throw new PropagatedCacheException(((ExceptionWrapper) v).getException());
    }
    return v;
  }

  /** Used by remove() and put() */
  protected E lookupEntryWoUsageRecording(K key, int hc) {
    E e = Hash.lookup(mainHash, key, hc);
    if (e == null) {
      return Hash.lookup(refreshHash, key, hc);
    }
    return e;
  }

  protected E lookupEntrySynchronized(K key) {
    int hc = modifiedHash(key.hashCode());
    E e = lookupEntryUnsynchronized(key, hc);
    if (e != null) {
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
   * Insert new entry in all structures (hash and replacement list). Evict an
   * entry we reached the maximum size.
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
      insertIntoReplcamentList(e);
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
  private void removeEntryFromHash(E e) {
    boolean f =
      mainHashCtrl.remove(mainHash, e) || refreshHashCtrl.remove(refreshHash, e);
    checkForHashCodeChange(e);

    cancelExpiryTimer(e);
    if (e.isDataValidState()) {
      e.setRemovedState();
    } else {
      if (e.isVirgin()) {
        virginEvictCnt++;
      }
      if (e.isVirginForFetchState()) {
        virginForFetchLostCnt++;
      }
      e.setRemovedNonValidState();
    }
  }

  private void cancelExpiryTimer(Entry e) {
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

  /**
   * Time when the element should be fetched again from the underlying storage.
   * If 0 then the object should not be cached at all. -1 means no expiry.
   *
   * @param _newObject might be a fetched value or an exception wrapped into the {@link ExceptionWrapper}
   */
  protected long calcNextRefreshTime(
     T _oldObject, T _newObject, long _lastUpdate, long now) {
    RefreshController<T> lc = refreshController;
    if (lc != null && !(_newObject instanceof ExceptionWrapper)) {
      long t = lc.calculateNextRefreshTime(_oldObject, _newObject, _lastUpdate, now);
      if (maxLinger > 0) {
        long _tMaximum = maxLinger + now;
        if (t > _tMaximum) {
          return _tMaximum;
        }
        if (t < -1 && -t > _tMaximum) {
          return -_tMaximum;
        }
      }
      return t;
    }
    if (maxLinger > 0) {
      return maxLinger + now;
    } else {
      return maxLinger;
    }
  }

  protected void fetch(final E e) {
    if (storage != null) {
      fetchWithStorage(e, true);
    } else {
      fetchFromSource(e);
    }
  }

  /**
   *
   * @param e
   * @param _peekOnly if true it means don't fetch the data from the source
   */
  protected void fetchWithStorage(E e, boolean _needsFetch) {
    if (!e.isVirgin()) {
      if (_needsFetch) {
        fetchFromSource(e);
      }
      return;
    }
    StorageEntry se = storage.get(e.key);
    if (se == null) {
      if (_needsFetch) {
        fetchFromSource(e);
        return;
      }
      touchedTime = System.currentTimeMillis();
      e.setStorageMiss();
      synchronized (lock) {
        storageMissCnt++;
      }
      return;
    }
    insertEntryFromStorage(se, e, _needsFetch);
  }

  protected void insertEntryFromStorage(StorageEntry se, E e, boolean _needsFetch) {
    e.setLastModificationFromStorage(se.getCreatedOrUpdated());
    long t0 = System.currentTimeMillis();
    T v = (T) se.getValueOrException();
    long _nextRefreshTime = maxLinger == 0 ? Entry.FETCH_NEXT_TIME_STATE : Entry.FETCHED_STATE;
    long _expiryTimeFromStorage = se.getExpiryTime();
    boolean _expired = (_expiryTimeFromStorage > 0 && _expiryTimeFromStorage <= t0);
    if (!_expired && timer != null) {
      _nextRefreshTime = calcNextRefreshTime(null, v, 0L, se.getCreatedOrUpdated());
      _expired = _nextRefreshTime > Entry.EXPIRY_TIME_MIN && _nextRefreshTime <= t0;
    }
    boolean _fetchAlways = timer == null && maxLinger == 0;
    if (_expired || _fetchAlways) {
      if (_needsFetch) {
        e.value = se.getValueOrException();
        e.setExpiredState();
        fetchFromSource(e);
        return;
      } else {
        touchedTime = System.currentTimeMillis();
        e.setStorageMiss();
        synchronized (lock) {
          storageMissCnt++;
        }
        return;
      }
    }
    insert(e, (T) se.getValueOrException(), t0, t0, true, _nextRefreshTime);
  }

  protected void fetchFromSource(E e) {
    T v;
    long t0 = System.currentTimeMillis();
    try {
      if (e.isVirgin() || e.hasException()) {
        if (source == null) {
          throw new CacheUsageExcpetion("source not set");
        }
        v = source.get((K) e.key, t0, null, e.getLastModification());
      } else {
        v = source.get((K) e.key, t0, (T) e.getValue(), e.getLastModification());
      }
      e.setLastModification(t0);
    } catch (Throwable _ouch) {
      v = (T) new ExceptionWrapper(_ouch);
    }
    long t = System.currentTimeMillis();
    insertFetched(e, v, t0, t);
  }

  protected final void insertFetched(E e, T v, long t0, long t) {
    insert(e, v, t0, t, true);
  }

  protected final void insertOnPut(E e, T v, long t0, long t) {
    e.setLastModification(t0);
    insert(e, v, t0, t, false);
  }

  /**
   * Calculate the next refresh time if a timer / expiry is needed and call insert.
   */
  protected final void insert(E e, T v, long t0, long t, boolean _updateStatistics) {
    long _nextRefreshTime = maxLinger == 0 ? Entry.FETCH_NEXT_TIME_STATE : Entry.FETCHED_STATE;
    if (timer != null) {
      if (e.isVirgin() || e.hasException()) {
        _nextRefreshTime = calcNextRefreshTime(null, v, 0L, t0);
      } else {
        _nextRefreshTime = calcNextRefreshTime((T) e.getValue(), v, e.getLastModification(), t0);
      }
    }
    insert(e,v,t0,t,_updateStatistics, _nextRefreshTime);
  }

  protected final void insert(E e, T v, long t0, long t, boolean _updateStatistics, long _nextRefreshTime) {
    touchedTime = t;
    synchronized (lock) {
      if (_updateStatistics) {
        fetchCnt++;
        fetchMillis += t - t0;
        if (e.isGettingRefresh()) {
          refreshCnt++;
        }
        if (e.isExpiredState()) {
          fetchedExpiredCnt++;
        }
      }
      if (e.task != null) {
        e.task.cancel();
      }
      e.value = v;
      if (hasSharpTimeout() || _nextRefreshTime > Entry.EXPIRY_TIME_MIN) {
        _nextRefreshTime = -_nextRefreshTime;
      }
      if (timer != null &&
        (_nextRefreshTime > Entry.EXPIRY_TIME_MIN || _nextRefreshTime < -1)) {
        if (_nextRefreshTime < -1) {
          _nextRefreshTime = -_nextRefreshTime;
          long _timerTime = _nextRefreshTime - 3;
          if (_timerTime >= t) {
            MyTimerTask tt = new MyTimerTask();
            tt.entry = e;
            timer.schedule(tt, new Date(_timerTime));
            e.task = tt;
          }
        } else {
          MyTimerTask tt = new MyTimerTask();
          tt.entry = e;
          timer.schedule(tt, new Date(_nextRefreshTime));
          e.task = tt;
        }
      } else {
        if (_nextRefreshTime == 0) {
          _nextRefreshTime = Entry.FETCH_NEXT_TIME_STATE;
        } else if (_nextRefreshTime == -1) {
          _nextRefreshTime = Entry.FETCHED_STATE;
        }
      }
    } // synchronized (lock)

    e.nextRefreshTime = _nextRefreshTime;
    if (storage != null && e.isDirty()) {
      storage.put(e);
    }
  }

  /**
   * When the time has come remove the entry from the cache.
   */
  protected void timerEvent(final E e) {
    if (e.isRemovedFromReplacementList()) {
      return;
    }
    if (refreshPool != null) {
      synchronized (lock) {
        if (e.task == null) {
          return;
        }
        if (mainHashCtrl.remove(mainHash, e)) {
          refreshHash = refreshHashCtrl.insert(refreshHash, e);
          if (e.hashCode != modifiedHash(e.key.hashCode())) {
            expiredRemoveCnt++;
            removeEntryFromHash(e);
            return;
          }
          Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                  synchronized (e) {
                    if (e.isRemovedFromReplacementList()) { return; }
                    e.setGettingRefresh();
                    fetch(e);
                  }
                } catch (Throwable ex) {
                  synchronized (lock) {
                    internalExceptionCnt++;
                  }
                  getLog().warn("Refresh exception", ex);
                  expireEntry(e);
                }
             }
          };
          boolean _submitOkay = refreshPool.submit(r);
          if (_submitOkay) { return; }
          refreshSubmitFailedCnt++;
        }

      }

    } else {
      long t = System.currentTimeMillis();
      if (t < e.nextRefreshTime && e.nextRefreshTime > Entry.EXPIRY_TIME_MIN) {
        synchronized (e) {
          t = System.currentTimeMillis();
          if (t < e.nextRefreshTime && e.nextRefreshTime > Entry.EXPIRY_TIME_MIN) {
            e.nextRefreshTime = -e.nextRefreshTime;
            return;
          }
          expireEntry(e);
          return;
        }
      }
    }
    expireEntry(e);
  }

  protected void expireEntry(E e) {
    synchronized (e) {
      if (e.isRemovedState() || e.isExpiredState()) {
        return;
      }
      e.setExpiredState();
      if (storage != null) {
        storage.expire(e);
      }
      synchronized (lock) {
        if (hasKeepAfterExpired()) {
          expiredKeptCnt++;
        } else {
          removeEntry(e);
          expiredRemoveCnt++;
        }
      }
    }
  }

  /**
   * Returns all cache entries within the heap cache. Entries that
   * are expires or contain no valid data are not filtered out.
   */
  final protected ClosableConcurrentHashEntryIterator<Entry> iterateAllLocalEntries() {
    return
      new ClosableConcurrentHashEntryIterator(
        mainHashCtrl, mainHash, refreshHashCtrl, refreshHash);
  }

  @Override
  public void removeAllAtOnce(Set<K> _keys) {
  }


  /** JSR107 convenience getAll from array */
  public Map<K, T> getAll(K[] _keys) {
    return getAll(new HashSet<>(Arrays.asList(_keys)));
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

    E[] _entries = (E[]) new Entry[_keys.length];
    boolean _mayDeadlock;
    synchronized (lock) {
      int _fetchCount = checkAndCreateEntries(_keys, _result, _fetched, _entries, s, e);
      if (_fetchCount == 0) {
        return;
      }
      _mayDeadlock = checkForDeadLockOrExceptions(_entries, s, e);
      if (!_mayDeadlock) {
        bulkGetCnt++;
      }
    }
    if (!_mayDeadlock) {
      try {
        for (int i = s; i < e; i += maximumBulkFetchSize) {
          long t = System.currentTimeMillis();
          int _endIdx = Math.min(e, i + maximumBulkFetchSize);
          fetchBulkLoop(_entries, _keys, _result, _fetched, i, _endIdx - 1, _endIdx, t);
        }
        return;
      } finally {
        freeBulkKeyRetrievalMarkers(_entries);
      }
    }
    sequentialGetFallBack(_keys, _result, _fetched, s, e);
  }


  /**
   * This is called recursively to synchronize on each hash entry.
   *
   * this implementation may deadlock if called on the same data concurrently and in different order.
   * for reason we keep track of all keys that are currently retrieved via bulk request and
   * fall back to the sequential get if needed.
   *
   * @param s start index
   * @param end end index, exclusive
   * @param i working index starting with e-1
   */
  final long fetchBulkLoop(final E[] ea, K[] k, T[] r, BitSet _fetched, int s, int i, int end, long t) {
    for (; i >= s; i--) {
      E e = ea[i];
      if (e == null) { continue; }
      if (!e.isDataValidState()) {
        synchronized (e) {
          if (!e.isDataValidState()) {
            r[i] = null;
            _fetched.set(i, false);
            long t2 = fetchBulkLoop(ea, k, r, _fetched, s, i - 1, end, t);
            if (!e.isDataValidState()) {
              e.setLastModification(t);
              insertFetched(e, r[i], t, t2);
            }
            return t2;
          } else {
          }
        }
      }
      r[i] = (T) e.getValue();
      _fetched.set(i);
    }
    int _needsFetchCnt = 0;
    for (i = s; i < end; i++) {
      if (!_fetched.get(i)) {
        _needsFetchCnt++;
      }
    }
    if (_needsFetchCnt == 0) {
      return 0;
    }
    if (experimentalBulkCacheSource == null && bulkCacheSource == null) {
      sequentialFetch(ea, k, r, _fetched, s, end);
      return 0;
    } if (bulkCacheSource != null) {
      final int[] _index = new int[_needsFetchCnt];
      int idx = 0;
      for (i = s; i < end; i++) {
        if (!_fetched.get(i)) {
          _index[idx++] = i;
        }
      }
      List<CacheEntry<K, T>> _entries = new AbstractList<CacheEntry<K,T>>() {
        @Override
        public CacheEntry<K, T> get(int index) {
          return ea[_index[index]];
        }

        @Override
        public int size() {
          return _index.length;
        }
      };
      try {
        List<T> _resultList = bulkCacheSource.getValues(_entries, t);
        if (_resultList.size() != _index.length) {
          throw new CacheUsageExcpetion("bulk source returned list with wrong length");
        }
        for (i = 0; i < _index.length; i++) {
          r[_index[i]] = _resultList.get(i);
        }
      } catch (Throwable _ouch) {
        T v = (T) new ExceptionWrapper(_ouch);
        for (i = s; i < end; i++) {
          if (!_fetched.get(i)) {
            r[i] = v;
          }
        }
      }
    } else {
      try {
        experimentalBulkCacheSource.getBulk(k, r, _fetched, s, end);
      } catch (Throwable _ouch) {
        T v = (T) new ExceptionWrapper(_ouch);
        for (i = s; i < end; i++) {
          Entry e = ea[i];
          if (!e.isDataValidState()) {
            r[i] = v;
          }
        }
      }
    }
    return System.currentTimeMillis();
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
        _result[i] = get(_keys[i]);
      }
    }
  }

  final void freeBulkKeyRetrievalMarkers(Entry[] _entries) {
    synchronized (lock) {
      for (Entry<E, K, T> et : _entries) {
        if (et == null) { continue; }
        bulkKeysCurrentlyRetrieved.remove(et.key);
      }
    }
  }

  private boolean checkForDeadLockOrExceptions(Entry[] _entries, int s, int e) {
    if (bulkKeysCurrentlyRetrieved == null) {
      bulkKeysCurrentlyRetrieved = new HashSet<>();
    }
    for (int i = s; i < e; i++) {
      Entry _entry = _entries[i];
      if (_entry != null) {
        if ((_entry.getException() != null) ||
            bulkKeysCurrentlyRetrieved.contains(_entry.key)) {
          return true;
        }
      }
    }
    for (int i = s; i < e; i++) {
      Entry<E, K, T> _entry = _entries[i];
      if (_entry != null) {
        bulkKeysCurrentlyRetrieved.add(_entry.key);
      }
    }
    return false;
  }

  /**
   * lookup entries or create new ones if needed, already fill the result
   * if the entry was fetched
   */
  private int checkAndCreateEntries(K[] _keys, T[] _result, BitSet _fetched, Entry[] _entries, int s, int e) {
    int _fetchCount = e - s;
    for (int i = s; i < e; i++) {
      if (_fetched.get(i)) { _fetchCount--; continue; }
      K key = _keys[i];
      int hc = modifiedHash(key.hashCode());
      Entry<E,K,T> _entry = lookupEntry(key, hc);
      if (_entry == null) {
        _entries[i] = newEntry(key, hc);
      } else {
        if (_entry.isDataValidState()) {
          _result[i] = _entry.getValue();
          _fetched.set(i);
          _fetchCount--;
        } else {
          _entries[i] = _entry;
        }
      }
    }
    return _fetchCount;
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
    return storageMissCnt + fetchCnt - fetchedExpiredCnt - refreshCnt;
  }

  public int getFetchesInFlight() {
    return (int) (newEntryCnt - putNewEntryCnt - virginForFetchLostCnt - getFetchesBecauseOfNewEntries());
  }

  protected IntegrityState getIntegrityState() {
    synchronized (lock) {
      return new IntegrityState()
        .checkEquals("newEntryCnt - virginForFetchLostCnt == getFetchesBecauseOfNewEntries() + getFetchesInFlight() + putNewEntryCnt",
          newEntryCnt - virginForFetchLostCnt, getFetchesBecauseOfNewEntries() + getFetchesInFlight() + putNewEntryCnt)
        .checkLessOrEquals("getFetchesInFlight() <= 100", getFetchesInFlight(), 100)
        .checkEquals("newEntryCnt == getSize() + evictedCnt + expiredRemoveCnt + removeCnt", newEntryCnt, getLocalSize() + evictedCnt + expiredRemoveCnt + removeCnt)
        .checkEquals("newEntryCnt == getSize() + evictedCnt + getExpiredCnt() - expiredKeptCnt + removeCnt", newEntryCnt, getLocalSize() + evictedCnt + getExpiredCnt() - expiredKeptCnt + removeCnt)
        .checkEquals("mainHashCtrl.size == Hash.calcEntryCount(mainHash)", mainHashCtrl.size, Hash.calcEntryCount(mainHash))
        .checkEquals("refreshHashCtrl.size == Hash.calcEntryCount(refreshHash)", refreshHashCtrl.size, Hash.calcEntryCount(refreshHash))
        .check("!!evictionNeeded | (getSize() <= maxSize)", !!evictionNeeded | (getLocalSize() <= maxSize))
        .checkLessOrEquals("bulkKeysCurrentlyRetrieved.size() <= getSize()",
          bulkKeysCurrentlyRetrieved == null ? 0 : bulkKeysCurrentlyRetrieved.size(), getLocalSize())
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

  public final CacheMXBean getMXBean() {
    return new Mgmt();
  }

  protected String getExtraStatistics() { return ""; }

  static String timestampToString(long t) {
    if (t == 0) {
      return "-";
    }
    return (new java.sql.Timestamp(t)).toString();
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
        + "storageMissCnt=" + storageMissCnt + ", "
        + "virginEvictCnt=" + virginEvictCnt + ", "
        + "virginForFetchLostCnt=" + virginForFetchLostCnt + ", "
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
        + "hitRate=" + fo.getDataHitString() + ", "
        + "collisionCnt=" + fo.getCollisionCnt() + ", "
        + "collisionSlotCnt=" + fo.getCollisionSlotCnt() + ", "
        + "longestCollisionSize=" + fo.getLongestCollisionSize() + ", "
        + "hashQuality=" + fo.getHashQualityInteger() + ", "
        + "msecs/fetch=" + (fo.getMillisPerFetch() >= 0 ? fo.getMillisPerFetch() : "-")  + ", "
        + "created=" + timestampToString(fo.getStarted()) + ", "
        + "cleared=" + timestampToString(fo.getCleared()) + ", "
        + "touched=" + timestampToString(fo.getTouched()) + ", "
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
    long missCnt = fetchCnt - refreshCnt + peekMissCnt;
    long newEntryCnt = BaseCache.this.newEntryCnt - virginForFetchLostCnt;
    long usageCnt = getHitCnt() + missCnt;
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

    public long getReadUsageCnt() { return usageCnt - (putCnt - putNewEntryCnt); }
    public long getUsageCnt() { return usageCnt; }
    public long getMissCnt() { return missCnt; }
    public long getNewEntryCnt() { return newEntryCnt; }
    public long getFetchCnt() { return fetchCnt; }
    public int getFetchesInFlightCnt() { return fetchesInFlight; }
    public long getBulkGetCnt() { return bulkGetCnt; }
    public long getRefreshCnt() { return refreshCnt; }
    public long getInternalExceptionCnt() { return internalExceptionCnt; }
    public long getRefreshSubmitFailedCnt() { return refreshSubmitFailedCnt; }
    public long getRefreshHitCnt() { return refreshHitCnt; }
    public long getExpiredCnt() { return BaseCache.this.getExpiredCnt(); }
    public long getEvictedCnt() { return evictedCnt - virginForFetchLostCnt; }
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

  public class Mgmt implements CacheMXBean {

    @Override
    public int getSize() {
      return getInfo().getSize();
    }

    @Override
    public int getMaximumSize() {
      return maxSize;
    }

    @Override
    public long getUsageCnt() {
      return getInfo().getUsageCnt();
    }

    @Override
    public long getMissCnt() {
      return getInfo().getMissCnt();
    }

    @Override
    public long getNewEntryCnt() {
      return getInfo().getNewEntryCnt();
    }

    @Override
    public long getFetchCnt() {
      return getInfo().getFetchCnt();
    }

    @Override
    public long getRefreshCnt() {
      return getInfo().getRefreshCnt();
    }

    @Override
    public long getRefreshSubmitFailedCnt() {
      return getInfo().getRefreshSubmitFailedCnt();
    }

    @Override
    public long getRefreshHitCnt() {
      return getInfo().getRefreshHitCnt();
    }

    @Override
    public long getExpiredCnt() {
      return getInfo().getExpiredCnt();
    }

    @Override
    public long getEvictedCnt() {
      return getInfo().getEvictedCnt();
    }

    @Override
    public long getKeyMutationCnt() {
      return getInfo().getKeyMutationCnt();
    }

    @Override
    public long getPutCnt() {
      return getInfo().getPutCnt();
    }

    @Override
    public double getHitRate() {
      return getInfo().getDataHitRate();
    }

    @Override
    public int getHashQuality() {
      return getInfo().getHashQualityInteger();
    }

    @Override
    public int getHashCollisionCnt() {
      return getInfo().getCollisionCnt();
    }

    @Override
    public int getHashCollisionsSlotCnt() {
      return getInfo().getCollisionSlotCnt();
    }

    @Override
    public int getHashLongestCollisionSize() {
      return getInfo().getLongestCollisionSize();
    }

    @Override
    public double getMillisPerFetch() {
      return getInfo().getMillisPerFetch();
    }

    @Override
    public int getMemoryUsage() {
      return -1;
    }

    @Override
    public long getFetchMillis() {
      return getInfo().getFetchMillis();
    }

    @Override
    public String getIntegrityDescriptor() {
      return getInfo().getIntegrityDescriptor();
    }

    @Override
    public Date getCreatedTime() {
      return new Date(getInfo().getStarted());
    }

    @Override
    public Date getClearedTime() {
      return new Date(getInfo().getCleared());
    }

    @Override
    public Date getLastOperationTime() {
      return new Date(getInfo().getTouched());
    }

    @Override
    public Date getInfoCreatedTime() {
      return new Date(getInfo().getInfoCreated());
    }

    @Override
    public int getInfoCreatedDetlaMillis() {
      return getInfo().getInfoCreationDeltaMs();
    }

    @Override
    public String getImplementation() {
      return getInfo().getImplementation();
    }

    public void clear() {
      BaseCache.this.clear();
    }

    @Override
    public void clearTimingStatistics() {
      BaseCache.this.clearTimingStatistics();
    }

    @Override
    public int getAlert() {
      return getInfo().getHealth();
    }

    @Override
    public String getExtraStatistics() {
      return getInfo().getExtraStatistics();
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

  /**
   * Fast hash table implementation. Why we don't want to use the Java HashMap implementation:
   *
   * We need to move the entries back and forth between different hashes.
   * If we do this, each time an entry object needs to be allocated and deallocated.
   *
   * Second, we need to do countermeasures, if a key changes its value and hashcode
   * during its use in the cache. Although it is a user error and therefore not
   * our primary concern, the effects can be very curious and hard to find.
   *
   * Third, we want to have use the hash partly unsynchronized, so we should know the
   * implementation details.
   *
   * Fourth, we can leave out "details" of a general hash table, like shrinking. Our hash table
   * only expands.
   *
   * Access needs to be public, since we want to access the hash primitives from classes
   * in another package.
   */
  public static class Hash<E extends Entry> {

    public int size = 0;
    public int maxFill = 0;
    private int suppressExpandCount;

    public static int index(Entry[] _hashTable, int _hashCode) {
      return _hashCode & (_hashTable.length - 1);
    }


    public static <E extends Entry> E lookup(E[] _hashTable, Object key, int _hashCode) {
      int i = index(_hashTable, _hashCode);
      E e = _hashTable[i];
      while (e != null) {
        if (e.hashCode == _hashCode &&
            key.equals(e.key)) {
          return e;
        }
        e = (E) e.another;
      }
      return null;
    }

    public static boolean contains(Entry[] _hashTable, Object key, int _hashCode) {
      int i = index(_hashTable, _hashCode);
      Entry e = _hashTable[i];
      while (e != null) {
        if (e.hashCode == _hashCode &&
            key.equals(e.key)) {
          return true;
        }
        e = e.another;
      }
      return false;
    }


    public static void insertWoExpand(Entry[] _hashTable, Entry e) {
      int i = index(_hashTable, e.hashCode);
      e.another = _hashTable[i];
      _hashTable[i] = e;
    }

    private static void rehash(Entry[] a1, Entry[] a2) {
      for (Entry e : a1) {
        while (e != null) {
          Entry _next = e.another;
          insertWoExpand(a2, e);
          e = _next;
        }
      }
    }

    private static <E extends Entry> E[] expandHash(E[] _hashTable) {
      E[] a2 = (E[]) Array.newInstance(
              _hashTable.getClass().getComponentType(),
              _hashTable.length * 2);
      rehash(_hashTable, a2);
      return a2;
    }

    public static void calcHashCollisionInfo(CollisionInfo inf, Entry[] _hashTable) {
      for (Entry e : _hashTable) {
        if (e != null) {
          e = e.another;
          if (e != null) {
            inf.collisionSlotCnt++;
            int _size = 1;
            while (e != null) {
              inf.collisionCnt++;
              e = e.another;
              _size++;
            }
            if (inf.longestCollisionSize < _size) {
              inf.longestCollisionSize = _size;
            }
          }
        }
      }

    }

    /**
     * Count the entries in the hash table, by scanning through the hash table.
     * This is used for integrity checks.
     */
    public static int calcEntryCount(Entry[] _hashTable) {
      int _entryCount = 0;
      for (Entry e : _hashTable) {
        while (e != null) {
          _entryCount++;
          e = e.another;
        }
      }
      return _entryCount;
    }

    public boolean remove(Entry[] _hashTable, Entry _entry) {
      int i = index(_hashTable, _entry.hashCode);
      Entry e = _hashTable[i];
      if (e == _entry) {
        _hashTable[i] = e.another;
        size--;
        return true;
      }
      while (e != null) {
        Entry _another = e.another;
        if (_another == _entry) {
          e.another = _another.another;
          size--;
          return true;
        }
        e = _another;
      }
      return false;
    }

    /**
     * Remove entry from the hash. We never shrink the hash table, so
     * the array keeps identical. After this remove operatoin the entry
     * object may be inserted in another hash.
     */
    public E remove(E[] _hashTable, Object key, int hc) {
      int i = index(_hashTable, hc);
      Entry e = _hashTable[i];
      if (e == null) {
        return null;
      }
      if (e.hashCode == hc && key.equals(e.key)) {
        _hashTable[i] = (E) e.another;
        size--;
        return (E) e;
      }
      Entry _another = e.another;
      while (_another != null) {
        if (_another.hashCode == hc && key.equals(_another.key)) {
          e.another = _another.another;
          size--;
          if (suppressExpandCount > 0) {
            _another = _another.clone();
          }
          return (E) _another;
        }
        e = _another;
        _another = _another.another;
      }
      return null;
    }


    public E[] insert(E[] _hashTable, Entry _entry) {
      size++;
      insertWoExpand(_hashTable, _entry);
      synchronized (this) {
        if (size >= maxFill && suppressExpandCount == 0) {
          maxFill = maxFill * 2;
          return expandHash(_hashTable);
        }
        return _hashTable;
      }
    }

    /**
     * Usage/reference counter for iterations to suspend expand
     * until the iteration finished. This is needed for correctness
     * of the iteration, if an expand is done during the iteration
     * process, the iterations returns duplicate entries or not
     * all entries.
     *
     * <p>Failing to operate the increment/decrement in balance will
     * mean that hash table expands are blocked forever, which is a
     * serious error condition. Typical problems arise by thrown
     * exceptions during an iteration.
     */
    public synchronized void incrementSuppressExpandCount() {
      suppressExpandCount++;
    }

    public synchronized void decrementSuppressExpandCount() {
      suppressExpandCount--;
    }

    /**
     * The cache with this hash was cleared and the hash table is no longer
     * in used. Signal to iterations to abort.
     */
    public void cleared() {
      if (size >= 0) {
        size = -1;
      }
    }

    /**
     * Cache was closed. Inform operations/iterators on the hash.
     */
    public void close() { size = -2; }

    /**
     * Operations should terminate
     */
    public boolean isCleared() { return size == -1; }

    /**
     * Operations should terminate and throw a {@link org.cache2k.impl.CacheClosedException}
     */
    public boolean isClosed() { return size == -2; }

    public boolean shouldAbort() { return size < 0; }

    public E[] init(Class<E> _entryType) {
      size = 0;
      maxFill = TUNABLE.initialHashSize * TUNABLE.hashLoadPercent / 100;
      return (E[]) Array.newInstance(_entryType, TUNABLE.initialHashSize);
    }

  }

  protected class MyTimerTask extends java.util.TimerTask {
    E entry;

    public void run() {
      timerEvent(entry);
    }
  }

  static class InitialValueInEntryNeverReturned { }

  final static InitialValueInEntryNeverReturned INITIAL_VALUE = new InitialValueInEntryNeverReturned();

  public static class Entry<E extends Entry, K, T>
    implements MutableCacheEntry<K,T>, StorageEntry, Cloneable {

    static final int FETCHED_STATE = 10;
    static final int REMOVED_STATE = 12;
    static final int REFRESH_STATE = 11;
    static final int REPUT_STATE = 13;

    /** Storage was checked, no data available */
    static final int STORAGE_MISS = 5;

    static final int EXPIRED_STATE = 4;

    /** Logically the same as immediatly expired */
    static final int FETCH_NEXT_TIME_STATE = 3;

    static private final int REMOVED_NON_VALID_STATE = 2;
    static private final int VIRGIN_FOR_FETCH_STATE = 1;
    static final int VIRGIN_STATE = 0;
    static final int EXPIRY_TIME_MIN = 20;

    public BaseCache.MyTimerTask task;

    /**
     * Time the entry was last updated by put or by fetching it from the cache source.
     * The time is the time in millis times 2. A set bit 1 means the entry is fetched from
     * the storage and not modified since then.
     */
    private long fetchedTime;

    /**
     * Contains the next time a refresh has to occur. Low values have a special meaning, see defined constants.
     * Negative values means the refresh time was expired, and we need to check the time.
     */
    public volatile long nextRefreshTime;

    public K key;

    public volatile T value = (T) INITIAL_VALUE;

    /**
     * Hash implementation: the calculated, modified hash code, retrieved from the key when the entry is
     * inserted in the cache
     *
     * @see #modifiedHash(int)
     */
    public int hashCode;

    /**
     * Hash implementation: Link to another entry in the same hash table slot when the hash code collides.
     */
    public Entry<E, K, T> another;

    /** Lru list: pointer to next element or list head */
    public E next;
    /** Lru list: pointer to previous element or list head */
    public E prev;

    public void setLastModification(long t) {
      fetchedTime = t << 1;
    }

    public boolean isDirty() {
      return (fetchedTime & 1) == 0;
    }

    public void setLastModificationFromStorage(long t) {
      fetchedTime = t << 1 | 1;
    }

    /** Reset next as a marker for {@link #isRemovedFromReplacementList()} */
    public final void removedFromList() {
      next = null;
    }

    /** Check that this entry is removed from the list, may be used in assertions. */
    public boolean isRemovedFromReplacementList() {
      return next == null;
    }

    public E shortCircuit() {
      return next = prev = (E) this;
    }

    public final boolean isVirgin() {
      return
        nextRefreshTime == VIRGIN_STATE ||
        nextRefreshTime == VIRGIN_FOR_FETCH_STATE;
    }

    public final boolean isVirginForFetchState() {
      boolean b = nextRefreshTime == VIRGIN_FOR_FETCH_STATE;
      return b;
    }

    public final void setVirginForFetchState() {
      nextRefreshTime = VIRGIN_FOR_FETCH_STATE;
    }

    /**
     * Entry was removed before, however the entry contains still valid data,
     * so {@link #isDataValidState()} is also true.
     * This state is state may not be terminal. When an parallel get is going on
     * it will change to just isFetched or isFetchNextTime even if it was
     * removed. Use {@link #isRemovedFromReplacementList()} as a flag whether
     * it is really removed.
     */
    public final boolean isRemovedState() {
      boolean b = nextRefreshTime == REMOVED_STATE;
      return b;
    }

    public final boolean isFetchNextTimeState() {
      return nextRefreshTime == FETCH_NEXT_TIME_STATE;
    }

    /**
     * The entry value was fetched and is valid, which means it can be
     * returned by the cache. If a valid an entry with {@link #isDataValidState()}
     * true gets removed from the cache the data is still valid. This is
     * because a concurrent get needs to return the data. There is also
     * the chance that an entry is removed by eviction, or is never inserted
     * to the cache, before the get returns it.
     */
    public final boolean isDataValidState() {
      return nextRefreshTime >= FETCHED_STATE;
    }

    /**
     * Returns true if the entry has a valid value and is fresh / not expired.
     */
    public final boolean hasFreshData() {
      if (isDataValidState()) {
        return true;
      }
      if (needsTimeCheck()) {
        long t = System.currentTimeMillis();
        return t < -nextRefreshTime;
      }
      return false;
    }

    public void setFetchedState() {
      nextRefreshTime = FETCHED_STATE;
    }

    public void setStorageMiss() {
      nextRefreshTime = STORAGE_MISS;
    }

    public boolean isStorageMiss() {
      return nextRefreshTime == STORAGE_MISS;
    }

    /** Entry is kept in the cache but has expired */
    public void setExpiredState() {
      nextRefreshTime = EXPIRED_STATE;
    }

    /**
     * The entry expired, but still in the cache. This may happen if
     * {@link org.cache2k.impl.BaseCache#hasKeepAfterExpired()} is true.
     */
    public boolean isExpiredState() {
      return nextRefreshTime == EXPIRED_STATE;
    }

    public void setRemovedState() {
      nextRefreshTime = REMOVED_STATE;
    }

    public void setRemovedNonValidState() {
      nextRefreshTime = REMOVED_NON_VALID_STATE;
    }

    public boolean isRemovedNonValidState() {
      return nextRefreshTime == REMOVED_NON_VALID_STATE;
    }

    public void setFetchNextTimeState() {
      nextRefreshTime = FETCH_NEXT_TIME_STATE;
    }

    public void setGettingRefresh() {
      nextRefreshTime = REFRESH_STATE;
    }

    public boolean isGettingRefresh() {
      return nextRefreshTime == REFRESH_STATE;
    }

    public boolean isBeeingReput() {
      return nextRefreshTime == REPUT_STATE;
    }

    public void setReputState() {
      nextRefreshTime = REPUT_STATE;
    }

    public boolean needsTimeCheck() {
      return nextRefreshTime < 0;
    }

    public boolean hasException() {
      return value instanceof ExceptionWrapper;
    }

    public Throwable getException() {
      if (value instanceof ExceptionWrapper) {
        return ((ExceptionWrapper) value).getException();
      }
      return null;
    }

    public void setException(Throwable exception) {
      value = (T) new ExceptionWrapper(exception);
    }

    public T getValue() {
      if (value instanceof ExceptionWrapper) { return null; }
      return value;
    }

    public boolean hasExpiryTime() {
      return nextRefreshTime > EXPIRY_TIME_MIN;
    }

    @Override
    public void setValue(T v) {
      value = v;
    }

    @Override
    public K getKey() {
      return key;
    }

    @Override
    public long getLastModification() {
      return fetchedTime >> 1;
    }

    /**
     * Expiry time or 0.
     */
    public long getExpiryTime() {
      if (hasExpiryTime()) {
        return nextRefreshTime;
      }
      return 0;
    }

    /**
     * Used for the storage interface.
     *
     * @see StorageEntry
     */
    @Override
    public Object getValueOrException() {
      return value;
    }

    /**
     * Used for the storage interface.
     *
     * @see StorageEntry
     */
    @Override
    public long getCreatedOrUpdated() {
      return getLastModification();
    }

    /**
     * Used for the storage interface.
     *
     * @see StorageEntry
     * @deprectated Always returns 0, only to fulfill the {@link StorageEntry} interface
     */
    @Override
    public long getLastUsed() {
      return 0;
    }

    /**
     * Used for the storage interface.
     *
     * @see StorageEntry
     * @deprectated Always returns 0, only to fulfill the {@link StorageEntry} interface
     */
    @Override
    public long getMaxIdleTime() {
      return 0;
    }


    @Override
    public String toString() {
      return "Entry{" +
        "fetchedTime=" + fetchedTime +
        ", nextRefreshTime=" + nextRefreshTime +
        ", key=" + key +
        ", mHC=" + hashCode +
        ", value=" + value +
        ", dirty=" + isDirty() +
        '}';
    }

    /**
     * Cache entries always have the object identity as equals method.
     */
    @Override
    public final boolean equals(Object obj) {
      return this == obj;
    }

    /**
     * The entry clone operation always does a shallow copy.
     */
    public final E clone() {
      try {
        Object o = super.clone();
        return (E) o;
      } catch (CloneNotSupportedException e) {
        throw new CacheInternalError("never happens");
      }
    }

  }

  public static class Tunable extends TunableConstants {

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


  }

}
