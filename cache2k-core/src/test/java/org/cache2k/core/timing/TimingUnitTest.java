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

import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.CacheManager;
import org.cache2k.config.Cache2kConfig;
import org.cache2k.config.CustomizationSupplier;
import org.cache2k.core.api.InternalCacheBuildContext;
import org.cache2k.core.Entry;
import org.cache2k.core.HeapCache;
import org.cache2k.core.util.DefaultClock;
import org.cache2k.core.api.InternalClock;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Test timing code directly without cache.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("unchecked")
@Category(FastTests.class)
public class TimingUnitTest {

  private static final Entry ENTRY = new Entry();
  private static final long NOW = 10000000;
  private static final InternalClock CLOCK = DefaultClock.INSTANCE;

  private <K, V> Timing<K, V> create(final InternalClock clock,
                                     final Cache2kConfig<K, V> cfg) {
    return Timing.of(new InternalCacheBuildContext<K, V>() {
      @Override
      public InternalClock getClock() {
        return clock;
      }

      @Override
      public Cache2kConfig<K, V> getConfiguration() {
        return cfg;
      }

      @Override
      public CacheManager getCacheManager() {
        return null;
      }

      @Override
      public String getName() {
        return null;
      }

      @Override
      public <T> T createCustomization(CustomizationSupplier<T> supplier) {
        try {
          return supplier.supply(null);
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
        .config()
    );
    assertEquals(TimeAgnosticTiming.ETERNAL_IMMEDIATE.getClass(), h.getClass());
  }

  @Test
  public void eternalNotSpecified() {
    Timing h = create(
      CLOCK,
      Cache2kBuilder.forUnknownTypes()
        .config()
    );
    assertEquals(TimeAgnosticTiming.ETERNAL_IMMEDIATE.getClass(), h.getClass());
  }

  @Test
  public void expireAfterWrite_overflow() {
    Timing h = create(
      CLOCK,
      Cache2kBuilder.forUnknownTypes()
        .expireAfterWrite(Long.MAX_VALUE - 47, TimeUnit.SECONDS)
        .config()
    );
    assertEquals(TimeAgnosticTiming.ETERNAL_IMMEDIATE.getClass(), h.getClass());
  }

  @Test
  public void almostEternal_noOverflow() {
    long bigValue = Long.MAX_VALUE - 47;
    Timing h = create(
      CLOCK,
      Cache2kBuilder.forUnknownTypes()
        .expireAfterWrite(bigValue, TimeUnit.MILLISECONDS)
        .config()
    );
    long t = h.calculateNextRefreshTime(ENTRY, null, 0);
    assertEquals(bigValue, t);
    t = h.calculateNextRefreshTime(ENTRY, null, 48);
    assertEquals(Long.MAX_VALUE, t);
  }

  @Test
  public void almostEternal_expiryPolicy_noOverflow() {
    long bigValue = Long.MAX_VALUE - 47;
    Timing h = create(
      CLOCK,
      Cache2kBuilder.forUnknownTypes()
        .expiryPolicy(new ExpiryPolicy() {
          @Override
          public long calculateExpiryTime(Object key, Object value, long loadTime,
                                          CacheEntry currentEntry) {
            return ETERNAL;
          }
        })
        .expireAfterWrite(bigValue, TimeUnit.MILLISECONDS)
        .config()
    );
    long t = h.calculateNextRefreshTime(ENTRY, null, 0);
    assertEquals(bigValue, t);
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
          public long calculateExpiryTime(Object key, Object value, long loadTime,
                                          CacheEntry currentEntry) {
            return loadTime + 1;
          }
        })
        .sharpExpiry(true)
        .config()
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
          public long calculateExpiryTime(Object key, Object value, long loadTime,
                                          CacheEntry currentEntry) {
            return ETERNAL;
          }
        })
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .config()
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
    long duration = 1000000;
    final long sharpPointInTime = NOW + 5000000;
    Timing h = create(
      CLOCK,
      Cache2kBuilder.forUnknownTypes()
        .expiryPolicy(new ExpiryPolicy() {
          @Override
          public long calculateExpiryTime(Object key, Object value, long loadTime,
                                          CacheEntry currentEntry) {
            return -sharpPointInTime;
          }
        })
        .expireAfterWrite(duration, TimeUnit.MILLISECONDS)
        .config()
    );
    Entry e = new Entry();
    long t = h.calculateNextRefreshTime(e, null, NOW);
    assertNotEquals(Long.MAX_VALUE, t);
    assertEquals("max expiry, but not sharp",
      NOW + duration, t);
    long later = NOW + duration;
    t = h.calculateNextRefreshTime(e, null, later);
    assertEquals(later + duration, t);
    later = sharpPointInTime - duration / 2;
    t = h.calculateNextRefreshTime(e, null, later);
    assertEquals("requested expiry via duration too close", -sharpPointInTime, t);
    later = sharpPointInTime - duration - 1;
    t = h.calculateNextRefreshTime(e, null, later);
    assertTrue(t <= later + duration);
    assertEquals(later + 1, t);
  }

  @Test
  public void expireAfterWrite_policy_limit_nonSharp() {
    long duration = 1000000;
    final long pointInTime = NOW + 5000000;
    Timing h = create(
      CLOCK,
      Cache2kBuilder.forUnknownTypes()
        .expiryPolicy(new ExpiryPolicy() {
          @Override
          public long calculateExpiryTime(Object key, Object value, long loadTime,
                                          CacheEntry currentEntry) {
            return pointInTime;
          }
        })
        .expireAfterWrite(duration, TimeUnit.MILLISECONDS)
        .config()
    );
    Entry e = new Entry();
    long t = h.calculateNextRefreshTime(e, null, NOW);
    assertNotEquals(Long.MAX_VALUE, t);
    assertEquals("max expiry, but not sharp",
      NOW + duration, t);
    long later = NOW + duration;
    t = h.calculateNextRefreshTime(e, null, later);
    assertEquals(later + duration, t);
    later = pointInTime - duration / 2;
    t = h.calculateNextRefreshTime(e, null, later);
    assertEquals("requested expiry via duration too close", pointInTime, t);
    later = pointInTime - duration - 1;
    t = h.calculateNextRefreshTime(e, null, later);
    assertTrue(t <= later + duration);
    assertEquals(later + duration, t);
  }

  /**
   * Maximum expiry is limited when sharp expiry requested.
   * Corner case if expiry will happen close to requested point in time.
   */
  @Test
  public void expireAfterWrite_policy_limit_sharp_close() {
    long duration = 100;
    final long sharpPointInTime = NOW + 5000;
    Timing h = create(
      CLOCK,
      Cache2kBuilder.forUnknownTypes()
        .expiryPolicy(new ExpiryPolicy() {
          @Override
          public long calculateExpiryTime(Object key, Object value, long loadTime,
                                          CacheEntry currentEntry) {
            return -sharpPointInTime;
          }
        })
        .expireAfterWrite(duration, TimeUnit.MILLISECONDS)
        .config()
    );
    Entry e = new Entry();
    long  later = sharpPointInTime - duration - 1;
    long t = h.calculateNextRefreshTime(e, null, later);
    assertTrue("expect gap bigger then duration",
      HeapCache.TUNABLE.sharpExpirySafetyGapMillis > duration);
    assertNotEquals(sharpPointInTime - HeapCache.TUNABLE.sharpExpirySafetyGapMillis, t);
    assertTrue(Math.abs(t) > NOW);
    assertTrue(Math.abs(t) < sharpPointInTime);
  }

}
