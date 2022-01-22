package org.cache2k.addon;

/*-
 * #%L
 * cache2k addon
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
import org.cache2k.io.LoadExceptionInfo;
import org.cache2k.io.ResiliencePolicy;

/**
 * Resilience policy which implements an exponential back off of the retry intervals.
 *
 * @author Jens Wilke
 * @see <a href="https://cache2k.org/docs/latest/user-guide.html#resilience">
 *      cache2k user guide - Exceptions and Resilience</a>
 */
public class UniversalResiliencePolicy<K, V> implements ResiliencePolicy<K, V> {

  @SuppressWarnings("rawtypes")
  public static final UniversalResilienceSupplier SUPPLIER = new UniversalResilienceSupplier();
  @SuppressWarnings("unchecked")
  public static <K, V> UniversalResilienceSupplier<K, V> supplier() { return SUPPLIER; }
  public static <K, V> UniversalResilienceSupplier<K, V> enable(Cache2kBuilder<K, V> builder) {
    builder.config().setResiliencePolicy(supplier());
    return supplier();
  }

  private final double multiplier;
  private final long resilienceDuration;
  private final long maxRetryInterval;
  private final long retryInterval;

  /**
   * Construct universal resilience policy. Duration and interval values are in milliseconds.
   *
   * @param resilienceDuration maximum duration in which exceptions are suppressed if a previous
   *                           load was successful
   * @param retryInterval start value of retry interval
   */
  public UniversalResiliencePolicy(long resilienceDuration, long retryInterval) {
    this(resilienceDuration, retryInterval, retryInterval, 1.0);
  }

  /**
   * Construct universal resilience policy. Duration and interval values are in milliseconds.
   *
   * @param resilienceDuration maximum duration in which exceptions are suppressed if a previous
   *                           load was successful
   * @param maxRetryInterval maximum value for retryInterval
   * @param retryInterval start value of retry interval
   * @param multiplier multiplier for retry interval in case of consecutive load exceptions
   */
  public UniversalResiliencePolicy(long resilienceDuration, long maxRetryInterval,
                                   long retryInterval, double multiplier) {
    if (multiplier < 1.0) {
      throw new IllegalArgumentException("multiplier needs to be greater than 1.0");
    }
    this.multiplier = multiplier;
    this.resilienceDuration = resilienceDuration;
    this.maxRetryInterval = maxRetryInterval;
    this.retryInterval = retryInterval;
  }

  /**
   * Allows exceptions to be suppressed for a maximum of resilienceDuration starting from
   * last successful load. Returns a shorter time based on the retry configuration
   * with exponential backoff.
   */
  @Override
  public long suppressExceptionUntil(K key,
                                     LoadExceptionInfo<K, V> loadExceptionInfo,
                                     CacheEntry<K, V> cachedEntry) {
    if (resilienceDuration == 0 || resilienceDuration == Long.MAX_VALUE) {
      return resilienceDuration;
    }
    long maxSuppressUntil = loadExceptionInfo.getSinceTime() + resilienceDuration;
    long deltaMs = calculateRetryDelta(loadExceptionInfo);
    return Math.min(loadExceptionInfo.getLoadTime() + deltaMs, maxSuppressUntil);
  }

  /**
   * Retries after the load time based on the retry configuration with exponential backoff.
   */
  @Override
  public long retryLoadAfter(K key, LoadExceptionInfo<K, V> loadExceptionInfo) {
    if (retryInterval == 0 || retryInterval == Long.MAX_VALUE) {
      return retryInterval;
    }
    return loadExceptionInfo.getLoadTime() + calculateRetryDelta(loadExceptionInfo);
  }

  private long calculateRetryDelta(LoadExceptionInfo<K, V> loadExceptionInfo) {
    long delta = (long)
      (retryInterval * Math.pow(multiplier, loadExceptionInfo.getRetryCount()));
    return Math.min(delta, maxRetryInterval);
  }

  public double getMultiplier() {
    return multiplier;
  }

  public long getResilienceDuration() { return resilienceDuration; }

  public long getMaxRetryInterval() { return maxRetryInterval; }

  public long getRetryInterval() { return retryInterval; }

}
