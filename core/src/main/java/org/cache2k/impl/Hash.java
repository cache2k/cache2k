package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2015 headissue GmbH, Munich
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
          (key == e.key || key.equals(e.key))) {
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

  public static void calcHashCollisionInfo(BaseCache.CollisionInfo inf, Entry[] _hashTable) {
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
   * the array keeps identical. After this remove operation the entry
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
   * Operations should terminate and throw a {@link CacheClosedException}
   */
  public boolean isClosed() { return size == -2; }

  public boolean shouldAbort() { return size < 0; }

  public E[] init(Class<E> _entryType) {
    size = 0;
    maxFill = BaseCache.TUNABLE.initialHashSize * BaseCache.TUNABLE.hashLoadPercent / 100;
    return (E[]) Array.newInstance(_entryType, BaseCache.TUNABLE.initialHashSize);
  }

}
