package org.cache2k.core;

/*
 * #%L
 * cache2k core
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
import org.cache2k.integration.LoadExceptionInformation;
import org.cache2k.integration.ResiliencePolicy;

import java.util.Random;

/**
 * @author Jens Wilke
 */
public class DefaultResiliencePolicy<K,V> extends ResiliencePolicy<K,V> {

  Context context;
  Random random;
  double multiplier = 1.5;
  double randomization = 0.5;
  long resilienceDuration;
  long maxRetryInterval;
  long retryInterval;

  public double getMultiplier() {
    return multiplier;
  }

  public void setMultiplier(final double _multiplier) {
    multiplier = _multiplier;
  }

  public double getRandomization() {
    return randomization;
  }

  public void setRandomization(final double _randomization) {
    randomization = _randomization;
  }

  public long getResilienceDuration() {
    return resilienceDuration;
  }

  public long getMaxRetryInterval() {
    return maxRetryInterval;
  }

  @Override
  public void init(final Context ctx) {
    context = ctx;
    resilienceDuration = context.getResilienceDurationMillis();
    if (resilienceDuration == -1) {
      resilienceDuration = Math.max(
        context.getExpireAfterWriteMillis(),
        context.getRetryIntervalMillis());
    }
    maxRetryInterval = context.getMaxRetryIntervalMillis();
    if (maxRetryInterval == -1) {
      maxRetryInterval = resilienceDuration;
    }
    retryInterval = context.getRetryIntervalMillis();
    if (retryInterval == -1) {
      retryInterval = context.getExpireAfterWriteMillis() * 100 / 5;
      retryInterval = Math.max(retryInterval, resilienceDuration);
    }
    random = HeapCache.SEED_RANDOM;
  }

  /**
   * No timer needed if the settings enable immediate expiry or never expire.
   */
  public boolean isNoTimerNeeded() {
    return
        (resilienceDuration == 0 || resilienceDuration == Long.MAX_VALUE) &&
        (retryInterval == 0 || retryInterval == Long.MAX_VALUE);
  }

  @Override
  public long suppressExceptionUntil(final Object key,
                                     final LoadExceptionInformation exceptionInformation,
                                     final CacheEntry cachedContent) {
    if (resilienceDuration == 0) {
      return 0;
    }
    if (resilienceDuration == Long.MAX_VALUE) {
      return Long.MAX_VALUE;
    }
    long _maxSuppressUntil = exceptionInformation.getSinceTime() + resilienceDuration;
    long _deltaMs = calculateRetryDelta(exceptionInformation);
    return Math.min(exceptionInformation.getLoadTime() + _deltaMs, _maxSuppressUntil);
  }

  private long calculateRetryDelta(final LoadExceptionInformation exceptionInformation) {
    long _delta = (long)
      (retryInterval * Math.pow(multiplier, exceptionInformation.getRetryCount()));
    _delta -= random.nextDouble() * randomization * _delta;
    return Math.min(_delta, maxRetryInterval);
  }

  @Override
  public long retryLoadAfter(final Object key,
                             final LoadExceptionInformation exceptionInformation) {
    if (retryInterval == 0) {
      return 0;
    }
    if (retryInterval == Long.MAX_VALUE) {
      return Long.MAX_VALUE;
    }
    return exceptionInformation.getLoadTime() + calculateRetryDelta(exceptionInformation);
  }

}
