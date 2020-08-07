package org.cache2k.test.stress;

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

import org.cache2k.Cache;
import org.cache2k.pinpoint.stress.pairwise.ActorPair;

/**
 * For actors on a cache key
 *
 * @author Jens Wilke
 */
public abstract class CacheKeyActorPair<R, K, V> implements ActorPair<R>, Cloneable {

  protected Cache<K, V> cache;
  protected K key;

  public CacheKeyActorPair<R, K, V> setCache(final Cache<K, V> cache) {
    this.cache = cache;
    return this;
  }

  public CacheKeyActorPair<R, K, V> setKey(final K key) {
    this.key = key;
    return this;
  }

  public V value() {
    return cache.peek(key);
  }

  @Override
  protected Object clone() {
    try {
      return super.clone();
    } catch (CloneNotSupportedException neverHappens) {
      throw new UnsupportedOperationException();
    }
  }

}
