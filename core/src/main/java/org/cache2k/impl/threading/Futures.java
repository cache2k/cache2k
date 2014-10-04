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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Jens Wilke; created: 2014-06-03
 */
public class Futures {

  /**
   * A container for futures which waits for all futures to finish.
   * Futures to wait for can be added with {@link #add(java.util.concurrent.Future)}
   * or the constructor. Waiting for all futures to finish is done
   * via {@link #get()}.
   *
   * <p/>The get method call will throw Exceptions from the added futures.
   */
  public static class WaitForAllFuture<V> implements Future<V> {

    List<Future<V>> futureList = new LinkedList<>();

    public WaitForAllFuture(Future<V> _top) {
      add(_top);
    }

    @SafeVarargs
    public WaitForAllFuture(final Future<V>... _top) {
      for (Future<V> f : _top) { add(f); }
    }

    /**
     * Add a new future to the list for Futures we should wait for.
     */
    public synchronized void add(Future<V> f) {
      if (f == null) { return; }
      futureList.add(f);
    }

    /**
     * Send cancel to all futures that are not yet cancelled. Returns
     * true if every future is in cancelled state.
     */
    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
      if (futureList.size() == 0) {
        return false;
      }
      boolean _flag = true;
      for (Future<V> f : futureList) {
        if (!f.isCancelled()) {
          f.cancel(mayInterruptIfRunning);
          _flag &= f.isCancelled();
        }
      }
      return _flag;
    }

    /**
     * Unsupported, it is not possible to implement useful semantics.
     */
    @Override
    public boolean isCancelled() {
      throw new UnsupportedOperationException();
    }

    /**
     * True, if every future is done or no future is contained.
     *
     * <p/>The list of futures is not touched, since an exception
     * may be thrown via get.
     */
    @Override
    public synchronized boolean isDone() {
      boolean _flag = true;
      for (Future<V> f : futureList) {
        _flag &= f.isDone();
      }
      return _flag;
    }

    /**
     * Wait until everything is finished. It may happen that a new future during
     * this method waits for finishing another one. If this happens, we wait
     * for that task also.
     *
     * <p/>All get methods of the futures are executed to probe for possible
     * exceptions. Futures completed without exceptions, will be removed
     * for the list.
     *
     * <p/>Implementation is a bit tricky. We need to call a potential stalling
     * get outside the synchronized block, since new futures may come in in parallel.
     */
    @Override
    public V get() throws InterruptedException, ExecutionException {
      for (;;) {
        Future<V> _needsGet = null;
        synchronized (this) {
          Iterator<Future<V>> it = futureList.iterator();
          while (it.hasNext()){
            Future<V> f = it.next();
            if (!f.isDone()) {
              _needsGet = f;
              break;
            }
            f.get();
            it.remove();
          }
        }
        if (_needsGet != null) {
          _needsGet.get();
          continue;
        }
        return null;
      }
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      long _maxTime = System.currentTimeMillis() + unit.toMillis(timeout);
      if (_maxTime < 0) {
        return get();
      }
      for (;;) {
        Future<V> _needsGet = null;
        synchronized (this) {
          Iterator<Future<V>> it = futureList.iterator();
          while (it.hasNext()){
            Future<V> f = it.next();
            if (!f.isDone()) {
              _needsGet = f;
              break;
            }
            f.get();
            it.remove();
          }
        }
        if (_needsGet != null) {
          long now = System.currentTimeMillis();
          long _waitTime = _maxTime - now;
          if (_waitTime <= 0) {
            throw new TimeoutException();
          }
          _needsGet.get(_maxTime - now, TimeUnit.MILLISECONDS);
          continue;
        }
        return null;
      }
    }

  }

  public static class FinishedFuture<V> implements Future<V> {

    V result;

    public FinishedFuture() {
    }

    public FinishedFuture(V result) {
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

  public static class ExceptionFuture<V> implements Future<V> {

    private Throwable exception;

    public ExceptionFuture(Throwable exception) {
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

  public static abstract class BusyWaitFuture<V> implements Future<V> {

    private int spinMillis = 123;
    private V result = null;

    protected BusyWaitFuture() { }

    protected BusyWaitFuture(V _defaultResult) {
      this.result = _defaultResult;
    }

    protected BusyWaitFuture(int spinMillis, V _defaultResult) {
      this.spinMillis = spinMillis;
      this.result = _defaultResult;
    }

    protected V getResult() { return result; }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) { return false; }

    @Override
    public boolean isCancelled() { return false; }

    @Override
    public abstract boolean isDone();

    /** Just busy wait for running fetches. We have no notification for this. */
    @Override
    public V get() throws InterruptedException, ExecutionException {
      while (!isDone()) { Thread.sleep(spinMillis); }
      return getResult();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      long _maxMillis = unit.toMillis(timeout) + System.currentTimeMillis();
      if (_maxMillis < 0) { return get(); }
      while (!isDone() && System.currentTimeMillis() < _maxMillis) { Thread.sleep(spinMillis); }
      if (!isDone()) { throw new TimeoutException(); }
      return getResult();
    }

  }

}
