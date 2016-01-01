package org.cache2k.jcache;

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

import org.cache2k.impl.BaseCache;

import javax.cache.management.CacheStatisticsMXBean;

/**
 * @author Jens Wilke; created: 2015-04-29
 */
public class CacheJmxStatistics implements CacheStatisticsMXBean {

  private static final boolean flushOnAccess = Tuning.GLOBAL.flushStatisticsOnAccess;
  private static final int tweakStatisticsForEntityProcessor = Tuning.GLOBAL.tweakStatisticsForEntityProcessor ? 1 : 0;

  BaseCache cache;
  Cache2kCacheAdapter adapter;

  BaseCache.Info getInfo() {
    return flushOnAccess ? cache.getLatestInfo() : cache.getInfo();
  }

  public CacheJmxStatistics(Cache2kCacheAdapter _cache) {
    cache = _cache.cacheImpl;
    adapter = _cache;
  }

  @Override
  public void clear() {

  }

  @Override
  public long getCacheHits() {
    BaseCache.Info inf = getInfo();
    return calcHits(inf);
  }

  private long calcHits(BaseCache.Info inf) {
    return inf.getReadUsageCnt() - inf.getMissCnt() + adapter.hitCorrectionCounter.get() * tweakStatisticsForEntityProcessor;
  }

  @Override
  public float getCacheHitPercentage() {
    if (tweakStatisticsForEntityProcessor == 1) {
      BaseCache.Info inf = getInfo();
      long _hits = calcHits(inf);
      long _miss = calcMisses(inf);
      if (_hits == 0) {
        return 0.0F;
      }
      return (float) _hits * 100F / (_hits + _miss);
    }
    return (float) getInfo().getDataHitRate();
  }

  @Override
  public long getCacheMisses() {
    BaseCache.Info inf = getInfo();
    return calcMisses(inf);
  }

  private long calcMisses(BaseCache.Info inf) {
    return inf.getMissCnt() + adapter.missCorrectionCounter.get() * tweakStatisticsForEntityProcessor;
  }

  @Override
  public float getCacheMissPercentage() {
    BaseCache.Info inf = getInfo();
    return inf.getReadUsageCnt() == 0 ? 0.0F : (100.0F * inf.getMissCnt() / inf.getReadUsageCnt());
  }

  @Override
  public long getCacheGets() {
    return getInfo().getReadUsageCnt();
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
