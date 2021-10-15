package org.cache2k.core.api;

/*
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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
import org.cache2k.CacheEntry;
import org.cache2k.config.CacheType;
import org.cache2k.core.ConcurrentMapWrapper;
import org.cache2k.core.eviction.Eviction;
import org.cache2k.core.operation.ExaminationEntry;
import org.cache2k.core.timing.TimerEventListener;
import org.cache2k.core.log.Log;
import org.cache2k.operation.TimeReference;

/**
 * Interface to extended cache functions for the internal components.
 *
 * @author Jens Wilke
 */
public interface InternalCache<K, V>
  extends Cache<K, V>, TimerEventListener<K, V>, InternalCacheCloseContext {

  CommonMetrics getCommonMetrics();

  /** used from the cache manager */
  Log getLog();

  CacheType getKeyType();

  CacheType getValueType();

  /** used from the cache manager for shutdown */
  void cancelTimerJobs();

  /**
   * Return cache statistic counters. This method is intended for regular statistics polling.
   * No extensive locking is performed to extract a consistent set of counters.
   */
  InternalCacheInfo getInfo();

  /**
   * Generate fresh statistics within a global cache lock. This version is used by internal
   * consistency tests. This method is not intended to be called at high frequencies or
   * for attaching monitoring or logging. Use the {@link #getInfo} method for requesting
   * information for monitoring.
   */
  InternalCacheInfo getConsistentInfo();

  String getEntryState(K key);

  /**
   * This method is used for {@link ConcurrentMapWrapper#size()}
   */
  long getTotalEntryCount();

  void logAndCountInternalException(String s, Throwable t);

  boolean isNullValuePermitted();

  /**
   * Time reference for the cache.
   */
  TimeReference getClock();

  CacheEntry<K, V> returnCacheEntry(ExaminationEntry<K, V> e);

  boolean isWeigherPresent();

  boolean isLoaderPresent();

  Eviction getEviction();

  /**
   * Cache used by user, eventually wrapped. We only need to know our "wrapped self"
   * in case events are send, so only implemented by WiredCache.
   */
  default Cache<K, V> getUserCache() { return null; }

  /**
   * Cache checks its internal integrity. This is a expansive operation because it
   * may traverse all cache entries. Used for testing.
   *
   * @throws IllegalStateException if integrity problem is found
   */
  void checkIntegrity();

  String getQualifiedName();

}
