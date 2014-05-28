package org.cache2k.impl.timer;

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

/**
 * Timer queue based on the {@link org.cache2k.impl.BaseCache.Entry#nextRefreshTime} field.
 * Earlier implementations used {@link java.util.Timer} which had three
 * disadvantages: first, per cache entry two additional objects are created,
 * the task and the lock object. Per timer a thread is needed. To
 * insert a timer task with exact time value a java.util.Date object is
 * needed.
 *
 * @author Jens Wilke; created: 2014-03-21
 */
public class ArrayHeapTimerQueue extends TimerTaskQueue {

  int[] lapse = new int[77];
  long maxLapse = 0;

  /**
   * Array to implement a priority queue based on a binary heap. The code
   * is inspired by {@link java.util.Timer}. Another implementation
   * is within the algorithms collection from princeton.
   *
   * @see <a href="http://algs4.cs.princeton.edu/24pq/"></a>
   * @see <a href="http://algs4.cs.princeton.edu/24pq/MaxPQ.java.html"></a>
   */
  TimerTask[] queue = new TimerTask[128];

  int size = 0;

  MyThread thread;

  String threadName;

  long eventsScheduled;

  long eventsDelivered;

  long wakeupCount;

  long purgeCount;

  long cancelCount;

  /**
   * The purge flag is set to indicate that this queue should sort out the cancelled
   * timers. Purging may be needed if a lot of cancellations occur without a timer
   * going off. This the case if we have expiry in the far future and a too small
   * cache size. We detect this by comparing the size of the timer queue and the
   * size of the caches.
   */
  boolean purgeFlag = false;

  public ArrayHeapTimerQueue(String _threadName) {
    threadName = _threadName;
  }

  public <T> TimerTask<T> addTimer(TimerListener<T> l, T e, long _fireTime) {
    TimerTask e2 = createTimerTask(l, e, _fireTime);
    addTimerEvent(e2);
    return e2;
  }

  private <T> TimerTask createTimerTask(TimerListener<T> l, T e, long _fireTime) {
    TimerTask e2 = new TimerTask();
    e2.listener = l;
    e2.payload = e;
    e2.time = _fireTime;
    return e2;
  }

  @Override
  public int getQueueSize() {
    return size;
  }

  @Override
  public long getEventsDelivered() {
    return eventsDelivered;
  }

  @Override
  public long getPurgeCount() {
    return purgeCount;
  }

  @Override
  public void schedulePurge() {
    purgeFlag = true;
  }

  @Override
  public long getEventsScheduled() {
    return eventsScheduled;
  }

  void addTimerEvent(TimerTask e) {
    synchronized(this) {
      if (thread == null) {
        thread = new MyThread();
        thread.setName(threadName);
        thread.setDaemon(true);
        thread.queue = this;
        thread.start();
      }
      addQueue(e);
      eventsScheduled++;
      if (getMin() == e) {
        notify();
      }
    }
  }

  void addQueue(TimerTask e) {
    size++;
    if (size >= queue.length) {
      TimerTask[] q2 = new TimerTask[queue.length * 2];
      System.arraycopy(queue, 0, q2, 0, queue.length);
      queue = q2;
    }
    queue[size] = e;
    swim(size);

  }

  TimerTask getMin() {
    return queue[1];
  }

  void removeMin() {
    queue[1] = queue[size];
    queue[size--] = null;
    sink(1);
  }

  TimerTask getNextNotCancelledMin() {
    TimerTask e = getMin();
    while (e != null && e.isCancelled()) {
      removeMin();
      cancelCount++;
      e = getMin();
    }
    return e;
  }

  void purge() {
    purgeFlag = false;
    purgeCount++;
    TimerTask[] q = queue;
    for (int i = 1; i <= size; i++) {
      if (q[i].isCancelled()) {
        q[i] = q[size];
        q[size--] = null;
      }
    }
    for (int i = size/2; i >= 1; i--) {
      sink(i);
    }
  }

  private static void swap(TimerTask[] q,  int j, int k) {
    TimerTask tmp = q[j];
    q[j] = q[k];
    q[k] = tmp;
  }

  /**
   * Let entry at position k move up the heap hierarchy if time is less.
   */
  private void swim(int k) {
    TimerTask[] q = queue;
    while (k > 1) {
      int j = k >> 1;
      if (q[j].time <= q[k].time) {
        break;
      }
      swap(q, j, k);
      k = j;
    }
  }

  /**
   * Push the entry at position k down the heap hierarchy until the
   * the right position.
   */
  private void sink(int k) {
    TimerTask[] q = queue;
    int j;
    while ((j = k << 1) < size) {
      int _rightChild = j + 1;
      if (q[j].time > q[_rightChild].time) {
        j = _rightChild;
      }
      if (q[k].time <= q[j].time) {
        return;
      }
      swap(q, j, k);
      k = j;
    }
    if (j == size &&
      q[k].time > q[j].time) {
      swap(q, j, k);
    }
  }

  static class MyThread extends Thread {

    ArrayHeapTimerQueue queue;

    void runEntry(TimerTask e) {
      queue.eventsDelivered++;
      try {
        e.listener.timerEvent(e.payload, e.time);
      } catch (Throwable ex) {
        ex.printStackTrace();
      }
    }

    void loop() {
      long _nextEvent;
      long t = System.currentTimeMillis();
      TimerTask e;
      for (;;) {
        synchronized (queue) {
          e = queue.getNextNotCancelledMin();
          if (e == null) {
            queue.thread = null;
            break;
          }
          _nextEvent = e.time;
          if (_nextEvent <= t) {
            queue.removeMin();
          } else {
            t = System.currentTimeMillis();
            if (_nextEvent <= t) {
              queue.removeMin();
            }
          }
          if (_nextEvent < t) {
            recordLapse(_nextEvent, t);
          }
        }
        if (_nextEvent <= t) {
          runEntry(e);
          continue;
        }
        long _waitTime = _nextEvent - t;
        waitUntilTimeout(_waitTime);
      }
    }

    private void recordLapse(long _nextEvent, long now) {
      long d = now - _nextEvent;
      if (queue.maxLapse < d) {
        queue.maxLapse = d;
      }
      int idx = (int) (d / 7);
      int[] ia = queue.lapse;
      if (idx < 0 || idx >= ia.length) {
        idx = ia.length - 1;
      }
      ia[idx]++;
    }

    private void waitUntilTimeout(long _waitTime) {
      try {
        synchronized (queue) {
          if (_waitTime > 377 && queue.purgeFlag) {
            queue.purge();
          }
          queue.wait(_waitTime);
          queue.wakeupCount++;
        }
      } catch (InterruptedException ex) {
      }
    }

    @Override
    public void run() {
      loop();
    }

  }

}
