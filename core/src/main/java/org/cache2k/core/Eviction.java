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

import org.cache2k.core.threading.Job;

/**
 * @author Jens Wilke
 */
public interface Eviction {

  void close();

  void evictEventually(int hc);
  void evictEventually();
  void execute(Entry e);

  /**
   * Submit to eviction for inserting or removing from the replacement list.
   * However, eviction should be triggered (which in turn triggers a hash table
   * update) since the hash segment lock is hold at the moment.
   */
  boolean executeWithoutEviction(Entry e);

  long removeAll();

  /**
   * Stop concurrent threads that may access the eviction data structures.
   * Needs to be called before checkIntegrity or accessing the counter
   * values.
   */
  void stop();

  boolean drain();

  /**
   * Start concurrent eviction threads.
   */
  void start();

  <T> T runLocked(Job<T> j);

  void checkIntegrity(IntegrityState _integrityState);
  String getExtraStatistics();

  long getHitCount();
  long getNewEntryCount();
  long getRemovedCount();
  long getExpiredRemovedCount();
  long getVirginRemovedCount();
  long getEvictedCount();
  long getSize();
  long getMaxSize();

}
