package org.cache2k.io;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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
import org.cache2k.DataAware;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Extension of {@link AsyncCacheLoader} with bulk load capabilities.
 *
 * <p>See {@link BulkCacheLoader} for more details.
 *
 * @author Jens Wilke
 * @see BulkCacheLoader
 */
@FunctionalInterface
public interface AsyncBulkCacheLoader<K, V> extends AsyncCacheLoader<K, V> {

  /**
   * Load all values referenced by the key set. This method is used to load more efficiently
   * in case the cache client uses {@link org.cache2k.Cache#loadAll(Iterable)} or
   * {@link org.cache2k.Cache#getAll(Iterable)}.
   *
   * @param keys the keys to load
   * @param context context of this request with additional information. Also contains
   *                per key context with current entry values, if present.
   * @param callback Callback to post results
   * @throws Exception An exception, if the load operation cannot be started. The exception will
   *                   be assigned to every requested key depending on the resilience policy.
   */
  void loadAll(Set<K> keys, BulkLoadContext<K, V> context, BulkCallback<K, V> callback)
    throws Exception;

  /**
   * By default loads a single value via {@link #loadAll}.
   *
   * @see AsyncCacheLoader#load
   */
  @Override
  default void load(K key, Context<K, V> context, Callback<V> callback) throws Exception {
    Set<K> keySet = Collections.singleton(key);
    BulkCallback<K, V> bulkCallback = new BulkCallback<K, V>() {
      @Override
      public void onLoadSuccess(Map<? extends K, ? extends V> data) {
        if (data.isEmpty()) {
          return;
        }
        Map.Entry<? extends K, ? extends V> entry = data.entrySet().iterator().next();
        onLoadSuccess(entry.getKey(), entry.getValue());
      }

      @Override
      public void onLoadSuccess(K key, V value) {
        Objects.requireNonNull(key);
        callback.onLoadSuccess(value);
      }

      @Override
      public void onLoadFailure(Throwable exception) {
        callback.onLoadFailure(exception);
      }

      @Override
      public void onLoadFailure(K key, Throwable exception) {
        Objects.requireNonNull(key);
        callback.onLoadFailure(exception);
      }
    };
    BulkLoadContext<K, V> bulkLoadContext = new BulkLoadContext<K, V>() {
      @Override public Cache<K, V> getCache() { return context.getCache(); }
      @Override public Map<K, Context<K, V>> getContextMap() {
        return Collections.singletonMap(key, context);
      }
      @Override public long getStartTime() { return context.getStartTime(); }
      @Override public Set<K> getKeys() { return keySet; }
      @Override public Executor getExecutor() { return context.getExecutor(); }
      @Override public Executor getLoaderExecutor() { return context.getLoaderExecutor(); }
      @Override public BulkCallback<K, V> getCallback() { return bulkCallback; }
    };
    loadAll(keySet, bulkLoadContext, bulkCallback);
  }

  interface BulkLoadContext<K, V> extends DataAware<K, V> {

    /** Cache originating the request */
    Cache<K, V> getCache();

    /**
     * Individual load context for each key.
     */
    Map<K, Context<K, V>> getContextMap();

    long getStartTime();

    /**
     * Keys requested to load.
     */
    Set<K> getKeys();

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

    BulkCallback<K, V> getCallback();

  }

  interface BulkCallback<K, V> extends DataAware<K, V> {

    /**
     * The load was successful. Per bulk request this method can be called multiple times
     * to transfer partial results to the cache as soon as data is available.
     *
     * @param data loaded data, either representing all requested keys or a partial result
     */
    void onLoadSuccess(Map<? extends K, ? extends V> data);

    /**
     * A single key value tuple was loaded successfully. This method
     * can be called multiple times to send all loaded values.
     */
    void onLoadSuccess(K key, V value);

    /**
     * The load attempt resulted in an exception. If partial results are returned via
     * {@link #onLoadSuccess(Map)} the exception is only processed for the remaining keys.
     *
     * <p>The logic for exception handling, e.g. the `ResiliencePolicy` will be run for each entry
     * individually. If the bulk operation was started on behalf a user cache operation, e.g.
     * {@link org.cache2k.Cache#loadAll(Iterable)} the exception will be propagated although
     * the operation was partially successful. When called multiple times, only the first
     * call has an effect and subsequent calls do nothing.
     */
    void onLoadFailure(Throwable exception);

    /**
     * Report exception for a set of keys.  Rationale: An incoming bulk load request might
     * be split and passed on as separate bulk requests, e.g. to meet some limits. One of these
     * partial request might result in an error, the other might go through successful.
     * In this case the exception should be propagated for the corresponding set of keys.
     *
     * <p>Multiple calls with the same key have no effect.
     */
    default void onLoadFailure(Set<? extends K> keys, Throwable exception) {
      for (K key : keys) { onLoadFailure(key, exception); }
    }

    /**
     * Report exception for a specific key. Multiple calls with the same key have no effect.
     */
    void onLoadFailure(K key, Throwable exception);

  }

}
