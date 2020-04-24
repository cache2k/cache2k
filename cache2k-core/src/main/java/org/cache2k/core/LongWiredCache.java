package org.cache2k.core;

/*
 * #%L
 * cache2k implementation
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

import org.cache2k.LongCache;

/**
 * Just delegate to the non specialized versions for now.
 *
 * @author Jens Wilke
 */
public class LongWiredCache<V> extends WiredCache<Long, V> implements LongCache<V> {

  @Override
  public V peek(final long key) {
    return super.peek(key);
  }

  @Override
  public boolean containsKey(final long key) {
    return super.containsKey(key);
  }

  @Override
  public void put(final long key, final V value) {
    super.put(key, value);
  }

  @Override
  public V get(final long key) {
    return super.get(key);
  }

  @Override
  public void remove(final long key) {
    super.remove(key);
  }

}
