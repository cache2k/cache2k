package org.cache2k.core.timing;

/*-
 * #%L
 * cache2k core implementation
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

import org.cache2k.config.Cache2kConfig;
import org.cache2k.CacheEntry;
import org.cache2k.core.api.InternalCacheBuildContext;
import org.cache2k.core.api.InternalCacheCloseContext;
import org.cache2k.core.Entry;
import org.cache2k.core.ExceptionWrapper;
import org.cache2k.core.api.NeedsClose;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.expiry.ValueWithExpiryTime;
import org.cache2k.io.LoadExceptionInfo;
import org.cache2k.io.ResiliencePolicy;

import java.time.Duration;

/**
 * Encapsulates logic for expiry times calculation and timer handling.
 *
 * @author Jens Wilke
 */
@SuppressWarnings({"unchecked"})
public abstract class Timing<K, V> implements NeedsClose {

  /**
   * Instance of expiry calculator that extracts the expiry time from the value.
   */
  static final ExpiryPolicy<?, ValueWithExpiryTime> ENTRY_EXPIRY_CALCULATOR_FROM_VALUE =
    (ExpiryPolicy<Object, ValueWithExpiryTime>) (key, value, startTime, currentEntry) -> value.getCacheExpiryTime();

  static boolean realDuration(Duration d) {
    return d != null && !Duration.ZERO.equals(d) && d != Cache2kConfig.EXPIRY_ETERNAL;
  }

  static boolean realDuration(long t) {
    return t > 0 && t < Long.MAX_VALUE;
  }

  static boolean zeroOrUnspecified(Duration t) {
    return t == null || t.equals(Duration.ZERO);
  }

  static boolean zeroOrUnspecified(long t) {
    return t == 0 || t == -1;
  }

  public static <K, V> Timing<K, V> of(InternalCacheBuildContext<K, V> ctx) {
    Cache2kConfig<K, V> cfg = ctx.getConfig();
    if (Duration.ZERO.equals(cfg.getExpireAfterWrite())) {
      return TimeAgnosticTiming.IMMEDIATE;
    }
    ResiliencePolicy<K, V> resiliencePolicy = (ResiliencePolicy<K, V>)
      ctx.createCustomization(cfg.getResiliencePolicy(), ResiliencePolicy.disabledPolicy());
    if (cfg.getExpiryPolicy() != null
      || (cfg.getValueType() != null
        && ValueWithExpiryTime.class.isAssignableFrom(cfg.getValueType().getType()))
      || resiliencePolicy != ResiliencePolicy.DISABLED_POLICY) {
      DynamicTiming<K, V> h = new DynamicTiming<>(ctx, resiliencePolicy);
      return h;
    }
    if (realDuration(cfg.getExpireAfterWrite()) || !cfg.isEternal()) {
      StaticTiming<K, V> h = new StaticTiming<>(ctx, resiliencePolicy);
      return h;
    }
    return TimeAgnosticTiming.ETERNAL_IMMEDIATE;
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

  @Override
  public void close(InternalCacheCloseContext closeContext) { }

  /**
   * Calculates the expiry time for a value that was just loaded or inserted into the cache.
   *
   * @param e The entry, filled with the previous value if there is a value present already.
   * @param value The new value or an exception wrapped in {@link ExceptionWrapper}
   * @param loadTime the time immediately before the load started
   * @return Point in time when the entry should expire. Meaning identical to
   *         {@link ExpiryPolicy#calculateExpiryTime(Object, Object, long, CacheEntry)}
   */
  public abstract long calculateNextRefreshTime(Entry<K, V> e, V value, long loadTime);

  /**
   * Delegated to the resilience policy
   *
   * @see ResiliencePolicy#suppressExceptionUntil
   */
  public abstract long suppressExceptionUntil(Entry<K, V> e, LoadExceptionInfo inf);

  /**
   * Delegated to the resilience policy
   *
   * @see ResiliencePolicy#retryLoadAfter
   */
  public abstract long cacheExceptionUntil(Entry<K, V> e, LoadExceptionInfo inf);

  /**
   * Convert expiry value to the entry field value, essentially maps 0 to {@link Entry#EXPIRED}
   * since 0 is a virgin entry. Restart the timer if needed.
   *
   * @param expiryTime calculated expiry time
   * @return sanitized nextRefreshTime for storage in the entry.
   * @throws IllegalArgumentException if time is not supported
   */
  public long stopStartTimer(long expiryTime, Entry<K, V> e) {
    if ((expiryTime > 0 && expiryTime < Long.MAX_VALUE) || expiryTime < 0) {
      throw new IllegalArgumentException("Expiry timer disabled via eternal = true");
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
