package org.cache2k.testing;

/*
 * #%L
 * cache2k testing
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
public class SimulatedClockTest {

  final SimulatedClock clock = new SimulatedClock(100000);
  final AtomicInteger trigger = new AtomicInteger();

  @Test(timeout = 10000)
  public void scheduleAndTrigger() throws InterruptedException {
    assertEquals(100000, clock.millis());
    final AtomicBoolean trigger = new AtomicBoolean();
    clock.schedule(new Runnable() {
      @Override
      public void run() {
        trigger.set(true);
      }
    }, 100005);
    clock.sleep(10);
    assertTrue(trigger.get());
  }

  @Test
  public void sequenceSleep0() throws InterruptedException {
    assertEquals(100000, clock.millis());
    clock.schedule(new Event(0), 0);
    clock.schedule(new Event(2), clock.millis() + 7);
    clock.schedule(new Event(1), 123);
    assertEquals(0, trigger.get());
    clock.sleep(0);
    assertEquals(1, trigger.get());
    clock.sleep(0);
    assertEquals(2, trigger.get());
    clock.sleep(0);
    assertEquals(3, trigger.get());
    clock.sleep(0);
    assertEquals(3, trigger.get());
  }

  @Test
  public void sequence0Sleep0() throws InterruptedException {
    assertEquals(100000, clock.millis());
    clock.schedule(new Event(-1), 0);
    clock.schedule(new Event(-1), 0);
    clock.schedule(new Event(-1), 0);
    clock.sleep(0);
    assertEquals(3, trigger.get());
  }

  @Test
  public void sequenceSleepX() throws InterruptedException {
    assertEquals(100000, clock.millis());
    clock.schedule(new Event(0), 0);
    clock.schedule(new Event(3), clock.millis() + 9);
    clock.schedule(new Event(2), clock.millis() + 7);
    clock.schedule(new Event(1), 123);
    assertEquals(0, trigger.get());
    clock.sleep(10);
    assertEquals(4, trigger.get());
  }

  @Test(expected = AssertionError.class)
  public void assertionPropagated() throws InterruptedException {
    clock.schedule(new Runnable() {
      @Override
      public void run() {
        fail("always");
      }
    }, 0);
    clock.sleep(0);
  }

  class Event implements Runnable {

    int expectedTrigger = -1;

    Event(int expectedTrigger) {
      this.expectedTrigger = expectedTrigger;
    }

    @Override
    public void run() {
      int count = trigger.getAndIncrement();
      if (expectedTrigger >= 0) {
        assertEquals("trigger sequence", expectedTrigger, count);
      }
    }
  }

  @Test(timeout = 10000)
  public void clockAdvancing() {
    long t0 = clock.millis();
    while (clock.millis() == t0) { }
    assertTrue(clock.millis() > t0);
  }

}
