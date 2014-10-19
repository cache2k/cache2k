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

import org.cache2k.impl.util.Log;

/**
 * Timer queue based on the {@link org.cache2k.impl.BaseCache.Entry#nextRefreshTime} field.
 * Earlier implementations used {@link java.util.Timer} which has some
 * disadvantages: per cache entry two additional objects are created,
 * the task and the lock object. Per one timer a thread is needed. To
 * insert a timer task with exact time value a java.util.Date object is
 * needed. A purge/cleanup of cancelled tasks is not done automatically.
 *
 * <p/>In situations with scarce memory, a lot of evictions may occur, which
 * causes a lot of timer cancellations. This is the reason why this timer implementation
 * does a purge of cancelled tasks automatically in a regular interval.
 * During the purge is done, timer events still can run in parallel. Only the
 * new timer events will be held up until the purge ist finished.
 *
 * <p/>Remark: Yes, this class is a little over engineered. After three make
 * overs it finally works as expected...
 *
 * @author Jens Wilke; created: 2014-03-21
 */
public class ArrayHeapTimerQueue extends TimerService {

  final static int MAXIMUM_ADDS_UNTIL_PURGE = 54321;
  final static int LAPSE_RESOLUTION = 10;
  final static int LAPSE_SIZE = 50;
  final static long KEEP_THREAD_WAITING_MILLIS = 17 * 1000;

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

  public ArrayHeapTimerQueue(String _threadName) {
    threadName = _threadName;
    log = Log.getLog(ArrayHeapTimerQueue.class.getName() + ":" + _threadName);
  }

  public NoPayloadTask add(TimerListener _listener, long _fireTime) {
    NoPayloadTask e2 = new NoPayloadTask(_fireTime, _listener);
    addTimerEvent(e2);
    return e2;
  }

  public <T> PayloadTask<T> add(TimerPayloadListener<T> _listener, T _payload, long _fireTime) {
    PayloadTask<T> e2 = new PayloadTask<T>(_fireTime, _payload, _listener);
    addTimerEvent(e2);
    return e2;
  }

  @Override
  public int getQueueSize() {
    Queue q = inQueue;
    if (q == outQueue) {
      return q.size;
    }
    synchronized (lock) {
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
      thread.timer = this;
      thread.start();
    }
  }

  /**
   * Cleans cancelled timer tasks from the priority queue. We do
   * this just in the thread that calls us, but without holding up
   * event delivery . When a purge occurs we work with two queues.
   * One for added timer tasks and one for delivered
   * timer tasks. Newly added timer tasks will be appended to the
   * the out queue after the purge is finished. Thus, the delivery of
   * new timer tasks is delayed after the purge is finished.
   *
   * <p/>One tricky thing is to catch up with tasks that fired during
   * the purge from the out queue. This is achieved by skipping to the
   * time of the task that will fire next and by the ensurance that the
   * timer thread will always process events of the same time within
   * one synchronized block.
   */
  void performPurge() {
    Queue _purgeQ;
    int _queueSizeBefore;
    synchronized (lock) {
      if (inQueue != outQueue || inQueue.size == 0) {
        return;
      }
      addedWithoutPurge = Integer.MIN_VALUE;
      cancelCount += inQueue.cancelCount;
      outQueue.cancelCount = 0;
      _purgeQ = outQueue.copy();
      inQueue = new Queue();
      _queueSizeBefore = outQueue.size;
    }
    _purgeQ.purge();
    synchronized (lock) {
      boolean _somethingFired = outQueue.size != _queueSizeBefore;
      if (_somethingFired) {
        BaseTimerTask _nextTimerTaskInQueue = outQueue.getNextNotCancelledMin();
        if (_nextTimerTaskInQueue == null) {

          cancelCount += outQueue.cancelCount;
          _purgeQ = new Queue();
        } else {
          long _forwardUntilTime = _nextTimerTaskInQueue.getTime();
          BaseTimerTask t;
          BaseTimerTask _previousSkippedTask = null;
          while ((t = _purgeQ.getMin()).getTime() != _forwardUntilTime) {
            if (t.isCancelled()) {
              _purgeQ.cancelCount++;
            }
            _purgeQ.removeMin();
            _previousSkippedTask = t;
          }
        }
      }
      BaseTimerTask t;
      while ((t = inQueue.getNextNotCancelledMin()) != null) {
        inQueue.removeMin();
        _purgeQ.addQueue(t);
      }
      cancelCount += inQueue.cancelCount;
      inQueue = outQueue = _purgeQ;
      addedWithoutPurge = 0;
      if (inQueue.size > 0) {
        startThread();
        lock.notify();
      }
      purgeCount++;
    }
  }

  static class MyThread extends Thread {

    ArrayHeapTimerQueue timer;
    long cancelCount;

    void fireTask(BaseTimerTask e) {
      try {
        boolean f = e.fire(e.getTime());
        if (f) {
          timer.eventsDelivered++;
        } else {
          cancelCount++;
        }
      } catch (Throwable ex) {
        timer.fireExceptionCount++;
        timer.log.warn("timer event caused exception", ex);
      }
    }

    int loop() {
      long _fireTime;
      long t = System.currentTimeMillis();
      BaseTimerTask e;
      int fireCnt = 0;
      for (;;) {
        boolean _fires;
        synchronized (timer.lock) {
          Queue q = timer.outQueue;
          for (;;) {
            e = q.getNextNotCancelledMin();
            if (e == null) {
              return fireCnt;
            }
            _fires = false;
            _fireTime = e.getTime();
            if (_fireTime > t) {
              t = System.currentTimeMillis();
            }
            if (_fireTime <= t) {
              if (_fireTime < t) { // missed timely execution?
                recordLapse(_fireTime, t);
              }
              q.removeMin();
              BaseTimerTask _next = q.getMin();
              boolean _fireWithinLock = _next != null && _fireTime == _next.getTime();
              if (_fireWithinLock) {
                fireTask(e);
                fireCnt++;
                continue;
              }
              _fires = true;
            }
            break;
          } // inner for loop
        } // synchronized
        if (_fires) {
          fireTask(e);
          fireCnt++;
        } else {
          long _waitTime = _fireTime - t;
          waitUntilTimeout(_waitTime);
        }
      } // outer loop
    }

    private void recordLapse(long _nextEvent, long now) {
      long d = now - _nextEvent;
      if (timer.maxLapse < d) {
        timer.maxLapse = d;
      }
      int idx = (int) (d / LAPSE_RESOLUTION);
      int[] ia = timer.lapse;
      if (idx < 0 || idx >= ia.length) {
        idx = ia.length - 1;
      }
      ia[idx]++;
    }

    private void waitUntilTimeout(long _waitTime) {
      try {
        synchronized (timer.lock) {
          timer.lock.wait(_waitTime);
          timer.wakeupCount++;
        }
      } catch (InterruptedException ex) {
      }
    }

    @Override
    public void run() {
      try {
        while (loop() > 0) {
          waitUntilTimeout(KEEP_THREAD_WAITING_MILLIS);
        }
        timer.thread = null;
        timer.cancelCount += cancelCount;
      } catch (Throwable t) {
        t.printStackTrace();
      }
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
      int _size = size;
      for (int i = 1; i <= _size; ) {
        if (q[i].isCancelled()) {
          cancelCount++;
          q[i] = q[_size];
          q[_size] = null;
          _size--;
        } else {
          i++;
        }
      }
      size = _size;
      for (int i = size / 2; i >= 1; i--) {
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
