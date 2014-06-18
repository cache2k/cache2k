package org.cache2k.impl.threading;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2014 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import org.cache2k.util.TunableConstants;

import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A thread pool to be shared amount several client caches for different purposes.
 * The pool creates a new thread whenever a new task is submitted but one task is
 * still in the queue and waiting for execution. After reaching a hard thread limit
 * new submissions fill the queue and stall if the queue is full. The hard limit is
 * only a precaution and not to be intended to be reached within normal operation.
 *
 * <p>The general idea is that a thread limit adapted to a specific use case
 * is introduced on top, so the thread pool is not used directly but by using a
 * {@link org.cache2k.impl.threading.LimitedPooledExecutor} which provides an
 *  {@link java.util.concurrent.ExecutorService} interface.
 *
 * <p>After some time waiting a pool thread will die. If there is no work to be
 * done no thread will be kept alive. Instead of defining a low pool size to have
 * some threads always available for the typical workloads, each thread waits for
 * a randomized idle time up to 30 minutes until it dies. This way the amount of
 * threads staying in the pool adapts to the workload itself (hopefully...).
 *
 * @see org.cache2k.impl.threading.LimitedPooledExecutor
 * @see java.util.concurrent.ExecutorService
 * @author Jens Wilke; created: 2014-05-12
 */
public class GlobalPooledExecutor {

  private static final Task<?> CLOSE_TASK = new Task<>();
  private static final Tunables TUNABLES = new Tunables();
  private static final ProgressNotifier DUMMY_NOTIFIER = new DummyNotifier();

  private int peakThreadCount = -1;
  private Random delayRandom = new Random(new SecureRandom().nextLong());
  private int threadCount;
  private int diedThreadCount;
  private BlockingQueue<Task<?>> taskQueue;
  private boolean closed;
  private Tunables tunables;
  private ThreadFactory factory;

  /**
   *
   * @param _name used for the thread name prefix.
   */
  public GlobalPooledExecutor(String _name) {
    this(TUNABLES, _name);
  }

  GlobalPooledExecutor() {
    this((String) null);
  }

  GlobalPooledExecutor(Tunables t) {
    this(t, null);
  }

  GlobalPooledExecutor(Tunables t, String _threadNamePrefix) {
    tunables = t;
    taskQueue = new ArrayBlockingQueue<>(tunables.queueSize);
    factory = tunables.threadFactoryFactory.newThreadFactory(_threadNamePrefix);
  }

  public void execute(Runnable r) throws InterruptedException, TimeoutException  {
    execute(r, DUMMY_NOTIFIER);
  }

  public <V> Future<V> execute(Callable<V> c) throws InterruptedException, TimeoutException  {
    return execute(c, DUMMY_NOTIFIER);
  }

  public void execute(final Runnable r, ProgressNotifier n)
    throws InterruptedException, TimeoutException {
    Callable<Void> c = new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        r.run();
        return null;
      }
    };
    execute(c, n);
  }

  public <V> Future<V> execute(Callable<V> c, ProgressNotifier n)
    throws InterruptedException, TimeoutException {
    return execute(c, n, Long.MAX_VALUE);
  }

  public <V> Future<V> execute(Callable<V> c, ProgressNotifier n, long _timeoutMillis)
    throws InterruptedException, TimeoutException {
    if (closed) {
      throw new IllegalStateException("pool was shut down");
    }
    Task<V> t = new Task<>(c, n);
    int cnt = getThreadInUseCount();
    if (cnt > 0) {
      if (taskQueue.size() == 0) {
        return queue(t, _timeoutMillis);
      }
      if (cnt >= tunables.hardLimitThreadCount) {
        return queue(t, _timeoutMillis);
      }
    }
    Thread thr = factory.newThread(new ExecutorThread());
    synchronized (this) {
      threadCount++;
    }
    thr.start();
    cnt = getThreadInUseCount();
    if (cnt < peakThreadCount) {
      peakThreadCount = cnt;
    }
    return queue(t, _timeoutMillis);
  }

  private <V> Future<V> queue(Task<V> t, long _timeoutMillis)
    throws InterruptedException, TimeoutException {
    boolean _queued = taskQueue.offer(t, _timeoutMillis, TimeUnit.MILLISECONDS);
    if (_queued) {
      return t;
    }
    throw new TimeoutException();
  }

  public void waitUntilAllDied() {
    int _delta;
    for (;;) {
      synchronized (this) {
        _delta = threadCount - diedThreadCount;
      }
      if (_delta == 0) {
         break;
      }
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
      }
    }
  }

  /**
   * Remove pending jobs from the task queue and stop threads in the pool.
   * Threads which run jobs will finish them.
   */
  public synchronized void close() {
    if (!closed) {
      closed = true;
      taskQueue.clear();
      taskQueue.add(CLOSE_TASK);
    }
  }

  public int getTotalStartedThreadCount() {
    return threadCount;
  }

  public int getThreadInUseCount() {
    return threadCount - diedThreadCount;
  }

  public int getDiedThreadCount() {
    return diedThreadCount;
  }

  public boolean wasHardLimitReached() {
    return peakThreadCount >= tunables.hardLimitThreadCount;
  }

  public int getPeakThreadCount() {
    return peakThreadCount;
  }

  public interface ProgressNotifier {

    void taskStarted();
    void taskFinished();

  }

  private static class Task<V> implements Future<V> {

    ProgressNotifier progressNotifier;
    boolean done = false;
    V result;
    Callable<V> callable;

    Task() { }

    Task(Callable<V> _callable, ProgressNotifier _progressNotifier) {
      callable = _callable;
      progressNotifier = _progressNotifier;
    }

    synchronized void done(V _result) {
      result = _result;
      done = true;
      notify();
    }

    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
      boolean f = callable != null;
      callable = null;
      return f;
    }

    @Override
    public boolean isCancelled() {
      return callable == null;
    }

    @Override
    public boolean isDone() {
      return done;
    }

    @Override
    public synchronized V get() throws InterruptedException, ExecutionException {
      while (!done) {
        wait();
      }
      return result;
    }

    @Override
    public synchronized V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      wait(unit.toMillis(timeout));
      if (!done) {
        throw new TimeoutException();
      }
      return result;
    }
  }

  /**
   * Factory which names the threads uniquely.
   */
  public static class GlobalThreadFactory implements ThreadFactory {

    AtomicInteger threadCount = new AtomicInteger();
    String prefix = "cache2k#";

    public GlobalThreadFactory(String _threadNamePrefix) {
      if (_threadNamePrefix != null) {
        this.prefix = _threadNamePrefix;
      }
    }

    @Override
    public Thread newThread(Runnable r) {
      int id = threadCount.getAndIncrement();
      Thread thr = new Thread(r);
      thr.setName(prefix + Integer.toString(id, 36));
      thr.setDaemon(true);
      return thr;
    }

  }

  public interface ThreadFactoryFactory {

    ThreadFactory newThreadFactory(String namePrefix);

  }

  public static class DefaultThreadFactoryFactory
    implements ThreadFactoryFactory {

    @Override
    public ThreadFactory newThreadFactory(String _namePrefix) {
      return new GlobalThreadFactory(_namePrefix);
    }
  }

  private class ExecutorThread implements Runnable {

    int waitTime =
      (tunables.randomizeIdleTime ? delayRandom.nextInt(tunables.randomIdleTimeMillis) : 0) +
      tunables.idleTimeMillis;

    @Override
    public void run() {
      try {
        Task t;
        for (;;) {
          t = taskQueue.poll(waitTime, TimeUnit.MILLISECONDS);
          if (t == CLOSE_TASK) {
            taskQueue.put(t);
            return;
          }
          if (t != null) {
            t.progressNotifier.taskStarted();
            t.done(t.callable.call());
            t.progressNotifier.taskFinished();
          } else {
            break;
          }
        }
      } catch (InterruptedException ex) {
      } catch (Throwable ex) {
        ex.printStackTrace();
      } finally {
        synchronized (GlobalPooledExecutor.this) {
          diedThreadCount++;
        }
      }
    }

  }

  static class DummyNotifier implements ProgressNotifier {
    @Override
    public void taskStarted() { }

    @Override
    public void taskFinished() { }

  }

  public static class Tunables implements TunableConstants {

    /**
     * Waiting task queue size. Must be greater 0. The executor always
     * queues in a task before starting a new thread. If the hardlimit
     * is reached submitted tasks will be queued in first and than
     * the submission stalls.
     */
    public int queueSize = 3;

    /**
     * Time a thread waits for a next task. Must be greater than zero.
     */
    public int idleTimeMillis = 9876;

    /**
     * A random value gets added to the idle time. A high value, so there
     * is an average amount of threads always available for operations.
     */
    public int randomIdleTimeMillis = 30 * 60 * 1000;

    /**
     * Idle time is extended by a random interval between 0 and {@link #randomIdleTimeMillis}.
     */
    public boolean randomizeIdleTime = true;

    public int hardLimitThreadCount = 100;

    public ThreadFactoryFactory threadFactoryFactory = new DefaultThreadFactoryFactory();

  }

}
