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
 * Simulated clock implementation that moves fast when {@link #sleep} is called and
 * is delivering timer events whenever a scheduled time is passed.
 *
 * @author Jens Wilke
 */
public class WarpableClock implements InternalClock, Closeable {

  final static Log LOG = Log.getLog(WarpableClock.class);

  /**
   * Current time of the clock in millis.
   */
  final AtomicLong now;

  /**
   * Use a fair lock implementation to ensure that there is no bias towards
   * the progress thread.
   */
  final ReentrantLock structureLock = new ReentrantLock();

  /**
   * Set to false on close to stop progress thread and avoid usage.
   */
  volatile boolean running = true;

  /**
   * Unique number for each waiter object
   */
  final AtomicLong waiterCounter = new AtomicLong();

  /**
   * The tree sorts the timer events.
   */
  final TreeMap<Waiter, Waiter> tree = new TreeMap<Waiter, Waiter>();

  /**
   * Hold the progress thread.
   */
  final Thread progressThread;

  /**
   * queue needs capacity, otherwise we deadlock
   */
  final BlockingQueue<Long> queue = new ArrayBlockingQueue<Long>(10);

  /**
   * Every time we look on the clock we count this.
   */
  final AtomicLong clockReadingCounter = new AtomicLong();

  /**
   * Clock makes a milli second progress after this many readings.
   */
  final static int CLOCK_READING_PROGRESS = 1000;

  /**
   * Create a clock with the initial time.
   *
   * @param _initialMillis
   */
  public WarpableClock(long _initialMillis) {
    now = new AtomicLong(_initialMillis);
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
    return new TRJ(new Waiter(-1, waiterCounter.getAndIncrement(), ev));
  }

  @Override
  public void schedule(final TimeReachedJob j, final long _millis) {
    LOG.debug(now.get() + " job " + _millis);
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
    public synchronized  void timeIsReached(final long _millis) {
      notifyAll();
    }
  };

  /**
   * A sleep of {@code 0} waits an undefined amount of time and makes sure that the next timer
   * events for the next upcoming millisecond are executed. A value greater then {@code 0}s advances the
   * time just by the specified amount.
   */
  @Override
  public void sleep(final long _waitMillis) throws InterruptedException {
    if (!running) {
      throw new IllegalStateException();
    }
    long _startTime = now.get();
    LOG.debug(now.get() + " sleep(" + _waitMillis + ")");
    if (_waitMillis == 0) {
      synchronized (queue) {
        if (queue.isEmpty()) {
          queue.put(0L);
        }
        queue.wait();
      }
      return;
    }
    long _wakeupTime = _startTime + _waitMillis;
    if (_wakeupTime < 0) {
      _wakeupTime = Long.MAX_VALUE;
    }
    queue.put(_wakeupTime);
    TimeReachedJob j = createJob(wakeUpSleep);
    synchronized (wakeUpSleep) {
      schedule(j, _wakeupTime);
      while (now.get() < _wakeupTime) {
        wakeUpSleep.wait();
      }
    }
  }

  public void close() {
    running = false;
    progressThread.interrupt();
  }

  private void progressThread() {
    while (running) {
      long _token = 0;
      long _nextWakeup;
      try {
        synchronized (queue) {
          queue.notifyAll();
        }
        _token = queue.take();
      } catch (InterruptedException ex) {
        close();
      }
      _nextWakeup = wakeupWaiters();
      if (_token == 0) {
        if (_nextWakeup > now.get()) {
          now.addAndGet(Math.max((now.get() - _nextWakeup) / 2, 1));
        } else {
          now.incrementAndGet();
        }
        wakeupWaiters();
        continue;
      }
      while (_token > now.get()) {
        long t = (_nextWakeup > 0) ? Math.min(_nextWakeup, _token) : _token;
        now.addAndGet(Math.max((now.get() - t) / 2, 1));
        _nextWakeup = wakeupWaiters();
      }
    }
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
      return "clock{time=" + now + ", waiters=" + tree + "}";
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

    final long wakeupTime;
    final long uniqueId;
    final TimeReachedEvent event;

    public Waiter(final long _wakeupTime, final long _uniqueId, TimeReachedEvent _event) {
      wakeupTime = _wakeupTime;
      uniqueId = _uniqueId;
      event = _event;
    }

    public void timeIsReached() {
      if (event != null) {
        try {
          event.timeIsReached(wakeupTime);
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

}
