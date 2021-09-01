 package org.cache2k.core.eviction;

/*
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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

import org.cache2k.config.Cache2kConfig;
import org.cache2k.core.Entry;
import org.cache2k.core.ExceptionWrapper;
import org.cache2k.core.IntegerTo16BitFloatingPoint;
import org.cache2k.operation.Weigher;

import java.util.function.Supplier;

 /**
 * Base class for different eviction algorithms, implementing statistics counting and
 * chunking.
 *
 * <p>Eviction happens in chunks, to do more work in one thread. Eviction may happen
 * in different threads in parallel, in case lots of concurrent inserts triggers
 * eviction. Theoretically it could be more effective to assign only one thread
 * for eviction and exploit locality and make other threads carrying out insert
 * wait to avoid overflowing the system. Since eviction listeners may be called
 * as well, it is more general useful to run eviction in multiple threads in parallel if needed.
 *
 * <p>An eviction is basically: finding an eviction candidate based on the eviction
 * algorithm {@link #findEvictionCandidate()}, mark entry for processing and call
 * the eviction listener,
 *
 * @author Jens Wilke
 */
@SuppressWarnings({"WeakerAccess", "SynchronizationOnLocalVariableOrMethodParameter", "unchecked",
  "rawtypes"})
public abstract class AbstractEviction implements Eviction, EvictionMetrics {

  public static final int MINIMAL_CHUNK_SIZE = 4;
  public static final int MAXIMAL_CHUNK_SIZE = 64;
  public static final long MINIMUM_CAPACITY_FOR_CHUNKING = 1000;

  private final Weigher weigher;
  protected final HeapCacheForEviction heapCache;
  private final Object lock = new Object();
  private final InternalEvictionListener listener;
  private final boolean noListenerCall;
  private final boolean noChunking;

  /**
   * Set when size is reached.
   */
  private long estimatedEntryCapacity = 0;

  /**
   * Number of entries being evicted in one go.
   */
  private int chunkSize = 1;

  /**
   * Size limit in number of entries. Derived from {@link Cache2kConfig#getEntryCapacity()}
   * May be changed during runtime. Guarded by lock.
   */
  protected long maxSize;

  /**
   * Maximum allowed weight.
   * May be changed during runtime. Guarded by lock.
   */
  protected long maxWeight;

  /**
   * This passes on the working chunk array from the last eviction to safe garbage collection.
   * May be changed during runtime. Guarded by lock.
   */
  private Entry[] evictChunkReuse = null;

  private int evictionRunningCount = 0;
  private long newEntryCounter;
  private long removedCnt;
  private long expiredRemovedCnt;
  private long virginRemovedCnt;
  private long evictedCount;
  private long totalWeight;
  private long evictedWeight;

  public AbstractEviction(HeapCacheForEviction heapCache, InternalEvictionListener listener,
                          long maxSize, Weigher weigher, long maxWeight,
                          boolean noChunking) {
    this.weigher = weigher;
    this.heapCache = heapCache;
    this.listener = listener;
    noListenerCall = listener == InternalEvictionListener.NO_OPERATION;

    this.noChunking = noChunking;
    this.maxSize = maxSize;
    this.maxWeight = maxWeight;
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

  private static int calculateChunkSize(boolean noChunking, long maxSize) {
    if (noChunking) { return 1; }
    if (maxSize < MINIMUM_CAPACITY_FOR_CHUNKING && maxSize >= 0) {
      return 1;
    }
    return Math.min(
      MAXIMAL_CHUNK_SIZE,
      MINIMAL_CHUNK_SIZE + Runtime.getRuntime().availableProcessors() - 1);
  }

  @Override
  public boolean isWeigherPresent() {
    return weigher != null;
  }

  /**
   * Call weigher with the value.
   * Exceptions have the minimum weight. A weight of 0 is legal.
   */
  private int calculateWeight(Entry e, Object v) {
    if (v instanceof ExceptionWrapper) { return 1; }
    int weight = weigher.weigh(e.getKey(), v);
    if (weight < 0) {
      throw new IllegalArgumentException("weight must be positive.");
    }
    return weight;
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

  /**
   * Update total weight in this eviction segment. The total weight differs from
   * the accumulated entry weight, since it is based on the stored decompressed, compressed
   * weight. We calculate based on the stored weight, because we don't want to call the
   * weigher for deletion again, which may cause wrong counts.
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

  private static int decompressWeight(int weight) {
    return IntegerTo16BitFloatingPoint.expand(weight);
  }

  private static int compressWeight(int weight) {
    return IntegerTo16BitFloatingPoint.compress(weight);
  }

  /**
   * Remove and update statistics if not removed already.
   * An entry may be removed if we race with {@link #removeAll} which is triggered
   * by {@link org.cache2k.Cache#clear}
   */
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

  private boolean isEvictionNeeded(int spaceNeeded) {
    if (isWeigherPresent()) {
      return totalWeight + spaceNeeded > maxWeight && getSize() > 0;
    } else {
      return getSize() + spaceNeeded - evictionRunningCount > maxSize;
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

  /**
   * Perform eviction, if needed.
   *
   * <p>We might do a second eviction attempt, if not within limits.
   * An eviction might be skipped after the entry is elected for eviction
   * from the eviction algorithm, if its currently processing.
   *
   * <p>If a weigher is present we might need to evict more than one entry.
   */
  private void evictEventually(int spaceNeeded) {
    Entry[] chunk;
    synchronized (lock) {
      chunk = fillEvictionChunk(spaceNeeded);
    }
    if (chunk == null) { return; }
    boolean needsEviction = evictChunk(chunk, spaceNeeded);
    if (!needsEviction) { return; }
    long loop = 1;
    if (weigher != null) {
      synchronized (lock) {
        loop = getSize();
      }
    }
    while (needsEviction && loop-- > 0) {
      synchronized (lock) {
        chunk = fillEvictionChunk(spaceNeeded);
      }
      needsEviction = evictChunk(chunk, spaceNeeded);
    }
  }

  private Entry[] fillEvictionChunk(int spaceNeeded) {
    if (!isEvictionNeeded(spaceNeeded)) {
      return null;
    }
    if (evictionRunningCount == 0 && estimatedEntryCapacity < getSize()) {
      updatesSizesAfterLimitReached();
    }
    Entry[] chunk = evictChunkReuse;
    evictChunkReuse = null;
    if (chunk == null) {
      chunk = new Entry[chunkSize];
    }
    evictionRunningCount += chunk.length;
    for (int i = 0; i < chunk.length; i++) {
      chunk[i] = findEvictionCandidate();
    }
    return chunk;
  }

  private boolean evictChunk(Entry[] chunk, int spaceNeeded) {
    if (chunk == null) { return false; }
    int processCount = removeFromHash(chunk);
    synchronized (lock) {
      if (processCount > 0) {
        removeAllFromReplacementListOnEvict(chunk);
      }
      evictionRunningCount -= chunk.length;
      evictChunkReuse = chunk;
      return isEvictionNeeded(spaceNeeded);
    }
  }

  /**
   * Eviction was run successfully. Update internal sizes.
   * This works regardless whether a max entry count is configured
   * or a weigher is used.
   */
  private void updatesSizesAfterLimitReached() {
    estimatedEntryCapacity = getSize();
    updateHotMax();
    int targetChunkSize = calculateChunkSize(noChunking, getSize());
    if (targetChunkSize != chunkSize) {
      chunkSize = targetChunkSize;
      evictChunkReuse = null;
    }
  }

  private int removeFromHash(Entry[] chunk) {
    if (noListenerCall) {
      return removeFromHashWithoutListener(chunk);
    }
    return removeFromHashWithListener(chunk);
  }

  /**
   * If there is concurrent processing going on, we skip that entry.
   * An entry may have been also already evicted in another task.
   */
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

  /**
   * Same as {@link #removeFromHash(Entry[])}
   * Before calling the listener, we need to lock the entry, to keep other
   * operations or evictions in concurrent tasks away from it.
   */
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
  public <T> T runLocked(Supplier<T> j) {
    synchronized (lock) {
      return j.get();
    }
  }

  @Override
  public String getExtraStatistics() {
    String s = "impl=" + this.getClass().getSimpleName() +
      ", chunkSize=" + chunkSize;
    if (isWeigherPresent()) {
      s +=
        ", maxWeight=" + maxWeight +
        ", totalWeight=" + totalWeight;
    } else {
      s +=
        ", maxSize=" + maxSize;
    }
    s +=
      ", size=" + getSize();
    return s;
  }

  /**
   * Update the limits and run eviction loop with chunks to get rid
   * of entries fast. Gives up the lock to send events and allow
   * other cache operations while adaption happens.
   */
  @Override
  public void changeCapacity(long entryCountOrWeight) {
    if (entryCountOrWeight < 0) {
      throw new IllegalArgumentException("Negative capacity or weight");
    }
    if (entryCountOrWeight <= 0) {
      throw new IllegalArgumentException("Capacity or weight of 0 is not supported");
    }
    Entry[] chunk;
    synchronized (lock) {
      modifyCapacityLimits(entryCountOrWeight);
      chunk = fillEvictionChunk(0);
    }
    while (chunk != null) {
      evictChunk(chunk, 0);
      synchronized (lock) {
        chunk = fillEvictionChunk(0);
        if (chunk == null) {
          updatesSizesAfterLimitReached();
        }
      }
    }
  }

  private void modifyCapacityLimits(long entryCountOrWeight) {
    if (isWeigherPresent()) {
      maxWeight = entryCountOrWeight;
    } else {
      maxSize = entryCountOrWeight;
    }
  }

  @Override
  public final long removeAll() {
    long removedCount = removeAllFromReplacementList();
    totalWeight = 0;
    return removedCount;
  }

  /**
   * Remove entries from the replacement list without locking the entry itself.
   */
  protected abstract long removeAllFromReplacementList();

  /**
   * Place the entry as a new entry into the eviction data structures.
   */
  protected abstract void insertIntoReplacementList(Entry e);

  /**
   * Find a candidate for eviction. The method may return the identical
   * if called many times but not sufficient more candidates are available.
   * In any situation, subsequent calls must iterate all entries.
   */
  protected abstract Entry findEvictionCandidate();

  /**
   * Identical to {@link #removeFromReplacementList(Entry)} by default but
   * allows the eviction algorithm to do additional bookkeeping of eviction history.
   */
  protected void removeFromReplacementListOnEvict(Entry e) { removeFromReplacementList(e); }

  /**
   * Remove entry from the eviction data structures, because it was evicted or deleted.
   */
  protected abstract void removeFromReplacementList(Entry e);

  /**
   * Gets called when eviction is needed. Used by the eviction algorithm to update
   * the clock sizes.
   */
  protected void updateHotMax() { }

}
