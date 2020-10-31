package org.cache2k.core;

/*
 * #%L
 * cache2k core implementation
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

import org.cache2k.CacheEntry;
import org.cache2k.configuration.CacheBuildContext;
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.configuration.CustomizationSupplier;
import org.cache2k.io.LoadExceptionInfo;
import org.cache2k.io.ResiliencePolicy;

import java.time.Duration;
import java.util.Random;

import static org.cache2k.configuration.Cache2kConfiguration.ETERNAL_DURATION;
import static org.cache2k.configuration.Cache2kConfiguration.UNSET_LONG;

/**
 * Default resilience policy which implements a exponential back off and randomization
 * of the retry intervals.
 *
 * @author Jens Wilke
 */
public class DefaultResiliencePolicy<K, V> implements ResiliencePolicy<K, V> {

  public static final Supplier SUPPLIER = new Supplier();

  /**
   * We use a common random instance. Since this is only called for an exception
   * we do not bother for contention.
   */
  static final Random SHARED_RANDOM = new Random();

  static final int RETRY_PERCENT_OF_RESILIENCE_DURATION = 10;
  static final int MIN_RETRY_INTERVAL = 1000;

  private double multiplier = 1.5;
  private double randomization = 0.5;

  private long resilienceDuration;
  private long maxRetryInterval;
  private long retryInterval;
  private boolean suppressExceptions;

  /**
   * Construct a resilience policy with multiplier 1.5 and randomization 0.5.
   */
  public DefaultResiliencePolicy(Cache2kConfiguration<K, V> cfg) {
    suppressExceptions = cfg.isSuppressExceptions();
    resilienceDuration = toMillis(cfg.getResilienceDuration());
    maxRetryInterval = toMillis(cfg.getMaxRetryInterval());
    retryInterval = toMillis(cfg.getRetryInterval());
    if (resilienceDuration == UNSET_LONG) {
      if (cfg.getExpireAfterWrite() == ETERNAL_DURATION) {
        resilienceDuration = 0;
      } else {
        if (cfg.getExpireAfterWrite() != null) {
          resilienceDuration = cfg.getExpireAfterWrite().toMillis();
        } else {
          resilienceDuration = UNSET_LONG;
        }
      }
    } else {
      if (maxRetryInterval == UNSET_LONG) {
        maxRetryInterval = resilienceDuration;
      }
    }
    if (maxRetryInterval == UNSET_LONG && retryInterval == UNSET_LONG) {
      maxRetryInterval = resilienceDuration;
    }
    if (retryInterval == UNSET_LONG) {
      retryInterval = resilienceDuration * RETRY_PERCENT_OF_RESILIENCE_DURATION / 100;
      retryInterval = Math.min(retryInterval, maxRetryInterval);
      retryInterval = Math.max(MIN_RETRY_INTERVAL, retryInterval);
    }
    if (retryInterval > maxRetryInterval) {
      maxRetryInterval = retryInterval;
    }
    if (maxRetryInterval > resilienceDuration && resilienceDuration != 0) {
      resilienceDuration = maxRetryInterval;
    }
    if (resilienceDuration != UNSET_LONG && !suppressExceptions) {
      throw new IllegalArgumentException(
        "exception suppression disabled " +
        "but resilience duration set");
    }
    if (!suppressExceptions) {
      resilienceDuration = 0;
    }
  }

  static long toMillis(Duration d) {
    if (d == null) {
      return Cache2kConfiguration.UNSET_LONG;
    }
    return d.toMillis();
  }

  public double getMultiplier() {
    return multiplier;
  }

  public void setMultiplier(double multiplier) {
    this.multiplier = multiplier;
  }

  public double getRandomization() {
    return randomization;
  }

  public void setRandomization(double randomization) {
    this.randomization = randomization;
  }

  public long getResilienceDuration() { return resilienceDuration; }

  public long getMaxRetryInterval() { return maxRetryInterval; }

  public long getRetryInterval() { return retryInterval; }

  @Override
  public long suppressExceptionUntil(K key,
                                     LoadExceptionInfo loadExceptionInfo,
                                     CacheEntry<K, V> cachedContent) {
    if (resilienceDuration == 0 || resilienceDuration == Long.MAX_VALUE) {
      return resilienceDuration;
    }
    long maxSuppressUntil = loadExceptionInfo.getSinceTime() + resilienceDuration;
    long deltaMs = calculateRetryDelta(loadExceptionInfo);
    return Math.min(loadExceptionInfo.getLoadTime() + deltaMs, maxSuppressUntil);
  }

  private long calculateRetryDelta(LoadExceptionInfo loadExceptionInfo) {
    long delta = (long)
      (retryInterval * Math.pow(multiplier, loadExceptionInfo.getRetryCount()));
    delta += SHARED_RANDOM.nextDouble() * randomization * delta;
    return Math.min(delta, maxRetryInterval);
  }

  @Override
  public long retryLoadAfter(K key, LoadExceptionInfo loadExceptionInfo) {
    if (retryInterval == 0) {
      return 0;
    }
    if (retryInterval == Long.MAX_VALUE) {
      return Long.MAX_VALUE;
    }
    return loadExceptionInfo.getLoadTime() + calculateRetryDelta(loadExceptionInfo);
  }

  public static class Supplier<K, V> implements CustomizationSupplier<ResiliencePolicy<K, V>> {
    @Override
    public ResiliencePolicy<K, V> supply(CacheBuildContext buildContext) {
      return new DefaultResiliencePolicy<>(buildContext.getConfiguration());
    }
  }

}
