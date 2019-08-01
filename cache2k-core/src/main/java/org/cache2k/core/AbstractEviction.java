package org.cache2k.core;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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

import org.cache2k.core.concurrency.Job;
import org.cache2k.Weigher;

/**
 * Basic eviction functionality.
 *
 * @author Jens Wilke
 */
@SuppressWarnings({"WeakerAccess", "SynchronizationOnLocalVariableOrMethodParameter", "unchecked"})
public abstract class AbstractEviction implements Eviction, EvictionMetrics {

  public static final int MINIMAL_CHUNK_SIZE = 4;
  public static final int MAXIMAL_CHUNK_SIZE = 64;
  public static final long MINIMUM_CAPACITY_FOR_CHUNKING = 1000;

  protected final long maxSize;
  protected final long maxWeight;
  protected final long correctedMaxSizeOrWeight;
  protected final HeapCache heapCache;
  private final Object lock = new Object();
  private long newEntryCounter;
  private long removedCnt;
  private long expiredRemovedCnt;
  private long virginRemovedCnt;
  private long evictedCount;
  private long currentWeight;
  private final HeapCacheListener listener;
  private final boolean noListenerCall;
  private Entry[] evictChunkReuse;
  private int chunkSize;
  private int evictionRunningCount = 0;
  private long evictionRunningWeight = 0;
  private final Weigher weigher;
  public AbstractEviction(final HeapCache _heapCache, final HeapCacheListener _listener,
                          final long _maxSize, final Weigher _weigher, final long _maxWeight) {
    weigher = _weigher;
    heapCache = _heapCache;
    listener = _listener;
    maxSize = _maxSize;
    maxWeight = _maxWeight;
    if (_maxSize < MINIMUM_CAPACITY_FOR_CHUNKING) {
      chunkSize = 1;
    } else {
      chunkSize = MINIMAL_CHUNK_SIZE + Runtime.getRuntime().availableProcessors() - 1;
      chunkSize = Math.min(MAXIMAL_CHUNK_SIZE, chunkSize);
    }
    noListenerCall = _listener instanceof HeapCacheListener.NoOperation;
    if (maxSize >= 0) {
      if (maxSize == Long.MAX_VALUE) {
        correctedMaxSizeOrWeight = Long.MAX_VALUE >> 1;
      } else {
        correctedMaxSizeOrWeight = maxSize + chunkSize / 2;
      }
    } else {
      if (_maxWeight < 0) {
        throw new IllegalArgumentException("either maxWeight or entryCapacity must be specified");
      }
      if (_maxWeight == Long.MAX_VALUE) {
        correctedMaxSizeOrWeight = Long.MAX_VALUE >> 1;
      } else {
        correctedMaxSizeOrWeight = _maxWeight;
      }
    }
  }

  @Override
  public boolean isWeigherPresent() {
    return weigher != null;
  }

  protected static long getWeightFromEntry(Entry e) {
    return LongTo16BitFloatingPoint.toLong(e.getCompressedWeight());
  }

  protected static void updateWeightInEntry(Entry e, long _weight) {
    e.setCompressedWeight(LongTo16BitFloatingPoint.fromLong(_weight));
  }

  /**
   * Exceptions have the minimum weight.
   */
  private long calculateWeight(final Entry e, final Object v) {
    long _weight;
    if (v instanceof ExceptionWrapper) {
      _weight = 1;
    } else {
      _weight = weigher.weigh(e.getKey(), v);
    }
    if (_weight < 0) {
      throw new IllegalArgumentException("weight must be positive.");
    }
    return _weight;
  }

  /**
   * The value in the entry is not set yet. Use a standard weight of 0.
   */
  protected void insertAndWeighInLock(Entry e) {
    if (!isWeigherPresent()) {
      return;
    }
  }

  protected void updateWeightInLock(Entry e) {
    Object v = e.getValueOrException();
    long _weight;
    _weight = calculateWeight(e, v);
    long _currentWeight = getWeightFromEntry(e);
    if (_currentWeight != _weight) {
      currentWeight += _weight - _currentWeight;
      updateWeightInEntry(e, _weight);
    }
  }

  /**
   * Called upon eviction or deletion
   */
  protected void updateTotalWeightForRemove(Entry e) {
    if (!isWeigherPresent()) {
      return;
    }
    currentWeight -= getWeightFromEntry(e);
  }

  /**
   * Update the weight in the entry and update the weight calculation in
   * this eviction segment. Since the weight limit might be reached, try to
   * evict until within limits again.
   */
  @Override
  public void updateWeight(final Entry e) {
    if (!isWeigherPresent()) {
      return;
    }
    Entry[] _evictionChunk = null;
    synchronized (lock) {
      updateWeightInLock(e);
      _evictionChunk = fillEvictionChunk();
    }
    evictChunk(_evictionChunk);
    int _processCount = 1;
    while (isEvictionNeeded() && _processCount > 0) {
      synchronized (lock) {
        _evictionChunk = fillEvictionChunk();
      }
      _processCount = evictChunk(_evictionChunk);
    }
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
      updateTotalWeightForRemove(e);
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
        insertAndWeighInLock(e);
        newEntryCounter++;
      } else {
        removeEventually(e);
        updateTotalWeightForRemove(e);
      }
      return isEvictionNeeded();
    }
  }

  /**
   * Do we need to trigger an eviction? For chunks sizes more than 1 the eviction
   * kicks in later.
   */
  boolean isEvictionNeeded() {
    if (isWeigherPresent()) {
      return currentWeight > (correctedMaxSizeOrWeight + evictionRunningWeight);
    } else {
      return getSize() > (correctedMaxSizeOrWeight + evictionRunningCount);
    }
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
  public void evictEventually(final int _hashCodeHint) {
    evictEventually();
  }

  private Entry[] fillEvictionChunk() {
    if (!isEvictionNeeded()) {
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
      if (isWeigherPresent()) {
        evictionRunningWeight += getWeightFromEntry(_chunk[i]);
      }
    }
    return _chunk;
  }

  private int evictChunk(Entry[] _chunk) {
    if (_chunk == null) { return 0; }
    int _processCount = removeFromHash(_chunk);
    if (_processCount > 0) {
      synchronized (lock) {
        removeAllFromReplacementListOnEvict(_chunk);
        evictChunkReuse = _chunk;
      }
    }
    return _processCount;
  }

  private int removeFromHash(final Entry[] _chunk) {
    if (!noListenerCall) {
      return removeFromHashWithListener(_chunk);
    }
    return removeFromHashWithoutListener(_chunk);
  }

  private int removeFromHashWithoutListener(final Entry[] _chunk) {
    int _processCount = 0;
    for (int i = 0; i < _chunk.length; i++) {
      Entry e = _chunk[i];
      synchronized (e) {
        if (e.isGone() || e.isProcessing()) {
          evictionRunningCount--;
          if (isWeigherPresent()) {
            evictionRunningWeight -= getWeightFromEntry(e);
          }
          _chunk[i] = null; continue;
        }
        heapCache.removeEntryForEviction(e);
      }
      _processCount++;
    }
    return _processCount;
  }

  private int removeFromHashWithListener(final Entry[] _chunk) {
    int _processCount = 0;
    for (int i = 0; i < _chunk.length; i++) {
      Entry e = _chunk[i];
      synchronized (e) {
        if (e.isGone() || e.isProcessing()) {
          evictionRunningCount--;
          if (isWeigherPresent()) {
            evictionRunningWeight -= getWeightFromEntry(e);
          }
          _chunk[i] = null; continue;
        }
        e.startProcessing(Entry.ProcessingState.EVICT);
      }
      listener.onEvictionFromHeap(e);
      synchronized (e) {
        e.processingDone();
        heapCache.removeEntryForEviction(e);
      }
      _processCount++;
    }
    return _processCount;
  }

  private void removeAllFromReplacementListOnEvict(final Entry[] _chunk) {
    for (int i = 0; i < _chunk.length; i++) {
      Entry e = _chunk[i];
      if (e != null) {
        if (!e.isRemovedFromReplacementList()) {
          removeFromReplacementListOnEvict(e);
          updateTotalWeightForRemove(e);
          evictedCount++;
        }
        evictionRunningCount--;
        if (isWeigherPresent()) {
          evictionRunningWeight -= getWeightFromEntry(e);
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
  public long getMaxWeight() {
    return maxWeight;
  }

  @Override
  public long getCurrentWeight() {
    return currentWeight;
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

  /**
   * Find a candidate for eviction. The caller needs to provide the previous eviction
   * candidate if this method is called multiple times until a fit is found.
   *
   * @param e {@code null} or previous found eviction candidate returned by this method.
   *                      This is currently not used by the implemented eviction methods
   *                      but might be in the future.
   */
  @SuppressWarnings("SameParameterValue")
  protected abstract Entry findEvictionCandidate(Entry e);
  protected abstract void removeFromReplacementList(Entry e);
  protected abstract void insertIntoReplacementList(Entry e);

  @Override
  public String getExtraStatistics() {
    return
      "impl=" + this.getClass().getSimpleName() +
      ", chunkSize=" + chunkSize;
  }

}
