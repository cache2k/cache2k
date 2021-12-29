package org.cache2k.core.eviction;

/*-
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

import org.cache2k.core.api.InternalCacheInfo;

/**
 * Access to eviction metrics. Eviction counters are separate from
 * {@link org.cache2k.core.api.CommonMetrics} because its more efficient to implement
 * these counters within the eviction algorithm and its respective locking.
 *
 * @author Jens Wilke
 */
public interface EvictionMetrics {

  /**
   * @see InternalCacheInfo#getNewEntryCount()
   */
  long getNewEntryCount();

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

  /** Accumulated weight of evicted or deleted entries */
  long getEvictedWeight();

  /**
   * Count of entries scanned for eviction. Used to evict idle entries.
   *
   * @see IdleProcessing
   */
  long getScanCount();

  /**
   * Entries removed from the cache which have either expired or are removed
   * programmatically, or in other words, they were removed but not evicted.
   * Reset at the start of each idle scan round. This is used to compensate
   * scan rates for idle scanning rounds.
   *
   * @return number of entries actively removed since scan round start.
   * @see IdleProcessing
   */
  long getIdleNonEvictDrainCount();

}
