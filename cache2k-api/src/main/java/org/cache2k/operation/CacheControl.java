package org.cache2k.operation;

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

/**
 * Combined interface for introspection and control functions of a cache relevant
 * for management and monitoring.
 *
 * @author Jens Wilke
 */
public interface CacheControl extends CacheOperation, CacheInfo {

  /**
   * Request an management interface of the given cache.
   */
  static CacheControl of(Cache<?, ?> cache) {
    return cache.requestInterface(CacheControl.class);
  }

  /**
   * Returns a snapshot of cache statistics if this cache supports statistics
   * or {@code null} otherwise.
   */
  CacheStatistics sampleStatistics();

}
