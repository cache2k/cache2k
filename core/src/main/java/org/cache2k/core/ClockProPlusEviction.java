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

import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.core.util.TunableConstants;
import org.cache2k.core.util.TunableFactory;

/**
 * CLOCK Pro implementation with 3 clocks.
 *
 * <p/>This version uses a static allocation for hot and cold space sizes. No online or dynamic
 * optimization is done yet. However, the hitrate for all measured access traces is better
 * then LRU and it is resistant to scans.
 *
 * @author Jens Wilke; created: 2013-07-12
 */
@SuppressWarnings("WeakerAccess")
public class ClockProPlusEviction extends AbstractEviction {

  private static final Tunable TUNABLE_CLOCK_PRO = TunableFactory.get(Tunable.class);

  private long hotHits;
  private long coldHits;
  private long ghostHits;

  private long hotRunCnt;
  private long hotScanCnt;
  private long coldRunCnt;
  private long coldScanCnt;

  private int coldSize;
  private int hotSize;

  /** Maximum size of hot clock. 0 means normal clock behaviour */
  private long hotMax;
  private long ghostMax;

  private Entry handCold;
  private Entry handHot;

  private Ghost[] ghosts;
  private Ghost ghostHead = new Ghost().shortCircuit();
  private int ghostSize = 0;
  private static final int GHOST_LOAD_PERCENT = 63;

  public ClockProPlusEviction(final HeapCache _heapCache, final HeapCacheListener _listener, final Cache2kConfiguration cfg, int _segmentsCount) {
    super(_heapCache, _listener, cfg, _segmentsCount);
    ghostMax = maxSize / 2 + 1;
    hotMax = maxSize * TUNABLE_CLOCK_PRO.hotMaxPercentage / 100;
    coldSize = 0;
    hotSize = 0;
    handCold = null;
    handHot = null;
    ghosts = new Ghost[4];
  }

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
  public long getHitCount() {
    return hotHits + coldHits + sumUpListHits(handCold) + sumUpListHits(handHot);
  }

  @Override
  public long removeAll() {
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
    handCold = null;
    coldSize = 0;
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
    handHot = null;
    hotSize = 0;
    return _count;
  }

  /**
   * Track the entry on the ghost list and call the usual remove procedure.
   */
  @Override
  public void evictEntry(final Entry e) {
    insertCopyIntoGhosts(e);
    removeEntryFromReplacementList(e);
  }

  /**
   * Remove, expire or eviction of an entry happens. Remove the entry from the
   * replacement list data structure.
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
      handHot = Entry.removeFromCyclicList(handHot, e);
      hotSize--;
    } else {
      coldHits += e.hitCnt;
      handCold = Entry.removeFromCyclicList(handCold, e);
      coldSize--;
    }
  }

  private void insertCopyIntoGhosts(Entry e) {
    int hc = e.hashCode;
    Ghost g = lookupGhost(hc);
    if (g != null) {
      /*
       * either this is a hash code collision, or a previous ghost hit that was not removed.
       */
      Ghost.moveToFront(ghostHead, g);
      return;
    }
    if (ghostSize >= ghostMax) {
      g = ghostHead.prev;
      Ghost.removeFromList(g);
      boolean f = removeGhost(g, g.hash);
    } else {
      g = new Ghost();
    }
    g.hash = hc;
    insertGhost(g, hc);
    Ghost.insertInList(ghostHead, g);
  }

  public long getSize() {
    return hotSize + coldSize;
  }

  @Override
  protected void insertIntoReplacementList(Entry e) {
    Ghost g = lookupGhost(e.hashCode);
    if (g != null) {
      /*
       * don't remove ghosts here, save object allocations.
       * removeGhost(g, g.hash);  Ghost.removeFromList(g);
       */
      ghostHits++;
      e.setHot(true);
      hotSize++;
      handHot = Entry.insertIntoTailCyclicList(handHot, e);
      return;
    }
    coldSize++;
    handCold = Entry.insertIntoTailCyclicList(handCold, e);
  }

  private Entry runHandHot() {
    hotRunCnt++;
    Entry _hand = handHot;
    Entry _coldCandidate = _hand;
    long _lowestHits = Long.MAX_VALUE;
    long _hotHits = hotHits;
    int _initialMaxScan = hotSize >> 2 + 1;
    int _maxScan = _initialMaxScan;
    long _decrease = ((_hand.hitCnt + _hand.next.hitCnt) >> TUNABLE_CLOCK_PRO.hitCounterDecreaseShift) + 1;
    while (_maxScan-- > 0) {
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
    }
    hotHits = _hotHits;
    hotScanCnt += _initialMaxScan - _maxScan;
    handHot = Entry.removeFromCyclicList(_hand, _coldCandidate);
    hotSize--;
    _coldCandidate.setHot(false);
    return _coldCandidate;
  }

  /**
   * Runs cold hand an in turn hot hand to find eviction candidate.
   */
  @Override
  protected Entry findEvictionCandidate(Entry _previous) {
    coldRunCnt++;
    Entry _hand = handCold;
    int _scanCnt = 1;
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
        _hand = Entry.removeFromCyclicList(e);
        coldSize--;
        e.setHot(true);
        hotSize++;
        handHot = Entry.insertIntoTailCyclicList(handHot, e);
      } while (_hand != null && _hand.hitCnt > 0);
    }

    if (_hand == null) {
      _hand = refillFromHot(_hand);
    }
    coldScanCnt += _scanCnt;
    handCold = _hand.next;
    return _hand;
  }

  private Entry refillFromHot(Entry _hand) {
    while (hotSize > hotMax || _hand == null) {
      Entry e = runHandHot();
      if (e != null) {
        _hand =  Entry.insertIntoTailCyclicList(_hand, e);
        coldSize++;
      }
    }
    return _hand;
  }

  @Override
  public void checkIntegrity(final IntegrityState _integrityState) {
    _integrityState
      .checkEquals("ghostSize == countGhostsInHash()", ghostSize, countGhostsInHash())
      .check("hotMax <= maxElements", hotMax <= maxSize)
      .check("checkCyclicListIntegrity(handHot)", Entry.checkCyclicListIntegrity(handHot))
      .check("checkCyclicListIntegrity(handCold)", Entry.checkCyclicListIntegrity(handCold))
      .checkEquals("getCyclicListEntryCount(handHot) == hotSize", Entry.getCyclicListEntryCount(handHot), hotSize)
      .checkEquals("getCyclicListEntryCount(handCold) == coldSize", Entry.getCyclicListEntryCount(handCold), coldSize)
      .checkEquals("Ghost.listSize(ghostHead) == ghostSize", Ghost.listSize(ghostHead), ghostSize);
  }

  @Override
  public String getExtraStatistics() {
    return "coldSize=" + coldSize +
      ", hotSize=" + hotSize +
      ", hotMaxSize=" + hotMax +
      ", ghostSize=" + ghostSize +
      ", coldHits=" + (coldHits + sumUpListHits(handCold)) +
      ", hotHits=" + (hotHits + sumUpListHits(handHot)) +
      ", ghostHits=" + ghostHits +
      ", coldRunCnt=" + coldRunCnt +// identical to the evictions anyways
      ", coldScanCnt=" + coldScanCnt +
      ", hotRunCnt=" + hotRunCnt +
      ", hotScanCnt=" + hotScanCnt;
  }

  public static class Tunable extends TunableConstants {

    int hotMaxPercentage = 97;

    int hitCounterDecreaseShift = 6;

  }

  private Ghost lookupGhost(int _hash) {
    Ghost[] tab = ghosts;
    int n = tab.length;
    int _mask = n - 1;
    int idx = _hash & (_mask);
    Ghost e = tab[idx];
    while (e != null) {
      if (e.hash == _hash) {
        return e;
      }
      e = e.another;
    }
    return null;
  }

  private void insertGhost(Ghost e2, int _hash) {
    Ghost[] tab = ghosts;
    int n = tab.length;
    int _mask = n - 1;
    int idx = _hash & (_mask);
    e2.another = tab[idx];
    tab[idx] = e2;
    ghostSize++;
    int _maxFill = n * GHOST_LOAD_PERCENT / 100;
    if (ghostSize > _maxFill) {
      expand();
    }
  }

  private void expand() {
    Ghost[] tab = ghosts;
    int n = tab.length;
    int _mask;
    int idx;Ghost[] _newTab = new Ghost[n * 2];
    _mask = _newTab.length - 1;
    for (Ghost g : tab) {
      while (g != null) {
        idx = g.hash & _mask;
        Ghost _next = g.another;
        g.another = _newTab[idx];
        _newTab[idx] = g;
        g = _next;
      }
    }
    ghosts = _newTab;
  }


  private boolean removeGhost(Ghost g, int _hash) {
    Ghost[] tab = ghosts;
    int n = tab.length;
    int _mask = n - 1;
    int idx = _hash & (_mask);
    Ghost e = tab[idx];
    if (e == g) {
      tab[idx] = e.another;
      ghostSize--;
      return true;
    } else {
      while (e != null) {
        Ghost _another = e.another;
        if (_another == g) {
          e.another = _another.another;
          ghostSize--;
          return true;
        }
        e = _another;
      }
    }
    return false;
  }

  private int countGhostsInHash() {
    int _entryCount = 0;
    for (Ghost e : ghosts) {
      while (e != null) {
        _entryCount++;
        e = e.another;
      }
    }
    return _entryCount;
  }

  /**
   * Ghost representing a entry we have seen and evicted from the cache. We only store
   * the hash to save memory, since holding the key references may cause a size overhead.
   */
  private static class Ghost {

    /** Modified hashcode of the key */
    int hash;
    /** Hash table chain */
    Ghost another;
    /** LRU double linked list */
    Ghost next;
    /** LRU double linked list */
    Ghost prev;

    Ghost shortCircuit() {
      return next = prev = this;
    }

    static void removeFromList(final Ghost e) {
      e.prev.next = e.next;
      e.next.prev = e.prev;
      e.next = e.prev = null;
    }

    static void insertInList(final Ghost _head, final Ghost e) {
      e.prev = _head;
      e.next = _head.next;
      e.next.prev = e;
      _head.next = e;
    }

    static void moveToFront(final Ghost _head, final Ghost e) {
      removeFromList(e);
      insertInList(_head, e);
    }

    static int listSize(final Ghost _head) {
      int _count = 0;
      Ghost e = _head;
      while ((e = e.next) != _head) { _count++; }
      return _count;
    }

  }

}
