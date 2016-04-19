package org.cache2k.core.operation;

/*
 * #%L
 * cache2k core package
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

/**
 * Interface for cache operation semantics to control the progress of the processing.
 *
 * @see Semantic
 *
 * @author Jens Wilke
 */
public interface Progress<V, R> {

  /**
   * Requests that the cache content for an entry will be provided.
   * If the cache is tiered, the data will be read into the heap cache.
   * Last command of semantic method. Calls back on
   * {@link Semantic#examine(Progress, ExaminationEntry)}
   */
  void wantData();

  /**
   * Entry has valid data in the cache and is not expired. This is used for all
   * operations that do not want to access the value.
   */
  boolean isPresent();

  /**
   * Entry has valid data in the cache and is not expired. This is used for all
   * operations that do not want to access the value.
   */
  boolean isPresentOrMiss();

  /**
   * The entry gets locked for mutation. Last command of semantic method.
   *  Calls back on {@link Semantic#update(Progress, ExaminationEntry)}
   */
  void wantMutation();

  /**
   * Sets the operation result.
   */
  void result(R result);

  /**
   * Request that the entry value gets loaded from loader. Last command of semantic method.
   * By default the loaded value will be set as operation result. Defined in
   * {@link org.cache2k.core.operation.Semantic.Base}
   */
  void load();

  /**
   * Request a load, however call update again for the final outcome.
   */
  void loadAndMutation();

  /**
   * The entry will be removed. Last command of semantic method.
   */
  void remove();

  /**
   * Update the entry with the new value. Last command of semantic method.
   */
  void put(V value);

  /**
   * Bad things happened, propagate the exception to the client.
   */
  void failure(Throwable t);

}
