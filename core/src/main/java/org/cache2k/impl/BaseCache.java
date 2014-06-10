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
import org.cache2k.ExperimentalBulkCacheSource;
import org.cache2k.Cache;
import org.cache2k.CacheConfig;
import org.cache2k.MutableCacheEntry;
import org.cache2k.StorageConfiguration;
import org.cache2k.jmx.CacheMXBean;
import org.cache2k.CacheSourceWithMetaInfo;
import org.cache2k.CacheSource;
import org.cache2k.RefreshController;
import org.cache2k.PropagatedCacheException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cache2k.storage.ImageFileStorage;
import org.cache2k.storage.CacheStorage;
import org.cache2k.storage.CacheStorageContext;
import org.cache2k.storage.MarshallerFactory;
import org.cache2k.storage.Marshallers;
import org.cache2k.storage.StorageEntry;

import java.io.IOException;
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
import java.util.TimerTask;

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
  implements Cache<K, T>, CanCheckIntegrity {

  static int HASH_INITIAL_SIZE = 64;
  static int HASH_LOAD_PERCENT = 64;


  static final Random SEED_RANDOM = new Random(new SecureRandom().nextLong());
  static int cacheCnt = 0;

  protected int hashSeed = SEED_RANDOM.nextInt();

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

  private Log lazyLog = null;
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

  /** Wait for this thread before shutdown */
  protected Thread clearThread;

  protected boolean shutdownInitiated = false;

  /**
   * Flag during operation that indicates, that the cache is full and eviction needs
   * to be done. Eviction is only allowed to happen after an entry is fetched, so
   * at the end of an cache operation that increased the entry count we check whether
   * something needs to be evicted.
   */
  protected boolean evictionNeeded = false;

  protected StorageAdapter storage;

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
   * Lazy construct a log when needed, normally a cache logs nothing.
   */
  protected Log getLog() {
    if (lazyLog == null) {
      lazyLog =
        LogFactory.getLog(Cache.class.getName() + '.' + name);
    }
    return lazyLog;
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
    setExpirySeconds(c.getExpirySeconds());
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
      storage = new PassingStorageAdapter(c, _stores.get(0));
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
      initializeMemoryCache();
      initTimer();
      if (refreshPool != null && timer == null) {
        if (maxLinger == 0) {
          getLog().warn("Background refresh is enabled, but elements are fetched always. Disable background refresh.");
        } else {
          getLog().warn("Background refresh is enabled, but elements are eternal. Disable background refresh.");
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
    synchronized (lock) {
      if (shutdownInitiated) {
        throw new IllegalStateException("cache closed");
      }
      if (touchedTime == 0 ||
        (touchedTime == clearedTime && getSize() == 0)) {
        storage.clearPrepare();
        storage.clearProceed();
        initializeMemoryCache();
        clearedTime = System.currentTimeMillis();
        touchedTime = clearedTime;
        return;
      }
    }
    StorageClearTask t = new StorageClearTask();
    t.mainHashCopy = mainHash;
    t.refreshHashCopy = refreshHash;
    t.storage = storage;
    while (true) {
      try {
        Thread.sleep(3);
      } catch (InterruptedException e) {
      }
      synchronized (lock) {
        if (shutdownInitiated) {
          throw new IllegalStateException("cache closed");
        }
        boolean f = storage.clearPrepare();
        if (!f) {
          continue;
        }
        clearLocalCache();
        Thread th = clearThread =  new Thread(t);
        th.setName("cache2k-clear:" + getName() + ":" + th.getId());
        th.setDaemon(true);
        th.start();
        break;
      }
    }
  }

  class StorageClearTask implements Runnable {

    E[] mainHashCopy;
    E[] refreshHashCopy;
    StorageAdapter storage;

    @Override
    public void run() {
      waitForEntryOperations();
      storage.clearProceed();
      clearThread = null;
    }

    private void waitForEntryOperations() {
      Iterator<Entry> it =
        new HashEntryIterator(mainHashCopy, refreshHashCopy);
      while (it.hasNext()) {
        Entry e = it.next();
        synchronized (e) { }
      }
    }
  }

  public final void clear() {
    if (storage != null) {
      processClearWithStorage();
      return;
    }
    clearLocalCache();
  }

  private void clearLocalCache() {
    synchronized (lock) {
      if (shutdownInitiated) {
        throw new IllegalStateException("cache closed");
      }
      Iterator<Entry> it = iterateAllLocalEntries();
      while (it.hasNext()) {
        Entry e = it.next();
        e.removedFromList();
        cancelExpiryTimer(e);
      }
      removeCnt += getSize();
      initializeMemoryCache();
      clearedTime = System.currentTimeMillis();
      touchedTime = clearedTime;
    }
  }

  protected void initializeMemoryCache() {
    mainHashCtrl = new Hash<>();
    refreshHashCtrl = new Hash<>();
    txHashCtrl = new Hash<>();
    mainHash = mainHashCtrl.init((Class<E>) newEntry().getClass());
    refreshHash = refreshHashCtrl.init((Class<E>) newEntry().getClass());
    txHash = txHashCtrl.init((Class<E>) newEntry().getClass());
    if (startedTime == 0) { startedTime = clearedTime; }
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
      shutdownInitiated = true;
    }
    Thread th = clearThread;
    if (th != null) {
      try {
        th.join();
      } catch (InterruptedException e) {
      }
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

  public CacheEntry<K, T> getEntry(K key) {
    return getEntryInternal(key);
  }

  protected Entry<E, K, T> getEntryInternal(K key) {
    for (;;) {
      E e = lookupOrNewEntrySynchronized(key);
      if (e.isDataValid()) {
        return e;
      }
      if (e.needsTimeCheck()) {
        long t = System.currentTimeMillis();
        if (t < -e.nextRefreshTime) {
          return e;
        }
      }
      synchronized (e) {
        if (!e.isDataValid()) {
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

  protected final void evictEventually() {
    while (evictionNeeded) {
      E e;
      synchronized (lock) {
        if (getSize() <= maxSize) {
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
          evictionNeeded = getSize() > maxSize;
        }
        if (storage != null && e.isDataValid()) {
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
        fetchWithStorage(e, false);
      }
    }
    evictEventually();
    if (!e.isDataValid()) {
      if (e.needsTimeCheck()) {
        long t = System.currentTimeMillis();
        if (t < -e.nextRefreshTime) {
          return returnValue(e);
        }
      }
    } else {
      return returnValue(e);
    }
    peekMissCnt++;
    return null;
  }


  @Override
  public void put(K key, T value) {
    E e;
    synchronized (lock) {
      putCnt++;
      int hc = modifiedHash(key.hashCode());
      e = lookupEntry(key, hc);
      if (e == null) {
        e = newEntry(key, hc);
        putNewEntryCnt++;
      } else {
        if (e.isDataValid()) {
          e.setReputState();
        }
      }
    }
    long t = System.currentTimeMillis();
    synchronized (e) {
      insertOnPut(e, value, t, t);
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
    if (getSize() >= maxSize) {
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
    if (e.isDataValid()) {
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
      final int _SUPPRESS_COUNT = 777;
      if (keyMutationCount % _SUPPRESS_COUNT ==  0) {
        if (keyMutationCount > 0) {
          getLog().fatal("Key mismatch! " + (_SUPPRESS_COUNT - 1) + " more errors suppressed");
        }
        getLog().fatal("Key mismatch! Key hashcode changed! keyClass=" + e.key.getClass().getName());
        String s;
        try {
          s = e.key.toString();
          if (s != null) {
            getLog().fatal("Key mismatch! key.toString(): " + s);
          }
        } catch (Throwable t) {
          getLog().fatal("Key mismatch! key.toString() threw exception", t);
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
                } catch (Exception ex) {
                  synchronized (lock) {
                    refreshSubmitFailedCnt++;
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

  public Iterator<Entry> iterateAllLocalEntries() {
    return new HashEntryIterator(mainHash, refreshHash);
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
      } catch (Exception ex) {
        getLog().info("bulk get failed", ex);
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
      if (!e.isDataValid()) {
        synchronized (e) {
          if (!e.isDataValid()) {
            r[i] = null;
            _fetched.set(i, false);
            long t2 = fetchBulkLoop(ea, k, r, _fetched, s, i - 1, end, t);
            if (!e.isDataValid()) {
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
          if (!e.isDataValid()) {
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
      if (!e.isDataValid()) {
        synchronized (e) {
          if (!e.isDataValid()) {
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
        if (_entry.isDataValid()) {
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

  protected final int getHashEntryCount() {
    return Hash.calcEntryCount(mainHash) + Hash.calcEntryCount(refreshHash);
  }

  public int getSize() {
    return mainHashCtrl.size + refreshHashCtrl.size;
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
        .checkEquals("newEntryCnt == getSize() + evictedCnt + expiredRemoveCnt + removeCnt", newEntryCnt, getSize() + evictedCnt + expiredRemoveCnt + removeCnt)
        .checkEquals("newEntryCnt == getSize() + evictedCnt + getExpiredCnt() - expiredKeptCnt + removeCnt", newEntryCnt, getSize() + evictedCnt + getExpiredCnt() - expiredKeptCnt + removeCnt)
        .checkEquals("mainHashCtrl.size == Hash.calcEntryCount(mainHash)", mainHashCtrl.size, Hash.calcEntryCount(mainHash))
        .checkEquals("refreshHashCtrl.size == Hash.calcEntryCount(refreshHash)", refreshHashCtrl.size, Hash.calcEntryCount(refreshHash))
        .check("!!evictionNeeded | (getSize() <= maxSize)", !!evictionNeeded | (getSize() <= maxSize))
        .checkLessOrEquals("bulkKeysCurrentlyRetrieved.size() <= getSize()",
          bulkKeysCurrentlyRetrieved == null ? 0 : bulkKeysCurrentlyRetrieved.size(), getSize());
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
        + "keyMutationCnt=" + fo.getKeyMutationCnt() + ", "
        + "hitRate=" + fo.getDataHitString() + ", "
        + "collisionCnt=" + fo.getCollisionCnt() + ", "
        + "collisionSlotCnt=" + fo.getCollisionSlotCnt() + ", "
        + "longestCollisionSize=" + fo.getLongestCollisionSize() + ", "
        + "hashQuality=" + fo.getHashQualityInteger() + ", "
        + "msecs/fetch=" + (fo.getMillisPerFetch() >= 0 ? fo.getMillisPerFetch() : "-")  + ", "
        + "created=" + (new java.sql.Timestamp(fo.getStarted())) + ", "
        + "cleared=" + (new java.sql.Timestamp(fo.getCleared())) + ", "
        + "touched=" + (new java.sql.Timestamp(fo.getTouched())) + ", "
        + "infoCreated=" + (new java.sql.Timestamp(fo.getInfoCreated())) + ", "
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

    int size = BaseCache.this.getSize();
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
      if (integrityState.getStateFlags() > 0 ||
          getHashQualityInteger() < 5) {
        return 2;
      }
      if (getHashQualityInteger() < 30 ||
        getKeyMutationCnt() > 0) {
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


    public static void insertWoExpand(Entry[] _hashTable, Entry e) {
      int i = index(_hashTable, e.hashCode);
      e.another = _hashTable[i];
      _hashTable[i] = e;
    }

    public static void rehash(Entry[] a1, Entry[] a2) {
      for (Entry e : a1) {
        while (e != null) {
          Entry _next = e.another;
          insertWoExpand(a2, e);
          e = _next;
        }
      }
    }

    public static <E extends Entry> E[] expandHash(E[] _hashTable) {
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
     * the array keeps identical.
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
      if (size >= maxFill) {
        maxFill = maxFill * 2;
        return expandHash(_hashTable);
      }
      return _hashTable;
    }

    public E[] init(Class<E> _entryType) {
      size = 0;
      maxFill = HASH_INITIAL_SIZE * HASH_LOAD_PERCENT / 100;
      return (E[]) Array.newInstance(_entryType, HASH_INITIAL_SIZE);
    }

  }

  protected class MyTimerTask extends TimerTask {
    E entry;

    public void run() {
      timerEvent(entry);
    }
  }

  static class InitialValueInEntryNeverReturned { }

  final static InitialValueInEntryNeverReturned INITIAL_VALUE = new InitialValueInEntryNeverReturned();

  public static class Entry<E extends Entry, K, T>
    implements MutableCacheEntry<K,T>, StorageEntry {

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

    public TimerTask task;

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
     * so {@link #isDataValid()} is also true.
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
     * returned by the cache. If a valid an entry with {@link #isDataValid()}
     * true gets removed from the cache the data is still valid. This is
     * because a concurrent get needs to return the data. There is also
     * the chance that an entry is removed by eviction, or is never inserted
     * to the cache, before the get returns it.
     */
    public final boolean isDataValid() {
      return nextRefreshTime >= FETCHED_STATE;
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
        ", value=" + value +
        '}';
    }

    /**
     * Cache entries always have the object identity as equals method.
     */
    @Override
    public final boolean equals(Object obj) {
      return this == obj;
    }

  }

  static abstract class StorageAdapter {

    public abstract void open();
    public abstract void shutdown();
    public abstract boolean clearPrepare();
    public abstract void clearProceed();
    public abstract void put(Entry e);
    public abstract StorageEntry get(Object key);
    public abstract void remove(Object key);
    public abstract void evict(Entry e);
    public abstract void expire(Entry e);

  }

  class StorageContext implements CacheStorageContext {

    Class<?> keyType;
    Class<?> valueType;

    @Override
    public String getManagerName() {
      return manager.getName();
    }

    @Override
    public String getCacheName() {
      return getName();
    }

    @Override
    public Class<?> getKeyType() {
      return keyType;
    }

    @Override
    public Class<?> getValueType() {
      return valueType;
    }

    @Override
    public MarshallerFactory getMarshallerFactory() {
      return Marshallers.getInstance();
    }

    @Override
    public void requestMaintenanceCall(int _intervalMillis) {
    }

    @Override
    public void notifyEvicted(StorageEntry e) {
    }

    @Override
    public void notifyExpired(StorageEntry e) {
    }
  }

  class PassingStorageAdapter extends StorageAdapter {

    CacheStorage storage;
    CacheStorage copyForClearing;
    boolean passivation = false;
    long storageErrorCount = 0;
    Set<Object> deletedKeys = null;
    StorageContext context;
    StorageConfiguration config;

    public PassingStorageAdapter(CacheConfig _cacheConfig,
                                 StorageConfiguration _storageConfig) {
      context = new StorageContext();
      context.keyType = _cacheConfig.getKeyType();
      context.valueType = _cacheConfig.getValueType();
      config = _storageConfig;
    }

    public void open() {

      try {
        ImageFileStorage s = new ImageFileStorage();
        s.open(context, config);
        storage = s;
        if (config.isPassivation()) {
          deletedKeys = new HashSet<>();
          passivation = true;
        }
      } catch (Exception ex) {
        getLog().warn("error initializing storage, running in-memory", ex);
      }
    }

    /**
     * Store entry on cache put. Entry must be locked, since we use the
     * entry directly for handing it over to the storage, it is not
     * allowed to change. If storeAlways is switched on the entry will
     * be in memory and in the storage after this operation.
     */
    public void put(Entry e) {
      if (deletedKeys != null) {
        synchronized (deletedKeys) {
          deletedKeys.remove(e.getKey());
        }
        return;
      }
      try {
         storage.put(e);
      } catch (Exception ex) {
        storageErrorCount++;
        throw new CacheStorageException("cache put", ex);
      }
    }

    public StorageEntry get(Object k) {
      if (deletedKeys != null) {
        synchronized (deletedKeys) {
          if (deletedKeys.contains(k)) {
            return null;
          }
        }
      }
      try {
        StorageEntry e = storage.get(k);
        return e;
      } catch (Exception ex) {
        storageErrorCount++;
        throw new CacheStorageException("cache get", ex);
      }
    }

    public void evict(Entry e) {
      if (passivation) {
        putIfDirty(e);
      }
    }

    /**
     * Entry is evicted from memory cache either because of an expiry or an
     * eviction.
     */
    public void expire(Entry e) {
      remove(e.getKey());
    }

    private void putIfDirty(Entry e) {
      try {
        if (e.isDirty()) {
          storage.put(e);
        }
      } catch (Exception ex) {
        storageErrorCount++;
        throw new CacheStorageException("cache put", ex);
      }
    }

    public void remove(Object key) {
      if (deletedKeys != null) {
        synchronized (deletedKeys) {
          deletedKeys.remove(key);
        }
        return;
      }
      try {
        storage.remove(key);
      } catch (Exception ex) {
        storageErrorCount++;
        throw new CacheStorageException("cache remove", ex);
      }
    }

    public void shutdown() {
      try {
        if (passivation) {
          Iterator<Entry> it = iterateAllLocalEntries();
          while (it.hasNext()) {
            Entry e = it.next();
            putIfDirty(e);
          }
          if (deletedKeys != null) {
            for (Object k : deletedKeys) {
              storage.remove(k);
            }
          }
        }
        storage.close();
      } catch (IOException | ClassNotFoundException ex) {
        ex.printStackTrace();
        storageErrorCount++;
      }

    }

    public boolean clearPrepare() {
      if (copyForClearing != null) {
        return false;
      }
      copyForClearing = storage;
      storage = new CacheStorageBuffer();
      return true;
    }

    public void clearProceed() {
      try {
        copyForClearing.clear();
        ((CacheStorageBuffer) storage).transfer(copyForClearing);
      } catch (IOException | ClassNotFoundException ex) {
        ex.printStackTrace();
        storageErrorCount++;
      } finally {
        synchronized (lock) {
          storage = copyForClearing;
          copyForClearing = null;
        }
      }
    }

    /**
     * Calculates the cache size, depending on the persistence configuration
     */
    public int getTotalEntryCount() {
      if (!passivation) {
        return storage.getEntryCount();
      }
      int size = storage.getEntryCount();
      Iterator<Entry> it = iterateAllLocalEntries();
      try {
        while (it.hasNext()) {
          Entry e = it.next();
          if (!storage.contains(e.getKey())) {
            size++;
          }
        }
      } catch (IOException ex) {
        ex.printStackTrace();
        storageErrorCount++;
      }
      return size;
    }

  }

}
