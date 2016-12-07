package org.cache2k.integration;

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
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.expiry.ExpiryTimeValues;

/**
 * Controls how to deal with loader exceptions in a read through configuration.
 * An exception can be cached and propagated or the cache can fallback to the
 * currently cached value if a previous load attempt did complete successful.
 *
 * @author Jens Wilke
 */
public abstract class ResiliencePolicy<K, V> implements ExpiryTimeValues {

  /**
   * Called before any other call.
   *
   * @param context Additional context information
   */
  public void init(Context context) { }

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
   * @param cachedContent The entry representing the currently cached content.
   *                      It is possible that this data is already expired.
   * @return Time in millis in the future when the content should expire again. A 0 or
   *         a time before the current time means the exception will not be suppressed. A
   *         {@link ExpiryPolicy#ETERNAL} means the exception will be
   *         suppressed and the recent content will be returned eternally.
   *         If the returned time is after {@link ExceptionInformation#getLoadTime()}
   *         the exception will be suppressed for the ongoing operation.
   */
  public abstract long suppressExceptionUntil(K key,
                              ExceptionInformation exceptionInformation,
                              CacheEntry<K, V> cachedContent);

  /**
   * Called after the loader threw an exception and no previous value is available or
   * {@link #suppressExceptionUntil} returned zero.
   */
  public abstract long retryLoadAfter(K key,
                                      ExceptionInformation exceptionInformation);

  /**
   * Provides additional context information. At the moment, this interface provides the
   * relevant configuration settings.
   *
   * <p>Compatibility: This interface is not intended for implementation
   * or extension by a cache client and may get additional methods in a new minor release.
   */
  public interface Context {

    /**
     * Expiry duration after entry mutation. -1 if not specified.
     *
     * @see org.cache2k.Cache2kBuilder#expireAfterWrite
     */
    long getExpireAfterWriteMillis();

    /**
     * Maximum time exceptions should be suppressed. -1 if not specified.
     * 0 if no exception suppression is requested.
     *
     * @see org.cache2k.Cache2kBuilder#resilienceDuration
     */
    long getResilienceDurationMillis();

    /**
     * Retry after the first exceptions happened for a key. -1 if not specified.
     *
     * @see org.cache2k.Cache2kBuilder#retryInterval
     */
    long getRetryIntervalMillis();

    /**
     * Retry after the first exceptions happened for a key. -1 if not specified.
     *
     * @see org.cache2k.Cache2kBuilder#maxRetryInterval
     */
    long getMaxRetryIntervalMillis();

  }

}
