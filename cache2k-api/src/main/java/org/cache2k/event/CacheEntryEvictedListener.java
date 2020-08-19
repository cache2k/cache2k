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
 * Called when an entry gets evicted by the cache. Eviction means removal from the cache due
 * to capacity constrains. For removal because of expiry a separate event is sent.
 *
 * @author Jens Wilke
 */
public interface CacheEntryEvictedListener<K, V> extends CacheEntryOperationListener<K, V> {

  /**
   * Called upon eviction of a cache entry. When used as synchronous listener other cache
   * operations can still proceed except for this entry or {@link Cache#removeAll()}.
   *
   * @param cache The cache that generated the event
   * @param entry Entry containing the recent data
   */
  void onEntryEvicted(Cache<K, V> cache, CacheEntry<K, V> entry);

}
