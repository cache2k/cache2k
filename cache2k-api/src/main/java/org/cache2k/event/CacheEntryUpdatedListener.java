package org.cache2k.event;

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
import org.cache2k.CacheEntry;

/**
 * Fires in case a cache entry is mutated. This happens on {@link Cache#put} and its variants
 * if a previous entry exists, or if a value is reloaded or refreshed after expiry.
 *
 * @author Jens Wilke
 */
public interface CacheEntryUpdatedListener<K, V> extends CacheEntryOperationListener<K, V> {

  /**
   * Called after a mutation of a cache entry and after all cache writers ran successfully.
   *
   * <p>Exceptions thrown by asynchronous listeners will be propagated to the cache client
   * directly.
   *
   * @param cache Reference to the cache that generated the event.
   * @param currentEntry Entry containing the current data. It is only valid to access the object
   *                     during the call of this method. The object value may become invalid
   *                     afterwards.
   * @param entryWithNewData entry containing the data after the update operation was applied.
   *                         The data will be visible after all synchronous events are processed.
   *                         It is only valid to access the object during the
   *                     call of this method. The object value may become invalid afterwards.
   */
  void onEntryUpdated(
    Cache<K, V> cache, CacheEntry<K, V> currentEntry, CacheEntry<K, V> entryWithNewData);

}
