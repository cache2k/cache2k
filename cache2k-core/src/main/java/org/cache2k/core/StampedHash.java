package org.cache2k.core;

/*-
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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
import org.cache2k.CacheClosedException;

import java.util.concurrent.locks.StampedLock;
import java.util.function.Supplier;

/**
 * Simple concurrent hash table implementation using optimistic locking
 * via StampedLock for the segments locks.
 *
 * @author Jens Wilke
 */
@SuppressWarnings({"WeakerAccess", "rawtypes"})
public class StampedHash<K, V> {

  /**
   * Size of the hash table before inserting the first entry. Must be power
   * of two. Default: 64.
   */
  private static final int INITIAL_HASH_SIZE = 64;

  /**
   * Fill percentage limit. When this is reached the hash table will get
   * expanded. Default: 64.
   */
  private static final int HASH_LOAD_PERCENT = 64;

  private static final int LOCK_SEGMENTS;
  private static final int LOCK_MASK;

  /* GraalVM: This runs at runtime, see native-image.properties */
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

  private Entry<K, V>[] entries;
  private final StampedLock[] locks;
  private final long[] segmentSize;

  /** Cache reference, only used for CacheClosedException */
  private final Cache<?, ?> maybeClosedCache;

  /**
   * @param cache Cache reference only needed for the cache name in case of an exception
   */
  public StampedHash(Cache<?, ?> cache) {
    maybeClosedCache = cache;
  }

  {
    locks = new StampedLock[LOCK_SEGMENTS];
    for (int i = 0; i < LOCK_SEGMENTS; i++) {
      locks[i] = new StampedLock();
    }
    segmentSize = new long[LOCK_SEGMENTS];
    initArray();
  }

  @SuppressWarnings("unchecked")
  private void initArray() {
    int len = Math.max(INITIAL_HASH_SIZE, LOCK_SEGMENTS * 4);
    entries = new Entry[len];
    calcMaxFill();
  }

  public long getEntryCapacity() {
    return entries.length * 1L * HASH_LOAD_PERCENT / 100;
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
  public Entry<K, V> lookup(K key, int hash, int keyValue) {
    StampedLock[] locks = this.locks;
    int si = hash & LOCK_MASK;
    StampedLock l = locks[si];
    long stamp = l.tryOptimisticRead();
    Entry<K, V>[] tab = entries;
    if (tab == null) {
      throw new CacheClosedException(maybeClosedCache);
    }
    Entry<K, V> e;
    int n = tab.length;
    int mask = n - 1;
    int idx = hash & (mask);
    e = tab[idx];
    for (;;) {
      if (e == null) {
        if (l.validate(stamp)) { return null; }
        break;
      }
      if (e.hashCode == keyValue && keyObjIsEqual(key, e)) {
        return e;
      }
      e = e.another;
    }
    stamp = l.readLock();
    try {
      tab = entries;
      if (tab == null) {
        throw new CacheClosedException(maybeClosedCache);
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
  public Entry<K, V> insertWithinLock(Entry<K, V> e, int hash, int keyValue) {
    K key = e.getKeyObj();
    int si = hash & LOCK_MASK;
    Entry<K, V> f; Object ek; Entry<K, V>[] tab = entries;
    if (tab == null) {
      throw new CacheClosedException(maybeClosedCache);
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
    segmentSize[si]++;
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
    long size = segmentSize[si];
    if (size > segmentMaxFill) {
      eventuallyExpand(si);
    }
  }

  public StampedLock getSegmentLock(int hash) {
    return locks[hash & LOCK_MASK];
  }

  /**
   * Remove existing entry from the hash.
   *
   * @return true, if entry was found and removed.
   */
  public boolean remove(Entry<K, V> e) {
    int hash = spreadHashFromEntry(e.hashCode);
    StampedLock[] locks = this.locks;
    int si = hash & LOCK_MASK;
    StampedLock l = locks[si];
    long stamp = l.writeLock();
    try {
      Entry<K, V> f; Entry<K, V>[] tab = entries;
      if (tab == null) {
        throw new CacheClosedException(maybeClosedCache);
      }
      int n = tab.length, mask = n - 1, idx = hash & (mask);
      f = tab[idx];
      if (f == e) {
        tab[idx] = f.another;
        segmentSize[si]--;
        return true;
      }
      while (f != null) {
        Entry<K, V> another = f.another;
        if (another == e) {
          f.another = another.another;
          segmentSize[si]--;
          return true;
        }
        f = another;
      }
    } finally {
      l.unlockWrite(stamp);
    }
    return false;
  }

  public boolean removeWithinLock(Entry<K, V> e, int hash) {
    int si = hash & LOCK_MASK;
    Entry<K, V> f; Entry<K, V>[] tab = entries;
    if (tab == null) {
      throw new CacheClosedException(maybeClosedCache);
    }
    int n = tab.length, mask = n - 1, idx = hash & (mask);
    f = tab[idx];
    if (f == e) {
      tab[idx] = f.another;
      segmentSize[si]--;
      return true;
    }
    while (f != null) {
      Entry<K, V> another = f.another;
      if (another == e) {
        f.another = another.another;
        segmentSize[si]--;
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
      long size = segmentSize[segmentIndex];
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
    StampedLock[] locks = this.locks;
    int sn = locks.length;
    long[] stamps = new long[this.locks.length];
    for (int i = 0; i < sn; i++) {
      StampedLock l = locks[i];
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
    StampedLock[] locks = this.locks;
    int sn = locks.length;
    for (int i = 0; i < sn; i++) {
      locks[i].unlockWrite(stamps[i]);
    }
  }

  /**
   * Return the spreaded hash code from the hash code that is stored in the entry.
   * For integer keys we store the key directly, so we need to calculate the spread
   * again.
   */
  protected int spreadHashFromEntry(int hc) {
    return hc;
  }

  /**
   * Double the hash table size and rehash the entries. Assumes total lock.
   */
  @SuppressWarnings("unchecked")
  void rehash() {
    Entry<K, V>[] src = entries;
    if (src == null) {
      throw new CacheClosedException(maybeClosedCache);
    }
    int i, sl = src.length, n = sl * 2, mask = n - 1, idx;
    Entry<K, V>[] tab = new Entry[n];
    long count = 0; Entry next, e;
    for (i = 0; i < sl; i++) {
      e = src[i];
      while (e != null) {
        count++; next = e.another; idx = spreadHashFromEntry(e.hashCode) & mask;
        e.another = tab[idx]; tab[idx] = e;
        e = next;
      }
    }
    entries = tab;
    calcMaxFill();
  }

  /**
   * Number of hash table entries. Uses locking to read the latest changes and consistent
   * long values for 23 bit systems. May not be called when lock is held.
   */
  public long getSize() {
    long sum = 0;
    for (int i = 0; i < segmentSize.length; i++) {
      long stamp = locks[i].tryOptimisticRead();
      long v = segmentSize[i];
      if (!locks[i].validate(stamp)) {
        stamp = locks[i].readLock();
        v = segmentSize[i];
        locks[i].unlockRead(stamp);
      }
      sum += v;
    }
    return sum;
  }

  /**
   * Separate version of getSize expected all segments are locked.
   */
  public long getSizeWithGlobalLock() {
    long sum = 0;
    for (long l : segmentSize) {
      sum += l;
    }
    return sum;
  }

  /**
   * Lock all segments and run the job.
   */
  public <T> T runTotalLocked(Supplier<T> j) {
    long[] stamps = lockAll();
    try {
      return j.get();
    } finally {
      unlockAll(stamps);
    }
  }

  public void clearWhenLocked() {
    for (int i = 0; i < segmentSize.length; i++) {
      segmentSize[i] = 0;
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
   * Entry table used by the iterator.
   */
  public Entry<K, V>[] getEntries() {
    return entries;
  }

}
