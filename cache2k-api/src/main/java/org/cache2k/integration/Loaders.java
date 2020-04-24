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

import org.cache2k.CacheEntry;

/**
 * Utility methods for cache loaders
 *
 * @author Jens Wilke
 */
public class Loaders {

  /**
   * Wraps a loaded value to add the refreshed value.
   *
   * @see CacheLoader#load(Object)
   * @see AdvancedCacheLoader#load(Object, long, CacheEntry)
   * @see FunctionalCacheLoader#load(Object)
   */
  @SuppressWarnings("unchecked")
  public static <V> LoadDetail<V> wrapRefreshedTime(V value, long refreshedTimeInMillis) {
    return new RefreshedTimeWrapper<V>(value, refreshedTimeInMillis);
  }

  public static <V> LoadDetail<V> wrapRefreshedTime(LoadDetail<V> value, long refreshedTimeInMillis) {
    return new RefreshedTimeWrapper<V>(value, refreshedTimeInMillis);
  }

}
