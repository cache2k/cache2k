package org.cache2k.processor;

/*-
 * #%L
 * cache2k API
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.annotation.Nullable;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.io.CacheLoader;
import org.cache2k.io.CacheLoaderException;
import org.cache2k.io.LoadExceptionInfo;
import org.cache2k.io.AdvancedCacheLoader;
import org.cache2k.io.CacheWriter;
import org.cache2k.io.ResiliencePolicy;

import java.time.Duration;

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
 * <p>Planed extensions: getExpiryTime, peekValue/peekException....
 *
 * @see EntryProcessor
 * @author Jens Wilke
 */
public interface MutableCacheEntry<K, V> extends CacheEntry<K, V> {

  /**
   * <p>Returns the value to which the cache associated the key,
   * or {@code null} if the cache contains no mapping for this key.
   * If no mapping exists and a loader is specified, the cache loader
   * is called.
   *
   * <p>In contrast to the main cache interface there is no peekValue method,
   * since the same effect can be achieved by the combination of {@link #exists()}
   * and {@link #getValue()}.
   *
   * @throws CacheLoaderException if loading produced an exception
   * @throws RestartException If the information is not yet available and the cache
   *                          needs to do an operation to supply it. After completion,
   *                          the entry processor will be executed again.
   * @return The cached value, the loaded value or {@code null} otherwise
   * @see CacheLoader
   */
  @Override
  @SuppressWarnings({"NullAway", "nullness"})
  @Nullable V getValue();

  /**
   * {@inheritDoc}
   *
   * <p>If a loader is present and the entry is not yet loaded or expired, a
   * load is triggered.
   *
   * @throws RestartException If the information is not yet available and the cache
   *                          needs to do an operation to supply it. After completion,
   *                          the entry processor will be executed again.
   */
  @Override
  @Nullable Throwable getException();

  /**
   * {@inheritDoc}
   *
   * <p>If a loader is present and the entry is not yet loaded or expired, a
   * load is triggered.
   *
   * @throws RestartException If the information is not yet available and the cache
   *                          needs to do an operation to supply it. After completion,
   *                          the entry processor will be executed again.
   */
  @Override
  @Nullable LoadExceptionInfo<K, V> getExceptionInfo();

  /**
   * {@code True} if a mapping exists in the cache, never invokes the loader.
   *
   * <p>After this method returns {@code true}, a call to {@code getValue} will always
   * return the cached value and never invoke the loader. The potential expiry
   * of the value is only checked once and the return values of this method and
   * {@code getValue} will be consistent.
   *
   * @throws RestartException If the information is not yet available and the cache
   *                          needs to do an operation to supply it. After completion,
   *                          the entry processor will be executed again.
   */
  boolean exists();

  /**
   * Time the operation started.
   *
   * <p>The time is retrieved once when the entry processor is invoked and will not change afterwards.
   * If a load is triggered this value will be identical to the {@code startTime} in
   * {@link AdvancedCacheLoader#load},
   * {@link LoadExceptionInfo#getLoadTime()} or {
   * @link AsyncCacheLoader.Context#getLoadStartTime()}
   *
   * @return Time in millis since epoch or as defined by
   *         {@link org.cache2k.operation.TimeReference}.
   */
  long getStartTime();

  /**
   * Locks the entry for mutation. Code following this method will be
   * executed once and atomically w.r.t. other operations changing an entry
   * with the same key.
   *
   * @throws RestartException If the information is not yet available and the cache
   *                          needs to do an operation to supply it. After completion,
   *                          the entry processor will be executed again.
   * @throws UnsupportedOperationException In case locking is not supported by this
   *                                       cache configuration.
   *
   */
  MutableCacheEntry<K, V> lock();

  /**
   * Insert or updates the cache value assigned to this key.
   *
   * <p>After calling this method the values of {@code exists} and {@code getValue}
   * will not change. This behavior is different from JSR107/JCache. This definition
   * reduces complexity and is more useful than the JSR107 behavior.
   *
   * <p>If a writer is registered, the
   * {@link CacheWriter#write(Object, Object)} is called.
   */
  MutableCacheEntry<K, V> setValue(V v);

  /**
   * Calls the loader unconditionally in this operation. Multiple calls have no effect.
   * After the load, the loaded value can be retrieved with {@link #getValue()}.
   *
   * @throws IllegalStateException if {@link #getValue()} was called before
   * @throws UnsupportedOperationException if no loader is defined
   * @throws RestartException If the information is not yet available and the cache
   *                          needs to do an operation to supply it. After completion,
   *                          the entry processor will be executed again.
   */
  MutableCacheEntry<K, V> load();

  /**
   * Removes an entry from the cache.
   *
   * <p>In case a writer is registered, {@link CacheWriter#delete}
   * is called. If a remove is performed on a not existing cache entry the writer
   * method will also be called.
   *
   * <p>After calling this method the values of {@code exists} and {@code getValue}
   * will not change. This behavior is different from JSR107/JCache. This definition
   * reduces complexity and is more useful than the JSR107 behavior.
   *
   * @see <a href="https://github.com/jsr107/jsr107tck/issues/84">JSR107 TCK issue 84</a>
   */
  MutableCacheEntry<K, V> remove();

  /**
   * Insert or update the entry and sets an exception. The exception will be
   * propagated as {@link CacheLoaderException}.
   *
   * <p>The effect depends on expiry and resilience setting. An exception
   * will be kept in the cache only if there is an expiry configured or
   * the resilience policy is allowing that.
   *
   * @throws RestartException If the information is not yet available and the cache
   *                          needs to do an operation to supply it. After completion,
   *                          the entry processor will be executed again.
   * @see ResiliencePolicy
   */
  MutableCacheEntry<K, V> setException(Throwable ex);

  /**
   * Set a new expiry time for the entry. If combined with {@link #setValue} the entry
   * will be updated or inserted with this expiry time, otherwise just the expiry time
   * will be updated. Using this method on a not existent entry, will not have any effect.
   *
   * <p>Special time values are defined and described at {@link org.cache2k.expiry.ExpiryTimeValues}
   *
   * <p>The effective time will be capped based on the setting of
   * {@link Cache2kBuilder#expireAfterWrite(Duration)}
   *
   * @param t millis since epoch or as defined by {@link org.cache2k.operation.TimeReference}.
   *          Some values have special meanings, see {@link ExpiryTimeValues}
   * @throws RestartException If the information is not yet available and the cache
   *                          needs to do an operation to supply it. After completion,
   *                          the entry processor will be executed again.
   * @throws IllegalArgumentException if expiry was disabled via
   *                                  {@link org.cache2k.Cache2kBuilder#eternal(boolean)}
   */
  MutableCacheEntry<K, V> setExpiryTime(long t);

  /**
   * Returns the effective expiry time of the current cache entry in case {@link #exists()}
   * is false it returns{@link org.cache2k.expiry.ExpiryTimeValues#NOW}. If the entry
   * was loaded, it returns the calculated expiry time from {@link org.cache2k.expiry.ExpiryPolicy}
   * or {@link ResiliencePolicy}. The value is always positive.
   *
   * @see org.cache2k.expiry.ExpiryPolicy
   */
  long getExpiryTime();

  /**
   * Time of the last update of the cached value. This is the start time
   * (before the loader was called) of a successful load operation, or the time
   * the value was modified directly via {@link org.cache2k.Cache#put} or other sorts
   * of mutation. Does not trigger a load.
   *
   * @throws RestartException If the information is not yet available and the cache
   *                          needs to do an operation to supply it. After completion,
   *                          the entry processor will be executed again.
   * @return Time in millis since epoch or as defined by
   *         {@link org.cache2k.operation.TimeReference}.
   */
  long getModificationTime();

  /**
   * If {@link #setValue(Object)} is used, this sets an alternative time for
   * expiry calculations. The entry refreshed time is not updated, if the entry is
   * not mutated.
   *
   * <p>If refresh ahead is enabled via {@link org.cache2k.Cache2kBuilder#refreshAhead(boolean)},
   * the next refresh time is controlled by the expiry time.
   *
   * @param t millis since epoch or as defined by {@link org.cache2k.operation.TimeReference}
   * @throws RestartException If the information is not yet available and the cache
   *                          needs to do an operation to supply it. After completion,
   *                          the entry processor will be executed again.
   */
  MutableCacheEntry<K, V> setModificationTime(long t);

}
