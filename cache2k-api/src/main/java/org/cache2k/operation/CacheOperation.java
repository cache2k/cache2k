package org.cache2k.operation;

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

import java.util.concurrent.CompletableFuture;

/**
 * Commands to influence the cache operation in general.
 *
 * <p>All commands return a {@link CompletableFuture}. A cache client may choose
 * to wait for the operation to complete or not. As of version 2.0 all operations
 * are completed instantly within the calling thread. This will change in later
 * versions.
 *
 * <p>Outlook: Could get ability to control expiry and refresh behavior, as well
 * as disable the cache.
 *
 * @author Jens Wilke
 * @since 2.0
 */
public interface CacheOperation {

  /**
   * Clears the cache contents. Identical to {@link Cache#clear()}
   *
   * @return See class description
   */
  CompletableFuture<Void> clear();

  /**
   * Removes all cache contents. This has the same semantics of calling
   * remove to every key, except that the cache is trying to optimize the
   * bulk operation. Same as {@code clear} but listeners will be called.
   */
  CompletableFuture<Void> removeAll();

  /**
   * End cache operations. Identical to {@link Cache#close()}
   *
   * @return See class description
   */
  CompletableFuture<Void> close();

  /**
   * A combination of {@link Cache#clear} and {@link Cache#close} potentially
   * wiping all stored data of this cache.
   *
   * <p>This method is to future proof the API, when a persistence feature is added.
   * In this case the method will stop cache operations and remove all stored external data.
   *
   * <p>Rationale: The corresponding method in JSR107 is {@code CacheManager.destroyCache()}.
   *
   * @return See class description
   */
  CompletableFuture<Void> destroy();

  /**
   * Change the maximum capacity of the cache. If a weigher is present
   * this is the maximum weight of all cache entries, otherwise the maximum count
   * of cache entries.
   *
   * <p>The value of {@code 0} has the special function to disable the cache.
   * Disabling the cache is only useful in development or if no concurrent
   * access is happening. When the cache is disabled an cache entries will still
   * be inserted and subsequent load requests on the same entry will block.
   * After every cache operation the entry is immediately expired.
   *
   * @see Weigher
   * @param entryCountOrWeight either maximum number of entries or maximum weight
   *
   * @return See class description
   */
  CompletableFuture<Void> changeCapacity(long entryCountOrWeight);

}
