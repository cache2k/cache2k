package org.cache2k.core.operation;

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

import org.cache2k.integration.ExceptionPropagator;

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
  long getMutationStartTime();

  /**
   * Requests that the cache content for an entry will be provided.
   * If the cache is tiered, the data will be read into the heap cache.
   * Last command of semantic method. Calls back on
   * {@link Semantic#examine(Progress, ExaminationEntry)}
   */
  void wantData();

  /**
   * A loader is present and the cache operates in read through.
   */
  boolean isLoaderPresent();

  /**
   * Entry has valid data in the cache and is not expired. This is used for
   * operations that do not want to access the value. If needed this will
   * check the clock.
   */
  boolean isDataFresh();

  /**
   * Entry reached the expiry time and expiry event can be sent and
   * entry state can be changed to expired.
   */
  boolean isExpiryTimeReachedOrInRefreshProbation();

  /**
   * Same as {@link #isDataFresh()} but also true if the entry is refreshing
   * or refreshed and in refresh probation. We need to take into account
   * refreshed entries for deciding whether we need to mutate for removal
   * or not.
   */
  boolean isDataFreshOrRefreshing();

  /**
   * Entry has valid data in the cache and is not expired. Counts a miss, if no entry is
   * available, this is used for operations that do want to access the value.
   */
  boolean isDataFreshOrMiss();

  /**
   * Value was loaded before as part of this operation.
   */
  boolean wasLoaded();

  /**
   * The entry gets locked for mutation. Last command of semantic method.
   * Calls {@link Semantic#examine(Progress, ExaminationEntry)} again after lock
   * is obtain to assess the the state again. Calls back on
   * {@link Semantic#mutate(Progress, ExaminationEntry)} if lock is already taken.
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
  void entryResult(ExaminationEntry<K, V> e);

  /**
   * Exception propagator in effect.
   */
  ExceptionPropagator getExceptionPropagator();

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
   * Request a load, and then call examine again to reevaluate. Last command of semantic method.
   */
  void loadAndRestart();

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
  void putAndSetExpiry(V value, long expiryTime, long refreshTime);

}
