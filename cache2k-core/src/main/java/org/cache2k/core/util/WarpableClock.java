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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Jens Wilke
 */
public class WarpableClock implements InternalClock {

  final Object structureLock = new Object();
  final int maxProgress;
  volatile boolean running = true;
  final AtomicLong wakeupCounter = new AtomicLong();
  final AtomicLong now = new AtomicLong();
  final TreeMap<Waiter, Waiter> tree = new TreeMap<Waiter, Waiter>();
  final Thread thread;

  public WarpableClock(final int _maxProgress) {
    maxProgress = _maxProgress;
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
    return now.get();
  }

  @Override
  public void waitMillis(final long _millis) throws InterruptedException {
    long _startTime = now.get();
    long _wakeupTime = _startTime + _millis;
    final Waiter w = new Waiter(this, null, _wakeupTime, wakeupCounter.getAndIncrement());
    synchronized (structureLock) {
      tree.put(w, w);
      w.waitUntil();
    }
  }

  @Override
  public Notifier createNotifier() {
    return new NotifyList();
  }

  @Override
  public void waitMillis(final Notifier n, final long _millis) throws InterruptedException {
    NotifyList l = (NotifyList) n;
    long _startTime = now.get();
    long _wakeupTime = _startTime + _millis;
    final Waiter w = new Waiter(this, l, _wakeupTime, wakeupCounter.getAndIncrement());
    synchronized (structureLock) {
      tree.put(w, w);
      l.add(w);
      w.waitUntil();
    }
  }

  @Override
  public void runLocked(final Notifier n, final Runnable r) {
    synchronized (((NotifyList) n).waitLock) {
      r.run();
    }
  }

  void remove(final Waiter w, final NotifyList l) {
    synchronized (structureLock) {
      tree.remove(this);
      if (l != null) {
        l.remove(w);
      }
    }
  }

  public void close() {
    running = false;
    thread.interrupt();
  }

  void progressThread() {
    Random r = new Random();
    try {
      Thread.sleep(1);
    } catch (InterruptedException ignore) {
    }
    now.addAndGet(r.nextInt(maxProgress));
    while (running) {
      try {
        long _nextWakeup = wakeupWaiters();
        if (_nextWakeup > 0) {
          long _progress =
            Math.max(1, r.nextInt((int) Math.min(Integer.MAX_VALUE, _nextWakeup - now.get()) / 3 * 2));
          _progress = Math.min(maxProgress, _progress);
          now.addAndGet(_progress);
          if (_progress <= 1) {
            Thread.sleep(1);
          }
          continue;
        } else {
          Thread.sleep(1);
          now.addAndGet(maxProgress);
        }
      } catch (InterruptedException ignore) {
      }
    }
  }

  private long wakeupWaiters() {
    synchronized (structureLock) {
      for (; ; ) {
        Map.Entry<Waiter, Waiter> e = tree.pollFirstEntry();
        if (e != null) {
          Waiter u = e.getKey();
          if (u.wakeupTime <= now.get()) {
            u.timeout();
            continue;
          }
          tree.put(u, u);
          return u.wakeupTime;
        }
      }
    }
  }

  class NotifyList implements Notifier {

    final Object waitLock = new Object();
    final List<Notifier> list = new ArrayList<Notifier>();

    @Override
    public void sendNotify() {
      List<Notifier> copy;
      synchronized (structureLock) {
        copy = new ArrayList<Notifier>(list);
      }
      for (Notifier n : copy) {
        n.notify();
      }
    }

    public void add(Notifier n) {
      list.add(n);
    }

    public void remove(Notifier n) {
      list.remove(n);
    }

  }

  static class Waiter implements Notifier, Comparable<Waiter> {

    volatile boolean wakeup = false;
    final long wakeupTime;
    final long uniqueId;
    final NotifyList notifyList;
    final WarpableClock clock;

    public Waiter(WarpableClock c, final NotifyList l, final long _wakeupTime, final long _uniqueId) {
      clock = c;
      notifyList = l;
      wakeupTime = _wakeupTime;
      uniqueId = _uniqueId;
    }

    public void sendNotify() {
      wakeup = true;
      notifyList.waitLock.notifyAll();
    }

    public void timeout() {
      notifyList.waitLock.notifyAll();
    }

    void waitUntil() throws InterruptedException {
      try {
        while (clock.millis() < wakeupTime || !wakeup) {
          synchronized (notifyList.waitLock) {
            notifyList.waitLock.wait();
          }
        }
      } finally {
        clock.remove(this, notifyList);
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
  }

}
