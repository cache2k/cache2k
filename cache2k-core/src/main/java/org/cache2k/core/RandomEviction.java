package org.cache2k.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2017 headissue GmbH, Munich
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
 * @author Jens Wilke
 */
public class RandomEviction extends AbstractEviction {

  private int evictionIndex = 0;
  private long size = 0;
  private Entry head = new Entry().shortCircuit();

  public RandomEviction(final HeapCache _heapCache, final HeapCacheListener _listener, final long _maxSize) {
    super(_heapCache, _listener, _maxSize);
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
  protected Entry findEvictionCandidate(Entry _previous) {
    Entry[] h0 = heapCache.hash.getEntries();
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
  public void checkIntegrity(final IntegrityState _integrityState) {

  }

  @Override
  public long removeAll() {
    long _count = 0;
    Entry _head = head;
    Entry e = head.prev;
    while (e != _head) {
      Entry _next = e.prev;
      e.removedFromList();
      _count++;
      e = _next;
    }
    return _count;
  }

  @Override
  public String getExtraStatistics() {
    return "";
  }

  @Override
  public long getHitCount() {
    return 0;
  }

  @Override
  public long getSize() {
    return size;
  }
}
