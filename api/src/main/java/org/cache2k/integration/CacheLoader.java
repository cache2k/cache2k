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

import org.cache2k.CacheOperationCompletionListener;
import org.cache2k.processor.MutableCacheEntry;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Retrieves or generates a value to load into the cache. Using a loader to automatically
 * populate the cache is called read through caching. If the cache is primarily used the
 * cache data that is expensive to generate or retrieve, using a {@code CacheLoader} has
 * several advantages. The usable features with a loader are explained in the following.
 *
 * <p>Transparent operation: If configured, the loader is invoked implicitly, in case there is no value in
 * the cache or it is expired, by the cache methods {@code get()}, {@code getAll()}
 * or {@code getEntry()} as well as {@link MutableCacheEntry#getValue()}.
 *
 * <p>The cache loader can be invoked explicitly via {@link Cache#reloadAll(CacheOperationCompletionListener, Iterable)}.
 *
 * <p>Prefetching: The method {@link Cache#prefetch(Object)} can be used to instruct the cache to load
 * multiple values in the background.
 *
 * <p>Blocking: If the loader is invoked by {@link Cache#get} or the other methods that allow transparent access
 * (see above) concurrent requests on the same key will block until the loading is completed.
 * For expired values blocking can be avoided by enabling {@link Cache2kBuilder#refreshAhead}.
 * There is no hard guarantee that the loader is invoked only for one key at a time, for example
 * after {@link Cache#clear()} is called load operations for one key may overlap.
 *
 * <p>Refresh ahead:
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
   *         values a {@link NullPointerException} is thrown, but the expiry policy is called before it.
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
   * @return The loaded values. A key may map to {@code null} if the cache permits {@code null} values.
   * @throws Exception Unhandled exception from the loader. Exceptions are suppressed or
   *                   wrapped and rethrown via a {@link CacheLoaderException}.
   *                   If an exception happens the cache may retry the load with the
   *                   single value load method.
   */
  public Map<K, V> loadAll(Iterable<? extends K> keys, Executor executor) throws Exception {
    throw new UnsupportedOperationException();
  }

}
