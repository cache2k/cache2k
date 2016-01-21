
package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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
 * Cache implementation with LRU eviction algorithm.
 *
 * @author Jens Wilke
 */
public class LruCache<K, V> extends BaseCache<K, V> {

  Entry<K, V> head;
  long hitCnt;

  @Override
  public long getHitCnt() {
    return hitCnt;
  }

  @Override
  protected void recordHit(Entry e) {
    if (!e.isRemovedFromReplacementList()) {
      removeEntryFromReplacementList(e);
      insertInList(head, e);
    }
    hitCnt++;
  }

  @Override
  protected void insertIntoReplacementList(Entry e) {
    insertInList(head, e);
  }

  @Override
  protected Entry newEntry() {
    return new Entry<K, V>();
  }


  @Override
  protected Entry findEvictionCandidate() {
    return head.prev;
  }

  @Override
  protected void initializeHeapCache() {
    super.initializeHeapCache();
    head = new Entry<K, V>().shortCircuit();
  }

  @Override
  protected IntegrityState getIntegrityState() {
    synchronized (lock) {
      return super.getIntegrityState()
        .checkEquals("size = list entry count", getLocalSize() , getListEntryCount(head));
    }
  }

}
