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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link CacheLoader} implementation that:
 * <ol>
 * <li>returns the key as the value for each key load request</li>
 * <li>throws a {@link NullPointerException} when an attempt to load a
 * <code>null</code> key is attempted</li>
 * </ol>
 *
 * @param <K> the type of keys (and values)
 * @author Brian Oliver
 */
public class RecordingCacheLoader<K> implements CacheLoader<K, K>, AutoCloseable {

  /**
   * The keys that have been loaded by this loader.
   */
  private ConcurrentHashMap<K, K> loaded = new ConcurrentHashMap<K, K>();

  /**
   * The number of loads that have occurred.
   */
  private AtomicInteger loadCount = new AtomicInteger(0);

  /**
   * {@inheritDoc}
   */
  @Override
  public K load(final K key) {
    if (key == null) {
      throw new NullPointerException("Attempted to load a null key!");
    } else {
      loaded.put(key, key);
      loadCount.incrementAndGet();

      return key;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<K, K> loadAll(Iterable<? extends K> keys) {
    Map<K, K> map = new HashMap<K, K>();
    for (K key : keys) {
      if (key == null) {
        throw new NullPointerException("Attempted to load a null key!");
      } else {
        map.put(key, key);
      }
    }

    loaded.putAll(map);
    loadCount.addAndGet(map.size());

    return map;
  }

  /**
   * Obtain the number of entries that have been loaded.
   *
   * @return the number of entries loaded thus far
   */
  public int getLoadCount() {
    return loadCount.get();
  }

  /**
   * Determines if the specified key has been loaded by this loader.
   *
   * @param key the key
   * @return true if the key has been loaded, false otherwise
   */
  public boolean hasLoaded(K key) {
    return key == null ? false : loaded.containsKey(key);
  }

  @Override
  public void close() throws Exception {
    // added for code coverage.
  }
}
