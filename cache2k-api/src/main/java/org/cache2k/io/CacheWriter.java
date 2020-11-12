package org.cache2k.io;

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
import org.cache2k.DataAwareCustomization;

/**
 * Writer for write-through configurations. Any mutation of the cache via the
 * {@link Cache}  interface, e.g.  {@link Cache#put(Object, Object)} or
 * {@link Cache#remove(Object)}  will cause a writer call.
 *
 * @author Jens Wilke
 * @since 2
 */
public interface CacheWriter<K, V> extends DataAwareCustomization<K, V> {

  /**
   * Called when the value was updated or inserted into the cache.
   *
   * <p><b>Calling cache operations:</b> It is illegal to call any
   * cache methods from this method. This may have an undesired effect
   * and can cause a deadlock.
   *
   * @param key key of the value to be written, never null.
   * @param value the value to be written, may be null if null is permitted.
   * @throws Exception if an exception occurs, the cache update will not occur and this
   *         exception will be wrapped in a {@link CacheWriterException}
   */
  void write(K key, V value) throws Exception;

  /**
   * Called when a mapping is removed from the cache. The removal was done by
   * {@link Cache#remove} or {@link Cache#removeAll()}. An expiry does not trigger a call
   * to this method.
   *
   * @param key key of the value removed from the cache, never null.
   * @throws Exception if an exception occurs, the cache update will not occur and this
   *         exception will be wrapped in a {@link CacheWriterException}
   */
  void delete(K key) throws Exception;


}
