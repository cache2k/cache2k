package org.cache2k.core;

/*
 * #%L
 * cache2k implementation
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

/**
 * Access to eviction metrics. Consistent reads are only possible while inside the eviction lock.
 *
 * @author Jens Wilke
 */
public interface EvictionMetrics {

  /**
   * @see InternalCacheInfo#getNewEntryCount()
   */
  long getNewEntryCount();

  /** Number of recorded hits. */
  long getHitCount();

  /**
   * @see InternalCacheInfo#getRemoveCount()
   */
  long getRemovedCount();

  /**
   * Removed entries, because expired
   *
   * @see InternalCacheInfo#getExpiredCount()
   */
  long getExpiredRemovedCount();

  /** Removal of an entry that was never used */
  long getVirginRemovedCount();

  /**
   * Number of entries evicted
   *
   * @see InternalCacheInfo#getEvictedCount()
   */
  long getEvictedCount();

  /** Number of eviction currently going on */
  int getEvictionRunningCount();

  /** Number of entries in the eviction data structure */
  long getSize();

  /** Size limit after eviction kicks in */
  long getMaxSize();

  long getMaxWeight();

  /** Accumulated weight of all entries currently controlled by eviction. */
  long getTotalWeight();

  /** Fragment that the eviction wants to add to the {@link org.cache2k.Cache#toString()} output. */
  String getExtraStatistics();

  /** Accumulated weight of evicted or deleted entries */
  long getEvictedWeight();

}
