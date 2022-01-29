package org.cache2k.core.eviction;

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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.testing.SimulatedClock;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.cache2k.Cache2kBuilder.of;
import static org.cache2k.core.eviction.IdleScan.calculateWakeupIntervalTicks;
import static org.assertj.core.api.Assertions.*;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class IdleScanTest {

  static final long START_OFFSET_MILLIS = 1000;

  @Test
  public void zeroScanTime() {
    assertThatCode(() ->
      Cache2kBuilder.forUnknownTypes().idleScanTime(0, MILLISECONDS)
      ).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void scanningTooSlow() throws InterruptedException {
    SimulatedClock clock = new SimulatedClock(true, START_OFFSET_MILLIS);
    Cache<Integer, Integer> cache =
      Cache2kBuilder.of(Integer.class, Integer.class)
        .timeReference(clock)
        .executor(clock.wrapExecutor(Runnable::run))
        .idleScanTime(1, TimeUnit.MILLISECONDS)
        .strictEviction(true)
        .loader(k -> k)
        .build();
    assertThat(cache.toString()).contains("IDLE");
    cache.put(1, 1);
    cache.put(2, 1);
    cache.put(3, 1);
    cache.put(4, 1);
    clock.sleep(1);
    assertThat(cache.toString()).contains("idleScanPercent=0");
    clock.sleep(1);
    clock.sleep(1);
    assertThat(cache.toString()).contains("IDLE");
  }

  @Test
  public void testIntervalCalculation() {
    assertThat(calculateWakeupIntervalTicks(1000, 10)).isEqualTo(100);
    assertThat(calculateWakeupIntervalTicks(1000, 1111)).isEqualTo(10);
    assertThat(calculateWakeupIntervalTicks(1000, 10_000)).isEqualTo(5);
  }

  @Test
  public void stayIdle() throws InterruptedException {
    SimulatedClock clock = new SimulatedClock(true, START_OFFSET_MILLIS);
    Cache<Integer, Integer> cache =
      Cache2kBuilder.of(Integer.class, Integer.class)
        .timeReference(clock)
        .executor(clock.wrapExecutor(Runnable::run))
        .idleScanTime(1_000, TimeUnit.MILLISECONDS)
        .strictEviction(true)
        .loader(k -> k)
        .build();
    for (int i = 0; i < 5; i++) {
      assertThat(cache.toString()).contains("IDLE");
      clock.sleep(500);
    }
    cache.close();
  }

  @Test
  public void idleScanTwoRounds() throws InterruptedException, ExecutionException {
    SimulatedClock clock = new SimulatedClock(true, START_OFFSET_MILLIS);
    Cache<Integer, Integer> cache =
      of(Integer.class, Integer.class)
        .timeReference(clock)
        .executor(clock.wrapExecutor(Runnable::run))
        .idleScanTime(1_000, MILLISECONDS)
        .strictEviction(true)
        .loader(k -> k)
        .build();
    cache.loadAll(range(1_000, 10)).get();
    clock.sleep(500);
    cache.loadAll(range(2_000, 10)).get();
    clock.sleep(500);
    assertThat(cache.asMap().size()).isEqualTo(20);
    clock.sleep(500);
    assertThat(cache.toString()).contains("idleScanPercent=50");
    assertThat(cache.asMap().size()).isEqualTo(10);
    clock.sleep(500);
    assertThat(cache.asMap().size()).isEqualTo(0);
    assertThat(cache.toString()).contains("idleScanRoundCompleted=1");
    cache.close();
  }

  /**
   * Check that there is compensation for removed entries and the scan is slowed down
   */
  @Test
  public void idleScanTwoRoundsWithRemovals() throws InterruptedException, ExecutionException {
    SimulatedClock clock = new SimulatedClock(true, START_OFFSET_MILLIS);
    Cache<Integer, Integer> cache =
      of(Integer.class, Integer.class)
        .timeReference(clock)
        .executor(clock.wrapExecutor(Runnable::run))
        .idleScanTime(1_000, MILLISECONDS)
        .strictEviction(true)
        .loader(k -> k)
        .build();
    cache.loadAll(range(1_000, 10)).get();
    clock.sleep(500);
    cache.loadAll(range(2_000, 10)).get();
    clock.sleep(500);
    assertThat(cache.asMap().size()).isEqualTo(20);
    cache.removeAll(range(1_000, 10));
    clock.sleep(500);
    assertThat(cache.asMap().size()).isEqualTo(10);
    clock.sleep(500);
    assertThat(cache.asMap().size()).isEqualTo(0);
    cache.close();
  }

  static Iterable<Integer> range(int from, int count) {
    int to = from + count;
    return new AbstractCollection<Integer>() {
      @Override
      public Iterator<Integer> iterator() {
        return new Iterator<Integer>() {
          int i = from;

          @Override
          public boolean hasNext() {
            return i < to;
          }

          @Override
          public Integer next() {
            return i++;
          }
        };
      }

      @Override
      public int size() {
        return count;
      }
    };
  }

}
