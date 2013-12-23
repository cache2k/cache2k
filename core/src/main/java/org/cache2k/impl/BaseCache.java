package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2013 headissue GmbH, Munich
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

import org.cache2k.ExperimentalBulkCacheSource;
import org.cache2k.Cache;
import org.cache2k.CacheConfig;
import org.cache2k.jmx.CacheMXBean;
import org.cache2k.CacheSourceWithMetaInfo;
import org.cache2k.CacheSource;
import org.cache2k.RefreshController;
import org.cache2k.PropagatedCacheException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.Array;
import java.security.SecureRandom;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Date;
import java.util.HashMap;
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
 * For a (in-memory) cache we need three things: a fast hash table implementation, a
 * LRU list (a simple double linked list), and a fast timer.
 * The variants implement different eviction strategies.
 *
 * @author Jens Wilke; created: 2013-07-09
 */
@SuppressWarnings("unchecked")
public abstract class BaseCache<E extends BaseCache.Entry, K, T>
  implements Cache<K, T>, CanCheckIntegrity {

  static final Random SEED_RANDOM = new SecureRandom();
  static int cacheCnt = 0;

  protected int hashSeed = SEED_RANDOM.nextInt();


  /** Maximum amount of elements in cache */
  protected int maxSize = 5000;

  /** Time in milliseconds we keep an element */
  protected int maxLinger = 10 * 60 * 1000;
  protected boolean keepAfterExpired;
  protected String name;
  protected CacheManagerImpl manager;
  protected CacheSourceWithMetaInfo<K, T> source;
  /** Statistics */

  protected RefreshController<T> refreshController;

  protected Info info;
  protected long clearedTime;
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
  protected int maximumBulkFetchSize = 100;

  protected final Object lock = this;

  private Log lazyLog = null;
  protected CacheRefreshThreadPool refreshPool;

  protected Hash<E> mainHashCtrl;
  protected E[] mainHash;

  protected Hash<E> refreshHashCtrl;
  protected E[] refreshHash;

  protected Hash<E> txHashCtrl;
  protected E[] txHash;


  protected ExperimentalBulkCacheSource<K, T> experimentalBulkCacheSource;
  protected HashSet<K> bulkKeysCurrentlyRetrieved;

  protected Timer timer;

  /**
   * Lazy construct a log when needed, normally a cache logs nothing.
   */
  protected Log getLog() {
    if (lazyLog == null) {
      return lazyLog =
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
    if (c.isBackgroundRefresh()) {
      refreshPool = CacheRefreshThreadPool.getInstance();
    }
    setExpirySeconds(c.getExpirySeconds());
    keepAfterExpired = c.isKeepDataAfterExpired();
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
    if (s == 0) {
      getLog().warn("Expiry time set to 0, which means no caching!");
    }
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
    if (name == null) {
      name = "" + cacheCnt++;
    }
    if (maxSize == 0) {
      throw new IllegalArgumentException("maxElements must be >0");
    }
    clear();
    initTimer();
    if (refreshPool != null && timer == null) {
      getLog().warn("background refresh is enabled, but elements are eternal");
      refreshPool.destroy();
      refreshPool = null;
    }
  }

  /** add or remove a timer */
  private synchronized void initTimer() {
    if (timer == null && maxLinger > 0) {
      timer = new Timer(name, true);
      return;
    }
    if (timer != null && maxLinger <= 0) {
      timer.cancel();
      timer = null;
    }
  }

  public void clear() {
    synchronized (lock) {
      if (mainHashCtrl != null) {
        removeCnt += getSize();
      }
      mainHashCtrl = new Hash<>();
      refreshHashCtrl = new Hash<>();
      txHashCtrl = new Hash<>();
      mainHash = mainHashCtrl.init((Class<E>) newEntry().getClass());
      refreshHash = refreshHashCtrl.init((Class<E>) newEntry().getClass());
      txHash = txHashCtrl.init((Class<E>) newEntry().getClass());
      clearedTime = System.currentTimeMillis();
      touchedTime = clearedTime;
      if (startedTime == 0) { startedTime = clearedTime; }
      if (timer != null) {
        timer.cancel();
        timer = null;
        initTimer();
      }
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


  protected void removeFromList(final E e) {
    e.prev.next = e.next;
    e.next.prev = e.prev;
    e.removedFromList();
  }

  protected final void insertInList(final Entry _head, final Entry e) {
    e.prev = _head;
    e.next = _head.next;
    e.next.prev = e;
    _head.next = e;
  }

  protected final int getListEntryCount(final Entry _head) {
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

  protected final void moveToFront(final E _head, final E e) {
    removeFromList(e);
    insertInList(_head, e);
  }


  protected final E insertIntoTailCyclicList(final E _head, final E e) {
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
   * Find entry to evict and call {@link #removeEntryFromCache(BaseCache.Entry)}.
   * Also remove it from the replacement structures.
   * precondition met: size > 0, evict was increased. Either this or evictSomeEntries()
   * needs to be implemented.
   */
  protected abstract void evictEntry();

  /**
   * Evict at least one entry. Increase evictedCnt accordingly.
   */
  protected void evictSomeEntries() {
    evictEntry();
    evictedCnt++;
  }

  /**
   * Precondition: Replacement list entry count == getSize()
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
    E e = lookupOrNewEntrySynchronized(key);
    if (!e.isFetched()) {
      synchronized (e) {
        if (!e.isFetched()) {
          fetch(e);
        }
      }
    }
    return returnValue(e);
  }

  @Override
  public T peek(K key) {
    int hc = modifiedHash(key.hashCode());
    do {
      Entry e = lookupEntryUnsynchronized(key, hc);
      if (e == null) {
        synchronized (lock) {
          e = lookupEntry(key, hc);
          if (e == null) {
            e = Hash.lookup(txHash, key, hc);
            if (e != null) {
              synchronized (e) {
                continue;
              }
            }
          }
        }
      }
      if (e != null && e.isFetched()) {
        return returnValue(e);
      }
      synchronized (lock) {
        peekMissCnt++;
      }
      return null;
    } while (true);
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
        e.setReputState();
      }
    }
    long t = System.currentTimeMillis();
    synchronized (e) {
      insertOnPut(e, value, t, t);
    }
  }

  /**
   * Remove the object mapped to key from the cache.
   */
  @Override
  public void remove(K key) {
    synchronized (lock) {
      int hc = modifiedHash(key.hashCode());
      E e = lookupEntryWoUsageRecording(key, hc);
      if (e != null) {
        removeEntryFromCacheAndReplacementList(e);
        removeCnt++;
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

  protected E lookupOrNewEntrySynchronized(K key) {
    int hc = modifiedHash(key.hashCode());
    E e = lookupEntryUnsynchronized(key, hc);
    if (e == null) {
      synchronized (lock) {
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
      evictSomeEntries();
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
   * Called when eviction or expiry of an entry happens. Remove it from the
   * main cache, refresh cache and from the (lru) list. Also cancel the timer.
   *
   * Called under big lock.
   */
  protected void removeEntryFromCacheAndReplacementList(E e) {
    removeEntryFromCache(e);
    removeEntryFromReplacementList(e);
  }

  /**
   * Remove entry from the cache hash and stop/reset the time if a timer was started.
   */
  protected void removeEntryFromCache(E e) {
    boolean f =
      mainHashCtrl.remove(mainHash, e) || refreshHashCtrl.remove(refreshHash, e);
    checkForHashCodeChange(e);
    if (e.isFetched()) {
      e.setRemovedState();
    }
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
   * If 0 then the object should not be cached at all.
   */
  protected long calcNextRefreshTime(
     T _oldObject, T _newObject, long _lastUpdate, long now) {
    RefreshController<T> lc = refreshController;
    long _tMaximum = maxLinger + now;
    long t = _tMaximum;
    if (lc != null && !(_newObject instanceof ExceptionWrapper)) {
      t = lc.calculateNextRefreshTime(_oldObject, _newObject, _lastUpdate, now);
      if (t > _tMaximum) {
        t = _tMaximum;
      }
    }
    if (t <= now) {
      return 0;
    }
    return t;
  }

  protected void fetch(E e) {
    long t0 = System.currentTimeMillis();
    T v;
    try {
      if (e.isVirgin() || e.hasException()) {
        if (source == null) {
          throw new CacheUsageExcpetion("source not set");
        }
        v = source.get((K) e.key, t0, null, e.fetchedTime);
      } else {
        v = source.get((K) e.key, t0, (T) e.getValue(), e.fetchedTime);
      }
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
    insert(e, v, t0, t, false);
  }

  protected final void insert(E e, T v, long t0, long t, boolean _updateStatistics) {
    touchedTime = t;
    long _nextRefreshTime = Entry.FETCHED_STATE;
    if (maxLinger >= 0) {
      if (e.isVirgin() || e.hasException()) {
        _nextRefreshTime = calcNextRefreshTime(null, v, 0L, t0);
      } else {
        _nextRefreshTime = calcNextRefreshTime((T) e.getValue(), v, e.fetchedTime, t0);
      }
    }
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
      if (e.isRemovedFromReplacementList()) {
        e.setFetchedState();
        e.value = v;
        return;
      }
      if (e.task != null) {
        e.task.cancel();
      }
      e.fetchedTime = t;
      e.value = v;
      if (timer != null && _nextRefreshTime > 0) {
        MyTimerTask tt = new MyTimerTask();
        tt.entry = e;
        timer.schedule(tt, new Date(_nextRefreshTime));
        e.task = tt;
      }
    }
    if (_nextRefreshTime > 10) {
      e.nextRefreshTime = _nextRefreshTime;
    } else {
      e.setFetchNextTimeState();
    }
  }

  /**
   * When the time has come remove the entry from the cache.
   */
  protected void timerEvent(final E e) {
    synchronized (lock) {
      CHECK_COUNTER_INVARIANTS();
      touchedTime = e.nextRefreshTime;
      if (e.task == null) {
        return;
      }
      if (refreshPool != null && mainHashCtrl.remove(mainHash, e)) {
        refreshHash = refreshHashCtrl.insert(refreshHash, e);
        if (e.hashCode != modifiedHash(e.key.hashCode())) {
          expiredRemoveCnt++;
          removeEntryFromCacheAndReplacementList(e);
          return;
        }
        Runnable r = new Runnable() {
          @Override
          public void run() {
            synchronized (e) {
              if (e.isRemovedFromReplacementList()) { return; }
              try {
                e.setGettingRefresh();
                fetch(e);
              } catch (Exception ex) {
                synchronized (lock) {
                  CHECK_COUNTER_INVARIANTS();
                  refreshSubmitFailedCnt++;
                  getLog().warn("Refresh exception", ex);
                  expireEntry(e);
                }
              }
            }
          }
        };
        boolean _submitOkay = refreshPool.submit(r);
        if (_submitOkay) { return; }
        refreshSubmitFailedCnt++;
      }
      expireEntry(e);
    }
  }

  protected void expireEntry(E e) {
    if (keepAfterExpired) {
      expiredKeptCnt++;
      e.setExpiredState();
    } else {
      if (!e.isRemovedFromReplacementList()) {
        expiredRemoveCnt++;
        removeEntryFromCacheAndReplacementList(e);
      }
    }
  }


  @Override
  public void removeAllAtOnce(Set<K> _keys) {
    synchronized (lock) {
      for (K key : _keys) {
        int hc = modifiedHash(key.hashCode());
        E e = lookupEntryWoUsageRecording(key, hc);
        if (e != null) {
          removeEntryFromCacheAndReplacementList(e);
          removeCnt++;
        }
      }
    }
  }






  /**
   * Idea: just return a map and do not do any thing
   * If we have prefetch threads, do a prefetch!
   */
  public Map<K, T> getAllFast(final Set<? extends K> _keys) {
    final Set<Map.Entry<K, T>> set =
      new AbstractSet<Map.Entry<K, T>>() {
        @Override
        public Iterator<Map.Entry<K, T>> iterator() {
          return new Iterator<Map.Entry<K, T>>() {
            Iterator<? extends K> keyIterator = _keys.iterator();
            @Override
            public boolean hasNext() {
              return keyIterator.hasNext();
            }

            @Override
            public Map.Entry<K, T> next() {
              final K key = keyIterator.next();
              return new Map.Entry<K, T>(){
                @Override
                public K getKey() {
                  return key;
                }

                @Override
                public T getValue() {
                  return get(key);
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
    return new AbstractMap<K, T>() {
      @Override
      public T get(Object key) {
        if (containsKey(key)) {
          return get(key);
        }
        return null;
      }

      @Override
      public boolean containsKey(Object key) {
        return _keys.contains(key);
      }

      @Override
      public Set<Entry<K, T>> entrySet() {
        return set;
      }
    };
  }


  /** JSR107 convenience getAll from array */
  public Map<K, T> getAll(K[] _keys) {
    return getAll(new HashSet<>(Arrays.asList(_keys)));
  }

  /**
   * JSR107 bulk interface
   */
  public Map<K, T> getAll(Set<? extends K> _keys) {
    K[] ka = (K[]) new Object[_keys.size()];
    int i = 0;
    for (K k : _keys) {
      ka[i++] = k;
    }
    T[] va = (T[]) new Object[ka.length];
    getBulk(ka, va, new BitSet(), 0, ka.length);
    Map<K,T> m = new HashMap<>();
    for (i = 0; i < ka.length; i++) {
      m.put(ka[i], va[i]);
    }
    return m;
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
  final long fetchBulkLoop(E[] ea, K[] k, T[] r, BitSet _fetched, int s, int i, int end, long t) {
    for (; i >= s; i--) {
      E e = ea[i];
      if (e == null) { continue; }
      if (!e.isFetched()) {
        synchronized (e) {
          if (!e.isFetched()) {
            r[i] = null;
            _fetched.set(i, false);
            long t2 = fetchBulkLoop(ea, k, r, _fetched, s, i - 1, end, t);
            if (!e.isFetched()) {
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
    if (experimentalBulkCacheSource == null) {
      sequentialFetch(ea, k, r, _fetched, s, end);
      return 0;
    } else {
      try {
        experimentalBulkCacheSource.getBulk(k, r, _fetched, s, end);
      } catch (Throwable _ouch) {
        T v = (T) new ExceptionWrapper(_ouch);
        for (i = s; i < end; i++) {
          Entry e = ea[i];
          if (!e.isFetched()) {
            e.value = v;
            r[i] = v;
          }
        }
      }
      return System.currentTimeMillis();
    }
  }

  final void sequentialFetch(E[] ea, K[] _keys, T[] _result, BitSet _fetched, int s, int end) {
    for (int i = s; i < end; i++) {
      E e = ea[i];
      if (e == null) { continue; }
      if (!e.isFetched()) {
        synchronized (e) {
          if (!e.isFetched()) {
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
        if (_entry.isFetched()) {
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




  protected boolean ENTRY_LOCK_HOLD(Entry e) {
    return true;
  }

  protected boolean BIG_LOCK_HOLD() {
    return true;
  }

  protected boolean CHECK_COUNTER_INVARIANTS() {
    return true;
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

  public long getFetchesBecauseOfNewEntries() {
    return fetchCnt - fetchedExpiredCnt - refreshCnt;
  }

  public int getFetchesInFlight() {
    return (int) (newEntryCnt - putNewEntryCnt - getFetchesBecauseOfNewEntries());
  }

  protected IntegrityState getIntegrityState() {
    synchronized (lock) {
      return new IntegrityState()
        .checkEquals("newEntryCnt == getFetchesBecauseOfNewEntries() + getFetchesInFlight() + putNewEntryCnt",
          newEntryCnt, getFetchesBecauseOfNewEntries() + getFetchesInFlight() + putNewEntryCnt)
         .checkLessOrEquals("getFetchedInFlight() <= 100", getFetchesInFlight(), 100)
        .checkEquals("newEntryCnt == getSize() + evictedCnt + expiredRemoveCnt + removeCnt", newEntryCnt, getSize() + evictedCnt + expiredRemoveCnt + removeCnt)
        .checkEquals("newEntryCnt == getSize() + evictedCnt + getExpiredCnt() - expiredKeptCnt + removeCnt", newEntryCnt, getSize() + evictedCnt + getExpiredCnt() - expiredKeptCnt + removeCnt)
        .checkEquals("mainHashCtrl.size == Hash.calcEntryCount(mainHash)", mainHashCtrl.size, Hash.calcEntryCount(mainHash))
        .checkEquals("refreshHashCtrl.size == Hash.calcEntryCount(refreshHash)", refreshHashCtrl.size, Hash.calcEntryCount(refreshHash))
        .checkLessOrEquals("size <= maxElements", getSize(), maxSize)
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
    long usageCnt = getHitCnt() + missCnt;
    CollisionInfo collisionInfo;
    String extraStatistics;
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
    public int getFetchesInFlightCnt() { return getFetchesInFlight(); }
    public long getBulkGetCnt() { return bulkGetCnt; }
    public long getRefreshCnt() { return refreshCnt; }
    public long getRefreshSubmitFailedCnt() { return refreshSubmitFailedCnt; }
    public long getRefreshHitCnt() { return refreshHitCnt; }
    public long getExpiredCnt() { return BaseCache.this.getExpiredCnt(); }
    public long getEvictedCnt() { return evictedCnt; }
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
      maxFill = 40;
      return (E[]) Array.newInstance(_entryType, 64);
    }

  }



  protected class MyTimerTask extends TimerTask {
    E entry;

    public void run() {
      timerEvent(entry);
    }
  }

  static class InitialValuePleaseComplainToJens { }

  final static InitialValuePleaseComplainToJens INITIAL_VALUE = new InitialValuePleaseComplainToJens();

  public static class Entry<E extends Entry, K, T> {

    static final int FETCHED_STATE = 10;
    static final int REMOVED_STATE = 12;
    static final int REFRESH_STATE = 11;
    static final int REPUT_STATE = 13;
    static final int EXPIRED_STATE = 4;
    static final int FETCH_NEXT_TIME_STATE = 3;
    static final int VIRGIN_STATE = 0;

    public TimerTask task;
    public long fetchedTime;
    public long nextRefreshTime;

    public K key;
    public T value = (T) INITIAL_VALUE;

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

    /** Reset next as a marker for {@link #isRemovedFromReplacementList()} */
    public final void removedFromList() {
      next = null;
    }

    /** Check that this entry is removed from the list, may be used in assertions. */
    public final boolean isRemovedFromReplacementList() {
      return next == null;
    }

    public E shortCircuit() {
      return next = prev = (E) this;
    }

    public final boolean isVirgin() {
      return nextRefreshTime == VIRGIN_STATE;
    }

    /**
     * Entry was removed before, however the entry contains still valid data.
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
      return nextRefreshTime == 3;
    }

    public final boolean isFetched() {
      return nextRefreshTime >= FETCHED_STATE;
    }

    public void setFetchedState() {
      nextRefreshTime = FETCHED_STATE;
    }

    /** Entry is kept in the cache but has expired */
    public void setExpiredState() {
      nextRefreshTime = EXPIRED_STATE;
    }

    public boolean isExpiredState() {
      return nextRefreshTime == EXPIRED_STATE;
    }

    public void setRemovedState() {
      nextRefreshTime = REMOVED_STATE;
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


  }

}
