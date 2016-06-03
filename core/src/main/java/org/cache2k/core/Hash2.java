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

import org.cache2k.core.threading.Job;
import org.cache2k.core.threading.Locks;
import org.cache2k.core.threading.OptimisticLock;

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

  private int clearCount = 0;
  private long maxFill;
  private Entry<K,V>[] entries;
  private final OptimisticLock[] locks;
  private final AtomicLong[] segmentSize;

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

  private void initArray() {
    int len = HeapCache.TUNABLE.initialHashSize;
    if (segmentSize.length > len) {
      len = segmentSize.length;
    }
    entries = new Entry[len];
    calcMaxFill();
  }

  private void calcMaxFill() {
    maxFill = entries.length * HeapCache.TUNABLE.hashLoadPercent / 100 / segmentSize.length;
  }

  /**
   * Lookup the entry in the hash table and return it. First tries an optimistic read.
   */
  public Entry<K,V> lookup(K key, int _hash) {
    OptimisticLock[] _locks = locks;
    int si = _hash & LOCK_MASK;
    OptimisticLock l = _locks[si];
    long _stamp = l.tryOptimisticRead();
    Entry<K,V>[] tab = entries;
    if (tab == null) {
      throw new CacheClosedException();
    }
    Entry e;
    Object ek;
    int n = tab.length;
    int _mask = n - 1;
    int idx = _hash & (_mask);
    e = tab[idx];
    while (e != null) {
      if (e.hashCode == _hash && ((ek = e.key) == key || (ek.equals(key)))) {
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
        throw new CacheClosedException();
      }
      n = tab.length;
      _mask = n - 1;
      idx = _hash & (_mask);
      e = tab[idx];
      while (e != null) {
        if (e.hashCode == _hash && ((ek = e.key) == key || (ek.equals(key)))) {
          return e;
        }
        e = e.another;
      }
      return null;
    } finally {
      l.unlockRead(_stamp);
    }
  }

  /**
   * Insert an entry. Checks if an entry already exists.
   */
  public Entry<K,V> insert(Entry<K,V> e) {
    long _size;
    int _hash = e.hashCode; K key = e.key;
    OptimisticLock[] _locks = locks;
    int si = _hash & LOCK_MASK;
    OptimisticLock l = _locks[si];
    long _stamp = l.writeLock();
    try {
      Entry f; Object ek; Entry<K,V>[] tab = entries;
      if (tab == null) {
        throw new CacheClosedException();
      }
      int n = tab.length, _mask = n - 1, idx = _hash & (_mask);
      f = tab[idx];
      while (f != null) {
        if (f.hashCode == _hash && ((ek = f.key) == key || (ek.equals(key)))) {
          return f;
        }
        f = f.another;
      }
      e.another = tab[idx];
      tab[idx] = e;
      _size = segmentSize[si].incrementAndGet();

    } finally {
      l.unlockWrite(_stamp);
    }
    if (_size > maxFill) {
      expand();
    }
    return e;
  }

  /**
   * Insert entry and overwrite existing entry.
   */
  public void insertOverwrite(Entry<K,V> e) {
    long _size;
    int _hash = e.hashCode; K key = e.key;
    OptimisticLock[] _locks = locks;
    int si = _hash & LOCK_MASK;
    OptimisticLock l = _locks[si];
    long _stamp = l.writeLock();
    try {
      Entry f; Object ek; Entry<K,V>[] tab = entries;
      if (tab == null) {
        throw new CacheClosedException();
      }
      int n = tab.length, _mask = n - 1, idx = _hash & (_mask);
      f = tab[idx];
      if (f != null) {
        if (f.hashCode == _hash && ((ek = f.key) == key || (ek.equals(key)))) {
          tab[idx] = e;
          e.another = f.another;
          return;
        }
        Entry _previous = f;
        f = f.another;
        while (f != null) {
          if (f.hashCode == _hash && ((ek = f.key) == key || (ek.equals(key)))) {
            e.another = f.another;
            _previous.another = e;
            return;
          }
          _previous = f;
          f = f.another;
        }
      }
      e.another = tab[idx];
      tab[idx] = e;
      _size = segmentSize[si].incrementAndGet();

    } finally {
      l.unlockWrite(_stamp);
    }
    if (_size > maxFill) {
      expand();
    }
  }

  /**
   * Insert an entry. Checks if an entry already exists.
   */
  public Entry<K,V> insertWithinLock(Entry<K,V> e, int _hash) {
    K key = e.key;
    int si = _hash & LOCK_MASK;
    Entry f; Object ek; Entry<K,V>[] tab = entries;
    if (tab == null) {
      throw new CacheClosedException();
    }
    int n = tab.length, _mask = n - 1, idx = _hash & (_mask);
    f = tab[idx];
    while (f != null) {
      if (f.hashCode == _hash && ((ek = f.key) == key || (ek.equals(key)))) {
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
   * Checks whether expansion is needed and expand when {@link #insertWithinLock(Entry, int)} is used.
   * No lock may be hold when calling this method, since the table must be locked completely using
   * the proper lock order.
   *
   * <p>Need for expansion is only checked by comparing whether the associated segment is
   * full. Should be called after insert after giving up the lock.
   */
  public void checkExpand(int _hash) {
    int si = _hash & LOCK_MASK;
    long _size = segmentSize[si].get();
    if (_size > maxFill) {
      expand();
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
    int _hash = e.hashCode;
    OptimisticLock[] _locks = locks;
    int si = _hash & LOCK_MASK;
    OptimisticLock l = _locks[si];
    long _stamp = l.writeLock();
    try {
      Entry f; Entry<K,V>[] tab = entries;
      if (tab == null) {
        throw new CacheClosedException();
      }
      int n = tab.length, _mask = n - 1, idx = _hash & (_mask);
      f = tab[idx];
      if (f == e) {
        tab[idx] = f.another;
        segmentSize[si].decrementAndGet();
        return true;
      }
      while (f != null) {
        Entry _another = f.another;
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
    Entry f; Entry<K,V>[] tab = entries;
    if (tab == null) {
      throw new CacheClosedException();
    }
    int n = tab.length, _mask = n - 1, idx = _hash & (_mask);
    f = tab[idx];
    if (f == e) {
      tab[idx] = f.another;
      segmentSize[si].decrementAndGet();
      return true;
    }
    while (f != null) {
      Entry _another = f.another;
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
   * Remove entry by the key.
   *
   * @return  The removed entry or null if not found.
   */
  public Entry<K,V> remove(Object key, int _hash) {
    OptimisticLock[] _locks = locks;
    int si = _hash & LOCK_MASK;
    OptimisticLock l = _locks[si];
    long _stamp = l.writeLock();
    try {
      Entry e; Object ek; Entry<K,V>[] tab = entries;
      if (tab == null) {
        throw new CacheClosedException();
      }
      int n = tab.length, _mask = n - 1, idx = _hash & (_mask);
      e = tab[idx];
      if (e == null) {
        return null;
      }
      if (e.hashCode == _hash && ((ek = e.key) == key || (ek.equals(key)))) {
        tab[idx] = e.another;
        segmentSize[si].decrementAndGet();
        return e;
      }
      Entry _previous = e; e = e.another;
      while (e != null) {
        if (e.hashCode == _hash && ((ek = e.key) == key || (ek.equals(key)))) {
          _previous.another = e.another;
          segmentSize[si].decrementAndGet();
          return e;
        }
        _previous = e;
        e = e.another;
      }
    } finally {
      l.unlockWrite(_stamp);
    }
    return null;
  }

  /**
   * Acquire all segment locks and rehash.
   */
  private void expand() {
    long[] _stamps = lockAll();
    try {
      rehash();
    } finally {
      unlockAll(_stamps);
    }
  }

  /**
   * Acquire all segment locks and return an array with the stamps.
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

  /**
   * Double the hash table size and rehash the entries. Assumes total lock.
   */
  private void rehash() {
    Entry<K,V>[] src = entries;
    int i, sl = src.length, n = sl * 2, _mask = n - 1, idx;
    Entry<K,V>[] tab = new Entry[n];
    long _count = 0; Entry _next, e;
    for (i = 0; i < sl; i++) {
      e = src[i];
      while (e != null) {
        _count++; _next = e.another; idx = e.hashCode & _mask;
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
   * Lock all segments and run job.
   */
  public <T> T runTotalLocked(Job<T> j) {
    long[] _stamps = lockAll();
    try {
      return j.call();
    } finally {
      unlockAll(_stamps);
    }
  }

  /**
   * True if all locks are held be the current thread. If locking
   * does not support holder check then always true.
   */
  private boolean allLocked() {
    for (OptimisticLock l : locks) {
      if (!l.canCheckHolder()) { return true; }
      if (!l.isHoldingWriteLock()) { return false; }
    }
    return true;
  }

  public void clearWhenLocked() {
    for (AtomicLong aSegmentSize : segmentSize) {
      aSegmentSize.set(0);
    }
    clearCount++;
    initArray();
  }

  public int getClearCount() {
    return clearCount;
  }

  public long getMaxFill() {
    return maxFill;
  }

  public void close() {
    entries = null;
  }

  public void calcHashCollisionInfo(Hash.CollisionInfo inf) {
    Entry<K,V>[] tab = entries;
    for (Entry<K, V> aTab : tab) {
      Entry e = aTab;
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
   * Entry table for used by the iterator.
   */
  public Entry<K,V>[] getEntries() {
    return entries;
  }

}
