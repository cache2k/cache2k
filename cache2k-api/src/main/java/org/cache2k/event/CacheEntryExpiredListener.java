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

import java.util.concurrent.Callable;

/**
 * Listener called when an entry expires.
 *
 * <p>The listener is called after an entry logically expired. The
 * An event may be delayed
 * or suppressed if a long running operation is
 * working on the, for example a load or a {@link Cache#computeIfAbsent(Object, Callable)}.
 * In case a load is triggered by the expiry an expiry event is send before the load
 * is started.
 *
 * @author Jens Wilke
 */
public interface CacheEntryExpiredListener<K, V> extends CacheEntryOperationListener<K, V> {

  /**
   * Called after the expiry of an entry.
   *
   * @param cache Reference to the cache that generated the event.
   * @param entry Entry containing the last data. It is only valid to access the object during the
   *              call of this method. The object value may become invalid afterwards.
   */
  void onEntryExpired(Cache<K, V> cache, CacheEntry<K, V> entry);

}
