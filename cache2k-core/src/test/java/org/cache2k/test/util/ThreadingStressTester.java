package org.cache2k.test.util;

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

import org.cache2k.test.core.TestingParameters;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
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
    Executors.newCachedThreadPool(new ThreadFactory() {
      @Override
      public Thread newThread(final Runnable r) {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("stress-tester-" + threadCounter.incrementAndGet());
        return t;
      }
    });

  private List<Runnable> tasks = new ArrayList<Runnable>();

  /** Thread array we will use for interruption if time boundary is set */
  private Thread[] threads;

  /** Possible exceptions in the threads */
  private Throwable[] threadException;

  private List<Throwable> exceptions;

  private CyclicBarrier barrier;

  private long startTime;

  private long stopTime;

  private long oneShotTimeoutMillis = TestingParameters.MAX_FINISH_WAIT_MILLIS + 10 * 1000;

  private long testTimeMillis = 1000 * 1;

  private long timeoutAfterInterruptMillis = TestingParameters.MAX_FINISH_WAIT_MILLIS;

  private boolean doNotInterrupt = false;

  private volatile boolean shouldStop = false;

  private Class[] ignoredExceptions = new Class[]{};

  private boolean disablePrintThreadExceptions = false;

  private PrintStream errorOutput = System.err;

  private CountDownLatch allStoppedLatch;

  private Runnable finalCheck = new Runnable() {
    @Override
    public void run() {

    }
  };

  private Runnable onError = new Runnable() {
    @Override
    public void run() {

    }
  };

  public Class[] getIgnoredExceptions() {
    return ignoredExceptions;
  }

  public Runnable getFinalCheck() {
    return finalCheck;
  }

  /**
   * Executed after test is run to assert end conditions. Only runs if threads
   * did not throw any exceptions.
   */
  public void setFinalCheck(Runnable finalCheck) {
    this.finalCheck = finalCheck;
  }

  public Runnable getOnError() {
    return onError;
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
   * Set of exceptions that can be ignored.
   */
  public void setIgnoredExceptions(Class[] v) {
    this.ignoredExceptions = v;
  }

  /**
   *
   * @see #setDisablePrintThreadExceptions(boolean)
   */
  public final boolean isDisablePrintThreadExceptions() {
    return disablePrintThreadExceptions;
  }

  /**
   * By default all thread exceptions are printed to standard error. This is
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
   * Default is {@link TestingParameters#MAX_FINISH_WAIT_MILLIS} plus 10 seconds.
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
   * By default the task execution is looped endless, until time is passed.
   * Setting this to 0, means one shot mode, every task is executed only once.
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
   * Default is {@link TestingParameters#MAX_FINISH_WAIT_MILLIS}.
   */
  public void setTimeoutAfterInterruptMillis(final long v) {
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
   * Enables one shot mode. Tasks will be only executed once. This sets test time to -1.
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

  /**
   * Add the same runnable multiple times, resulting in the amount of threads
   * running the same runnable.
   *
   * @param _multipleCount number of threads for this runnable
   */
  public final void addTask(int _multipleCount, Runnable r) {
    for (int i = 0; i < _multipleCount; i++) {
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

  private boolean ignoreException(final Throwable _exception) {
    Throwable _rootCause = _exception;
    while (_rootCause.getCause() != null) {
      _rootCause = _rootCause.getCause();
    }
    return ignoreException(_exception, _rootCause);
  }
  protected boolean ignoreException(Throwable _exception, Throwable _rootCause) {
    for (Class c : ignoredExceptions) {
      if (_exception.getClass().equals(c)) {
        return true;
      }
      if (_rootCause.getClass().equals(c)) {
        return true;
      }
    }
    return false;
  }

  /**
   * List of thread exceptions. This can be used to customize the error output.
   */
  public final List<Throwable> getThreadExceptions() {
    return exceptions;
  }

  public final void run() {
    int _taskCount = tasks.size();
    threads = new Thread[_taskCount];
    threadException = new Throwable[_taskCount];
    barrier =  new CyclicBarrier(_taskCount + 1);
    allStoppedLatch = new CountDownLatch(_taskCount);
    for (int i = 0; i < _taskCount; i++) {
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
      barrier.await();
      startTime = System.currentTimeMillis();
      boolean _allStopped;
      boolean _timeout = false;
      if (testTimeMillis > 0) {
        _allStopped = allStoppedLatch.await(testTimeMillis, TimeUnit.MILLISECONDS);
      } else {
        _allStopped = allStoppedLatch.await(oneShotTimeoutMillis, TimeUnit.MILLISECONDS);
        _timeout = !_allStopped;
      }
      if (!_allStopped) {
        shouldStop = true;
        if (!doNotInterrupt) {
          sendInterrupt();
        }
        boolean _timeoutAfterInterrupt = !allStoppedLatch.await(timeoutAfterInterruptMillis, TimeUnit.MILLISECONDS);
        stopTime = System.currentTimeMillis();
        if (_timeoutAfterInterrupt) {
          String _timeoutReason = "Threads ignoring interrupt signal. " +
            "Timeout after " + (stopTime - startTime ) + " milliseconds.";
          printTimeoutInfo(_timeoutReason);
          throw new TimeoutThreadingStressTestException(_timeoutReason);
        }
      }
      if (_timeout) {
        String _timeoutReason = "Timeout after " + (stopTime - startTime ) + " milliseconds.";
        printTimeoutInfo(_timeoutReason);
        throw new TimeoutThreadingStressTestException(_timeoutReason);
      }
    } catch (BrokenBarrierException e) {
      throw new UnexpectedThreadingStressTestException(e);
    } catch (InterruptedException e) {
      throw new UnexpectedThreadingStressTestException(e);
    }
    fillExceptionList();

    if (exceptions.isEmpty()) {
      try {
        finalCheck.run();
      } catch (Throwable t) {
        onError.run();
        throw new ThreadingStressTestException(t);
      }
    }
    if (!exceptions.isEmpty() && !ignoreAllExceptions()) {
      onError.run();
      propagateException();
    }
  }

  private void printTimeoutInfo(String _reason) {
    errorOutput.println("*** TIMEOUT ***");
    errorOutput.println(_reason);
    errorOutput.println(
      "testTimeMillis=" + testTimeMillis + ", timeoutMillis=" + oneShotTimeoutMillis +
      ", timeoutAfterInterruptMillis=" + timeoutAfterInterruptMillis);
    printTimeoutInfoOnce();
    try {
      Thread.sleep(234);
    } catch (InterruptedException ignore) {
      Thread.currentThread().interrupt();
    }
    errorOutput.println("\nAfter 234 milliseconds pause: ");
    printTimeoutInfoOnce();
  }

  private void printTimeoutInfoOnce() {
    errorOutput.println(allStoppedLatch.getCount() + " of " + threads.length + " threads not stopped after receiving the interrupt signal.");
    fillExceptionList();
    printThreadExceptions();
    for (Thread t : threads) {
      if (t == null) {
        continue;
      }
      errorOutput.println("\nThread: " + t + ", id=" + t.getId() + ", state=" + t.getState());
      final StackTraceElement[] stackTraceElements = t.getStackTrace();
      for (StackTraceElement el : stackTraceElements) {
        errorOutput.println(t.getId() + "     at " + el);
      }
    }
  }

  private void propagateException() {
    printThreadExceptions();
    if (gotAllAssertionErrors()) {
      throw new AssertionError(
        "Got " + exceptions.size() + " thread assertion error(s). First one propagated.",
        exceptions.get(0));
    } else {
      throw new ThreadingStressTestException(
        "Got " + exceptions.size() + " thread exceptions(s). First one propagated.",
        exceptions.get(0));
    }
  }

  private void fillExceptionList() {
    exceptions = new ArrayList<Throwable>();
    for (Throwable t : threadException) {
      if (t instanceof InterruptedException) {
        continue;
      }
      if (t != null) {
        exceptions.add(t);
      }
    }
  }

  private boolean gotAllAssertionErrors() {
    for (Throwable t : exceptions) {
      if (!(t instanceof AssertionError)) {
        return false;
      }
    }
    return true;
  }

  private boolean ignoreAllExceptions() {
    for (Throwable t : exceptions) {
      if (!ignoreException(t)) {
        return false;
      }
    }
    return true;
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
    errorOutput.println(
      exceptions.size() + " " + "exceptions detected during test run.");
    for (Throwable t : exceptions) {
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
        threads[threadIndex] = Thread.currentThread();
        barrier.await();
        runWrapped();
      } catch (Throwable t) {
        threadException[threadIndex] = t;
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

    public ThreadingStressTestException(String message, Throwable cause) {
      super(message, cause);
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

  static class UnexpectedThreadingStressTestException extends ThreadingStressTestException {

    public UnexpectedThreadingStressTestException(Throwable cause) {
      super(cause);
    }

  }

}
