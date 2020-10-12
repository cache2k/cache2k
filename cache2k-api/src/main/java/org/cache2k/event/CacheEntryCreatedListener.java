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
import org.cache2k.io.CacheLoader;

/**
 * A new entry is inserted into the cache, e.g. by {@link Cache#put(Object, Object)} or by
 * read through and the {@link CacheLoader}.
 *
 * @author Jens Wilke
 */
public interface CacheEntryCreatedListener<K, V> extends CacheEntryOperationListener<K, V> {

  /**
   * Called for the creation of a cache entry and after all cache writers ran successfully.
   * A synchronous event is executed before the entry becomes visible for cache clients.
   * If an inserted or loaded value expires immediately, no created event is sent.
   *
   * <p>Exceptions thrown by asynchronous listeners will be propagated to the cache client
   * directly.
   *
   * @param cache Reference to the cache that generated the event.
   * @param entry Entry containing the current data. It is only valid to access the object during
   *              the call of this method. The object value may become invalid afterwards.
   */
  void onEntryCreated(Cache<K, V> cache, CacheEntry<K, V> entry);

}
