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
@SuppressWarnings("unchecked")
public abstract class TimingHandler<K,V>  {

  /** Used as default */
  final static TimingHandler ETERNAL = new Eternal();
  final static TimingHandler ETERNAL_IMMEDIATE = new EternalImmediate();

  private final static TimingHandler IMMEDIATE = new Immediate();
  private final static int PURGE_INTERVAL = TunableFactory.get(Tunable.class).purgeInterval;
  private final static long SAFETY_GAP_MILLIS = HeapCache.TUNABLE.sharpExpirySafetyGapMillis;

  /**
   * Instance of expiry calculator that extracts the expiry time from the value.
   */
  final static ExpiryPolicy<?, ValueWithExpiryTime> ENTRY_EXPIRY_CALCULATOR_FROM_VALUE =
    new ExpiryPolicy<Object, ValueWithExpiryTime>() {
      @Override
      public long calculateExpiryTime(
        Object _key, ValueWithExpiryTime _value, long _loadTime,
        CacheEntry<Object, ValueWithExpiryTime> _oldEntry) {
        return _value.getCacheExpiryTime();
      }
    };

  static boolean realDuration(long t) {
    return t > 0 && t < Long.MAX_VALUE;
  }

  static boolean zeroOrUnspecified(long t) {
    return t == 0 || t == -1;
  }

  public static <K, V> TimingHandler<K,V> of(InternalClock _clock, Cache2kConfiguration<K,V> cfg) {
    if (cfg.getExpireAfterWrite() == 0
      && zeroOrUnspecified(cfg.getRetryInterval())) {
      return IMMEDIATE;
    }
    if (cfg.getExpiryPolicy() != null
      || (cfg.getValueType() != null && ValueWithExpiryTime.class.isAssignableFrom(cfg.getValueType().getType()))
      || cfg.getResiliencePolicy() != null) {
      TimingHandler.Dynamic<K,V> h = new TimingHandler.Dynamic<K, V>(_clock, cfg);
      return h;
    }
    if (cfg.getResilienceDuration() > 0 && !cfg.isSuppressExceptions()) {
      throw new IllegalArgumentException("Ambiguous: exceptions suppression is switched off, but resilience duration is specified");
    }
    if (realDuration(cfg.getExpireAfterWrite())
      || realDuration(cfg.getRetryInterval())
      || realDuration(cfg.getResilienceDuration())) {
      TimingHandler.Static<K,V> h = new TimingHandler.Static<K, V>(_clock, cfg);
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
  public void init(InternalCache<K,V> c) { }

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
   * Calculates the expiry time for a value that was just loaded or inserted into the cache.
   *
   * @param e The entry, filled with the previous value if there is a value present alreay.
   * @param v The new value or an exception wrapped in {@link ExceptionWrapper}
   * @param _loadTime the time immediately before the load started
   * @return Point in time when the entry should expire. Meaning identical to
   *         {@link ExpiryPolicy#calculateExpiryTime(Object, Object, long, CacheEntry)}
   */
  public abstract long calculateNextRefreshTime(Entry<K, V> e, V v, long _loadTime);

  /**
   * Delegated to the resilience policy
   *
   * @see ResiliencePolicy#suppressExceptionUntil
   */
  public abstract long suppressExceptionUntil(Entry<K,V> e, ExceptionInformation inf);

  /**
   * Delegated to the resilience policy
   *
   * @see ResiliencePolicy#retryLoadAfter
   */
  public abstract long cacheExceptionUntil(Entry<K,V> e, ExceptionInformation inf);

  /**
   * Convert expiry value to the entry field value, essentially maps 0 to {@link Entry#EXPIRED}
   * since 0 is a virgin entry. Restart the timer if needed.
   *
   * @param _expiryTime calculated expiry time
   * @return sanitized nextRefreshTime for storage in the entry.
   */
  public long stopStartTimer(long _expiryTime, Entry<K,V> e) {
    if ((_expiryTime > 0 && _expiryTime < Long.MAX_VALUE) || _expiryTime < 0) {
      throw new IllegalArgumentException("invalid expiry time, cache is not initialized with expiry: " + Util.formatMillis(_expiryTime));
    }
    return _expiryTime == 0 ? Entry.EXPIRED : _expiryTime;
  }

  /**
   * Start timer for expiring an entry on the separate refresh hash.
   */
  public boolean startRefreshProbationTimer(Entry<K,V> e, long _nextRefreshTime) {
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
  static abstract class TimeAgnostic<K,V> extends TimingHandler<K,V> {

  }

  private static class Eternal<K,V> extends TimeAgnostic<K,V> {

    @Override
    public long calculateNextRefreshTime(final Entry<K,V> e, final V v, final long _loadTime) {
      return ExpiryPolicy.ETERNAL;
    }

    @Override
    public long cacheExceptionUntil(final Entry<K, V> e, final ExceptionInformation inf) {
      return ExpiryPolicy.ETERNAL;
    }

    @Override
    public long suppressExceptionUntil(final Entry<K, V> e, final ExceptionInformation inf) {
      return ExpiryPolicy.ETERNAL;
    }
  }

  static class EternalImmediate<K,V> extends TimeAgnostic<K,V> {

    @Override
    public long calculateNextRefreshTime(final Entry<K,V> e, final V v, final long _loadTime) {
      return ExpiryPolicy.ETERNAL;
    }

    @Override
    public long cacheExceptionUntil(final Entry<K, V> e, final ExceptionInformation inf) {
      return 0;
    }

    @Override
    public long suppressExceptionUntil(final Entry<K, V> e, final ExceptionInformation inf) {
      return 0;
    }

  }

  static class Immediate<K,V> extends TimeAgnostic<K,V> {

    @Override
    public long calculateNextRefreshTime(final Entry<K,V> e, final V v, final long _loadTime) {
      return 0;
    }

    @Override
    public long cacheExceptionUntil(final Entry<K, V> e, final ExceptionInformation inf) {
      return 0;
    }

    @Override
    public long suppressExceptionUntil(final Entry<K, V> e, final ExceptionInformation inf) {
      return 0;
    }
  }

  static class Static<K,V> extends TimingHandler<K,V> {

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
    ResiliencePolicy<K,V> resiliencePolicy;
    CustomizationSupplier<ResiliencePolicy<K,V>> resiliencePolicyFactory;

    public Static(InternalClock c, final Cache2kConfiguration<K, V> cc) {
      clock = c;
      configure(cc);
    }

    void configure(final Cache2kConfiguration<K, V> c) {
      long _expiryMillis  = c.getExpireAfterWrite();
      if (_expiryMillis == ExpiryPolicy.ETERNAL || _expiryMillis < 0) {
        maxLinger = ExpiryPolicy.ETERNAL;
      } else {
        maxLinger = _expiryMillis;
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
      int _timerCount = 1;
      if (c.isBoostConcurrency()) {
        int _ncpu = Runtime.getRuntime().availableProcessors();
        _timerCount = 2 << (31 - Integer.numberOfLeadingZeros(_ncpu));
      }
      timer = new SimpleTimer[_timerCount];
      timerMask = _timerCount - 1;
    }

    @Override
    public synchronized void init(InternalCache<K,V> c) {
      cache = c;
      if (resiliencePolicy == null) {
        resiliencePolicy = c.createCustomization(resiliencePolicyFactory);
      }
      resiliencePolicyFactory = null;
    }

    @Override
    public synchronized  void reset() {
      cancelAll();
      for (int i = 0; i <= timerMask; i++) {
        if (timer[i] != null) { continue; }
        timer[i] =  new SimpleTimer(clock, cache.getName(), true);
      }
    }

    @Override
    public synchronized void cancelAll() {
      SimpleTimer _timer;
      for (int i = 0; i <= timerMask; i++) {
        if ((_timer = timer[i]) == null) { continue; }
        _timer.cancel();
        timer[i] = null;
      }
    }

    @Override
    public long calculateNextRefreshTime(final Entry<K,V> e, final V v, final long _loadTime) {
      return calcNextRefreshTime(e.getKey(), v, _loadTime, e, null, maxLinger, sharpExpiry);
    }

    @Override
    public long suppressExceptionUntil(final Entry<K, V> e, final ExceptionInformation inf) {
      return resiliencePolicy.suppressExceptionUntil(
        e.getKey(), inf, cache.returnCacheEntry(e));
    }

    @Override
    public long cacheExceptionUntil(final Entry<K, V> e, final ExceptionInformation inf) {
      return resiliencePolicy.retryLoadAfter(e.getKey(), inf);
    }

    /**
     * If we are about to start the timer, but discover that the entry is
     * expired already, we need to start the refresh task.
     * This will also start a refresh task, if the entry just was refreshed and it is
     * expired immediately. The refresh task will handle this and expire the entry.
     */
    long expiredEventuallyStartBackgroundRefresh(final Entry e, boolean _sharpExpiry) {
      if (refreshAhead) {
        e.setTask(new RefreshTimerTask<K,V>().to(cache, e));
        scheduleTask(0, e);
        return _sharpExpiry ? Entry.EXPIRED_REFRESH_PENDING : Entry.DATA_VALID;
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
     * @param _expiryTime expiry time with special values as defined in {@link ExpiryTimeValues}
     * @param e the entry
     * @return adjusted value for nextRefreshTime.
     */
    @Override
    public long stopStartTimer(long _expiryTime, final Entry e) {
      cancelExpiryTimer(e);
      if (_expiryTime == ExpiryTimeValues.NO_CACHE) {
        return Entry.EXPIRED;
      }
      if (_expiryTime == ExpiryTimeValues.NEUTRAL) {
        long nrt = e.getNextRefreshTime();
        if (nrt == 0) {
          throw new IllegalArgumentException("neutral expiry not allowed for creation");
        }
        return e.getNextRefreshTime();
      }
      if (_expiryTime == ExpiryTimeValues.ETERNAL) {
        return _expiryTime;
      }
      if (_expiryTime == ExpiryTimeValues.REFRESH) {
        return expiredEventuallyStartBackgroundRefresh(e, false);
      }
      final long now = clock.millis();
      if (Math.abs(_expiryTime) <= now) {
        return expiredEventuallyStartBackgroundRefresh(e, _expiryTime < 0);
      }
      if (_expiryTime < 0) {
        long _timerTime = -_expiryTime - SAFETY_GAP_MILLIS;
        if (_timerTime >= now) {
          e.setTask(new ExpireTimerTask().to(cache, e));
          scheduleTask(_timerTime, e);
          _expiryTime = -_expiryTime;
        } else {
          scheduleFinalExpireWithOptionalRefresh(e, -_expiryTime);
        }
      } else {
        scheduleFinalExpireWithOptionalRefresh(e, _expiryTime);
      }
      return _expiryTime;
    }

    @Override
    public boolean startRefreshProbationTimer(Entry<K,V> e, long _nextRefreshTime) {
      cancelExpiryTimer(e);
      if (_nextRefreshTime == ExpiryTimeValues.ETERNAL) {
        e.setNextRefreshTime(_nextRefreshTime);
        return false;
      }
      if (_nextRefreshTime > 0 && _nextRefreshTime < Entry.EXPIRY_TIME_MIN) {
        e.setNextRefreshTime(Entry.EXPIRED);
        return true;
      }
      long _absTime = Math.abs(_nextRefreshTime);
      e.setRefreshProbationNextRefreshTime(_absTime);
      e.setNextRefreshTime(Entry.EXPIRED_REFRESHED);
      e.setTask(new RefreshExpireTimerTask<K,V>().to(cache, e));
      scheduleTask(_absTime, e);
      return false;
    }

    @Override
    public void scheduleFinalTimerForSharpExpiry(final Entry<K, V> e) {
      cancelExpiryTimer(e);
      scheduleFinalExpireWithOptionalRefresh(e, e.getNextRefreshTime());
    }

    /**
     * Sharp expiry is requested: Either schedule refresh or expiry.
     */
    void scheduleFinalExpireWithOptionalRefresh(final Entry<K, V> e, long t) {
      if (refreshAhead) {
        e.setTask(new RefreshTimerTask().to(cache, e));
      } else {
        e.setTask(new ExpireTimerTask().to(cache, e));
      }
      scheduleTask(t, e);
    }

    void scheduleTask(final long _nextRefreshTime, final Entry e) {
      SimpleTimer _timer = timer[e.hashCode & timerMask];
      if (_timer != null) {
        try {
          _timer.schedule(e.getTask(), _nextRefreshTime);
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

  static abstract class CommonTimerTask<K,V> extends SimpleTimerTask {
    private Entry<K,V> entry;
    private InternalCache<K,V> cache;

    CommonTimerTask<K,V> to(final InternalCache<K,V> c, final Entry<K, V> e) {
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

    protected InternalCache<K,V> getCache() { return cache; }
    protected Entry<K,V> getEntry() { return entry; }

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

  private static class RefreshTimerTask<K,V> extends CommonTimerTask<K,V> {
    public void fire() {
      getCache().timerEventRefresh(getEntry(), this);
    }
  }

  private static class ExpireTimerTask<K,V> extends CommonTimerTask<K,V> {
    public void fire() {
      getCache().timerEventExpireEntry(getEntry(), this);
    }
  }

  private static class RefreshExpireTimerTask<K,V> extends CommonTimerTask<K,V> {
    public void fire() {
      getCache().timerEventProbationTerminated(getEntry(), this);
    }
  }

  private static class Dynamic<K,V> extends Static<K,V> {

    private ExpiryPolicy<K, V> expiryPolicy;

    /** Store policy factory until init is called and we have the cache reference */
    private CustomizationSupplier<ExpiryPolicy<K, V>> policyFactory;

    public Dynamic(InternalClock c, final Cache2kConfiguration<K, V> cc) {
      super(c, cc);
    }

    void configure(Cache2kConfiguration<K,V> c) {
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
    public synchronized void init(final InternalCache<K, V> c) {
      super.init(c);
      if (expiryPolicy == null) {
        expiryPolicy = c.createCustomization(policyFactory);
      }
      policyFactory = null;
    }

    long calcNextRefreshTime(K _key, V _newObject, long now, Entry _entry) {
      return calcNextRefreshTime(
        _key, _newObject, now, _entry,
        expiryPolicy, maxLinger, sharpExpiry);
    }

    public long calculateNextRefreshTime(Entry<K, V> _entry, V _newValue, long _loadTime) {
      long t;
      if (_entry.isDataAvailable() || _entry.isExpiredState() || _entry.nextRefreshTime == Entry.EXPIRED_REFRESH_PENDING) {
        t = calcNextRefreshTime(_entry.getKey(), _newValue, _loadTime, _entry);
      } else {
        t = calcNextRefreshTime(_entry.getKey(), _newValue, _loadTime, null);
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
    K _key, T _newObject, long now, org.cache2k.core.Entry _entry,
    ExpiryPolicy<K, T> ec, long _maxLinger, boolean _sharpExpiryEnabled) {
    if (_maxLinger == 0) {
      return 0;
    }
    if (ec != null) {
      long t = ec.calculateExpiryTime(_key, _newObject, now, _entry);
      return limitExpiryToMaxLinger(now, _maxLinger, t, _sharpExpiryEnabled);
    }
    if (_maxLinger < ExpiryPolicy.ETERNAL) {
      long t = _maxLinger + now;
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
  static long limitExpiryToMaxLinger(long now, long _maxLinger, long _requestedExpiryTime, boolean _sharpExpiryEnabled) {
    if (_sharpExpiryEnabled && _requestedExpiryTime > ExpiryPolicy.REFRESH && _requestedExpiryTime < ExpiryPolicy.ETERNAL) {
      _requestedExpiryTime = -_requestedExpiryTime;
    }
    return Expiry.mixTimeSpanAndPointInTime(now, _maxLinger, _requestedExpiryTime);
  }

  public static class Tunable extends TunableConstants {

    /**
     * The number of cancelled timer tasks after that a purge is performed.
     */
    public int purgeInterval = 10000;

  }

}
