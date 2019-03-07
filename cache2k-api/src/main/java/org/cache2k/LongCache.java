package org.cache2k;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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
 * Specialized version of {@link Cache} for long keys.
 *
 * <p>The version of cache2k 1.2 does not provide particular optimizations for long keys.
 * Future versions of cache2k may provide specialized implementations for long
 * keys that enhance the efficiency. The interface is introduced along with {@link IntCache}
 * to be able to provide optimized implementations of cache2k for {@code long} keys, without
 * another API change.
 *
 * @author Jens Wilke
 * @since 1.2
 */
public interface LongCache<V> extends Cache<Long, V>, LongKeyValueStore<V> {

  /**
   * Specialized version of {@code peek} for long keys.
   *
   * @see Cache#peek(Object)
   * @since 1.2
   */
  V peek(long key);

  /**
   * Specialized version of {@code containsKey} for long keys.
   *
   * @see Cache#containsKey(Object)
   */
  boolean containsKey(long key);

}
