package org.cache2k.integration;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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

import java.util.EventListener;
import java.util.concurrent.Executor;

/**
 * @author Jens Wilke
 */
public interface AsyncCacheLoader<K,V> {

  /**
   * Starts an asynchronous load operation.
   *
   * @param key key of the value to load
   * @param context additional context information for the load operation
   * @param callback callback interface to notify on load completion
   * @throws Exception an exception, if the load operation cannot be started
   */
  void load(K key, Context<K, V> context, Callback<K, V> callback) throws Exception;

  /**
   * Relevant context information for a single load request.
   *
   * <p>Rationale: Instead of a rather long parameter list, we define an interface.
   * This allows us later to add some information without breaking implementations
   * of the {@link AsyncCacheLoader}.
   */
  interface Context<K, V> {

    /**
     * Cache key for the load request.
     */
    K getKey();

    /**
     * The configured loader executor.
     *
     * @see org.cache2k.Cache2kBuilder#loaderExecutor(Executor)
     */
    Executor getLoaderExecutor();

    /**
     * Currently cached value. This is present even if it is expired.
     */
    V getCachedValue();

    /**
     * Currently cached exception. This present even if it is expired.
     */
    Throwable getCachedException();

    /**
     * Time in millis since epoch of start of load operation
     */
    long getCurrentTime();

  }

  /**
   * Callback for async cache load.
   *
   * @author Jens Wilke
   */
  interface Callback<K, V> extends EventListener {

    void onLoadSuccess(V value);

    void onLoadFailure(Throwable t);

  }

}
