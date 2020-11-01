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

import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;

/**
 * @deprecated Replaced with {@link org.cache2k.io.AdvancedCacheLoader},
 *   to be removed in version 2.2
 */
@Deprecated
public abstract class AdvancedCacheLoader<K, V>
  implements org.cache2k.io.AdvancedCacheLoader<K, V> {

  /**
   * Retrieves or generates data based on the key parameter.
   *
   * @param key The non-null key to provide the value for.
   * @param startTime Time in millis, retrieved before the call.
   * @param currentEntry Current entry in the cache. The entry is available if the load is caused
   *                     by a reload or refresh. If expired before, this is null {@code null}.
   *                     If {@link Cache2kBuilder#keepDataAfterExpired(boolean)} is enabled, also
   *                     an expired entry is provided to the loader for optimization purposes.
   *                     If the previous load had thrown an excetion, this is {@code null}.
   *                     See also the description of
   *                     {@link Cache2kBuilder#keepDataAfterExpired(boolean)} and
   *                     {@link Cache2kBuilder#refreshAhead(boolean)}.
   * @return value to be associated with the key. If the cache permits {@code null} values
   *         a {@code null} is associated with the key.
   * @throws Exception Unhandled exception from the loader. Exceptions are suppressed or
   *                   wrapped and rethrown via a {@link CacheLoaderException}
   */
  @Override
  public abstract V load(K key, long startTime, CacheEntry<K, V> currentEntry) throws Exception;

}
