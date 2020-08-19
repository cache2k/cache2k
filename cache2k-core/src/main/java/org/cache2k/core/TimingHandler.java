package org.cache2k.core;

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
import org.cache2k.configuration.CustomizationSupplier;
import org.cache2k.configuration.CustomizationReferenceSupplier;
import org.cache2k.core.util.InternalClock;
import org.cache2k.core.util.SimpleTimer;
import org.cache2k.core.util.SimpleTimerTask;
import org.cache2k.core.util.Util;
import org.cache2k.expiry.Expiry;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.expiry.ExpiryTimeValues;
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
public abstract class TimingHandler<K, V>  {

  /** Used as default */
  static final TimingHandler ETERNAL = new Eternal();
  static final TimingHandler ETERNAL_IMMEDIATE = new EternalImmediate();

  private static final TimingHandler IMMEDIATE = new Immediate();
  private static final int PURGE_INTERVAL = TunableFactory.get(Tunable.class).purgeInterval;
  private static final long SAFETY_GAP_MILLIS = HeapCache.TUNABLE.sharpExpirySafetyGapMillis;

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

  public static <K, V> TimingHandler<K, V> of(InternalClock clock, Cache2kConfiguration<K, V> cfg) {
    if (cfg.getExpireAfterWrite() == 0
      && zeroOrUnspecified(cfg.getRetryInterval())) {
      return IMMEDIATE;
    }
    if (cfg.getExpiryPolicy() != null
      || (cfg.getValueType() != null
        && ValueWithExpiryTime.class.isAssignableFrom(cfg.getValueType().getType()))
      || cfg.getResiliencePolicy() != null) {
      TimingHandler.Dynamic<K, V> h = new TimingHandler.Dynamic<K, V>(clock, cfg);
      return h;
    }
    if (cfg.getResilienceDuration() > 0 && !cfg.isSuppressExceptions()) {
      throw new IllegalArgumentException(
        "Ambiguous: exceptions suppression is switched off, but resilience duration is specified");
    }
    if (realDuration(cfg.getExpireAfterWrite())
      || realDuration(cfg.getRetryInterval())
      || realDuration(cfg.getResilienceDuration())) {
      TimingHandler.Static<K, V> h = new TimingHandler.Static<K, V>(clock, cfg);
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
   * Cancel all timer events, and re-initialize timer
   */
  public void reset() { }

  /**
   * Cancels all pending timer events.
   */
  public void cancelAll() { }

  public void close() { cancelAll(); }

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
   * Start timer for expiring an entry on the separate refresh hash.
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
  abstract static class TimeAgnostic<K, V> extends TimingHandler<K, V> {

  }

  private static class Eternal<K, V> extends TimeAgnostic<K, V> {

    @Override
    public long calculateNextRefreshTime(Entry<K, V> e, V v, long loadTime) {
      return ExpiryPolicy.ETERNAL;
    }

    @Override
    public long cacheExceptionUntil(Entry<K, V> e, ExceptionInformation inf) {
      return ExpiryPolicy.ETERNAL;
    }

    @Override
    public long suppressExceptionUntil(Entry<K, V> e, ExceptionInformation inf) {
      return ExpiryPolicy.ETERNAL;
    }
  }

  static class EternalImmediate<K, V> extends TimeAgnostic<K, V> {

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

  static class Immediate<K, V> extends TimeAgnostic<K, V> {

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

  static class Static<K, V> extends TimingHandler<K, V> {

    final InternalClock clock;
    boolean sharpExpiry;
    boolean refreshAhead;
    SimpleTimer[] timer;
    int timerMask;
    long maxLinger;
    InternalCache cache;
    /** Dirty counter, intentionally only 32 bit */
    int timerCancelCount = 0;
    int purgeIndex = 0;
    ResiliencePolicy<K, V> resiliencePolicy;
    CustomizationSupplier<ResiliencePolicy<K, V>> resiliencePolicyFactory;

    Static(InternalClock c, Cache2kConfiguration<K, V> cc) {
      clock = c;
      configure(cc);
    }

    void configure(final Cache2kConfiguration<K, V> c) {
      long expiryMillis  = c.getExpireAfterWrite();
      if (expiryMillis == ExpiryPolicy.ETERNAL || expiryMillis < 0) {
        maxLinger = ExpiryPolicy.ETERNAL;
      } else {
        maxLinger = expiryMillis;
      }
      ResiliencePolicy.Context ctx = new ResiliencePolicy.Context() {
        @Override
        public long getExpireAfterWriteMillis() {
          return c.getExpireAfterWrite();
        }

        @Override
        public long getResilienceDurationMillis() {
          return c.isSuppressExceptions() ? c.getResilienceDuration() : 0;
        }

        @Override
        public long getRetryIntervalMillis() {
          return c.getRetryInterval();
        }

        @Override
        public long getMaxRetryIntervalMillis() {
          return c.getMaxRetryInterval();
        }
      };
      resiliencePolicyFactory = c.getResiliencePolicy();
      if (resiliencePolicyFactory == null) {
        resiliencePolicy = new DefaultResiliencePolicy<K, V>();
      } else {
        if (resiliencePolicyFactory instanceof CustomizationReferenceSupplier) {
          try {
            resiliencePolicy = resiliencePolicyFactory.supply(null);
          } catch (Exception ignore) { }
        }
      }
      resiliencePolicy.init(ctx);
      refreshAhead = c.isRefreshAhead();
      sharpExpiry = c.isSharpExpiry();
      int timerCount = 1;
      if (c.isBoostConcurrency()) {
        int ncpu = Runtime.getRuntime().availableProcessors();
        timerCount = 2 << (31 - Integer.numberOfLeadingZeros(ncpu));
      }
      timer = new SimpleTimer[timerCount];
      timerMask = timerCount - 1;
    }

    @Override
    public synchronized void init(InternalCache<K, V> c) {
      cache = c;
      if (resiliencePolicy == null) {
        resiliencePolicy = c.createCustomization(resiliencePolicyFactory);
      }
      resiliencePolicyFactory = null;
    }

    @Override
    public synchronized void reset() {
      cancelAll();
      for (int i = 0; i <= timerMask; i++) {
        if (timer[i] != null) { continue; }
        timer[i] =  new SimpleTimer(clock, cache.getName(), true);
      }
    }

    @Override
    public synchronized void cancelAll() {
      SimpleTimer timer;
      for (int i = 0; i <= timerMask; i++) {
        if ((timer = this.timer[i]) == null) { continue; }
        timer.cancel();
        this.timer[i] = null;
      }
    }

    @Override
    public long calculateNextRefreshTime(Entry<K, V> e, V v, long loadTime) {
      return calcNextRefreshTime(e.getKey(), v, loadTime, e, null, maxLinger, sharpExpiry);
    }

    @Override
    public long suppressExceptionUntil(Entry<K, V> e, ExceptionInformation inf) {
      return resiliencePolicy.suppressExceptionUntil(
        e.getKey(), inf, cache.returnCacheEntry(e));
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
    long expiredEventuallyStartBackgroundRefresh(Entry e, boolean sharpExpiry) {
      if (refreshAhead) {
        e.setTask(new RefreshTimerTask<K, V>().to(cache, e));
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
     * @param e the entry
     * @return adjusted value for nextRefreshTime.
     */
    @Override
    public long stopStartTimer(long expiryTime, Entry e) {
      cancelExpiryTimer(e);
      if (expiryTime == ExpiryTimeValues.NO_CACHE) {
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
        long timerTime = -expiryTime - SAFETY_GAP_MILLIS;
        if (timerTime >= now) {
          e.setTask(new ExpireTimerTask().to(cache, e));
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
      e.setTask(new RefreshExpireTimerTask<K, V>().to(cache, e));
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
        e.setTask(new RefreshTimerTask().to(cache, e));
      } else {
        e.setTask(new ExpireTimerTask().to(cache, e));
      }
      scheduleTask(t, e);
    }

    void scheduleTask(long nextRefreshTime, Entry e) {
      SimpleTimer timer = this.timer[e.hashCode & timerMask];
      if (timer != null) {
        try {
          timer.schedule(e.getTask(), nextRefreshTime);
        } catch (IllegalStateException ignore) {
        }
      }
    }

    public void cancelExpiryTimer(Entry<K, V> e) {
      CommonTimerTask tsk = (CommonTimerTask) e.getTask();
      if (tsk != null && tsk.cancel()) {
        timerCancelCount++;
        if (timerCancelCount >= PURGE_INTERVAL) {
          synchronized (timer) {
            timer[purgeIndex].purge();
            purgeIndex = (purgeIndex + 1) & timerMask;
            timerCancelCount = 0;
          }
        }
      }
      e.setTask(null);
    }

  }

  abstract static class CommonTimerTask<K, V> extends SimpleTimerTask {
    private Entry<K, V> entry;
    private InternalCache<K, V> cache;

    CommonTimerTask<K, V> to(InternalCache<K, V> c, Entry<K, V> e) {
      cache = c;
      entry = e;
      return this;
    }

    /**
     * Null out references to avoid mem leaks, when timer is cancelled.
     */
    @Override
    public boolean cancel() {
      if (super.cancel()) {
        cache = null;
        entry = null;
        return true;
      }
      return false;
    }

    protected InternalCache<K, V> getCache() { return cache; }
    protected Entry<K, V> getEntry() { return entry; }

    public abstract void fire() throws Exception;

    public final void run() {
      try {
        fire();
      } catch (CacheClosedException ignore) {
      } catch (Throwable ex) {
        cache.logAndCountInternalException("Timer execution exception", ex);
      }
    }

  }

  private static class RefreshTimerTask<K, V> extends CommonTimerTask<K, V> {
    public void fire() {
      getCache().timerEventRefresh(getEntry(), this);
    }
  }

  private static class ExpireTimerTask<K, V> extends CommonTimerTask<K, V> {
    public void fire() {
      getCache().timerEventExpireEntry(getEntry(), this);
    }
  }

  private static class RefreshExpireTimerTask<K, V> extends CommonTimerTask<K, V> {
    public void fire() {
      getCache().timerEventProbationTerminated(getEntry(), this);
    }
  }

  private static class Dynamic<K, V> extends Static<K, V> {

    private ExpiryPolicy<K, V> expiryPolicy;

    /** Store policy factory until init is called and we have the cache reference */
    private CustomizationSupplier<ExpiryPolicy<K, V>> policyFactory;

    Dynamic(InternalClock c, Cache2kConfiguration<K, V> cc) {
      super(c, cc);
    }

    void configure(Cache2kConfiguration<K, V> c) {
      super.configure(c);
      policyFactory = c.getExpiryPolicy();
      if (policyFactory instanceof CustomizationReferenceSupplier) {
        try {
          expiryPolicy = policyFactory.supply(null);
        } catch (Exception ignore) { }
      }
      if (c.getValueType() != null &&
        ValueWithExpiryTime.class.isAssignableFrom(c.getValueType().getType()) &&
        c.getExpiryPolicy() == null)  {
        expiryPolicy =
          (ExpiryPolicy<K, V>)
            ENTRY_EXPIRY_CALCULATOR_FROM_VALUE;
      }
    }

    @Override
    public synchronized void init(InternalCache<K, V> c) {
      super.init(c);
      if (expiryPolicy == null) {
        expiryPolicy = c.createCustomization(policyFactory);
      }
      policyFactory = null;
    }

    long calcNextRefreshTime(K key, V newObject, long now, Entry entry) {
      return calcNextRefreshTime(
        key, newObject, now, entry,
        expiryPolicy, maxLinger, sharpExpiry);
    }

    public long calculateNextRefreshTime(Entry<K, V> entry, V newValue, long loadTime) {
      long t;
      if (entry.isDataAvailable() || entry.isExpiredState()
        || entry.nextRefreshTime == Entry.EXPIRED_REFRESH_PENDING) {
        t = calcNextRefreshTime(entry.getKey(), newValue, loadTime, entry);
      } else {
        t = calcNextRefreshTime(entry.getKey(), newValue, loadTime, null);
      }
      return t;
    }

    @Override
    public synchronized void close() {
      super.cancelAll();
      cache.closeCustomization(expiryPolicy, "expiryPolicy");
    }

  }

  static <K, T> long calcNextRefreshTime(
    K key, T newObject, long now, org.cache2k.core.Entry entry,
    ExpiryPolicy<K, T> ec, long maxLinger, boolean sharpExpiryEnabled) {
    if (maxLinger == 0) {
      return 0;
    }
    if (ec != null) {
      long t = ec.calculateExpiryTime(key, newObject, now, entry);
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

  public static class Tunable extends TunableConstants {

    /**
     * The number of cancelled timer tasks after that a purge is performed.
     */
    public int purgeInterval = 10000;

  }

}
