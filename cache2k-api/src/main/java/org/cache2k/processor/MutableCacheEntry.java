package org.cache2k.processor;

/*
 * #%L
 * cache2k API
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
import org.cache2k.integration.CacheLoader;

/**
 * A mutable entry is used inside the {@link EntryProcessor} to perform
 * updates and retrieve information from a cache entry.
 *
 * <p>A mutation is only done if a method for mutation is called, e.g.
 * {@code setValue} or {@code remove}. If multiple mutate methods
 * are called in sequence only the last method will have an effect.
 *
 * <p>One instance is only usable by a single thread and for
 * one call of {@link EntryProcessor#process(MutableCacheEntry)}.
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
   * <p>If the cache does permit {@code null} values, then a return value of
   * {@code null} does not necessarily indicate that the cache
   * contained no mapping for the key. It is also possible that the cache
   * explicitly associated the key to the value {@code null}. Use {@link #exists()}
   * to check whether an entry is existing instead of a null check.
   *
   * <p>If read through operation is enabled and the entry is not yet existing
   * in the cache, the call to this method triggers a call to the cache loader.
   *
   * <p>In contrast to the main cache interface there is no no peekValue method,
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
   * The current value in the cache. Identical to {@link #getValue()}, but not modified
   * after a mutation method is called. Intended for fluent operation.
   *
   * <p>If read through operation is enabled and the entry is not yet existing
   * in the cache, the call to this method triggers a call to the cache loader.
   *
   * @throws RestartException If the information is not yet available and the cache
   *                          needs to do an asynchronous operation to supply it.
   *                          After completion, the entry processor will be
   *                          executed again.
   */
  V getOldValue();

  /**
   * True if a mapping exists in the cache, never invokes the loader / cache source.
   * Identical to {@link #exists()}, but not modified
   * after a mutation method is called. Intended for fluent operation.
   *
   * @throws RestartException If the information is not yet available and the cache
   *                          needs to do an asynchronous operation to supply it.
   *                          After completion, the entry processor will be
   *                          executed again.
   */
  boolean wasExisting();

  /**
   * Insert or updates the cache value assigned to this key. After calling this method
   * {@code exists} will return true and {@code getValue} will return the set value.
   *
   * <p>If a writer is registered, the {@link org.cache2k.integration.CacheWriter#write(Object, Object)}
   * is called.
   */
  MutableCacheEntry<K,V> setValue(V v);

  /**
   * Removes an entry from the cache.
   *
   * <p>In case a writer is registered, {@link org.cache2k.integration.CacheWriter#delete}
   * is called. If a remove is performed on a not existing cache entry the writer
   * method will also be called.
   *
   * @see <a href="https://github.com/jsr107/jsr107tck/issues/84">JSR107 TCK issue 84</a>
   */
  MutableCacheEntry<K,V> remove();

  /**
   * Insert or update the entry and sets an exception. The exception will be
   * propagated as {@link org.cache2k.integration.CacheLoaderException}.
   *
   * <p>Identical to {@code setValue} an expiry of the exception will be determined
   * according to the resilience settings. Hint: If no expiry is configured the
   * default behavior will be that the set exception expires immediately, so
   * the effect will be similar to {@code remove} in this case.
   */
  MutableCacheEntry<K,V> setException(Throwable ex);

  /**
   * Set a new expiry time for the entry. If combined with {@link #setValue} the entry
   * will be updated or inserted with this expiry time, otherwise just the expiry time
   * will be updated.
   *
   * <p>Special time values are defined and described at {@link org.cache2k.expiry.ExpiryTimeValues}
   *
   * @param t Time in millis since epoch.
   */
  MutableCacheEntry<K,V> setExpiry(long t);

  /**
   * Timestamp of the last modification of the cached value. This is the start time
   * (before the loader was called) of a successful load operation, or the time
   * the value was modified directly via {@link org.cache2k.Cache#put} or other sorts
   * of mutation.
   *
   * <p>For optimizations it is possible to disable time recording. In this case a
   * {@code 0} is returned.
   */
  @SuppressWarnings("deprecation")
  long getLastModification();

}
