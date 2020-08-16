package org.cache2k.jmx;

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

/**
 * Adds actions to the cache mx bean.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("unused")
public interface CacheMXBean extends CacheInfoMXBean {

  /**
   * Clears the cache contents.
   */
  void clear();

  /**
   * Change the maximum capacity of the cache. If a weigher is present
   * this is the maximum weight of all cache entries, otherwise the maximum count
   * of cache entries. The capacity is not allowed to be 0.
   *
   * @see org.cache2k.Weigher
   */
  void changeCapacity(long entryCountOrWeight);

}
