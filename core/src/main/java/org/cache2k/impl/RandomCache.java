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
 * Random eviction without usage counters.
 *
 * @author Jens Wilke; created: 2013-12-22
 */
public class RandomCache<K, V> extends ConcurrentEvictionCache<K, V> {

  int evictionIndex = 0;

  @Override
  protected void recordHit(Entry entry) { }

  @Override
  protected void removeEntryFromReplacementList(Entry e) {
    e.removedFromList();
  }

  @Override
  protected void insertIntoReplacementList(Entry e) {
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
    return h0[idx];
  }

  @Override
  public long getHitCnt() {
    return 0;
  }

}
