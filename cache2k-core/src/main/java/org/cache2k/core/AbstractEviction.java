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

  private int evictionRunningCount = 0;
  private long newEntryCounter;
  private long removedCnt;
  private long expiredRemovedCnt;
  private long virginRemovedCnt;
  private long evictedCount;
  private long totalWeight;
  private long evictedWeight;

  private Entry[] evictChunkReuse;

  public AbstractEviction(HeapCache heapCache, HeapCacheListener listener,
                          long maxSize, Weigher weigher, long maxWeight,
                          boolean noChunking) {
    this.weigher = weigher;
    this.heapCache = heapCache;
    this.listener = listener;
    noListenerCall = listener instanceof HeapCacheListener.NoOperation;

    this.noChunking = noChunking;
    updateLimits(maxSize, maxWeight);
  }

  private void updateLimits(long maxSize, long maxWeight) {
    this.maxSize = maxSize;
    this.maxWeight = maxWeight;
    chunkSize = calculateChunkSize(noChunking, maxSize);
    evictChunkReuse = new Entry[chunkSize];
    if (this.maxSize < 0 && this.maxWeight < 0) {
      throw new IllegalArgumentException("either maxWeight or entryCapacity must be specified");
    }
  }

  private static int calculateChunkSize(boolean noChunking, long maxSize) {
    if (noChunking) {
      return 1;
    }
    if (maxSize < MINIMUM_CAPACITY_FOR_CHUNKING && maxSize >= 0) {
      return 1;
    } else {
      return Math.min(
        MAXIMAL_CHUNK_SIZE,
        MINIMAL_CHUNK_SIZE + Runtime.getRuntime().availableProcessors() - 1);
    }
  }

  @Override
  public boolean isWeigherPresent() {
    return weigher != null;
  }

  static int decompressWeight(int weight) {
    return IntegerTo16BitFloatingPoint.expand(weight);
  }

  static int compressWeight(int weight) {
    return IntegerTo16BitFloatingPoint.compress(weight);
  }

  /**
   * Exceptions have the minimum weight.
   */
  private int calculateWeight(Entry e, Object v) {
    int weight;
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
   * To retrieve the consistent weight from the entry, we need to hold the
   * eviction lock, since storage of weight is combined with hot marker.
   */
  protected void updateAccumulatedWeightInLock(Entry e) {
    Object v = e.getValueOrException();
    int requestedCompressedWeight = compressWeight(calculateWeight(e, v));
    if (e.getCompressedWeight() != requestedCompressedWeight) {
      long decompressedEntryWeight = decompressWeight(e.getCompressedWeight());
      long requestedWeightWithLostPrecision = decompressWeight(requestedCompressedWeight);
      totalWeight += requestedWeightWithLostPrecision - decompressedEntryWeight;
      e.setCompressedWeight(requestedCompressedWeight);
    }
  }

  /**
   * Called upon eviction or deletion
   */
  protected void updateTotalWeightForRemove(Entry e) {
    if (!isWeigherPresent()) {
      return;
    }
    int entryWeight = getWeightFromEntry(e);
    totalWeight -= entryWeight;
    evictedWeight += entryWeight;
  }

  private int getWeightFromEntry(Entry e) {
    return decompressWeight(e.getCompressedWeight());
  }

  @Override
  public boolean updateWeight(Entry e) {
    if (!isWeigherPresent()) {
      return false;
    }
    synchronized (lock) {
      updateAccumulatedWeightInLock(e);
      return isEvictionNeeded(0);
    }
  }

  @Override
  public boolean submitWithoutTriggeringEviction(Entry e) {
    synchronized (lock) {
      if (e.isNotYetInsertedInReplacementList()) {
        insertIntoReplacementList(e);
        newEntryCounter++;
      } else {
        removeEventually(e);
      }
      return isEvictionNeeded(1);
    }
  }

  private void removeEventually(Entry e) {
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

  /**
   * Do we need to trigger an eviction? For chunks sizes more than 1 the eviction
   * kicks in later.
   *
   * <p>Eviction happens one unit before the capacity is reached because this is called
   * to make space on behalf of a new entry. Needs a redesign.
   * We need to get rid of the getSize() > 0 here.
   * TODO: Add a parameter to the eviction process about how much entries should be freed?
   */
  boolean isEvictionNeeded(int spaceNeeded) {
    if (evictionRunningCount > 0) {
      return false;
    }
    if (isWeigherPresent()) {
      return totalWeight + spaceNeeded > maxWeight;
    } else {
      return getSize() + spaceNeeded > maxSize;
    }
  }

  @Override
  public void evictEventuallyBeforeInsert() {
    evictEventually(1);
  }

  @Override
  public void evictEventuallyBeforeInsertOnSegment(int hashCodeHint) {
    evictEventuallyBeforeInsert();
  }

  @Override
  public void evictEventually() {
    evictEventually(0);
  }

  private void evictEventually(int spaceNeeded) {
    Entry[] chunk;
    synchronized (lock) {
      chunk = fillEvictionChunk(spaceNeeded);
    }
    evictChunk(chunk);
  }

  private Entry[] fillEvictionChunk(int spaceNeeded) {
    if (!isEvictionNeeded(spaceNeeded)) {
      return null;
    }
    Entry[] chunk = evictChunkReuse;
    return refillChunk(chunk);
  }

  private Entry[] refillChunk(Entry[] chunk) {
    if (chunk == null) {
      chunk = new Entry[chunkSize];
    }
    evictionRunningCount += chunk.length;
    for (int i = 0; i < chunk.length; i++) {
      chunk[i] = findEvictionCandidate();
    }
    return chunk;
  }

  private int evictChunk(Entry[] chunk) {
    if (chunk == null) { return 0; }
    int processCount = removeFromHash(chunk);
    synchronized (lock) {
      if (processCount > 0) {
        removeAllFromReplacementListOnEvict(chunk);
      }
      evictionRunningCount -= chunk.length;
    }
    return processCount;
  }

  private int removeFromHash(Entry[] chunk) {
    if (noListenerCall) {
      return removeFromHashWithoutListener(chunk);
    }
    return removeFromHashWithListener(chunk);
  }

  private int removeFromHashWithoutListener(Entry[] chunk) {
    int processCount = 0;
    for (int i = 0; i < chunk.length; i++) {
      Entry e = chunk[i];
      synchronized (e) {
        if (e.isGone() || e.isProcessing()) {
          chunk[i] = null;
          continue;
        }
        heapCache.removeEntryForEviction(e);
      }
      processCount++;
    }
    return processCount;
  }

  private int removeFromHashWithListener(Entry[] chunk) {
    int processCount = 0;
    for (int i = 0; i < chunk.length; i++) {
      Entry e = chunk[i];
      synchronized (e) {
        if (e.isGone() || e.isProcessing()) {
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

  private void removeAllFromReplacementListOnEvict(Entry[] chunk) {
    for (int i = 0; i < chunk.length; i++) {
      Entry e = chunk[i];
      if (e != null) {
        if (!e.isRemovedFromReplacementList()) {
          removeFromReplacementListOnEvict(e);
          updateTotalWeightForRemove(e);
          evictedCount++;
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
  public long getTotalWeight() {
    return totalWeight;
  }

  @Override
  public long getEvictedWeight() {
    return evictedWeight;
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
  public <T> T runLocked(Job<T> j) {
    synchronized (lock) {
      return j.call();
    }
  }

  protected void removeFromReplacementListOnEvict(Entry e) { removeFromReplacementList(e); }

  /**
   * Find a candidate for eviction. The method may return the identical
   * if called many times but not sufficient more candidates are available.
   */
  @SuppressWarnings("SameParameterValue")
  protected abstract Entry findEvictionCandidate();
  protected abstract void removeFromReplacementList(Entry e);
  protected abstract void insertIntoReplacementList(Entry e);

  @Override
  public String getExtraStatistics() {
    String s = "impl=" + this.getClass().getSimpleName() +
      ", chunkSize=" + chunkSize;
    if (isWeigherPresent()) {
      s +=
        ", maxWeight=" + maxWeight +
        ", totalWeight=" + totalWeight;
    }
    return s;
  }

  /**
   * Update the limits and run eviction loop with chunks to get rid
   * of entries fast. Gives up the lock to send events and allow
   * other cache operations while adaption happens.
   */
  @Override
  public void changeCapacity(long entryCountOrWeight) {
    if (entryCountOrWeight <= 0) {
      throw new IllegalArgumentException("Capacity of 0 is not supported.");
    }
    Entry[] chunk;
    synchronized (lock) {
      modifyCapacityLimits(entryCountOrWeight);
      chunk = fillEvictionChunk(0);
    }
    while (chunk != null) {
      evictChunk(chunk);
      synchronized (lock) {
        chunk = fillEvictionChunk(0);
      }
    }
  }

  private void modifyCapacityLimits(long entryCountOrWeight) {
    if (isWeigherPresent()) {
      updateLimits(maxSize, entryCountOrWeight);
    } else {
      updateLimits(entryCountOrWeight, maxWeight);
    }
  }

}
