package org.cache2k.operation;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;

import java.util.Date;

/**
 * Information of a cache for introspection at runtime, for
 * generic monitoring and management proposes. Additional information
 * is available via {@link CacheStatistics} if statistics are enabled.
 *
 * <p>This interface is exposed as JMX bean if JMX support is enabled or can be
 * requested via {@link #of}. Alternatively use the combined interface
 * {@link CacheControl}.
 *
 * <p>It is intentionally that there is no way to retrieve the original
 * configuration after creation.
 *
 * @author Jens Wilke
 */
public interface CacheInfo {

  /**
   * Request an instance for the given cache.
   */
  static CacheInfo of(Cache<?, ?> cache) { return cache.requestInterface(CacheInfo.class); }

  String getName();

  String getManagerName();

  /**
   * Type of the cache key.
   */
  String getKeyType();

  /**
   * Type of the cache value.
   */
  String getValueType();

  /**
   * The current number of entries within the cache, starting with 0. This value is an
   * estimate, when iterating the entries the cache will always return less or an
   * identical number of entries.
   *
   * <p>Expired entries may stay in the cache {@link Cache2kBuilder#keepDataAfterExpired(boolean)}.
   * These entries will be counted, but will not be returned by the iterator or a {@code peek}
   * operation
   */
  long getSize();

  /**
   * The configured maximum number of entries in the cache.
   */
  long getEntryCapacity();

  /**
   * Configured maximum weight or -1.
   *
   * @see org.cache2k.Weigher
   * @see Cache2kBuilder#maximumWeight(long)
   */
  long getMaximumWeight();

  /**
   * Total weight of all entries in the cache.
   *
   * @see org.cache2k.Weigher
   * @see Cache2kBuilder#maximumWeight(long)
   */
  long getTotalWeight();

  /**
   * Either {@link #getMaximumWeight()} or {@link #getEntryCapacity()} depending
   * on whether a weigher is present or not.
   *
   * @see org.cache2k.Weigher
   */
  long getCapacityLimit();

  /**
   * Descriptor of cache implementation in use. Either a class name or multiple class names.
   */
  String getImplementation();

  /**
   * A loader is configured. This can be used to determine, whether it makes sense to
   * send the report/monitor the loader metrics.
   */
  boolean isLoaderPresent();

  /**
   * A weigher is configured. This can be used to determine, whether it makes sense to
   * send the report/monitor the weigher metrics.
   */
  boolean isWeigherPresent();

  /**
   * This cache supports statistics.
   */
  boolean isStatisticsEnabled();

  /**
   * Time when the cache was created.
   *
   * <p>We use the old type {@code Date} and not {@code Instant} here in order to make this interface
   * usable for JMX directly, which does not support {@code Instant}.
   */
  Date getCreatedTime();

  /**
   * Time of the most recent {@link org.cache2k.Cache#clear} operation.
   */
  Date getClearedTime();

}
