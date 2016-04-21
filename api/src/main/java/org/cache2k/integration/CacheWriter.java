package org.cache2k.integration;

/*
 * #%L
 * cache2k API only package
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

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Writer for write-through configurations. Any mutation of the cache via the
 * {@link Cache}  interface, e.g.  {@link Cache#put(Object, Object)} or
 * {@link Cache#remove(Object)}  will cause a writer call.
 *
 * @author Jens Wilke
 */
public abstract class CacheWriter<K, V> {

  /**
   * Called when the value was updated or inserted into the cache.
   *
   * @param key key of the value to be written, never null.
   * @param value the value to be written, may be null if null is permitted.
   * @throws Exception if an exception occurs, the cache update will not occur and this
   *         exception will be wrapped in a {@link CacheWriterException}
   */
  public abstract void write(K key, V value) throws Exception;

  /**
   * Called when a mapping is removed from the cache. The removal was done by
   * {@link Cache#remove} or {@link Cache#removeAll()}. An expiry does not trigger a call
   * to this method.
   *
   * @param key key of the value removed from the cache, never null.
   * @throws Exception if an exception occurs, the cache update will not occur and this
   *         exception will be wrapped in a {@link CacheWriterException}
   */
  public abstract void delete(K key) throws Exception;

  /**
   * Write all values in the map.
   *
   * <p>Optional method, by default throws an {@link UnsupportedOperationException}.
   *
   * <p>The method is currently (1.0) not used by cache2k and are provided to future
   * proof the interface.
   *
   * <p>The method is provided to complete the API. At the moment cache2k is not
   * using it. Please see the road map.
   *
   * @param values The map of key and values to be written, never null.
   * @throws Exception if an exception occurs, the cache update will not occur and this
   *         exception will be wrapped in a {@link CacheWriterException}
   */
  public void writeAll(Map<? extends K, ? extends V> values, Executor executor) throws Exception {
    throw new UnsupportedOperationException();
  }

  /**
   * Called when a set of mappings is removed from the cache. The removal was done by
   * {@link Cache#remove} or {@link Cache#removeAll()}. An expiry does not trigger a call
   * to this method.
   *
   * <p>Optional method, by default throws an {@link UnsupportedOperationException}.
   *
   * <p>The method is provided to complete the API. At the moment cache2k is not
   * using it. Please see the road map.
   *
   * @param keys set of keys for the values to be deleted.
   * @throws Exception if an exception occurs, the cache update will not occur and this
   *         exception will be wrapped in a {@link CacheWriterException}
   */
  public void deleteAll(Iterable<? extends K> keys, Executor executor) throws Exception {
    throw new UnsupportedOperationException();
  }

}
