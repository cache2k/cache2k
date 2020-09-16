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
 */
public class SimpleTimerImpl extends SimpleTimer {

  private final Lock lock = new ReentrantLock();
  private final InternalClock clock;
  private final Scheduler scheduler;

  private final TimerStructure structure = new QueueTimerStructure();

  private final Runnable reachedJob = new Runnable() {
    @Override
    public void run() {
      timeReachedEvent(clock.millis());
    }
  };

  /**
   * Creates a new timer whose associated thread has the specified name,
   * and may be specified to
   * {@linkplain Thread#setDaemon run as a daemon}.
   *
   * @throws NullPointerException if {@code name} is null
   * @since 1.5
   */
  public SimpleTimerImpl(InternalClock c) {
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
   *
   * @throws IllegalArgumentException if <tt>time</tt> is negative.
   * @throws IllegalStateException if task was already scheduled or
   *         cancelled, timer was cancelled, or timer thread terminated.
   * @throws NullPointerException if {@code task} is null
   */
  @Override
  public void schedule(SimpleTimerTask task, long time) {
    if (time < 0) {
      throw new IllegalArgumentException("Illegal execution time.");
    }
    if (!task.schedule()) {
      throw new IllegalStateException("Task already scheduled or cancelled");
    }
    lock.lock();
    try {
      if (structure.schedule(task, time)) {
        pacedSchedule(time);
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void cancel(SimpleTimerTask t) {
    lock.lock();
    try {
      structure.cancel(t);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Terminates all timer tasks current pending.
   */
  @Override
  public void cancel() {
    lock.lock();
    try {
      structure.cancel();
    } finally {
      lock.unlock();
    }
  }

  void timeReachedEvent(long currentTime) {
    while (true) {
      SimpleTimerTask task;
      lock.lock();
      try {
        task = structure.removeNextToRun(currentTime);
      } finally {
        lock.unlock();
      }
      if (task != null) {
        task.run();
      } else {
        long nextTime;
        lock.lock();
        try {
          nextTime = structure.nextRun();
        } finally {
          lock.unlock();
        }
        if (nextTime > 0) {
          pacedSchedule(nextTime);
        }
        break;
      }
    }
  }

  void pacedSchedule(long nextWakeup) {
    scheduler.schedule(reachedJob, nextWakeup);
  }

}
