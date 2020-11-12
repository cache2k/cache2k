package org.cache2k;

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

import org.cache2k.io.CacheLoader;
import org.cache2k.io.CacheWriter;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Map;

/**
 * Reduced interface for read/write data access.
 *
 * <p>This interface contains no methods that expose or mutate the
 * cache state. This is intended as a reduced interface that
 * has transparent semantics in cache through operation.
 *
 * @author Jens Wilke
 */
public interface KeyValueStore<K, V>
  extends AdvancedKeyValueSource<K, V> {

  /**
   * Insert or update a value associated with the given key.
   *
   * @see Cache#put(Object, Object)
   * @param key key with which the specified value is associated
   * @param value value to be associated with the specified key
   */
  void put(K key, V value);

  /**
   * Insert or update all elements of the map into the cache.
   *
   * @param valueMap Map of keys with associated values to be inserted in the cache
   * @throws NullPointerException if one of the specified keys is null
   */
  void putAll(Map<? extends K, ? extends V> valueMap);

  /**
   * Remove a value from the cache that is associated with the key.
   *
   * <p>Rationale: It is intentional that this method does not return
   * a boolean or the previous entry. When operating in cache through
   * configuration (which means {@link CacheWriter}
   * {@link CacheLoader} is registered) a boolean
   * could mean two different things: the value was present in the cache or
   * the value was present in the system of authority. The purpose of this
   * interface is a reduced set of methods that cannot be misinterpreted.
   *
   * @see Cache#remove
   * @param key key which mapping is to be removed from the cache, not null
   */
  void remove(K key);

  /**
   * Remove mappings from the cache.
   *
   * @see Cache#removeAll
   * @param keys keys is to be removed from the cache
   */
  void removeAll(Iterable<? extends K> keys);

}
