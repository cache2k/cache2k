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

import org.cache2k.core.storageApi.StorageAdapter;

import java.util.concurrent.ThreadPoolExecutor;

import static org.cache2k.core.util.Util.formatMillis;

/**
 * Stable interface to request information from the cache, the object
 * safes values that need a longer calculation time, other values are
 * requested directly.
 */
class CacheBaseInfo implements InternalCacheInfo {

  StorageAdapter storage;
  CommonMetrics metrics;
  StorageMetrics storageMetrics = StorageMetrics.DUMMY;
  private HeapCache heapCache;
  int size;
  long creationTime;
  int creationDeltaMs;
  long missCnt;
  long storageMissCnt;
  long storageLoadCnt;
  long newEntryCnt;
  long hitCnt;
  long correctedPutCnt;
  long usageCnt;
  Hash.CollisionInfo collisionInfo;
  String extraStatistics;
  IntegrityState integrityState;
  long asyncLoadsStarted = 0;
  long asyncLoadsInFlight = 0;
  int loaderThreadsLimit = 0;
  int loaderThreadsMaxActive = 0;
  long totalLoadCnt;

  public CacheBaseInfo(HeapCache _heapCache, InternalCache _userCache) {
    this.heapCache = _heapCache;
    metrics = _heapCache.metrics;
    integrityState = _heapCache.getIntegrityState();
    storageMetrics = _userCache.getStorageMetrics();
    collisionInfo = new Hash.CollisionInfo();
    Hash.calcHashCollisionInfo(collisionInfo, _heapCache.mainHash);
    Hash.calcHashCollisionInfo(collisionInfo, _heapCache.refreshHash);
    extraStatistics = _heapCache.getExtraStatistics();
    if (extraStatistics.startsWith(", ")) {
      extraStatistics = extraStatistics.substring(2);
    }
    size = _userCache.getTotalEntryCount();
    missCnt = metrics.getLoadCount() + metrics.getReloadCount() + _heapCache.peekHitNotFreshCnt + _heapCache.peekMissCnt;
    storageMissCnt = storageMetrics.getReadMissCount() + storageMetrics.getReadNonFreshCount();
    storageLoadCnt = storageMissCnt + storageMetrics.getReadHitCount();
    newEntryCnt = _heapCache.newEntryCnt;
    hitCnt = _heapCache.getHitCnt();
    correctedPutCnt = metrics.getPutNewEntryCount() + metrics.getPutHitCount() + metrics.getPutNoReadHitCount() - _heapCache.putButExpiredCnt;
    usageCnt =
            hitCnt + newEntryCnt + _heapCache.peekMissCnt + metrics.getPutHitCount() + metrics.getRemoveCount();
    if (_heapCache.loaderExecutor instanceof ThreadPoolExecutor) {
      ThreadPoolExecutor ex = (ThreadPoolExecutor) _heapCache.loaderExecutor;
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

  @Override
  public String getName() { return heapCache.name; }
  @Override
  public String getImplementation() { return heapCache.getClass().getSimpleName(); }

  @Override
  public long getLoadButHitCnt() {
    return metrics.getReloadCount();
  }

  @Override
  public long getSize() { return size; }
  @Override
  public long getMaxSize() { return heapCache.maxSize; }
  @Override
  public long getStorageHitCnt() { return storageMetrics.getReadHitCount(); }

  @Override
  public long getStorageMissCnt() { return storageMissCnt; }
  @Override
  public long getReadUsageCnt() {
    long _putHit = metrics.getPutNoReadHitCount();
    long _containsBitHit = metrics.getContainsButHitCount();
    long _heapHitButNoRead = metrics.getHeapHitButNoReadCount();
    return
      hitCnt + heapCache.peekMissCnt
      + metrics.getLoadCount() - _putHit - _containsBitHit - _heapHitButNoRead;
  }
  @Override
  public long getUsageCnt() { return usageCnt; }
  @Override
  public long getMissCnt() { return missCnt; }
  @Override
  public long getNewEntryCnt() { return newEntryCnt; }
  @Override
  public long getLoadCnt() { return totalLoadCnt; }
  @Override
  public long getRefreshCnt() { return metrics.getRefreshCount(); }
  @Override
  public long getInternalExceptionCnt() { return metrics.getInternalExceptionCount(); }
  @Override
  public long getRefreshSubmitFailedCnt() { return heapCache.refreshSubmitFailedCnt; }
  @Override
  public long getSuppressedExceptionCnt() { return metrics.getSuppressedExceptionCount(); }
  @Override
  public long getLoadExceptionCnt() { return metrics.getLoadExceptionCount() + metrics.getSuppressedExceptionCount(); }
  @Override
  public long getRefreshHitCnt() { return heapCache.refreshHitCnt; }
  @Override
  public long getExpiredCnt() { return heapCache.getExpiredCnt(); }
  @Override
  public long getEvictedCnt() { return heapCache.evictedCnt; }
  @Override
  public long getRemovedCnt() { return metrics.getRemoveCount(); }
  @Override
  public long getPutNewEntryCnt() { return heapCache.putNewEntryCnt; }
  @Override
  public long getPutCnt() { return correctedPutCnt; }
  @Override
  public long getKeyMutationCnt() { return heapCache.keyMutationCount; }
  @Override
   public long getTimerEventCnt() { return heapCache.metrics.getTimerEventCount(); }
  @Override
  public double getDataHitRate() {
    long cnt = getReadUsageCnt();
    return cnt == 0 ? 0.0 : ((cnt - missCnt) * 100D / cnt);
  }
  @Override
  public String getDataHitString() { return percentString(getDataHitRate()); }
  @Override
  public double getEntryHitRate() { return usageCnt == 0 ? 100 : (usageCnt - newEntryCnt + metrics.getPutNewEntryCount()) * 100D / usageCnt; }
  @Override
  public String getEntryHitString() { return percentString(getEntryHitRate()); }
  /** How many items will be accessed with collision */
  @Override
  public int getCollisionPercentage() {
    return
      (size - collisionInfo.collisionCnt) * 100 / size;
  }
  /** 100 means each collision has its own slot */
  @Override
  public int getSlotsPercentage() {
    return collisionInfo.collisionSlotCnt * 100 / collisionInfo.collisionCnt;
  }
  @Override
  public int getHq0() {
    return Math.max(0, 105 - collisionInfo.longestCollisionSize * 5) ;
  }
  @Override
  public int getHq1() {
    final int _metricPercentageBase = 60;
    int m =
      getCollisionPercentage() * ( 100 - _metricPercentageBase) / 100 + _metricPercentageBase;
    m = Math.min(100, m);
    m = Math.max(0, m);
    return m;
  }
  @Override
  public int getHq2() {
    final int _metricPercentageBase = 80;
    int m =
      getSlotsPercentage() * ( 100 - _metricPercentageBase) / 100 + _metricPercentageBase;
    m = Math.min(100, m);
    m = Math.max(0, m);
    return m;
  }
  @Override
  public int getHashQualityInteger() {
    if (size == 0 || collisionInfo.collisionSlotCnt == 0) {
      return 100;
    }
    int _metric0 = getHq0();
    int _metric1 = getHq1();
    int _metric2 = getHq2();
    if (_metric1 < _metric0) {
      int v = _metric0;
      _metric0 = _metric1;
      _metric1 = v;
    }
    if (_metric2 < _metric0) {
      int v = _metric0;
      _metric0 = _metric2;
      _metric2 = v;
    }
    if (_metric2 < _metric1) {
      int v = _metric1;
      _metric1 = _metric2;
      _metric2 = v;
    }
    if (_metric0 <= 0) {
      return 0;
    }
    _metric0 = _metric0 + ((_metric1 - 50) * 5 / _metric0);
    _metric0 = _metric0 + ((_metric2 - 50) * 2 / _metric0);
    _metric0 = Math.max(0, _metric0);
    _metric0 = Math.min(100, _metric0);
    return _metric0;
  }
  @Override
  public double getMillisPerLoad() { return getLoadCnt() == 0 ? 0 : (metrics.getLoadMillis() * 1D / getLoadCnt()); }
  @Override
  public long getLoadMillis() { return metrics.getLoadMillis(); }
  @Override
  public int getCollisionCnt() { return collisionInfo.collisionCnt; }
  @Override
  public int getCollisionSlotCnt() { return collisionInfo.collisionSlotCnt; }
  @Override
  public int getLongestCollisionSize() { return collisionInfo.longestCollisionSize; }
  @Override
  public String getIntegrityDescriptor() { return integrityState.getStateDescriptor(); }
  @Override
  public long getStarted() { return heapCache.startedTime; }
  @Override
  public long getCleared() { return heapCache.clearedTime; }
  @Override
  public long getInfoCreated() { return creationTime; }
  @Override
  public int getInfoCreationDeltaMs() { return creationDeltaMs; }
  @Override
  public int getHealth() {
    if (storage != null && storage.getAlert() == 2) {
      return 2;
    }
    if (integrityState.getStateFlags() > 0 ||
        getHashQualityInteger() < 5) {
      return 2;
    }
    if (storage != null && storage.getAlert() == 1) {
      return 1;
    }
    if (getHashQualityInteger() < 30 ||
      getKeyMutationCnt() > 0 ||
      getInternalExceptionCnt() > 0) {
      return 1;
    }
    return 0;
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

  static String timestampToString(long t) {
    if (t == 0) {
      return "-";
    }
    return formatMillis(t);
  }

  @Override
  public long getClearCnt() {
    return heapCache.clearCnt;
  }

  @Override
  public long getClearedCnt() {
    return heapCache.clearedCnt;
  }

  public String toString() {
    return "Cache{" + heapCache.name + "}("
            + "size=" + getSize() + ", "
            + "maxSize=" + getMaxSize() + ", "
            + "usageCnt=" + getUsageCnt() + ", "
            + "missCnt=" + getMissCnt() + ", "
            + "peekMissCnt=" + (heapCache.peekMissCnt) + ", "
            + "peekHitNotFresh=" + (heapCache.peekHitNotFreshCnt) + ", "
            + "loadCnt=" + getLoadCnt() + ", "
            + "loadButHitCnt=" + getLoadButHitCnt() + ", "
            + "heapHitCnt=" + hitCnt + ", "
            + "newEntryCnt=" + getNewEntryCnt() + ", "
            + "refreshCnt=" + getRefreshCnt() + ", "
            + "refreshSubmitFailedCnt=" + getRefreshSubmitFailedCnt() + ", "
            + "refreshHitCnt=" + getRefreshHitCnt() + ", "
            + "putCnt=" + getPutCnt() + ", "
            + "putNewEntryCnt=" + getPutNewEntryCnt() + ", "
            + "expiredCnt=" + getExpiredCnt() + ", "
            + "evictedCnt=" + getEvictedCnt() + ", "
            + "removedCnt=" + getRemovedCnt() + ", "
            + "clearedCnt=" + getClearedCnt() + ", "
            + "timerEventCnt=" + getTimerEventCnt() + ", "
            + "hitRate=" + getDataHitString() + ", "
            + "collisionCnt=" + getCollisionCnt() + ", "
            + "collisionSlotCnt=" + getCollisionSlotCnt() + ", "
            + "longestCollisionSize=" + getLongestCollisionSize() + ", "
            + "hashQuality=" + getHashQualityInteger() + ", "
            + "msecs/load=" + (getMillisPerLoad() >= 0 ? getMillisPerLoad() : "-")  + ", "
            + "asyncLoadsStarted=" + asyncLoadsStarted + ", "
            + "asyncLoadsInFlight=" + asyncLoadsInFlight + ", "
            + "loaderThreadsLimit=" + loaderThreadsLimit + ", "
            + "loaderThreadsMaxActive=" + loaderThreadsMaxActive + ", "
            + "created=" + timestampToString(getStarted()) + ", "
            + "cleared=" + timestampToString(getCleared()) + ", "
            + "clearCnt=" + getClearCnt() + ", "
            + "loadExceptionCnt=" + getLoadExceptionCnt() + ", "
            + "suppressedExceptionCnt=" + getSuppressedExceptionCnt() + ", "
            + "internalExceptionCnt=" + getInternalExceptionCnt() + ", "
            + "keyMutationCnt=" + getKeyMutationCnt() + ", "
            + "infoCreated=" + timestampToString(getInfoCreated()) + ", "
            + "infoCreationDeltaMs=" + getInfoCreationDeltaMs() + ", "
            + "impl=" + getImplementation() + ", "
            + getExtraStatistics() + ", "
            + "integrityState=" + getIntegrityDescriptor()
      + ")";
  }

}
