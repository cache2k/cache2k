package org.cache2k.core;

/*
 * #%L
 * cache2k core implementation
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

import org.cache2k.IntCache;

/**
 * Just delegate to the non specialized versions for now.
 *
 * @author Jens Wilke
 */
public class IntWiredCache<V> extends WiredCache<Integer, V> implements IntCache<V> {

  @Override
  public V peek(final int key) {
    return super.peek(key);
  }

  @Override
  public boolean containsKey(final int key) {
    return super.containsKey(key);
  }

  @Override
  public void put(final int key, final V value) {
    super.put(key, value);
  }

  @Override
  public V get(final int key) {
    return super.get(key);
  }

  @Override
  public void remove(final int key) {
    super.remove(key);
  }

}
