package org.cache2k.testing;

/*-
 * #%L
 * cache2k testing
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

import org.cache2k.pinpoint.ExpectedException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * @author Jens Wilke
 */
public class SimulatedClockTest {

  final SimulatedClock clock = new SimulatedClock(100000);
  final AtomicInteger trigger = new AtomicInteger();

  private long ticksToMillis(long v) {
    return v;
  }

  @Test
  public void scheduleAndTrigger() throws InterruptedException {
    assertThat(clock.ticks()).isEqualTo(100000);
    AtomicBoolean trigger = new AtomicBoolean();
    clock.schedule(() -> trigger.set(true), ticksToMillis(5));
    clock.sleep(10);
    assertThat(trigger.get()).isTrue();
  }

  @Test
  public void sequenceSleep0() throws InterruptedException {
    assertThat(clock.ticks()).isEqualTo(100000);
    clock.schedule(new Event(0), 0);
    clock.schedule(new Event(2), 7);
    clock.schedule(new Event(1), 1);
    assertThat(trigger.get()).isEqualTo(0);
    clock.sleep(0);
    assertThat(trigger.get()).isEqualTo(1);
    clock.sleep(0);
    assertThat(trigger.get()).isEqualTo(2);
    clock.sleep(0);
    assertThat(trigger.get()).isEqualTo(3);
    clock.sleep(0);
    assertThat(trigger.get()).isEqualTo(3);
  }

  @Test
  public void sequence0Sleep0() throws InterruptedException {
    assertThat(clock.ticks()).isEqualTo(100000);
    clock.schedule(new Event(-1), 0);
    clock.schedule(new Event(-1), 0);
    clock.schedule(new Event(-1), 0);
    clock.sleep(0);
    assertThat(trigger.get()).isEqualTo(3);
  }

  @Test
  public void sequenceSleepX() throws InterruptedException {
    assertThat(clock.ticks()).isEqualTo(100000);
    clock.schedule(new Event(0), 0);
    clock.schedule(new Event(3), 9);
    clock.schedule(new Event(2), 7);
    clock.schedule(new Event(1), 1);
    assertThat(trigger.get()).isEqualTo(0);
    clock.sleep(ticksToMillis(10));
    assertThat(trigger.get()).isEqualTo(4);
  }

  @Test
  public void assertionPropagated() {
    assertThatCode(
      () -> {
        clock.schedule(() -> {
          throw new ExpectedException();
        }, 0);
        clock.sleep(0);
      }
    ).isInstanceOf(ExpectedException.class);
  }

  class Event implements Runnable {

    final int expectedTrigger;

    Event(int expectedTrigger) {
      this.expectedTrigger = expectedTrigger;
    }

    @Override
    public void run() {
      int count = trigger.getAndIncrement();
      if (expectedTrigger >= 0) {
        assertThat(count)
          .as("trigger sequence")
          .isEqualTo(expectedTrigger);
      }
    }
  }

  @Test
  public void clockAdvancing() {
    long t0 = clock.ticks();
    while (clock.ticks() == t0) {
    }
    assertThat(clock.ticks() > t0).isTrue();
  }

}
