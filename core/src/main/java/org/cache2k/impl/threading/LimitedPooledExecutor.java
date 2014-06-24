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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link java.util.concurrent.ExecutorService} that forwards the tasks
 * to the {@link org.cache2k.impl.threading.GlobalPooledExecutor}. Keeps track of
 * the number of tasks in flight and limits them. The idea is to have a
 * lightweight ExecutorService implementation for a particular task which
 * has no big creation and shutdown overhead.
 *
 * <p>Right now the thread limit is a fixed value. Maybe we implement an
 * adaptive behaviour, that increases the thread count as long as throughput
 * is increasing, too. Idea: Implementation similar to TCP congestion control?
 * TODO-C: change to adaptive behaviour
 *
 * @author Jens Wilke; created: 2014-05-12
 */
public class LimitedPooledExecutor implements ExecutorService {

  private static final Tunables TUNABLES = new Tunables();

  private GlobalPooledExecutor globalPooledExecutor;
  private MyNotifier notifier;
  private boolean shutdown = false;
  private Tunables tunables;
  private ExceptionListener exceptionListener;

  public LimitedPooledExecutor(GlobalPooledExecutor gpe) {
    this(gpe, TUNABLES);
  }

  public LimitedPooledExecutor(GlobalPooledExecutor gpe, Tunables t) {
    globalPooledExecutor = gpe;
    notifier = new MyNotifier(t.maxThreadCount);
    tunables = t;
  }

  public void setExceptionListener(ExceptionListener exceptionListener) {
    this.exceptionListener = exceptionListener;
  }

  @Override
  public void shutdown() {
    shutdown = true;
  }

  /**
   * Identical to {@link #shutdown}. Since we hand over everything to the
   * global executor, implementing this would mean we need to keep track of
   * out submitted task and finished tasks. Since we have no long running tasks
   * there is no real benefit to implement this.
   */
  @SuppressWarnings("unchecked")
  @Override
  public List<Runnable> shutdownNow() {
    shutdown();
    return Collections.EMPTY_LIST;
  }

  @Override
  public boolean isShutdown() {
    return shutdown;
  }

  @Override
  public boolean isTerminated() {
    return shutdown && notifier.isTerminated();
  }

  @Override
  public boolean awaitTermination(long _timeout, TimeUnit _unit) throws InterruptedException {
    if (!shutdown) {
      throw new IllegalStateException("awaitTermination, expects shutdown first");
    }
    notifier.waitUntilFinishedComplete(_unit.toMillis(_timeout));
    return isTerminated();
  }

  @Override
  public <T> Future<T> submit(Callable<T> c) {
    if (c instanceof NeverRunInCallingTask) {
      notifier.stallIfLimitReached();
    } else {
      if (notifier.isLimitReached()) {
        return stallAndRunInCallingThread(c);
      }
    }
    try {
      Future<T> f = globalPooledExecutor.execute(c, notifier);
      notifier.taskSubmitted();
      return f;
    } catch (InterruptedException ex) {
      return new ExceptionFuture<>(ex);
    } catch (TimeoutException ex) {
      return new ExceptionFuture<>(ex);
    }
  }

  /**
   * When the thread limit is reached, slow down by running the task within
   * the callers thread.
   */
  private <T> Future<T> stallAndRunInCallingThread(Callable<T> c) {
    notifier.taskSubmitted();
    notifier.taskStarted();
    try {
      T _result = c.call();
      return new FinishedFuture<>(_result);
    } catch (Exception ex) {
      return new ExceptionFuture<>(ex);
    } finally {
      notifier.taskFinished();
    }
  }

  @Override
  public <T> Future<T> submit(final Runnable r, final T _result) {
    Callable<T> c = new Callable<T>() {
      @Override
      public T call() throws Exception {
        r.run();
        return _result;
      }
    };
    return submit(c);
  }

  @Override
  public Future<?> submit(Runnable r) {
    return submit(r, DUMMY_OBJECT);
  }

  @Override
  public void execute(Runnable r) {
    submit(r);
  }

  final static Object DUMMY_OBJECT = new Object();

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> _tasks)
    throws InterruptedException {
    if (!tunables.enableUntested) {
      throw new UnsupportedOperationException("untested code");
    }
    List<Future<T>> _list = new ArrayList<>();
    try {
      for (Callable<T> c : _tasks) {
        notifier.stallIfLimitReached();
        Future<T> f = globalPooledExecutor.execute(c, notifier);
        notifier.taskSubmitted();
        _list.add(f);
      }
    } catch (TimeoutException ex) {
    }
    return _list;
  }

  @Override
  public <T> List<Future<T>> invokeAll(
    Collection<? extends Callable<T>> _tasks, long _timeout, TimeUnit _unit)
    throws InterruptedException {
    if (!tunables.enableUntested) {
      throw new UnsupportedOperationException("untested code");
    }
    long now = System.currentTimeMillis();
    long _timeoutMillis = _unit.toMillis(_timeout);
    List<Future<T>> _list = new ArrayList<>();
    try {
      for (Callable<T> c : _tasks) {
        long _restTimeout = _timeoutMillis - (System.currentTimeMillis() - now);
        if (_restTimeout <= 0) {
          break;
        }
        notifier.stallIfLimitReached(_restTimeout);
        _restTimeout = _timeoutMillis - (System.currentTimeMillis() - now);
        if (_restTimeout <= 0) {
          break;
        }
        Future<T> f = globalPooledExecutor.execute(c, notifier, _restTimeout);
        notifier.taskSubmitted();
        _list.add(f);
      }
    } catch (TimeoutException ex) {
    }
    return _list;
  }

  /**
   * Not supported. If needed we can implement this by using a local notifier, to signal
   * when the first task finished.
   */
  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
    throws InterruptedException, ExecutionException {
    throw new UnsupportedOperationException();
  }

  /**
   * Not supported. If needed we can implement this by using a local notifier, to signal
   * when the first task finished.
   */
  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
    throws InterruptedException, ExecutionException, TimeoutException {
    throw new UnsupportedOperationException();
  }

  class MyNotifier implements GlobalPooledExecutor.ProgressNotifier {

    int counter;
    int threadLimit;

    MyNotifier(int _threadLimit) {
      threadLimit = _threadLimit;
    }

    public boolean isTerminated() {
      return counter == 0;
    }

    public synchronized void taskSubmitted() { counter++; }

    @Override
    public void taskStarted() { }

    @Override
    public synchronized void taskFinished() { counter--; notify(); }

    @Override
    public synchronized void taskFinishedWithException(Exception ex) {
      counter--; notify();
      if (exceptionListener != null) {
        exceptionListener.exceptionWasThrown(ex);
      }
    }

    public synchronized void waitUntilNextFinished() throws InterruptedException {
      wait();
    }

    public synchronized void waitUntilNextFinished(long _millis) throws InterruptedException {
      wait(_millis);
    }

    public void waitUntilFinishedComplete() throws InterruptedException {
      synchronized (this) {
        while (counter > 0) {
          waitUntilNextFinished();
        }
      }
    }

    public void waitUntilFinishedComplete(long _millis) throws InterruptedException {
      long t = System.currentTimeMillis();
      long _maxTime = t + _millis;
      if (_maxTime < 0) {
        waitUntilFinishedComplete();
        return;
      }
      synchronized (this) {
        while (counter > 0 && t < _maxTime) {
          long _waitTime = _maxTime - t;
          if (_waitTime <= 0) {
            return;
          }
          waitUntilNextFinished(_waitTime);
          t = System.currentTimeMillis();
        }
      }
    }

    public void stallIfLimitReached(long _millis) {
      long t = System.currentTimeMillis();
      long _maxTime = t + _millis;
      if (_maxTime < 0) {
        stallIfLimitReached();
        return;
      }
      while (isLimitReached() && t < _maxTime) {
        try {
          waitUntilNextFinished(Math.max(_maxTime - t, 0));
          t = System.currentTimeMillis();
        } catch (InterruptedException ex) {
        }
      }
    }

    private boolean isLimitReached() {
      return counter >= threadLimit;
    }

    public void stallIfLimitReached() {
      while (isLimitReached()) {
        try {
          waitUntilNextFinished();
        } catch (InterruptedException ex) {
        }
      }
    }

  }

  static class FinishedFuture<V> implements Future<V> {

    V result;

    FinishedFuture(V result) {
      this.result = result;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return false;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public boolean isDone() {
      return true;
    }

    @Override
    public V get() {
      return result;
    }

    @Override
    public V get(long timeout, TimeUnit unit)  {
      return result;
    }
  }

  static class ExceptionFuture<V> implements Future<V> {

    Exception exception;

    ExceptionFuture(Exception exception) {
      this.exception = exception;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return false;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public boolean isDone() {
      return true;
    }

    @Override
    public V get() throws ExecutionException {
      throw new ExecutionException(exception);
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws ExecutionException {
      throw new ExecutionException(exception);
    }
  }

  /**
   * Marker interface for a Callable to indicate that we need the
   * calling task to continue after the submit and a new thread for
   * the Callable.
   */
  public static interface NeverRunInCallingTask {

  }

  public static interface ExceptionListener {

    void exceptionWasThrown(Exception ex);

  }

  public static class Tunables implements TunableConstants {

    /**
     * For now we use the available processor count as limit throughout.
     * This may have adverse effects, e.g. if a storage on hard disk is
     * starting to many requests in parallel. See outer class documentation.
     */
    public int maxThreadCount = Runtime.getRuntime().availableProcessors();

    /**
     * Enables yet untested code. Default false.
     */
    public boolean enableUntested = false;

  }

}
