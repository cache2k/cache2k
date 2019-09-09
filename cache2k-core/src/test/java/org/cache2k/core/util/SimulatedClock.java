package org.cache2k.core.util;

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

import java.io.Closeable;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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

  private final static Log LOG = Log.getLog(SimulatedClock.class);

  final long QUEUE_TOKEN_EXECUTE_WAITING_JOB = -1L;

  final long QUEUE_TOKEN_RESET = -2L;

  private final long initialMillis;

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
   * Hold the progress thread.
   */
  private final Thread progressThread;

  /**
   * queue needs capacity, otherwise we deadlock
   */
  private final BlockingQueue<Long> queue = new ArrayBlockingQueue<Long>(10);

  /**
   * Every time we look on the clock we count this.
   */
  private final AtomicLong clockReadingCounter = new AtomicLong();

  /**
   * Clock makes a milli second progress after this many readings.
   */
  private final static int CLOCK_READING_PROGRESS = 1000;

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

  /**
   * For waiting until reset is done in progress thread.
   */
  final Semaphore resetDone = new Semaphore(0);

  /**
   * Create a clock with the initial time.
   *
   * @param initialMillis initial time in millis since epoch, can start 0 for easy debugging
   * @param skipExecutorWrapping don't wrap executors to wait for concurrent task execution,
   *                              also does not restart clock from 0 after {@link #reset()}
   */
  public SimulatedClock(long initialMillis, boolean skipExecutorWrapping) {
    skipWrapping = skipExecutorWrapping;
    this.initialMillis = initialMillis;
    now = new AtomicLong(initialMillis);
    progressThread = new Thread("warpableclock-progress") {
      @Override
      public void run() {
        progressThread();
      }
    };
    progressThread.setDaemon(true);
    progressThread.start();
  }

  @Override
  public boolean isJobSchedulable() {
    return true;
  }

  @Override
  public TimeReachedJob createJob(final TimeReachedEvent ev) {
    return new TRJ(new Waiter(-1, -1, waiterCounter.getAndIncrement(), ev));
  }

  @Override
  public void schedule(final TimeReachedJob j, final long millis) {
    schedule(j, millis, millis + jobExecutionLagMillis);
  }

  public void schedule(final TimeReachedJob j, final long requestedMillis, final long millis) {
    LOG.debug(now.get() + " job " + millis);
    final TRJ w = (TRJ) j;
    structureLock.lock();
    try {
      tree.remove(w.waiter);
      if ((millis - now.get()) > cutOffDeltaMillis) {
        return;
      }
      w.waiter = new Waiter(requestedMillis, millis, waiterCounter.getAndIncrement(), w.waiter.event);
      tree.put(w.waiter, w.waiter);
    } finally {
      structureLock.unlock();
    }
    if (Thread.currentThread() != progressThread) {
      try {
        if (queue.isEmpty()) {
          queue.put(QUEUE_TOKEN_EXECUTE_WAITING_JOB);
        }
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Override
  public void disableJob(final TimeReachedJob j) {
    final TRJ w = (TRJ) j;
    structureLock.lock();
    try {
      tree.remove(w.waiter);
    } finally {
      structureLock.unlock();
    }
  }

  /**
   * Returns the current simulated time. Schedules a clock progress by one milli if
   * this was called {@value CLOCK_READING_PROGRESS} times.
   */
  @Override
  public long millis() {
    if (clockReadingCounter.incrementAndGet() % CLOCK_READING_PROGRESS == 0) {
      try {
        queue.put(now.get() + 1L);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }
    return now.get();
  }

  private final TimeReachedEvent wakeUpSleep = new TimeReachedEvent() {
    @Override
    public synchronized  void timeIsReached(final long millis) {
      notifyAll();
    }
  };

  /**
   * A sleep of {@code 0} waits an undefined amount of time and makes sure that the next timer
   * events for the next upcoming millisecond are executed. A value greater then {@code 0}s
   * advances the time just by the specified amount.
   */
  @Override
  public void sleep(final long millis) throws InterruptedException {
    if (!running) {
      throw new IllegalStateException();
    }
    long startTime = now.get();
    LOG.debug(now.get() + " sleep(" + millis + ")");
    if (millis == 0) {
      if (isTasksWaiting()) {
        waitForParallelExecutions();
        return;
      }
      synchronized (queue) {
        if (queue.isEmpty()) {
          queue.put(0L);
        }
        queue.wait();
      }
      return;
    }
    long wakeupTime = startTime + millis;
    if (wakeupTime < 0) {
      wakeupTime = Long.MAX_VALUE;
    }
    TimeReachedJob j = createJob(wakeUpSleep);
    queue.put(wakeupTime);
    synchronized (wakeUpSleep) {
      schedule(j, wakeupTime, wakeupTime);
      while (now.get() < wakeupTime) {
        wakeUpSleep.wait(5);
      }
    }
  }

  public Executor wrapExecutor(Executor ex) {
    if (skipWrapping) {
      return ex;
    }
    return new WrappedExecutor(ex);
  }

  public void reset() {
    try {
      queue.put(QUEUE_TOKEN_RESET);
      while (!resetDone.tryAcquire(3, TimeUnit.SECONDS)) {
        System.err.println(this + ": Waiting for clock reset. Time progress thread blocked or stopped.");
      }
    } catch (InterruptedException ex) {
      close();
      Thread.currentThread().interrupt();
    }
  }

  public void close() {
    running = false;
    progressThread.interrupt();
  }

  private void progressThread() {
    try {
      while (running) {
        long token = 0;
        long nextWakeup;
        try {
          synchronized (queue) {
            queue.notifyAll();
          }
          token = queue.take();
        } catch (InterruptedException ex) {
          close();
          break;
        }
        if (token == QUEUE_TOKEN_RESET) {
          resetInProgressThread();
          continue;
        }
        nextWakeup = wakeupWaiters();
        if (token == QUEUE_TOKEN_EXECUTE_WAITING_JOB) {
          continue;
        }
        if (token == 0) {
          if (nextWakeup > now.get()) {
            now.set(nextWakeup);
          } else {
            now.incrementAndGet();
          }
          wakeupWaiters();
          continue;
        }
        while (token > now.get()) {
          long t = (nextWakeup > 0) ? Math.min(nextWakeup, token) : token;
          if (t > now.get()) {
            now.set(t);
          }
          nextWakeup = wakeupWaiters();
        }
      }
    } catch (Throwable t) {
      System.err.println("Clock progress thread exception: " + t);
    }
    System.err.println("Progress thread stopped");
  }

  protected void resetInProgressThread() {
    boolean settleMore;
    do {
      settleMore = false;
      long MAX_WAIT = 5000;
      long t0 = System.currentTimeMillis();
      while (isTasksWaiting()) {
        if ((System.currentTimeMillis() - t0) > MAX_WAIT) {
          close();
          throw new IllegalStateException("Timeout waiting " + MAX_WAIT + " for parallel tasks to finish");
        }
        synchronized (wakeUpSleep) {
          wakeUpSleep.notifyAll();
        }
      }
      long nextWakeup = wakeupWaiters();
      while (nextWakeup > 0) {
        settleMore = true;
        if (nextWakeup > now.get()) {
          now.set(nextWakeup);
        }
        nextWakeup = wakeupWaiters();
      }
    } while (settleMore);
    if (!skipWrapping) {
      waiterCounter.set(0);
      now.set(initialMillis);
    }
    resetDone.release();
  }

  private long wakeupWaiters() {
    while (running) {
      Waiter u;
      Map.Entry<Waiter, Waiter> e;
      structureLock.lock();
      try {
        e = tree.pollFirstEntry();
        if (e != null) {
          u = e.getKey();
          if (u.wakeupTime > now.get()) {
            tree.put(u, u);
            return u.wakeupTime;
          }
        } else {
          break;
        }
      } finally {
        structureLock.unlock();
      }
      LOG.debug(now.get() + " " + e.getKey() + " notify timeout");
      e.getKey().timeIsReached();
    }
    return -1;
  }

  public String toString() {
    structureLock.lock();
    try {
      return "clock{time=" + now + ", tasksWaitingForExecution=" + tasksWaitingForExecution.get() + ", waiters=" + tree + "}";
    } finally {
      structureLock.unlock();
    }
  }

  static class TRJ implements TimeReachedJob {
    Waiter waiter;
    public TRJ(final Waiter w) {
      waiter = w;
    }
  }

  class Waiter implements Comparable<Waiter>, TimeReachedJob {

    final long requestedWakeupTime;
    final long wakeupTime;
    final long uniqueId;
    final TimeReachedEvent event;

    public Waiter(final long requestedWakeupTime, final long wakeupTime,
                  final long uniqueId, TimeReachedEvent event) {
      this.wakeupTime = wakeupTime;
      this.requestedWakeupTime = requestedWakeupTime;
      this.uniqueId = uniqueId;
      this.event = event;
    }

    public void timeIsReached() {
      if (event != null) {
        try {
          event.timeIsReached(requestedWakeupTime);
        } catch (Throwable t) {
          LOG.warn("Error from time reached event", t);
        }
      }
    }

    @Override
    public int compareTo(final Waiter o) {
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

  final void waitForParallelExecutions() {
    if (!isTasksWaiting()) {
      return;
    }
    synchronized (parallelExecutionsWaiter) {
      try {
        while (isTasksWaiting()) {
          parallelExecutionsWaiter.wait(5);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  final boolean isTasksWaiting() {
    return tasksWaitingForExecution.get() > 0;
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

    public WrappedExecutor(final Executor ex) {
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
      } catch(RejectedExecutionException ex) {
        taskFinished();
      }
    }

    @Override
    public String toString() {
      return "WrappedExecutor{totalTasksWaitingInAssociatedClockInstance=" + tasksWaitingForExecution.get() +
        ", executor=" + executor +
        '}';
    }
  }

}
