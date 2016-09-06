package org.cache2k.processor;

/*
 * #%L
 * cache2k API
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

import org.cache2k.CacheEntry;
import org.cache2k.integration.CacheLoader;

/**
 * A mutable entry is used inside the {@link EntryProcessor} to perform
 * updates and retrieve information if the entry is existing in the cache.
 *
 * <p>A mutation is only done if a method for mutation is called, e.g.
 * {@code setValue} or {@code remove}. If multiple mutate methods
 * are called only the last method will be accounted for.
 *
 * <p>One instance is only intended for usage by a single thread.
 *
 * @see EntryProcessor
 * @author Jens Wilke
 */
public interface MutableCacheEntry<K, V> extends CacheEntry<K, V> {

  /**
   * <p>Returns the value to which the cache associated the key,
   * or {@code null} if the cache contains no mapping for this key.
   * {@code null} is also returned if this entry contains an exception.
   *
   * <p>If the cache does permit null values, then a return value of
   * {@code null} does not necessarily indicate that the cache
   * contained no mapping for the key. It is also possible that the cache
   * explicitly associated the key to the value {@code null}. Use {@link #exists()}
   * to check whether an entry is existing instead of a null check.
   *
   * <p>If read through operation is enabled and the entry not yet existing
   * in the cache, the call to this method triggers a call to the cache loader.
   *
   * <p>In contrast to the main Cache interface there is no no peekValue method,
   * since the same effect can be achieved by the combination of {@link #exists()}
   * and {@link #getValue()}.
   *
   * @throws RestartException If the information is not yet available and the cache
   *                          needs to do an asynchronous operation to supply it.
   *                          After completion, the entry processor will be
   *                          executed again.
   * @see CacheLoader
   */
  @Override
  V getValue();

  /**
   * True if a mapping exists in the cache, never invokes the loader / cache source.
   *
   * <p>After this method returns true, a call to {@code getValue} will always
   * return the cached value and never invoke the loader. The potential expiry
   * of the value is only checked once and the return values of this method and
   * {@code getValue} will be consistent.
   *
   * @throws RestartException If the information is not yet available and the cache
   *                          needs to do an asynchronous operation to supply it.
   *                          After completion, the entry processor will be
   *                          executed again.
   */
  boolean exists();

  /**
   * Insert or updates the cache value assigned to this key. After calling this method
   * {@code exists} will return true and {@code getValue} will return the set value.
   *
   * <p>If a writer is registered, the {@link org.cache2k.integration.CacheWriter#write(Object, Object)}
   * is called.
   */
  void setValue(V v);

  /**
   * Removes an entry from the cache.
   *
   * <p>In case a writer is registered, {@link org.cache2k.integration.CacheWriter#delete}
   * is called. If a remove is performed on a not existing cache entry the writer
   * method will also be called.
   *
   * @see <a href="https://github.com/jsr107/jsr107tck/issues/84">JSR107 TCK issue 84</a>
   */
  void remove();

  /**
   * Insert or update the entry and sets an exception. The exception will be
   * propagated as {@link org.cache2k.integration.CacheLoaderException}.
   *
   * <p>Identical to {@code setValue} an expiry of the exception will be determined
   * according to the resilience settings. Hint: If no expiry is configured the
   * default behavior will be that the set exception expires immediately, so
   * the effect will be similar to {@code remove} in this case.
   */
  void setException(Throwable ex);

  /**
   * Set a new expiry time for the entry. If combined with {@link #setValue} the entry
   * will be updated or inserted with this expiry time, otherwise just the expiry time
   * will be updated. Special time values are defined and described at
   * {@link org.cache2k.expiry.ExpiryTimeValues}
   *
   * @param t Time in millis since epoch.
   */
  void setExpiry(long t);

}
