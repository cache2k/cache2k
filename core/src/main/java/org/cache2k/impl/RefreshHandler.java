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
import org.cache2k.EntryExpiryCalculator;
import org.cache2k.ExceptionExpiryCalculator;
import org.cache2k.ValueWithExpiryTime;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Encapsulates logic for expiry and refresh calculation and timer handling.
 *
 * @author Jens Wilke
 */
public abstract class RefreshHandler<K,V>  {

  /** Millis value meaning eternal, this is Long.MAX_VALUE */
  public final static long ETERNAL_MILLIS = Long.MAX_VALUE;

  final static long SAFETY_GAP_MILLIS = BaseCache.TUNABLE.sharpExpirySafetyGapMillis;
  final static RefreshHandler ETERNAL = new Eternal();
  final static RefreshHandler IMMEDIATE = new Immediate();

  /**
   * Instance of expiry calculator that extracts the expiry time from the value.
   */
  final static EntryExpiryCalculator<?, ValueWithExpiryTime> ENTRY_EXPIRY_CALCULATOR_FROM_VALUE = new
    EntryExpiryCalculator<Object, ValueWithExpiryTime>() {
      @Override
      public long calculateExpiryTime(
        Object _key, ValueWithExpiryTime _value, long _loadTime,
        CacheEntry<Object, ValueWithExpiryTime> _oldEntry) {
        return _value.getCacheExpiryTime();
      }
    };

  static <K, V> RefreshHandler<K,V> of(InternalCache<K,V> _cache, CacheConfig<K,V> _cfg) {
    RefreshHandler.Dynamic<K,V> h = new RefreshHandler.Dynamic<K, V>(_cache);
    h.configure(_cfg);
    return h;
  }

  public void init() { }

  public void shutdown() { }

  public abstract long calculateNextRefreshTime(Entry<K, V> e, V v, long t0);

  public abstract long stopStartTimer(long _nextRefreshTime, Entry<K,V> e, long now);

  public abstract void cancelExpiryTimer(Entry<K, V> e);

  static class Eternal<K,V> extends RefreshHandler<K,V> {

    @Override
    public void init() { }

    @Override
    public void shutdown() { }

    @Override
    public long calculateNextRefreshTime(final Entry<K,V> e, final V v, final long t0) {
      return Long.MAX_VALUE;
    }

    @Override
    public long stopStartTimer(final long _nextRefreshTime, final Entry<K,V> e, final long now) {
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
    public long stopStartTimer(final long _nextRefreshTime, final Entry<K,V> e, final long now) {
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

    public Static() {
    }

    public Static(final InternalCache _cache) {
      cache = _cache;
    }

    boolean isNeedingTimer() {
      return
        maxLinger > 0 || exceptionMaxLinger > 0;
    }

    @Override
    public synchronized void init() {
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
      return maxLinger + t0;
    }

    @Override
    public long stopStartTimer(long _nextRefreshTime, final Entry e, final long now) {
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

    EntryExpiryCalculator<K, V> entryExpiryCalculator;
    ExceptionExpiryCalculator<K> exceptionExpiryCalculator;

    public Dynamic(final InternalCache _cache) {
      super(_cache);
    }

    @SuppressWarnings("unchecked")
    public void configure(CacheConfig<K,V> c) {
      long _expiryMillis  = c.getExpiryMillis();
      if (_expiryMillis == ETERNAL_MILLIS || _expiryMillis < 0) {
        maxLinger = ETERNAL_MILLIS;
      } else if (_expiryMillis >= 0) {
        maxLinger = _expiryMillis;
      }
      long _exceptionExpiryMillis = c.getExceptionExpiryMillis();
      if (_exceptionExpiryMillis == -1) {
        if (maxLinger == ETERNAL_MILLIS) {
          exceptionMaxLinger = ETERNAL_MILLIS;
        } else {
          exceptionMaxLinger = maxLinger / 10;
        }
      } else {
        exceptionMaxLinger = _exceptionExpiryMillis;
      }
      backgroundRefresh = c.isBackgroundRefresh();
      sharpTimeout = c.isSharpExpiry();
      if (ValueWithExpiryTime.class.isAssignableFrom(c.getValueType().getType()) &&
        entryExpiryCalculator == null)  {
        entryExpiryCalculator =
          (EntryExpiryCalculator<K, V>)
            ENTRY_EXPIRY_CALCULATOR_FROM_VALUE;
      }
    }

    @Override
    boolean isNeedingTimer() {
      return super.isNeedingTimer() ||
         entryExpiryCalculator != null || exceptionExpiryCalculator != null;
    }

    long calcNextRefreshTime(K _key, V _newObject, long now, Entry _entry) {
      return calcNextRefreshTime(
        _key, _newObject, now, _entry,
        entryExpiryCalculator, maxLinger,
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
    EntryExpiryCalculator<K, T> ec, long _maxLinger,
    ExceptionExpiryCalculator<K> _exceptionEc, long _exceptionMaxLinger) {
    if (!(_newObject instanceof ExceptionWrapper)) {
      if (_maxLinger == 0) {
        return 0;
      }
      if (ec != null) {
        long t = ec.calculateExpiryTime(_key, _newObject, now, _entry);
        return limitExpiryToMaxLinger(now, _maxLinger, t);
      }
      if (_maxLinger < ETERNAL_MILLIS) {
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
    if (_exceptionMaxLinger < ETERNAL_MILLIS) {
      return _exceptionMaxLinger + now;
    } else {
      return _exceptionMaxLinger;
    }
  }

  static long limitExpiryToMaxLinger(long now, long _maxLinger, long t) {
    if (_maxLinger > 0 && _maxLinger < ETERNAL_MILLIS) {
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
