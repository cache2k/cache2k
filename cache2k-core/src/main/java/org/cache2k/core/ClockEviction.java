package org.cache2k.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2017 headissue GmbH, Munich
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
 * @author Jens Wilke
 */
public class ClockEviction extends AbstractEviction {

  private long hits;
  private long runCnt;
  private long scan24hCnt;
  private long scanCnt;
  private long size = 0;
  private Entry hand = null;

  public ClockEviction(final HeapCache _heapCache, final HeapCacheListener _listener, final long _maxSize) {
    super(_heapCache, _listener, _maxSize);
  }

  @Override
  public long getHitCount() {
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
  public long removeAll() {
    Entry e, _head;
    int _count = 0;
    e = _head = hand;
    long _hits = 0;
    if (e != null) {
      do {
        _hits += e.hitCnt;
        Entry _next = e.prev;
        e.removedFromList();
        _count++;
        e = _next;
      } while (e != _head);
      hits += _hits;
    }
    return _count;
  }

  @Override
  protected void removeFromReplacementList(final Entry e) {
    hand = Entry.removeFromCyclicList(hand, e);
    hits += e.hitCnt;
    size--;
  }

  public long getSize() {
    return size;
  }

  @Override
  protected void insertIntoReplacementList(Entry e) {
    size++;
    hand = Entry.insertIntoTailCyclicList(hand, e);
  }

  /**
   * Run to evict an entry.
   */
  @Override
  protected Entry findEvictionCandidate(Entry _previous) {
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
  public void checkIntegrity(IntegrityState _integrityState) {
    _integrityState
      .check("checkCyclicListIntegrity(hand)", Entry.checkCyclicListIntegrity(hand))
      .checkEquals("getCyclicListEntryCount(hand) == size", Entry.getCyclicListEntryCount(hand), size);
  }

  @Override
  public String getExtraStatistics() {
    return  ", clockRunCnt=" + runCnt +
      ", scanCnt=" + scanCnt +
      ", scan24hCnt=" + scan24hCnt;
  }

}
