package org.cache2k.io;

/*-
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.DataAware;
import org.cache2k.DataAwareCustomization;
import org.cache2k.annotation.Nullable;

import java.util.EventListener;
import java.util.concurrent.Executor;

/**
 * Alternative interface to {@link CacheLoader} for asynchronous operation.
 * There is also version with bulk support, {@link AsyncBulkCacheLoader}.
 *
 * @author Jens Wilke
 * @since 2.0
 * @see CacheLoader
 * @see AsyncBulkCacheLoader
 */
@FunctionalInterface
public interface AsyncCacheLoader<K, V> extends DataAwareCustomization<K, V> {

  /**
   * Starts an asynchronous load operation.
   *
   * <p>If this call throws an exception, it is assumed that the load operation was not
   * started and the callback will not be called.
   *
   * @param key key of the value to load
   * @param context additional context information for the load operation
   * @param callback interface to notify for load completion
   * @throws Exception an exception, if the load operation cannot be started
   */
  void load(K key, Context<K, V> context, Callback<V> callback) throws Exception;

  /**
   * Relevant context information for a single load request.
   *
   * <p>Rationale: Instead of a rather long parameter list, we define an interface.
   * This allows us later to add some information without breaking implementations
   * of the {@link AsyncCacheLoader}. The context does not include the cache, since the
   * loader should not depend on it and do any other operations on the cache while loading.
   */
  interface Context<K, V> extends DataAware<K, V> {

    /**
     * The cache originating the load request
     */
    Cache<K, V> getCache();

    /**
     * Start of load operation
     *
     * @return Time in millis since epoch or as defined by
     *         {@link org.cache2k.operation.TimeReference}.
     */
    long getStartTime();

    /**
     * Cache key for the load request. Although the key is a call parameter
     * it is repeated here, so users can choose to pass on the key or the
     * whole context.
     */
    K getKey();

    /**
     * The configured executor for async operations.
     *
     * @see org.cache2k.Cache2kBuilder#executor(Executor)
     */
    Executor getExecutor();

    /**
     * The configured loader executor.
     *
     * @see org.cache2k.Cache2kBuilder#loaderExecutor(Executor)
     */
    Executor getLoaderExecutor();

    /**
     * Current entry in the cache. The entry is available if the load is caused
     * by reload or refresh. If expired before, {@code null} is returned.
     * If {@link Cache2kBuilder#keepDataAfterExpired(boolean)} is enabled, also
     * an expired entry is provided to the loader for optimization purposes.
     * See also the description of
     * {@link Cache2kBuilder#keepDataAfterExpired(boolean)} and
     * {@link Cache2kBuilder#refreshAhead(boolean)}.
     *
     * @return the current entry if a mapping is present in the cache, or {@code null}
     */
    @Nullable CacheEntry<K, V> getCurrentEntry();

    /**
     * Operation is refresh and not an immediate client request. This can be used
     * to execute the operation at a lower priority. The {@code CoalescingBulkLoader}
     * is using the flag to delay refresh requests and combine them into bulk requests.
     */
    boolean isRefreshAhead();
  }

  /**
   * Callback for async cache load.
   */
  interface Callback<V> extends EventListener {

    /**
     * Called to provide the loaded value to be stored in the cache.
     *
     * @throws IllegalStateException if the callback was already made
     */
    void onLoadSuccess(V value);

    /**
     * Called if a failure happened. The exception is propagated to
     * the clients accessing the associated key.
     *
     * @throws IllegalStateException if the callback was already made
     */
    void onLoadFailure(Throwable t);

  }

}
