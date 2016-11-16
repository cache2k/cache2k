/**
 *  Copyright 2011-2013 Terracotta, Inc.
 *  Copyright 2011-2013 Oracle, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jsr107.tck.integration;

import javax.cache.integration.CacheLoader;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link CacheLoader} implementation that always returns <code>null</code>
 * values for keys requested.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 * @author Brian Oliver
 */
public class NullValueCacheLoader<K, V> implements CacheLoader<K, V> {

  /**
   * {@inheritDoc}
   */
  @Override
  public V load(K key) {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<K, V> loadAll(Iterable<? extends K> keys) {
    HashMap<K, V> map = new HashMap<K, V>();
    for (K key : keys) {
      map.put(key, null);
    }

    return map;
  }
}
