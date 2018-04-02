package org.cache2k.core.util;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Jens Wilke
 */
public class WarpableClock implements InternalClock, Closeable {

  /**
   * Use a fair lock implementation to ensure that there is no bias towards
   * the progress thread.
   */
  final ReentrantLock structureLock = new ReentrantLock();
  final Log log = Log.getLog(WarpableClock.class);
  volatile boolean running = true;
  final AtomicLong waiterCounter = new AtomicLong();
  final AtomicLong now;
  final TreeMap<Waiter, Waiter> tree = new TreeMap<Waiter, Waiter>();
  final Thread progressThread;
  volatile int pongCounter = 0;
  /**
   * queue needs capacity, otherwise we deadlock
   */
  final BlockingQueue<Long> queue = new ArrayBlockingQueue<Long>(10);
  final AtomicLong counter = new AtomicLong();

  public WarpableClock(long _initialMillis) {
    now = new AtomicLong(_initialMillis);
    progressThread = new Thread("clock-progress") {
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

  static class TRJ implements TimeReachedJob {
    Waiter waiter;
    public TRJ(final Waiter w) {
      waiter = w;
    }
  }

  @Override
  public TimeReachedJob createJob(final TimeReachedEvent ev) {
    return new TRJ(new Waiter(-1, waiterCounter.getAndIncrement(), ev));
  }

  @Override
  public void schedule(final TimeReachedJob j, final long _millis) {
    log.debug(now.get() + " job " + _millis);
    final TRJ w = (TRJ) j;
    structureLock.lock();
    try {
      tree.remove(w.waiter);
      w.waiter = new Waiter(_millis, waiterCounter.getAndIncrement(), w.waiter.event);
      tree.put(w.waiter, w.waiter);
    } finally {
      structureLock.unlock();
    }
    if (Thread.currentThread() != progressThread) {
      try {
        queue.put(0L);
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

  @Override
  public long millis() {
    if (counter.incrementAndGet() % 1000 == 0) {
      try {
        queue.put(now.get() + 1L);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }
    return now.get();
  }

  @Override
  public void sleep(final long _waitMillis) throws InterruptedException {
    if (!running) {
      throw new IllegalStateException();
    }
    long _startTime = now.get();
    log.debug(now.get() + " sleep(" + _waitMillis + ")");
    if (_waitMillis == 0) {
      Thread.yield();
      return;
    }
    long _wakeupTime = _startTime + _waitMillis;
    if (_wakeupTime < 0) {
      _wakeupTime = Long.MAX_VALUE;
    }
    queue.put(_wakeupTime);
    while (now.get() < _wakeupTime) {
      Thread.yield();
    }
  }

  public void close() {
    running = false;
    progressThread.interrupt();
  }

  private void progressThread() {
    while (running) {
      long _token = 0;
      try {
        _token = queue.take();
      } catch (InterruptedException ex) {
        close();
      }
      wakeupWaiters();
      if (_token == 0) {
        continue;
      }
      while (_token > now.get()) {
        now.incrementAndGet();
        wakeupWaiters();
      }
    }
  }

  private void wakeupWaiters() {
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
            return;
          }
        } else {
          return;
        }
      } finally {
        structureLock.unlock();
      }
      log.debug(now.get() + " " + e.getKey() + " notify timeout");
      e.getKey().timeout();
    }
  }

  public String toString() {
    structureLock.lock();
    try {
      return "clock{time=" + now + ", pongs=" + pongCounter + ", waiters=" + tree + "}";
    } finally {
      structureLock.unlock();
    }
  }

  class Waiter implements Comparable<Waiter>, TimeReachedJob {

    final long wakeupTime;
    final long uniqueId;
    final TimeReachedEvent event;

    public Waiter(final long _wakeupTime, final long _uniqueId, TimeReachedEvent _event) {
      wakeupTime = _wakeupTime;
      uniqueId = _uniqueId;
      event = _event;
    }

    public void timeout() {
      if (event != null) {
        event.timeIsReached(wakeupTime);
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
      if (uniqueId < o.uniqueId) {
        return -1;
      }
      if (uniqueId > o.uniqueId) {
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

}
