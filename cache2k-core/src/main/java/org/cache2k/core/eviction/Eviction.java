package org.cache2k.core.eviction;

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

import org.cache2k.core.Entry;
import org.cache2k.core.IntegrityState;
import org.cache2k.core.api.NeedsClose;

import java.util.function.Supplier;

/**
 * Interface to the eviction data structure.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("rawtypes")
public interface Eviction extends NeedsClose {

  /**
   * Start new idle scan round and return current scan counter
   * @see IdleProcessing
   */
  long startNewIdleScanRound();

  /**
   * Submit to eviction for inserting or removing from the replacement list.
   * However, eviction should not be triggered (which in turn triggers a hash table
   * update) since the hash segment lock is hold at the moment.
   */
  boolean submitWithoutTriggeringEviction(Entry e);

  /**
   * Updates the weight on the entry and recalculates the total weight if needed.
   *
   * <p>Expected not to hold the entry lock, which means, that this does not run
   * in sync with the actual update. That is okay as long as it runs after every
   * update.
   *
   * <p>Since we need to lock the eviction structure, this can happen in a separate thread.
   *
   * @return hint whether eviction should be run. for a bulk operation we want to do
   *         eviction once, so not do it within this method
   */
  boolean updateWeight(Entry e);

  /**
   * Evict if needed, focused on the segment addressed by the hash code.
   * Called before a new entry is inserted (changed from after in v1.4)
   */
  void evictEventuallyBeforeInsertOnSegment(int hashCodeHint);

  /**
   * Evict if needed, checks all segments.
   * Called before a new entry is inserted (changed from after in v1.4)
   */
  void evictEventuallyBeforeInsert();

  /**
   * Evict if needed, checks all segments.
   */
  void evictEventually();

  /**
   * Scan for idle (no access count since last scan) entries and evict them.
   *
   * @return number of evicted entries
   * @see IdleProcessing
   */
  long evictIdleEntries(int maxScan);

  /**
   * Remove all entries from the eviction data structure.
   *
   * @return entry count
   */
  long removeAll();

  /**
   * Runs job making sure concurrent evictions operations pause.
   */
  <T> T runLocked(Supplier<T> j);

  void checkIntegrity(IntegrityState integrityState);

  /**
   * Get metrics related to the eviction. No lock is needed before calling this method.
   * Best efforts are mad to extract consistent statistics and interruption short.
   */
  EvictionMetrics getMetrics();

  boolean isWeigherPresent();

  /**
   * Change the capacity. If capacity is reduced, it will evict entries
   * before returning.
   */
  void changeCapacity(long entryCountOrWeight);

}
