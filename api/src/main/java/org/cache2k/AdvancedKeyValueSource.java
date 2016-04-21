package org.cache2k;

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

import java.util.Map;

/**
 * Key/value source with bulk get and prefetching.
 *
 * @author Jens Wilke
 */
public interface AdvancedKeyValueSource<K,V> extends KeyValueSource<K,V> {

  /**
   * Retrieves all objects via the key.
   */
  Map<K, V> getAll(Iterable<? extends K> keys);

  /**
   * Notify about the intend to retrieve the value for this key in the
   * near future.
   **
   * @see Cache#prefetch(Object)
   */
  void prefetch(K key);

  /**
   * Notify about the intend to retrieve the value for the keys in the
   * near future.
   *
   * @see Cache#prefetchAll(Iterable)
   */
  void prefetchAll(Iterable<? extends K> keys);

}
