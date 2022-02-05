package org.cache2k.testing;

/*-
 * #%L
 * cache2k testing
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

import org.cache2k.operation.Scheduler;
import org.cache2k.operation.TimeReference;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
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
public class SimulatedClock extends TimeReference.Milliseconds implements Scheduler {

  static final ThreadLocal<Boolean> EXECUTOR_CONTEXT = ThreadLocal.withInitial(() -> false);

  /**
   * Current time of the clock in ticks.
   */
  final AtomicLong now;

  /**
   * Use a fair lock implementation to ensure that there is no bias towards
   * the progress thread.
   */
  private final ReentrantLock structureLock = new ReentrantLock();

  /**
   * The tree sorts the timer events.
   * Guarded by: {@link #structureLock}.
   */
  private final TreeMap<Event, Event> tree = new TreeMap<>();

  /**
   * Every time we look on the clock we count this.
   */
  private final AtomicLong clockReadingCounter = new AtomicLong();

  /**
   * Clock makes a millisecond progress after this many readings.
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
  private final int jobExecutionLagTicks;

  /**
   * Executor used to move the clock. If ticks() moves the clock, we need
   * to do that in a separate executor, because that may trigger timer events.
   * Otherwise, that can mean a deadlock since ticks() is called within
   * the cache when a lock is held.
   */
  private final Executor advanceExecutor = wrapExecutor(ForkJoinPool.commonPool());

  /**
   * Count timer events. Guarded by lock.
   */
  private int eventSequenceCount;

  private final boolean deterministic;

  /**
   * Create a clock with the initial time.
   *
   * @param initialTicks initial time in millis since epoch, can start 0 for easy debugging
   */
  public SimulatedClock(boolean deterministic, long initialTicks) {
    now = new AtomicLong(initialTicks);
    this.deterministic = deterministic;
    if (deterministic) {
      jobExecutionLagTicks = 0;
    } else {
      jobExecutionLagTicks = (int) (System.currentTimeMillis() % 2);
    }
  }

  public SimulatedClock(long initialTicks) {
    this(false, initialTicks);
  }

  public SimulatedClock() {
    this(1000000);
  }

  public void schedule(Runnable runnable, long delayMillis) {
    long ticks = rawTicks() + toTicks(Duration.ofMillis(delayMillis));
    if (ticks < 0) {
      schedule(runnable, Long.MAX_VALUE, Long.MAX_VALUE);
      return;
    }
    schedule(runnable, ticks, ticks + jobExecutionLagTicks);
  }

  private void schedule(Runnable runnable, long requestedMillis, long millis) {
    structureLock.lock();
    try {
      Event event = new Event(eventSequenceCount++, requestedMillis, millis, runnable);
      tree.put(event, event);
    } finally {
      structureLock.unlock();
    }
  }

  @Override
  public void execute(Runnable command) {
    schedule(command, 0, 0);
  }

  final Runnable advance = () -> progressAndRunEvents(rawTicks() + 1);

  /**
   * Returns the current simulated time. Schedules a clock progress by one milli if
   * this was called {@value CLOCK_READING_PROGRESS} times.
   */
  @Override
  public long ticks() {
    if (!deterministic && clockReadingCounter.incrementAndGet() % CLOCK_READING_PROGRESS == 0) {
      advanceExecutor.execute(advance);
    }
    return rawTicks();
  }

  long rawTicks() {
    return now.get();
  }

  /**
   * A sleep of {@code 0} waits an undefined amount of time, running scheduled tasks.
   * A value greater then {@code 0}s advances the time just by the specified amount.
   */
  @Override
  public void sleep(long ticks) throws InterruptedException {
    if (EXECUTOR_CONTEXT.get()) {
      sleepInExecutor(ticks);
      return;
    }
    if (ticks < 0) {
      waitForTaskExecution();
      return;
    }
    if (ticks == 0) {
      sleep0();
      return;
    }
    long wakeupTime = ticks() + ticks;
    progressAndRunEvents(wakeupTime);
  }

  /**
   * When run from within an executor, don't make time progress.
   * We wait until another thread (the testing thread) moves time forward.
   * This is not heavily used. Tests we sleep in executors should be
   * examined and may be replaced.
   */
  private void sleepInExecutor(long ticks) throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    schedule(latch::countDown, ticksToMillisCeiling(ticks - rawTicks()));
    latch.await(3, TimeUnit.MILLISECONDS);
  }

  /**
   * Sleep(0) means sleep until something happens, so we
   * wait for tasks to be executed or the next timer event.
   */
  private void sleep0() throws InterruptedException {
    if (tasksWaitingForExecution.get() > 0) {
      waitForTaskExecution();
      return;
    }
    long nextTime = progressAndRunEvents(-1);
    if (nextTime >= 0) {
      progressAndRunEvents(nextTime);
      return;
    }
    moveForward(rawTicks() + 1);
  }

  private void waitForTaskExecution() throws InterruptedException {
    while (tasksWaitingForExecution.get() > 0) {
      synchronized (parallelExecutionsWaiter) {
        parallelExecutionsWaiter.wait(5);
      }
    }
  }

  public Executor wrapExecutor(Executor ex) {
    return new WrappedExecutor(ex);
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
   * @param time time until events should be notified, inclusive.
   *             -1 to execute no events and return next scheduled time
   * @return next schedule time or -1 if no more events
   */
  private long progressAndRunEvents(long time) {
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
            moveForward(time);
            return u.time;
          }
        } else {
          break;
        }
        moveForward(u.time);
      } finally {
        structureLock.unlock();
      }
      runEvent(e.getKey());
    }
    moveForward(time);
    return -1;
  }

  private void runEvent(Event e) {
    e.action.run();
  }

  /**
   * Test code can simulate concurrent clock movements
   */
  protected void injectConcurrentAdvancing() { }

  /**
   * Update clock to the given time. Make sure to move the clock only forward and
   * update only if not moved forward by somebody else. This is called
   * with a time in the future, if nothing will happen in between.
   */
  void moveForward(long time) {
    long currentTime = rawTicks();
    injectConcurrentAdvancing();
    while (currentTime < time) {
      if (now.compareAndSet(currentTime, time)) {
        break;
      }
      currentTime = rawTicks();
      injectConcurrentAdvancing();
    }
  }

  public String toString() {
    structureLock.lock();
    try {
      return "clock#" + Integer.toString(hashCode(), 36) +
        "{time=" + now + ", tasksWaitingForExecution=" +
        tasksWaitingForExecution.get() + ", events=" + tree + "}";
    } finally {
      structureLock.unlock();
    }
  }

  static class Event implements Comparable<Event> {

    final int number;
    final long requestedWakeupTime;
    final long time;
    final Runnable action;

    Event(int sequenceNumber, long requestedWakeupTime, long time, Runnable action) {
      this.number = sequenceNumber;
      this.time = time;
      this.requestedWakeupTime = requestedWakeupTime;
      this.action = action;
    }

    @Override
    public int compareTo(Event o) {
      if (time < o.time) {
        return -1;
      }
      if (time > o.time) {
        return 1;
      }
      return o.number - number;
    }

    public String toString() {
      return "Event#" + number + "{" +
        "requestedWakeupTime=" + timeToString(requestedWakeupTime) +
        ", time=" + timeToString(time) + "}";
    }
  }

  static String timeToString(long time) {
    return time == Long.MAX_VALUE ? "eternal" : Long.toString(time);
  }

  private void taskFinished() {
    int v = tasksWaitingForExecution.decrementAndGet();
    if (v == 0) {
      synchronized (parallelExecutionsWaiter) {
        parallelExecutionsWaiter.notifyAll();
      }
    }
  }

  public int getTasksWaitingForExecutionCount() {
    return tasksWaitingForExecution.get();
  }

  class WrappedExecutor implements Executor {

    private final Executor executor;

    WrappedExecutor(Executor ex) {
      executor = ex;
    }

    @Override
    public void execute(Runnable r) {
      tasksWaitingForExecution.incrementAndGet();
      try {
        executor.execute(() -> {
          EXECUTOR_CONTEXT.set(true);
          try {
            r.run();
          } finally {
            taskFinished();
          }
        });
      } catch (RejectedExecutionException ex) {
        taskFinished();
        throw new RejectedExecutionException(ex);
      }
    }

    @Override
    public String toString() {
      return "WrappedExecutor{clock=" + Integer.toString(SimulatedClock.this.hashCode(), 36)
        + ", executor=" + executor + '}';
    }
  }

}
