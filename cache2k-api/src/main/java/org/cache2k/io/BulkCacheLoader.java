package org.cache2k.io;

/*
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
 * Extension to the cache load with bulk load capabilities. There is no {@link AdvancedCacheLoader}.
 * The async variant {@link AsyncBulkCacheLoader} provides the detailed context information.
 *
 * <p>Semantics: A bulk load is issued when the cache client uses a bulk method to retrieve
 * data, like {@link org.cache2k.Cache#loadAll} or {@link org.cache2k.Cache#getAll}. Not all
 * keys that the client requests end up in the load request. In case of
 * {@link org.cache2k.Cache#getAll} mappings that are already present in the cache, will not
 * loaded again. The cache also may issue partial bulk loads in case requested entries are currently
 * processing (maybe a concurrent load or another cache operation). The bulk load will be
 * started with as much entries that can be accessed and locked as possible. Thus, multiple load
 * requests may result in one client request. Consequently, there is no guarantee about
 * atomicity or order of a bulk request.
 *
 * <p>Deadlock avoidance: When a load operation is in flight the entry is blocked and no
 * other operations take place on that entry. When operating on multiple entries, deadlocks
 * might occur, when one operation is processing and locking one entry and waiting for another
 * entry. To avoid deadlocks, the cache will only wait for one entry when holding no locks on
 * another entry. Practically speaking: The cache will issue a bulk request to as many entries
 * it can lock at once, when that request is completed, it will will try again or wait
 * for concurrent processing if no more operations can be started.
 *
 * <p>Rationale: Other cache products and JCache/JSR107 defines a {@link #loadAll}
 * on the same interface as the cache loader. The JCache variant does not work as functional
 * interface because both methods have to be implemented. Other caches implement {@link #loadAll}
 * as iteration over {@link #load(Object)} by default. With a separate interface the cache "knows"
 * whether bulk operations can be optimized and its possible to be implemented as lambda function
 * as well.
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
