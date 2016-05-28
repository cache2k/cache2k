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

import java.lang.reflect.Array;

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
public class Hash<E extends Entry> {

  private volatile long clearCount;
  private int maxFill = 0;
  private Object bigLock;
  private Object[] segmentLocks;
  private int[] segmentSize;

  public E[] initSegmented(Class<E> _entryType, Object _bigLock) {
    bigLock = _bigLock;
    int _segmentCount = 8;
    segmentLocks = new Object[_segmentCount];
    for (int i = 0; i < _segmentCount; i++) {
      segmentLocks[i] = new Object();
    }
    segmentSize = new int[_segmentCount];
    return initArray(_entryType);
  }

  public E[] initSegmented(Class<E> _entryType, Object _bigLock, Hash<E> _takeOverLocks) {
    bigLock = _bigLock;
    int _segmentCount = _takeOverLocks.segmentLocks.length;
    segmentLocks = _takeOverLocks.segmentLocks;
    segmentSize = new int[_segmentCount];
    return initArray(_entryType);
  }

  public E[] initUseBigLock(Class<E> _entryType, Object _bigLock) {
    bigLock = _bigLock;
    int _segmentCount = 1;
    segmentLocks = new Object[_segmentCount];
    for (int i = 0; i < _segmentCount; i++) {
      segmentLocks[i] = _bigLock;
    }
    segmentSize = new int[_segmentCount];
    return initArray(_entryType);
  }

  public E[] initSingleThreaded(Class<E> _entryType) {
    int _segmentCount = 1;
    segmentSize = new int[_segmentCount];
    return initArray(_entryType);
  }

  private E[] initArray(Class<E> _entryType) {
    maxFill =
      HeapCache.TUNABLE.initialHashSize * HeapCache.TUNABLE.hashLoadPercent /
        100 / segmentSize.length;
    if (maxFill == 0) {
      throw new IllegalArgumentException("values for hash size or load factor too low.");
    }
    return (E[]) Array.newInstance(_entryType, HeapCache.TUNABLE.initialHashSize);
  }

  public E[] clear(Class<E> _entryType) {
    for (int i = 0; i < segmentSize.length; i++) {
      segmentSize[i] = 0;
    }
    clearCount++;
    return initArray(_entryType);
  }

  public Object segmentLock(int _hashCode) {
    Object obj = segmentLocks[segmentIndex(_hashCode)];
    return obj;
  }

  public boolean everyLockTaken() {
    for (Object o : segmentLocks) {
      if (!Thread.holdsLock(o)) {
        return false;
      }
    }
    return true;
  }

  public boolean anyLockTaken() {
    for (Object o : segmentLocks) {
      if (Thread.holdsLock(o)) {
        return true;
      }
    }
    return false;
  }

  private int segmentIndex(final int _hashCode) {
    return _hashCode & (segmentSize.length - 1);
  }

  public static int index(Entry[] _hashTable, int _hashCode) {
    if (_hashTable == null) {
      throw new CacheClosedException();
    }
    return _hashCode & (_hashTable.length - 1);
  }


  public static <E extends Entry> E lookup(E[] _hashTable, Object key, int _hashCode) {
    Object ek;
    int i = index(_hashTable, _hashCode);
    E e = _hashTable[i];
    while (e != null) {
      if (e.hashCode == _hashCode &&
        (key == (ek = e.key) || key.equals(ek))) {
        return e;
      }
      e = (E) e.another;
    }
    return null;
  }

  public static <E extends Entry> E lookupHashCode(E[] _hashTable, int _hashCode) {
    int i = index(_hashTable, _hashCode);
    E e = _hashTable[i];
    while (e != null) {
      if (e.hashCode == _hashCode) {
        return e;
      }
      e = (E) e.another;
    }
    return null;
  }

  public static boolean contains(Entry[] _hashTable, Object key, int _hashCode) {
    Object ek;
    int i = index(_hashTable, _hashCode);
    Entry e = _hashTable[i];
    while (e != null) {
      if (e.hashCode == _hashCode &&
          (key == (ek = e.key) || key.equals(ek))) {
        return true;
      }
      e = e.another;
    }
    return false;
  }


  private static void insertWoExpand(Entry[] _hashTable, Entry e) {
    int i = index(_hashTable, e.hashCode);
    e.another = _hashTable[i];
    _hashTable[i] = e;
  }

  private void rehash(Entry[] a1, Entry[] a2) {
    long _count = 0;
    for (Entry e : a1) {
      while (e != null) {
        _count++;
        Entry _next = e.another;
        insertWoExpand(a2, e);
        e = _next;
      }
    }
    if (getSize() != _count) {
      throw new InternalError(getSize() + " != " + _count);
    }
  }

  private <E extends Entry> E[] expandHash(E[] _hashTable) {
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
    int hc = _entry.hashCode;
    int idx = segmentIndex(hc);
    int i = index(_hashTable, hc);
    Entry e = _hashTable[i];
    if (e == _entry) {
      _hashTable[i] = e.another;
      segmentSize[idx]--;
      return true;
    }
    while (e != null) {
      Entry _another = e.another;
      if (_another == _entry) {
        e.another = _another.another;
        segmentSize[idx]--;
        return true;
      }
      e = _another;
    }
    return false;
  }

  /**
   * Remove entry from the hash. We never shrink the hash table, so
   * the array keeps identical. After this remove operation the entry
   * object may be inserted in another hash.
   */
  public E remove(E[] _hashTable, Object key, int hc) {
    int idx = segmentIndex(hc);
    int i = index(_hashTable, hc);
    Entry e = _hashTable[i];
    if (e == null) {
      return null;
    }
    if (e.hashCode == hc && key.equals(e.key)) {
      _hashTable[i] = (E) e.another;
      segmentSize[idx]--;
      return (E) e;
    }
    Entry _another = e.another;
    while (_another != null) {
      if (_another.hashCode == hc && key.equals(_another.key)) {
        e.another = _another.another;
        segmentSize[idx]--;
        return (E) _another;
      }
      e = _another;
      _another = _another.another;
    }
    return null;
  }

  /**
   * Remove entry with this hash code from the hash. We never shrink the hash table, so
   * the array keeps identical. After this remove operation the entry
   * object may be inserted in another hash.
   */
  public E removeWithIdenticalHashCode(E[] _hashTable, int hc) {
    int idx = segmentIndex(hc);
    int i = index(_hashTable, hc);
    Entry e = _hashTable[i];
    if (e == null) {
      return null;
    }
    if (e.hashCode == hc) {
      _hashTable[i] = (E) e.another;
      segmentSize[idx]--;
      return (E) e;
    }
    Entry _another = e.another;
    while (_another != null) {
      if (_another.hashCode == hc) {
        e.another = _another.another;
        segmentSize[idx]--;
        return (E) _another;
      }
      e = _another;
      _another = _another.another;
    }
    return null;
  }


  public void insert(final E[] _hashTable, Entry _entry) {
    int idx = segmentIndex(_entry.hashCode);
    segmentSize[idx]++;
    insertWoExpand(_hashTable, _entry);
  }

  public boolean needsExpansion(int hc) {
    int idx = segmentIndex(hc);
    return segmentSize[idx] >= maxFill;
  }

  public E[] expand(final E[] _hashTable, int hc) {
    int idx = segmentIndex(hc);
    if (segmentSize[idx] >= maxFill) {
      maxFill = maxFill * 2;
      return expandHash(_hashTable);
    }
    return _hashTable;
  }

  private <T> T runTotalLocked(Job<T> j, int idx) {
    if (segmentLocks == null) {
      return j.call();
    }
    Object obj = segmentLocks[idx];
    synchronized (obj) {
      idx++;
      if (idx == segmentLocks.length) {
        return j.call();
      } else {
        return runTotalLocked(j, idx);
      }
    }
  }

  public interface Job<T> {
    T call();
  }

  public <T> T runTotalLocked(Job<T> j) {
    return runTotalLocked(j, 0);
  }

  /**
   * Cache was closed. Inform operations/iterators on the hash.
   */
  public void close() { maxFill = Integer.MAX_VALUE - 1; }

  public long getClearCount() {
    return clearCount;
  }

  /**
   * Operations should terminate
   */
  public boolean isCleared() { return maxFill == Integer.MAX_VALUE; }

  /**
   * Operations should terminate and throw a {@link CacheClosedException}
   */
  public boolean isClosed() { return maxFill == Integer.MAX_VALUE - 1; }

  static class CollisionInfo {
    int collisionCnt; int collisionSlotCnt; int longestCollisionSize;
  }

  public int getSize() {
    int cnt = 0;
    for (int i = 0; i < segmentSize.length; i++) {
      cnt += segmentSize[i];
    }
    return cnt;
  }

  public int getMaxFill() {
    return maxFill;
  }

}
