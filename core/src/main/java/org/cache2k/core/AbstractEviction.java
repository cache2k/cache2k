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
 * Basic eviction functionality.
 *
 * @author Jens Wilke
 */
@SuppressWarnings({"WeakerAccess", "SynchronizationOnLocalVariableOrMethodParameter"})
public abstract class AbstractEviction implements Eviction, EvictionMetrics {

  private static final int MAX_EVICTION_SPINS = 1;
  private static final int DECREASE_AFTER_NO_CONTENTION = 6;
  private static final int INITIAL_CHUNK_SIZE = 4;
  private static final long MINIMUM_CAPACITY_FOR_CHUNKING = 1000;

  protected final long maxSize;
  protected final HeapCache heapCache;
  private final Object lock = new Object();
  private long newEntryCounter;
  private long removedCnt;
  private long expiredRemovedCnt;
  private long virginRemovedCnt;
  private long evictedCount;
  private final HeapCacheListener listener;
  private final boolean noListenerCall;
  private Entry[] evictChunkReuse;
  private int chunkSize;
  private int evictionRunningCount = 0;

  private final int maximumChunkSize;
  private int noEvictionContentionCount = 0;

  public AbstractEviction(final HeapCache _heapCache, final HeapCacheListener _listener, final Cache2kConfiguration cfg, final int evictionSegmentCount) {
    heapCache = _heapCache;
    listener = _listener;
    maxSize = cfg.getEntryCapacity() / evictionSegmentCount;
    if (cfg.getEntryCapacity() < MINIMUM_CAPACITY_FOR_CHUNKING) {
      chunkSize = 1;
      maximumChunkSize = 1;
    } else {
      chunkSize = INITIAL_CHUNK_SIZE;
      int _maximumChunkSize = Runtime.getRuntime().availableProcessors() * 8;
      maximumChunkSize = (int) Math.min((long) _maximumChunkSize, maxSize * 10 / 100);
    }
    noListenerCall = _listener instanceof HeapCacheListener.NoOperation;
  }

  @Override
  public void submit(final Entry e) {
    Entry[] _evictionChunk = null;
    synchronized (lock) {
      if (e.isNotYetInsertedInReplacementList()) {
        insertIntoReplacementList(e);
        newEntryCounter++;
        _evictionChunk = fillEvictionChunk();
      } else {
        removeEventually(e);
      }
    }
    evictChunk(_evictionChunk);
  }

  /** Safe GC overhead by reusing the chunk array. */
  Entry[] reuseChunkArray() {
    Entry[] ea = evictChunkReuse;
    if (ea != null) {
      evictChunkReuse = null;
    } else {
      ea = new Entry[chunkSize];
    }
    return ea;
  }

  private void removeEventually(final Entry e) {
    if (!e.isRemovedFromReplacementList()) {
      removeFromReplacementList(e);
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
  public boolean submitWithoutEviction(final Entry e) {
    synchronized (lock) {
      if (e.isNotYetInsertedInReplacementList()) {
        insertIntoReplacementList(e);
        newEntryCounter++;
      } else {
        removeEventually(e);
      }
      return evictionNeeded();
    }
  }

  /**
   * Do we need to trigger an eviction? For chunks sizes more than 1 the eviction
   * kicks later.
   */
  boolean evictionNeeded() {
    return getSize() > (maxSize + evictionRunningCount + chunkSize / 2);
  }

  @Override
  public void evictEventually() {
    Entry[] _chunk;
    synchronized (lock) {
      _chunk = fillEvictionChunk();
    }
    evictChunk(_chunk);
  }

  @Override
  public void evictEventually(final int hc) {
    evictEventually();
  }

  private Entry[] fillEvictionChunk() {
    if (!evictionNeeded()) {
      return null;
    }
    final Entry[] _chunk = reuseChunkArray();
    return refillChunk(_chunk);
  }

  private Entry[] refillChunk(Entry[] _chunk) {
    if (_chunk == null) {
      _chunk = new Entry[chunkSize];
    }
    evictionRunningCount += _chunk.length;
    for (int i = 0; i < _chunk.length; i++) {
      _chunk[i] = findEvictionCandidate(null);
    }
    return _chunk;
  }

  private void evictChunk(Entry[] _chunk) {
    int _evictSpins = MAX_EVICTION_SPINS;
    for (;;) {
      if (_chunk == null) { return; }
      removeFromHash(_chunk);
      synchronized (lock) {
        removeAllFromReplacementListOnEvict(_chunk);
        long _evictionRunningCount = (evictionRunningCount -= _chunk.length);
        boolean _evictionNeeded = evictionNeeded();
        _chunk = adaptChunkSize(_evictionNeeded, _evictionRunningCount, _chunk);
        if (_evictionNeeded && --_evictSpins > 0) {
          _chunk = refillChunk(_chunk);
        } else {
          evictChunkReuse = _chunk;
          return;
        }
      }
    }
  }

  /**
   * Increase chunk size either if other evictions run in parallel or when one eviction was not enough.
   * This will never increase the chunk size, when we just run on a single core.
   */
  Entry[] adaptChunkSize(boolean _evictionNeeded, long _evictionRunningCount, Entry[] _chunk) {
    if (_evictionNeeded || _evictionRunningCount > 0) {
      if (eventuallyExtendChunkSize()) {
        _chunk = null;
      }
    } else {
      if (eventuallyReduceChunkSize()) {
        _chunk = null;
      }
    }
    return _chunk;
  }

  private boolean eventuallyExtendChunkSize() {
    noEvictionContentionCount = 0;
    if (chunkSize < maximumChunkSize) {
      chunkSize = Math.min(maximumChunkSize, chunkSize + chunkSize >> 1);
      return true;
    }
    return false;
  }

  private boolean eventuallyReduceChunkSize() {
    int _count = noEvictionContentionCount++;
    if (_count >= DECREASE_AFTER_NO_CONTENTION) {
      if (chunkSize > INITIAL_CHUNK_SIZE) {
        chunkSize = Math.max(INITIAL_CHUNK_SIZE, chunkSize * 7 / 8);
        noEvictionContentionCount = 0;
        return true;
      }
    }
    return false;
  }

  private void removeFromHash(final Entry[] _chunk) {
    if (noListenerCall) {
      removeFromHashWithoutListener(_chunk);
    } else {
      removeFromHashWithListener(_chunk);
    }
  }

  private void removeFromHashWithoutListener(final Entry[] _chunk) {
    for (int i = 0; i < _chunk.length; i++) {
      Entry e = _chunk[i];
      synchronized (e) {
        if (e.isGone() || e.isProcessing()) {
          _chunk[i] = null; continue;
        }
        boolean f = heapCache.removeEntryForEviction(e);
      }
    }
  }

  private void removeFromHashWithListener(final Entry[] _chunk) {
    for (int i = 0; i < _chunk.length; i++) {
      Entry e = _chunk[i];
      synchronized (e) {
        if (e.isGone() || e.isProcessing()) {
          _chunk[i] = null; continue;
        }
        e.startProcessing(Entry.ProcessingState.EVICT);
      }
      listener.onEvictionFromHeap(e);
      synchronized (e) {
        e.processingDone();
        boolean f = heapCache.removeEntryForEviction(e);
      }
    }
  }

  private void removeAllFromReplacementListOnEvict(final Entry[] _chunk) {
    for (int i = 0; i < _chunk.length; i++) {
      Entry e = _chunk[i];
      if (e != null) {
        if (!e.isRemovedFromReplacementList()) {
          removeFromReplacementListOnEvict(e);
          evictedCount++;
        }
        /* we reuse the chunk array, null the array position to avoid memory leak */
        _chunk[i] = null;
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
  public int getEvictionRunningCount() {
    return evictionRunningCount;
  }

  @Override
  public EvictionMetrics getMetrics() {
    return this;
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

  protected void removeFromReplacementListOnEvict(Entry e) { removeFromReplacementList(e); }

  protected abstract Entry findEvictionCandidate(Entry e);
  protected abstract void removeFromReplacementList(Entry e);
  protected abstract void insertIntoReplacementList(Entry e);

  @Override
  public String getExtraStatistics() {
    return "chunkSize=" + chunkSize;
  }

}
