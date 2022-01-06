package org.cache2k.core.eviction;

/*-
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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

import org.cache2k.operation.Weigher;
import org.cache2k.core.Entry;
import org.cache2k.core.IntegrityState;

/**
 * @author Jens Wilke
 */
@SuppressWarnings("rawtypes")
public class RandomEviction extends AbstractEviction {

  private int evictionIndex = 0;
  private long size = 0;
  private final Entry head = new Entry().shortCircuit();

  public RandomEviction(HeapCacheForEviction heapCache, InternalEvictionListener listener,
                        long maxSize, Weigher weigher, long maxWeight) {
    super(heapCache, listener, maxSize, weigher, maxWeight, false);
  }

  @Override
  public boolean updateWeight(Entry e) {
    return false;
  }

  @Override
  protected void removeFromReplacementList(Entry e) {
    Entry.removeFromList(e);
  }

  @Override
  protected void insertIntoReplacementList(Entry e) {
    size++;
    Entry.insertInList(head, e);
  }

  @Override
  protected Entry findEvictionCandidate() {
    Entry[] h0 = heapCache.getHashEntries();
    int idx = evictionIndex % (h0.length);
    Entry e;
    while ((e = h0[idx]) == null) {
      idx++;
      if (idx >= h0.length) {
        idx = 0;
      }
    }
    evictionIndex += e.hashCode;
    if (evictionIndex < 0) {
    evictionIndex = -evictionIndex;
    }
    return e;
  }

  @Override
  public void checkIntegrity(IntegrityState integrityState) {
  }

  @Override
  protected long removeAllFromReplacementList() {
    long count = 0;
    Entry head = this.head;
    Entry e = this.head.prev;
    while (e != head) {
      Entry next = e.prev;
      e.removedFromList();
      count++;
      e = next;
    }
    return count;
  }

  @Override
  public long getSize() {
    return size;
  }

  @Override
  protected Entry findIdleCandidate(int maxScan) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected long getScanCount() {
    return 0;
  }
}
