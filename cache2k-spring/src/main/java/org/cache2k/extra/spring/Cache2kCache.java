package org.cache2k.extra.spring;

/*
 * #%L
 * cache2k JCache provider
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

import org.cache2k.CacheEntry;
import org.springframework.cache.Cache;
import org.springframework.util.Assert;

import java.util.concurrent.Callable;

/**
 * Cache wrapper for the spring cache abstraction.
 *
 * @author Jens Wilke
 */
public class Cache2kCache implements Cache {

  protected final org.cache2k.Cache<Object,Object> cache;

  /**
   * Create an adapter instance for the given cache2k instance.
   *
   * @param cache the cache2k cache instance to adapt
   * values for this cache
   */
  public Cache2kCache(org.cache2k.Cache<Object,Object> cache) {
    Assert.notNull(cache, "Cache must not be null");
    this.cache = cache;
  }

  @Override
  public String getName() {
    return cache.getName();
  }

  @Override
  public org.cache2k.Cache<Object,Object> getNativeCache() {
    return cache;
  }

  @Override
  public ValueWrapper get(final Object key) {
    final CacheEntry<Object,Object> entry = cache.getEntry(key);
    if (entry == null) {
      return null;
    }
    return returnWrappedValue(entry);
  }

  private ValueWrapper returnWrappedValue(final CacheEntry<Object, Object> entry) {
    return () -> entry.getValue();
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T get(final Object key, final Class<T> type) {
    Object value = cache.get(key);
    if (value != null && type != null && !type.isInstance(value)) {
      throw new IllegalStateException("Cached value is not of required type [" + type.getName() + "]: " + value);
    }
    return (T) value;
  }

  /**
   * This method is called instead of {@link #get} and {@link #put} in case {@code sync=true} is
   * specified on the {@code Cachable} annotation.
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T get(final Object key, final Callable<T> valueLoader) {
    try {
      return (T) cache.computeIfAbsent(key, (Callable<Object>) valueLoader);
    } catch (RuntimeException ex) {
      throw new ValueRetrievalException(key, valueLoader, ex);
    }
  }

  @Override
  public void put(final Object key, final Object value) {
    cache.put(key, value);
  }

  @Override
  public ValueWrapper putIfAbsent(final Object key, final Object value) {
    return cache.invoke(key, e -> {
      if (e.exists()) {
        return returnWrappedValue(e);
      }
      e.setValue(value);
      return null;
    });
  }

  @Override
  public void evict(final Object key) {
    cache.remove(key);
  }

  @Override
  public void clear() {
    cache.clear();
  }

  public boolean isLoaderPresent() {
    return false;
  }

}
