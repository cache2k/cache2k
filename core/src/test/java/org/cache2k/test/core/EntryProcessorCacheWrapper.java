package org.cache2k.test.core;

/*
 * #%L
 * cache2k core
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

import org.cache2k.Cache;
import org.cache2k.processor.CacheEntryProcessor;
import org.cache2k.processor.MutableCacheEntry;
import org.cache2k.core.extra.CacheWrapper;

/**
 * Override operations mutating or querying the cache by using the entry processor.
 * This way we have a large test base for the entry processor, and we can verify that
 * every possible cache operation can be expressed via an entry processor invocation.
 * This class keeps the basic operations like peek, get, contains and put, since these
 * will be used by the tests to set initial cache state and assert the correct behavior.
 */
public class EntryProcessorCacheWrapper<K, V> extends CacheWrapper<K, V> {

  public EntryProcessorCacheWrapper(Cache<K, V> cache) {
    super(cache);
  }

  /**
   * Not replaces by entry processor invocation.
   */
  @Override
  public V get(K key) {
    return super.get(key);
  }

  /**
   * Not replaces by entry processor invocation.
   */
  @Override
  public V peek(K key) {
    return super.peek(key);
  }

  @Override
  public boolean contains(K key) {
    CacheEntryProcessor<K, V, Boolean> p = new CacheEntryProcessor<K, V, Boolean>() {
      @Override
      public Boolean process(MutableCacheEntry<K, V> entry, Object... arguments) throws Exception {
        if (!entry.exists()) {
          return false;
        }
        return true;
      }
    };
    return invoke(key, p);
  }

  /**
   * Not replaces by entry processor invocation.
   */
  @Override
  public void put(K key, final V value) {
    CacheEntryProcessor<K, V, Void> p = new CacheEntryProcessor<K, V, Void>() {
      @Override
      public Void process(MutableCacheEntry<K, V> entry, Object... arguments) throws Exception {
        entry.setValue(value);
        return null;
      }
    };
    invoke(key, p);
  }

  @Override
  public boolean replace(final K key, final V _newValue) {
    CacheEntryProcessor<K, V, Boolean> p = new CacheEntryProcessor<K, V, Boolean>() {
      @Override
      public Boolean process(MutableCacheEntry<K, V> entry, Object... arguments) throws Exception {
        if (!entry.exists()) {
          return false;
        }
        entry.setValue(_newValue);
        return true;
      }
    };
    return invoke(key, p);
  }

  @Override
  public boolean replaceIfEquals(final K key, final V _oldValue, final V _newValue) {
    CacheEntryProcessor<K, V, Boolean> p = new CacheEntryProcessor<K, V, Boolean>() {
      @Override
      public Boolean process(MutableCacheEntry<K, V> entry, Object... arguments) throws Exception {
        if (!entry.exists()) {
          return false;
        }
        if (_oldValue == null) {
          if (null != entry.getValue()) {
            return false;
          }
        } else {
          if (!_oldValue.equals(entry.getValue())) {
            return false;
          }
        }
        entry.setValue(_newValue);
        return true;
      }
    };
    return invoke(key, p);
  }

}
