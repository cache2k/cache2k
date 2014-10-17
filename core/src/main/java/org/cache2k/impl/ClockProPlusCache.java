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
 * Clock pro implementation
 *
 * @author Jens Wilke; created: 2013-07-12
 */
@SuppressWarnings("unchecked")
public class ClockProPlusCache<K, T> extends LockFreeCache<ClockProPlusCache.Entry, K, T> {

  long hotHits;
  long coldHits;
  long ghostHits;

  int hotRunCnt;
  int hot24hCnt;
  int hotScanCnt;

  long hotSizeSum;
  int coldRunCnt;
  int cold24hCnt;
  int coldScanCnt;

  int coldSize;
  int hotSize;
  int staleSize;

  /** Maximum size of hot clock. 0 means normal clock behaviour */
  int hotMax;
  int ghostMax;
  boolean hotMaxFix = false;

  Entry handCold;
  Entry handHot;
  Entry handGhost;
  Hash<Entry> ghostHashCtrl;
  Entry[] ghostHash;

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
  public long getHitCnt() {
    return hotHits + coldHits + countAllHits();
  }

  @Override
  protected void initializeHeapCache() {
    super.initializeHeapCache();
    ghostMax = maxSize;
    hotMax = maxSize * 50 / 100;
    hotMaxFix = false;
    coldSize = 0;
    hotSize = 0;
    staleSize = 0;
    handCold = null;
    handHot = null;
    handGhost = null;
    ghostHashCtrl = new Hash<Entry>();
    ghostHash = ghostHashCtrl.init(Entry.class);
  }

  /**
   * We are called e.g. from the timer event to remove the entry.
   * Eviction does not call this entry, because the findCandidate evicts
   * directly.
   *
   * <p>The entry may be in the cold or the hot clock. For removing it
   * we need to know in what clock it resided, to decrement the size counter.
   *
   */
  @Override
  protected void removeEntryFromReplacementList(Entry e) {
    staleSize++;
    e.setStale();
    insertCopyIntoGhosts(e);
  }

  private void insertCopyIntoGhosts(Entry e) {
    Entry<K,T> e2 = new Entry<K, T>();
    e2.key = (K) e.key;
    e2.hashCode = e.hashCode;
    ghostHash = ghostHashCtrl.insert(ghostHash, e2);
    handGhost = insertIntoTailCyclicList(handGhost, e2);
    if (ghostHashCtrl.size > maxSize - hotMax) {
      runHandGhost();
    }
  }

  private int getListSize() {
    return hotSize + coldSize - staleSize;
  }

  @Override
  protected void recordHit(Entry e) {
    e.hitCnt++;
  }

  @Override
  protected void insertIntoReplcamentList(Entry e) {
    coldSize++;
    handCold = insertIntoTailCyclicList(handCold, e);
  }

  @Override
  protected Entry newEntry() {
    return new Entry();
  }





  protected Entry<K,T> runHandHot() {
    if (handHot == null) {
      handHot = handCold;
    }
    hotRunCnt++;

    Entry<K,T> _handStart = handHot;
    Entry<K,T> _hand = _handStart;
    Entry<K,T> _coldCandidate = _hand;
    int _hits = Integer.MAX_VALUE;
    long _hotHits = hotHits;
    int _scanCnt = 0;
    do {
      if (_hand.hot) {
        _scanCnt++;
        int _hitCnt = _hand.hitCnt;
        if (_hitCnt < _hits) {
          _hits = _hitCnt;
          _coldCandidate = _hand;
          if (_hits == 0) {
            break;
          }
        }
        int _decrease = ((_hits>>4) + 1);
        _hand.hitCnt -= _decrease;
        _hotHits += _decrease;
      }
      _hand = _hand.next;
    } while (_hand != _handStart && _hits > 0);
    _hotHits += _coldCandidate.hitCnt;
    _coldCandidate.hitCnt = 0;
    hotHits = _hotHits;
    hotScanCnt += _scanCnt;
    if (_hand == _handStart) {
      hot24hCnt++; // count a full clock cycle
    }
    handHot = _hand;
    _coldCandidate.hot = false;
    hotSize--;
    return _coldCandidate;
  }

  private void decreaseColdSpace() {
    if (hotMaxFix) { return; }
    hotMax = Math.min(maxSize - 1, hotMax + 1);
  }

  private void increaseColdSpace() {
    if (hotMaxFix) { return; }
    hotMax = Math.max(0, hotMax - 1);
  }

  /**
   * Run hand cold.
   * Promote reference cold to hot or unreferenced test to cold
   */
  protected Entry findEvictionCandidate() {
    hotSizeSum += hotMax;
    coldRunCnt++;
    Entry<K,T> _hand = handCold;
    int _scanCnt = 1;
    int _coldSize = coldSize;
    Entry<K, T> _evictedEntry;
    do {
      if (!_hand.hot && _hand.hitCnt == 0) {
        decreaseColdSpace();
      }
      while (true) {
        while (_hand.hot) {
          _hand = _hand.next;
        }
        if (_hand.hitCnt == 0) {
          break;
        }
        increaseColdSpace();
        _scanCnt++;
        coldHits += _hand.hitCnt;
        _hand.hitCnt = 0;
        _hand.hot = true;
        hotSize++;
        _coldSize--;
        runHandHot();
        _coldSize++;
        _hand = _hand.next;
      }

      while (hotSize > hotMax) {
        runHandHot();
        _coldSize++;
      }

      _evictedEntry = _hand;
      _hand = (Entry<K,T>) removeFromCyclicList(_hand);
      if (handHot == _evictedEntry) {
        handHot = _hand;
      }
      _coldSize--;

      if (!_evictedEntry.isStale()) {
        break;
      }
      staleSize--;
      _scanCnt--;
    } while(true);
    insertCopyIntoGhosts(_evictedEntry);
    if (_scanCnt > coldSize) {
      cold24hCnt++;
    }
    while (hotSize > hotMax) {
      runHandHot();
      _coldSize++;
    }
    coldScanCnt += _scanCnt;
    coldSize = _coldSize;
    handCold = _hand;
    return _evictedEntry;
  }

  protected void runHandGhost() {
    boolean f = ghostHashCtrl.remove(ghostHash, handGhost);
    handGhost = (Entry) removeFromCyclicList(handGhost);
  }

  @Override
  protected Entry checkForGhost(K key, int hc) {
    Entry e = ghostHashCtrl.remove(ghostHash, key, hc);
    if (e != null) {
      handGhost = removeFromCyclicList(handGhost, e);
      ghostHits++;
      ghostHit(e);
    }
    return e;
  }

  private void ghostHit(Entry e) {

    increaseColdSpace();
    handCold = insertIntoTailCyclicList(handCold, e);
    e.hot = true;
    hotSize++;
    while (hotSize > hotMax) {
      runHandHot();
      coldSize++;
    }
  }

  int countHotCold(boolean _status) {
    if (handCold == null) {
      return 0;
    }
    int cnt = 0;
    Entry<K, T> e = handCold;
    do {
      if (e.hot == _status) {
        cnt++;
      }
      e = e.next;
    } while (e != handCold);
    return cnt;
  }

  long countHotColdHits(boolean _status) {
    if (handCold == null) {
      return 0;
    }
    long cnt = 0;
    Entry<K, T> e = handCold;
    do {
      if (e.hot == _status) {
        cnt += e.hitCnt;
      }
      e = e.next;
    } while (e != handCold);
    return cnt;
  }

  long countAllHits() {
    if (handCold == null) {
      return 0;
    }
    long cnt = 0;
    Entry<K, T> e = handCold;
    do {
      cnt += e.hitCnt;
      e = e.next;
    } while (e != handCold);
    return cnt;
  }

  @Override
  protected IntegrityState getIntegrityState() {
    synchronized (lock) {
      return super.getIntegrityState()
              .checkEquals("ghostHashCtrl.size == Hash.calcEntryCount(refreshHash)",
                      ghostHashCtrl.size, Hash.calcEntryCount(ghostHash))
              .check("hotMax <= maxElements", hotMax <= maxSize)
              .check("hotSize <= hotMax", hotSize <= hotMax)
              .checkEquals("getSize() == (getListSize() + evictedButInHashCnt) ", getLocalSize(),  getListSize() + evictedButInHashCnt)
              .check("checkCyclicListIntegrity(handHot)", checkCyclicListIntegrity(handHot))
              .check("checkCyclicListIntegrity(handCold)", checkCyclicListIntegrity(handCold))
              .check("checkCyclicListIntegrity(handGhost)", checkCyclicListIntegrity(handGhost))
              .checkEquals("listEntries == coldSize", countHotCold(false), coldSize)
              .checkEquals("listEntries == hotSize", countHotCold(true), hotSize)
              .checkEquals("getCyclicListEntryCount(handGhost) == ghostSize", getCyclicListEntryCount(handGhost), ghostHashCtrl.size);
    }
  }

  @Override
  protected String getExtraStatistics() {
    return ", coldSize=" + coldSize +
           ", hotSize=" + hotSize +
           ", hotMaxSize=" + hotMax +
           ", hotSizeAvg=" + (coldRunCnt > 0 ? (hotSizeSum / coldRunCnt) : -1) +
           ", ghostSize=" + ghostHashCtrl.size +
           ", staleSize=" + staleSize +
           ", coldHits=" + (coldHits + countHotColdHits(false)) +
           ", hotHits=" + (hotHits + countHotColdHits(true)) +
           ", ghostHits=" + ghostHits +
           ", coldRunCnt=" + coldRunCnt +
           ", coldScanCnt=" + coldScanCnt +
           ", cold24hCnt=" + cold24hCnt +
           ", hotRunCnt=" + hotRunCnt +
           ", hotScanCnt=" + hotScanCnt +
           ", hot24hCnt=" + hot24hCnt;
  }

  static class Entry<K, T> extends BaseCache.Entry<Entry, K, T> {

    int hitCnt;
    boolean hot;

  }

}
