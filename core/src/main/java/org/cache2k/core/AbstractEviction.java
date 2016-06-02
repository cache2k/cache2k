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

/**
 * @author Jens Wilke
 */
public abstract class AbstractEviction implements Eviction {

  Object lock = new Object();
  long maxSize;
  long newEntryCounter;
  long removedCnt;
  long expiredRemovedCnt;
  long virginRemovedCnt;
  long evictedCount;
  private final HeapCacheListener listener;
  protected final HeapCache heapCache;

  public AbstractEviction(final HeapCache _heapCache, final HeapCacheListener _listener, final Cache2kConfiguration cfg) {
    heapCache = _heapCache;
    listener = _listener;
    maxSize = cfg.getEntryCapacity();
  }

  @Override
  public void insert(final Entry e) {
    boolean _evictionNeeded = false;
    synchronized (lock) {
      if (getSize() >= maxSize) {
        _evictionNeeded = true;
      }
      insertIntoReplacementList(e);
      newEntryCounter++;
    }
    if (_evictionNeeded) {
      evictEventually();
    }
  }

  @Override
  public void insertWithoutEviction(final Entry e) {
    synchronized (lock) {
      insertIntoReplacementList(e);
      newEntryCounter++;
    }
  }

  @Override
  public void remove(final Entry e) {
    synchronized (lock) {
      if (!e.isRemovedFromReplacementList()) {
        removeEntryFromReplacementList(e);
        long nrt = e.getNextRefreshTime();
        if (nrt == (Entry.GONE + Entry.EXPIRED)) {
          expiredRemovedCnt++;
        } else if (nrt == (Entry.GONE + Entry.VIRGIN)) {
          virginRemovedCnt++;
        } else {
          removedCnt++;
        }
      }
    }
  }

  @Override
  public void evictEventually() {
    boolean _evictionNeeded = true;
    Entry _previousCandidate = null;
    int _spinCount = 5;
    while (_evictionNeeded) {
      Entry e;
      if (_spinCount-- <= 0) { return; }
      synchronized (lock) {
        if (getSize() <= maxSize) {
          return;
        }
        e = findEvictionCandidate(_previousCandidate);
      }
      synchronized (e) {
        if (e.isGone()) {
          continue;
        }
        if (e.isProcessing()) {
          if (e != _previousCandidate) {
            _previousCandidate = e;
            continue;
          } else {
            return;
          }
        }
        e.startProcessing(Entry.ProcessingState.EVICT);
      }
      listener.onEvictionFromHeap(e);
      synchronized (e) {
        e.notifyAll();
        e.processingDone();
        boolean f = heapCache.removeEntryFromHash(e);
      }
      synchronized (lock) {
        if (!e.isRemovedFromReplacementList()) {
          evictEntry(e);
          evictedCount++;
        }
      }
    }
  }

  @Override
  public long getNewEntryCount() {
    return newEntryCounter;
  }

  @Override
  public long getRemovedCount() {
    return removedCnt;
  }

  @Override
  public long getVirginRemovedCount() {
    return virginRemovedCnt;
  }

  @Override
  public long getExpiredRemovedCount() {
    return expiredRemovedCnt;
  }

  @Override
  public long getEvictedCount() {
    return evictedCount;
  }

  @Override
  public void start() { }

  @Override
  public void stop() { }

  @Override
  public boolean drain() {
    return false;
  }

  @Override
  public void close() { }

  protected void evictEntry(Entry e) { removeEntryFromReplacementList(e); }

  protected abstract Entry findEvictionCandidate(Entry e);
  protected abstract void removeEntryFromReplacementList(Entry e);
  protected abstract void insertIntoReplacementList(Entry e);

}
