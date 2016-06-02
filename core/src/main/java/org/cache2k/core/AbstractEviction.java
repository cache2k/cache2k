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
import org.cache2k.core.threading.Job;

/**
 * @author Jens Wilke
 */
public abstract class AbstractEviction implements Eviction {

  final Object lock = new Object();
  final protected long maxSize;
  private long newEntryCounter;
  private long removedCnt;
  private long expiredRemovedCnt;
  private long virginRemovedCnt;
  private long evictedCount;
  private final HeapCacheListener listener;
  private final boolean noListenerCall;
  protected final HeapCache heapCache;
  Entry[] evictChunkReuse;
  int chunkSize;

  public AbstractEviction(final HeapCache _heapCache, final HeapCacheListener _listener, final Cache2kConfiguration cfg, final int evictionSegmentCount) {
    heapCache = _heapCache;
    listener = _listener;
    chunkSize = Runtime.getRuntime().availableProcessors() * 3;
    if (cfg.getEntryCapacity() <= 1000) {
      maxSize = cfg.getEntryCapacity() / evictionSegmentCount;
      chunkSize = 1;
    } else {
      maxSize = (cfg.getEntryCapacity() + chunkSize) / evictionSegmentCount;
    }
    noListenerCall = _listener instanceof HeapCacheListener.NoOperation;
  }

  @Override
  public void execute(final Entry e) {
    Entry[] _evictionChunk = null;
    synchronized (lock) {
      if (e.isNotYetInsertedInReplacementList()) {
        insertIntoReplacementList(e);
        newEntryCounter++;
        if (getSize() > maxSize) {
          _evictionChunk = reuseChunkArray();
          fillEvictionChunk(_evictionChunk);
        }
      } else {
        removeEventually(e);
      }
    }
    if (_evictionChunk != null) {
      evictChunk(_evictionChunk);
    }
  }

  Entry[] reuseChunkArray() {
    Entry[] ea = evictChunkReuse;
    if (ea != null) {
      evictChunkReuse = null;
    } else {
      return new Entry[chunkSize];
    }
    return ea;
  }

  private void removeEventually(final Entry e) {
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

  @Override
  public boolean executeWithoutEviction(final Entry e) {
    synchronized (lock) {
      if (e.isNotYetInsertedInReplacementList()) {
        insertIntoReplacementList(e);
        newEntryCounter++;
      } else {
        removeEventually(e);
      }
      return getSize() > maxSize;
    }
  }

  @Override
  public void evictEventually() {
    Entry[] _chunk;
    synchronized (lock) {
      if (getSize() <= maxSize) { return; }
      _chunk = reuseChunkArray();
      fillEvictionChunk(_chunk);
    }
    evictChunk(_chunk);
  }

  @Override
  public void evictEventually(final int hc) {
    evictEventually();
  }

  private void fillEvictionChunk(final Entry[] _chunk) {
    for (int i = 0; i < _chunk.length; i++) {
      _chunk[i] = findEvictionCandidate(null);
    }
  }

  private void evictChunk(Entry[] _chunk) {
    int _evictSpins = 5;
    int _evictedCount = 0;
    int _goneCount = 0;
    int _processingCount = 0;
    int _alreadyEvicted = 0;
    for (;;) {
      for (int i = 0; i < _chunk.length; i++) {
        Entry e = _chunk[i];
        if (noListenerCall) {
          synchronized (e) {
            if (e.isGone()) {
              _goneCount++;
              _chunk[i] = null;
              continue;
            }
            if ( e.isProcessing()) {
              _processingCount++;
              _chunk[i] = null;
              continue;
            }
            boolean f = heapCache.removeEntryForEviction(e);
          }
        } else {
          synchronized (e) {
            if (e.isGone() || e.isProcessing()) {
              _chunk[i] = null;
              continue;
            }
            e.startProcessing(Entry.ProcessingState.EVICT);
          }
          listener.onEvictionFromHeap(e);
          synchronized (e) {
            e.notifyAll();
            e.processingDone();
            boolean f = heapCache.removeEntryForEviction(e);
          }
        }
      }
      synchronized (lock) {
        for (int i = 0; i < _chunk.length; i++) {
          Entry e = _chunk[i];
          if (e != null) {
            if (!e.isRemovedFromReplacementList()) {
              evictEntry(e);
              evictedCount++;
              _evictedCount++;
            } else {
              _alreadyEvicted++;
            }
          }
        }
        if (getSize() > maxSize) {
          if (--_evictSpins > 0) {
            fillEvictionChunk(_chunk);
          } else {
            evictChunkReuse = _chunk;
            return;
          }
        } else {
          evictChunkReuse = _chunk;
          return;
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
  public long getMaxSize() {
    return maxSize;
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

  @Override
  public <T> T runLocked(final Job<T> j) {
    synchronized (lock) {
      return j.call();
    }
  }

  protected void evictEntry(Entry e) { removeEntryFromReplacementList(e); }

  protected abstract Entry findEvictionCandidate(Entry e);
  protected abstract void removeEntryFromReplacementList(Entry e);
  protected abstract void insertIntoReplacementList(Entry e);

}
