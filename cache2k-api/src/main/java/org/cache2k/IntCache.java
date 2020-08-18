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
 * Specialized version of {@link Cache} for int keys.
 *
 * <p>Using this interface has performance benefits when int keys are used.
 * No autoboxing is needed to access the cache entries. Furthermore, memory is
 * saved, since no key objects need to be saved.
 *
 * @author Jens Wilke
 * @since 1.2
 * @deprecated will be removed in version 2.0
 */
public interface IntCache<V> extends Cache<Integer, V>, IntKeyValueStore<V> {

  /**
   * Specialized version of {@code peek} for int keys.
   *
   * @see Cache#peek(Object)
   * @since 1.2
   */
  V peek(int key);

  /**
   * Specialized version of {@code containsKey} for int keys.
   *
   * @see Cache#containsKey(Object)
   */
  boolean containsKey(int key);

}
