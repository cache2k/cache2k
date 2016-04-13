package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
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

/**
 * Straight forward CLOCK implementation. This is probably the simplest
 * eviction algorithm around. Interestingly it produces good results.
 *
 * @author Jens Wilke; created: 2013-12-01
 */
public class ClockCache<K, V> extends ConcurrentEvictionCache<K, V> {

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

}
