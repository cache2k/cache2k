
package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

/**
 * Adaptive Replacement Cache implementation for cache2k. The algorithm itself is patented by IBM, so the
 * implementation will probably never be available or used as default eviction algorithm. This implementation
 * may be removed from the cache2k core package before 1.0.
 *
 * <p/>The implemented algorithm changed from cache2k version 0.19 to 0.20. Within version 0.20
 * the eviction is separated from the general cache access, because of the consequent entry locking
 * scheme an entry that is accessed, is always present in the cache. This means, the eviction is
 * postponed to the end of the cache operation. The ARC algorithm was augmented to reflect this.
 * This means for a cache operation that a newly inserted cache entry is also a candidate
 * for eviction at the end of the operation.
 *
 * @see <a href="http://www.usenix.org/event/fast03/tech/full_papers/megiddo/megiddo.pdf‎">A Self-Tuning, Low Overhead Replacement Cache</a>
 * @see <a href="http://en.wikipedia.org/wiki/Adaptive_replacement_cache">Wikipedia: Adaptive Replacement Cache</a>
 *
 * @author Jens Wilke
 */
@SuppressWarnings("unchecked")
public class ArcCache<K, T> extends BaseCache<ArcCache.Entry, K, T> {

  int arcP = 0;

  Hash<Entry> b1HashCtrl;
  Entry[] b1Hash;

  Hash<Entry> b2HashCtrl;
  Entry[] b2Hash;

  /** Statistics */
  long t2Hit;
  long t1Hit;

  int t1Size = 0;
  Entry<K,T> t2Head;
  Entry<K,T> t1Head;
  Entry<K,T> b1Head;
  Entry<K,T> b2Head;

  boolean b2HitPreferenceForEviction;

  @Override
  public long getHitCnt() {
    return t2Hit + t1Hit;
  }

  @Override
  protected void recordHit(Entry e) {
    moveToFront(t2Head, e);
    if (e.withinT2) {
      t2Hit++;
    } else {
      e.withinT2 = true;
      t1Hit++;
      t1Size--;
    }
  }

  @Override
  protected void insertIntoReplacementList(Entry e) {
    insertInList(t1Head, e);
    t1Size++;
  }

  /** Emtpy, done by replace / checkForGhost. */

  @Override
  protected Entry<K, T> newEntry() {
    return new Entry<K, T>();
  }

  @Override
  protected Entry checkForGhost(K key, int hc) {
    Entry e = b1HashCtrl.remove(b1Hash, key, hc);
    if (e != null) {
      removeFromList(e);
      b1HitAdaption();
      insertT2(e);
      return e;
    }
    e = b2HashCtrl.remove(b2Hash, key, hc);
    if (e != null) {
      removeFromList(e);
      b2HitAdaption();
      insertT2(e);
      return e;
    }
    allMissEvictGhosts();
    return null;
  }

  @Override
  protected void removeEntryFromReplacementList(Entry e) {
    if (!e.withinT2) {
      t1Size--;
    }
    super.removeEntryFromReplacementList(e);
    e.withinT2 = false;
  }

  private void b1HitAdaption() {
    int _b1Size = b1HashCtrl.size + 1;
    int _b2Size = b2HashCtrl.size;
    int _delta = _b1Size >= _b2Size ? 1 : _b2Size / _b1Size;
    arcP = Math.min(arcP + _delta, maxSize);
    b2HitPreferenceForEviction = false;
  }

  private void b2HitAdaption() {
    int _b1Size = b1HashCtrl.size;
    int _b2Size = b2HashCtrl.size + 1;
    int _delta = _b2Size >= _b1Size ? 1 : _b1Size / _b2Size;
    arcP = Math.max(arcP - _delta, 0);
    b2HitPreferenceForEviction = true;
  }

  private void insertT2(Entry<K, T> e) {
    e.withinT2 = true;
    insertInList(t2Head, e);
  }

  int getT2Size() {
    return getLocalSize() - t1Size;
  }

  Entry cloneGhost(Entry e) {
    Entry e2 = new Entry();
    e2.hashCode = e.hashCode;
    e2.key = e.key;
    return e2;
  }

  /**
   * Called when no entry was hit within b1 or b2. This checks whether we need to
   * remove some entries from the b1 and b2 lists.
   */
  private void allMissEvictGhosts() {
    if ((t1Size + b1HashCtrl.size) >= maxSize) {
      if (b1HashCtrl.size > 0) {
        Entry e = b1Head.prev;
        removeFromList(e);
        boolean f = b1HashCtrl.remove(b1Hash, e);
      } else {
        if (b2HashCtrl.size >= maxSize) {
          Entry e = b2Head.prev;
          removeFromList(e);
          boolean f = b2HashCtrl.remove(b2Hash, e);
        }
      }
    } else {
      int _totalCnt = b1HashCtrl.size + b2HashCtrl.size;
      if (_totalCnt >= maxSize) {
        if (b2HashCtrl.size == 0) {
          Entry e = b1Head.prev;
          removeFromList(e);
          boolean f = b1HashCtrl.remove(b1Hash, e);
          return;
        }
        Entry e = b2Head.prev;
        removeFromList(e);
        boolean f = b2HashCtrl.remove(b2Hash, e);
      }
    }
  }

  @Override
  protected Entry findEvictionCandidate() {
    Entry e;
    if (b2HitPreferenceForEviction) {
      e = replaceB2Hit();
    } else {
      e = replace();
    }
    if (b1HashCtrl.size + b2HashCtrl.size > maxSize) {
      allMissEvictGhosts();
    }
    return e;
  }

  private Entry replace() {
    return replace((t1Size > arcP) || getT2Size() == 0);
  }

  private Entry replaceB2Hit() {
    return replace((t1Size >= arcP && t1Size > 0) || getT2Size() == 0);
  }

  private Entry<K,T> replace(boolean _fromT1) {
    Entry<K,T> e;
    if (_fromT1) {
      e = t1Head.prev;
      Entry<K,T> e2 = cloneGhost(e);
      insertInList(b1Head, e2);
      b1Hash = b1HashCtrl.insert(b1Hash, e2);
    } else {
      e = t2Head.prev;
      Entry<K,T> e2 = cloneGhost(e);
      insertInList(b2Head, e2);
      b2Hash = b2HashCtrl.insert(b2Hash, e2);
    }
    return e;
  }

  @Override
  protected void initializeHeapCache() {
    super.initializeHeapCache();
    t1Size = 0;
    b1HashCtrl = new Hash<Entry>();
    b2HashCtrl = new Hash<Entry>();
    b1Hash = b1HashCtrl.init(Entry.class);
    b2Hash = b2HashCtrl.init(Entry.class);
    t1Head = new Entry<K,T>().shortCircuit();
    t2Head = new Entry<K,T>().shortCircuit();
    b1Head = new Entry<K,T>().shortCircuit();
    b2Head = new Entry<K,T>().shortCircuit();
  }

  final int getListEntryCount() {
    return getListEntryCount(t1Head) + getListEntryCount(t2Head);
  }

  @Override
  protected String getExtraStatistics() {
    return  ", arcP=" + arcP + ", "
          + "t1Size=" + t1Size + ", "
          + "t2Size=" + (mainHashCtrl.size - t1Size) + ", "
          + "b1Size=" + b1HashCtrl.size + ", "
          + "b2Size=" + b2HashCtrl.size ;
  }

  @Override
  protected IntegrityState getIntegrityState() {
    return super.getIntegrityState()
      .check("getSize() == getHashEntryCount()", getLocalSize() == calculateHashEntryCount())
      .check("getSize() == getListEntryCount()", getLocalSize() == getListEntryCount())
      .checkEquals("t1Size == getListEntryCount(t1Head)", t1Size, getListEntryCount(t1Head))
      .checkEquals("getSize() - t1Size == getListEntryCount(t2Head)", getLocalSize() - t1Size, getListEntryCount(t2Head))
      .checkLessOrEquals(
        "b1HashCtrl.size + b2HashCtrl.size <= maxSize",
        b1HashCtrl.size + b2HashCtrl.size, maxSize)
      .checkEquals("b1HashCtrl.size == getListEntryCount(b1Head)", b1HashCtrl.size, getListEntryCount(b1Head))
      .checkEquals("b2HashCtrl.size == getListEntryCount(b2Head)", b2HashCtrl.size, getListEntryCount(b2Head));
  }

  /** An entry in the hash table */
  protected static class Entry<K,T> extends org.cache2k.impl.Entry<Entry<K,T>, K, T> {

    boolean withinT2;

  }

}
