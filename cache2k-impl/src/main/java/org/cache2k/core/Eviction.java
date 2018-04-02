package org.cache2k.core;

/*
 * #%L
 * cache2k implementation
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

import org.cache2k.core.concurrency.Job;

/**
 * Interface to the eviction data structure (replacement list).
 *
 * @author Jens Wilke
 */
public interface Eviction {

  /**
   * Submit to eviction for inserting or removing from the replacement list.
   * Will call the eviction if limits are reached.
   *
   * <p>Whether the entry is inserted or removed is detected via the entry
   * state {@link Entry#isNotYetInsertedInReplacementList()}.
   */
  void submit(Entry e);

  /**
   * Submit to eviction for inserting or removing from the replacement list.
   * However, eviction should not be triggered (which in turn triggers a hash table
   * update) since the hash segment lock is hold at the moment.
   */
  boolean submitWithoutEviction(Entry e);

  /**
   * Evict if needed, focused on the segment addressed by the hash code.
   * Called when eviction is might be needed after a new entry was inserted.
   */
  void evictEventually(int _hashCode);

  /**
   * Evict if needed, checks all segments.
   */
  void evictEventually();

  /**
   * Remove all entries from the eviction data structure.
   *
   * @return entry count
   */
  long removeAll();

  /**
   * Drain eviction queue and do updates in the eviction data structures.
   * Does no eviction when size limit is reached.
   *
   * @return true, if eviction is needed
   */
  boolean drain();

  /**
   * Start concurrent eviction threads.
   */
  void start();

  /**
   * Stop concurrent threads that may access the eviction data structures.
   * Needs to be called before checkIntegrity or accessing the counter
   * values.
   */
  void stop();

  /**
   * Free resources, for example thread pool or queue.
   */
  void close();

  /**
   * Runs job making sure concurrent evictions operations pause.
   */
  <T> T runLocked(Job<T> j);

  void checkIntegrity(IntegrityState _integrityState);

  EvictionMetrics getMetrics();

}
