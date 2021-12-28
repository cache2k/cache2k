package org.cache2k.test.util;

/*
 * #%L
 * cache2k core implementation
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

import org.cache2k.operation.TimeReference;
import org.cache2k.pinpoint.TimeBox;
import org.cache2k.test.core.TestingParameters;

/**
 * @author Jens Wilke
 */
public class TimeStepper {

  private final TimeReference clock;

  public TimeStepper(TimeReference clock) {
    this.clock = clock;
  }

  /**
   * Wait for the check become true. With the simulated clock {@code sleep(0)} waits
   * until the executors are finished or the clock moves to next scheduled event time.
   * In other words, for the simulated clock after {@code sleep(0)} always something
   * has happened, so the loop execution is efficient, also it looks like a busy waiting
   * loop. For testing with real clocks the {@code sleep(0)} waits and hopefully gives
   * some CPU time to other tasks.
   */
  public void await(String description, long timeoutMillis, Condition c) {
    long t0 = clock.millis();
    try {
      while (!c.check()) {
        if (t0 + timeoutMillis < clock.millis()) {
          if (description != null) {
            throw new TimeoutException("waiting for " + timeoutMillis + " milliseconds for event '" + description + "'");
          } else {
            throw new TimeoutException("waiting for " + timeoutMillis + " milliseconds");
          }
        }
        clock.sleep(0);
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(ex);
    }
  }

  public void await(long timeoutMillis, Condition c) {
    await(null, timeoutMillis, c);
  }

  /**
   * Wait for an event the maximum test time.
   */
  public void await(String description, Condition c) {
    await(description, TestingParameters.MAX_FINISH_WAIT_MILLIS, c);
  }

  /**
   * Wait for an event the maximum test time, as defined at {@link TestingParameters#MAX_FINISH_WAIT_MILLIS}
   */
  public void await(Condition c) {
    await(null, TestingParameters.MAX_FINISH_WAIT_MILLIS, c);
  }

  /**
   * Never make this an assertion error, because we do not want the
   * timeout being suppressed at {@link TimeBox#expectMaybe(Runnable)}
   */
  static class TimeoutException extends RuntimeException {
    TimeoutException(String message) {
      super(message);
    }
  }

}
