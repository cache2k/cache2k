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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * @author Jens Wilke
 */
public class Hash2<K,V> {

  private volatile int clearCount = 0;
  private long maxFill;
  private Object bigLock;
  volatile AtomicReferenceArray<Entry<K,V>> entries;
  AtomicInteger size = new AtomicInteger();
  OptimisticLock[] locks;
  AtomicLong[] segmentSize;

  public AtomicReferenceArray<Entry<K,V>> initSegmented(Object _bigLock) {
    bigLock = _bigLock;
    int _segmentCount = 8;
    locks = new OptimisticLock[_segmentCount];
    for (int i = 0; i < _segmentCount; i++) {
      locks[i] = Locks.newOptimistic();
    }
    segmentSize = new AtomicLong[_segmentCount];
    for (int i = 0; i < _segmentCount; i++) {
      segmentSize[i] = new AtomicLong();
    }
    initArray();
    return entries;
  }

  private void initArray() {
    maxFill =
      HeapCache.TUNABLE.initialHashSize * HeapCache.TUNABLE.hashLoadPercent /
        100 / segmentSize.length;
    if (maxFill == 0) {
      throw new IllegalArgumentException("values for hash size or load factor too low.");
    }
    entries = new AtomicReferenceArray(HeapCache.TUNABLE.initialHashSize);
  }

  public static <K,V> Entry<K,V> lookupQuick(K key, int _hash, AtomicReferenceArray<Entry<K,V>> tab) {
    if (tab == null) {
      throw new CacheClosedException();
    }
    Entry<K,V> e; K ek; int n = tab.length(); int _mask = n - 1; int idx = _hash & (_mask);
    e = tab.get(idx);
    while (e != null) {
      if (e.hashCode == _hash && ((ek = e.key) == key || (ek.equals(key)))) {
        return e;
      }
      e = e.another;
    }
    return null;
  }

  public Entry<K,V> lookupQuick(K key, int _hash) {
    return lookupQuick(key, _hash, entries);
  }

  /**
   * Lookup the entry in the hash table and return it. First tries an optimistic read.
   */
  public Entry<K,V> lookup(K key, int _hash) {
    AtomicReferenceArray<Entry<K,V>> tab = entries;
    if (tab == null) {
      throw new CacheClosedException();
    }
    OptimisticLock[] _locks = locks;
    int si = _hash & (_locks.length - 1);
    OptimisticLock l = _locks[si];
    long _stamp = l.tryOptimisticRead();
    Entry e;
    Object ek;
    int n = tab.length();
    int _mask = n - 1;
    int idx = _hash & (_mask);
    e = tab.get(idx);
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
      n = tab.length();
      _mask = n - 1;
      idx = _hash & (_mask);
      e = tab.get(idx);
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
  public Entry<K,V> insert(Entry<K,V> e2) {
    long _size;
    int _hash = e2.hashCode; K key = e2.key;
    OptimisticLock[] _locks = locks;
    int si = _hash & (_locks.length - 1);
    OptimisticLock l = _locks[si];
    long _stamp = l.writeLock();
    try {
      Entry e; Object ek; AtomicReferenceArray<Entry<K,V>> tab = entries;
      if (tab == null) {
        throw new CacheClosedException();
      }
      int n = tab.length(), _mask = n - 1, idx = _hash & (_mask);
      e = tab.get(idx);
      while (e != null) {
        if (e.hashCode == _hash && ((ek = e.key) == key || (ek.equals(key)))) {
          return e;
        }
        e = e.another;
      }
      e2.another = tab.get(idx);
      tab.set(idx, e2);
      _size = segmentSize[si].incrementAndGet();

    } finally {
      l.unlockWrite(_stamp);
    }
    if (_size > maxFill) {
      expand();
    }
    return e2;
  }

  public boolean remove(Entry<K,V> e2) {
    int _hash = e2.hashCode;
    OptimisticLock[] _locks = locks;
    int si = _hash & (_locks.length - 1);
    OptimisticLock l = _locks[si];
    long _stamp = l.writeLock();
    try {
      Entry e; AtomicReferenceArray<Entry<K,V>> tab = entries;
      if (tab == null) {
        throw new CacheClosedException();
      }
      int n = tab.length(), _mask = n - 1, idx = _hash & (_mask);
      e = tab.get(idx);
      if (e == e2) {
        tab.set(idx, e.another);
        segmentSize[si].decrementAndGet();
        return true;
      }
      while (e != null) {
        Entry _another = e.another;
        if (_another == e2) {
          e.another = _another.another;
          segmentSize[si].decrementAndGet();
          return true;
        }
        e = _another;
      }
    } finally {
      l.unlockWrite(_stamp);
    }
    return false;
  }

  public Entry<K,V> remove(Object key, int _hash) {
    OptimisticLock[] _locks = locks;
    int si = _hash & (_locks.length - 1);
    OptimisticLock l = _locks[si];
    long _stamp = l.writeLock();
    try {
      Entry e; Object ek; AtomicReferenceArray<Entry<K,V>> tab = entries;
      if (tab == null) {
        throw new CacheClosedException();
      }
      int n = tab.length(), _mask = n - 1, idx = _hash & (_mask);
      e = tab.get(idx);
      if (e != null && e.hashCode == _hash && ((ek = e.key) == key || (ek.equals(key)))) {
        tab.set(idx, e.another);
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

  private void expand() {
    long[] _stamps = lockAll();
    try {
      rehash();
    } finally {
      unlockAll(_stamps);
    }
  }

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

  private void unlockAll(long[] _stamps) {
    OptimisticLock[] _locks = locks;
    int sn = _locks.length;
    for (int i = 0; i < sn; i++) {
      _locks[i].unlockWrite(_stamps[i]);
    }
  }

  private void rehash() {
    maxFill = maxFill * 2;
    AtomicReferenceArray<Entry<K,V>> src = entries;
    int i, sl = src.length(), n = sl * 2, _mask = n - 1, idx;
    AtomicReferenceArray<Entry<K,V>> tab = new AtomicReferenceArray<Entry<K, V>>(n);
    long _count = 0; Entry _next, e;
    for (i = 0; i < sl; i++) {
      e = src.get(i);
      while (e != null) {
        _count++; _next = e.another; idx = e.hashCode & _mask;
        e.another = tab.get(idx); tab.set(idx, e);
        e = _next;
      }
    }
    entries = tab;
  }

  public long getSize() {
    long sum = 0;
    for (AtomicLong al : segmentSize) {
      sum += al.get();
    }
    return sum;
  }

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
    for (int i = 0; i < segmentSize.length; i++) {
      segmentSize[i].set(0);
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
    AtomicReferenceArray<Entry<K,V>> tab = entries;
    int n = tab.length();
    for (int i = 0; i < n; i++) {
      Entry e = tab.get(i);
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

}
