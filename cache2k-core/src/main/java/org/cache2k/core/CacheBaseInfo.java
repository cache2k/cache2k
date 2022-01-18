package org.cache2k.core;

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

import org.cache2k.core.api.CommonMetrics;
import org.cache2k.core.api.InternalCache;
import org.cache2k.core.api.InternalCacheInfo;
import org.cache2k.core.eviction.EvictionMetrics;
import org.cache2k.core.util.Util;

import java.text.DecimalFormat;
import java.time.Instant;

/**
 * Stable interface to request information from the cache, the object
 * safes values that need a longer calculation time, other values are
 * requested directly.
 */
class CacheBaseInfo implements InternalCacheInfo {

  private final CommonMetrics metrics;
  private final HeapCache heapCache;
  private final InternalCache cache;
  private final long size;
  private final long infoCreatedTime;
  private final long missCnt;
  /** Hit count from the statistics */
  private final long heapHitCnt;
  private final long correctedPutCnt;
  /** May stay null, if not retrieved under global lock. */
  private final IntegrityState integrityState;
  private final long totalLoadCnt;
  private final long evictedWeight;
  private final EvictionMetrics evictionMetrics;

  /*
   * Consistent copies from heap cache. for 32 bit machines the access
   * is not atomic. We copy the values while under big lock.
   */
  private final long clearedTime;
  private final long newEntryCnt;
  private final long keyMutationCnt;
  private final long removedCnt;
  private final long clearRemovedCnt;
  private final long clearCnt;
  private final long expiredRemoveCnt;
  private final long evictedCnt;
  private final long maxSize;
  private final int evictionRunningCnt;
  private final long internalExceptionCnt;
  private final long maxWeight;
  private final long totalWeight;
  private final String evictionToString;

  CacheBaseInfo(HeapCache heapCache, InternalCache userCache, long now) {
    infoCreatedTime = now;
    cache = userCache;
    this.heapCache = heapCache;
    metrics = heapCache.metrics;
    evictionMetrics = heapCache.eviction.getMetrics();
    newEntryCnt = evictionMetrics.getNewEntryCount();
    expiredRemoveCnt = evictionMetrics.getExpiredRemovedCount();
    evictedCnt = evictionMetrics.getEvictedCount();
    maxSize = evictionMetrics.getMaxSize();
    maxWeight = evictionMetrics.getMaxWeight();
    totalWeight = evictionMetrics.getTotalWeight();
    evictedWeight = evictionMetrics.getEvictedWeight();
    clearedTime = heapCache.clearedTime;
    keyMutationCnt = heapCache.keyMutationCnt;
    removedCnt = evictionMetrics.getRemovedCount();
    clearRemovedCnt = heapCache.clearRemovedCnt;
    clearCnt = heapCache.clearCnt;
    internalExceptionCnt = heapCache.internalExceptionCnt;
    evictionRunningCnt = evictionMetrics.getEvictionRunningCount();
    if (Thread.holdsLock(heapCache.lock)) {
      evictionToString = heapCache.eviction.toString();
      integrityState = heapCache.getIntegrityState();
    } else {
      integrityState = null;
      evictionToString = null;
    }
    size = evictionMetrics.getSize();
    missCnt = metrics.getReadThroughCount() + metrics.getExplicitLoadCount() +
      metrics.getPeekHitNotFreshCount() + metrics.getPeekMissCount();
    heapHitCnt = metrics.getHeapHitCount();
    correctedPutCnt = metrics.getPutNewEntryCount() + metrics.getPutHitCount();
    totalLoadCnt = metrics.getReadThroughCount() + metrics.getExplicitLoadCount() +
      metrics.getRefreshCount();
  }

  String percentString(double d) {
    String s = Double.toString(d);
    return (s.length() > 5 ? s.substring(0, 5) : s) + "%";
  }

  @Override
  public String getName() { return heapCache.name; }
  @Override
  public String getImplementation() { return cache.getClass().getSimpleName(); }

  @Override
  public long getExplicitLoadCount() {
    return metrics.getExplicitLoadCount();
  }

  @Override
  public long getSize() { return size; }
  @Override
  public long getHeapCapacity() { return maxSize; }

  @Override
  public long getMaximumWeight() {
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
  public long getScanCount() {
    return evictionMetrics.getScanCount();
  }

  @Override
  public long getGetCount() {
    return
      heapHitCnt + metrics.getPeekMissCount()
      + metrics.getReadThroughCount() -  metrics.getHeapHitButNoReadCount();
  }
  @Override
  public long getMissCount() { return missCnt; }
  @Override
  public long getNewEntryCount() { return newEntryCnt; }
  @Override
  public long getHeapHitCount() { return heapHitCnt; }
  @Override
  public long getLoadCount() { return totalLoadCnt; }
  @Override
  public long getRefreshCount() { return metrics.getRefreshCount(); }
  @Override
  public long getInternalExceptionCount() { return internalExceptionCnt; }
  @Override
  public long getRefreshRejectedCount() { return metrics.getRefreshRejectedCount(); }
  @Override
  public long getSuppressedExceptionCount() { return metrics.getSuppressedExceptionCount(); }
  @Override
  public long getLoadExceptionCount() {
    return metrics.getLoadExceptionCount() + metrics.getSuppressedExceptionCount(); }
  @Override
  public long getRefreshedHitCount() { return metrics.getRefreshedHitCount(); }
  @Override
  public long getExpiredCount() { return expiredRemoveCnt + metrics.getExpiredKeptCount(); }
  @Override
  public long getEvictedCount() { return evictedCnt; }
  @Override
  public int getEvictionRunningCount() { return evictionRunningCnt; }
  @Override
  public long getRemoveCount() { return removedCnt; }
  @Override
  public long getPutCount() { return correctedPutCnt; }

  @Override
  public long getGoneSpinCount() {
    return metrics.getGoneSpinCount();
  }

  @Override
  public long getKeyMutationCount() { return keyMutationCnt; }
  @Override
  public long getTimerEventCount() { return metrics.getTimerEventCount(); }
  @Override
  public double getHitRate() {
    long cnt = getGetCount();
    return cnt == 0 ? 0.0 : ((cnt - missCnt) * 100D / cnt);
  }
  @Override
  public String getHitRateString() { return percentString(getHitRate()); }
  @Override
  public double getMillisPerLoad() {
    return getLoadCount() == 0 ? 0 : (getLoadMillis() * 1D / getLoadCount()); }
  @Override
  public long getLoadMillis() {
    return cache.getClock().ticksToMillisCeiling(metrics.getLoadTicks());
  }
  @Override
  public boolean isIntegrityFailure() {
    return integrityState.isFailure();
  }
  @Override
  public String getFailedIntegrityChecks() {
    return integrityState.getFailingChecks();
  }
  @Override
  public Instant getStartedTime() { return instantOrNull(heapCache.startedTime); }
  @Override
  public Instant getClearedTime() { return instantOrNull(clearedTime); }
  @Override
  public Instant getInfoCreatedTime() { return instantOrNull(infoCreatedTime); }

  Instant instantOrNull(long ticks) {
    return ticks == 0 ? null : cache.getClock().ticksToInstant(ticks);
  }

  private static String timestampToString(Instant t) {
    if (t == null) {
      return "-";
    }
    return Util.formatTime(t);
  }

  @Override
  public long getClearCount() {
    return clearCnt;
  }

  public long getClearedEntriesCount() {
    return clearRemovedCnt;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Cache(");
    CacheManagerImpl cm = (CacheManagerImpl) (cache.getCacheManager());
    sb.append("name=").append(cache.getQualifiedName()).append(", ")
      .append("size=").append(getSize()).append(", ");
    if (getHeapCapacity() >= 0) {
      sb.append("capacity=").append(
        getHeapCapacity() != Long.MAX_VALUE ? getHeapCapacity() : "unlimited").append(", ");
    } else {
      sb.append("maximumWeight=").append(
        getMaximumWeight() != Long.MAX_VALUE ? getMaximumWeight() : "unlimited").append(", ");
      sb.append("currentWeight=").append(getTotalWeight()).append(", ");
    }
    sb.append("get=").append(getGetCount()).append(", ")
      .append("miss=").append(getMissCount()).append(", ")
      .append("put=").append(getPutCount()).append(", ")
      .append("load=").append(getLoadCount()).append(", ")
      .append("reload=").append(getExplicitLoadCount()).append(", ")
      .append("heapHit=").append(getHeapHitCount()).append(", ")
      .append("refresh=").append(getRefreshCount()).append(", ")
      .append("refreshRejected=").append(getRefreshRejectedCount()).append(", ")
      .append("refreshedHit=").append(getRefreshedHitCount()).append(", ")
      .append("loadException=").append(getLoadExceptionCount()).append(", ")
      .append("suppressedException=").append(getSuppressedExceptionCount()).append(", ")
      .append("new=").append(getNewEntryCount()).append(", ")
      .append("expire=").append(getExpiredCount()).append(", ")
      .append("remove=").append(getRemoveCount()).append(", ")
      .append("clear=").append(getClearCount()).append(", ")
      .append("removeByClear=").append(getClearedEntriesCount()).append(", ")
      .append("evict=").append(getEvictedCount()).append(", ")
      .append("timer=").append(getTimerEventCount()).append(", ")
      .append("goneSpin=").append(getGoneSpinCount()).append(", ")
      .append("hitRate=").append(getHitRateString()).append(", ")
      .append("msecs/load=").append(formatMillisPerLoad(getMillisPerLoad())).append(", ")
      .append("created=").append(timestampToString(getStartedTime())).append(", ")
      .append("cleared=").append(timestampToString(getClearedTime())).append(", ")
      .append("infoCreated=").append(timestampToString(getInfoCreatedTime())).append(", ")
      .append("impl=").append(getImplementation()).append(", ")
      .append("evictionRunning=").append(getEvictionRunningCount()).append(", ")
      .append("keyMutation=").append(getKeyMutationCount()).append(", ")
      .append("evictionScanCount=").append(evictionMetrics.getScanCount()).append(", ")
      .append("internalException=").append(getInternalExceptionCount()).append(", ")
      .append("version=").append(cm.getProvider().getVersion());
    if (evictionToString != null && !evictionToString.isEmpty()) {
      sb.append(", ");
      sb.append(evictionToString);
    }
    sb.append(")");
    return sb.toString();
  }

  static String formatMillisPerLoad(double val) {
    if (val < 0) {
      return "-";
    }
    DecimalFormat f = new DecimalFormat("#.###");
    return f.format(val);
  }

}
