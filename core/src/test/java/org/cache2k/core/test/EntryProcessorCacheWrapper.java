package org.cache2k.core.test;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import org.cache2k.Cache;
import org.cache2k.processor.CacheEntryProcessor;
import org.cache2k.processor.MutableCacheEntry;
import org.cache2k.extra.CacheWrapper;

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
