package org.cache2k.jmx;

/*
 * #%L
 * cache2k JMX API
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.configuration.Cache2kConfiguration;

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
   * When iterating the entries the cache will always return less or an identical number of entries.
   *
   * <p>Expired entries may stay in the cache {@link Cache2kBuilder#keepDataAfterExpired(boolean)}.
   * These entries will be counted, but will not be returned by the iterator.
   */
  long getSize();

  /**
   * The configured maximum number of entries in the cache.
   */
  long getEntryCapacity();

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
   * How many times a load succeeded.
   */
  long getLoadCnt();

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
   * in case that the keep value option is enabled.
   *
   * @see Cache2kConfiguration#setKeepDataAfterExpired(boolean)
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
   * Number of exceptions thrown by the {@link org.cache2k.integration.CacheLoader}.
   */
  long getLoadExceptionCnt();

  /**
   * Number of exceptions thrown by the {@code CacheLoader} that were ignored and
   * the previous data value got returned.
   */
  long getSuppressedExceptionCnt();

  /**
   * The percentage of cache accesses the cache delivered data.
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
  long getHashCollisionCnt();

  /**
   * Number of collision slots within the cache. E.g. the hashCode: 2, 3, 3, 4, 4, 4 will mean two
   * collision slots.
   */
  long getHashCollisionsSlotCnt();

  /**
   * The number of entries of the collision slot with the most collisions. Either 0, 2 or more.
   */
  long getHashLongestCollisionSize();

  /**
   * Milliseconds per load.
   */
  double getMillisPerLoad();

  /**
   * Total number of time spent loading entries from the cache loader.
   */
  long getTotalLoadMillis();

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
  int getInfoCreatedDeltaMillis();

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
