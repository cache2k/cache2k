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

import org.cache2k.core.timing.Scheduler;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simulated clock implementation that moves fast when {@link #sleep} is called and
 * is delivering timer events whenever a scheduled time is passed.
 *
 * <p>In case the testing target is starting parallel tasks via an executor, those
 * need to be wrapped via {@link #wrapExecutor(Executor)}. If an execution is pending
 * the call to {@link #sleep} will not advance time but wait for the executions.
 *
 * @author Jens Wilke
 */
public class SimulatedClock implements InternalClock, Scheduler {

  private static final Executor DEFAULT_EXECUTOR = Executors.newSingleThreadExecutor();

  private static final Log LOG = Log.getLog(SimulatedClock.class);

  /**
   * Current time of the clock in millis.
   */
  private final AtomicLong now;

  /**
   * Use a fair lock implementation to ensure that there is no bias towards
   * the progress thread.
   */
  private final ReentrantLock structureLock = new ReentrantLock();

  /**
   * The tree sorts the timer events.
   * Guarded by: {@link #structureLock}.
   */
  private final TreeMap<Event, Event> tree = new TreeMap<Event, Event>();

  /**
   * Every time we look on the clock we count this.
   */
  private final AtomicLong clockReadingCounter = new AtomicLong();

  /**
   * Clock makes a milli second progress after this many readings.
   */
  private static final int CLOCK_READING_PROGRESS = 1000;

  /**
   * If an executor is wrapped, the number of tasks that are waiting or executing.
   */
  private final AtomicInteger tasksWaitingForExecution = new AtomicInteger(0);

  /**
   * Lock and wait object, if we want to wait until all parallel tasks are executed.
   */
  private final Object parallelExecutionsWaiter = new Object();

  /**
   * Scheduled jobs are executed some millis after the scheduled time was reached.
   * That is to simulate that time passes during execution and that it is not possible
   * to exactly execute at a point in time. Randomized by default.
   */
  private final int jobExecutionLagMillis = (int) (System.currentTimeMillis() % 2);

  /**
   * Executor used to move the clock.
   */
  private Executor advanceExecutor = DEFAULT_EXECUTOR;

  /**
   * Create a clock with the initial time.
   *
   * @param initialMillis initial time in millis since epoch, can start 0 for easy debugging
   */
  public SimulatedClock(long initialMillis) {
    now = new AtomicLong(initialMillis);
  }

  @Override
  public void schedule(Runnable runnable, long requestedMillis) {
    long millis = requestedMillis + jobExecutionLagMillis;
    Event event =
      new Event(requestedMillis, millis, runnable);
    structureLock.lock();
    try {
      tree.put(event, event);
    } finally {
      structureLock.unlock();
    }
  }

  Runnable advance = new Runnable() {
    @Override
    public void run() {
      advanceAndRunEvents(now.get() + 1);
    }
  };

  /**
   * Returns the current simulated time. Schedules a clock progress by one milli if
   * this was called {@value CLOCK_READING_PROGRESS} times.
   */
  @Override
  public long millis() {
    if (clockReadingCounter.incrementAndGet() % CLOCK_READING_PROGRESS == 0) {
      advanceExecutor.execute(advance);
    }
    return now.get();
  }

  /**
   * A sleep of {@code 0} waits an undefined amount of time.
   * . A value greater then {@code 0}s
   * advances the time just by the specified amount.
   */
  @Override
  public void sleep(long millis) throws InterruptedException {
    if (millis == 0) {
      sleep0();
      return;
    }
    long wakeupTime = millis() + millis;
    advanceAndRunEvents(wakeupTime);
  }

  /**
   * Sleep(0) means sleep until something happens, so we
   * wait for tasks to be executed or the next timer event.
   */
  private void sleep0() throws InterruptedException {
    if (tasksWaitingForExecution.get() > 0) {
      while (tasksWaitingForExecution.get() > 0) {
        synchronized (parallelExecutionsWaiter) {
          parallelExecutionsWaiter.wait(5);
        }
      }
      return;
    }
    long nextTime = advanceAndRunEvents(0);
    if (nextTime >= 0) {
      advanceAndRunEvents(nextTime);
      return;
    }
    advanceClock(now.get() + 1);
  }

  public Executor wrapExecutor(Executor ex) {
    return advanceExecutor = new WrappedExecutor(ex);
  }

  public void reset() {
    while (true) {
      structureLock.lock();
      try {
        tree.clear();
        if (tasksWaitingForExecution.get() == 0) {
          break;
        }
      } finally {
        structureLock.unlock();
      }
    }
  }

  /**
   * Move clock forward and notify waiting events while doing so.
   *
   * @param time time until events should be notified, inclusive
   * @return -1 if no more waiting or next event time
   */
  private long advanceAndRunEvents(long time) {
    while (true) {
      Event u;
      Map.Entry<Event, Event> e;
      structureLock.lock();
      try {
        e = tree.pollFirstEntry();
        if (e != null) {
          u = e.getKey();
          if (u.time > time) {
            tree.put(u, u);
            advanceClock(time);
            return u.time;
          }
        } else {
          break;
        }
        advanceClock(u.time);
      } finally {
        structureLock.unlock();
      }
      e.getKey().runAction();
    }
    advanceClock(time);
    return -1;
  }

  /**
   * Update clock, but only move clock forward.
   */
  private void advanceClock(long time) {
    long currentTime = now.get();
    while (currentTime < time) {
      if (now.compareAndSet(currentTime, time)) {
        break;
      }
      currentTime = now.get();
    }
  }

  public String toString() {
    structureLock.lock();
    try {
      return "clock{time=" + now + ", tasksWaitingForExecution=" +
        tasksWaitingForExecution.get() + ", events=" + tree + "}";
    } finally {
      structureLock.unlock();
    }
  }

  static class Event implements Comparable<Event> {

    final long requestedWakeupTime;
    final long time;
    final Runnable action;

    Event(long requestedWakeupTime, long time, Runnable action) {
      this.time = time;
      this.requestedWakeupTime = requestedWakeupTime;
      this.action = action;
    }

    public void runAction() {
      if (action != null) {
        try {
          action.run();
        } catch (Throwable t) {
          LOG.warn("Error from scheduled event", t);
        }
      }
    }

    @Override
    public int compareTo(Event o) {
      if (time < o.time) {
        return -1;
      }
      if (time > o.time) {
        return 1;
      }
      return 0;
    }

    public String toString() {
      return "Event#" + hashCode() + "{time=" +
        (time == Long.MAX_VALUE ? "forever" : Long.toString(time))
        + "}";
    }
  }

  private void taskFinished() {
    int v = tasksWaitingForExecution.decrementAndGet();
    if (v == 0) {
      synchronized (parallelExecutionsWaiter) {
        parallelExecutionsWaiter.notifyAll();
      }
    }
  }

  class WrappedExecutor implements Executor {

    Executor executor;

    WrappedExecutor(Executor ex) {
      executor = ex;
    }

    @Override
    public void execute(final Runnable r) {
      tasksWaitingForExecution.incrementAndGet();
      try {
        executor.execute(new Runnable() {
          @Override
          public void run() {
            try {
              r.run();
            } finally {
              taskFinished();
            }
          }
        });
      } catch (RejectedExecutionException ex) {
        taskFinished();
        throw new RejectedExecutionException(ex);
      }
    }

    @Override
    public String toString() {
      return "WrappedExecutor{executor=" + executor + '}';
    }
  }

}
