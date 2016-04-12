package org.cache2k.event;

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

import org.cache2k.Cache;
import org.cache2k.CacheEntry;

/**
 * Listener called for an expired entry. An expiry event may not be sent if an
 * entry is refreshed before the expiry is detected. In this case an update event is sent.
 *
 * <p>Expiry events are not yet completely implemented.
 *
 * @author Jens Wilke
 */
public interface CacheEntryExpiredListener<K, V> extends CacheEntryOperationListener<K,V> {

  void onEntryExpired(Cache<K,V> c, CacheEntry<K,V> e);

}
