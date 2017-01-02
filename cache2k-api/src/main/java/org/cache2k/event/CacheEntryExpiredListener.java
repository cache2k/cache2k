package org.cache2k.event;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2017 headissue GmbH, Munich
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
 * Listener called for an expired entry. An expiry event may not be sent if an
 * entry is refreshed before the expiry is detected. In this case an update event is sent.
 *
 * @author Jens Wilke
 */
public interface CacheEntryExpiredListener<K, V> extends CacheEntryOperationListener<K,V> {

  /**
   * Called after the expiry of an entry. The call is always done in the asynchronous thread
   * pool and does not block cache internal resources.
   *
   * @param cache Reference to the cache that generated the event.
   * @param entry Entry containing the last data. It is only valid to access the object during the
   *                     call of this method. The object value may become invalid afterwards.
   */
  void onEntryExpired(Cache<K,V> cache, CacheEntry<K,V> entry);

}
