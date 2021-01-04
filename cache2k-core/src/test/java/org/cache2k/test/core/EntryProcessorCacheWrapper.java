package org.cache2k.test.core;

/*
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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
import org.cache2k.ForwardingCache;
import org.cache2k.annotation.Nullable;
import org.cache2k.processor.EntryProcessor;
import org.cache2k.processor.MutableCacheEntry;

/**
 * Override operations mutating or querying the cache by using the entry processor.
 * This way we have a large test base for the entry processor, and we can verify that
 * every possible cache operation can be expressed via an entry processor invocation.
 *
 * @author Jens Wilke
 */
public class EntryProcessorCacheWrapper<K, V> extends ForwardingCache<K, V> {

  private final Cache<K, V> cache;

  public EntryProcessorCacheWrapper(Cache<K, V> cache) {
    this.cache = cache;
  }

  @Override
  protected Cache<K, V> delegate() {
    return cache;
  }

  /**
   * Not replaced by entry processor invocation.
   */
  @Override
  public V get(K key) {
    return super.get(key);
  }

  /**
   * Not replaced by entry processor invocation.
   */
  @Override
  public @Nullable V peek(K key) {
    EntryProcessor<K, V, V> p = e -> {
      if (!e.exists()) {
        return null;
      }
      return e.getValue();
    };
    return invoke(key, p);
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public boolean containsKey(K key) {
    EntryProcessor<K, V, Boolean> p = MutableCacheEntry::exists;
    return invoke(key, p);
  }

  /**
   * Not replaces by entry processor invocation.
   */
  @Override
  public void put(K key, V value) {
    EntryProcessor<K, V, Void> p = e -> {
      e.setValue(value);
      return null;
    };
    invoke(key, p);
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public boolean replace(K key, V newValue) {
    EntryProcessor<K, V, Boolean> p = e -> {
      if (!e.exists()) {
        return false;
      }
      e.setValue(newValue);
      return true;
    };
    return invoke(key, p);
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public boolean replaceIfEquals(K key, V oldValue, V newValue) {
    EntryProcessor<K, V, Boolean> p = e -> {
      if (!e.exists()) {
        return false;
      }
      if (oldValue == null) {
        if (null != e.getValue()) {
          return false;
        }
      } else {
        if (!oldValue.equals(e.getValue())) {
          return false;
        }
      }
      e.setValue(newValue);
      return true;
    };
    return invoke(key, p);
  }

}
