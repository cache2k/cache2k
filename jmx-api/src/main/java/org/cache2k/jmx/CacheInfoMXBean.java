package org.cache2k.jmx;

/*
 * #%L
 * cache2k jmx api definitions
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

import java.util.Date;

/**
 * Exposed statistics via JMX from a cache.
 *
 * @author Jens Wilke; created: 2013-07-16
 */
@SuppressWarnings("unused")
public interface CacheInfoMXBean {

  /**
   * The current number of entries within the cache, starting with 0.
   */
  int getSize();

  /**
   * The configured maximum number of entries in the cache.
   */
  int getMaximumSize();

  /**
   * How often data was requested from the cache. In multi threading scenarios this
   * counter may be not totally accurate. For performance reason the cache implementation
   * may choose to only present a best effort value. It is guaranteed that the
   * usage count is always greater than the miss count.
   */
  long getUsageCnt();

  /**
   * Counter of the event that: a client requested a data which was not
   * present in the cache or had expired.
   */
  long getMissCnt();

  /**
   * How many new cache entries are created. This counts a cache miss on get()
   * or a put().
   */
  long getNewEntryCnt();

  /**
   * How many times the data was fetched from the cache source.
   */
  long getFetchCnt();

  /**
   * Counter for the event that the data of a cache entry was refreshed.
   */
  long getRefreshCnt();

  /**
   * Counter how many times a refresh submit failed, meaning that there were
   * not enough thread resources available.
   */
  long getRefreshSubmitFailedCnt();

  /**
   * How many times we had a hit on a refreshed entry.
   */
  long getRefreshHitCnt();

  /**
   * Counter for the event that data in the cache has expired.
   *
   * <p>This can mean that the cache entry is removed or just marked as expired
   * in case that the keep data option is enabled.
   *
   * @see org.cache2k.CacheConfig#setKeepDataAfterExpired(boolean)
   */
  long getExpiredCnt();

  /**
   * An entry was evicted from the cache because of size limits.
   */
  long getEvictedCnt();

  /**
   * Number of calls to put().
   */
  long getPutCnt();

  /**
   * Number of key mutations occurred. This should be always 0, otherwise it is an indicator
   * that the hash keys objects are modified by the application after usage within a cache
   * request.
   */
  long getKeyMutationCnt();

  /**
   * Number of exceptions thrown by the {@link org.cache2k.CacheSource}.
   */
  long getFetchExceptionCnt();

  /**
   * Number of exceptions thrown by the CacheSource that were ignored and
   * the previous data value got returned.
   */
  long getSuppressedExceptionCnt();

  /**
   * The percentage of cache accesses the cache delivered data directly instead of fetching it.
   */
  double getHitRate();

  /**
   * Value between 100 and 0 to help evaluate the quality of the hashing function. 100 means perfect.
   * This metric takes into account the collision to size ratio, the longest collision size
   * and the collision to slot ratio. The value reads 0 if the longest collision size gets more
   * then 20.
   */
  int getHashQuality();

  /**
   * Number of hashcode collisions within the cache. E.g. the hashCode: 2, 3, 3, 4, 4, 4 will
   * mean three collisions.
   */
  int getHashCollisionCnt();

  /**
   * Number of collision slots within the cache. E.g. the hashCode: 2, 3, 3, 4, 4, 4 will mean two
   * collision slots.
   */
  int getHashCollisionsSlotCnt();

  /**
   * The number of entries of the collision slot with the most collisions. Either 0, 2 or more.
   */
  int getHashLongestCollisionSize();

  /**
   * Milliseconds per fetch.
   */
  double getMillisPerFetch();

  /**
   * Total number of time spent fetching entries from the cache source.
   */
  long getFetchMillis();

  /**
   * Amount of memory the cache
   */
  int getMemoryUsage();

  /**
   * Implementation class of the cache which controls the eviction strategy.
   */
  String getImplementation();

  /**
   * The cache checks some internal values for correctness. If this does not start with
   * "0.", then please raise a bug.
   */
  String getIntegrityDescriptor();

  /**
   * Time of last meaningful cache operation. This is when the cache changed its
   * structure or data was modified. Basically this means everything except a
   * straight cache hit, that puts no effort on the cache.
   */
  Date getLastOperationTime();

  /**
   * Time when cache object was created.
   */
  Date getCreatedTime();

  /**
   * Time when cache object was cleared.
   */
  Date getClearedTime();

  /**
   * Time when the cache information was created for JMX. Some of the values may
   * take processing time. The cache does not always return the latest values
   * if the object is requested very often.
   */
  Date getInfoCreatedTime();

  /**
   * Milliseconds needed to provide the data.
   */
  int getInfoCreatedDetlaMillis();

  /**
   * Single health value from 0 meaning good, 1 meaning warning, and 2 meaning failure.
   * Some operations may cause a warning alert level and then, after a few seconds,
   * when everything is back to normal, reset it. A monitoring trigger, should
   * have a delay (e.g. 30 seconds) before escalating to the operations team.
   */
  int getAlert();

  /**
   * String with additional statistics from the cache implementation.
   */
  String getExtraStatistics();

}
