package org.cache2k.test.core;

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

import org.cache2k.Cache2kBuilder;
import org.cache2k.core.CacheClosedException;
import org.cache2k.core.timing.TimingUnitTest;
import org.cache2k.event.CacheEntryCreatedListener;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.test.util.TestingBase;
import org.cache2k.Cache;
import org.cache2k.event.CacheEntryExpiredListener;
import org.cache2k.core.HeapCache;
import org.cache2k.testing.category.SlowTests;
import static org.hamcrest.Matchers.*;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * More thorough expiry events.
 *
 * @author Jens Wilke
 */
@Category(SlowTests.class)
public class ExpiryListenerTest extends TestingBase {

  @Test
  public void simpleAsyncExpiredListenerCalled() {
    testListenerCalled(false, false);
  }

  @Test
  public void asyncExpiredListenerCalled() {
    testListenerCalled(false, true);
  }

  @Test
  public void expireBeforePut() {
    AtomicInteger callCount = new AtomicInteger();
    final long expiryMillis = 100;
    assertThat(TimingUnitTest.SHARP_EXPIRY_GAP_MILLIS, greaterThan(expiryMillis));
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .addListener((CacheEntryExpiredListener<Integer, Integer>) (c1, e) -> callCount.incrementAndGet())
      .expiryPolicy((key, value, startTime, currentEntry) -> startTime + expiryMillis)
      .sharpExpiry(true)
      .build();
    final int anyKey = 1;
    c.put(anyKey, 4711);
    for (int i = 1; i <= 10; i++) {
      sleep(expiryMillis);
      assertFalse("expired based on wall clock", c.containsKey(anyKey));
      c.put(anyKey, 1802);
      assertThat("expiry event before put", callCount.get(), greaterThanOrEqualTo(i));
    }
  }

  @Test
  public void expireAtSendsOneEvent() {
    AtomicInteger listenerCallCount = new AtomicInteger();
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .addListener((CacheEntryExpiredListener<Integer, Integer>) (c1, e) -> listenerCallCount.incrementAndGet())
      .build();
    final int anyKey = 1;
    c.put(anyKey, 4711);
    c.expireAt(anyKey, ExpiryTimeValues.NOW);
    assertEquals("expiry event before put", 1, listenerCallCount.get());
  }

  @Test
  public void normalExpireSendsOneEvent() {
    final long expiryMillis = TestingParameters.MINIMAL_TICK_MILLIS;
    AtomicInteger listenerCallCount = new AtomicInteger();
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .addListener((CacheEntryExpiredListener<Integer, Integer>) (c1, e) -> listenerCallCount.incrementAndGet())
      .expireAfterWrite(expiryMillis, TimeUnit.MILLISECONDS)
      .build();
    final int anyKey = 1;
    c.put(anyKey, 4711);
    await(() -> listenerCallCount.get() > 0);
    sleep(expiryMillis);
    assertEquals("expiry event before put", 1, listenerCallCount.get());
  }

  @Test
  public void normalExpireSendsOneEvent_sharp() {
    final long expiryMillis = TestingParameters.MINIMAL_TICK_MILLIS;
    AtomicInteger listenerCallCount = new AtomicInteger();
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .addListener((CacheEntryExpiredListener<Integer, Integer>) (c1, e) -> listenerCallCount.incrementAndGet())
      .expiryPolicy((key, value, startTime, currentEntry) -> startTime + expiryMillis)
      .sharpExpiry(true)
      .build();
    final int anyKey = 1;
    c.put(anyKey, 4711);
    await(() -> listenerCallCount.get() > 0);
    sleep(expiryMillis);
    assertEquals("expect exactly one", 1, listenerCallCount.get());
  }

  @Test
  public void expireAfterRefreshProbationEnded() {
    AtomicInteger loaderCount = new AtomicInteger();
    AtomicInteger eventCount = new AtomicInteger();
    final long expiryMillis = TestingParameters.MINIMAL_TICK_MILLIS;
    Cache2kBuilder<Integer, Integer> builder =
      builder(Integer.class, Integer.class)
        .loader((key, startTime, currentEntry) -> {
          loaderCount.getAndIncrement();
          assertEquals(0, eventCount.get());
          return key;
        })
        .expireAfterWrite(expiryMillis, TimeUnit.MILLISECONDS)
        .refreshAhead(true)
        .addListener((CacheEntryExpiredListener<Integer, Integer>) (c, e) -> eventCount.incrementAndGet());

    Cache<Integer, Integer> c = builder.build();
    final int anyKey = 1;
    c.get(anyKey);
    await(() -> eventCount.get() == 1);
    assertEquals(2, loaderCount.get());
  }

  /**
   * Is expiry listener called before the load?
   */
  @Test
  public void expireBeforeLoadSharp() {
    expireBeforeLoad(true);
  }

  @Test
  public void expireBeforeLoad() {
    expireBeforeLoad(false);
  }

  private void expireBeforeLoad(boolean sharpExpiry) {
    AtomicInteger listenerCallCount = new AtomicInteger();
    final long expiryMillis = 432;
    Cache2kBuilder<Integer, Integer> builder =
      builder(Integer.class, Integer.class)
      .loader((key, startTime, currentEntry) -> {
        if (currentEntry != null) {
          assertEquals(1, listenerCallCount.get());
          return 0;
        }
        return 1;
      })
      .keepDataAfterExpired(true)
      .addListener((CacheEntryExpiredListener<Integer, Integer>) (c, e) -> listenerCallCount.incrementAndGet());
    if (sharpExpiry) {
      builder.expiryPolicy((key, value, startTime, currentEntry) -> startTime + expiryMillis)
        .sharpExpiry(true);
    } else {
      builder.expireAfterWrite(expiryMillis, TimeUnit.MILLISECONDS);
    }
    Cache<Integer, Integer> c = builder.build();
    final int anyKey = 1;
    await(() -> c.get(anyKey) == 0);
  }

  @Test
  public void simpleAsyncExpiredListenerCalledSharpExpiry() {
    testListenerCalled(true, false);
  }

  @Test
  public void asyncExpiredListenerCalledSharpExpiry() {
    testListenerCalled(true, true);
  }

  private void testListenerCalled(boolean sharp, boolean beyondSafetyGap) {
    AtomicInteger callCount = new AtomicInteger();
    long expiryMillis =
      (beyondSafetyGap ? getEffectiveSafetyGapMillis() : 0) + TestingParameters.MINIMAL_TICK_MILLIS;
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .addListener((CacheEntryExpiredListener<Integer, Integer>) (c1, e) -> callCount.incrementAndGet())
      .expireAfterWrite(expiryMillis, TimeUnit.MILLISECONDS)
      .sharpExpiry(sharp)
      .build();
    final int anyKey = 1;
    within(expiryMillis)
      .perform(() -> c.put(anyKey, 4711))
      .expectMaybe(() -> {
        assertEquals(0, callCount.get());
        assertTrue(c.containsKey(anyKey));
      });
    sleep(expiryMillis * 2);
    assertThat(callCount.get(), lessThanOrEqualTo(1));
    await(() -> callCount.get() == 1);
  }

  @Test
  public void asyncExpiredListenerAfterRefreshCalled() {
    AtomicInteger listenerCallCount = new AtomicInteger();
    AtomicInteger loaderCallCount = new AtomicInteger();
    final long extraGap = TestingParameters.MINIMAL_TICK_MILLIS;
    long expiryMillis = getEffectiveSafetyGapMillis() + extraGap;
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .addListener((CacheEntryExpiredListener<Integer, Integer>) (c1, e) -> listenerCallCount.incrementAndGet())
      .expireAfterWrite(expiryMillis, TimeUnit.MILLISECONDS)
      .refreshAhead(true)
      .loader(key -> {
        loaderCallCount.incrementAndGet();
        return 123;
      })
      .build();
    final int anyKey = 1;
    within(expiryMillis)
      .perform(() -> c.get(anyKey))
      .expectMaybe(() -> {
        assertEquals(0, listenerCallCount.get());
        assertTrue(c.containsKey(anyKey));
      });
    await(() -> listenerCallCount.get() == 1);
    assertEquals(2, loaderCallCount.get());
  }

  /**
   * We hold up the insert task with the created listener. Checks that we get an expired
   * event after the insert event. Also checks that the cache entry is not visible.
   */
  @Test
  public void expiresDuringInsert() throws InterruptedException {
    AtomicInteger gotExpired = new AtomicInteger();
    CountDownLatch gotCreated = new CountDownLatch(1);
    final long expiryMillis = TestingParameters.MINIMAL_TICK_MILLIS;
    CountDownLatch waitInCreated = new CountDownLatch(1);
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .addListener((CacheEntryExpiredListener<Integer, Integer>) (c1, e) -> gotExpired.incrementAndGet())
      .addListener((CacheEntryCreatedListener<Integer, Integer>) (cache, entry) -> {
        assertEquals(123, (long) entry.getValue());
        gotCreated.countDown();
        try {
          waitInCreated.await();
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
      })
      .expireAfterWrite(expiryMillis, TimeUnit.MILLISECONDS)
      .loader(key -> 123)
      .build();
    final int anyKey = 1;
    execute(() -> {
      try {
        c.get(anyKey);
      } catch (CacheClosedException ignore) {
      }
    });
    gotCreated.await();
    assertFalse("entry is not visible", c.containsKey(anyKey));
    sleep(expiryMillis);
    waitInCreated.countDown();
    await(() -> gotExpired.get() > 0);
  }

}
