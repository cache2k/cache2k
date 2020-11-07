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
import org.cache2k.config.CacheBuildContext;

import java.util.concurrent.CompletableFuture;

/**
 * @author Jens Wilke
 */
public interface CacheCreatedListener extends CacheLifecycleListener {

  /**
   * A new cache has been created.
   *
   * @param ctx The build context of the cache. The listener may read
   *            but not modify its configuration parameters.
   * @return {@code null} or a CompletableFuture, if this method uses async
   *         processing
   */
  <K, V> CompletableFuture<Void> onCacheCreated(Cache<K, V> cache,
                                                CacheBuildContext<K, V> ctx);

}
