package org.cache2k.testsuite;

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
import org.cache2k.event.CacheEntryExpiredListener;
import org.cache2k.operation.Scheduler;
import org.cache2k.pinpoint.ExceptionCollector;
import org.cache2k.testing.SimulatedClock;
import org.cache2k.testsuite.support.AbstractCacheTester;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Straight forward tests for lag time via the interface.
 * See also {@code TimerTest} in core for more detailed tests.
 *
 * @author Jens Wilke
 */
public class LagTimeTest<K, V> extends AbstractCacheTester<K, V> {

  CountingScheduler scheduler;

  @Override
  protected Cache2kBuilder<K, V> provideBuilder() {
    SimulatedClock clock = new SimulatedClock(true, 100000);
    setClock(clock);
    scheduler = new CountingScheduler(clock);
    return super.provideBuilder()
      .executor(clock.wrapExecutor(ForkJoinPool.commonPool()))
      .scheduler(scheduler);
  }

  @Test
  public void lagTimeSetExpireTime() {
    long lagTimeTicks = 100;
    int timeSpan = 1234;
    AtomicInteger expiryCount = new AtomicInteger();
    AtomicLong startTime = new AtomicLong();
    ExceptionCollector exceptionCollector = new ExceptionCollector();
    init(b -> b
      .entryCapacity(timeSpan * 2)
      .timerLag(clock().ticksToMillisCeiling(lagTimeTicks), TimeUnit.MILLISECONDS)
      .addListener((CacheEntryExpiredListener<K, V>) (cache, entry) -> exceptionCollector.runAndCatch(() -> {
        expiryCount.incrementAndGet();
        int valueIndex = values.toIndex(entry.getValue());
        long delta = clock().ticks() - startTime.get();
        assertThat(valueIndex + lagTimeTicks).isGreaterThanOrEqualTo(delta);
      })));
    startTime.set(now());
    keys.get(timeSpan);
    values.get(timeSpan);
    for (int i = 0; i < timeSpan; i++) {
      int idx = i;
      invoke(keys.get(i), entry ->
          entry.setValue(values.get(idx))
            .setExpiryTime(startTime.get() + idx));
    }
    for (int i = 0; i < timeSpan + lagTimeTicks; i++) {
      sleep(i - (now() - startTime.get()));
      sleep(0);
    }
    exceptionCollector.assertNoException();
    assertThat(expiryCount.get()).isEqualTo(timeSpan);
  }

  @Test
  public void maxDelayIsLagTime2() {
    long lagTimeTicks = 31;
    int count = (int) lagTimeTicks * 3;
    AtomicLong startTime = new AtomicLong();
    Duration expiryAfterWrite = Duration.ofMillis(1234);
    init(b -> b
      .entryCapacity(count * 2)
      .expireAfterWrite(expiryAfterWrite)
      .timerLag(clock().ticksToMillisCeiling(lagTimeTicks), TimeUnit.MILLISECONDS));
    startTime.set(now());
    for (int i = 0; i < count; i++) {
      sleep(i);
      put(k0, v0);
      assertThat(scheduler.getScheduleCount()).isEqualTo(i + 1);
      assertThat(containsKey(k0)).isTrue();
      sleep(expiryAfterWrite);
      sleep(lagTimeTicks);
      sleep(-1);
      assertThat(containsKey(k0)).isFalse();
    }
  }

  @Test
  public void testOneScheduleWithinLag() {
    long lagTimeTicks = 100;
    init(b -> b
      .expireAfterWrite(10, TimeUnit.MILLISECONDS)
      .timerLag(clock().ticksToMillisCeiling(lagTimeTicks), TimeUnit.MILLISECONDS));
    put(k0, v0);
    sleep(1);
    put(k0, v0);
    sleep(1);
    put(k0, v0);
    sleep(1);
    assertThat(scheduler.getScheduleCount()).isEqualTo(1);
    sleep(lagTimeTicks * 2);
    sleep(0);
    assertThat(scheduler.getScheduleCount()).isEqualTo(1);
  }

  @Test
  public void testOneScheduleAfterLag() {
    long lagTimeTicks = 100;
    init(b -> b
      .expireAfterWrite(1000, TimeUnit.MILLISECONDS)
      .timerLag(clock().ticksToMillisCeiling(lagTimeTicks), TimeUnit.MILLISECONDS));
    put(k0, v0);
    sleep(50);
    put(k0, v0);
    sleep(50);
    put(k0, v0);
    sleep(50);
    put(k0, v0);
    sleep(50);
    put(k0, v0);
    sleep(50);
    put(k0, v0);
    sleep(50);
    assertThat(scheduler.getScheduleCount()).isEqualTo(1);
    sleep(5432);
    sleep(0);
    assertThat(scheduler.getScheduleCount()).isEqualTo(2);
  }

  @Test
  public void reschedule() {
    long lagTimeTicks = 100;
    init(b -> b
      .timerLag(clock().ticksToMillisCeiling(lagTimeTicks), TimeUnit.MILLISECONDS));
    invoke(k0, entry ->
      entry.setValue(v0).setExpiryTime(now() + 500));
    invoke(k1, entry ->
      entry.setValue(v1).setExpiryTime(now() + 401));
    assertThat(containsKey(k1)).isTrue();
    sleep(401 + lagTimeTicks);
    sleep(-1);
    assertThat(containsKey(k1)).isFalse();
    sleep(lagTimeTicks);
    sleep(0);
    assertThat(containsKey(k0)).isFalse();
  }

  static class CountingScheduler implements Scheduler {
    Scheduler scheduler;
    LongAdder scheduleCount = new LongAdder();

    public CountingScheduler(Scheduler scheduler) {
      this.scheduler = scheduler;
    }

    @Override
    public void schedule(Runnable runnable, long delayMillis) {
      scheduleCount.increment();
      scheduler.schedule(runnable, delayMillis);
    }

    @Override
    public void execute(Runnable command) {
      scheduler.execute(command);
    }

    public long getScheduleCount() {
      return scheduleCount.sum();
    }

  }

}
