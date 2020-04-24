package org.cache2k.core;

/*
 * #%L
 * cache2k implementation
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

import org.cache2k.Cache;
import org.cache2k.core.concurrency.Job;
import org.cache2k.core.concurrency.Locks;
import org.cache2k.core.concurrency.OptimisticLock;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple concurrent hash table implementation using optimistic locking
 * for the segments locks.
 *
 * @author Jens Wilke
 * @see OptimisticLock
 */
@SuppressWarnings({"ConstantConditions", "WeakerAccess"})
public class Hash2<K,V> {

  private static final int LOCK_SEGMENTS;
  private static final int LOCK_MASK;

  static {
    int _ncpu = Runtime.getRuntime().availableProcessors();
    LOCK_SEGMENTS = 2 << (31 - Integer.numberOfLeadingZeros(_ncpu));
    LOCK_MASK = LOCK_SEGMENTS - 1;
  }

  /**
   * Counts clear and close operation on the hash.
   * Needed for the iterator to detect the need for an abort.
   */
  private volatile int clearOrCloseCount = 0;

  /**
   * Maximum size of one segment, after we expand.
   */
  private long segmentMaxFill;

  private Entry<K,V>[] entries;
  private final OptimisticLock[] locks;
  private final AtomicLong[] segmentSize;

  private final Cache cache;

  /**
   *
   * @param _cache Cache reference only needed for the cache name in case of an exception
   */
  public Hash2(final Cache _cache) {
    cache = _cache;
  }

  {
    locks = new OptimisticLock[LOCK_SEGMENTS];
    for (int i = 0; i < LOCK_SEGMENTS; i++) {
      locks[i] = Locks.newOptimistic();
    }
    segmentSize = new AtomicLong[LOCK_SEGMENTS];
    for (int i = 0; i < LOCK_SEGMENTS; i++) {
      segmentSize[i] = new AtomicLong();
    }
    initArray();
  }

  @SuppressWarnings("unchecked")
  private void initArray() {
    int len = Math.max(HeapCache.TUNABLE.initialHashSize, LOCK_SEGMENTS * 4);
    entries = new Entry[len];
    calcMaxFill();
  }

  public long getEntryCapacity() {
    return entries.length * 1L * HeapCache.TUNABLE.hashLoadPercent / 100;
  }

  /** For testing */
  public long getSegmentMaxFill() {
    return segmentMaxFill;
  }

  private void calcMaxFill() {
    segmentMaxFill = getEntryCapacity() / LOCK_SEGMENTS;
  }

  /**
   * Lookup the entry in the hash table and return it. First tries an optimistic read.
   */
  public Entry<K,V> lookup(K key, int _hash, int _keyValue) {
    OptimisticLock[] _locks = locks;
    int si = _hash & LOCK_MASK;
    OptimisticLock l = _locks[si];
    long _stamp = l.tryOptimisticRead();
    Entry<K,V>[] tab = entries;
    if (tab == null) {
      throw new CacheClosedException(cache);
    }
    Entry<K,V> e;
    int n = tab.length;
    int _mask = n - 1;
    int idx = _hash & (_mask);
    e = tab[idx];
    while (e != null) {
      if (e.hashCode == _keyValue && keyObjIsEqual(key, e)) {
        return e;
      }
      e = e.another;
    }
    if (l.validate(_stamp)) {
      return null;
    }
    _stamp = l.readLock();
    try {
      tab = entries;
      if (tab == null) {
        throw new CacheClosedException(cache);
      }
      n = tab.length;
      _mask = n - 1;
      idx = _hash & (_mask);
      e = tab[idx];
      while (e != null) {
        if (e.hashCode == _keyValue && (keyObjIsEqual(key, e))) {
          return e;
        }
        e = e.another;
      }
      return null;
    } finally {
      l.unlockRead(_stamp);
    }
  }

  protected boolean keyObjIsEqual(final K key, final Entry e) {
    Object ek;
    return (ek = e.getKeyObj()) == key || (ek.equals(key));
  }



  /**
   * Insert an entry. Checks if an entry already exists.
   */
  public Entry<K,V> insertWithinLock(Entry<K,V> e, int _hash, int _keyValue) {
    K key = e.getKeyObj();
    int si = _hash & LOCK_MASK;
    Entry<K,V> f; Object ek; Entry<K,V>[] tab = entries;
    if (tab == null) {
      throw new CacheClosedException(cache);
    }
    int n = tab.length, _mask = n - 1, idx = _hash & (_mask);
    f = tab[idx];
    while (f != null) {
      if (f.hashCode == _keyValue && ((ek = f.getKeyObj()) == key || (ek.equals(key)))) {
        return f;
      }
      f = f.another;
    }
    e.another = tab[idx];
    tab[idx] = e;
    segmentSize[si].incrementAndGet();
    return e;
  }

  /**
   * Checks whether expansion is needed and expand when {@link #insertWithinLock(Entry, int, int)} is used.
   * No lock may be hold when calling this method, since the table must be locked completely using
   * the proper lock order.
   *
   * <p>Need for expansion is only checked by comparing whether the associated segment is
   * full. Should be called after insert after giving up the lock.
   */
  public void checkExpand(int _hash) {
    int si = _hash & LOCK_MASK;
    long _size = segmentSize[si].get();
    if (_size > segmentMaxFill) {
      eventuallyExpand(si);
    }
  }

  public OptimisticLock getSegmentLock(int _hash) {
    return locks[_hash & LOCK_MASK];
  }

  /**
   * Remove existing entry from the hash.
   *
   * @return true, if entry was found and removed.
   */
  public boolean remove(Entry<K,V> e) {
    int _hash = modifiedHashCode(e.hashCode);
    OptimisticLock[] _locks = locks;
    int si = _hash & LOCK_MASK;
    OptimisticLock l = _locks[si];
    long _stamp = l.writeLock();
    try {
      Entry<K,V> f; Entry<K,V>[] tab = entries;
      if (tab == null) {
        throw new CacheClosedException(cache);
      }
      int n = tab.length, _mask = n - 1, idx = _hash & (_mask);
      f = tab[idx];
      if (f == e) {
        tab[idx] = f.another;
        segmentSize[si].decrementAndGet();
        return true;
      }
      while (f != null) {
        Entry<K,V> _another = f.another;
        if (_another == e) {
          f.another = _another.another;
          segmentSize[si].decrementAndGet();
          return true;
        }
        f = _another;
      }
    } finally {
      l.unlockWrite(_stamp);
    }
    return false;
  }

  public boolean removeWithinLock(Entry<K,V> e, int _hash) {
    int si = _hash & LOCK_MASK;
    Entry<K,V> f; Entry<K,V>[] tab = entries;
    if (tab == null) {
      throw new CacheClosedException(cache);
    }
    int n = tab.length, _mask = n - 1, idx = _hash & (_mask);
    f = tab[idx];
    if (f == e) {
      tab[idx] = f.another;
      segmentSize[si].decrementAndGet();
      return true;
    }
    while (f != null) {
      Entry<K,V> _another = f.another;
      if (_another == e) {
        f.another = _another.another;
        segmentSize[si].decrementAndGet();
        return true;
      }
      f = _another;
    }
    return false;
  }


  /**
   * Acquire all segment locks and rehash, if really needed.
   */
  private void eventuallyExpand(int _segmentIndex) {
    long[] _stamps = lockAll();
    try {
      long _size = segmentSize[_segmentIndex].get();
      if (_size <= segmentMaxFill) {
        return;
      }
      rehash();
    } finally {
      unlockAll(_stamps);
    }
  }

  /**
   * Acquire all segment locks and return an array with the lock stamps.
   */
  private long[] lockAll() {
    OptimisticLock[] _locks = locks;
    int sn = _locks.length;
    long[] _stamps = new long[locks.length];
    for (int i = 0; i < sn; i++) {
      OptimisticLock l = _locks[i];
      _stamps[i] = l.writeLock();
    }
    return _stamps;
  }

  /**
   * Release the all segment locks.
   *
   * @param _stamps array with the lock stamps.
   */
  private void unlockAll(long[] _stamps) {
    OptimisticLock[] _locks = locks;
    int sn = _locks.length;
    for (int i = 0; i < sn; i++) {
      _locks[i].unlockWrite(_stamps[i]);
    }
  }

  protected int modifiedHashCode(int hc) {
    return hc;
  }

  /**
   * Double the hash table size and rehash the entries. Assumes total lock.
   */
  @SuppressWarnings("unchecked")
  void rehash() {
    Entry<K,V>[] src = entries;
    if (src == null) {
      throw new CacheClosedException(cache);
    }
    int i, sl = src.length, n = sl * 2, _mask = n - 1, idx;
    Entry<K,V>[] tab = new Entry[n];
    long _count = 0; Entry _next, e;
    for (i = 0; i < sl; i++) {
      e = src[i];
      while (e != null) {
        _count++; _next = e.another; idx = modifiedHashCode(e.hashCode) & _mask;
        e.another = tab[idx]; tab[idx] = e;
        e = _next;
      }
    }
    entries = tab;
    calcMaxFill();
  }

  public long getSize() {
    long sum = 0;
    for (AtomicLong al : segmentSize) {
      sum += al.get();
    }
    return sum;
  }

  /**
   * Lock all segments and run the job.
   */
  public <T> T runTotalLocked(Job<T> j) {
    long[] _stamps = lockAll();
    try {
      return j.call();
    } finally {
      unlockAll(_stamps);
    }
  }

  public void clearWhenLocked() {
    for (AtomicLong aSegmentSize : segmentSize) {
      aSegmentSize.set(0);
    }
    clearOrCloseCount++;
    initArray();
  }

  public int getClearOrCloseCount() {
    return clearOrCloseCount;
  }

  /**
   * Close the cache by discarding the entry table. Assumes total lock.
   *
   * <p>Closing will be visible to other threads, because of the guarantees of the locking.
   * Using the entry table for closing has the advantage that the close check collapses with
   * the implicit null check and has no additional overhead.
   */
  public void close() {
    clearOrCloseCount++;
    entries = null;
  }

  public void calcHashCollisionInfo(CollisionInfo inf) {
    for (Entry<K, V> e : entries) {
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
  public long calcEntryCount() {
    long _count = 0;
    for (Entry e : entries) {
      while (e != null) {
        _count++;
        e = e.another;
      }
    }
    return _count;
  }

  /**
   * Entry table for used by the iterator.
   */
  public Entry<K,V>[] getEntries() {
    return entries;
  }

}
