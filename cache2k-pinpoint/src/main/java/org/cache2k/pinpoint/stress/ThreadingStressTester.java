package org.cache2k.pinpoint.stress;

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

import org.cache2k.pinpoint.CaughtInterruptedExceptionError;
import org.cache2k.pinpoint.PinpointParameters;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Jens Wilke
 */
public class ThreadingStressTester {

  static final AtomicInteger threadCounter = new AtomicInteger();

  /**
   * Global thread pool. This might speed up tests with lots of concurrency
   * testing.
   */
  private static final ExecutorService pool =
    Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r);
      t.setDaemon(true);
      t.setName("stress-tester-" + threadCounter.incrementAndGet());
      return t;
    });

  private final List<Runnable> tasks = new ArrayList<>();

  /** Thread array we will use for interruption if time boundary is set */
  private final List<Thread> threads = new CopyOnWriteArrayList<>();

  /** Possible exceptions in the threads */
  private final Map<Thread, Throwable> threadException = new ConcurrentHashMap<>();

  private CountDownLatch startSyncLatch;

  private CountDownLatch allRunningLatch;

  private long startTime;

  private long stopTime;

  private long oneShotTimeoutMillis = PinpointParameters.TIMEOUT.toMillis();

  private long testTimeMillis = 1000;

  private long timeoutAfterInterruptMillis = PinpointParameters.TIMEOUT.toMillis();

  private boolean doNotInterrupt = false;

  private volatile boolean shouldStop = false;

  private boolean disablePrintThreadExceptions = false;

  private PrintStream errorOutput = System.err;

  private CountDownLatch allStoppedLatch;

  private Runnable finalCheck = () -> { };

  private Runnable onError = () -> { };

  /**
   * Executed after test is run to assert end conditions. Only runs if threads
   * did not throw any exceptions.
   */
  public void setFinalCheck(Runnable finalCheck) {
    this.finalCheck = finalCheck;
  }

  /**
   * Executed once after tests if assertions happened, e.g. to print debug state information.
   */
  public void setOnError(Runnable onError) {
    this.onError = onError;
  }

  /**
   * True if threads should stop processing.
   */
  public boolean isShouldStop() {
    return shouldStop;
  }

  public boolean isDoNotInterrupt() {
    return doNotInterrupt;
  }

  /**
   * Don't use {@link Thread#interrupt()} to stop threads from processing.
   */
  public void setDoNotInterrupt(boolean doNotInterrupt) {
    this.doNotInterrupt = doNotInterrupt;
  }

  /**
   *
   * @see #setDisablePrintThreadExceptions(boolean)
   */
  public final boolean isDisablePrintThreadExceptions() {
    return disablePrintThreadExceptions;
  }

  /**
   * By default, all thread exceptions are printed to standard error. This is
   * useful because all exceptions are needed to analyze the problem cause.
   * This can be optionally disabled. If disabled only an exceptions is
   * throws by the run method.
   */
  public final void setDisablePrintThreadExceptions(boolean disablePrintThreadExceptions) {
    this.disablePrintThreadExceptions = disablePrintThreadExceptions;
  }

  /**
   *
   * @see #setErrorOutput(PrintStream)
   */
  public final PrintStream getErrorOutput() {
    return errorOutput;
  }

  /**
   * Alternative output for the printing of the thread exceptions.
   */
  public final void setErrorOutput(PrintStream errorOutput) {
    this.errorOutput = errorOutput;
  }

  /**
   *
   * @see #setOneShotTimeoutMillis(long)
   */
  public final long getOneShotTimeoutMillis() {
    return oneShotTimeoutMillis;
  }

  /**
   * Test timeout in milliseconds. This is the maximum time the runner waits for the
   * tasks. Reaching the timeout is a failure and an exception is thrown.
   * Default is 30 seconds.
   */
  public final void setOneShotTimeoutMillis(long oneShotTimeoutMillis) {
    this.oneShotTimeoutMillis = oneShotTimeoutMillis;
  }

  /**
   *
   * @see #setTestTimeMillis(long)
   */
  public final long getTestTimeMillis() {
    return testTimeMillis;
  }

  /**
   * Time span the test tasks are run. After the time span the thread task will get an interrupt signal.
   * By default, the task execution is looped endless, until time is passed.
   * The default is 1000 milliseconds.
   */
  public final void setTestTimeMillis(long testTimeMillis) {
    this.testTimeMillis = testTimeMillis;
  }

  public long getTimeoutAfterInterruptMillis() {
    return timeoutAfterInterruptMillis;
  }

  /**
   * Timeout to wait for threads stopping after an interrupt signal is sent.
   * Default is 30 seconds.
   */
  public void setTimeoutAfterInterruptMillis(long v) {
    timeoutAfterInterruptMillis = v;
  }

  /**
   * Tasks are only executed once. Derived from test time.
   *
   * @see #setTestTimeMillis(long)
   */
  public final boolean isOneShotMode() {
    return testTimeMillis <= 0;
  }

  /**
   * Enables one shot mode. Tasks will be only executed once.
   * In one shot mode the runner waits until all threads have finished. The maximum
   * time is controlled via {@link #setOneShotTimeoutMillis(long)}.
   */
  public final void setOneShotMode(boolean f) {
    if (f) {
      testTimeMillis = -1;
    }
  }

  /**
   * Add a task that will be executed in parallel with the other tasks.
   */
  public final void addTask(Runnable r) {
    tasks.add(r);
  }

  public final void addExceptionalTask(ExceptionalRunnable r) {
    addTask(() -> {
      try {
        r.run();
      } catch (InterruptedException ex) {
        if (isDoNotInterrupt()) {
          throw new CaughtInterruptedExceptionError(ex);
        } else {
          Thread.currentThread().interrupt();
        }
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
  }

  /**
   * Add the same runnable multiple times, resulting in the amount of threads
   * running the same runnable.
   *
   * @param multipleCount number of threads for this runnable
   */
  public final void addTask(int multipleCount, Runnable r) {
    for (int i = 0; i < multipleCount; i++) {
      tasks.add(r);
    }
  }

  /**
   * Time in millis when all tasks reached runnable state and are just about
   * to execute the work load.
   */
  public final long getStartTime() {
    return startTime;
  }

  /**
   * Time in millis after all tasks stopped executing the workload. In case of a timeout
   * it is the time of the timeout.
   */
  public final long getStopTime() {
    return stopTime;
  }

  public final void run() {
    int taskCount = tasks.size();
    startSyncLatch = new CountDownLatch(taskCount);
    allRunningLatch = new CountDownLatch(taskCount);
    allStoppedLatch = new CountDownLatch(taskCount);
    for (int i = 0; i < taskCount; i++) {
      Runnable r;
      if (isOneShotMode()) {
        r = new OneShotRunWrapper(i, tasks.get(i));
      } else {
        if (doNotInterrupt) {
          r = new ShouldStopRunWrapper(i, tasks.get(i));
        } else {
          r = new RunWrapper(i, tasks.get(i));
        }
      }
      pool.execute(r);
    }
    try {
      allRunningLatch.await();
      startTime = System.currentTimeMillis();
      boolean allStopped;
      boolean timeout = false;
      if (testTimeMillis >= 0) {
        allStopped = allStoppedLatch.await(testTimeMillis, TimeUnit.MILLISECONDS);
      } else {
        allStopped = allStoppedLatch.await(oneShotTimeoutMillis, TimeUnit.MILLISECONDS);
        timeout = !allStopped;
      }
      stopTime = System.currentTimeMillis();
      if (!allStopped) {
        shouldStop = true;
        if (!doNotInterrupt) {
          sendInterrupt();
        }
        boolean timeoutAfterInterrupt = !allStoppedLatch.await(timeoutAfterInterruptMillis, TimeUnit.MILLISECONDS);
        if (timeoutAfterInterrupt) {
          String timeoutReason = "Threads ignoring interrupt signal. " +
            "Timeout after " + (stopTime - startTime ) + " milliseconds.";
          printTimeoutInfo(timeoutReason);
          throw new TimeoutThreadingStressTestException(timeoutReason);
        }
      }
      if (timeout) {
        String timeoutReason = "Timeout after " + (stopTime - startTime ) + " milliseconds.";
        printTimeoutInfo(timeoutReason);
        throw new TimeoutThreadingStressTestException(timeoutReason);
      }
    } catch (InterruptedException e) {
      throw new CaughtInterruptedExceptionError(e);
    }
    if (threadException.isEmpty()) {
      try {
        finalCheck.run();
      } catch (Throwable t) {
        onError.run();
        throw new ThreadingStressTestException(t);
      }
    } else {
      onError.run();
      propagateException();
    }
  }

  private void printTimeoutInfo(String reason) {
    errorOutput.println("*** TIMEOUT ***");
    errorOutput.println(reason);
    errorOutput.println(
      "testTimeMillis=" + testTimeMillis + ", timeoutMillis=" + oneShotTimeoutMillis +
      ", timeoutAfterInterruptMillis=" + timeoutAfterInterruptMillis);
    printTimeoutInfoOnce();
  }

  private void printTimeoutInfoOnce() {
    errorOutput.println(allStoppedLatch.getCount() + " of " + threads.size() + " threads not stopped after receiving the interrupt signal.");
    printThreadExceptions();
    for (Thread t : threads) {
      errorOutput.println("\nThread: " + t + ", id=" + t.getId() + ", state=" + t.getState());
      StackTraceElement[] stackTraceElements = t.getStackTrace();
      for (StackTraceElement el : stackTraceElements) {
        errorOutput.println(t.getId() + "     at " + el);
      }
    }
  }

  private void propagateException() {
    printThreadExceptions();
    throw new AssertionError(
      "Got " + threadException.size() + " thread assertion error(s). First one propagated.",
      threadException.values().iterator().next());
  }

  private void sendInterrupt() {
    for (Thread t : threads) {
      t.interrupt();
    }
  }

  private void printThreadExceptions() {
    if (disablePrintThreadExceptions) {
      return;
    }
    errorOutput.println(threadException.size() + " " + "exceptions detected during test run.");
    for (Throwable t : threadException.values()) {
      errorOutput.println("Thread task exception:");
      t.printStackTrace(errorOutput);
      errorOutput.flush();
    }
  }

  private class RunWrapper implements Runnable {

    int threadIndex;
    Runnable runnable;

    public RunWrapper(int threadIndex, Runnable runnable) {
      this.threadIndex = threadIndex;
      this.runnable = runnable;
    }

    /**
     * Loops until interrupted.
     */
    protected void runWrapped() {
      while (!Thread.interrupted()) {
        runnable.run();
      }
    }

    @Override
    public void run() {
      try {
        threads.add(Thread.currentThread());
        startSyncLatch.countDown();
        startSyncLatch.await();
        allRunningLatch.countDown();
        runWrapped();
      } catch (Throwable t) {
        threadException.put(Thread.currentThread(), t);
      }
      allStoppedLatch.countDown();
    }

  }

  private class OneShotRunWrapper extends RunWrapper {

    public OneShotRunWrapper(int threadIndex, Runnable runnable) {
      super(threadIndex, runnable);
    }

    @Override
    protected void runWrapped() {
      runnable.run();
    }

  }

  private class ShouldStopRunWrapper extends RunWrapper {

    public ShouldStopRunWrapper(int threadIndex, Runnable runnable) {
      super(threadIndex, runnable);
    }

    @Override
    protected void runWrapped() {
      while (!shouldStop) {
        runnable.run();
      }
    }

  }

  static class ThreadingStressTestException extends RuntimeException {

    public ThreadingStressTestException(Throwable cause) {
      super(cause);
    }

    public ThreadingStressTestException(String message) {
      super(message);
    }
  }

  static class TimeoutThreadingStressTestException extends ThreadingStressTestException {

    public TimeoutThreadingStressTestException(String message) {
      super(message);
    }

  }

  @FunctionalInterface
  public interface ExceptionalRunnable {
    void run() throws Exception;
  }

}
