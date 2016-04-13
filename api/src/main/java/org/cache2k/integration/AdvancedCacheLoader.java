package org.cache2k.integration;

/*
 * #%L
 * cache2k API only package
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;

/**
 * Retrieves or generates a value to load into the cache. The advanced loader interface
 * contains the current time and the previous cache entry. The previous cache entry
 * can be used for a more intelligent loading strategy, e.g. for HTTP based loading with
 * the <code>If-Modified-Since</code> header.
 *
 * @author Jens Wilke
 * @see CacheLoader
 * @since 0.24
 */
public abstract class AdvancedCacheLoader<K,V> {

  /**
   * Retrieves or generates data based on the key.
   *
   * @param key The non-null key to provide the value for.
   * @param currentTime Time in millis, retrieved before the call.
   * @param previousEntry entry currently in the cache, regardless whether expired or not.
   *                     There is no guarantee that an expired entry will be provided to the loader.
   *                     Depending und passed time and configuration expired entries may be purged.
   *                     Check the configuration parameters {@link Cache2kBuilder#keepDataAfterExpired(boolean)}
   *                      and {@link Cache2kBuilder#refreshAhead(boolean)}.
   * @return value to be associated with the key. If the cache permits null values
   *         a null is associated with the key.
   * @throws Exception Unhandled exception from the loader. The exception will be
   *         handled by the cache based on its configuration.
   */
  public abstract V load(K key, long currentTime, CacheEntry<K,V> previousEntry) throws Exception;

}
