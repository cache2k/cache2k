package org.cache2k.integration;

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

/**
 * @author Jens Wilke
 * @deprecated Replaced with {@link org.cache2k.io.CacheWriter}
 */
@Deprecated
public abstract class CacheWriter<K, V> implements org.cache2k.io.CacheWriter<K, V> {

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


}
