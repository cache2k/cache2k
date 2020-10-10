package org.cache2k.core;

/*
 * #%L
 * cache2k core implementation
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

import org.cache2k.core.api.CommonMetrics;
import org.cache2k.core.api.HealthInfoElement;
import org.cache2k.core.api.InternalCache;
import org.cache2k.core.api.InternalCacheInfo;
import org.cache2k.core.eviction.EvictionMetrics;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import static org.cache2k.core.util.Util.formatMillis;

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
  private int infoCreationDeltaMs;
  private final long missCnt;
  private final long hitCnt;
  private final long correctedPutCnt;
  private String extraStatistics;
  private final IntegrityState integrityState;
  private final long totalLoadCnt;

  private int loaderThreadsLimit = -1;
  private long asyncLoadsStarted = -1;
  private long asyncLoadsInFlight = -1;
  private int loaderThreadsMaxActive = -1;
  private long evictedWeight;

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

  CacheBaseInfo(HeapCache heapCache, InternalCache userCache, long now) {
    infoCreatedTime = now;
    cache = userCache;
    this.heapCache = heapCache;
    metrics = heapCache.metrics;
    EvictionMetrics em = heapCache.eviction.getMetrics();
    newEntryCnt = em.getNewEntryCount();
    expiredRemoveCnt = em.getExpiredRemovedCount();
    evictedCnt = em.getEvictedCount();
    maxSize = em.getMaxSize();
    maxWeight = em.getMaxWeight();
    totalWeight = em.getTotalWeight();
    evictedWeight = em.getEvictedWeight();
    clearedTime = heapCache.clearedTime;
    keyMutationCnt = heapCache.keyMutationCnt;
    removedCnt = em.getRemovedCount();
    clearRemovedCnt = heapCache.clearRemovedCnt;
    clearCnt = heapCache.clearCnt;
    internalExceptionCnt = heapCache.internalExceptionCnt;
    evictionRunningCnt = em.getEvictionRunningCount();
    integrityState = heapCache.getIntegrityState();
    extraStatistics = em.getExtraStatistics();
    if (extraStatistics.startsWith(", ")) {
      extraStatistics = extraStatistics.substring(2);
    }
    size = this.heapCache.getLocalSize();
    missCnt = metrics.getReadThroughCount() + metrics.getExplicitLoadCount() +
      metrics.getPeekHitNotFreshCount() + metrics.getPeekMissCount();
    hitCnt = em.getHitCount();
    correctedPutCnt = metrics.getPutNewEntryCount() + metrics.getPutHitCount();
    if (heapCache.loaderExecutor instanceof ExclusiveExecutor) {
      ThreadPoolExecutor ex =
        ((ExclusiveExecutor) heapCache.loaderExecutor).getThreadPoolExecutor();
      asyncLoadsInFlight = ex.getActiveCount();
      asyncLoadsStarted = ex.getTaskCount();
      loaderThreadsLimit = ex.getCorePoolSize();
      loaderThreadsMaxActive = ex.getLargestPoolSize();
    }
    totalLoadCnt = metrics.getReadThroughCount() + metrics.getExplicitLoadCount() +
      metrics.getRefreshCount();
  }

  String percentString(double d) {
    String s = Double.toString(d);
    return (s.length() > 5 ? s.substring(0, 5) : s) + "%";
  }

  public void setInfoCreationDeltaMs(int millis) {
    infoCreationDeltaMs = millis;
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
  public long getGetCount() {
    return
      hitCnt + metrics.getPeekMissCount()
      + metrics.getReadThroughCount() -  metrics.getHeapHitButNoReadCount();
  }
  @Override
  public long getMissCount() { return missCnt; }
  @Override
  public long getNewEntryCount() { return newEntryCnt; }
  @Override
  public long getHeapHitCount() { return hitCnt; }
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
    return getLoadCount() == 0 ? 0 : (metrics.getLoadMillis() * 1D / getLoadCount()); }
  @Override
  public long getLoadMillis() { return metrics.getLoadMillis(); }
  @Override
  public String getIntegrityDescriptor() { return integrityState.getStateDescriptor(); }
  @Override
  public long getStartedTime() { return heapCache.startedTime; }
  @Override
  public long getClearedTime() { return clearedTime; }
  @Override
  public long getInfoCreatedTime() { return infoCreatedTime; }
  @Override
  public int getInfoCreationDeltaMs() { return infoCreationDeltaMs; }

  @Override
  public Collection<HealthInfoElement> getHealth() {
    List<HealthInfoElement> l = new ArrayList<HealthInfoElement>();
    if (integrityState.getStateFlags() > 0) {
      l.add(new HealthBean(cache, "integrity",
        HealthInfoElement.FAILURE, "Integrity check error: " + integrityState.getStateFlags()));
    }
    if (getKeyMutationCount() > 0) {
      l.add(new HealthBean(cache, "keyMutation",
        HealthInfoElement.WARNING, "key mutation detected"));
    }
    if (getInternalExceptionCount() > 0) {
      l.add(new HealthBean(cache, "internalException",
        HealthInfoElement.WARNING, "internal exception"));
    }
    return l;
  }
  @Override
  public long getAsyncLoadsStarted() {
    return asyncLoadsStarted;
  }

  @Override
  public long getAsyncLoadsInFlight() {
    return asyncLoadsInFlight;
  }

  @Override
  public int getLoaderThreadsLimit() {
    return loaderThreadsLimit;
  }

  @Override
  public int getLoaderThreadsMaxActive() {
    return loaderThreadsMaxActive;
  }

  @Override
  public String getExtraStatistics() {
    return extraStatistics;
  }

  private static String timestampToString(long t) {
    if (t == 0) {
      return "-";
    }
    return formatMillis(t);
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
    sb.append("name=").append(BaseCache.nameQualifier(cache)).append(", ")
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
      .append("asyncLoadsStarted=").append(asyncLoadsStarted).append(", ")
      .append("asyncLoadsInFlight=").append(asyncLoadsInFlight).append(", ")
      .append("loaderThreadsLimit=").append(loaderThreadsLimit).append(", ")
      .append("loaderThreadsMaxActive=").append(loaderThreadsMaxActive).append(", ")
      .append("created=").append(timestampToString(getStartedTime())).append(", ")
      .append("cleared=").append(timestampToString(getClearedTime())).append(", ")
      .append("infoCreated=").append(timestampToString(getInfoCreatedTime())).append(", ")
      .append("infoCreationDeltaMs=").append(getInfoCreationDeltaMs()).append(", ")
      .append("impl=").append(getImplementation()).append(", ")
      .append(getExtraStatistics()).append(", ")
      .append("evictionRunning=").append(getEvictionRunningCount()).append(", ")
      .append("keyMutation=").append(getKeyMutationCount()).append(", ")
      .append("internalException=").append(getInternalExceptionCount()).append(", ")
      .append("integrityState=").append(getIntegrityDescriptor()).append(", ")
      .append("version=").append(cm.getProvider().getVersion());
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

  static final double EXPONENT_CONSTANT = -0.011;
  static final int SLOT_SIZE_MINIMUM = 5;

  static int hashQuality(int noCollisionPercent, int longestSlot) {
    if (longestSlot == 0) {
      return 100;
    }
    int correctionForOversizeSlot = (int)
      ((1 - Math.exp(EXPONENT_CONSTANT * Math.max(0, longestSlot - SLOT_SIZE_MINIMUM))) * 100);
    int quality = noCollisionPercent - correctionForOversizeSlot;
    return Math.max(0, Math.min(100, quality));
  }

  static class HealthBean implements HealthInfoElement {

    String id;
    String message;
    String level;
    InternalCache cache;

    HealthBean(InternalCache cache, String id, String level, String message) {
      this.cache = cache;
      this.id = id;
      this.level = level;
      this.message = message;
    }

    @Override
    public InternalCache getCache() {
      return cache;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getLevel() {
      return level;
    }

    @Override
    public String getMessage() {
      return message;
    }
  }

}
