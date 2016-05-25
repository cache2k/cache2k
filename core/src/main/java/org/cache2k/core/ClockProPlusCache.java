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

import org.cache2k.core.util.TunableConstants;
import org.cache2k.core.util.TunableFactory;

/**
 * CLOCK Pro implementation with 3 clocks. Using separate clocks for hot and cold
 * has saves us the extra marker (4 bytes in Java) for an entry to decide whether it is in hot
 * or cold. OTOH we shuffle around the entries in different lists and loose the order they
 * were inserted, which leads to less cache efficiency.
 *
 * <p/>This version uses a static allocation for hot and cold spaces. No online or dynamic
 * optimization is done yet. However, the hitrate for all measured access traces is better
 * then LRU and it is resistant to scans.
 *
 * @author Jens Wilke; created: 2013-07-12
 */
@SuppressWarnings("unchecked")
public class ClockProPlusCache<K, V> extends ConcurrentEvictionCache<K, V> {

  private static final Tunable TUNABLE_CLOCK_PRO = TunableFactory.get(Tunable.class);

  long hotHits;
  long coldHits;
  long ghostHits;

  long hotRunCnt;
  long hot24hCnt;
  long hotScanCnt;
  long coldRunCnt;
  long cold24hCnt;
  long coldScanCnt;

  int coldSize;
  int hotSize;

  /** Maximum size of hot clock. 0 means normal clock behaviour */
  long hotMax;
  long ghostMax;

  Entry handCold;
  Entry handHot;
  Entry handGhost;
  Hash<Entry> ghostHashCtrl;
  Entry[] ghostHash;

  private long sumUpListHits(Entry e) {
    if (e == null) { return 0; }
    long cnt = 0;
    Entry _head = e;
    do {
      cnt += e.hitCnt;
      e = e.next;
    } while (e != _head);
    return cnt;
  }

  @Override
  public long getHitCnt() {
    return hotHits + coldHits + sumUpListHits(handCold) + sumUpListHits(handHot);
  }

  protected void initializeHeapCache() {
    super.initializeHeapCache();
    ghostMax = maxSize / 2 + 1;
    hotMax = maxSize * TUNABLE_CLOCK_PRO.hotMaxPercentage / 100;
    coldSize = 0;
    hotSize = 0;
    handCold = null;
    handHot = null;
    handGhost = null;
    ghostHashCtrl = new Hash<Entry>();
    ghostHash = ghostHashCtrl.initUseBigLock(Entry.class, lock);
  }

  @Override
  protected void iterateAllEntriesAndRemoveFromReplacementList() {
    Entry e, _head;
    int _count = 0;
    e = _head = handCold;
    long _hits = 0;
    if (e != null) {
      do {
        _hits += e.hitCnt;
        Entry _next = e.prev;
        e.removedFromList();
        _count++;
        e = _next;
      } while (e != _head);
      coldHits += _hits;
    }
    e = _head = handHot;
    if (e != null) {
      _hits = 0;
      do {
        _hits += e.hitCnt;
        Entry _next = e.prev;
        e.removedFromList();
        _count++;
        e = _next;
      } while (e != _head);
      hotHits += _hits;
    }
  }


  /**
   * Track the entry on the ghost list and call the usual remove procedure.
   */
  @Override
  protected void evictEntry(final Entry e) {
    insertCopyIntoGhosts(e);
    removeEntryFromReplacementList(e);
  }

  /**
   * Remove, expire or eviction of an entry happens. Remove the entry from the
   * replacement list data structure. We can just remove the entry from the list,
   * but we don't know which list it is in to correct the counters accordingly.
   * So, instead of removing it directly, we just mark it and remove it
   * by the normal eviction process.
   *
   * <p>Why don't generate ghosts here? If the entry is removed because of
   * a programmatic remove or expiry we should not occupy any resources.
   * Removing and expiry may also take place when no eviction is needed at all,
   * which happens when the cache size did not hit the maximum yet. Producing ghosts
   * would add additional overhead, when it is not needed.
   */
  @Override
  protected void removeEntryFromReplacementList(Entry e) {
    if (e.isHot()) {
      hotHits += e.hitCnt;
      handHot = removeFromCyclicList(handHot, e);
      hotSize--;
    } else {
      coldHits += e.hitCnt;
      handCold = removeFromCyclicList(handCold, e);
      coldSize--;
    }
  }

  private void insertCopyIntoGhosts(Entry<K, V> e) {
    Entry<K, V> e2 = new Entry<K, V>();
    e2.key = e.key;
    e2.hashCode = e.hashCode;
    ghostHashCtrl.insert(ghostHash, e2);
    ghostHash = ghostHashCtrl.expand(ghostHash, e.hashCode);
    handGhost = insertIntoTailCyclicList(handGhost, e2);
    if (ghostHashCtrl.getSize() > ghostMax) {
      runHandGhost();
    }
  }

  private int getListSize() {
    return hotSize + coldSize;
  }

  @Override
  protected void recordHit(Entry e) {
    long _hitCnt = e.hitCnt + 1;
    if ((_hitCnt & 0x100000000L) != 0 ) {
      scrubCache(e);
      recordHit(e);
      return;
    }
    e.hitCnt = _hitCnt;
  }

  private long scrubCounters(Entry e) {
    if (e == null) { return 0; }
    int cnt = 0;
    Entry _head = e;
    do {
      long _hitCnt = e.hitCnt;
      long _hitCnt2 = e.hitCnt = e.hitCnt >> 1;
      cnt += _hitCnt - _hitCnt2;
      e = e.next;
    } while (e != _head);
    return cnt;
  }

  protected void scrubCache(Entry e) {
    synchronized (lock) {
      checkClosed();
      if (e.hitCnt != Integer.MAX_VALUE) {
        return;
      }
      coldHits += scrubCounters(handCold);
      hotHits += scrubCounters(handHot);
    }
  }

  @Override
  protected void insertIntoReplacementList(Entry e) {
    coldSize++;
    handCold = insertIntoTailCyclicList(handCold, e);
  }

  @Override
  protected Entry newEntry() {
    return new Entry();
  }

  protected Entry<K, V> runHandHot() {
    hotRunCnt++;
    Entry<K, V> _handStart = handHot;
    Entry<K, V> _hand = _handStart;
    Entry<K, V> _coldCandidate = _hand;
    long _lowestHits = Long.MAX_VALUE;
    long _hotHits = hotHits;
    int _scanCnt = -1;
    long _decrease = ((_hand.hitCnt + _hand.next.hitCnt) >> TUNABLE_CLOCK_PRO.hitCounterDecreaseShift) + 1;
    do {
      _scanCnt++;
      long _hitCnt = _hand.hitCnt;
      if (_hitCnt < _lowestHits) {
        _lowestHits = _hitCnt;
        _coldCandidate = _hand;
        if (_hitCnt == 0) {
          break;
        }
      }
      if (_hitCnt < _decrease) {
        _hand.hitCnt = 0;
        _hotHits += _hitCnt;
      } else {
        _hand.hitCnt = _hitCnt - _decrease;
        _hotHits += _decrease;
      }
      _hand = _hand.next;
    } while (_hand != _handStart);
    hotHits = _hotHits;
    hotScanCnt += _scanCnt;
    if (_scanCnt == hotMax ) {
      hot24hCnt++; // count a full clock cycle
    }
    handHot = removeFromCyclicList(_hand, _coldCandidate);
    hotSize--;
    _coldCandidate.setHot(false);
    return _coldCandidate;
  }

  /**
   * Runs cold hand an in turn hot hand to find eviction candidate.
   */
  @Override
  protected Entry findEvictionCandidate() {
    coldRunCnt++;
    Entry _hand = handCold;
    int _scanCnt = 0;
    if (_hand == null) {
      _hand = refillFromHot(_hand);
    }
    if (_hand.hitCnt > 0) {
      _hand = refillFromHot(_hand);
      do {
        _scanCnt++;
        coldHits += _hand.hitCnt;
        _hand.hitCnt = 0;
        Entry e = _hand;
        _hand = removeFromCyclicList(e);
        coldSize--;
        e.setHot(true);
        hotSize++;
        handHot = insertIntoTailCyclicList(handHot, e);
      } while (_hand != null && _hand.hitCnt > 0);
    }

    if (_hand == null) {
      _hand = refillFromHot(_hand);
    }
    if (_scanCnt > this.coldSize) {
      cold24hCnt++;
    }
    coldScanCnt += _scanCnt;
    handCold = _hand;
    return _hand;
  }

  private Entry refillFromHot(Entry _hand) {
    while (hotSize > hotMax || _hand == null) {
      Entry e = runHandHot();
      if (e != null) {
        _hand = insertIntoTailCyclicList(_hand, e);
        coldSize++;
      }
    }
    return _hand;
  }

  protected void runHandGhost() {
    Entry e = handGhost;
    boolean f = ghostHashCtrl.remove(ghostHash, e);
    handGhost = removeFromCyclicList(e);
  }

  @Override
  protected Entry checkForGhost(K key, int hc) {
    Entry e = ghostHashCtrl.remove(ghostHash, key, hc);
    if (e != null) {
      handGhost = removeFromCyclicList(handGhost, e);
      ghostHits++;
      e.setHot(true);
      hotSize++;
      handHot = insertIntoTailCyclicList(handHot, e);
    }
    return e;
  }

  @Override
  protected IntegrityState getIntegrityState() {
    return super.getIntegrityState()
            .checkEquals("ghostHashCtrl.size == Hash.calcEntryCount(refreshHash)",
                    ghostHashCtrl.getSize(), Hash.calcEntryCount(ghostHash))
            .check("hotMax <= maxElements", hotMax <= maxSize)
            .checkEquals("getListSize() == getSize()", (getListSize()) , getLocalSize())
            .check("checkCyclicListIntegrity(handHot)", checkCyclicListIntegrity(handHot))
            .check("checkCyclicListIntegrity(handCold)", checkCyclicListIntegrity(handCold))
            .check("checkCyclicListIntegrity(handGhost)", checkCyclicListIntegrity(handGhost))
            .checkEquals("getCyclicListEntryCount(handHot) == hotSize", getCyclicListEntryCount(handHot), hotSize)
            .checkEquals("getCyclicListEntryCount(handCold) == coldSize", getCyclicListEntryCount(handCold), coldSize)
            .checkEquals("getCyclicListEntryCount(handGhost) == ghostSize", getCyclicListEntryCount(handGhost), ghostHashCtrl.getSize());
  }

  @Override
  protected String getExtraStatistics() {
    return ", coldSize=" + coldSize +
           ", hotSize=" + hotSize +
           ", hotMaxSize=" + hotMax +
           ", ghostSize=" + ghostHashCtrl.getSize() +
           ", coldHits=" + (coldHits + sumUpListHits(handCold)) +
           ", hotHits=" + (hotHits + sumUpListHits(handHot)) +
           ", ghostHits=" + ghostHits +
           ", coldRunCnt=" + coldRunCnt +// identical to the evictions anyways
           ", coldScanCnt=" + coldScanCnt +
           ", cold24hCnt=" + cold24hCnt +
           ", hotRunCnt=" + hotRunCnt +
           ", hotScanCnt=" + hotScanCnt +
           ", hot24hCnt=" + hot24hCnt;
  }

  public static class Tunable extends TunableConstants {

    int hotMaxPercentage = 97;

    int hitCounterDecreaseShift = 6;

  }

}
