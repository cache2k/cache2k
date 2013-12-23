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

/**
 * Straight forward CLOCK implementation. This is probably the simplest
 * eviction algorithm around. Interestingly it produces good results.
 *
 * @author Jens Wilke; created: 2013-12-01
 */
public class ClockCache<K, T> extends LockFreeCache<ClockCache.Entry, K, T> {

  long hits;
  int runCnt;
  int scan24hCnt;
  int scanCnt;
  int size;

  Entry hand;

  @Override
  public long getHitCnt() {
    return hits + sumUpListHits(hand);
  }

  private int sumUpListHits(Entry e) {
    if (e == null) { return 0; }
    int cnt = 0;
    Entry _head = e;
    do {
      cnt += e.hitCnt;
      e = (Entry) e.next;
    } while (e != _head);
    return cnt;
  }

  @Override
  public void clear() {
    synchronized (lock) {
      super.clear();
      size = 0;
      hand = null;
    }
  }

  @Override
  protected void removeEntryFromReplacementList(Entry e) {
    hand = removeFromCyclicList(hand, e);
  }

  private int getListSize() {
    return size;
  }

  @Override
  protected void recordHit(Entry e) {
    e.hitCnt++;
  }

  @Override
  protected void insertIntoReplcamentList(Entry e) {
    size++;
    hand = insertIntoTailCyclicList(hand, e);
  }

  @Override
  protected Entry newEntry() {
    return new Entry();
  }

  @Override
  protected void evictEntry() {
    runHand();
  }

  /**
   * Run to evict an entry.
   */
  protected void runHand() {
    runCnt++;
    int _scanCnt = 1;
    Entry _evictedEntry;
    while (hand.hitCnt > 0) {
      _scanCnt++;
      hits += hand.hitCnt;
      hand.hitCnt = 0;
      hand = (Entry) hand.next;
    }
    if (_scanCnt > size) {
      scan24hCnt++;
    }
    _evictedEntry = hand;
    hand = (Entry) removeFromCyclicList(hand);
    size--;
    scanCnt += _scanCnt;
    removeEntryFromCache(_evictedEntry);
  }

  @Override
  protected IntegrityState getIntegrityState() {
    synchronized (lock) {
      return super.getIntegrityState()
              .checkEquals("getListSize() == getSize()", (getListSize()), getSize())
              .check("checkCyclicListIntegrity(handCold)", checkCyclicListIntegrity(hand))
              .checkEquals("getCyclicListEntryCount(handCold) == coldSize", getCyclicListEntryCount(hand), size);
    }
  }

  @Override
  protected String getExtraStatistics() {
    return  ", clockRunCnt=" + runCnt +
            ", scanCnt=" + scanCnt +
            ", scan24hCnt=" + scan24hCnt;
  }

  static class Entry<K, T> extends BaseCache.Entry<Entry, K, T> {

    int hitCnt;

  }

}
