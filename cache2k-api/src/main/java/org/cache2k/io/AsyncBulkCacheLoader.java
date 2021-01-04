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

import org.cache2k.DataAware;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
   * Load all data referenced by the key set. This operation to load more efficiently
   * in case the cache client uses {@link org.cache2k.Cache#loadAll(Iterable)} or
   * {@link org.cache2k.Cache#getAll(Iterable)}.
   *
   * @param keys the keys to load
   * @param contextSet set of contexts that contain the keys to load as well as more detailed
   *             information
   * @return a map containing values for all keys that were requested
   */
  void loadAll(Set<K> keys, Set<Context<K, V>> contextSet, BulkCallback<K, V> callback)
    throws Exception;

  /**
   * By default loads a single value via {@link #loadAll}.
   *
   * @see AsyncCacheLoader#load
   */
  @Override
  default void load(K key, Context<K, V> context, Callback<V> callback) throws Exception {
    loadAll(Collections.singleton(key), Collections.singleton(context), new BulkCallback<K, V>() {
      @Override
      public void onLoadSuccess(Map<? extends K, ? extends V> data) {
        Map.Entry<? extends K, ? extends V> entry = data.entrySet().iterator().next();
        onLoadSuccess(entry.getKey(), entry.getValue());
      }

      @Override
      public void onLoadSuccess(K key, V value) {
        Objects.requireNonNull(key);
        callback.onLoadSuccess((V) value);
      }

      @Override
      public void onLoadFailure(Throwable exception) {
        callback.onLoadFailure(exception);
      }
    });
  }

  interface BulkCallback<K, V> extends DataAware<K, V> {

    /**
     * The load was successful.
     *
     * @param data Loaded data, can be partial
     */
    void onLoadSuccess(Map<? extends K, ? extends V> data);

    /**
     * A single key value tuple was loaded successfully. This method
     * can be called multiple times to send all loaded values.
     */
    void onLoadSuccess(K key, V value);

    /**
     * The processing resulted in an exception. The logic for exception
     * handling, e.g. the `ResiliencePolicy` will be run for each entry
     * individually. If partial results are returned via {@link #onLoadSuccess(Map)}
     * the exception is only processed for the remaining keys.
     * If the bulk operation was started on behalf a user cache operation, e.g.
     * {@link org.cache2k.Cache#loadAll(Iterable)} the exception will be propagated even
     * if partially successful.
     */
    void onLoadFailure(Throwable exception);

  }

}
