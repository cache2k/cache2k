package org.cache2k;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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

/**
 * Specialized version of {@link KeyValueStore} for long keys.
 *
 * @author Jens Wilke
 * @since 1.2
 */
public interface LongKeyValueStore<V> extends AdvancedKeyValueSource<Long, V>, LongKeyValueSource<V>  {

  /**
   * Insert or update a value associated with the given key.
   *
   * @see Cache#put(Object, Object)
   * @param key key with which the specified value is associated
   * @param value value to be associated with the specified key
   * @since 1.2
   */
  void put(long key, V value);

  /**
   * Remove a value from the cache that is associated with the key.
   *
   * @see KeyValueStore#remove(Object)
   * @see Cache#remove
   * @param key key which mapping is to be removed from the cache, not null
   * @since 1.2
   */
  void remove(long key);

}
