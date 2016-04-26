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

/**
 * Controls how to deal with loader exceptions in a read through configuration.
 * An exception can be cached and propagated or the cache can fallback to the
 * currently cached value if a previous load attempt did complete successful.
 *
 * @author Jens Wilke
 */
public interface ResiliencePolicy<K, V> {

  void open(Context context);

  /**
   * Called after the loader threw an exception and a previous value is available.
   * Determines from the given parameters whether the exception should be suppressed and
   * the cache should return the last cached content.
   *
   * <p>The returned value is a point in time. If the currently cached
   * content is expired, returning a future time will effectively extend the entry
   * expiry.
   *
   * @param cachedContent The entry representing the currently cached content.
   *                      It is possible that this data is already expired.
   * @return Time in millis in the future when the content should expire again or 0 if the
   *         exception should not be suppressed.
   */
  long suppressExceptionUntil(K key,
                              LoadExceptionInformation exceptionInformation,
                              CacheEntry<K, V> cachedContent);

  /**
   * Called after the loader threw an exception and no previous value is available or
   * {@link #suppressExceptionUntil} returned null.
   */
  long cacheExceptionUntil(K key,
                           LoadExceptionInformation exceptionInformation);

  interface Context {

    long getExpireAfterWriteMillis();

    /**
     *
     *
     * @return duration in milliseconds
     */
    long getSuppressDurationMillis();

    long getRetryIntervalMillis();

    long getMaxRetryIntervalMillis();

  }

}
