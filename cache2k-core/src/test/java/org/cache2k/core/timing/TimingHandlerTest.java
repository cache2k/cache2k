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

import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.CacheManager;
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.configuration.CustomizationSupplier;
import org.cache2k.core.CacheBuildContext;
import org.cache2k.core.Entry;
import org.cache2k.core.HeapCache;
import org.cache2k.core.util.DefaultClock;
import org.cache2k.core.util.InternalClock;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.testing.category.FastTests;
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
  private static final InternalClock CLOCK = DefaultClock.INSTANCE;

  private <K, V> Timing<K, V> create(final InternalClock clock, final Cache2kConfiguration<K, V> cfg) {
    return Timing.of(new CacheBuildContext<K, V>() {
      @Override
      public InternalClock getClock() {
        return clock;
      }

      @Override
      public Cache2kConfiguration<K, V> getConfiguration() {
        return cfg;
      }

      @Override
      public CacheManager getCacheManager() {
        return null;
      }

      @Override
      public <T> T createCustomization(CustomizationSupplier<T> supplier) {
        return createCustomization(supplier, null);
      }

      @Override
      public <T> T createCustomization(CustomizationSupplier<T> supplier, T fallback) {
        if (supplier == null) { return fallback; }
        try {
          return supplier.supply(getCacheManager());
        } catch (Exception e) {
          return null;
        }
      }
    });
  }

  @Test
  public void eternalSpecified() {
    Timing h = create(
      CLOCK,
      Cache2kBuilder.forUnknownTypes()
        .eternal(true)
        .toConfiguration()
    );
    assertEquals(TimeAgnosticTiming.ETERNAL_IMMEDIATE.getClass(), h.getClass());
  }

  @Test
  public void eternalNotSpecified() {
    Timing h = create(
      CLOCK,
      Cache2kBuilder.forUnknownTypes()
        .toConfiguration()
    );
    assertEquals(TimeAgnosticTiming.ETERNAL_IMMEDIATE.getClass(), h.getClass());
  }

  @Test
  public void expireAfterWrite_overflow() {
    Timing h = create(
      CLOCK,
      Cache2kBuilder.forUnknownTypes()
        .expireAfterWrite(Long.MAX_VALUE - 47, TimeUnit.SECONDS)
        .toConfiguration()
    );
    assertEquals(TimeAgnosticTiming.ETERNAL_IMMEDIATE.getClass(), h.getClass());
  }

  @Test
  public void almostEternal_noOverflow() {
    long _BIG_VALUE = Long.MAX_VALUE - 47;
    Timing h = create(
      CLOCK,
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
    Timing h = create(
      CLOCK,
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
    Timing h = create(
      CLOCK,
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
    Timing h = create(
      CLOCK,
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
    Timing h = create(
      CLOCK,
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
    Timing h = create(
      CLOCK,
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
    Timing h = create(
      CLOCK,
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

}
