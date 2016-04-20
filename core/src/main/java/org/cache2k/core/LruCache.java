
package org.cache2k.core;

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
 * Cache implementation with LRU eviction algorithm.
 *
 * @author Jens Wilke
 */
public class LruCache<K, V> extends HeapCache<K, V> {

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
