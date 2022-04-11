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

import org.cache2k.CacheManager;
import org.cache2k.config.Cache2kConfig;
import org.cache2k.config.CustomizationSupplier;
import org.cache2k.core.api.InternalCacheBuildContext;
import org.cache2k.core.Entry;
import org.cache2k.operation.Scheduler;
import org.cache2k.operation.TimeReference;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import java.util.concurrent.Executor;

import static java.lang.Long.MAX_VALUE;
import static java.lang.Math.abs;
import static java.util.concurrent.TimeUnit.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.cache2k.Cache2kBuilder.forUnknownTypes;
import static org.cache2k.core.timing.TimeAgnosticTiming.ETERNAL_IMMEDIATE;
import static org.cache2k.expiry.ExpiryTimeValues.ETERNAL;

/**
 * Test timing code directly without cache.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("unchecked")
@Category(FastTests.class)
public class TimingUnitTest {

  public static final long SHARP_EXPIRY_GAP_MILLIS =
    StaticTiming.SHARP_EXPIRY_SAFETY_GAP.toMillis();

  private static final Entry ENTRY = new Entry();
  private static final long NOW = 10000000;
  private static final TimeReference CLOCK = TimeReference.DEFAULT;

  private <K, V> Timing<K, V> create(TimeReference clock,
                                     Cache2kConfig<K, V> cfg) {
    return Timing.of(new InternalCacheBuildContext<K, V>() {
      @Override
      public TimeReference getTimeReference() {
        return clock;
      }

      @Override
      public Cache2kConfig<K, V> getConfig() {
        return cfg;
      }

      @Override
      public CacheManager getCacheManager() {
        return null;
      }

      @Override
      public Executor getExecutor() {
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

      @Override
      public Scheduler createScheduler() {
        return null;
      }
    });
  }

  @Test
  public void eternalSpecified() {
    Timing h = create(
      CLOCK,
      forUnknownTypes()
        .eternal(true)
        .config()
    );
    assertThat(h.getClass()).isEqualTo(ETERNAL_IMMEDIATE.getClass());
  }

  @Test
  public void eternalNotSpecified() {
    Timing h = create(
      CLOCK,
      forUnknownTypes()
        .config()
    );
    assertThat(h.getClass()).isEqualTo(StaticTiming.class);
  }

  @Test
  public void expireAfterWrite_overflow() {
    Timing h = create(
      CLOCK,
      forUnknownTypes()
        .expireAfterWrite(MAX_VALUE - 47, SECONDS)
        .config()
    );
    assertThat(h.getClass()).isEqualTo(StaticTiming.class);
  }

  @Test
  public void almostEternal_noOverflow() {
    long bigValue = MAX_VALUE - 47;
    Timing h = create(
      CLOCK,
      forUnknownTypes()
        .expireAfterWrite(bigValue, MILLISECONDS)
        .config()
    );
    long t = h.calculateExpiry(ENTRY, null, 0);
    assertThat(t).isEqualTo(bigValue);
    t = h.calculateExpiry(ENTRY, null, 48);
    assertThat(t).isEqualTo(MAX_VALUE);
  }

  @Test
  public void almostEternal_expiryPolicy_noOverflow() {
    long bigValue = MAX_VALUE - 47;
    Timing h = create(
      CLOCK,
      forUnknownTypes()
        .expiryPolicy((ExpiryPolicy) (key, value, startTime, currentEntry) -> ETERNAL)
        .expireAfterWrite(bigValue, MILLISECONDS)
        .config()
    );
    long t = h.calculateExpiry(ENTRY, null, 0);
    assertThat(t).isEqualTo(bigValue);
    t = h.calculateExpiry(ENTRY, null, 48);
    assertThat(t).isEqualTo(MAX_VALUE);
  }

  /**
   * Sharp is honored in the calculation phase.
   */
  @Test
  public void policy_sharp() {
    Timing h = create(
      CLOCK,
      forUnknownTypes()
        .expiryPolicy((ExpiryPolicy) (key, value, startTime, currentEntry) -> startTime + 1)
        .sharpExpiry(true)
        .config()
    );
    Entry e = new Entry();
    long t = h.calculateExpiry(e, null, NOW);
    assertThat(t).isEqualTo(-NOW - 1);
  }

  /**
   * The configuration setting serves as a time limit.
   */
  @Test
  public void expireAfterWrite_policy_limit() {
    Timing h = create(
      CLOCK,
      forUnknownTypes()
        .expiryPolicy((ExpiryPolicy) (key, value, startTime, currentEntry) -> ETERNAL)
        .expireAfterWrite(5, MINUTES)
        .config()
    );
    Entry e = new Entry();
    long t = h.calculateExpiry(e, null, NOW);
    assertThat(t).isNotEqualTo(MAX_VALUE);
    assertThat(t).isEqualTo(NOW + MINUTES.toMillis(5));
    t = h.calculateExpiry(e, null, NOW);
    assertThat(t).isEqualTo(NOW + MINUTES.toMillis(5));
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
      forUnknownTypes()
        .expiryPolicy((ExpiryPolicy) (key, value, startTime, currentEntry) -> -sharpPointInTime)
        .expireAfterWrite(duration, MILLISECONDS)
        .config()
    );
    Entry e = new Entry();
    long t = h.calculateExpiry(e, null, NOW);
    assertThat(t).isNotEqualTo(MAX_VALUE);
    assertThat(t)
      .as("max expiry, but not sharp")
      .isEqualTo(NOW + duration);
    long later = NOW + duration;
    t = h.calculateExpiry(e, null, later);
    assertThat(t).isEqualTo(later + duration);
    later = sharpPointInTime - duration / 2;
    t = h.calculateExpiry(e, null, later);
    assertThat(t)
      .as("requested expiry via duration too close")
      .isEqualTo(-sharpPointInTime);
    later = sharpPointInTime - duration - 1;
    t = h.calculateExpiry(e, null, later);
    assertThat(t <= later + duration).isTrue();
    assertThat(t).isEqualTo(later + 1);
  }

  @Test
  public void expireAfterWrite_policy_limit_nonSharp() {
    long duration = 1000000;
    final long pointInTime = NOW + 5000000;
    Timing h = create(
      CLOCK,
      forUnknownTypes()
        .expiryPolicy((ExpiryPolicy) (key, value, startTime, currentEntry) -> pointInTime)
        .expireAfterWrite(duration, MILLISECONDS)
        .config()
    );
    Entry e = new Entry();
    long t = h.calculateExpiry(e, null, NOW);
    assertThat(t).isNotEqualTo(MAX_VALUE);
    assertThat(t)
      .as("max expiry, but not sharp")
      .isEqualTo(NOW + duration);
    long later = NOW + duration;
    t = h.calculateExpiry(e, null, later);
    assertThat(t).isEqualTo(later + duration);
    later = pointInTime - duration / 2;
    t = h.calculateExpiry(e, null, later);
    assertThat(t)
      .as("requested expiry via duration too close")
      .isEqualTo(pointInTime);
    later = pointInTime - duration - 1;
    t = h.calculateExpiry(e, null, later);
    assertThat(t <= later + duration).isTrue();
    assertThat(t).isEqualTo(later + duration);
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
      forUnknownTypes()
        .expiryPolicy((ExpiryPolicy) (key, value, startTime, currentEntry) -> -sharpPointInTime)
        .expireAfterWrite(duration, MILLISECONDS)
        .config()
    );
    Entry e = new Entry();
    long later = sharpPointInTime - duration - 1;
    long t = h.calculateExpiry(e, null, later);
    assertThat(SHARP_EXPIRY_GAP_MILLIS > duration)
      .as("expect gap bigger then duration")
      .isTrue();
    assertThat(t).isNotEqualTo(sharpPointInTime - SHARP_EXPIRY_GAP_MILLIS);
    assertThat(abs(t)).isGreaterThan(NOW);
    assertThat(abs(t)).isLessThan(sharpPointInTime);
  }

}
