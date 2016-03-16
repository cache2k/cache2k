package org.cache2k;

/*
 * #%L
 * cache2k API only package
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

import java.util.HashMap;
import java.util.Map;

/**
 * Retrieves or generates a value to load into the cache.
 *
 * <p>The alternative loader interface {@link AdvancedCacheLoader} provides the loader
 * with the current cache value.
 *
 * @author Jens Wilke
 * @see AdvancedCacheLoader
 * @since 0.24
 */
public abstract class CacheLoader<K, V> {

  /**
   * Retrieves or generates data based on the key.
   *
   * <p>API rationale: This method declares an exception to allow any unhandled
   * exceptions of the loader implementation to just pass through. Since the cache
   * needs to catch an deal with loader exceptions in any way, this saves otherwise
   * necessary try/catch clauses in the loader.
   *
   * @param key the non-null key to provide the value for.
   * @return value to be associated with the key. If the cache permits null values
   *         a null is associated with the key.
   * @throws Exception Unhandled exception from the loader. The exception will be
   *         handled by the cache based on its configuration.
   */
  public abstract V load(K key) throws Exception;

  /**
   * Loads multiple values to the cache.
   *
   * <p>The method is provided to complete the API. At the moment cache2k is not
   * using them. Bulk operation will be implemented later.
   *
   * @param keys set of keys for the values to be loaded
   * @return The loaded values. A key may map to null if the cache permits null values.
   * @throws Exception Unhandled exception from the loader. The exception will be
   *           handled by the cache based on its configuration. If an exception happens
   *           the cache will retry the load with the single value load method.
   */
  public Map<K, V> loadAll(Iterable<? extends K> keys) throws Exception {
    Map<K,V> map = new HashMap<K, V>();
    for (K key : keys) {
      map.put(key, load(key));
    }
    return map;
  }

}
