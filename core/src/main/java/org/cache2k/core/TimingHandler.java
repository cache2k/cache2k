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

import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.CacheEntry;
import org.cache2k.core.util.Util;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.expiry.ValueWithExpiryTime;
import org.cache2k.core.util.TunableConstants;
import org.cache2k.core.util.TunableFactory;
import org.cache2k.integration.ExceptionInformation;
import org.cache2k.integration.ResiliencePolicy;

import java.util.Date;
import java.util.Timer;

/**
 * Encapsulates logic for expiry times calculation and timer handling.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("unchecked")
public abstract class TimingHandler<K,V>  {

  final static int PURGE_INTERVAL = TunableFactory.get(Tunable.class).purgeInterval;
  final static long SAFETY_GAP_MILLIS = HeapCache.TUNABLE.sharpExpirySafetyGapMillis;
  final static TimingHandler ETERNAL = new Eternal();
  final static TimingHandler IMMEDIATE = new Immediate();
  final static TimingHandler ETERNAL_IMMEDIATE = new EternalImmediate();

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

  public static <K, V> TimingHandler<K,V> of(Cache2kConfiguration<K,V> cfg) {
    if (cfg.getExpireAfterWriteMillis() < 0) {
      throw new IllegalArgumentException(
        "Specify expiry or no expiry explicitly. " +
        "Either set CacheBuilder.eternal(true) or CacheBuilder.expiryDuration(...). " +
        "See: https://github.com/cache2k/cache2k/issues/21"
      );
    }
    if (cfg.getExpireAfterWriteMillis() == 0
      && zeroOrUnspecified(cfg.getRetryIntervalMillis())) {
      return IMMEDIATE;
    }
    if (cfg.getExpiryPolicy() != null
      || ValueWithExpiryTime.class.isAssignableFrom(cfg.getValueType().getType())
      || cfg.getResiliencePolicy() != null) {
      TimingHandler.Dynamic<K,V> h = new TimingHandler.Dynamic<K, V>();
      h.configure(cfg);
      return h;
    }
    if (cfg.getResilienceDurationMillis() > 0 && !cfg.isSuppressExceptions()) {
      throw new IllegalArgumentException("Ambiguous: exceptions suppression is switched off, but resilience duration is specified");
    }
    if (realDuration(cfg.getExpireAfterWriteMillis())
      || realDuration(cfg.getRetryIntervalMillis())
      || realDuration(cfg.getResilienceDurationMillis())) {
      TimingHandler.Static<K,V> h = new TimingHandler.Static<K, V>();
      h.configureStatic(cfg);
      return h;
    }
    if (cfg.getExpireAfterWriteMillis() == ExpiryPolicy.ETERNAL
      && zeroOrUnspecified(cfg.getRetryIntervalMillis())) {
      return ETERNAL_IMMEDIATE;
    }
    if ((cfg.getExpireAfterWriteMillis() == ExpiryPolicy.ETERNAL || cfg.getExpireAfterWriteMillis() == -1)
      && (cfg.getRetryIntervalMillis() == ExpiryPolicy.ETERNAL || cfg.getRetryIntervalMillis() == -1)) {
      return ETERNAL;
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
  public void shutdown() { }

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
  public boolean startRefreshProbationTimer(Entry<K,V> e, long _nextRefreshTimern) {
    return true;
  }

  /**
   * Cancel the timer on the entry, if a timer was set.
   */
  public void cancelExpiryTimer(Entry<K, V> e) { }

  /**
   * Schedule second timer event for the expiry tie if sharp expiry is switched on.
   */
  public void scheduleFinalExpiryTimer(Entry<K, V> e) { }

  static class Eternal<K,V> extends TimingHandler<K,V> {

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

  static class EternalImmediate<K,V> extends TimingHandler<K,V> {

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

  static class Immediate<K,V> extends TimingHandler<K,V> {

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

    boolean sharpExpiry;
    boolean refreshAhead;
    Timer timer;
    long maxLinger;
    InternalCache cache;
    /** Dirty counter, intentionally only 32 bit */
    int timerCancelCount = 0;
    ResiliencePolicy<K,V> resiliencePolicy;

    void configureStatic(final Cache2kConfiguration<K, V> c) {
      long _expiryMillis  = c.getExpireAfterWriteMillis();
      if (_expiryMillis == ExpiryPolicy.ETERNAL || _expiryMillis < 0) {
        maxLinger = ExpiryPolicy.ETERNAL;
      } else {
        maxLinger = _expiryMillis;
      }
      ResiliencePolicy.Context ctx = new ResiliencePolicy.Context() {
        @Override
        public long getExpireAfterWriteMillis() {
          return c.getExpireAfterWriteMillis();
        }

        @Override
        public long getResilienceDurationMillis() {
          return c.isSuppressExceptions() ? c.getResilienceDurationMillis() : 0;
        }

        @Override
        public long getRetryIntervalMillis() {
          return c.getRetryIntervalMillis();
        }

        @Override
        public long getMaxRetryIntervalMillis() {
          return c.getMaxRetryIntervalMillis();
        }
      };
      resiliencePolicy = c.getResiliencePolicy();
      if (resiliencePolicy == null) {
        resiliencePolicy = new DefaultResiliencePolicy<K, V>();
      }
      resiliencePolicy.init(ctx);
      refreshAhead = c.isRefreshAhead();
      sharpExpiry = c.isSharpExpiry();
    }

    @Override
    public synchronized void init(InternalCache<K,V> c) {
      if (cache == null) {
        cache = c;
      }
    }

    @Override
    public synchronized  void reset() {
      shutdown();
      if (timer == null) {
        timer = new Timer(cache.getName(), true);
      }
    }

    @Override
    public synchronized void shutdown() {
      if (timer != null) {
        timer.cancel();
        timer = null;
      }
    }

    @Override
    public long calculateNextRefreshTime(final Entry<K,V> e, final V v, final long _loadTime) {
      return calcNextRefreshTime(e.getKey(), v, _loadTime, e, null, maxLinger);
    }

    @Override
    public long suppressExceptionUntil(final Entry<K, V> e, final ExceptionInformation inf) {
      return resiliencePolicy.suppressExceptionUntil(e.getKey(), inf, e);
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
        e.setTask(new RefreshTask<K,V>().to(cache, e));
        scheduleTask(0, e);
        return sharpExpiry || _sharpExpiry ? Entry.EXPIRED_REFRESH_PENDING : Entry.DATA_VALID;
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
      if (_expiryTime == ExpiryTimeValues.REFRESH_IMMEDIATELY) {
        return expiredEventuallyStartBackgroundRefresh(e, false);
      }
      final long now = System.currentTimeMillis();
      if (Math.abs(_expiryTime) <= now) {
        return expiredEventuallyStartBackgroundRefresh(e, _expiryTime < 0);
      }
      long _nextRefreshTime = _expiryTime;
      if (sharpExpiry && _nextRefreshTime > Entry.EXPIRY_TIME_MIN) {
        _nextRefreshTime = -_nextRefreshTime;
      }
      if (_nextRefreshTime >= Entry.EXPIRY_TIME_MIN || _nextRefreshTime < 0) {
        if (_nextRefreshTime < 0) {
          long _timerTime =
            -_nextRefreshTime - SAFETY_GAP_MILLIS;
          if (_timerTime >= now) {
            e.setTask(new ExpireTask().to(cache, e));
            scheduleTask(_timerTime, e);
            _nextRefreshTime = -_nextRefreshTime;
          } else {
            scheduleFinalExpireWithOptionalRefresh(e, -_nextRefreshTime);
          }
        } else {
          if (refreshAhead) {
            e.setTask(new RefreshTask<K,V>().to(cache, e));
            scheduleTask(_nextRefreshTime, e);
          } else {
            e.setTask(new ExpireTask<K,V>().to(cache, e));
            scheduleTask(_nextRefreshTime, e);
          }
        }
      }
      return _nextRefreshTime;
    }

    @Override
    public boolean startRefreshProbationTimer(Entry<K,V> e, long _nextRefreshTime) {
      e.cancelTimerTask();
      if (_nextRefreshTime == ExpiryTimeValues.ETERNAL) {
        e.setNextRefreshTime(_nextRefreshTime);
        return false;
      }
      if (_nextRefreshTime > 0 && _nextRefreshTime < Entry.EXPIRY_TIME_MIN) {
        e.setNextRefreshTime(Entry.EXPIRED);
        return true;
      }
      e.setRefreshProbationNextRefreshTime(_nextRefreshTime);
      e.setNextRefreshTime(Entry.EXPIRED_REFRESHED);
      e.setTask(new RefreshExpireTask<K,V>().to(cache, e));
      scheduleTask(Math.abs(_nextRefreshTime), e);
      return false;
    }

    @Override
    public void scheduleFinalExpiryTimer(final Entry<K, V> e) {
      cancelExpiryTimer(e);
      scheduleFinalExpireWithOptionalRefresh(e, e.getNextRefreshTime());
    }

    /**
     * If sharp and refresh is requested, we schedule a refresh task at the some time.
     * The refresh should start just when the value expired.
     */
    void scheduleFinalExpireWithOptionalRefresh(final Entry<K, V> e, long t) {
      e.setTask(new ExpireTask().to(cache, e));
      scheduleTask(t, e);
      if (sharpExpiry && refreshAhead) {
        e.setTask(new RefreshTask().to(cache, e));
        scheduleTask(t, e);
      }
    }

    void scheduleTask(final long _nextRefreshTime, final Entry e) {
      Timer _timer = timer;
      if (_timer != null) {
        try {
          _timer.schedule(e.getTask(), new Date(_nextRefreshTime));
        } catch (IllegalStateException ignore) {
        }
      }
    }

    public void cancelExpiryTimer(Entry<K, V> e) {
      if (e.cancelTimerTask()) {
        timerCancelCount++;
        if (timerCancelCount >= PURGE_INTERVAL) {
          timer.purge();
          timerCancelCount = 0;
        }
      }
    }

  }

  static abstract class CommonTask<K,V> extends java.util.TimerTask {
    Entry<K,V> entry;
    InternalCache<K,V> cache;

    CommonTask<K,V> to(final InternalCache<K,V> c, final Entry<K, V> e) {
      cache = c;
      entry = e;
      return this;
    }

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

  static class RefreshTask<K,V> extends CommonTask<K,V> {
    public void fire() {
      cache.timerEventRefresh(entry);
    }
  }

  static class ExpireTask<K,V> extends CommonTask<K,V> {
    public void fire() {
      cache.timerEventExpireEntry(entry);
    }
  }

  static class RefreshExpireTask<K,V> extends CommonTask<K,V> {
    public void fire() {
      cache.timerEventProbationTerminated(entry);
    }
  }

  static class Dynamic<K,V> extends Static<K,V> {

    ExpiryPolicy<K, V> expiryPolicy;

    @SuppressWarnings("unchecked")
    void configure(Cache2kConfiguration<K,V> c) {
      configureStatic(c);
      expiryPolicy = c.getExpiryPolicy();
      if (c.getValueType() != null &&
        ValueWithExpiryTime.class.isAssignableFrom(c.getValueType().getType()) &&
        expiryPolicy == null)  {
        expiryPolicy =
          (ExpiryPolicy<K, V>)
            ENTRY_EXPIRY_CALCULATOR_FROM_VALUE;
      }
    }

    long calcNextRefreshTime(K _key, V _newObject, long now, Entry _entry) {
      return calcNextRefreshTime(
        _key, _newObject, now, _entry,
        expiryPolicy, maxLinger);
    }

    public long calculateNextRefreshTime(Entry<K, V> _entry, V _newValue, long _loadTime) {
      if (_entry.isDataValid() || _entry.isExpired()) {
        return calcNextRefreshTime(_entry.getKey(), _newValue, _loadTime, _entry);
      } else {
        return calcNextRefreshTime(_entry.getKey(), _newValue, _loadTime, null);
      }
    }

  }

  static <K, T>  long calcNextRefreshTime(
    K _key, T _newObject, long now, org.cache2k.core.Entry _entry,
    ExpiryPolicy<K, T> ec, long _maxLinger) {
    if (_maxLinger == 0) {
      return 0;
    }
    if (ec != null) {
      long t = ec.calculateExpiryTime(_key, _newObject, now, _entry);
      return limitExpiryToMaxLinger(now, _maxLinger, t);
    }
    if (_maxLinger < ExpiryPolicy.ETERNAL) {
      return _maxLinger + now;
    }
    return _maxLinger;
  }

  static long limitExpiryToMaxLinger(long now, long _maxLinger, long t) {
    if (_maxLinger > 0 && _maxLinger < ExpiryPolicy.ETERNAL) {
      long _tMaximum = _maxLinger + now;
      if (t > _tMaximum) {
        return _tMaximum;
      }
      if (t < -1 && -t > _tMaximum) {
        return -_tMaximum;
      }
    }
    return t;
  }

  public static class Tunable extends TunableConstants {

    /**
     * The number of cancelled timer tasks after that a purge is performed.
     */
    public int purgeInterval = 10000;

  }

}
