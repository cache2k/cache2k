package org.cache2k.test.util;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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

import org.cache2k.core.util.InternalClock;
import org.cache2k.test.core.TestingParameters;

/**
 * @author Jens Wilke
 */
public class TimeStepper {

  private final InternalClock clock;

  public TimeStepper(final InternalClock _clock) {
    clock = _clock;
  }

  /**
   * Wait for the check become true.
   */
  public void await(String _description, final long _timeoutMillis, final Condition c) {
    long t0 = clock.millis();
    try {
      while (!c.check()) {
        if (t0 + _timeoutMillis < clock.millis()) {
          if (_description != null) {
            throw new TimeoutException("waiting for " + _timeoutMillis + " milliseconds for event '" + _description + "'");
          } else {
            throw new TimeoutException("waiting for " + _timeoutMillis + " milliseconds");
          }
        }
        clock.sleep(0);
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(ex);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public void await(long _timeoutMillis, final Condition c) {
    await(null, _timeoutMillis, c);
  }

  /**
   * Wait for an event the maximum test time.
   */
  public void await(String _description, final Condition c) {
    await(_description, TestingParameters.MAX_FINISH_WAIT_MILLIS, c);
  }

  /**
   * Wait for an event the maximum test time, as defined at {@link TestingParameters#MAX_FINISH_WAIT_MILLIS}
   */
  public void await(final Condition c) {
    await(null, TestingParameters.MAX_FINISH_WAIT_MILLIS, c);
  }

  static class TimeoutException extends RuntimeException {
    public TimeoutException(String message) {
      super(message);
    }
  }

}
