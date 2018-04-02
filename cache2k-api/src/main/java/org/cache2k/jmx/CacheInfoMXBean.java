package org.cache2k.jmx;

/*
 * #%L
 * cache2k API
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.configuration.Cache2kConfiguration;

import java.util.Date;

/**
 * Exposed statistics via JMX from a cache.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("unused")
public interface CacheInfoMXBean {

  /**
   * Type of the cache key.
   */
  String getKeyType();

  /**
   * Type of the cache value.
   */
  String getValueType();

  /**
   * The current number of entries within the cache, starting with 0.
   * When iterating the entries the cache will always return less or an identical number of entries.
   *
   * <p>Expired entries may stay in the cache {@link Cache2kBuilder#keepDataAfterExpired(boolean)}.
   * These entries will be counted, but will not be returned by the iterator or a {@code peek} operation
   */
  long getSize();

  /**
   * The configured maximum number of entries in the cache.
   */
  long getEntryCapacity();

  /**
   * How many times a new entry was inserted into the cache.
   */
  long getInsertCount();

  /**
   * How often data was requested from the cache. In multi threading scenarios this
   * counter may be not totally accurate. For performance reason the cache implementation
   * may choose to only present a best effort value. It is guaranteed that the
   * usage count is always greater than the miss count.
   */
  long getGetCount();

  /**
   * Counter of the event that: a client requested a data which was not
   * present in the cache or had expired.
   */
  long getMissCount();

  /**
   * How many times a load succeeded.
   */
  long getLoadCount();

  /**
   * Counter for the event that the data of a cache entry was refreshed.
   */
  long getRefreshCount();

  /**
   * Counter how many times a refresh failed, because there were
   * not enough thread resources available.
   */
  long getRefreshFailedCount();

  /**
   * How many times we had a hit on a refreshed entry.
   */
  long getRefreshedHitCount();

  /**
   * Counter for the event that data in the cache has expired.
   *
   * <p>This can mean that the cache entry is removed or just marked as expired
   * in case that the keep value option is enabled.
   *
   * @see Cache2kConfiguration#setKeepDataAfterExpired(boolean)
   */
  long getExpiredCount();

  /**
   * An entry was evicted from the cache because of size limits.
   */
  long getEvictedCount();

  /**
   * The total number of insert or update operations.
   */
  long getPutCount();

  /**
   * Number of remove operations.
   */
  long getRemoveCount();

  /**
   * Number entries removed from the cache by the {@link org.cache2k.Cache#clear} operation.
   */
  long getClearedEntriesCount();

  /**
   * Number of {@link org.cache2k.Cache#clear} invocations.
   */
  long getClearCount();

  /**
   * Number of key mutations occurred. This should be always 0, otherwise it is an indicator
   * that the hash keys objects are modified by the application after usage within a cache
   * request.
   */
  long getKeyMutationCount();

  /**
   * Number of exceptions thrown by the {@link org.cache2k.integration.CacheLoader}.
   */
  long getLoadExceptionCount();

  /**
   * Number of exceptions thrown by the {@code CacheLoader} that were ignored and
   * the previous data value got returned.
   */
  long getSuppressedLoadExceptionCount();

  /**
   * The percentage of cache accesses the cache delivered data.
   */
  double getHitRate();

  /**
   * A value between 0 and 100 to help evaluate the quality of the hashing function. 100 means perfect, there
   * are no collisions. A value of 80 means that 80% of the entries are reachable without collision.
   * The size of the longest collision list is also combined into this value, for example if the longest collision
   * size is 20, then this value is 85 and below. This way this metrics can be used to detect bad hash function
   * and hash collision attacks.
   */
  int getHashQuality();

  /**
   * Average number of milliseconds per load.
   */
  double getMillisPerLoad();

  /**
   * Total number of time spent loading entries from the cache loader.
   */
  long getTotalLoadMillis();

  /**
   * Implementation class of the cache.
   */
  String getImplementation();

  /**
   * Time when the cache was created.
   */
  Date getCreatedTime();

  /**
   * Time of the most recent {@link org.cache2k.Cache#clear} operation.
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
   * Additional statistics from the eviction algorithm. This is an arbitrary string
   * for debugging and tuning the eviction algorithm.
   */
  String getEvictionStatistics();

  /**
   * The cache checks some internal values for correctness. If this does not start with
   * "0.", then please raise a bug.
   */
  String getIntegrityDescriptor();

}
