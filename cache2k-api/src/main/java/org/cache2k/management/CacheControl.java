package org.cache2k.management;

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

import org.cache2k.Cache;

/**
 * General functions to control and tune a cache.
 *
 * <p>Outlook: CacheControl may get ability to control expiry and refresh behavior
 *
 * @author Jens Wilke
 */
public interface CacheControl {

  /**
   * Clears the cache contents. Identical to {@link Cache#clear()}
   */
  void clear();

  /**
   * End cache operations. Identical to {@link Cache#close()}
   */
  void close();

  /**
   * A combination of {@link Cache#clear} and {@link Cache#close} potentially
   * wiping all stored data of this cache.
   *
   * <p>This method is to future proof the API, when a persistence feature is added.
   * In this case the method will stop cache operations and remove all stored external data.
   *
   * <p>Rationale: The corresponding method in JSR107 is {@code CacheManager.destroyCache()}.
   */
  void destroy();

  /**
   * Change the maximum capacity of the cache. If a weigher is present
   * this is the maximum weight of all cache entries, otherwise the maximum count
   * of cache entries. The capacity is not allowed to be 0.
   *
   * @see org.cache2k.Weigher
   */
  void changeCapacity(long entryCountOrWeight);

}
