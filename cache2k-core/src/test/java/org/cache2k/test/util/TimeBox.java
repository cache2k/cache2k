package org.cache2k.test.util;

/*
 * #%L
 * cache2k core implementation
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

import org.cache2k.core.util.DefaultClock;
import org.cache2k.core.util.InternalClock;

import java.sql.Timestamp;

/**
 * Execute a piece of work and assertions. The assertions are only
 * respected if everything happens in a given timebox.
 *
 * @author Jens Wilke
 */
public class TimeBox {

  private final InternalClock clock;
  private long startTime;
  private final long timeBox;
  private boolean outcomeUndefined = false;

  public static TimeBox millis(long t) {
    TimeBox b = new TimeBox(DefaultClock.INSTANCE, t);
    return b;
  }

  public static TimeBox seconds(long t) {
    return millis(t * 1000);
  }

  public TimeBox(InternalClock _clock, long _timeBox) {
    clock = _clock;
    startTime = _clock.millis();
    timeBox = _timeBox;
  }

  /**
   * Immediately executes the runnable. This method serves the purpose to make the
   * code look more fluent.
   */
  public TimeBox perform(Runnable r) {
    r.run();
    return this;
  }

  /**
   * Execute the runnable. AssertionErrors will be suppressed if the execution
   * is not happening within the given time box.
   */
  public Chain expectMaybe(Runnable r) {
    AssertionError failedAssertion = null;
    try {
      r.run();
    } catch (AssertionError ex) {
      failedAssertion = ex;
    }
    long ms = clock.millis();
    long delta = ms - startTime;
    boolean withinTimeBox = delta < timeBox;
    if (withinTimeBox) {
      if (failedAssertion != null) {
        throw new PropagateAssertionError(startTime, delta, failedAssertion);
      }
    } else {
      return new Noop();
    }
    return new Chain();
  }

  public class Chain {

    public void concludesMaybe(Runnable r) {
      r.run();
    }

    public TimeBox within(long timeBox) {
      return new TimeBox(clock, timeBox);
    }

  }

  public class Noop extends Chain {

    @Override
    public void concludesMaybe(Runnable r) {
    }

    @Override
    public TimeBox within(long timeBox) {
      return new TimeBox(null, 0) {
        @Override
        public TimeBox perform(Runnable r) {
          return this;
        }

        @Override
        public Chain expectMaybe(Runnable r) {
          return Noop.this;
        }
      };
    }
  }

  public static class PropagateAssertionError extends AssertionError {

    public PropagateAssertionError(long startTime, long delta, Throwable cause) {
      super("Assertion failed at start time " + (new Timestamp(startTime)) + " + " +  delta, cause);
    }

  }

}
