package org.cache2k.core.timing;

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

import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.CacheEntry;
import org.cache2k.core.api.CacheBuildContext;
import org.cache2k.core.api.CacheCloseContext;
import org.cache2k.core.Entry;
import org.cache2k.core.ExceptionWrapper;
import org.cache2k.core.util.Util;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.expiry.ValueWithExpiryTime;
import org.cache2k.integration.ExceptionInformation;
import org.cache2k.integration.ResiliencePolicy;

/**
 * Encapsulates logic for expiry times calculation and timer handling.
 *
 * @author Jens Wilke
 */
@SuppressWarnings({"unchecked"})
public abstract class Timing<K, V>  {

  /**
   * Instance of expiry calculator that extracts the expiry time from the value.
   */
  static final ExpiryPolicy<?, ValueWithExpiryTime> ENTRY_EXPIRY_CALCULATOR_FROM_VALUE =
    new ExpiryPolicy<Object, ValueWithExpiryTime>() {
      @Override
      public long calculateExpiryTime(
        Object key, ValueWithExpiryTime value, long loadTime,
        CacheEntry<Object, ValueWithExpiryTime> oldEntry) {
        return value.getCacheExpiryTime();
      }
    };

  static boolean realDuration(long t) {
    return t > 0 && t < Long.MAX_VALUE;
  }

  static boolean zeroOrUnspecified(long t) {
    return t == 0 || t == -1;
  }

  public static <K, V> Timing<K, V> of(CacheBuildContext<K, V> buildContext) {
    Cache2kConfiguration<K, V> cfg = buildContext.getConfiguration();
    if (cfg.getExpireAfterWrite() == 0
      && zeroOrUnspecified(cfg.getRetryInterval())) {
      return TimeAgnosticTiming.IMMEDIATE;
    }
    if (cfg.getExpiryPolicy() != null
      || (cfg.getValueType() != null
        && ValueWithExpiryTime.class.isAssignableFrom(cfg.getValueType().getType()))
      || cfg.getResiliencePolicy() != null) {
      DynamicTiming<K, V> h = new DynamicTiming<K, V>(buildContext);
      return h;
    }
    if (cfg.getResilienceDuration() > 0 && !cfg.isSuppressExceptions()) {
      throw new IllegalArgumentException(
        "Ambiguous: exceptions suppression is switched off, but resilience duration is specified");
    }
    if (realDuration(cfg.getExpireAfterWrite())
      || realDuration(cfg.getRetryInterval())
      || realDuration(cfg.getResilienceDuration())) {
      StaticTiming<K, V> h = new StaticTiming<K, V>(buildContext);
      return h;
    }
    if ((cfg.getExpireAfterWrite() == ExpiryPolicy.ETERNAL || cfg.getExpireAfterWrite() == -1)) {
      if (zeroOrUnspecified(cfg.getRetryInterval())) {
        return TimeAgnosticTiming.ETERNAL_IMMEDIATE;
      }
      if (cfg.getRetryInterval() == ExpiryPolicy.ETERNAL) {
        return TimeAgnosticTiming.ETERNAL;
      }
    }
    throw new IllegalArgumentException("expiry time ambiguous");
  }

  /**
   * Set the target for timer events. Called during cache build before any timer
   * tasks are created. For each cache instance there is a timing instance and both
   * reference each other. We create timing first, then the cache.
   */
  public void setTarget(TimerEventListener<K, V> c) { }

  /**
   * Cancels all pending timer events.
   */
  public void cancelAll() { }

  public void close(CacheCloseContext closeContext) { }

  /**
   * Calculates the expiry time for a value that was just loaded or inserted into the cache.
   *
   * @param e The entry, filled with the previous value if there is a value present already.
   * @param v The new value or an exception wrapped in {@link ExceptionWrapper}
   * @param loadTime the time immediately before the load started
   * @return Point in time when the entry should expire. Meaning identical to
   *         {@link ExpiryPolicy#calculateExpiryTime(Object, Object, long, CacheEntry)}
   */
  public abstract long calculateNextRefreshTime(Entry<K, V> e, V v, long loadTime);

  /**
   * Delegated to the resilience policy
   *
   * @see ResiliencePolicy#suppressExceptionUntil
   */
  public abstract long suppressExceptionUntil(Entry<K, V> e, ExceptionInformation inf);

  /**
   * Delegated to the resilience policy
   *
   * @see ResiliencePolicy#retryLoadAfter
   */
  public abstract long cacheExceptionUntil(Entry<K, V> e, ExceptionInformation inf);

  /**
   * Convert expiry value to the entry field value, essentially maps 0 to {@link Entry#EXPIRED}
   * since 0 is a virgin entry. Restart the timer if needed.
   *
   * @param expiryTime calculated expiry time
   * @return sanitized nextRefreshTime for storage in the entry.
   */
  public long stopStartTimer(long expiryTime, Entry<K, V> e) {
    if ((expiryTime > 0 && expiryTime < Long.MAX_VALUE) || expiryTime < 0) {
      throw new IllegalArgumentException("Cache is not configured for variable expiry");
    }
    return expiryTime == 0 ? Entry.EXPIRED : expiryTime;
  }

  /**
   * Start probation timer.
   */
  public boolean startRefreshProbationTimer(Entry<K, V> e, long nextRefreshTime) {
    return true;
  }

  /**
   * Cancel the timer on the entry, if a timer was set.
   */
  public void cancelExpiryTimer(Entry<K, V> e) { }

  /**
   * Schedule second timer event for the expiry tie if sharp expiry is switched on.
   */
  public void scheduleFinalTimerForSharpExpiry(Entry<K, V> e) { }

}
