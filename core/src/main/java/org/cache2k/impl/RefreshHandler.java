package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import org.cache2k.CacheConfig;
import org.cache2k.CacheEntry;
import org.cache2k.customization.ExpiryCalculator;
import org.cache2k.customization.ExceptionExpiryCalculator;
import org.cache2k.customization.ValueWithExpiryTime;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Encapsulates logic for expiry and refresh calculation and timer handling.
 *
 * @author Jens Wilke
 */
public abstract class RefreshHandler<K,V>  {

  final static long SAFETY_GAP_MILLIS = BaseCache.TUNABLE.sharpExpirySafetyGapMillis;
  final static RefreshHandler ETERNAL = new Eternal();
  final static RefreshHandler IMMEDIATE = new Immediate();

  /**
   * Instance of expiry calculator that extracts the expiry time from the value.
   */
  final static ExpiryCalculator<?, ValueWithExpiryTime> ENTRY_EXPIRY_CALCULATOR_FROM_VALUE = new
    ExpiryCalculator<Object, ValueWithExpiryTime>() {
      @Override
      public long calculateExpiryTime(
        Object _key, ValueWithExpiryTime _value, long _loadTime,
        CacheEntry<Object, ValueWithExpiryTime> _oldEntry) {
        return _value.getCacheExpiryTime();
      }
    };

  public static <K, V> RefreshHandler<K,V> of(CacheConfig<K,V> cfg) {
    if (cfg.getExceptionExpiryCalculator() != null || cfg.getExpiryCalculator() != null ||
      ValueWithExpiryTime.class.isAssignableFrom(cfg.getValueType().getType())) {
      RefreshHandler.Dynamic<K,V> h = new RefreshHandler.Dynamic<K, V>();
      h.configure(cfg);
      return h;
    }
    if ((cfg.getExpiryMillis() > 0 && cfg.getExpiryMillis() < Long.MAX_VALUE) ||
        (cfg.getExceptionExpiryMillis() > 0 && cfg.getExceptionExpiryMillis() < Long.MAX_VALUE) ) {
      RefreshHandler.Static<K,V> h = new RefreshHandler.Static<K, V>();
      h.configureStatic(cfg);
      return h;
    }
    if (cfg.getExpiryMillis() == 0 &&
      (cfg.getExceptionExpiryMillis() == 0 || cfg.getExceptionExpiryMillis() == -1)) {
      return IMMEDIATE;
    }
    if ((cfg.getExpiryMillis() == ExpiryCalculator.ETERNAL || cfg.getExceptionExpiryMillis() == -1) &&
      (cfg.getExceptionExpiryMillis() == ExpiryCalculator.ETERNAL || cfg.getExceptionExpiryMillis() == -1)) {
      return ETERNAL;
    }
    throw new IllegalArgumentException("expiry time ambiguous");
  }

  public void init(InternalCache<K,V> c) { }

  public void shutdown() { }

  public abstract long calculateNextRefreshTime(Entry<K, V> e, V v, long t0);

  public abstract long stopStartTimer(long _nextRefreshTime, Entry<K,V> e);

  public abstract void cancelExpiryTimer(Entry<K, V> e);

  static class Eternal<K,V> extends RefreshHandler<K,V> {

    @Override
    public void shutdown() { }

    @Override
    public long calculateNextRefreshTime(final Entry<K,V> e, final V v, final long t0) {
      return Long.MAX_VALUE;
    }

    @Override
    public long stopStartTimer(final long _nextRefreshTime, final Entry<K,V> e) {
      return _nextRefreshTime;
    }

    @Override
    public void cancelExpiryTimer(final Entry e) { }

  }

  static class Immediate<K,V> extends RefreshHandler<K,V> {

    @Override
    public long calculateNextRefreshTime(final Entry<K,V> e, final V _o, final long t0) {
      return 0;
    }

    @Override
    public long stopStartTimer(final long _nextRefreshTime, final Entry<K,V> e) {
      return _nextRefreshTime;
    }

    @Override
    public void cancelExpiryTimer(final Entry e) { }

  }

  static class Static<K,V> extends RefreshHandler<K,V> {

    boolean sharpTimeout;
    boolean backgroundRefresh;
    Timer timer;
    long maxLinger =  10 * 60 * 1000;
    long exceptionMaxLinger = 1 * 60 * 1000;
    InternalCache cache;
    long timerCancelCount = 0;

    void configureStatic(final CacheConfig<K, V> c) {
      long _expiryMillis  = c.getExpiryMillis();
      if (_expiryMillis == ExpiryCalculator.ETERNAL || _expiryMillis < 0) {
        maxLinger = ExpiryCalculator.ETERNAL;
      } else if (_expiryMillis >= 0) {
        maxLinger = _expiryMillis;
      }
      long _exceptionExpiryMillis = c.getExceptionExpiryMillis();
      if (_exceptionExpiryMillis == -1) {
        if (maxLinger == ExpiryCalculator.ETERNAL) {
          exceptionMaxLinger = ExpiryCalculator.ETERNAL;
        } else {
          exceptionMaxLinger = maxLinger / 10;
        }
      } else {
        exceptionMaxLinger = _exceptionExpiryMillis;
      }
      backgroundRefresh = c.isBackgroundRefresh();
      sharpTimeout = c.isSharpExpiry();
    }

    boolean isNeedingTimer() {
      return
        maxLinger > 0 || exceptionMaxLinger > 0;
    }

    @Override
    public synchronized void init(InternalCache<K,V> c) {
      cache = c;
      if (isNeedingTimer()) {
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
    public long calculateNextRefreshTime(final Entry<K,V> e, final V _o, final long t0) {
      if (_o instanceof ExceptionWrapper) {
        if (exceptionMaxLinger == ExpiryCalculator.ETERNAL || exceptionMaxLinger == 0) {
          return exceptionMaxLinger;
        }
        return exceptionMaxLinger + t0;
      }
      if (maxLinger == ExpiryCalculator.ETERNAL || maxLinger == 0) {
        return maxLinger;
      }
      return maxLinger + t0;
    }

    @Override
    public long stopStartTimer(long _nextRefreshTime, final Entry e) {
      final long now = System.currentTimeMillis();
      TimerTask _task = e.task;
      if (_task != null) {
        _task.cancel();
      }
      if ((_nextRefreshTime > Entry.EXPIRY_TIME_MIN && _nextRefreshTime <= now) &&
        (_nextRefreshTime < -1 && (now >= -_nextRefreshTime))) {
        return Entry.EXPIRED;
      }
      if (sharpTimeout && _nextRefreshTime > Entry.EXPIRY_TIME_MIN && _nextRefreshTime != Long.MAX_VALUE) {
        _nextRefreshTime = -_nextRefreshTime;
      }
      if (timer != null &&
        (_nextRefreshTime > Entry.EXPIRY_TIME_MIN || _nextRefreshTime < -1)) {
        if (_nextRefreshTime < -1) {
          long _timerTime =
            -_nextRefreshTime - SAFETY_GAP_MILLIS;
          if (_timerTime >= now) {
            e.task = new ExpireTask(cache, e);
            scheduleTask(_timerTime, e);
            _nextRefreshTime = -_nextRefreshTime;
          } else {
          }
        } else {
          if (backgroundRefresh) {
            e.task = new RefreshTask<K,V>(cache, e);
            scheduleTask(_nextRefreshTime, e);
          } else {
            e.task = new ExpireTask(cache, e);
            scheduleTask(_nextRefreshTime, e);
          }
        }
      } else {
      }
      return _nextRefreshTime;
    }

    void scheduleTask(final long _nextRefreshTime, final Entry e) {
      Timer _timer = timer;
      if (_timer != null) {
        try {
          _timer.schedule(e.task, new Date(_nextRefreshTime));
        } catch (IllegalStateException ignore) {
        }
      }
    }

    public void cancelExpiryTimer(Entry<K, V> e) {
      TimerTask _task = e.task;
      if (_task != null) {
        if (_task.cancel()) {
          timerCancelCount++;
          if (timerCancelCount >= 10000) {
            timer.purge();
            timerCancelCount = 0;
          }
        }
      }
    }

  }

  static class RefreshTask<K,V> extends java.util.TimerTask {
    Entry<K,V> entry;
    InternalCache cache;

    public RefreshTask(final InternalCache _cache, final Entry<K, V> _entry) {
      cache = _cache;
      entry = _entry;
    }

    public void run() {
      cache.timerEventRefresh(entry);
    }
  }

  static class ExpireTask<K,V> extends java.util.TimerTask {
    Entry<K,V> entry;
    InternalCache cache;

    public ExpireTask(final InternalCache _cache, final Entry<K, V> _entry) {
      cache = _cache;
      entry = _entry;
    }

    public void run() {
      cache.timerEventExpireEntry(entry);
    }
  }

  static class Dynamic<K,V> extends Static<K,V> {

    ExpiryCalculator<K, V> expiryCalculator;
    ExceptionExpiryCalculator<K> exceptionExpiryCalculator;

    @SuppressWarnings("unchecked")
    void configure(CacheConfig<K,V> c) {
      configureStatic(c);
      expiryCalculator = c.getExpiryCalculator();
      exceptionExpiryCalculator = c.getExceptionExpiryCalculator();
      if (ValueWithExpiryTime.class.isAssignableFrom(c.getValueType().getType()) &&
        expiryCalculator == null)  {
        expiryCalculator =
          (ExpiryCalculator<K, V>)
            ENTRY_EXPIRY_CALCULATOR_FROM_VALUE;
      }
    }

    @Override
    boolean isNeedingTimer() {
      return super.isNeedingTimer() ||
         expiryCalculator != null || exceptionExpiryCalculator != null;
    }

    long calcNextRefreshTime(K _key, V _newObject, long now, Entry _entry) {
      return calcNextRefreshTime(
        _key, _newObject, now, _entry,
        expiryCalculator, maxLinger,
        exceptionExpiryCalculator, exceptionMaxLinger);
    }

    public long calculateNextRefreshTime(Entry<K, V> _entry, V _newValue, long now) {
      if (_entry.isDataValid() || _entry.isExpired()) {
        return calcNextRefreshTime(_entry.getKey(), _newValue, now, _entry);
      } else {
        return calcNextRefreshTime(_entry.getKey(), _newValue, now, null);
      }
    }

  }

  /**
   * Time when the element should be fetched again from the underlying storage.
   * If 0 then the object should not be cached at all. -1 means no expiry.
   *
   * @param _newObject might be a fetched value or an exception wrapped into the {@link ExceptionWrapper}
   */
  static <K, T>  long calcNextRefreshTime(
    K _key, T _newObject, long now, org.cache2k.impl.Entry _entry,
    ExpiryCalculator<K, T> ec, long _maxLinger,
    ExceptionExpiryCalculator<K> _exceptionEc, long _exceptionMaxLinger) {
    if (!(_newObject instanceof ExceptionWrapper)) {
      if (_maxLinger == 0) {
        return 0;
      }
      if (ec != null) {
        long t = ec.calculateExpiryTime(_key, _newObject, now, _entry);
        return limitExpiryToMaxLinger(now, _maxLinger, t);
      }
      if (_maxLinger < ExpiryCalculator.ETERNAL) {
        return _maxLinger + now;
      }
      return _maxLinger;
    }
    if (_exceptionMaxLinger == 0) {
      return 0;
    }
    if (_exceptionEc != null) {
      ExceptionWrapper _wrapper = (ExceptionWrapper) _newObject;
      long t = _exceptionEc.calculateExpiryTime(_key, _wrapper.getException(), now);
      t = limitExpiryToMaxLinger(now, _exceptionMaxLinger, t);
      return t;
    }
    if (_exceptionMaxLinger < ExpiryCalculator.ETERNAL) {
      return _exceptionMaxLinger + now;
    } else {
      return _exceptionMaxLinger;
    }
  }

  static long limitExpiryToMaxLinger(long now, long _maxLinger, long t) {
    if (_maxLinger > 0 && _maxLinger < ExpiryCalculator.ETERNAL) {
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

}
