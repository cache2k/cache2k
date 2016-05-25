package org.cache2k.jcache.provider;

/*
 * #%L
 * cache2k JSR107 support
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

import org.cache2k.core.InternalCache;
import org.cache2k.core.InternalCacheInfo;

import javax.cache.management.CacheStatisticsMXBean;

/**
 * @author Jens Wilke; created: 2015-04-29
 */
public class CacheJmxStatistics implements CacheStatisticsMXBean {

  InternalCache cache;
  JCacheAdapter adapter;

  InternalCacheInfo getInfo() {
    return adapter.flushJmxStatistics ? cache.getLatestInfo() : cache.getInfo();
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
      adapter.iterationHitCorrectionCounter.get();
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
    return inf.getMissCnt();
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
    return (float) getInfo().getMillisPerLoad();
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
