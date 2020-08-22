package org.cache2k.core;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
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

import org.cache2k.Weigher;
import org.cache2k.core.util.TunableConstants;
import org.cache2k.core.util.TunableFactory;

/**
 * Eviction algorithm inspired from CLOCK Pro with 3 clocks.
 *
 * <p>Uses a static allocation for hot and cold space sizes. No online or dynamic
 * optimization is done yet. However, the hit rate for all measured access traces is better
 * then LRU and it is resistant to scans.
 *
 * <p>From cache2k version 1.2 to version 1.4 the implementation was simplefied and the
 * demotion of hot entries removed. The result achieves similar or better hitrates.
 *
 * <p>The Clock-Pro algorithm is explained by the authors in
 * <a href="http://www.ece.eng.wayne.edu/~sjiang/pubs/papers/jiang05_CLOCK-Pro.pdf">CLOCK-Pro:
 * An Effective Improvement of the CLOCK Replacement</a>
 * and <a href="http://www.slideshare.net/huliang64/clockpro">Clock-Pro: An Effective
 * Replacement in OS Kernel</a>.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("WeakerAccess")
public class ClockProPlusEviction extends AbstractEviction {

  public static final Tunable TUNABLE_CLOCK_PRO = TunableFactory.get(Tunable.class);

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

  private Entry handCold;
  private Entry handHot;

  private Ghost[] ghosts;
  private final Ghost ghostHead = new Ghost().shortCircuit();
  private int ghostSize = 0;
  private static final int GHOST_LOAD_PERCENT = 63;

  public ClockProPlusEviction(HeapCache heapCache, HeapCacheListener listener,
                              long maxSize, Weigher weigher, long maxWeight,
                              boolean noChunking) {
    super(heapCache, listener, maxSize, weigher, maxWeight, noChunking);

    coldSize = 0;
    hotSize = 0;
    handCold = null;
    handHot = null;
    ghosts = new Ghost[4];
  }

  private long sumUpListHits(Entry e) {
    if (e == null) { return 0; }
    long cnt = 0;
    Entry head = e;
    do {
      cnt += e.hitCnt;
      e = e.next;
    } while (e != head);
    return cnt;
  }

  public long getHotMax() {
    return isWeigherPresent() ?
      (getSize() * TUNABLE_CLOCK_PRO.hotMaxPercentage / 100) :
      (getMaxSize() * TUNABLE_CLOCK_PRO.hotMaxPercentage / 100);
  }

  public long getGhostMax() {
    return getSize() / 2 + 1;
  }

  @Override
  public long getHitCount() {
    return hotHits + coldHits + sumUpListHits(handCold) + sumUpListHits(handHot);
  }

  @Override
  public long removeAll() {
    Entry e, head;
    int count = 0;
    e = head = handCold;
    long hits = 0;
    if (e != null) {
      do {
        hits += e.hitCnt;
        Entry next = e.prev;
        e.removedFromList();
        count++;
        e = next;
      } while (e != head);
      coldHits += hits;
    }
    handCold = null;
    coldSize = 0;
    e = head = handHot;
    if (e != null) {
      hits = 0;
      do {
        hits += e.hitCnt;
        Entry next = e.prev;
        e.removedFromList();
        count++;
        e = next;
      } while (e != head);
      hotHits += hits;
    }
    handHot = null;
    hotSize = 0;
    return count;
  }

  /**
   * Track the entry on the ghost list and call the usual remove procedure.
   */
  @Override
  public void removeFromReplacementListOnEvict(Entry e) {
    insertCopyIntoGhosts(e);
    removeFromReplacementList(e);
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
  protected void removeFromReplacementList(Entry e) {
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
      Ghost.moveToFront(ghostHead, g);
      return;
    }
    if (ghostSize >= getGhostMax()) {
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
    }
    if (g != null || (coldSize == 0 && hotSize < getHotMax())) {
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
    Entry hand = handHot;
    Entry coldCandidate = hand;
    long lowestHits = Long.MAX_VALUE;
    long hotHits = this.hotHits;
    int initialMaxScan = hotSize >> 2 + 1;
    int maxScan = initialMaxScan;
    long decrease =
      ((hand.hitCnt + hand.next.hitCnt) >> TUNABLE_CLOCK_PRO.hitCounterDecreaseShift) + 1;
    while (maxScan-- > 0) {
      long hitCnt = hand.hitCnt;
      if (hitCnt < lowestHits) {
        lowestHits = hitCnt;
        coldCandidate = hand;
        if (hitCnt == 0) {
          break;
        }
      }
      if (hitCnt < decrease) {
        hand.hitCnt = 0;
        hotHits += hitCnt;
      } else {
        hand.hitCnt = hitCnt - decrease;
        hotHits += decrease;
      }
      hand = hand.next;
    }
    this.hotHits = hotHits;
    long scanCount = initialMaxScan - maxScan;
    hotScanCnt += scanCount;
    handHot = hand;
    return coldCandidate;
  }

  /**
   * Runs cold hand an in turn hot hand to find eviction candidate.
   */
  @Override
  protected Entry findEvictionCandidate() {
    Entry hand = handCold;
    if (hotSize > getHotMax() || hand == null) {
      return runHandHot();
    }
    coldRunCnt++;
    int scanCnt = 1;
    if (hand.hitCnt > 0) {
      Entry evictFromHot = null;
      do {
        if (hotSize >= getHotMax() && handHot != null) {
          evictFromHot = runHandHot();
        }
        coldHits += hand.hitCnt;
        hand.hitCnt = 0;
        Entry e = hand;
        hand = Entry.removeFromCyclicList(e);
        coldSize--;
        e.setHot(true);
        hotSize++;
        handHot = Entry.insertIntoTailCyclicList(handHot, e);
        if (evictFromHot != null) {
          coldScanCnt += scanCnt;
          handCold = hand;
          return evictFromHot;
        }
        scanCnt++;
      } while (hand != null && hand.hitCnt > 0);
    }
    coldScanCnt += scanCnt;
    if (hand == null) {
      handCold = null;
      return runHandHot();
    }
    handCold = hand.next;
    return hand;
  }

  @Override
  public void checkIntegrity(IntegrityState integrityState) {
    integrityState.checkEquals("ghostSize == countGhostsInHash()", ghostSize, countGhostsInHash())
      .check("isWeigherPresent() || hotMax <= size",
        isWeigherPresent() || getHotMax() <= getMaxSize())
      .check("checkCyclicListIntegrity(handHot)", Entry.checkCyclicListIntegrity(handHot))
      .check("checkCyclicListIntegrity(handCold)", Entry.checkCyclicListIntegrity(handCold))
      .checkEquals("getCyclicListEntryCount(handHot) == hotSize",
        Entry.getCyclicListEntryCount(handHot), hotSize)
      .checkEquals("getCyclicListEntryCount(handCold) == coldSize",
        Entry.getCyclicListEntryCount(handCold), coldSize)
      .checkEquals("Ghost.listSize(ghostHead) == ghostSize",
        Ghost.listSize(ghostHead), ghostSize);
  }

  @Override
  public String getExtraStatistics() {
    return super.getExtraStatistics() +
      ", coldSize=" + coldSize +
      ", hotSize=" + hotSize +
      ", hotMaxSize=" + getHotMax() +
      ", ghostSize=" + ghostSize +
      ", coldHits=" + (coldHits + sumUpListHits(handCold)) +
      ", hotHits=" + (hotHits + sumUpListHits(handHot)) +
      ", ghostHits=" + ghostHits +
      ", coldRunCnt=" + coldRunCnt + // identical to the evictions anyways
      ", coldScanCnt=" + coldScanCnt +
      ", hotRunCnt=" + hotRunCnt +
      ", hotScanCnt=" + hotScanCnt;
  }

  public static class Tunable extends TunableConstants {

    int hotMaxPercentage = 97;

    int hitCounterDecreaseShift = 6;

  }

  private Ghost lookupGhost(int hash) {
    Ghost[] tab = ghosts;
    int n = tab.length;
    int mask = n - 1;
    int idx = hash & (mask);
    Ghost e = tab[idx];
    while (e != null) {
      if (e.hash == hash) {
        return e;
      }
      e = e.another;
    }
    return null;
  }

  private void insertGhost(Ghost e2, int hash) {
    Ghost[] tab = ghosts;
    int n = tab.length;
    int mask = n - 1;
    int idx = hash & (mask);
    e2.another = tab[idx];
    tab[idx] = e2;
    ghostSize++;
    int maxFill = n * GHOST_LOAD_PERCENT / 100;
    if (ghostSize > maxFill) {
      expand();
    }
  }

  private void expand() {
    Ghost[] tab = ghosts;
    int n = tab.length;
    int mask;
    int idx;
    Ghost[] newTab = new Ghost[n * 2];
    mask = newTab.length - 1;
    for (Ghost g : tab) {
      while (g != null) {
        idx = g.hash & mask;
        Ghost next = g.another;
        g.another = newTab[idx];
        newTab[idx] = g;
        g = next;
      }
    }
    ghosts = newTab;
  }


  private boolean removeGhost(Ghost g, int hash) {
    Ghost[] tab = ghosts;
    int n = tab.length;
    int mask = n - 1;
    int idx = hash & (mask);
    Ghost e = tab[idx];
    if (e == g) {
      tab[idx] = e.another;
      ghostSize--;
      return true;
    } else {
      while (e != null) {
        Ghost another = e.another;
        if (another == g) {
          e.another = another.another;
          ghostSize--;
          return true;
        }
        e = another;
      }
    }
    return false;
  }

  private int countGhostsInHash() {
    int entryCount = 0;
    for (Ghost e : ghosts) {
      while (e != null) {
        entryCount++;
        e = e.another;
      }
    }
    return entryCount;
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

    static void removeFromList(Ghost e) {
      e.prev.next = e.next;
      e.next.prev = e.prev;
      e.next = e.prev = null;
    }

    static void insertInList(Ghost head, Ghost e) {
      e.prev = head;
      e.next = head.next;
      e.next.prev = e;
      head.next = e;
    }

    static void moveToFront(Ghost head, Ghost e) {
      removeFromList(e);
      insertInList(head, e);
    }

    static int listSize(Ghost head) {
      int count = 0;
      Ghost e = head;
      while ((e = e.next) != head) { count++; }
      return count;
    }

  }

}
