package org.cache2k.impl.operation;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
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
   * {@link org.cache2k.impl.operation.Semantic.Base}
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
