package org.cache2k.core.util;

/*
 * #%L
 * cache2k core
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Jens Wilke
 */
public class WarpableClock implements InternalClock, Closeable {

  final Log log = Log.getLog(WarpableClock.class);
  final Object structureLock = new Object();
  final int maxProgress;
  volatile boolean running = true;
  final AtomicLong wakeupCounter = new AtomicLong();
  final AtomicLong now;
  final TreeMap<Waiter, Waiter> tree = new TreeMap<Waiter, Waiter>();
  final Thread thread;
  final AtomicLong counter = new AtomicLong();

  public WarpableClock(long _initialMillis) {
    now = new AtomicLong(_initialMillis);
    maxProgress = 1;
    thread = new Thread() {
      @Override
      public void run() {
        progressThread();
      }
    };
    thread.setDaemon(true);
    thread.start();
  }

  @Override
  public long millis() {
    if (counter.incrementAndGet() % 1000 == 0) {
      synchronized (structureLock) {
        structureLock.notify();
      }
    }
    return now.get();
  }

  @Override
  public void waitMillis(final long _waitMillis) throws InterruptedException {
    if (_waitMillis == 0) {
      Thread.yield();
      return;
    }
    if (_waitMillis == 1) {
      sleepForAtLeastOneMillisecond();
      return;
    }
    long _startTime = millis();
    long _wakeupTime = _startTime + _waitMillis;
    if (_wakeupTime < 0) {
      _wakeupTime = Long.MAX_VALUE;
    }
    _waitMillis(null, _wakeupTime);
  }

  /**
   * Optimization for code that does {@code sleep(1)} to make some time pass by.
   */
  private void sleepForAtLeastOneMillisecond() {
    long t0 = millis();
    int _yields = 3;
    for (; _yields > 0; _yields--) {
      if (millis() > t0) {
        return;
      }
      Thread.yield();
    }
    if (millis() > t0) {
      return;
    }
    synchronized (structureLock) {
      structureLock.notify();
    }
    while (millis() <= t0) {
      Thread.yield();
    }
  }

  @Override
  public Notifier createNotifier() {
    return new NotifyList();
  }

  @Override
  public void waitMillis(final Notifier n, final long _waitMillis) throws InterruptedException {
    NotifyList l = (NotifyList) n;
    checkLock(l);
    long _startTime = millis();
    long _wakeupTime = _startTime + _waitMillis;
    if (_wakeupTime < 0 || _waitMillis == 0) {
      _wakeupTime = Long.MAX_VALUE;
    }

    _waitMillis(l, _wakeupTime);
  }

  private void checkLock(final NotifyList _l) {
    if (!Thread.holdsLock(_l.waitLock)) {
      throw new IllegalStateException("must be run from within runExclusive");
    }
  }

  private void _waitMillis(final NotifyList _l, final long _wakeupTime) throws InterruptedException {
    final Waiter w = new Waiter(_l, _wakeupTime, wakeupCounter.getAndIncrement());
    synchronized (structureLock) {
      if (_wakeupTime < Long.MAX_VALUE) {
        tree.put(w, w);
      }
      w.notifyList.add(w);
      structureLock.notify();
    }
    w.waitUntil();
  }

  @Override
  public void runExclusive(final Notifier n, final Runnable r) {
    synchronized (((NotifyList) n).waitLock) {
      r.run();
    }
  }

  @Override
  public <T, R> R runExclusive(final Notifier n, final T v, final ExclusiveFunction<T, R> r) {
    synchronized (((NotifyList) n).waitLock) {
      return r.apply(v);
    }
  }

  void remove(final Waiter w, final NotifyList l) {
    synchronized (structureLock) {
      tree.remove(w);
      if (l != null) {
        l.remove(w);
      }
    }
  }

  public void close() {
    running = false;
    thread.interrupt();
  }

  private void progressThread() {
    Thread.yield();
    while (running) {
      waitAndWakeupWaiters();
      now.incrementAndGet();
      Thread.yield();
    }
  }

  private long waitAndWakeupWaiters() {
    while (running) {
      Waiter u;
      synchronized (structureLock) {
        Map.Entry<Waiter, Waiter> e = tree.pollFirstEntry();
        if (e == null) {
          try {
            structureLock.wait();
          } catch (InterruptedException ignore) {
          }
          return 0;
        }
        u = e.getKey();
        if (u.wakeupTime > now.get()) {
          tree.put(u, u);
          return u.wakeupTime;
        }
      }
      u.timeout();
    }
    return 0;
  }

  class NotifyList implements Notifier {

    final Object waitLock = new Object();
    final List<Notifier> list = new ArrayList<Notifier>();

    @Override
    public void sendNotify() {
      checkLock(this);
      List<Notifier> copy;
      synchronized (structureLock) {
        copy = new ArrayList<Notifier>(list);
      }
      for (Notifier n : copy) {
        n.sendNotify();
      }
    }

    public void add(Notifier n) {
      list.add(n);
    }

    public void remove(Notifier n) {
      list.remove(n);
    }

  }

  class Waiter implements Notifier, Comparable<Waiter> {

    volatile boolean notified = false;
    final long wakeupTime;
    final long uniqueId;
    final boolean noNotification;
    final NotifyList notifyList;

    public Waiter(final NotifyList l, final long _wakeupTime, final long _uniqueId) {
      noNotification = l == null;
      notifyList = l == null ? new NotifyList() : l;
      wakeupTime = _wakeupTime;
      uniqueId = _uniqueId;
    }

    public void sendNotify() {
      notified = true;
      notifyList.waitLock.notifyAll();
    }

    public void timeout() {
      synchronized (notifyList.waitLock) {
        notifyList.waitLock.notifyAll();
      }
    }

    void waitUntil() throws InterruptedException {
      log.debug(this + " waiting...");
      try {
        synchronized (notifyList.waitLock) {
          while (millis() < wakeupTime && !notified) {
            notifyList.waitLock.wait();
          }
        }
      } finally {
        remove(this, notifyList);
      }
      if (notified) {
        log.debug(millis() + " " + toString() + " was notified");
      } else {
        log.debug(millis() + " " + toString() + " had timeout");
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
