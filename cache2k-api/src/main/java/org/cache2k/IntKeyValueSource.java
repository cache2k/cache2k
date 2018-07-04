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
 * Specialized version of {@link KeyValueSource} for int keys.
 *
 * @author Jens Wilke
 * @see IntCache
 * @since 1.2
 */
public interface IntKeyValueSource<V> extends KeyValueSource<Integer, V> {

  /**
   * Returns a value associated with this key.
   *
   * @see Cache#get(Object)
   * @since 1.2
   */
  V get(int key);

}
