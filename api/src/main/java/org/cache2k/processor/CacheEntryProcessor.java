package org.cache2k.processor;

/*
 * #%L
 * cache2k API
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

/**
 * An invokable function to perform an atomic operation on a cache entry. For actions
 * on the {@link MutableCacheEntry} only the final effect will be applied to the cache.
 *
 * @author Jens Wilke
 */
public interface CacheEntryProcessor<K, V, R> {

  /**
   * Applies mutations to an entry.
   *
   * @param entry      the entry to mutate
   * @param arguments  passed arguments to {@link Cache#invoke}
   * @return Use defined result
   * @throws Exception an arbitrary exception that will be wrapped into a {@link CacheEntryProcessingException}
   */
  R process(MutableCacheEntry<K, V> entry, Object... arguments) throws Exception;

}
