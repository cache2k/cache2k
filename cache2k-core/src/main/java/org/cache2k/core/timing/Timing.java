package org.cache2k.core.timing;

/*
 * #%L
 * cache2k implementation
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
import org.cache2k.core.Entry;
import org.cache2k.core.ExceptionWrapper;
import org.cache2k.core.HeapCache;
import org.cache2k.core.InternalCache;
import org.cache2k.core.util.InternalClock;
import org.cache2k.core.util.Util;
import org.cache2k.expiry.Expiry;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.expiry.ValueWithExpiryTime;
import org.cache2k.core.util.TunableConstants;
import org.cache2k.core.util.TunableFactory;
import org.cache2k.integration.ExceptionInformation;
import org.cache2k.integration.ResiliencePolicy;

/**
 * Encapsulates logic for expiry times calculation and timer handling.
 *
 * @author Jens Wilke
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public abstract class Timing<K, V>  {

  /** Used as default */
  public static final Timing ETERNAL = new EternalTiming();
  public static final Timing ETERNAL_IMMEDIATE = new EternalImmediate();

  private static final Timing IMMEDIATE = new Immediate();

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

  public static <K, V> Timing<K, V> of(InternalClock clock, Cache2kConfiguration<K, V> cfg) {
    if (cfg.getExpireAfterWrite() == 0
      && zeroOrUnspecified(cfg.getRetryInterval())) {
      return IMMEDIATE;
    }
    if (cfg.getExpiryPolicy() != null
      || (cfg.getValueType() != null
        && ValueWithExpiryTime.class.isAssignableFrom(cfg.getValueType().getType()))
      || cfg.getResiliencePolicy() != null) {
      DynamicTiming<K, V> h = new DynamicTiming<K, V>(clock, cfg);
      return h;
    }
    if (cfg.getResilienceDuration() > 0 && !cfg.isSuppressExceptions()) {
      throw new IllegalArgumentException(
        "Ambiguous: exceptions suppression is switched off, but resilience duration is specified");
    }
    if (realDuration(cfg.getExpireAfterWrite())
      || realDuration(cfg.getRetryInterval())
      || realDuration(cfg.getResilienceDuration())) {
      StaticTiming<K, V> h = new StaticTiming<K, V>(clock, cfg);
      return h;
    }
    if ((cfg.getExpireAfterWrite() == ExpiryPolicy.ETERNAL || cfg.getExpireAfterWrite() == -1)) {
      if (zeroOrUnspecified(cfg.getRetryInterval())) {
        return ETERNAL_IMMEDIATE;
      }
      if (cfg.getRetryInterval() == ExpiryPolicy.ETERNAL) {
        return ETERNAL;
      }
    }
    throw new IllegalArgumentException("expiry time ambiguous");
  }

  /**
   * Initialize timer, if needed.
   */
  public void init(InternalCache<K, V> c) { }

  /**
   * Cancels all pending timer events.
   */
  public void cancelAll() { }

  public void close() { }

  /**
   * Return effective expiry policy, or null
   */
  public ExpiryPolicy<K, V> getExpiryPolicy() { return null; }

  /**
   * Calculates the expiry time for a value that was just loaded or inserted into the cache.
   *
   * @param e The entry, filled with the previous value if there is a value present alreay.
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
      throw new IllegalArgumentException(
        "Cache is not configured for variable expiry: " + Util.formatMillis(expiryTime));
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

  /**
   * Base class for all timing handlers that actually need not to know the current time.
   */
  public abstract static class AgnosticTimingHandler<K, V> extends Timing<K, V> { }

  static class EternalImmediate<K, V> extends AgnosticTimingHandler<K, V> {

    @Override
    public long calculateNextRefreshTime(Entry<K, V> e, V v, long loadTime) {
      return ExpiryPolicy.ETERNAL;
    }

    @Override
    public long cacheExceptionUntil(Entry<K, V> e, ExceptionInformation inf) {
      return 0;
    }

    @Override
    public long suppressExceptionUntil(Entry<K, V> e, ExceptionInformation inf) {
      return 0;
    }

  }

  static class Immediate<K, V> extends AgnosticTimingHandler<K, V> {

    @Override
    public long calculateNextRefreshTime(Entry<K, V> e, V v, long loadTime) {
      return 0;
    }

    @Override
    public long cacheExceptionUntil(Entry<K, V> e, ExceptionInformation inf) {
      return 0;
    }

    @Override
    public long suppressExceptionUntil(Entry<K, V> e, ExceptionInformation inf) {
      return 0;
    }
  }

}
