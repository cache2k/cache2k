package org.cache2k.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.core.util.TunableFactory;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.integration.CacheLoader;
import org.cache2k.testing.category.FastTests;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@SuppressWarnings("unchecked")
@Category(FastTests.class)
public class TimingHandlerTest {

  private final Entry ENTRY = new Entry();
  private final long NOW = 10000000;

  @Test
  public void eternalSpecified() {
    TimingHandler h = TimingHandler.of(
      Cache2kBuilder.forUnknownTypes()
        .eternal(true)
        .toConfiguration()
    );
    assertEquals(TimingHandler.ETERNAL_IMMEDIATE.getClass(), h.getClass());
  }

  @Test
  public void eternalNotSpecified() {
    TimingHandler h = TimingHandler.of(
      Cache2kBuilder.forUnknownTypes()
        .toConfiguration()
    );
    assertEquals(TimingHandler.ETERNAL_IMMEDIATE.getClass(), h.getClass());
  }

  @Test
  public void expireAfterWrite_overflow() {
    TimingHandler h = TimingHandler.of(
      Cache2kBuilder.forUnknownTypes()
        .expireAfterWrite(Long.MAX_VALUE - 47, TimeUnit.SECONDS)
        .toConfiguration()
    );
    assertEquals(TimingHandler.ETERNAL_IMMEDIATE.getClass(), h.getClass());
  }

  @Test
  public void almostEternal_noOverflow() {
    long _BIG_VALUE = Long.MAX_VALUE - 47;
    TimingHandler h = TimingHandler.of(
      Cache2kBuilder.forUnknownTypes()
        .expireAfterWrite(_BIG_VALUE, TimeUnit.MILLISECONDS)
        .toConfiguration()
    );
    long t = h.calculateNextRefreshTime(ENTRY, null, 0);
    assertEquals(_BIG_VALUE, t);
    t = h.calculateNextRefreshTime(ENTRY, null, 48);
    assertEquals(Long.MAX_VALUE, t);
  }

  @Test
  public void almostEternal_expiryPolicy_noOverflow() {
    long _BIG_VALUE = Long.MAX_VALUE - 47;
    TimingHandler h = TimingHandler.of(
      Cache2kBuilder.forUnknownTypes()
        .expiryPolicy(new ExpiryPolicy() {
          @Override
          public long calculateExpiryTime(final Object key, final Object value, final long loadTime, final CacheEntry oldEntry) {
            return ETERNAL;
          }
        })
        .expireAfterWrite(_BIG_VALUE, TimeUnit.MILLISECONDS)
        .toConfiguration()
    );
    long t = h.calculateNextRefreshTime(ENTRY, null, 0);
    assertEquals(_BIG_VALUE, t);
    t = h.calculateNextRefreshTime(ENTRY, null, 48);
    assertEquals(Long.MAX_VALUE, t);
  }

  /**
   * Sharp is honored in the calculation phase.
   */
  @Test
  public void policy_sharp() {
    TimingHandler h = TimingHandler.of(
      Cache2kBuilder.forUnknownTypes()
        .expiryPolicy(new ExpiryPolicy() {
          @Override
          public long calculateExpiryTime(Object key, Object value, long loadTime, CacheEntry oldEntry) {
            return loadTime + 1;
          }
        })
        .sharpExpiry(true)
        .toConfiguration()
    );
    Entry e = new Entry();
    long t = h.calculateNextRefreshTime(e, null, NOW);
    assertEquals(-NOW - 1, t);
  }

  /**
   * The configuration setting serves as a time limit.
   */
  @Test
  public void expireAfterWrite_policy_limit() {
    TimingHandler h = TimingHandler.of(
      Cache2kBuilder.forUnknownTypes()
        .expiryPolicy(new ExpiryPolicy() {
          @Override
          public long calculateExpiryTime(Object key, Object value, long loadTime, CacheEntry oldEntry) {
            return ETERNAL;
          }
        })
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .toConfiguration()
    );
    Entry e = new Entry();
    long t = h.calculateNextRefreshTime(e, null, NOW);
    assertNotEquals(Long.MAX_VALUE, t);
    assertEquals(NOW + TimeUnit.MINUTES.toMillis(5), t);
    t = h.calculateNextRefreshTime(e, null, NOW);
    assertEquals(NOW + TimeUnit.MINUTES.toMillis(5), t);
  }

  /**
   * Maximum expiry is limited when sharp expiry requested.
   */
  @Test
  public void expireAfterWrite_policy_limit_sharp() {
    long _DURATION = 1000000;
    final long _SHARP_POINT_IN_TIME = NOW + 5000000;
    TimingHandler h = TimingHandler.of(
      Cache2kBuilder.forUnknownTypes()
        .expiryPolicy(new ExpiryPolicy() {
          @Override
          public long calculateExpiryTime(Object key, Object value, long loadTime, CacheEntry oldEntry) {
            return -_SHARP_POINT_IN_TIME;
          }
        })
        .expireAfterWrite(_DURATION, TimeUnit.MILLISECONDS)
        .toConfiguration()
    );
    Entry e = new Entry();
    long t = h.calculateNextRefreshTime(e, null, NOW);
    assertNotEquals(Long.MAX_VALUE, t);
    assertEquals("max expiry, but not sharp", NOW + _DURATION , t);
    long _later = NOW + _DURATION;
    t = h.calculateNextRefreshTime(e, null, _later);
    assertEquals(_later + _DURATION, t);
    _later = _SHARP_POINT_IN_TIME - _DURATION / 2;
    t = h.calculateNextRefreshTime(e, null, _later);
    assertEquals("requested expiry via duration too close", -_SHARP_POINT_IN_TIME, t);
    _later = _SHARP_POINT_IN_TIME - _DURATION - 1;
    t = h.calculateNextRefreshTime(e, null, _later);
    assertTrue(t <= _later + _DURATION);
    assertEquals(_later + 1, t);
  }

  @Test
  public void expireAfterWrite_policy_limit_nonSharp() {
    long _DURATION = 1000000;
    final long _POINT_IN_TIME = NOW + 5000000;
    TimingHandler h = TimingHandler.of(
      Cache2kBuilder.forUnknownTypes()
        .expiryPolicy(new ExpiryPolicy() {
          @Override
          public long calculateExpiryTime(Object key, Object value, long loadTime, CacheEntry oldEntry) {
            return _POINT_IN_TIME;
          }
        })
        .expireAfterWrite(_DURATION, TimeUnit.MILLISECONDS)
        .toConfiguration()
    );
    Entry e = new Entry();
    long t = h.calculateNextRefreshTime(e, null, NOW);
    assertNotEquals(Long.MAX_VALUE, t);
    assertEquals("max expiry, but not sharp", NOW + _DURATION , t);
    long _later = NOW + _DURATION;
    t = h.calculateNextRefreshTime(e, null, _later);
    assertEquals(_later + _DURATION, t);
    _later = _POINT_IN_TIME - _DURATION / 2;
    t = h.calculateNextRefreshTime(e, null, _later);
    assertEquals("requested expiry via duration too close", _POINT_IN_TIME, t);
    _later = _POINT_IN_TIME - _DURATION - 1;
    t = h.calculateNextRefreshTime(e, null, _later);
    assertTrue(t <= _later + _DURATION);
    assertEquals(_later + _DURATION, t);
  }

  /**
   * Maximum expiry is limited when sharp expiry requested.
   * Corner case if expiry will happen close to requested point in time.
   */
  @Test
  public void expireAfterWrite_policy_limit_sharp_close() {
    long _DURATION = 100;
    final long _SHARP_POINT_IN_TIME = NOW + 5000;
    TimingHandler h = TimingHandler.of(
      Cache2kBuilder.forUnknownTypes()
        .expiryPolicy(new ExpiryPolicy() {
          @Override
          public long calculateExpiryTime(Object key, Object value, long loadTime, CacheEntry oldEntry) {
            return -_SHARP_POINT_IN_TIME;
          }
        })
        .expireAfterWrite(_DURATION, TimeUnit.MILLISECONDS)
        .toConfiguration()
    );
    Entry e = new Entry();
    long  _later = _SHARP_POINT_IN_TIME - _DURATION - 1;
    long t = h.calculateNextRefreshTime(e, null, _later);
    assertTrue("expect gap bigger then duration", HeapCache.TUNABLE.sharpExpirySafetyGapMillis > _DURATION);
    assertNotEquals(_SHARP_POINT_IN_TIME - HeapCache.TUNABLE.sharpExpirySafetyGapMillis, t);
    assertTrue(Math.abs(t) > NOW);
    assertTrue(Math.abs(t) < _SHARP_POINT_IN_TIME);
  }

  @Test @Ignore("purgeCalled: move to slow tests")
  public void purgeCalled() {
    int _SIZE = 1000;
    int _PURGE_INTERVAL = TunableFactory.get(TimingHandler.Tunable.class).purgeInterval;
    Cache<Integer, Integer> c = Cache2kBuilder.of(Integer.class, Integer.class)
      .entryCapacity(_SIZE)
      .expireAfterWrite(5, TimeUnit.MINUTES)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          return key + 123;
        }
      })
      .build();
    for (int i = 0; i < _PURGE_INTERVAL + _SIZE + 125; i++) {
      c.get(i);
    }
    assertEquals(10000, _PURGE_INTERVAL);
    int _timerCacnelCount =
      ((TimingHandler.Static) c.requestInterface(HeapCache.class).timing).timerCancelCount;
    assertTrue("purge called", _timerCacnelCount < _PURGE_INTERVAL);
    c.close();
  }

  /**
   * Check that the maximize concurrency is routed through to the timing handler properly.
   */
  @Test
  public void maximizeConcurrency() {
    Cache<Integer, Integer> c = Cache2kBuilder.of(Integer.class, Integer.class)
      .boostConcurrency(true)
      .expireAfterWrite(5, TimeUnit.MINUTES)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          return key + 123;
        }
      })
      .build();
    int _timerMask =
      ((TimingHandler.Static) c.requestInterface(HeapCache.class).timing).timerMask;
    if (Runtime.getRuntime().availableProcessors() >1) {
      assertTrue(_timerMask > 0);
    }
    c.close();
  }

}
