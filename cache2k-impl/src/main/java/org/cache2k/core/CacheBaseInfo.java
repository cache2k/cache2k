package org.cache2k.core;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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

  private CommonMetrics metrics;
  private StorageMetrics storageMetrics = StorageMetrics.DUMMY;
  private HeapCache heapCache;
  private InternalCache cache;
  private long size;
  private long infoCreatedTime;
  private int infoCreationDeltaMs;
  private long missCnt;
  private long storageMissCnt;
  private long hitCnt;
  private long correctedPutCnt;
  private CollisionInfo collisionInfo;
  private String extraStatistics;
  private IntegrityState integrityState;
  private long totalLoadCnt;

  private int loaderThreadsLimit = 0;
  private long asyncLoadsStarted = 0;
  private long asyncLoadsInFlight = 0;
  private int loaderThreadsMaxActive = 0;

  /*
   * Consistent copies from heap cache. for 32 bit machines the access
   * is not atomic. We copy the values while under big lock.
   */
  private long clearedTime;
  private long newEntryCnt;
  private long keyMutationCnt;
  private long removedCnt;
  private long clearRemovedCnt;
  private long clearCnt;
  private long expiredRemoveCnt;
  private long evictedCnt;
  private long maxSize;
  private int evictionRunningCnt;
  private long internalExceptionCnt;

  public CacheBaseInfo(HeapCache _heapCache, InternalCache _userCache, long now) {
    infoCreatedTime = now;
    cache = _userCache;
    heapCache = _heapCache;
    metrics = _heapCache.metrics;
    EvictionMetrics em = _heapCache.eviction.getMetrics();
    newEntryCnt = em.getNewEntryCount();
    expiredRemoveCnt = em.getExpiredRemovedCount();
    evictedCnt = em.getEvictedCount();
    maxSize = em.getMaxSize();
    clearedTime = _heapCache.clearedTime;
    keyMutationCnt = _heapCache.keyMutationCnt;
    removedCnt = em.getRemovedCount();
    clearRemovedCnt = _heapCache.clearRemovedCnt;
    clearCnt = _heapCache.clearCnt;
    internalExceptionCnt = _heapCache.internalExceptionCnt;
    evictionRunningCnt = em.getEvictionRunningCount();
    integrityState = _heapCache.getIntegrityState();
    storageMetrics = _userCache.getStorageMetrics();
    collisionInfo = new CollisionInfo();
    _heapCache.hash.calcHashCollisionInfo(collisionInfo);
    extraStatistics = em.getExtraStatistics();
    if (extraStatistics.startsWith(", ")) {
      extraStatistics = extraStatistics.substring(2);
    }
    size = heapCache.getLocalSize();
    missCnt = metrics.getLoadCount() + metrics.getReloadCount() + metrics.getPeekHitNotFreshCount() + metrics.getPeekMissCount();
    storageMissCnt = storageMetrics.getReadMissCount() + storageMetrics.getReadNonFreshCount();
    hitCnt = em.getHitCount();
    correctedPutCnt = metrics.getPutNewEntryCount() + metrics.getPutHitCount() + metrics.getPutNoReadHitCount();
    if (_heapCache.loaderExecutor instanceof ExclusiveExecutor) {
      ThreadPoolExecutor ex = ((ExclusiveExecutor) _heapCache.loaderExecutor).getThreadPoolExecutor();
      asyncLoadsInFlight = ex.getActiveCount();
      asyncLoadsStarted = ex.getTaskCount();
      loaderThreadsLimit = ex.getCorePoolSize();
      loaderThreadsMaxActive = ex.getLargestPoolSize();
    }
    totalLoadCnt = metrics.getLoadCount() + metrics.getReloadCount() + metrics.getRefreshCount();
  }

  String percentString(double d) {
    String s = Double.toString(d);
    return (s.length() > 5 ? s.substring(0, 5) : s) + "%";
  }

  public void setInfoCreationDeltaMs(int _millis) {
    infoCreationDeltaMs = _millis;
  }

  @Override
  public String getName() { return heapCache.name; }
  @Override
  public String getImplementation() { return cache.getClass().getSimpleName(); }

  @Override
  public long getReloadCount() {
    return metrics.getReloadCount();
  }

  @Override
  public long getSize() { return size; }
  @Override
  public long getHeapCapacity() { return maxSize; }
  @Override
  public long getStorageHitCnt() { return storageMetrics.getReadHitCount(); }

  @Override
  public long getStorageMissCnt() { return storageMissCnt; }
  @Override
  public long getGetCount() {
    long _putHit = metrics.getPutNoReadHitCount();
    long _heapHitButNoRead = metrics.getHeapHitButNoReadCount();
    return
      hitCnt + metrics.getPeekMissCount()
      + metrics.getLoadCount() - _putHit - _heapHitButNoRead;
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
  public long getRefreshFailedCount() { return metrics.getRefreshFailedCount(); }
  @Override
  public long getSuppressedExceptionCount() { return metrics.getSuppressedExceptionCount(); }
  @Override
  public long getLoadExceptionCount() { return metrics.getLoadExceptionCount() + metrics.getSuppressedExceptionCount(); }
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

  /** How many items will be accessed with collision */
  @Override
  public int getNoCollisionPercent() {
    if (size == 0) {
      return 100;
    }
    return
      (int) ((size - collisionInfo.collisionCnt) * 100 / size);
  }
  @Override
  public int getHashQuality() {
    return hashQuality(getNoCollisionPercent(), getHashLongestSlotSize());
  }
  @Override
  public double getMillisPerLoad() { return getLoadCount() == 0 ? 0 : (metrics.getLoadMillis() * 1D / getLoadCount()); }
  @Override
  public long getLoadMillis() { return metrics.getLoadMillis(); }
  @Override
  public int getHashCollisionCount() { return collisionInfo.collisionCnt; }
  @Override
  public int getHashCollisionSlotCount() { return collisionInfo.collisionSlotCnt; }
  @Override
  public int getHashLongestSlotSize() { return collisionInfo.longestCollisionSize; }
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
      l.add(new HealthBean(cache, "integrity", HealthInfoElement.FAILURE, "Integrity check error: " + integrityState.getStateFlags()));
    }
    final int _WARNING_THRESHOLD = HeapCache.TUNABLE.hashQualityWarningThreshold;
    final int _ERROR_THRESHOLD = HeapCache.TUNABLE.hashQualityErrorThreshold;
    if (getHashQuality() < _ERROR_THRESHOLD) {
      l.add(new HealthBean(cache, "hashing", HealthInfoElement.FAILURE, "hash quality is " + getHashQuality() + " (threshold: " + _ERROR_THRESHOLD  + ")"));
    } else if (getHashQuality() < _WARNING_THRESHOLD) {
      l.add(new HealthBean(cache, "hashing", HealthInfoElement.WARNING, "hash quality is " + getHashQuality() + " (threshold: " + _WARNING_THRESHOLD + ")"));
    }
    if (getKeyMutationCount() > 0) {
      l.add(new HealthBean(cache, "keyMutation", HealthInfoElement.WARNING, "key mutation detected"));
    }
    if (getInternalExceptionCount() > 0) {
      l.add(new HealthBean(cache, "internalException", HealthInfoElement.WARNING, "internal exception"));
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
    sb.append("Cache{");
    CacheManagerImpl cm = (CacheManagerImpl) (cache.getCacheManager());
    if (!cache.getCacheManager().isDefaultManager()) {
      sb.append(cm.getName()).append(':');
    }
    sb.append(heapCache.name).append("}(");
    sb.append("size=").append(getSize()).append(", ")
      .append("capacity=").append(getHeapCapacity() != Long.MAX_VALUE ? getHeapCapacity() : "unlimited").append(", ")
      .append("get=").append(getGetCount()).append(", ")
      .append("miss=").append(getMissCount()).append(", ")
      .append("put=").append(getPutCount()).append(", ")
      .append("load=").append(getLoadCount()).append(", ")
      .append("reload=").append(getReloadCount()).append(", ")
      .append("heapHit=").append(getHeapHitCount()).append(", ")
      .append("refresh=").append(getRefreshCount()).append(", ")
      .append("refreshFailed=").append(getRefreshFailedCount()).append(", ")
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
      .append("collisions=").append(getHashCollisionCount()).append(", ")
      .append("collisionSlots=").append(getHashCollisionSlotCount()).append(", ")
      .append("longestSlot=").append(getHashLongestSlotSize()).append(", ")
      .append("hashQuality=").append(getHashQuality()).append(", ")
      .append("noCollisionPercent=").append(getNoCollisionPercent()).append(", ")
      .append("impl=").append(getImplementation()).append(", ")
      .append(getExtraStatistics()).append(", ")
      .append("evictionRunning=").append(getEvictionRunningCount()).append(", ")
      .append("keyMutation=").append(getKeyMutationCount()).append(", ")
      .append("internalException=").append(getInternalExceptionCount()).append(", ")
      .append("integrityState=").append(getIntegrityDescriptor()).append(", ")
      .append("version=").append(cm.getProvider().getVersion()).append(")");
    return sb.toString();
  }

  static String formatMillisPerLoad(double val) {
    if (val < 0) {
      return "-";
    }
    DecimalFormat f = new DecimalFormat("#.###");
    return f.format(val);
  }

  static int hashQuality(int _noCollisionPercent, int _longestSlot) {
    if (_longestSlot == 0) {
      return 100;
    }
    final double _EXPONENT_CONSTANT = -0.011;
    final int _SLOT_SIZE_MINIMUM = 5;
    int _correctionForOversizeSlot = (int)
      ((1 - Math.exp(_EXPONENT_CONSTANT * Math.max(0, _longestSlot - _SLOT_SIZE_MINIMUM))) * 100);
    int _quality = _noCollisionPercent - _correctionForOversizeSlot;
    return Math.max(0, Math.min(100, _quality));
  }

  static class HealthBean implements HealthInfoElement {

    String id;
    String message;
    String level;
    InternalCache cache;

    public HealthBean(final InternalCache _cache, final String _id, final String _level, final String _message) {
      cache = _cache;
      id = _id;
      level = _level;
      message = _message;
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
