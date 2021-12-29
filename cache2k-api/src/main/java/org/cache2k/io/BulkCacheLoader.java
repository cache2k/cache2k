package org.cache2k.io;

/*-
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Extension to the cache load with bulk load capabilities. The async variant
 * {@link AsyncBulkCacheLoader} provides the detailed context information.
 *
 * <p>See {@link AsyncBulkCacheLoader} for more details on bulk processing.
 * The {@link AsyncBulkCacheLoader} interface should be preferred.
 *
 * @author Jens Wilke
 * @since 2.2
 * @see CacheLoader
 * @see AsyncBulkCacheLoader
 */
@FunctionalInterface
public interface BulkCacheLoader<K, V> extends CacheLoader<K, V> {

  /**
   * Load all data referenced by the key set. This operation to load more efficiently
   * in case the cache client uses {@link org.cache2k.Cache#loadAll(Iterable)} or
   * {@link org.cache2k.Cache#getAll(Iterable)}.
   *
   * @param keys keys to load
   * @return a map containing values for all keys that were requested
   */
  Map<K, V> loadAll(Set<? extends K> keys) throws Exception;

  /**
   * By default uses {@link #loadAll} to load a singe value.
   */
  @Override
  default V load(K key) throws Exception {
    return loadAll(Collections.singleton(key)).get(key);
  }

}
