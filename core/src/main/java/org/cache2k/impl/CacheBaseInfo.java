package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import static org.cache2k.impl.util.Util.formatMillis;

/**
 * Stable interface to request information from the cache, the object
 * safes values that need a longer calculation time, other values are
 * requested directly.
 */
class CacheBaseInfo implements InternalCacheInfo {

  StorageAdapter storage;
  CommonMetrics metrics;
  private BaseCache baseCache;
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
  BaseCache.CollisionInfo collisionInfo;
  String extraStatistics;
  int fetchesInFlight;
  IntegrityState integrityState;

  public CacheBaseInfo(BaseCache baseCache) {
    this.baseCache = baseCache;
    metrics = baseCache.metrics;
    integrityState = baseCache.getIntegrityState();
    collisionInfo = new BaseCache.CollisionInfo();
    Hash.calcHashCollisionInfo(collisionInfo, baseCache.mainHash);
    Hash.calcHashCollisionInfo(collisionInfo, baseCache.refreshHash);
    extraStatistics = baseCache.getExtraStatistics();
    if (extraStatistics.startsWith(", ")) {
      extraStatistics = extraStatistics.substring(2);
    }
    fetchesInFlight = baseCache.getFetchesInFlight();
    size = baseCache.getLocalSize();
    missCnt = baseCache.loadCnt + baseCache.peekHitNotFreshCnt + baseCache.peekMissCnt;
    storageMissCnt = baseCache.readMissCnt + baseCache.readNonFreshCnt + baseCache.readNonFreshAndFetchedCnt;
    storageLoadCnt = storageMissCnt + baseCache.readHitCnt;
    newEntryCnt = baseCache.newEntryCnt - baseCache.virginEvictCnt;
    hitCnt = baseCache.getHitCnt();
    correctedPutCnt = metrics.getPutNewEntryCount() + metrics.getPutHitCount() + metrics.getPutNoReadHitCount() - baseCache.putButExpiredCnt;
    usageCnt =
            hitCnt + newEntryCnt + baseCache.peekMissCnt + metrics.getPutHitCount() + metrics.getRemoveCount();
  }

  String percentString(double d) {
    String s = Double.toString(d);
    return (s.length() > 5 ? s.substring(0, 5) : s) + "%";
  }

  @Override
  public String getName() { return baseCache.name; }
  @Override
  public String getImplementation() { return baseCache.getClass().getSimpleName(); }

  @Override
  public long getFetchButHitCnt() {
    return baseCache.loadButHitCnt;
  }

  @Override
  public long getVirginEvictCnt() {
    return baseCache.virginEvictCnt;
  }

  @Override
  public int getSize() { return size; }
  @Override
  public int getMaxSize() { return baseCache.maxSize; }
  @Override
  public long getStorageHitCnt() { return baseCache.readHitCnt; }
  @Override
  public long getStorageLoadCnt() { return storageLoadCnt; }
  @Override
  public long getStorageMissCnt() { return storageMissCnt; }
  @Override
  public long getReadUsageCnt() {
    long _putHit = metrics.getPutNoReadHitCount();
    long _containsBitHit = metrics.getContainsButHitCount();
    long _heapHitButNoRead = metrics.getHeapHitButNoReadCount();
    return
      hitCnt + baseCache.peekMissCnt
      + baseCache.loadCnt - baseCache.loadButHitCnt - _putHit - _containsBitHit - _heapHitButNoRead;
  }
  @Override
  public long getUsageCnt() { return usageCnt; }
  @Override
  public long getMissCnt() { return missCnt; }
  @Override
  public long getNewEntryCnt() { return newEntryCnt; }
  @Override
  public long getFetchCnt() { return baseCache.loadCnt; }
  @Override
  public int getFetchesInFlightCnt() { return fetchesInFlight; }
  @Override
  public long getBulkGetCnt() { return baseCache.bulkGetCnt; }
  @Override
  public long getRefreshCnt() { return baseCache.refreshCnt; }
  @Override
  public long getInternalExceptionCnt() { return baseCache.internalExceptionCnt; }
  @Override
  public long getRefreshSubmitFailedCnt() { return baseCache.refreshSubmitFailedCnt; }
  @Override
  public long getSuppressedExceptionCnt() { return baseCache.suppressedExceptionCnt; }
  @Override
  public long getFetchExceptionCnt() { return baseCache.loadExceptionCnt; }
  @Override
  public long getRefreshHitCnt() { return baseCache.refreshHitCnt; }
  @Override
  public long getExpiredCnt() { return baseCache.getExpiredCnt(); }
  @Override
  public long getEvictedCnt() { return baseCache.evictedCnt - baseCache.virginEvictCnt; }
  @Override
  public long getRemovedCnt() { return metrics.getRemoveCount(); }
  @Override
  public long getPutNewEntryCnt() { return baseCache.putNewEntryCnt; }
  @Override
  public long getPutCnt() { return correctedPutCnt; }
  @Override
  public long getKeyMutationCnt() { return baseCache.keyMutationCount; }
  @Override
  public long getTimerEventCnt() { return baseCache.timerEvents; }
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
  public double getMillisPerFetch() { return baseCache.loadCnt == 0 ? 0 : (baseCache.fetchMillis * 1D / baseCache.loadCnt); }
  @Override
  public long getFetchMillis() { return baseCache.fetchMillis; }
  @Override
  public int getCollisionCnt() { return collisionInfo.collisionCnt; }
  @Override
  public int getCollisionSlotCnt() { return collisionInfo.collisionSlotCnt; }
  @Override
  public int getLongestCollisionSize() { return collisionInfo.longestCollisionSize; }
  @Override
  public String getIntegrityDescriptor() { return integrityState.getStateDescriptor(); }
  @Override
  public long getStarted() { return baseCache.startedTime; }
  @Override
  public long getCleared() { return baseCache.clearedTime; }
  @Override
  public long getTouched() { return baseCache.touchedTime; }
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
  public String getExtraStatistics() {
    return extraStatistics;
  }

  static String timestampToString(long t) {
    if (t == 0) {
      return "-";
    }
    return formatMillis(t);
  }

  public String toString() {
    return "size=" + getSize() + ", "
            + "maxSize=" + getMaxSize() + ", "
            + "usageCnt=" + getUsageCnt() + ", "
            + "missCnt=" + getMissCnt() + ", "
            + "peekMissCnt=" + (baseCache.peekMissCnt) + ", "
            + "peekHitNotFresh=" + (baseCache.peekHitNotFreshCnt) + ", "
            + "fetchCnt=" + getFetchCnt() + ", "
            + "fetchButHitCnt=" + getFetchButHitCnt() + ", "
            + "heapHitCnt=" + hitCnt + ", "
            + "virginEvictCnt=" + getVirginEvictCnt() + ", "
            + "fetchesInFlightCnt=" + getFetchesInFlightCnt() + ", "
            + "newEntryCnt=" + getNewEntryCnt() + ", "
            + "bulkGetCnt=" + getBulkGetCnt() + ", "
            + "refreshCnt=" + getRefreshCnt() + ", "
            + "refreshSubmitFailedCnt=" + getRefreshSubmitFailedCnt() + ", "
            + "refreshHitCnt=" + getRefreshHitCnt() + ", "
            + "putCnt=" + getPutCnt() + ", "
            + "putNewEntryCnt=" + getPutNewEntryCnt() + ", "
            + "expiredCnt=" + getExpiredCnt() + ", "
            + "evictedCnt=" + getEvictedCnt() + ", "
            + "removedCnt=" + getRemovedCnt() + ", "
            + "timerEventCnt=" + getTimerEventCnt() + ", "
            + "storageLoadCnt=" + getStorageLoadCnt() + ", "
            + "storageMissCnt=" + getStorageMissCnt() + ", "
            + "storageHitCnt=" + getStorageHitCnt() + ", "
            + "hitRate=" + getDataHitString() + ", "
            + "collisionCnt=" + getCollisionCnt() + ", "
            + "collisionSlotCnt=" + getCollisionSlotCnt() + ", "
            + "longestCollisionSize=" + getLongestCollisionSize() + ", "
            + "hashQuality=" + getHashQualityInteger() + ", "
            + "msecs/fetch=" + (getMillisPerFetch() >= 0 ? getMillisPerFetch() : "-")  + ", "
            + "created=" + timestampToString(getStarted()) + ", "
            + "cleared=" + timestampToString(getCleared()) + ", "
            + "touched=" + timestampToString(getTouched()) + ", "
            + "fetchExceptionCnt=" + getFetchExceptionCnt() + ", "
            + "suppressedExceptionCnt=" + getSuppressedExceptionCnt() + ", "
            + "internalExceptionCnt=" + getInternalExceptionCnt() + ", "
            + "keyMutationCnt=" + getKeyMutationCnt() + ", "
            + "infoCreated=" + timestampToString(getInfoCreated()) + ", "
            + "infoCreationDeltaMs=" + getInfoCreationDeltaMs() + ", "
            + getExtraStatistics() + ", "
            + "integrityState=" + getIntegrityDescriptor();
  }

}
