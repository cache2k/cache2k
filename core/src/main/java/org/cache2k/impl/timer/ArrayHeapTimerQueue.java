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

import org.cache2k.util.Log;

import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.logging.Logger;

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
public class ArrayHeapTimerQueue extends TimerService {

  final static int MAXIMUM_ADDS_UNTIL_PURGE = 76543;
  final static int LAPSE_RESOLUTION = 10;
  final static int LAPSE_SIZE = 50;

  Log log;

  int[] lapse = new int[LAPSE_SIZE];
  long maxLapse = 0;

  final Object lock = new Object();

  MyThread thread;

  String threadName;

  long eventsScheduled;

  long eventsDelivered;

  long wakeupCount;

  int addedWithoutPurge;

  int purgeCount;

  /**
   * Protection: Only update within thread or when thread is stopped.
   */
  long cancelCount;

  long fireExceptionCount;

  Queue inQueue = new Queue();
  Queue outQueue = inQueue;
  Queue purgeQueue = null;

  public ArrayHeapTimerQueue(String _threadName) {
    threadName = _threadName;
    log = Log.getLog(ArrayHeapTimerQueue.class.getName() + ":" + _threadName);
  }

  public NoPayloadTask add(@Nonnull TimerListener _listener, long _fireTime) {
    NoPayloadTask e2 = new NoPayloadTask(_fireTime, _listener);
    addTimerEvent(e2);
    return e2;
  }

  public <T> PayloadTask<T> add(@Nonnull TimerPayloadListener<T> _listener, T _payload, long _fireTime) {
    PayloadTask<T> e2 = new PayloadTask<>(_fireTime, _payload, _listener);
    addTimerEvent(e2);
    return e2;
  }

  @Override
  public int getQueueSize() {
    synchronized (lock) {
      if (inQueue == outQueue) {
        return inQueue.size;
      }
      return inQueue.size + outQueue.size;
    }
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
  public long getEventsScheduled() {
    return eventsScheduled;
  }

  @Override
  public long getCancelCount() {
    synchronized (lock) {
      MyThread th = thread;
      if (th != null) {
        return cancelCount + inQueue.cancelCount + th.cancelCount;
      }
      return cancelCount + inQueue.cancelCount;
    }
  }

  @Override
  public long getFireExceptionCount() {
    return fireExceptionCount;
  }

  void addTimerEvent(BaseTimerTask e) {
    synchronized(lock) {
      startThread();
      inQueue.addQueue(e);
      eventsScheduled++;
      addedWithoutPurge++;
      if (outQueue.getMin() == e) {
        lock.notify();
      }
    }
    if (addedWithoutPurge > MAXIMUM_ADDS_UNTIL_PURGE) {
      performPurge();
    }
  }

  private void startThread() {
    if (thread == null) {
      thread = new MyThread();
      thread.setName(threadName);
      thread.setDaemon(true);
      thread.queue = this;
      thread.start();
    }
  }

  /**
   * Cleans cancelled timer tasks from the priority queue. We do
   * this just in one thread that calls us, but without holding up
   * event delivery or other threads. When a purge occurs we work
   * with two queues. One for added timer tasks and one for delivered
   * timer tasks. The added timer tasks will be appended to the
   * the out queue after the purge is finished. Of course, the
   * delivery of new timer task is delayed after the purge is finished
   * which should not be a big problem.
   */
  void performPurge() {
    Queue _purgee;
    synchronized (lock) {
      if (inQueue != outQueue || inQueue.size == 0) {
        return;
      }
      addedWithoutPurge = Integer.MIN_VALUE;
      cancelCount += inQueue.cancelCount;
      _purgee = purgeQueue = inQueue.copy();
      inQueue = new Queue();
    }
    _purgee.purge();
    synchronized (lock) {
      BaseTimerTask _nextTimerTaskInQueue = outQueue.getNextNotCancelledMin();
      if (_nextTimerTaskInQueue == null) {
        cancelCount += _purgee.cancelCount;
        _purgee = new Queue();
      } else {
        BaseTimerTask t;
        while ((t = _purgee.getMin()) != _nextTimerTaskInQueue) {
          _purgee.removeMin();
        }
      }
      BaseTimerTask t;
      while ((t = inQueue.getNextNotCancelledMin()) != null) {
        inQueue.removeMin();
        _purgee.addQueue(t);
      }
      cancelCount += inQueue.cancelCount;
      inQueue = outQueue = _purgee;
      addedWithoutPurge = 0;
      startThread();
    }
  }

  static class MyThread extends Thread {

    ArrayHeapTimerQueue queue;
    long cancelCount;

    void fireTask(BaseTimerTask e) {
      try {
        boolean f = e.fire(e.getTime());
        if (f) {
          queue.eventsDelivered++;
        } else {
          cancelCount++;
        }
      } catch (Throwable ex) {
        queue.fireExceptionCount++;
        queue.log.warn("timer event caused exception", ex);
      }
    }

    void loop() {
      long _nextEvent;
      long t = System.currentTimeMillis();
      BaseTimerTask e;
      for (;;) {
        synchronized (queue.lock) {
          e = queue.outQueue.getNextNotCancelledMin();
          if (e == null) {
            queue.thread = null;
            queue.cancelCount += cancelCount;
            break;
          }
          _nextEvent = e.getTime();
          if (_nextEvent <= t) {
            queue.outQueue.removeMin();
          } else {
            t = System.currentTimeMillis();
            if (_nextEvent <= t) {
              queue.outQueue.removeMin();
            }
          }
          if (_nextEvent < t) {
            recordLapse(_nextEvent, t);
          }
        }
        if (_nextEvent <= t) {
          fireTask(e);
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
      int idx = (int) (d / LAPSE_RESOLUTION);
      int[] ia = queue.lapse;
      if (idx < 0 || idx >= ia.length) {
        idx = ia.length - 1;
      }
      ia[idx]++;
    }

    private void waitUntilTimeout(long _waitTime) {
      try {
        synchronized (queue.lock) {
          queue.lock.wait(_waitTime);
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

  static class Queue {

    /**
     * Array to implement a priority queue based on a binary heap. The code
     * is inspired by {@link java.util.Timer}. Another implementation
     * is within the algorithms collection from princeton.
     *
     * @see <a href="http://algs4.cs.princeton.edu/24pq/"></a>
     * @see <a href="http://algs4.cs.princeton.edu/24pq/MaxPQ.java.html"></a>
     */
    BaseTimerTask[] queue = new BaseTimerTask[128];
    int size = 0;
    long cancelCount = 0;

    void addQueue(BaseTimerTask e) {
      size++;
      if (size >= queue.length) {
        BaseTimerTask[] q2 = new BaseTimerTask[queue.length * 2];
        System.arraycopy(queue, 0, q2, 0, queue.length);
        queue = q2;
      }
      queue[size] = e;
      swim(size);
    }

    BaseTimerTask getMin() {
      return queue[1];
    }

    void removeMin() {
      queue[1] = queue[size];
      queue[size--] = null;
      sink(1);
    }

    BaseTimerTask getNextNotCancelledMin() {
      BaseTimerTask e = getMin();
      while (e != null && e.isCancelled()) {
        removeMin();
        cancelCount++;
        e = getMin();
      }
      return e;
    }

    Queue copy() {
      Queue q = new Queue();
      q.size = size;
      q.queue = new BaseTimerTask[queue.length];
      System.arraycopy(queue, 0, q.queue, 0, queue.length);
      return q;
    }

    void purge() {
      BaseTimerTask[] q = queue;
      for (int i = 1; i <= size; i++) {
        while (q[i] != null && q[i].isCancelled()) {
          cancelCount++;
          q[i] = q[size];
          q[size] = null;
          size--;
        }
      }
      for (int i = size/2; i >= 1; i--) {
        sink(i);
      }
    }

    private static void swap(BaseTimerTask[] q,  int j, int k) {
      BaseTimerTask tmp = q[j];
      q[j] = q[k];
      q[k] = tmp;
    }

    /**
     * Let entry at position k move up the heap hierarchy if time is less.
     */
    private void swim(int k) {
      BaseTimerTask[] q = queue;
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
      BaseTimerTask[] q = queue;
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

  }

}
