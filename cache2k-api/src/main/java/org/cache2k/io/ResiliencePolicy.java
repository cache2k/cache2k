package org.cache2k.io;

/*
 * #%L
 * cache2k API
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.Customization;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.expiry.ExpiryTimeValues;

/**
 * Controls how to deal with loader exceptions in a read through configuration.
 * An exception can be cached and propagated or the cache can fallback to the
 * currently cached value if a previous load attempt did complete successful.
 *
 * @author Jens Wilke
 * @since 2
 */
public interface ResiliencePolicy<K, V> extends ExpiryTimeValues, Customization<K, V> {

  /**
   * A policy always returning zero, thus disabling resilience features.
   */
  ResiliencePolicy<?, ?> DISABLED_POLICY = new ResiliencePolicy<Object, Object>() {
    @Override
    public long suppressExceptionUntil(Object key, LoadExceptionInfo<Object> loadExceptionInfo,
                                       CacheEntry<Object, Object> cachedEntry) {
      return NOW;
    }

    @Override
    public long retryLoadAfter(Object key, LoadExceptionInfo<Object> loadExceptionInfo) {
      return NOW;
    }
  };
  static <K, V> ResiliencePolicy<K, V> disabledPolicy() {
    return (ResiliencePolicy<K, V>) DISABLED_POLICY;
  }

  static <K, V> void disable(Cache2kBuilder<K, V> b) {
    b.config().setResiliencePolicy(null);
  }

  /**
   * Called after the loader threw an exception and a previous value is available.
   * Determines from the given parameters whether the exception should be suppressed and
   * the cache should return the last cached content.
   *
   * <p>The returned value is a point in time. If the currently cached
   * content is expired, returning a future time will effectively extend the entry
   * expiry.
   *
   * <p>Returning 0 or a past time means the exception should not be suppressed.
   * The cache will call immediately {@link #retryLoadAfter} to determine how long the
   * exception should stay in the cache and when the next retry attempt takes place.
   *
   * <p>If the exception is not suppressed, it will wrapped into a {@link CacheLoaderException}
   * and propagated to the cache client. This is customizable by the {@link ExceptionPropagator}.
   *
   * @param cachedEntry The entry representing the currently cached content.
   *                    It is possible that this data is already expired.
   *                    Never contains an exception.
   * @return Time in millis in the future when the content should expire again. A zero or
   *         a time before the current time means the exception will not be suppressed. A
   *         {@link ExpiryPolicy#ETERNAL} means the exception will be
   *         suppressed and the recent content will be returned eternally.
   *         If the returned time is after {@link LoadExceptionInfo#getLoadTime()}
   *         the exception will be suppressed for the ongoing operation.
   */
  long suppressExceptionUntil(K key,
                              LoadExceptionInfo<K> loadExceptionInfo,
                              CacheEntry<K, V> cachedEntry);

  /**
   * Called after the loader threw an exception and no previous value is available or
   * {@link #suppressExceptionUntil} returned zero.
   */
  long retryLoadAfter(K key, LoadExceptionInfo<K> loadExceptionInfo);

}
