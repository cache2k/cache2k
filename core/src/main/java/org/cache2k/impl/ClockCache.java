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
      e = (Entry) e.prev;
    } while (e != _head);
    return cnt;
  }

  @Override
  protected void initializeHeapCache() {
    super.initializeHeapCache();
    size = 0;
    hand = null;
  }

  @Override
  protected void iterateAllEntriesRemoveAndCancelTimer() {
    Entry e, _head;
    int _count = 0;
    e = _head = hand;
    long _hits = 0;
    if (e != null) {
      do {
        _hits += e.hitCnt;
        e.removedFromList();
        cancelExpiryTimer(e);
        _count++;
        e = (Entry) e.prev;
      } while (e != _head);
      hits += _hits;
    }
  }

  @Override
  protected void removeEntryFromReplacementList(Entry e) {
    hand = removeFromCyclicList(hand, e);
    hits += e.hitCnt;
    size--;
  }

  private int getListSize() {
    return size;
  }

  @Override
  protected void recordHit(Entry e) {
    e.hitCnt++;
  }

  @Override
  protected void insertIntoReplacementList(Entry e) {
    size++;
    hand = insertIntoTailCyclicList(hand, e);
  }

  @Override
  protected Entry newEntry() {
    return new Entry();
  }

  /**
   * Run to evict an entry.
   */
  @Override
  protected Entry findEvictionCandidate() {
    runCnt++;
    int _scanCnt = 0;

    while (hand.hitCnt > 0) {
      _scanCnt++;
      hits += hand.hitCnt;
      hand.hitCnt = 0;
      hand = (Entry) hand.next;
    }
    if (_scanCnt > size) {
      scan24hCnt++;
    }
    scanCnt += _scanCnt;
    return hand;
  }

  @Override
  protected IntegrityState getIntegrityState() {
    synchronized (lock) {
      return super.getIntegrityState()
              .checkEquals("getListSize() + evictedButInHashCnt == getSize()", getListSize() + evictedButInHashCnt, getLocalSize())
              .check("checkCyclicListIntegrity(hand)", checkCyclicListIntegrity(hand))
              .checkEquals("getCyclicListEntryCount(hand) == size", getCyclicListEntryCount(hand), size);
    }
  }

  @Override
  protected String getExtraStatistics() {
    return  ", clockRunCnt=" + runCnt +
            ", scanCnt=" + scanCnt +
            ", scan24hCnt=" + scan24hCnt;
  }

  static class Entry<K, T> extends org.cache2k.impl.Entry<Entry, K, T> {

    int hitCnt;

  }

}
