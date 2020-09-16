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

import java.io.Closeable;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simulated clock implementation that moves fast when {@link #sleep} is called and
 * is delivering timer events whenever a scheduled time is passed.
 *
 * <p>In case the testing target is starting parallel tasks via an executor, those
 * can be wrapped via {@link #wrapExecutor(Executor)}. If an execution is pending
 * the call to {@link #sleep} will not advance time but wait for the executions.
 *
 * @author Jens Wilke
 */
public class SimulatedClock implements InternalClock, Closeable {

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
   * Set to false on close to stop progress thread and avoid usage.
   */
  private volatile boolean running = true;

  /**
   * Unique number for each waiter object
   */
  private final AtomicLong waiterCounter = new AtomicLong();

  /**
   * The tree sorts the timer events.
   * Locked by: {@link #structureLock}.
   */
  private final TreeMap<Waiter, Waiter> tree = new TreeMap<Waiter, Waiter>();

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
   * don't wrap executors to wait for concurrent task execution,
   * also does not restart clock from 0 after {@link #reset()}
   */
  private final boolean skipWrapping;

  /**
   * Don't expect that we can wait for more than this millis.
   */
  private final long cutOffDeltaMillis = Long.MAX_VALUE >> 10;

  /**
   * Scheduled jobs are executed some millis after the scheduled time was reached.
   * That is to simulate that time passes during execution and that it is not possible
   * to exactly execute at a point in time. Randomized by default.
   */
  private final int jobExecutionLagMillis = (int) (System.currentTimeMillis() % 2);

  private Executor advanceExecutor = null;

  /**
   * Create a clock with the initial time.
   *
   * @param initialMillis initial time in millis since epoch, can start 0 for easy debugging
   * @param skipExecutorWrapping don't wrap executors to wait for concurrent task execution,
   *                              also does not restart clock from 0 after {@link #reset()}
   */
  public SimulatedClock(long initialMillis, boolean skipExecutorWrapping) {
    skipWrapping = skipExecutorWrapping;
    now = new AtomicLong(initialMillis);
  }

  @Override
  public boolean isJobSchedulable() {
    return true;
  }

  @Override
  public void schedule(Runnable runnable, long requestedMillis) {
    long millis = requestedMillis + jobExecutionLagMillis;
    if ((millis - now.get()) > cutOffDeltaMillis) {
      return;
    }
    Waiter waiter =
      new Waiter(requestedMillis, millis, waiterCounter.getAndIncrement(), runnable);
    structureLock.lock();
    try {
      tree.put(waiter, waiter);
    } finally {
      structureLock.unlock();
    }
  }

  Runnable advance = new Runnable() {
    @Override
    public void run() {
      advanceAndWakeupWaiters(now.get() + 1);
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
   * A sleep of {@code 0} waits an undefined amount of time and makes sure that the next timer
   * events for the next upcoming millisecond are executed. A value greater then {@code 0}s
   * advances the time just by the specified amount.
   */
  @Override
  public void sleep(long millis) throws InterruptedException {
    if (!running) {
      throw new IllegalStateException();
    }
    LOG.debug(now.get() + " sleep(" + millis + ")");
    if (millis == 0) {
      if (tasksWaitingForExecution.get() > 0) {
        return;
      }
    }
    long wakeupTime = millis() + millis;
    advanceAndWakeupWaiters(wakeupTime);
  }

  public Executor wrapExecutor(Executor ex) {
    if (skipWrapping) {
      return ex;
    }
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

  public void close() {
    running = false;
  }

  /**
   *
   * @return -1 if no more waiting or next waiter time
   */
  private long advanceAndWakeupWaiters(long time) {
    while (running) {
      Waiter u;
      Map.Entry<Waiter, Waiter> e;
      structureLock.lock();
      try {
        e = tree.pollFirstEntry();
        if (e != null) {
          u = e.getKey();
          if (u.wakeupTime > time) {
            tree.put(u, u);
            advanceClock(time);
            return u.wakeupTime;
          }
        } else {
          break;
        }
        advanceClock(u.wakeupTime);
      } finally {
        structureLock.unlock();
      }
      e.getKey().timeIsReached();
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
        tasksWaitingForExecution.get() + ", waiters=" + tree + "}";
    } finally {
      structureLock.unlock();
    }
  }

  class Waiter implements Comparable<Waiter> {

    final long requestedWakeupTime;
    final long wakeupTime;
    final long uniqueId;
    final Runnable event;

    Waiter(long requestedWakeupTime, long wakeupTime,
                  long uniqueId, Runnable event) {
      this.wakeupTime = wakeupTime;
      this.requestedWakeupTime = requestedWakeupTime;
      this.uniqueId = uniqueId;
      this.event = event;
    }

    public void timeIsReached() {
      if (event != null) {
        try {
          event.run();
        } catch (Throwable t) {
          LOG.warn("Error from time reached event", t);
        }
      }
    }

    @Override
    public int compareTo(Waiter o) {
      if (wakeupTime < o.wakeupTime) {
        return -1;
      }
      if (wakeupTime > o.wakeupTime) {
        return 1;
      }
      return 0;
    }

    public String toString() {
      return "Waiter#" + uniqueId + "{time=" +
        (wakeupTime == Long.MAX_VALUE ? "forever" : Long.toString(wakeupTime))
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
      return "WrappedExecutor{totalTasksWaitingInAssociatedClockInstance=" +
        tasksWaitingForExecution.get() +
        ", executor=" + executor +
        '}';
    }
  }

}
