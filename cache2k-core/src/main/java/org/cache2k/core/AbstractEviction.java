package org.cache2k.core;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
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

  private final Weigher weigher;
  protected final HeapCache heapCache;
  private final Object lock = new Object();
  private final HeapCacheListener listener;
  private final boolean noListenerCall;
  private final boolean noChunking;

  private int chunkSize = 1;
  protected long maxSize;
  protected long maxWeight;
  protected long correctedMaxSizeOrWeight;

  private int evictionRunningCount = 0;
  private long evictionRunningWeight = 0;
  private long newEntryCounter;
  private long removedCnt;
  private long expiredRemovedCnt;
  private long virginRemovedCnt;
  private long evictedCount;
  private long currentWeight;

  private Entry[] evictChunkReuse;

  public AbstractEviction(final HeapCache heapCache, final HeapCacheListener listener,
                          final long maxSize, final Weigher weigher, final long maxWeight,
                          final boolean noChunking) {
    this.weigher = weigher;
    this.heapCache = heapCache;
    this.listener = listener;
    noListenerCall = listener instanceof HeapCacheListener.NoOperation;
    this.noChunking = noChunking;
    updateLimits(maxSize, maxWeight);
  }

  private void updateLimits(final long maxSize, final long maxWeight) {
    this.maxSize = maxSize;
    this.maxWeight = maxWeight;
    if (maxSize < MINIMUM_CAPACITY_FOR_CHUNKING || this.noChunking) {
      chunkSize = 1;
    } else {
      chunkSize = MINIMAL_CHUNK_SIZE + Runtime.getRuntime().availableProcessors() - 1;
      chunkSize = Math.min(MAXIMAL_CHUNK_SIZE, chunkSize);
    }
    if (this.maxSize >= 0) {
      if (this.maxSize == Long.MAX_VALUE) {
        correctedMaxSizeOrWeight = Long.MAX_VALUE >> 1;
      } else {
        correctedMaxSizeOrWeight = this.maxSize + chunkSize / 2;
      }
    } else {
      if (maxWeight < 0) {
        throw new IllegalArgumentException("either maxWeight or entryCapacity must be specified");
      }
      if (maxWeight == Long.MAX_VALUE) {
        correctedMaxSizeOrWeight = Long.MAX_VALUE >> 1;
      } else {
        correctedMaxSizeOrWeight = maxWeight;
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

  protected static void updateWeightInEntry(Entry e, long weight) {
    e.setCompressedWeight(LongTo16BitFloatingPoint.fromLong(weight));
  }

  /**
   * Exceptions have the minimum weight.
   */
  private long calculateWeight(final Entry e, final Object v) {
    long weight;
    if (v instanceof ExceptionWrapper) {
      weight = 1;
    } else {
      weight = weigher.weigh(e.getKey(), v);
    }
    if (weight < 0) {
      throw new IllegalArgumentException("weight must be positive.");
    }
    return weight;
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
    long weight;
    weight = calculateWeight(e, v);
    long currentWeight = getWeightFromEntry(e);
    if (currentWeight != weight) {
      this.currentWeight += weight - currentWeight;
      updateWeightInEntry(e, weight);
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
    Entry[] evictionChunk = null;
    synchronized (lock) {
      updateWeightInLock(e);
      if (currentWeight <= (correctedMaxSizeOrWeight + evictionRunningWeight)) {
        return;
      }
      evictionChunk = fillEvictionChunk();
    }
    evictChunk(evictionChunk);
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
   *
   * <p>Eviction happens one unit before the capacity is reached because this is called
   * to make space on behalf of a new entry. Needs a redesign.
   * We need to get rid of the getSize() > 0 here.
   * TODO: Add a parameter to the eviction process about how much entries should be freed?
   */
  boolean isEvictionNeeded() {
    if (isWeigherPresent()) {
      return currentWeight >= (correctedMaxSizeOrWeight + evictionRunningWeight);
    } else {
      return getSize() >= (correctedMaxSizeOrWeight + evictionRunningCount);
    }
  }

  @Override
  public void evictEventually() {
    Entry[] chunk;
    synchronized (lock) {
      chunk = fillEvictionChunk();
    }
    evictChunk(chunk);
  }

  @Override
  public void evictEventually(final int hashCodeHint) {
    evictEventually();
  }

  private Entry[] fillEvictionChunk() {
    if (!isEvictionNeeded()) {
      return null;
    }
    final Entry[] chunk = reuseChunkArray();
    return refillChunk(chunk);
  }

  private Entry[] refillChunk(Entry[] chunk) {
    if (chunk == null) {
      chunk = new Entry[chunkSize];
    }
    evictionRunningCount += chunk.length;
    for (int i = 0; i < chunk.length; i++) {
      chunk[i] = findEvictionCandidate(null);
      if (isWeigherPresent()) {
        evictionRunningWeight += getWeightFromEntry(chunk[i]);
      }
    }
    return chunk;
  }

  private int evictChunk(Entry[] chunk) {
    if (chunk == null) { return 0; }
    int processCount = removeFromHash(chunk);
    if (processCount > 0) {
      synchronized (lock) {
        removeAllFromReplacementListOnEvict(chunk);
        evictChunkReuse = chunk;
      }
    }
    return processCount;
  }

  private int removeFromHash(final Entry[] chunk) {
    if (!noListenerCall) {
      return removeFromHashWithListener(chunk);
    }
    return removeFromHashWithoutListener(chunk);
  }

  private int removeFromHashWithoutListener(final Entry[] chunk) {
    int processCount = 0;
    for (int i = 0; i < chunk.length; i++) {
      Entry e = chunk[i];
      synchronized (e) {
        if (e.isGone() || e.isProcessing()) {
          evictionRunningCount--;
          if (isWeigherPresent()) {
            evictionRunningWeight -= getWeightFromEntry(e);
          }
          chunk[i] = null; continue;
        }
        heapCache.removeEntryForEviction(e);
      }
      processCount++;
    }
    return processCount;
  }

  private int removeFromHashWithListener(final Entry[] chunk) {
    int processCount = 0;
    for (int i = 0; i < chunk.length; i++) {
      Entry e = chunk[i];
      synchronized (e) {
        if (e.isGone() || e.isProcessing()) {
          evictionRunningCount--;
          if (isWeigherPresent()) {
            evictionRunningWeight -= getWeightFromEntry(e);
          }
          chunk[i] = null; continue;
        }
        e.startProcessing(Entry.ProcessingState.EVICT, null);
      }
      listener.onEvictionFromHeap(e);
      synchronized (e) {
        e.processingDone();
        heapCache.removeEntryForEviction(e);
      }
      processCount++;
    }
    return processCount;
  }

  private void removeAllFromReplacementListOnEvict(final Entry[] chunk) {
    for (int i = 0; i < chunk.length; i++) {
      Entry e = chunk[i];
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
        chunk[i] = null;
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

  /**
   * Update the limits and run eviction loop with chunks to get rid
   * of entries fast. Gives up the lock to send events and allow
   * other cache operations while adaption happens.
   */
  @Override
  public void changeCapacity(final long entryCountOrWeight) {
    if (entryCountOrWeight <= 0) {
      throw new IllegalArgumentException("Capacity of 0 is not supported.");
    }
    Entry[] chunk;
    synchronized (lock) {
      if (entryCountOrWeight >= 0 && entryCountOrWeight < Long.MAX_VALUE) {
        modifyCapacityLimits(entryCountOrWeight + 1);
      } else {
        modifyCapacityLimits(entryCountOrWeight);
      }
      synchronized (lock) {
        evictChunkReuse = null;
        chunk = fillEvictionChunk();
      }
    }
    while (chunk != null) {
      evictChunk(chunk);
      synchronized (lock) {
        chunk = fillEvictionChunk();
      }
    }
    synchronized (lock) {
      modifyCapacityLimits(entryCountOrWeight);
    }
  }

  private void modifyCapacityLimits(final long entryCountOrWeight) {
    if (isWeigherPresent()) {
      updateLimits(maxSize, entryCountOrWeight);
    } else {
      updateLimits(entryCountOrWeight, maxWeight);
    }
  }

}
