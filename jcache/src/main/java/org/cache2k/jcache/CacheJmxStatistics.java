package org.cache2k.jcache;

/*
 * #%L
 * cache2k JCache JSR107 implementation
 * %%
 * Copyright (C) 2000 - 2015 headissue GmbH, Munich
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

import org.cache2k.jmx.CacheInfoMXBean;

import javax.cache.management.CacheStatisticsMXBean;

/**
 * @author Jens Wilke; created: 2015-04-29
 */
public class CacheJmxStatistics implements CacheStatisticsMXBean {

  CacheInfoMXBean stats;

  public CacheJmxStatistics(CacheInfoMXBean stats) {
    this.stats = stats;
  }

  @Override
  public void clear() {

  }

  @Override
  public long getCacheHits() {
    return stats.getUsageCnt() - stats.getMissCnt();
  }

  @Override
  public float getCacheHitPercentage() {
    return (float) stats.getHitRate();
  }

  @Override
  public long getCacheMisses() {
    return stats.getMissCnt();
  }

  @Override
  public float getCacheMissPercentage() {
    return 0;
  }

  @Override
  public long getCacheGets() {
    return stats.getUsageCnt();
  }

  @Override
  public long getCachePuts() {
    return stats.getPutCnt();
  }

  @Override
  public long getCacheRemovals() {
    return 0;
  }

  @Override
  public long getCacheEvictions() {
    return stats.getEvictedCnt();
  }

  @Override
  public float getAverageGetTime() {
    return (float) stats.getMillisPerFetch();
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
