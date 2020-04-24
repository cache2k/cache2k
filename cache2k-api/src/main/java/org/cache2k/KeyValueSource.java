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

/**
 * Reduced interface to return a value selected by a key object. Cache users
 * of a read-through cache may choose this simple interface for requesting data
 * only, rather to use the full blown cache interface.
 *
 * @author Jens Wilke
 */
public interface KeyValueSource<K, V> {

  /**
   * Returns a value associated with this key.
   *
   * @see Cache#get(Object)
   */
  V get(K key);

}
