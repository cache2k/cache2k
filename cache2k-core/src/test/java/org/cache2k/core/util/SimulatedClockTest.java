package org.cache2k.core.util;

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

import static org.junit.Assert.*;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Jens Wilke
 */
public class SimulatedClockTest {

  private SimulatedClock clock = new SimulatedClock(100000);

  @Test(timeout = 10000)
  public void create() throws InterruptedException {
    assertEquals(100000, clock.millis());
    final AtomicBoolean TRIGGER = new AtomicBoolean();
    clock.schedule(new Runnable() {
      @Override
      public void run() {
        TRIGGER.set(true);
      }
    }, 100005);
    clock.sleep(10);
    assertTrue(TRIGGER.get());
  }

  @Test(timeout = 10000)
  public void waitSomeMillis() throws Exception {
    clock.sleep(1);
  }

  @Test(timeout = 10000)
  public void clockAdvancing() throws Exception {
    long t0 = clock.millis();
    while (clock.millis() == t0) { }
    assertTrue(clock.millis() > t0);
  }

  @Test(timeout = 10000)
  public void wait123() throws Exception {
    final CountDownLatch _wakeup = new CountDownLatch(2);
    Thread t1 = new Thread() {
      @Override
      public void run() {
        try {
          clock.sleep( 7);
          _wakeup.countDown();
        } catch (InterruptedException ex) {
          ex.printStackTrace();
        }
      }
    };
    t1.start();
    Thread t2 = new Thread() {
      @Override
      public void run() {
        try {
          clock.sleep( 12);
          _wakeup.countDown();
        } catch (InterruptedException ex) {
          ex.printStackTrace();
        }
      }
    };
    t2.start();
    _wakeup.await();
  }

}
