package org.cache2k.core.timing;

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

import org.cache2k.core.CacheClosedException;
import org.cache2k.core.api.InternalCacheCloseContext;
import org.cache2k.operation.TimeReference;
import org.cache2k.operation.Scheduler;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Standard timer implementation. Due timer tasks are executed via a scheduler
 * that runs at most every second (lag time, configurable). There is always only
 * one pending scheduler job per timer.
 *
 * @author Jens Wilke
 */
public class DefaultTimer implements Timer {

  /**
   * Default for timer lag in milliseconds
   * @see org.cache2k.Cache2kBuilder#timerLag(long, TimeUnit)
   */
  public static final long DEFAULT_TIMER_LAG_MILLIS = 1003;
  /**
   * Expecting that expiry values between 0 and 15 minutes are very
   * common, we cover these on the first level.
   */
  public static final int DEFAULT_SLOTS_PER_WHEEL = 921;

  private final Lock lock = new ReentrantLock();
  private final TimeReference clock;
  private final Scheduler scheduler;
  private final TimerStructure structure;
  private long nextScheduled = Long.MAX_VALUE;
  /**
   * Lag time to gather timer tasks for more efficient execution.
   */
  private final long lagMillis;

  private final Runnable timerAction = new Runnable() {
    @Override
    public void run() {
      timeReachedEvent(clock.millis());
    }
  };

  public DefaultTimer(TimeReference clock, Scheduler scheduler) {
    this(clock, scheduler, DEFAULT_TIMER_LAG_MILLIS);
  }

  public DefaultTimer(TimeReference clock, Scheduler scheduler, long lagMillis) {
    this(clock, scheduler, lagMillis, DEFAULT_SLOTS_PER_WHEEL);
  }

  public DefaultTimer(TimeReference c, Scheduler scheduler, long lagMillis, int steps) {
    structure = new TimerWheels(c.millis() + 1, lagMillis + 1, steps);
    this.lagMillis = lagMillis;
    this.clock = c;
    this.scheduler = scheduler;
  }

  /**
   * Schedule the specified timer task for execution at the specified
   * time, in milliseconds.
   */
  @Override
  public void schedule(TimerTask task, long time) {
    if (time < 0) {
      throw new IllegalArgumentException("Illegal execution time.");
    }
    if (!task.isUnscheduled()) {
      throw new IllegalStateException("scheduled already");
    }
    if (time == 0) {
      executeImmediately(task);
      return;
    }
    lock.lock();
    try {
      if (structure.schedule(task, time)) {
        rescheduleEventually(time + lagMillis);
        return;
      }
    } finally {
      lock.unlock();
    }
    executeImmediately(task);
  }

  /**
   * Tasks which reached their scheduled time already on insert are executed
   * as soon as possible. Execution is done via executor instead of in the current thread.
   * Alternatively, we could add it to wheel for the next timeslot to execute, which
   * would mean further delay, but is within lag limits.
   */
  private void executeImmediately(TimerTask task) {
    task.time = 0;
    task.execute();
    scheduler.execute(task);
  }

  @Override
  public void cancel(TimerTask t) {
    lock.lock();
    try {
      structure.cancelAll(t);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Lag to gather timer tasks processing. In milliseconds.
   */
  public long getLagMillis() {
    return lagMillis;
  }

  /**
   * Terminates all timer tasks current pending.
   */
  @Override
  public void cancelAll() {
    lock.lock();
    try {
      structure.cancelAll();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void close(InternalCacheCloseContext closeContext) {
    cancelAll();
    closeContext.closeCustomization(clock, "timeReference");
    closeContext.closeCustomization(scheduler, "scheduler");
  }

  /**
   * Called from the scheduler when a scheduled time was reached.
   * Its expected that the time is increasing constantly.
   * Per timer there is only one scheduled event, so this method is not
   * running concurrently
   */
  private void timeReachedEvent(long currentTime) {
    while (true) {
      TimerTask task;
      lock.lock();
      try {
        task = structure.removeNextToRun(currentTime);
      } finally {
        lock.unlock();
      }
      if (task != null) {
        task.execute();
        task.action();
      } else {
        long nextTime;
        lock.lock();
        try {
          nextTime = structure.nextRun();
          schedule(currentTime, nextTime);
        } catch (CacheClosedException ex) {
        } finally {
          lock.unlock();
        }
        break;
      }
    }
  }

  /**
   * Schedule the next time we process expired times. At least wait {@link #lagMillis}.
   * Also marks that no timer job is scheduled if run with -1 as time parameter.
   *
   * @param now the current time for calculations
   * @param time requested time for processing, or -1 if nothing needs to be scheduled
   */
  private void schedule(long now, long time) {
    if (time != Long.MAX_VALUE) {
      long earliestTime = now + lagMillis;
      nextScheduled = Math.max(earliestTime, time);
      scheduler.schedule(timerAction, clock.toMillis(nextScheduled));
    } else {
      nextScheduled = Long.MAX_VALUE;
    }
  }

  /**
   * Reschedule processing. Called when processing needs to be done earlier.
   * Don't schedule when within lag time.
   * We don't cancel a scheduled task. The additional event does not hurt.
   */
  void rescheduleEventually(long time) {
    if (time >= nextScheduled - lagMillis) {
      return;
    }
    nextScheduled = time;
    scheduler.schedule(timerAction, nextScheduled);
  }

}
