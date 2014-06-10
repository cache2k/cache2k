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
 * Random eviction without usage counters.
 *
 * @author Jens Wilke; created: 2013-12-22
 */
public class RandomCache<K, T> extends LockFreeCache<BaseCache.Entry, K, T> {

  int evictionIndex = 0;

  @Override
  protected void recordHit(Entry entry) { }

  @Override
  protected void removeEntryFromReplacementList(Entry e) {
    e.next = null;
  }

  @Override
  protected void insertIntoReplcamentList(Entry e) {
    e.next = e;
  }

  @Override
  protected Entry newEntry() {
    return new Entry();
  }

  /**
   * Start at arbitrary hash slot and evict the next best entry.
   */
  @Override
  protected Entry findEvictionCandidate() {
    Entry[] h0 = mainHash;
    Entry[] h1 = refreshHash;
    int idx = evictionIndex % (h0.length + h1.length);
    if (idx >= h0.length) {
      idx -= h0.length;
      Entry[] t = h0;
      h0 = h1;
      h1 = t;
    }
    while (h0[idx] == null) {
      idx++;
      if (idx >= h0.length) {
        Entry[] t = h0;
        h0 = h1;
        h1 = t;
        idx = 0;
      }
    }
    evictionIndex += h0[idx].hashCode;
    if (evictionIndex < 0) {
      evictionIndex = -evictionIndex;
    }
    h0[idx].removedFromList();
    return h0[idx];
  }

  @Override
  public long getHitCnt() {
    return 0;
  }

}
