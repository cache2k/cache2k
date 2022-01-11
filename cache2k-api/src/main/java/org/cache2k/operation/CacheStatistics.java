package org.cache2k.operation;

/*-
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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

import org.cache2k.config.Cache2kConfig;
import org.cache2k.io.CacheLoader;

/**
 * Set of metrics for cache statistics. The cache statistics can be retrieved
 * via {@link CacheControl#sampleStatistics()}. The statistics may be unavailable
 * if disabled via {@link org.cache2k.Cache2kBuilder#disableStatistics(boolean)}.
 * General information about the cache is available via {@link CacheInfo}.
 *
 * <p>For exporting to a monitoring system the modules {@code cache2k-jmx}
 * and {@code cache2k-micrometer} are available.
 *
 * <p>Statistics are retrieved with minimum effect on the running application
 * and avoid locks. This may lead to slight inconsistencies within the metrics,
 * e.g. the two values {@link #getTotalLoadMillis()} and {@link #getLoadCount()}
 * are not updated atomically.
 *
 * @author Jens Wilke
 */
public interface CacheStatistics {

  /**
   * How many times a new entry was inserted into the cache.
   */
  long getInsertCount();

  /**
   * How often data was requested from the cache.
   */
  long getGetCount();

  /**
   * Counter of the event that: a client requested a data which was not
   * present in the cache or had expired. In other word {@code containsKey}
   * does not count a miss.
   */
  long getMissCount();

  /**
   * How many times a load succeeded.
   */
  long getLoadCount();

  /**
   * Number of exceptions thrown by the {@link CacheLoader}.
   */
  long getLoadExceptionCount();

  /**
   * Number of exceptions thrown by the {@code CacheLoader} that were ignored and
   * the previous data value got returned.
   */
  long getSuppressedLoadExceptionCount();

  /**
   * Average number of milliseconds per load.
   */
  double getMillisPerLoad();

  /**
   * Total number of time spent loading entries from the cache loader.
   */
  long getTotalLoadMillis();

  /**
   * Refreshed entries counter.
   */
  long getRefreshCount();

  /**
   * Counter how many times a refresh failed, e.g. because there were
   * not enough thread resources available.
   */
  long getRefreshFailedCount();

  /**
   * How many times there was a hit on a refreshed entry. This counter is incremented
   * once, after a refreshed entry is requested by the application for the first time.
   * That means the quotient from {@code refreshedCount} and {@code refreshedHitCount}
   * is the efficiency of refreshing.
   */
  long getRefreshedHitCount();

  /**
   * A cache entry has expired. The counter is incremented after the actual expiry.
   *
   * <p>This can mean that the cache entry is removed or just marked as expired
   * in case that the keep value option is enabled.
   *
   * @see Cache2kConfig#setKeepDataAfterExpired(boolean)
   */
  long getExpiredCount();

  /**
   * An entry was evicted from the cache because of size limits.
   */
  long getEvictedCount();

  /**
   * Accumulated number of weight evicted or removed entries
   */
  long getEvictedOrRemovedWeight();

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
  long getClearedCount();

  /**
   * Number of {@link org.cache2k.Cache#clear} invocations.
   */
  long getClearCallsCount();

  /**
   * Number of key mutations occurred. This should be always 0, otherwise it is an indicator
   * that the key object was modified after it was stored in the cache. Whenever
   * {@code `keyMutationCount`} is non-zero, check and correct your application.
   *
   * <p>How it works: When an entry is evicted the cache checks whether the {@code hashCode} of
   * the key object is identical to the code when it was inserted. If not, the counter is
   * incremented. When a mutation is detected, also a warning is logged but only once
   * during the cache lifetime.
   */
  long getKeyMutationCount();

  /**
   * The percentage of cache accesses the cache delivered data.
   */
  double getHitRate();

}
