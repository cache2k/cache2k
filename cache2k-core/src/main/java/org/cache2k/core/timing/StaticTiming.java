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

import org.cache2k.CacheEntry;
import org.cache2k.annotation.Nullable;
import org.cache2k.config.Cache2kConfig;
import org.cache2k.core.AccessWrapper;
import org.cache2k.core.api.InternalCacheBuildContext;
import org.cache2k.core.api.InternalCacheCloseContext;
import org.cache2k.core.Entry;
import org.cache2k.core.ExceptionWrapper;
import org.cache2k.expiry.RefreshAheadPolicy;
import org.cache2k.operation.TimeReference;
import org.cache2k.expiry.Expiry;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.io.LoadExceptionInfo;
import org.cache2k.io.ResiliencePolicy;

/**
 * Expiry time is constant
 *
 * @author Jens Wilke
 */
public class StaticTiming<K, V> extends Timing<K, V> {

  protected final ResiliencePolicy<K, V> resiliencePolicy;
  protected final TimeReference clock;
  protected final boolean sharpExpiry;
  @Nullable protected final RefreshAheadPolicy<? super K, ? super V, Object> refreshAheadPolicy;
  protected final long expiryTicks;
  private final Timer timer;
  private TimerEventListener<K, V> target;

  StaticTiming(InternalCacheBuildContext<K, V> buildContext,
               ResiliencePolicy<K, V> resiliencePolicy) {
    clock = buildContext.getTimeReference();
    Cache2kConfig<K, V> cfg = buildContext.getConfig();
    if (cfg.getExpireAfterWrite() == null
      || cfg.getExpireAfterWrite() == Cache2kConfig.EXPIRY_ETERNAL) {
      this.expiryTicks = ExpiryPolicy.ETERNAL;
    } else {
      this.expiryTicks = clock.toTicks(cfg.getExpireAfterWrite());
    }
    if (cfg.isRefreshAhead()) {
      if (buildContext.getConfig().getRefreshAheadPolicy() != null) {
        throw new IllegalArgumentException("User refreshAhead flag or policy but not both");
      }
      refreshAheadPolicy = (RefreshAheadPolicy<K, V, Object>) RefreshAheadPolicy.LEGACY_DEFAULT;
    } else {
      refreshAheadPolicy =
        (RefreshAheadPolicy<? super K, ? super V, Object>)
        buildContext.createCustomization(buildContext.getConfig().getRefreshAheadPolicy());
    }
    sharpExpiry = cfg.isSharpExpiry();
    if (cfg.getTimerLag() == null) {
      timer = new DefaultTimer(clock, buildContext.createScheduler());
    } else {
      timer =
        new DefaultTimer(clock, buildContext.createScheduler(), clock.toTicks(cfg.getTimerLag()));
    }
    this.resiliencePolicy = resiliencePolicy;
  }

  @Override
  public void setTarget(TimerEventListener<K, V> target) {
    this.target = target;
  }

  @Override
  public void cancelAll() {
    timer.cancelAll();
  }

  @Override
  public void close(InternalCacheCloseContext closeContext) {
    closeContext.closeCustomization(resiliencePolicy, "resiliencePolicy");
    timer.close(closeContext);
  }

  @Override
  public long calculateExpiry(Entry<K, V> e, V value, long loadTime) {
    return calcNextRefreshTime(e.getKey(), value, loadTime, e, null, expiryTicks, sharpExpiry);
  }

  @Override
  public long limitExpiryTime(long now, long expiryTime) {
    return limitExpiryToMaxLinger(now, expiryTicks, expiryTime, sharpExpiry);
  }

  @Override
  public long suppressExceptionUntil(Entry<K, V> e, LoadExceptionInfo inf) {
    long pointInTime =
      resiliencePolicy.suppressExceptionUntil(e.getKey(), inf, e.getInspectionEntry());
    return Expiry.mixTimeSpanAndPointInTime(inf.getLoadTime(), expiryTicks, pointInTime);
  }

  @Override
  public long cacheExceptionUntil(Entry<K, V> e, LoadExceptionInfo inf) {
    long pointInTime = resiliencePolicy.retryLoadAfter(e.getKey(), inf);
    return Expiry.mixTimeSpanAndPointInTime(inf.getLoadTime(), expiryTicks, pointInTime);
  }

  /**
   * If we are about to start the timer, but discover that the entry is
   * expired already, we need to start the refresh task.
   * This will also start a refresh task, if the entry just was refreshed and it is
   * expired immediately. The refresh task will handle this and expire the entry.
   */
  long expiredEventuallyStartBackgroundRefresh(Entry<K, V> e, boolean sharpExpiry) {
    if (refreshAheadPolicy != null) {
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
   * @param e          the entry
   * @param expiryTime expiry time with special values as defined in {@link ExpiryTimeValues}
   *
   * @param refreshTime
   * @return adjusted value for {@link Entry#setRawExpiry(long)}
   */
  @Override
  public long stopStartTimer(Entry<K, V> e, long expiryTime, long refreshTime) {
    cancelExpiryTimer(e);
    if (expiryTime == ExpiryTimeValues.NOW) {
      return Entry.EXPIRED;
    }
    if (expiryTime == ExpiryTimeValues.NEUTRAL) {
      long currentExpiry = e.getRawExpiry();
      if (currentExpiry == 0) {
        throw new IllegalArgumentException("neutral expiry not allowed for creation");
      }
      return e.getRawExpiry();
    }
    if (expiryTime == ExpiryTimeValues.ETERNAL) {
      return expiryTime;
    }
    long now = clock.ticks();
    long absExpiryTime = Math.abs(expiryTime);
    if (absExpiryTime <= now) {
      return expiredEventuallyStartBackgroundRefresh(e, expiryTime < 0);
    }
    if (refreshTime > 0) {
      e.setTask(new Tasks.RefreshTimerTask<K, V>().to(target, e));
      scheduleTask(refreshTime, e);
    } else {
      e.setTask(new Tasks.ExpireTimerTask<K, V>().to(target, e));
      scheduleTask(absExpiryTime, e);
    }
    return expiryTime;
  }

  void scheduleTask(long t, Entry<K, V> e) {
    try {
      timer.schedule(e.getTask(), t);
    } catch (IllegalStateException ignore) {
    }
  }

  @SuppressWarnings("unchecked")
  public void cancelExpiryTimer(Entry<K, V> e) {
    Tasks<K, V> tsk = (Tasks<K, V>) e.getTask();
    if (tsk != null) {
      timer.cancel(tsk);
    }
    e.setTask(null);
  }

  @Override
  public long getExpiryAfterWriteTicks() {
    return expiryTicks;
  }

  @Override
  public boolean hasRefreshAheadPolicy() {
    return refreshAheadPolicy != null;
  }

  @Override
  public long calculateRefreshTime(RefreshAheadPolicy.Context<Object> context) {
    if (refreshAheadPolicy == null) {
      return 0;
    }
    return refreshAheadPolicy.refreshAheadTime(context);
  }

  static <K, V> long calcNextRefreshTime(
    K key, V value, long now, CacheEntry<K, V> entry,
    ExpiryPolicy<K, V> policy, long maxLinger, boolean sharpExpiryEnabled) {
    if (maxLinger == 0) {
      return 0;
    }
    if (policy != null) {
      long t = policy.calculateExpiryTime(key, value, now, entry);
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
    if (sharpExpiryEnabled && requestedExpiryTime > ExpiryTimeValues.NOW
      && requestedExpiryTime < ExpiryPolicy.ETERNAL) {
      requestedExpiryTime = -requestedExpiryTime;
    }
    return Expiry.mixTimeSpanAndPointInTime(now, maxLinger, requestedExpiryTime);
  }

  @Override
  public Object wrapLoadValueForRefresh(RefreshAheadPolicy.Context<Object> ctx ,
                                        Entry<K, V> e, Object valueOrException) {
    if (refreshAheadPolicy == null) {
      return valueOrException;
    }
    int requiredAccessCount = refreshAheadPolicy.requiredHits(ctx);
    return AccessWrapper.of(e, (V) valueOrException, requiredAccessCount);
  }

}
