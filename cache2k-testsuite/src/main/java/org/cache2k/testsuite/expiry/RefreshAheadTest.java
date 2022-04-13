package org.cache2k.testsuite.expiry;

/*-
 * #%L
 * cache2k testsuite on public API
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
import org.cache2k.pinpoint.TimeoutError;
import org.cache2k.testing.SimulatedClock;
import org.cache2k.testsuite.support.AbstractCacheTester;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jens Wilke
 */
public class RefreshAheadTest<K, V> extends AbstractCacheTester<K, V> {

  @Override
  protected Cache2kBuilder<K, V> provideBuilder() {
    SimulatedClock clock = new SimulatedClock(true, 100000);
    setClock(clock);
    return super.provideBuilder()
      .executor(clock.wrapExecutor(ForkJoinPool.commonPool()))
      .loaderExecutor(clock.wrapExecutor(ForkJoinPool.commonPool()))
      .scheduler(clock);
  }

  long refreshInterval = 1234;
  final AtomicInteger loadRequests = new AtomicInteger(0);

  private void checkNeutral(Runnable neutral, boolean sharpExpiry) {
    within(refreshInterval)
      .perform(() -> {
          invoke(k0, entry -> entry.load());
          neutral.run();
        }
      )
      .expectMaybe(() -> {
        assertThat(containsKey(k0)).isTrue();
      });
    sleep(refreshInterval);
    checkExpired(sharpExpiry);
  }

  /**
   * Expect that entry is expired now for sharp expiry or
   * that entry will expiry soon.
   */
  private void checkExpired(boolean sharpExpiry) {
    if (sharpExpiry) {
      assertThat(containsKey(k0)).isFalse();
    } else {
      await(() -> !containsKey(k0));
    }
  }

  private void checkDoubleTouchRequired(Runnable touch, boolean sharpExpiry) {
    long t0 = now();
    get(k0);
    touch.run();
    if (now() >= t0 + refreshInterval) { return; }
    sleep(refreshInterval);
    checkExpired(sharpExpiry);
    sleep(refreshInterval);
    checkExpired(sharpExpiry);
    System.err.println(now());
    System.err.println("HUHU");
    t0 = now();
    if (now() >= t0 + refreshInterval) { return; }
    sleep(refreshInterval);
  }

  private void checkLegacyPolicy(Runnable touch, boolean sharpExpiry) {
    long t0 = now();
    get(k0);
    sleep(refreshInterval);
    sleep(refreshInterval);
    sleep(refreshInterval);
    await(() -> loadRequests.get() >= 2);
    checkExpired(sharpExpiry);
    checkExpired(sharpExpiry);
    System.err.println(now());
    System.err.println("HUHU");
    t0 = now();
    if (now() >= t0 + refreshInterval) { return; }
    sleep(refreshInterval);
  }

  /** Start time of reload or refresh */
  long reloadTime = 0;

  <K, V> Cache2kBuilder<K, V> standardSetup(Cache2kBuilder<K, V> b) {
    return b
      .loader(key -> {
        loadRequests.incrementAndGet();
        return (V) key;
      })
      .refreshAhead(true)
      .sharpExpiry(true)
      .timerLag(0, TimeUnit.MILLISECONDS)
      .expiryPolicy((key, value, startTime, currentEntry) -> {
        if (currentEntry != null) {
          reloadTime = startTime;
        }
        return startTime + refreshInterval;
      });
  }

  @Test
  public void checkLegacyPolicy() {
    init(b -> standardSetup(b));
    get(k0);
    sleep(refreshInterval);
    await("initial load and refresh", () -> loadRequests.get() == 2);
    await("expires", () -> !containsKey(k0));
    await("really expired", () -> asMap().size() == 0);
    loadRequests.set(0);
    get(k0);
    sleep(refreshInterval);
    try {
      await("initial load and refresh", () -> loadRequests.get() == 2);
    } catch (TimeoutError err) {
      System.err.println(loadRequests.get());
      throw err;
    }
    get(k0);
    if (now() >= reloadTime + refreshInterval) { return; }
    assertThat(loadRequests.get()).isEqualTo(2);
    await("another refresh, since accessed", () -> loadRequests.get() == 3);
  }

  public void checkTouchTwiceRequired() throws InterruptedException {
    init(b -> standardSetup(b));
    get(k0);
    sleep(refreshInterval);
    await("initial load and refresh", () -> loadRequests.get() == 2);
    await("expires", () -> !containsKey(k0));
    loadRequests.set(0);
    get(k0);
    sleep(refreshInterval);
    await("initial load and refresh", () -> loadRequests.get() == 2);
    get(k0);
    if (now() > reloadTime + refreshInterval) { return; }
    assertThat(loadRequests).isEqualTo(2);
    await("another refresh, since accessed", () -> loadRequests.get() == 3);
  }

}
