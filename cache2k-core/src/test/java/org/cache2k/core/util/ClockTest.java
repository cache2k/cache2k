package org.cache2k.core.util;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

/**
 * @author Jens Wilke
 */
public class ClockTest {

  private InternalClock clock = new WarpableClock(100000);

  @Test(timeout = 10000)
  public void waitSomeMillis() throws Exception {
    clock.sleep(1);
  }

  @Test(timeout = 10000)
  public void clockAdvancing() throws Exception {
    long t0 = clock.millis();
    while (clock.millis() == t0) { }
    Assert.assertTrue(clock.millis() > t0);
  }

  @Test(expected = IllegalStateException.class)
  public void noWaitOutsideExclusive() throws Exception {
    final InternalClock.Notifier n = clock.createNotifier();
    clock.waitMillis(n, 0);
  }

  @Test(timeout = 10000)
  public void waitUntilNotified() throws Exception {
    final CountDownLatch _waiting = new CountDownLatch(1);
    final CountDownLatch _notified = new CountDownLatch(1);
    final InternalClock.Notifier n = clock.createNotifier();
    Thread t = new Thread() {
      @Override
      public void run() {
        try {
          clock.runExclusive(n, new Runnable() {
            @Override
            public void run() {
              _waiting.countDown();
              try {
                clock.waitMillis(n, 0);
                _notified.countDown();
              } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
              }
            }
          });
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      }
    };
    t.start();
    _waiting.await();
    clock.runExclusive(n, new Runnable() {
      @Override
      public void run() {
        n.sendNotify();
      }
    });
    _notified.await();
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
