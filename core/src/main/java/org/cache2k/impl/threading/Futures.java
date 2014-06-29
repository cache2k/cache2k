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

  public static class WaitforAllFuture<V> implements Future<V> {

    List<Future<V>> futureList = new LinkedList<>();

    public WaitforAllFuture(Future<V> _top) {
      add(_top);
    }

    @SafeVarargs
    public WaitforAllFuture(final Future<V>... _top) {
      for (Future<V> f : _top) { add(f); }
    }

    /**
     * Add a new future to the list. The future methods will also
     * call the additional future to check status or to wait for
     * the operation to finish.
     */
    public synchronized void add(Future<V> f) {
      futureList.add(f);
    }

    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
      boolean _flag = true;
      for (Future<V> f : futureList) {
        if (!f.isCancelled()) {
          f.cancel(mayInterruptIfRunning);
          _flag &= f.isCancelled();
        }
      }
      return _flag;
    }

    @Override
    public boolean isCancelled() {
      throw new UnsupportedOperationException();
    }

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
     */
    @Override
    public V get() throws InterruptedException, ExecutionException {
      for (;;) {
        Future<V> _needsGet = null;
        synchronized (this) {
          for (Future<V> f : futureList) {
            if (!f.isDone()) {
              _needsGet = f;
            }
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
          for (Future<V> f : futureList) {
            if (!f.isDone()) {
              _needsGet = f;
            }
          }
        }
        if (_needsGet != null) {
          long now = System.currentTimeMillis();
          if (now <= _maxTime) {
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

    private Exception exception;

    public ExceptionFuture(Exception exception) {
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
}
