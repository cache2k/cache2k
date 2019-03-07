package org.cache2k.event;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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

import java.util.EventListener;

/**
 * Listener called when cache is closed. This is intended for resource cleanup
 * of cache customizations.
 *
 * @author Jens Wilke
 */
public interface CacheClosedListener extends EventListener {

  /**
   * Called when cache closes. This is intended for resource cleanup. No heavy operations
   * are allowed, like flushing I/O.
   *
   * @param cache The cache that is closing. No cache operations on entries are allowed.
   */
  void onCacheClosed(Cache cache);

}
