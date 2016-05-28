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

/**
 * Base class of all eviction algorithms that allow full concurrent read access without
 * locking.
 *
 * @author Jens Wilke; created: 2013-12-22
 */
public abstract class ConcurrentEvictionCache<K, V> extends HeapCache<K, V> {

  /**
   * No locking needed.
   */
  @Override
  protected final void recordHitLocked(Entry e) {
    recordHit(e);
  }

  /**
   * First lookup in the hash unsynchronized, if missed, do synchronize and
   * try again.
   */
  @Override
  protected final Entry lookupOrNewEntrySynchronized(K key) {
    int hc = modifiedHash(key.hashCode());
    Entry e = Hash.lookup(mainHash, key, hc);
    if (e != null) {
      recordHit(e);
      return e;
    }
    synchronized (mainHashCtrl.segmentLock(hc)) {
      checkClosed();
      e = lookupEntry(key, hc);
      if (e == null) {
        e = insertNewEntry(key, hc);
      }
    }
    checkExpandMainHash(hc);
    return e;
  }

  @Override
  protected final Entry lookupEntryUnsynchronized(K key, int hc) {
    Entry e = Hash.lookup(mainHash, key, hc);
    if (e != null) {
      recordHit(e);
      return e;
    }
    return null;
  }

}
