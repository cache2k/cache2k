package org.cache2k.integration;

/*
 * #%L
 * cache2k API
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Retrieves or generates a value to load into the cache.
 *
 * <p>The alternative loader interface {@link AdvancedCacheLoader} provides the loader
 * with the current cache value.
 *
 * @author Jens Wilke
 * @see AdvancedCacheLoader
 */
public abstract class CacheLoader<K, V> {

  /**
   * Retrieves or generates data based on the key.
   *
   * <p>Concurrent load requests on the same key will be blocked.
   *
   * <p>This method may not mutate the cache contents directly.
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
   * <p>This method may not mutate the cache contents directly.
   *
   * <p>The method is provided to complete the API. At the moment cache2k is not
   * using it. Please see the road map.
   *
   * @param keys set of keys for the values to be loaded
   * @param executor an executor for concurrent loading
   * @return The loaded values. A key may map to null if the cache permits null values.
   * @throws Exception Unhandled exception from the loader. The exception will be
   *           handled by the cache based on its configuration. If an exception happens
   *           the cache will retry the load with the single value load method.
   */
  public Map<K, V> loadAll(Iterable<? extends K> keys, Executor executor) throws Exception {
    throw new UnsupportedOperationException();
  }

}
