package org.cache2k.jcache.provider;

/*
 * #%L
 * cache2k JCache JSR107 implementation
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

import org.cache2k.impl.InternalCache;
import org.cache2k.impl.InternalCacheInfo;

import javax.cache.management.CacheStatisticsMXBean;

/**
 * @author Jens Wilke; created: 2015-04-29
 */
public class CacheJmxStatistics implements CacheStatisticsMXBean {

  private static final boolean flushOnAccess = Tuning.GLOBAL.flushStatisticsOnAccess;
  private static final int tweakStatisticsForEntryProcessor =
    Tuning.GLOBAL.tweakStatisticsForEntityProcessor && false ? 1 : 0;

  InternalCache cache;
  JCacheAdapter adapter;

  InternalCacheInfo getInfo() {
    return flushOnAccess ? cache.getLatestInfo() : cache.getInfo();
  }

  public CacheJmxStatistics(JCacheAdapter _cache) {
    cache = _cache.cacheImpl;
    adapter = _cache;
  }

  @Override
  public void clear() {

  }

  @Override
  public long getCacheHits() {
    InternalCacheInfo inf = getInfo();
    return calcHits(inf);
  }

  private long calcHits(InternalCacheInfo inf) {
    long _readUsage = inf.getReadUsageCnt();
    long _missCount = inf.getMissCnt();
    return _readUsage - _missCount +
      adapter.iterationHitCorrectionCounter.get() +
      adapter.hitCorrectionCounter.get() * tweakStatisticsForEntryProcessor;
  }

  @Override
  public float getCacheHitPercentage() {
    InternalCacheInfo inf = getInfo();
    long _hits = calcHits(inf);
    long _miss = calcMisses(inf);
    if (_hits == 0) {
      return 0.0F;
    }
    return (float) _hits * 100F / (_hits + _miss);
  }

  @Override
  public long getCacheMisses() {
    InternalCacheInfo inf = getInfo();
    return calcMisses(inf);
  }

  private long calcMisses(InternalCacheInfo inf) {
    return inf.getMissCnt() + adapter.missCorrectionCounter.get() * tweakStatisticsForEntryProcessor;
  }

  @Override
  public float getCacheMissPercentage() {
    InternalCacheInfo inf = getInfo();
    return inf.getReadUsageCnt() == 0 ? 0.0F : (100.0F * inf.getMissCnt() / inf.getReadUsageCnt());
  }

  @Override
  public long getCacheGets() {
    return getInfo().getReadUsageCnt() + adapter.iterationHitCorrectionCounter.get();
  }

  @Override
  public long getCachePuts() {
    return getInfo().getPutCnt();
  }

  @Override
  public long getCacheRemovals() {
    return getInfo().getRemovedCnt();
  }

  @Override
  public long getCacheEvictions() {
    return getInfo().getEvictedCnt();
  }

  @Override
  public float getAverageGetTime() {
    return (float) getInfo().getMillisPerFetch();
  }

  @Override
  public float getAveragePutTime() {
    return 0;
  }

  @Override
  public float getAverageRemoveTime() {
    return 0;
  }
}
