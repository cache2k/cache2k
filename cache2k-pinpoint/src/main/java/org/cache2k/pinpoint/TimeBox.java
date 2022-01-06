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

import java.util.Date;

/**
 * Execute a piece of work and assertions. The assertions are only
 * respected if everything happens in a given time box.
 *
 * @author Jens Wilke
 */
public class TimeBox {

  private final PinpointClock clock;
  private final long startTime;
  private final long timeBox;

  public TimeBox(PinpointClock clock, long timeBox) {
    this.clock = clock;
    startTime = clock.ticks();
    this.timeBox = timeBox;
  }

  /**
   * Always executes the runnable. This method serves the purpose to make the
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
    long now = clock.ticks();
    long delta = now - startTime;
    boolean withinTimeBox = delta < timeBox;
    if (!withinTimeBox) {
      return new Noop();
    }
    if (failedAssertion != null) {
      throw new PropagateAssertionError(startTime, delta, failedAssertion);
    }
    return new Chain();
  }

  public class Chain {

    /**
     * Span new time box, if previous time box was met and no exception occurred.
     */
    public TimeBox within(long timeBox) {
      return new TimeBox(clock, timeBox);
    }

  }

  private class Noop extends Chain {

    @Override
    public TimeBox within(long timeBox) {
      return new TimeBox(clock, 0) {
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
      super("Assertion failed at start time " + (new Date(startTime)) + " after " +  delta + "ms", cause);
    }

  }

}
