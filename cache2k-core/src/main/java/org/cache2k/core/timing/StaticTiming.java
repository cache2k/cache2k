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

import org.cache2k.CacheEntry;
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.core.CacheBuildContext;
import org.cache2k.core.CacheCloseContext;
import org.cache2k.core.DefaultResiliencePolicy;
import org.cache2k.core.Entry;
import org.cache2k.core.ExceptionWrapper;
import org.cache2k.core.HeapCache;
import org.cache2k.core.util.InternalClock;
import org.cache2k.expiry.Expiry;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.integration.ExceptionInformation;
import org.cache2k.integration.ResiliencePolicy;

/**
 * Expiry time is constant
 *
 * @author Jens Wilke
 */
public class StaticTiming<K, V> extends Timing<K, V> {

  static final long SAFETY_GAP_MILLIS = HeapCache.TUNABLE.sharpExpirySafetyGapMillis;

  protected final ResiliencePolicy<K, V> resiliencePolicy;
  protected final InternalClock clock;
  protected final boolean sharpExpiry;
  protected final boolean refreshAhead;
  protected final long expiryMillis;
  protected final long lagMillis;

  private Timer timer;
  private TimerEventListener<K, V> target;

  StaticTiming(CacheBuildContext<K, V> buildContext) {
    clock = buildContext.getClock();
    Cache2kConfiguration<K, V> c = buildContext.getConfiguration();
    long expiryMillis = c.getExpireAfterWrite();
    if (expiryMillis == ExpiryPolicy.ETERNAL || expiryMillis < 0) {
      this.expiryMillis = ExpiryPolicy.ETERNAL;
    } else {
      this.expiryMillis = expiryMillis;
    }
    refreshAhead = c.isRefreshAhead();
    sharpExpiry = c.isSharpExpiry();
    long lagMillisTmp = c.getTimerLag();
    if (lagMillisTmp == Cache2kConfiguration.UNSET_LONG) {
      lagMillis = HeapCache.TUNABLE.timerLagMillis;
    } else {
      lagMillis = lagMillisTmp;
    }
    timer = new DefaultTimer(clock, lagMillis);
    resiliencePolicy = provideResiliencePolicy(buildContext);
  }

  ResiliencePolicy<K, V> provideResiliencePolicy(CacheBuildContext<K, V> buildContext) {
    Cache2kConfiguration<K, V> c = buildContext.getConfiguration();
    final long expireAfterWriteMillis = c.getExpireAfterWrite();
    final long resilienceDuration = c.getResilienceDuration();
    final boolean suppressException = c.isSuppressExceptions();
    final long retryInterval = c.getRetryInterval();
    final long maxRetryInterval = c.getMaxRetryInterval();
    ResiliencePolicy.Context ctx = new ResiliencePolicy.Context() {
      @Override
      public long getExpireAfterWriteMillis() {
        return expireAfterWriteMillis;
      }

      @Override
      public long getResilienceDurationMillis() {
        return suppressException ? resilienceDuration : 0;
      }

      @Override
      public long getRetryIntervalMillis() {
        return retryInterval;
      }

      @Override
      public long getMaxRetryIntervalMillis() {
        return maxRetryInterval;
      }
    };
    ResiliencePolicy<K, V> policy = buildContext.createCustomization(c.getResiliencePolicy());
    if (policy == null) {
      policy = new DefaultResiliencePolicy<K, V>();
    }
    policy.init(ctx);
    return policy;
  }

  @Override
  public void setTarget(TimerEventListener<K, V> target) {
    this.target = target;
  }

  @Override
  public void cancelAll() {
    Timer timer = this.timer;
    if (timer != null) {
      timer.cancelAll();
    }
  }

  @Override
  public void close(CacheCloseContext closeContext) {
    Timer timer = this.timer;
    if (timer != null) {
      timer.cancelAll();
      closeContext.closeCustomization(resiliencePolicy, "resiliencePolicy");
    }
    this.timer = null;
  }

  @Override
  public long calculateNextRefreshTime(Entry<K, V> e, V v, long loadTime) {
    return calcNextRefreshTime(e.getKey(), v, loadTime, e, null, expiryMillis, sharpExpiry);
  }

  @Override
  public long suppressExceptionUntil(Entry<K, V> e, ExceptionInformation inf) {
    return resiliencePolicy.suppressExceptionUntil(e.getKey(), inf, e.getTempCacheEntry());
  }

  @Override
  public long cacheExceptionUntil(Entry<K, V> e, ExceptionInformation inf) {
    return resiliencePolicy.retryLoadAfter(e.getKey(), inf);
  }

  /**
   * If we are about to start the timer, but discover that the entry is
   * expired already, we need to start the refresh task.
   * This will also start a refresh task, if the entry just was refreshed and it is
   * expired immediately. The refresh task will handle this and expire the entry.
   */
  long expiredEventuallyStartBackgroundRefresh(Entry<K, V> e, boolean sharpExpiry) {
    if (refreshAhead) {
      e.setTask(new Tasks.RefreshTimerTask<K, V>().to(target, e));
      scheduleTask(0, e);
      return sharpExpiry ? Entry.EXPIRED_REFRESH_PENDING : Entry.DATA_VALID;
    }
    return Entry.EXPIRED;
  }

  /**
   * Calculate the needed timer value, which depends on the setting of
   * sharpExpiry and refreshAhead. It may happen that the timer is not
   * started, because the time is already passed. In this case
   * {@link Entry#EXPIRED} is returned. Callers need to check that and may
   * be remove the entry consequently from the cache.
   *
   * @param expiryTime expiry time with special values as defined in {@link ExpiryTimeValues}
   * @param e          the entry
   * @return adjusted value for nextRefreshTime.
   */
  @Override
  public long stopStartTimer(long expiryTime, Entry<K, V> e) {
    cancelExpiryTimer(e);
    if (expiryTime == ExpiryTimeValues.NOW) {
      return Entry.EXPIRED;
    }
    if (expiryTime == ExpiryTimeValues.NEUTRAL) {
      long nrt = e.getNextRefreshTime();
      if (nrt == 0) {
        throw new IllegalArgumentException("neutral expiry not allowed for creation");
      }
      return e.getNextRefreshTime();
    }
    if (expiryTime == ExpiryTimeValues.ETERNAL) {
      return expiryTime;
    }
    if (expiryTime == ExpiryTimeValues.REFRESH) {
      return expiredEventuallyStartBackgroundRefresh(e, false);
    }
    long now = clock.millis();
    if (Math.abs(expiryTime) <= now) {
      return expiredEventuallyStartBackgroundRefresh(e, expiryTime < 0);
    }
    if (expiryTime < 0) {
      long timerTime = -expiryTime - SAFETY_GAP_MILLIS - timer.getLagMillis();
      if (timerTime >= now) {
        e.setTask(new Tasks.ExpireTimerTask<K, V>().to(target, e));
        scheduleTask(timerTime, e);
        expiryTime = -expiryTime;
      } else {
        scheduleFinalExpireWithOptionalRefresh(e, -expiryTime);
      }
    } else {
      scheduleFinalExpireWithOptionalRefresh(e, expiryTime);
    }
    return expiryTime;
  }

  @Override
  public boolean startRefreshProbationTimer(Entry<K, V> e, long nextRefreshTime) {
    cancelExpiryTimer(e);
    if (nextRefreshTime == ExpiryTimeValues.ETERNAL) {
      e.setNextRefreshTime(nextRefreshTime);
      return false;
    }
    if (nextRefreshTime > 0 && nextRefreshTime < Entry.EXPIRY_TIME_MIN) {
      e.setNextRefreshTime(Entry.EXPIRED);
      return true;
    }
    long absTime = Math.abs(nextRefreshTime);
    e.setRefreshProbationNextRefreshTime(absTime);
    e.setNextRefreshTime(Entry.EXPIRED_REFRESHED);
    e.setTask(new Tasks.RefreshExpireTimerTask<K, V>().to(target, e));
    scheduleTask(absTime, e);
    return false;
  }

  @Override
  public void scheduleFinalTimerForSharpExpiry(Entry<K, V> e) {
    cancelExpiryTimer(e);
    scheduleFinalExpireWithOptionalRefresh(e, e.getNextRefreshTime());
  }

  /**
   * Sharp expiry is requested: Either schedule refresh or expiry.
   */
  void scheduleFinalExpireWithOptionalRefresh(Entry<K, V> e, long t) {
    if (refreshAhead) {
      e.setTask(new Tasks.RefreshTimerTask<K, V>().to(target, e));
    } else {
      e.setTask(new Tasks.ExpireTimerTask<K, V>().to(target, e));
    }
    scheduleTask(t, e);
  }

  void scheduleTask(long nextRefreshTime, Entry<K, V> e) {
    Timer timer = this.timer;
    if (timer != null) {
      try {
        timer.schedule(e.getTask(), nextRefreshTime);
      } catch (IllegalStateException ignore) {
      }
    }
  }

  @SuppressWarnings("unchecked")
  public void cancelExpiryTimer(Entry<K, V> e) {
    Tasks<K, V> tsk = (Tasks<K, V>) e.getTask();
    Timer timer = this.timer;
    if (tsk != null && timer != null) {
      timer.cancel(tsk);
    }
    e.setTask(null);
  }

  static <K, V> long calcNextRefreshTime(
    K key, V newValue, long now, CacheEntry<K, V> entry,
    ExpiryPolicy<K, V> policy, long maxLinger, boolean sharpExpiryEnabled) {
    if (maxLinger == 0) {
      return 0;
    }
    if (policy != null) {
      long t = policy.calculateExpiryTime(key, newValue, now, entry);
      return limitExpiryToMaxLinger(now, maxLinger, t, sharpExpiryEnabled);
    }
    if (maxLinger < ExpiryPolicy.ETERNAL) {
      long t = maxLinger + now;
      if (t >= 0) {
        return t;
      }
    }
    return ExpiryPolicy.ETERNAL;
  }

  /**
   * Ignore the value of the expiry policy if later then the maximum expiry time.
   * If max linger takes over, we do not request sharp expiry.
   *
   * <p>The situation becomes messy if the point in time for the maximum expiry is
   * close to the requested expiry time and sharp expiry is requested. The expiry or
   * a reload (with refresh ahead) may come to late and overlap the expiry time. As
   * incomplete fix we use the safety gap, but if loaders are taking longer then the
   * safety gap a value becomes visible that should be expired. The solutions would
   * be to pass on two times from here: a refresh time and the point in time for sharp
   * expiry, and expiry correctly while a concurrent load is proceeding.
   */
  static long limitExpiryToMaxLinger(long now, long maxLinger, long requestedExpiryTime,
                                     boolean sharpExpiryEnabled) {
    if (sharpExpiryEnabled && requestedExpiryTime > ExpiryPolicy.REFRESH
      && requestedExpiryTime < ExpiryPolicy.ETERNAL) {
      requestedExpiryTime = -requestedExpiryTime;
    }
    return Expiry.mixTimeSpanAndPointInTime(now, maxLinger, requestedExpiryTime);
  }

}
