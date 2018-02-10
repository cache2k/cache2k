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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Jens Wilke
 */
public class WarpableClock implements InternalClock, Closeable {

  /**
   * Use a fair lock implementation to ensure that there is no bias towards
   * the progress thread.
   */
  final ReentrantLock structureLock = new ReentrantLock(true);
  final Log log = Log.getLog(WarpableClock.class);
  final int maxProgress;
  volatile boolean running = true;
  final AtomicLong wakeupCounter = new AtomicLong();
  final AtomicLong now;
  final TreeMap<Waiter, Waiter> tree = new TreeMap<Waiter, Waiter>();
  final Thread timerAdvanceThread;
  long nextWakeup;
  final Semaphore pongPermit;
  volatile int pongCounter = 0;
  final PongThread pongThreads[];
  final Semaphore progressPermit = new Semaphore(0);
  final AtomicLong counter = new AtomicLong();

  public WarpableClock(long _initialMillis) {
    now = new AtomicLong(_initialMillis);
    maxProgress = 1;
    structureLock.lock();
    try {
      pongThreads = new PongThread[Runtime.getRuntime().availableProcessors() * 3];
      for (int i = 0; i < pongThreads.length; i++) {
        PongThread t = new PongThread();
        pongThreads[i] = t;
        t.setName("clock-schedpingpong" + i);
        t.setPriority(Thread.MIN_PRIORITY);
        t.setDaemon(true);
        t.start();
      }
      pongPermit = new Semaphore(0);
    } finally {
      structureLock.unlock();
    }
    timerAdvanceThread = new Thread("clock-progress") {
      @Override
      public void run() {
        progressThread();
      }
    };
    timerAdvanceThread.setDaemon(true);
    timerAdvanceThread.start();
  }

  @Override
  public long millis() {
    if (counter.incrementAndGet() % 1000 == 0) {
      if (progressPermit.availablePermits() <= 0) {
        progressPermit.release();
      }
    }
    return now.get();
  }

  @Override
  public void sleep(final long _waitMillis) throws InterruptedException {
    log.debug(millis() + " sleep(" + _waitMillis + ")");
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
      Thread.yield();
      if (millis() > t0) {
        return;
      }
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
    log.debug("waitMillis (notifier, " + ( _wakeupTime == Long.MAX_VALUE ? "forever" : _waitMillis ) + ")");
    _waitMillis(l, _wakeupTime);
  }

  private void checkLock(final NotifyList l) {
    if (!l.lock.isHeldByCurrentThread()) {
      throw new IllegalStateException("must be run from within runExclusive");
    }
  }

  private void _waitMillis(final NotifyList l, final long _wakeupTime) throws InterruptedException {
    final Waiter w = new Waiter(l, _wakeupTime, wakeupCounter.getAndIncrement());
    structureLock.lock();
    try {
      if (_wakeupTime < Long.MAX_VALUE) {
        tree.put(w, w);
      }
      w.notifyList.add(w);
    } finally {
      structureLock.unlock();
    }
    progressPermit.release();
    w.notifyList.lock.lock();
    try {
      w.waitUntil();
    } finally {
      w.notifyList.lock.unlock();
    }
  }

  @Override
  public void runExclusive(final Notifier n, final Runnable r) {
    log.debug("runExclusive");
    Lock l = ((NotifyList) n).lock;
    l.lock();
    try {
      log.debug("run");
      r.run();
      log.debug("done");
    } finally {
      l.unlock();
    }
  }

  @Override
  public <T, R> R runExclusive(final Notifier n, final T v, final ExclusiveFunction<T, R> r) {
    log.debug("runExclusive");
    Lock l = ((NotifyList) n).lock;
    l.lock();
    try {
      log.debug("apply");
      R o = r.apply(v);
      log.debug("done");
      return o;
    } finally {
      l.unlock();
    }
  }

  void remove(final Waiter w, final NotifyList l) {
    structureLock.lock();
    try {
      tree.remove(w);
      if (l != null) {
        l.remove(w);
      }
    } finally {
      structureLock.unlock();
    }
  }

  public void close() {
    running = false;
    timerAdvanceThread.interrupt();
    for (int i = 0; i < pongThreads.length; i++) {
      pongThreads[i].interrupt();
    }
  }

  private void progressThread() {
    while (running) {
      long _nextWakeup = waitAndWakeupWaiters();
      long _delta = _nextWakeup - millis();
      if ((_nextWakeup > 0 && nextWakeup != _nextWakeup && _delta >= 100) || wakeupCount > 0) {
        nextWakeup = _nextWakeup;
        scheduleOtherThreads();
        continue;
      }
      if (_delta < 3) {
        scheduleOtherThreads();
      }
      now.incrementAndGet();
    }
  }

  private void scheduleOtherThreads() {
    log.info(millis() + " sched");
    for (int i = 0; i < pongThreads.length; i++) {
      pongThreads[i].pingPermit.release();
    }
    for (int i = 0; i < pongThreads.length; i++) {
      aquirePongPermit();
    }
  }

  private void aquirePongPermit() {
    try {
      pongPermit.acquire();
    } catch (InterruptedException ex) {
      close();
    }
  }

  long wakeupCount;

  private long waitAndWakeupWaiters() {
    wakeupCount = 0;
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
          } else {
            u.notifyList.remove(u);
          }
        }
      } finally {
        structureLock.unlock();
      }
      if (e != null) {
        wakeupCount++;
        log.debug(millis() + " " + e.getKey() + " notify timeout");
        e.getKey().timeout();
        try {
          e.getKey().waitUntilProceeding.await(1234, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
          close();
        }
      } else {
        try {
          progressPermit.acquire();
          return 0;
        } catch (InterruptedException ex) {
          close();
        }
      }
    }
    return 0;
  }

  public String toString() {
    structureLock.lock();
    try {
      return "clock{time=" + now + ", pongs=" + pongCounter + ", waiters=" + tree + "}";
    } finally {
      structureLock.unlock();
    }
  }

  class PongThread extends Thread {

    final Semaphore pingPermit = new Semaphore(0);

    private void aquirePingPermit() {
      try {
        pingPermit.acquire();
      } catch (InterruptedException ex) {
        close();
      }
    }

    @Override
    public void run() {
      Thread t = Thread.currentThread();
      try {
        while (running) {
          aquirePingPermit();
          structureLock.lock();
          Thread.yield();
          pongCounter++;
          structureLock.unlock();
          pongPermit.release();
        }
      } finally {
        log.debug("stopped " + t.getName() + "@" + t.getId());
      }
    }

  }

  class NotifyList implements Notifier {

    final ReentrantLock lock = new ReentrantLock(true);
    final Condition condition = lock.newCondition();
    final Object waitLock = new Object();
    final List<Waiter> list = new ArrayList<Waiter>();

    public void sendNotify() {
      checkLock(this);
      List<Waiter> copy;
      structureLock.lock();
      try {
        copy = new ArrayList<Waiter>(list);
        list.clear();
      } finally {
        structureLock.unlock();
      }
      for (Waiter n : copy) {
        n.sendNotify();
      }
      condition.signalAll();
    }

    public void add(Waiter n) {
      list.add(n);
    }

    public void remove(Waiter n) {
      list.remove(n);
    }

  }

  class Waiter implements Notifier, Comparable<Waiter> {

    volatile boolean notified = false;
    final long wakeupTime;
    final long uniqueId;
    final boolean noNotification;
    final NotifyList notifyList;
    final CountDownLatch waitUntilProceeding = new CountDownLatch(1);

    public Waiter(final NotifyList l, final long _wakeupTime, final long _uniqueId) {
      noNotification = l == null;
      notifyList = l == null ? new NotifyList() : l;
      wakeupTime = _wakeupTime;
      uniqueId = _uniqueId;
    }

    public void sendNotify() {
      notified = true;
    }

    public void timeout() {
      notifyList.lock.lock();
      try {
        notifyList.condition.signalAll();
      } finally {
        notifyList.lock.unlock();
      }
      try {
        waitUntilProceeding.await(1234, TimeUnit.MILLISECONDS);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }

    void waitUntil() throws InterruptedException {
      log.debug(millis() + " " + this + " waiting...");
      try {
        while (millis() < wakeupTime && !notified) {
          notifyList.condition.await();
        }
      } finally {
        if (notified) {
          log.debug(millis() + " " + toString() + " was notified");
        } else {
          log.debug(millis() + " " + toString() + " had timeout");
        }
        waitUntilProceeding.countDown();
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
