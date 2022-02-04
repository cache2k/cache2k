package org.cache2k.pinpoint;

/*-
 * #%L
 * cache2k pinpoint
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

import java.time.Duration;
import java.util.function.Supplier;

/**
 * @author Jens Wilke
 */
public class Await {

  private final YieldCpu yieldCpu;

  public Await(YieldCpu yieldCpu) {
    this.yieldCpu = yieldCpu;
  }

  /**
   * Wait for the check become true. With the simulated clock {@code sleep(0)} waits
   * until the executors are finished or the clock moves to next scheduled event time.
   * In other words, for the simulated clock after {@code sleep(0)} always something
   * has happened, so the loop execution is efficient, also it looks like a busy waiting
   * loop. For testing with real clocks the {@code sleep(0)} waits and hopefully gives
   * some CPU time to other tasks.
   */
  public void await(String description, Duration timeoutDuration, Supplier<Boolean> c) {
    long t = System.currentTimeMillis() + timeoutDuration.toMillis();
    try {
      while (!c.get()) {
        if (System.currentTimeMillis() >= t) {
          throw new TimeoutError(description, timeoutDuration);
        }
        yieldCpu.yield();
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new CaughtInterruptedExceptionError(ex);
    }
  }

  /**
   * Wait for an event the maximum test time.
   */
  public void await(String description, Supplier<Boolean> c) {
    await(description, PinpointParameters.TIMEOUT, c);
  }

  /**
   * Wait for an event the maximum test time
   */
  public void await(Supplier<Boolean> c) {
    await(null, c);
  }

  public interface YieldCpu {
    /**
     * Yield or sleep to wait until condition may be satisfied
     */
    void yield() throws InterruptedException;
  }

}
