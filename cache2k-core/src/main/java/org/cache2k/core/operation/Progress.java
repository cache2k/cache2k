package org.cache2k.core.operation;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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

import org.cache2k.core.util.InternalClock;
import org.cache2k.integration.ExceptionInformation;

/**
 * Interface for cache operation semantics to control the progress of the processing.
 *
 * @see Semantic
 *
 * @author Jens Wilke
 */
public interface Progress<K, V, R> {

  /**
   * The current time in millis or the value when it was first called.
   */
  long getCurrentTime();

  /**
   * Requests that the cache content for an entry will be provided.
   * If the cache is tiered, the data will be read into the heap cache.
   * Last command of semantic method. Calls back on
   * {@link Semantic#examine(Progress, ExaminationEntry)}
   */
  void wantData();

  /**
   * Entry has valid data in the cache and is not expired. This is used for
   * operations that do not want to access the value.
   */
  boolean isPresent();

  /**
   * Same as {@link #isPresent()} but also true if the entry is in refresh probation.
   */
  boolean isPresentOrInRefreshProbation();

  /**
   * Entry has valid data in the cache and is not expired. Counts a miss, if no entry is
   * available, this is used for operations that do want to access the value.
   */
  boolean isPresentOrMiss();

  /**
   * The entry gets locked for mutation. Last command of semantic method.
   * Calls back on {@link Semantic#update(Progress, ExaminationEntry)}
   */
  void wantMutation();

  /**
   * No mutation is done. Last command of semantic method.
   */
  void noMutation();

  /**
   * Sets the operation result.
   */
  void result(R result);

  /**
   * Returns a cache entry as result. The entry will be copied before returning.
   */
  void entryResult(ExaminationEntry e);

  /**
   * Needed for the mutable entry getValue() to throw an exception.
   */
  RuntimeException propagateException(K key, ExceptionInformation inf);

  /**
   * Request that the entry value gets loaded from loader. Last command of semantic method.
   * By default the loaded value will be set as operation result. Defined in
   * {@link org.cache2k.core.operation.Semantic.Base}
   */
  void load();

  /**
   * Same as load but counting statistics as refresh. Last command of semantic method.
   */
  void refresh();

  /**
   * Request a load, and then call update again for the final outcome. Last command of semantic method.
   */
  void loadAndMutation();

  /**
   * Reset expiry to the specified value. Don't change the value.
   */
  void expire(long expiryTime);

  /**
   * The entry will be removed. Last command of semantic method.
   */
  void remove();

  /**
   * Update the entry with the new value. Last command of semantic method.
   */
  void put(V value);

  /**
   * Bad things happened, propagate the exception to the client. The original exception
   * must be wrapped. The calling stacktrace will be filled into the wrapped exception
   * before thrown. Last command of semantic method.
   */
  void failure(RuntimeException t);

  /**
   * Set new value, skip expiry calculation and set expiry time directly.
   */
  void putAndSetExpiry(V value, long expiryTime, final long refreshTime);

}
