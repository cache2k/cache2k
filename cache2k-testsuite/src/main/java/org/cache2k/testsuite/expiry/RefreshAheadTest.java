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
import org.cache2k.expiry.RefreshAheadPolicy;
import org.cache2k.pinpoint.ExceptionCollector;
import org.cache2k.pinpoint.ExpectedException;
import org.cache2k.testing.SimulatedClock;
import org.cache2k.testsuite.support.AbstractCacheTester;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

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
  final AtomicLong reloadTime = new AtomicLong();

  <K, V> Cache2kBuilder<K, V> standardSetup(Cache2kBuilder<K, V> b) {
    return b
      .loader(key -> {
        loadRequests.incrementAndGet();
        return (V) key;
      })
      .sharpExpiry(true)
      .timerLag(0, TimeUnit.MILLISECONDS)
      .expiryPolicy((key, value, startTime, currentEntry) -> {
        if (currentEntry != null) {
          reloadTime.set(startTime);
        }
        return startTime + refreshInterval;
      });
  }

  @Test
  public void checkLegacyPolicy() {
    init(b -> standardSetup(b).refreshAhead(true));
    get(k0);
    sleep(refreshInterval);
    await("initial load and refresh", () -> loadRequests.get() == 2);
    await("expires", () -> !containsKey(k0));
    await("really expired", () -> asMap().size() == 0);
    loadRequests.set(0);
    get(k0);
    sleep(refreshInterval);
    await("initial load and refresh", () -> loadRequests.get() == 2);
    get(k0);
    if (now() >= reloadTime.get() + refreshInterval) { return; }
    assertThat(loadRequests.get()).isEqualTo(2);
    await("another refresh, since accessed", () -> loadRequests.get() == 3);
  }

  @Test
  public void checkPolicyCallForInitialLoad() throws InterruptedException {
    Semaphore semaphore = new Semaphore(0);
    ExceptionCollector collector = new ExceptionCollector();
    AtomicLong t0 = new AtomicLong();
    init(b -> standardSetup((b))
      .refreshAheadPolicy(new RefreshAheadPolicy<K, V, Object>() {
        @Override
        public long refreshAheadTime(Context<Object> ctx) {
          try {
            assertThat(ctx.isLoad()).isTrue();
            assertThat(ctx.isRefreshAhead()).isFalse();
            assertThat(ctx.isAccessed()).isFalse();
            assertThat(ctx.getStartTime()).isGreaterThanOrEqualTo(t0.get());
            assertThat(ctx.getStopTime()).isGreaterThanOrEqualTo(ctx.getStartTime());
            assertThat(ctx.getCurrentTime()).isEqualTo(ctx.getStopTime());
            assertThat(ctx.getExpiryTime()).isEqualTo(ctx.getStartTime() + refreshInterval);
          } catch (Throwable err) {
            collector.exception(err);
          }
          semaphore.release();
          return ctx.getExpiryTime();
        }

        @Override
        public int requiredHits(Context<Object> ctx) {
          try {
            assertThat(ctx.isLoad()).isTrue();
            assertThat(ctx.isRefreshAhead()).isFalse();
            assertThat(ctx.isAccessed()).isFalse();
            assertThat(ctx.getStartTime()).isGreaterThanOrEqualTo(t0.get());
            assertThat(ctx.getStopTime()).isGreaterThanOrEqualTo(ctx.getStartTime());
            assertThat(ctx.getCurrentTime()).isEqualTo(ctx.getStopTime());
            assertThat(ctx.getExpiryTime()).isEqualTo(ctx.getStartTime() + refreshInterval);
          } catch (Throwable err) {
            collector.exception(err);
          }
          semaphore.release();
          return 1;
        }
      })
    );
    t0.set(now());
    get(k0);
    semaphore.acquire();
    semaphore.acquire();
    collector.assertNoException();
  }

  @Test
  public void checkPolicyCallForRefresh() throws InterruptedException {
    Semaphore semaphore = new Semaphore(0);
    ExceptionCollector collector = new ExceptionCollector();
    AtomicLong t0 = new AtomicLong();
    AtomicBoolean firstCallIsInitialLoad = new AtomicBoolean(true);
    init(b -> standardSetup((b))
      .refreshAheadPolicy(new RefreshAheadPolicy<K, V, Object>() {
        @Override
        public long refreshAheadTime(Context<Object> ctx) {
          if (firstCallIsInitialLoad.get()) {
            return ctx.getExpiryTime();
          }
          try {
            checkContextForRefresh(ctx, t0);
            semaphore.release();
            return ctx.getExpiryTime();
          } catch (Throwable err) {
            collector.exception(err);
          }
          semaphore.release();
          return ctx.getExpiryTime();
        }

        @Override
        public int requiredHits(Context<Object> ctx) {
          if (firstCallIsInitialLoad.get()) {
            firstCallIsInitialLoad.set(false);
            return 0;
          }
          try {
            checkContextForRefresh(ctx, t0);
          } catch (Throwable err) {
            collector.exception(err);
          }
          semaphore.release();
          return 0;
        }
      })
    );
    t0.set(now());
    get(k0);
    sleep(refreshInterval * 3);
    semaphore.acquire();
    semaphore.acquire();
    collector.assertNoException();
  }

  /**
   * Check {@link RefreshAheadPolicy.Context#isLoadException()}
   */
  @Test
  public void checkContextOnLoadException() throws InterruptedException {
    Semaphore semaphore = new Semaphore(0);
    ExceptionCollector collector = new ExceptionCollector();
    AtomicLong t0 = new AtomicLong();
    boolean exceptionNotCached = true;
    init(b -> standardSetup((b))
      .loader(key -> { throw new ExpectedException(); })
      .refreshAheadPolicy(new RefreshAheadPolicy<K, V, Object>() {
        @Override
        public long refreshAheadTime(Context<Object> ctx) {
          try {
            assertThat(ctx.isLoadException()).isTrue();
          } catch (Throwable err) {
            collector.exception(err);
          }
          semaphore.release();
          return ctx.getExpiryTime();
        }

        @Override
        public int requiredHits(Context<Object> ctx) {
          try {
            if (exceptionNotCached) {
              fail("Expected to be not called since exception is not cached");
            }
            assertThat(ctx.isLoadException()).isTrue();
          } catch (Throwable err) {
            collector.exception(err);
          }
          semaphore.release();
          return 0;
        }
      })
    );
    t0.set(now());
    assertThatCode(() -> get(k0))
      .getRootCause().isInstanceOf(ExpectedException.class);
    sleep(refreshInterval * 3);
    semaphore.acquire();
    if (!exceptionNotCached) {
      semaphore.acquire();
    }
    collector.assertNoException();
  }

  private void checkContextForRefresh(RefreshAheadPolicy.Context<Object> ctx, AtomicLong t0) {
    assertThat(ctx.isLoad()).isTrue();
    assertThat(ctx.isRefreshAhead()).isTrue();
    assertThat(ctx.isAccessed()).isFalse();
    assertThat(ctx.getStartTime()).isGreaterThanOrEqualTo(t0.get());
    assertThat(ctx.getStopTime()).isGreaterThanOrEqualTo(ctx.getStartTime());
    assertThat(ctx.getCurrentTime()).isEqualTo(ctx.getStopTime());
    assertThat(ctx.getExpiryTime()).isEqualTo(ctx.getStartTime() + refreshInterval);
  }

  @Test
  public void checkPolicyCallForRefreshWithAccess() throws InterruptedException {
    Semaphore semaphore = new Semaphore(0);
    AtomicInteger callCount = new AtomicInteger(0);
    AtomicBoolean wasAccessed = new AtomicBoolean(false);
    init(b -> standardSetup((b))
      .refreshAheadPolicy(new RefreshAheadPolicy<K, V, Object>() {
        @Override
        public long refreshAheadTime(Context<Object> ctx) {
          if (callCount.get() == 1) {
            wasAccessed.set(ctx.isAccessed());
            semaphore.release();
          }
          return ctx.getExpiryTime();
        }

        @Override
        public int requiredHits(Context<Object> ctx) {
          callCount.incrementAndGet();
          return 0;
        }
      })
    );
    long t0 = now();
    get(k0);
    peek(k0);
    if (now() - t0 < refreshInterval) {
      sleep(refreshInterval * 3);
      semaphore.acquire();
      assertThat(wasAccessed.get()).isTrue();
    }
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
    if (now() > reloadTime.get() + refreshInterval) { return; }
    assertThat(loadRequests).isEqualTo(2);
    await("another refresh, since accessed", () -> loadRequests.get() == 3);
  }

}
