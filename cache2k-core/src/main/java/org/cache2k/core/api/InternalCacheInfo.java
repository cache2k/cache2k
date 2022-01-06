package org.cache2k.core.api;

/*-
 * #%L
 * cache2k core implementation
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

import org.cache2k.core.eviction.EvictionMetrics;
import org.cache2k.operation.CacheControl;
import org.cache2k.operation.Weigher;

/**
 * Collection of all metrics of a cache. The data can be retrieved via
 * {@link InternalCache#getInfo()} or {@link InternalCache#getConsistentInfo()}.
 *
 * <p>The interface is not exposed via the exposed API since it may change
 * between versions. Exposed metrics are defined in {@link org.cache2k.operation.CacheStatistics}
 * which can be retrieved via {@link CacheControl#sampleStatistics()}
 *
 * @author Jens Wilke
 * @see EvictionMetrics
 * @see CommonMetrics
 */
public interface InternalCacheInfo {

  /**
   * Configured name of the cache or null if anonymous.
   */
  String getName();

  String getImplementation();

  /**
   * Current number of entries in the cache. This may include entries with expired
   * values.
   */
  long getSize();

  /**
   * Configured limit of the total cache entry capacity or -1 if weigher is used.
   */
  long getHeapCapacity();

  /**
   * Configured maximum weight or -1 if entry capacity is used.
   */
  long getMaximumWeight();

  /**
   * Current sum of entry weights as returned by the {@link Weigher}
   */
  long getTotalWeight();

  /**
   * Total counted hits on the heap cache data. The counter is increased when an entry is present
   * in the cache, regardless whether the value is valid or not.
   */
  long getHeapHitCount();

  /**
   * Number of cache operations, only access
   */
  long getGetCount();

  /**
   * A value was requested, either the entry is not present or the data was expired.
   */
  long getMissCount();

  /**
   * Number of created cache entries. Counter is increased for a load operation, put, etc. when the
   * entry is not yet in the cache. A load operation always creates a new cache entry, even if the
   * the expiry is immediately to block multiple loads. This counter is provided by the eviction
   * implementation.
   *
   * @see EvictionMetrics#getNewEntryCount()
   */
  long getNewEntryCount();

  /**
   * Successful loads including reloads and refresh.
   */
  long getLoadCount();

  /**
   * @see CommonMetrics#getExplicitLoadCount()
   */
  long getExplicitLoadCount();

  /**
   * Entry was loaded again, triggered by timer. The count includes all triggered and tried
   * refresh actions, including those that produced and exception as result. A refresh
   * is also counted as load in {@link #getLoadCount()}
   *
   * @see CommonMetrics#getRefreshCount()
   */
  long getRefreshCount();

  /**
   * The cache produced an exception by itself that should have been prevented.
   */
  long getInternalExceptionCount();

  /**
   * Entry was supposed to be refreshed, but there was no thread available for executing it.
   *
   * @see CommonMetrics#getRefreshRejectedCount()
   */
  long getRefreshRejectedCount();

  /**
   * Loader exception occurred, but the resilience policy decided to suppress the exception and
   * continue to use the available value.
   *
   * @see CommonMetrics#getSuppressedExceptionCount()
   */
  long getSuppressedExceptionCount();

  /**
   * Counter of exceptions thrown from the loader.
   *
   * @see CommonMetrics#getLoadExceptionCount()
   */
  long getLoadExceptionCount();

  /**
   * A previously refreshed entry was accessed. The access is only counted once after a refresh
   * operation, so the ration of refresh and refreshed hit is the efficiency of the refresh
   * operation.
   *
   * @see CommonMetrics#getRefreshedHitCount()
   */
  long getRefreshedHitCount();

  /**
   * Counts entries that expired. This counter includes removed entries from the cache and
   * entries that are kept in the cache but expired.
   *
   * @see EvictionMetrics#getExpiredRemovedCount()
   * @see CommonMetrics#getExpiredKeptCount()
   */
  long getExpiredCount();

  /**
   * Entry was evicted.
   *
   * @see EvictionMetrics#getEvictedCount()
   */
  long getEvictedCount();

  /**
   * Number of entries currently being evicted.
   *
   * @see EvictionMetrics#getEvictionRunningCount()
   */
  int getEvictionRunningCount();

  /**
   * Removed entries, because of programmatic removal. Removal of entries by clear is
   * counted separately. Provided by the eviction implementation.
   *
   * @see #getClearedEntriesCount()
   * @see EvictionMetrics#getRemovedCount()
   */
  long getRemoveCount();

  /**
   * Entry was inserted in the cache via put or another operation not including a load.
   *
   * @see CommonMetrics#getPutNewEntryCount()
   * @see CommonMetrics#getPutHitCount()
   */
  long getPutCount();

  /**
   * Number of entries removed from the cache by the {@code clear} operation.
   *
   * @see org.cache2k.Cache#clear()
   */
  long getClearedEntriesCount();

  /**
   * Number of calls to {@code clear} this cache has received.
   *
   * @see org.cache2k.Cache#clear()
   */
  long getClearCount();

  /**
   * After inserting into the cache the key object changed its hash code.
   */
  long getKeyMutationCount();

  /**
   * Count of timer events delivered to this cache.
   *
   * @see CommonMetrics#getTimerEventCount()
   */
  long getTimerEventCount();

  /**
   * Hit rate of the cache
   */
  double getHitRate();

  /**
   * Hit rate of the cache in string representation
   */
  String getHitRateString();

  /**
   * Average duration in milliseconds for each load operation.
   */
  double getMillisPerLoad();

  /**
   * Accumulated loader execution time.
   *
   * @see CommonMetrics#getLoadMillis()
   */
  long getLoadMillis();

  String getIntegrityDescriptor();

  /**
   * Entry was removed while waiting to get the mutation lock.
   *
   * @see CommonMetrics#getGoneSpinCount()
   */
  long getGoneSpinCount();

  /**
   * Time when the cache started the operation.
   */
  long getStartedTime();

  /**
   * Time of last clear operation.
   */
  long getClearedTime();

  /**
   * Time when the info object was created. The information needs time to collect. Whenever
   * statistics are requested, a new values may be collected or old values are used. The recency
   * of the information can be determined by this value.
   */
  long getInfoCreatedTime();

  long getEvictedWeight();

  long getScanCount();

}
