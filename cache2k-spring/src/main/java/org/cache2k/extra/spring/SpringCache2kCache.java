package org.cache2k.extra.spring;

/*-
 * #%L
 * cache2k Spring framework support
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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
import org.cache2k.annotation.Nullable;
import org.cache2k.processor.EntryProcessingException;
import org.springframework.cache.Cache;
import org.springframework.util.Assert;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Cache wrapper for the spring cache abstraction.
 *
 * @author Jens Wilke
 */
public class SpringCache2kCache implements Cache {

  protected final org.cache2k.Cache<Object, Object> cache;

  /**
   * Create an adapter instance for the given cache2k instance.
   *
   * @param cache the cache2k cache instance to adapt
   * values for this cache
   */
  public SpringCache2kCache(org.cache2k.Cache<Object, Object> cache) {
    Assert.notNull(cache, "Cache must not be null");
    this.cache = cache;
  }

  @Override
  public String getName() {
    return cache.getName();
  }

  @Override
  public org.cache2k.Cache<Object, Object> getNativeCache() {
    return cache;
  }

  /**
   * Get value from the cache, may invoke the loader if one is specified.
   * the get() method is used by Spring if the sync parameter is not set.
   */
  @Override
  public @Nullable ValueWrapper get(Object key) {
    CacheEntry<Object, Object> entry = cache.getEntry(key);
    if (entry == null) {
      return null;
    }
    return returnWrappedValue(entry);
  }

  private ValueWrapper returnWrappedValue(CacheEntry<Object, Object> entry) {
    Object v = entry.getValue();
    return () -> v;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T get(Object key, Class<T> type) {
    Object value = cache.get(key);
    if (value != null && type != null && !type.isInstance(value)) {
      throw new IllegalStateException(
        "Cached value is not of required type [" + type.getName() + "]: " + value);
    }
    return (T) value;
  }

  /**
   * This method is called instead of {@link #get} and {@link #put} in case {@code sync=true} is
   * specified on the {@code Cachable} annotation.
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T get(Object key, Callable<T> valueLoader) {
    try {
      return (T) cache.computeIfAbsent(key, v -> {
        try {
          return valueLoader.call();
        } catch (Throwable ex) {
          throw new WrappedException(ex);
        }});
    } catch (WrappedException ex) {
      throw new ValueRetrievalException(key, valueLoader, ex.getCause());
    }
  }

  private class WrappedException extends RuntimeException {
    public WrappedException(Throwable cause) {
      super(cause);
    }
  }

  @Override
  public void put(Object key, Object value) {
    cache.put(key, value);
  }

  @Override
  public ValueWrapper putIfAbsent(Object key, Object value) {
    return cache.invoke(key, e -> {
      if (e.exists()) {
        return returnWrappedValue(e);
      }
      e.setValue(value);
      return null;
    });
  }

  /**
   * May work async.
   */
  @Override
  public void evict(Object key) {
    cache.remove(key);
  }

  /**
   * May work async.
   */
  @Override
  public void clear() {
    cache.clear();
  }

  /**
   * Returns when evicted.
   */
  @Override
  public boolean evictIfPresent(Object key) {
    return cache.containsAndRemove(key);
  }

  /**
   * Like clear, but returns when everything is cleared.
   */
  @Override
  public boolean invalidate() {
    boolean notEmpty = !cache.asMap().isEmpty();
    cache.clear();
    return notEmpty;
  }

  public boolean isLoaderPresent() {
    return false;
  }

}
