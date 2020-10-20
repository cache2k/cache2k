package org.cache2k.integration;

/*
 * #%L
 * cache2k API
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

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * @deprecated Replaced with {@link org.cache2k.io.CacheLoader},
 *   to be removed in version 2.2
 */
@Deprecated
public abstract class CacheLoader<K, V> implements FunctionalCacheLoader<K, V> {

  /**
   * Retrieves or generates data based on the key.
   *
   * <p>From inside this method it is illegal to call methods on the same cache. This
   * may cause a deadlock.
   *
   * <p>API rationale: This method declares an exception to allow any unhandled
   * exceptions of the loader implementation to just pass through. Since the cache
   * needs to catch an deal with loader exceptions in any way, this saves otherwise
   * necessary try/catch clauses in the loader.
   *
   * @param key the non-null key to provide the value for.
   * @return value to be associated with the key. If the cache does not permit {@code null}
   *         values a {@link NullPointerException} is thrown, but the expiry policy is
   *         called before it.
   * @throws Exception Unhandled exception from the loader. Exceptions are suppressed or
   *                   wrapped and rethrown via a {@link CacheLoaderException}
   */
  public abstract V load(K key) throws Exception;

  /**
   * Loads multiple values to the cache.
   *
   * <p>From inside this method it is illegal to call methods on the same cache. This
   * may cause a deadlock.
   *
   * <p>The method is provided to complete the API. At the moment cache2k is not
   * using it. Please see the road map.
   *
   * @param keys set of keys for the values to be loaded
   * @param executor an executor for concurrent loading
   * @return The loaded values. A key may map to {@code null} if the cache permits
   *         {@code null} values.
   * @throws Exception Unhandled exception from the loader. Exceptions are suppressed or
   *                   wrapped and rethrown via a {@link CacheLoaderException}.
   *                   If an exception happens the cache may retry the load with the
   *                   single value load method.
   */
  public Map<K, V> loadAll(Iterable<? extends K> keys, Executor executor) throws Exception {
    throw new UnsupportedOperationException();
  }

}
