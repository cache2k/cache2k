package org.cache2k.core.timing;

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

import org.cache2k.core.util.InternalClock;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Standard timer implementation. Due timer tasks are executed via a scheduler
 * that runs approx. every second (lag time, configurable). There always only
 * one pending scheduler job per timer.
 *
 * @author Jens Wilke
 */
public class DefaultTimer implements Timer {

  private final Lock lock = new ReentrantLock();
  private final InternalClock clock;
  private final Scheduler scheduler;
  private final TimerStructure structure = new QueueTimerStructure();
  private long nextScheduled = Long.MAX_VALUE;
  /**
   * Lag time to gather timer tasks for more efficient execution.
   * Default timer lag defined at {@link org.cache2k.core.HeapCache.Tunable#timerLagMillis}
   */
  private long lag;

  private final Runnable timerAction = new Runnable() {
    @Override
    public void run() {
      timeReachedEvent(clock.millis());
    }
  };

  public DefaultTimer(InternalClock c, long lagMillis) {
    lag = lagMillis;
    this.clock = c;
    if (c instanceof Scheduler) {
      scheduler = (Scheduler) clock;
    } else {
      scheduler = DefaultScheduler.INSTANCE;
    }
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
    if (!task.schedule()) {
      throw new IllegalStateException("Task already scheduled or cancelled");
    }
    if (time == 0) {
      scheduler.execute(task);
      return;
    }
    lock.lock();
    try {
      if (structure.schedule(task, time)) {
        reschedule(time);
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void cancel(TimerTask t) {
    lock.lock();
    try {
      structure.cancel(t);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Lag to gather timer tasks processing. In milliseconds.
   */
  public long getLag() {
    return lag;
  }

  /**
   * Terminates all timer tasks current pending.
   */
  @Override
  public void cancelAll() {
    lock.lock();
    try {
      structure.cancel();
    } finally {
      lock.unlock();
    }
  }

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
        task.action();
      } else {
        long nextTime;
        lock.lock();
        try {
          nextTime = structure.nextRun();
          schedule(currentTime, nextTime);
        } finally {
          lock.unlock();
        }
        break;
      }
    }
  }

  /**
   * Schedule the next time we process expired times. At least wait {@link #lag}.
   * Also marks that no timer job is scheduled if run with -1 as time parameter.
   *
   * @param now the current time for calculations
   * @param time requested time for processing, or -1 if nothing needs to be scheduled
   */
  private void schedule(long now, long time) {
    if (time >= 0) {
      long earliestTime = now + lag;
      nextScheduled = Math.max(earliestTime, time);
      scheduler.schedule(timerAction, nextScheduled);
    } else {
      nextScheduled = Long.MAX_VALUE;
    }
  }

  /**
   * Reschedule processing. Called when processing needs to be done earlier.
   * Don't schedule when within lag time.
   * We don't cancel a scheduled task. The additional event does not hurt.
   */
  void reschedule(long nextWakeup) {
    if (nextWakeup >= nextScheduled - lag) {
      return;
    }
    nextScheduled = nextWakeup + lag;
    scheduler.schedule(timerAction, nextScheduled);
  }

}
