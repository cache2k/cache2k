package org.cache2k.core;

/*
 * #%L
 * cache2k core
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

import org.cache2k.Cache;

/**
 * Access to eviction metrics. Consistent reads are only possible while inside the eviction lock.
 *
 * @author Jens Wilke
 */
public interface EvictionMetrics {

  /** Number of new created entries */
  long getNewEntryCount();

  /** Fragment that the eviction wants to add to the {@link Cache#toString()} output. */
  String getExtraStatistics();

  /** Number of recorded hits. */
  long getHitCount();

  /** Removed entries, because of programmatic removal */
  long getRemovedCount();

  /** Removed entries, because expired */
  long getExpiredRemovedCount();

  /** Removal of an entry that was never used */
  long getVirginRemovedCount();

  /** Number of entries evicted */
  long getEvictedCount();

  /** Number of eviction currently going on */
  int getEvictionRunningCount();

  /** Number of entries in the viction data structure */
  long getSize();

  /** Size limit after eviction kicks in */
  long getMaxSize();

}
