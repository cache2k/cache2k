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
@SuppressWarnings({"WeakerAccess", "rawtypes"})
public class Hash2<K,V> {

  private static final int LOCK_SEGMENTS;
  private static final int LOCK_MASK;

  static {
    int ncpu = Runtime.getRuntime().availableProcessors();
    LOCK_SEGMENTS = 2 << (31 - Integer.numberOfLeadingZeros(ncpu));
    LOCK_MASK = LOCK_SEGMENTS - 1;
  }

  /**
   * Counts clear and close operation on the hash.
   * Needed for the iterator to detect the need for an abort.
   */
  private volatile int clearOrCloseCount = 0;

  /**
   * Maximum size of one segment, after we expand. Although there are concurrent updates/reads
   * this field does not need to be volatile, since expansion occurs very seldom.
   * Instead of adding volatile overhead, there might be unnecessary locks immediately after
   * an expansion.
   */
  private long segmentMaxFill;

  private Entry<K,V>[] entries;
  private final OptimisticLock[] locks;
  private final AtomicLong[] segmentSize;

  private final Cache cache;

  /**
   *
   * @param cache Cache reference only needed for the cache name in case of an exception
   */
  public Hash2(Cache cache) {
    this.cache = cache;
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
  public Entry<K,V> lookup(K key, int hash, int keyValue) {
    OptimisticLock[] locks = this.locks;
    int si = hash & LOCK_MASK;
    OptimisticLock l = locks[si];
    long stamp = l.tryOptimisticRead();
    Entry<K,V>[] tab = entries;
    if (tab == null) {
      throw new CacheClosedException(cache);
    }
    Entry<K,V> e;
    int n = tab.length;
    int mask = n - 1;
    int idx = hash & (mask);
    e = tab[idx];
    while (e != null) {
      if (e.hashCode == keyValue && keyObjIsEqual(key, e)) {
        return e;
      }
      e = e.another;
    }
    if (l.validate(stamp)) {
      return null;
    }
    stamp = l.readLock();
    try {
      tab = entries;
      if (tab == null) {
        throw new CacheClosedException(cache);
      }
      n = tab.length;
      mask = n - 1;
      idx = hash & (mask);
      e = tab[idx];
      while (e != null) {
        if (e.hashCode == keyValue && (keyObjIsEqual(key, e))) {
          return e;
        }
        e = e.another;
      }
      return null;
    } finally {
      l.unlockRead(stamp);
    }
  }

  protected boolean keyObjIsEqual(K key, Entry e) {
    Object ek;
    return (ek = e.getKeyObj()) == key || (ek.equals(key));
  }



  /**
   * Insert an entry. Checks if an entry already exists.
   */
  public Entry<K,V> insertWithinLock(Entry<K,V> e, int hash, int keyValue) {
    K key = e.getKeyObj();
    int si = hash & LOCK_MASK;
    Entry<K,V> f; Object ek; Entry<K,V>[] tab = entries;
    if (tab == null) {
      throw new CacheClosedException(cache);
    }
    int n = tab.length, mask = n - 1, idx = hash & (mask);
    f = tab[idx];
    while (f != null) {
      if (f.hashCode == keyValue && ((ek = f.getKeyObj()) == key || (ek.equals(key)))) {
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
   * Checks whether expansion is needed and expand when {@link #insertWithinLock(Entry, int, int)}
   * is used. No lock may be hold when calling this method, since the table must be locked
   * completely using the proper lock order.
   *
   * <p>Need for expansion is only checked by comparing whether the associated segment is
   * full. Should be called after insert after giving up the lock.
   */
  public void checkExpand(int hash) {
    int si = hash & LOCK_MASK;
    long size = segmentSize[si].get();
    if (size > segmentMaxFill) {
      eventuallyExpand(si);
    }
  }

  public OptimisticLock getSegmentLock(int hash) {
    return locks[hash & LOCK_MASK];
  }

  /**
   * Remove existing entry from the hash.
   *
   * @return true, if entry was found and removed.
   */
  public boolean remove(Entry<K,V> e) {
    int hash = modifiedHashCode(e.hashCode);
    OptimisticLock[] locks = this.locks;
    int si = hash & LOCK_MASK;
    OptimisticLock l = locks[si];
    long stamp = l.writeLock();
    try {
      Entry<K,V> f; Entry<K,V>[] tab = entries;
      if (tab == null) {
        throw new CacheClosedException(cache);
      }
      int n = tab.length, mask = n - 1, idx = hash & (mask);
      f = tab[idx];
      if (f == e) {
        tab[idx] = f.another;
        segmentSize[si].decrementAndGet();
        return true;
      }
      while (f != null) {
        Entry<K,V> another = f.another;
        if (another == e) {
          f.another = another.another;
          segmentSize[si].decrementAndGet();
          return true;
        }
        f = another;
      }
    } finally {
      l.unlockWrite(stamp);
    }
    return false;
  }

  public boolean removeWithinLock(Entry<K,V> e, int hash) {
    int si = hash & LOCK_MASK;
    Entry<K,V> f; Entry<K,V>[] tab = entries;
    if (tab == null) {
      throw new CacheClosedException(cache);
    }
    int n = tab.length, mask = n - 1, idx = hash & (mask);
    f = tab[idx];
    if (f == e) {
      tab[idx] = f.another;
      segmentSize[si].decrementAndGet();
      return true;
    }
    while (f != null) {
      Entry<K,V> another = f.another;
      if (another == e) {
        f.another = another.another;
        segmentSize[si].decrementAndGet();
        return true;
      }
      f = another;
    }
    return false;
  }


  /**
   * Acquire all segment locks and rehash, if really needed.
   */
  private void eventuallyExpand(int segmentIndex) {
    long[] stamps = lockAll();
    try {
      long size = segmentSize[segmentIndex].get();
      if (size <= segmentMaxFill) {
        return;
      }
      rehash();
    } finally {
      unlockAll(stamps);
    }
  }

  /**
   * Acquire all segment locks and return an array with the lock stamps.
   */
  private long[] lockAll() {
    OptimisticLock[] locks = this.locks;
    int sn = locks.length;
    long[] stamps = new long[this.locks.length];
    for (int i = 0; i < sn; i++) {
      OptimisticLock l = locks[i];
      stamps[i] = l.writeLock();
    }
    return stamps;
  }

  /**
   * Release the all segment locks.
   *
   * @param stamps array with the lock stamps.
   */
  private void unlockAll(long[] stamps) {
    OptimisticLock[] locks = this.locks;
    int sn = locks.length;
    for (int i = 0; i < sn; i++) {
      locks[i].unlockWrite(stamps[i]);
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
    int i, sl = src.length, n = sl * 2, mask = n - 1, idx;
    Entry<K,V>[] tab = new Entry[n];
    long count = 0; Entry next, e;
    for (i = 0; i < sl; i++) {
      e = src[i];
      while (e != null) {
        count++; next = e.another; idx = modifiedHashCode(e.hashCode) & mask;
        e.another = tab[idx]; tab[idx] = e;
        e = next;
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
    long[] stamps = lockAll();
    try {
      return j.call();
    } finally {
      unlockAll(stamps);
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
          int size = 1;
          while (e != null) {
            inf.collisionCnt++;
            e = e.another;
            size++;
          }
          if (inf.longestCollisionSize < size) {
            inf.longestCollisionSize = size;
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
    long count = 0;
    for (Entry e : entries) {
      while (e != null) {
        count++;
        e = e.another;
      }
    }
    return count;
  }

  /**
   * Entry table for used by the iterator.
   */
  public Entry<K,V>[] getEntries() {
    return entries;
  }

}
