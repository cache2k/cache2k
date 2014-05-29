
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

/**
 * Adaptive Replacement Cache implementation for cache2k. The algorithm itself is patented by IBM, so the
 * implementation will probably never be available or used as default eviction algorithm. This implementation
 * will be removed from the cache2k core package before 1.0.
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
  protected void insertIntoReplcamentList(Entry e) {
    insertInList(t1Head, e);
    t1Size++;
  }

  /** Emtpy, done by replace / checkForGhost. */

  @Override
  protected Entry<K, T> newEntry() {
    return new Entry<>();
  }

  @Override
  protected Entry checkForGhost(K key, int hc) {
    Entry e = b1HashCtrl.remove(b1Hash, key, hc);
    if (e != null) {
      removeEntryFromReplacementList(e);
      b1Hit();
      insertT2(e);
      return e;
    }
    e = b2HashCtrl.remove(b2Hash, key, hc);
    if (e != null) {
      removeEntryFromReplacementList(e);
      b2Hit();
      insertT2(e);
      return e;
    }
    allMiss();
    return null;
  }

  @Override
  protected void removeEntry(Entry e) {
    super.removeEntry(e);
    if (!e.withinT2) {
      t1Size--;
    }
    e.withinT2 = false;
  }

  private void b1Hit() {
    int _b1Size = b1HashCtrl.size + 1;
    int _b2Size = b2HashCtrl.size;
    int _delta = _b1Size >= _b2Size ? 1 : _b2Size / _b1Size;
    arcP = Math.min(arcP + _delta, maxSize);
    replace();
  }

  private void b2Hit() {
    int _b1Size = b1HashCtrl.size;
    int _b2Size = b2HashCtrl.size + 1;
    int _delta = _b2Size >= _b1Size ? 1 : _b1Size / _b2Size;
    arcP = Math.max(arcP - _delta, 0);
    replaceB2Hit();
  }

  private void allMiss() {
    if ((t1Size + b1HashCtrl.size) == maxSize) {
      if (t1Size < maxSize) {
        Entry e = b1Head.prev;
        removeEntryFromReplacementList(e);
        boolean f = b1HashCtrl.remove(b1Hash, e);
        replace();
      } else {
        removeEntry(t1Head.prev);
        evictedCnt++;
      }
    } else {
      int _totalCnt = getSize() + b1HashCtrl.size + b2HashCtrl.size;
      if (_totalCnt >= maxSize) {
        if (_totalCnt == maxSize * 2) {
          Entry e = b2Head.prev;
          removeEntry(e);
          boolean f = b2HashCtrl.remove(b2Hash, e);
        }
        replace();
      }
    }
  }

  private void insertT2(Entry<K, T> e) {
    e.withinT2 = true;
    insertInList(t2Head, e);
  }

  int getT2Size() {
    return getSize() - t1Size;
  }

  Entry cloneGhost(Entry e) {
    Entry e2 = new Entry();
    e2.hashCode = e.hashCode;
    e2.key = e.key;
    return e2;
  }

  private void replace(boolean _fromT1) {
    if (_fromT1) {
      Entry<K,T> e = t1Head.prev;
      removeEntry(e);
      e = cloneGhost(e);
      insertInList(b1Head, e);
      b1Hash = b1HashCtrl.insert(b1Hash, e);
    } else {
      Entry<K,T> e = t2Head.prev;
      removeEntry(e);
      e = cloneGhost(e);
      insertInList(b2Head, e);
      b2Hash = b2HashCtrl.insert(b2Hash, e);
    }
    evictedCnt++;
  }

  private void replace() {
    replace((t1Size > arcP) || getT2Size() == 0);
  }

  private void replaceB2Hit() {
    replace((t1Size >= arcP && t1Size > 0) || getT2Size() == 0);
  }

  @Override
  public void clear() {
    synchronized (lock) {
      super.clear();
      t1Size = 0;
      b1HashCtrl = new Hash<>();
      b2HashCtrl = new Hash<>();
      b1Hash = b1HashCtrl.init(Entry.class);
      b2Hash = b2HashCtrl.init(Entry.class);
      t1Head = new Entry<K,T>().shortCircuit();
      t2Head = new Entry<K,T>().shortCircuit();
      b1Head = new Entry<K,T>().shortCircuit();
      b2Head = new Entry<K,T>().shortCircuit();
    }
  }

  final int getListEntryCount() {
    return getListEntryCount(t1Head) + getListEntryCount(t2Head);
  }

  @Override
  protected String getExtraStatistics() {
    return  ", arcP=" + arcP + ", "
          + "t1Size=" + t1Size + ", "
          + "t2Size=" + (mainHashCtrl.size - t1Size);
  }

  @Override
  protected IntegrityState getIntegrityState() {
    return super.getIntegrityState()
      .check("getSize() == getHashEntryCount()", getSize() == getHashEntryCount())
      .check("getSize() == getListEntryCount()", getSize() == getListEntryCount())
      .check("t1Size == getListEntryCount(t1Head)", t1Size == getListEntryCount(t1Head))
      .check("getSize() - t1Size == getListEntryCount(t2Head)", getSize() - t1Size == getListEntryCount(t2Head))
      .check("getSize() + b1HashCtrl.size + b2HashCtrl.size <= maxElements * 2", getSize() + b1HashCtrl.size + b2HashCtrl.size <= maxSize * 2)
      .check("b1HashCtrl.size == getListEntryCount(b1Head)", b1HashCtrl.size == getListEntryCount(b1Head))
      .check("b2HashCtrl.size == getListEntryCount(b2Head)", b2HashCtrl.size == getListEntryCount(b2Head));
  }

  /** An entry in the hash table */
  protected static class Entry<K,T> extends BaseCache.Entry<Entry<K,T>, K, T> {

    boolean withinT2;

  }

}
