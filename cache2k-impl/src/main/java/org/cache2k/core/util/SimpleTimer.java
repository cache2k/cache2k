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

import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Copy from {@link java.util.Timer} to work on top of the {@link InternalClock}
 * interface.
 *
 * <p>A facility for threads to schedule tasks for future execution in a
 * background thread.  Tasks may be scheduled for one-time execution, or for
 * repeated execution at regular intervals.
 *
 * <p>Corresponding to each <tt>Timer</tt> object is a single background
 * thread that is used to execute all of the timer's tasks, sequentially.
 * Timer tasks should complete quickly.  If a timer task takes excessive time
 * to complete, it "hogs" the timer's task execution thread.  This can, in
 * turn, delay the execution of subsequent tasks, which may "bunch up" and
 * execute in rapid succession when (and if) the offending task finally
 * completes.
 *
 * <p>After the last live reference to a <tt>Timer</tt> object goes away
 * <i>and</i> all outstanding tasks have completed execution, the timer's task
 * execution thread terminates gracefully (and becomes subject to garbage
 * collection).  However, this can take arbitrarily long to occur.  By
 * default, the task execution thread does not run as a <i>daemon thread</i>,
 * so it is capable of keeping an application from terminating.  If a caller
 * wants to terminate a timer's task execution thread rapidly, the caller
 * should invoke the timer's <tt>cancel</tt> method.
 *
 * <p>If the timer's task execution thread terminates unexpectedly, for
 * example, because its <tt>stop</tt> method is invoked, any further
 * attempt to schedule a task on the timer will result in an
 * <tt>IllegalStateException</tt>, as if the timer's <tt>cancel</tt>
 * method had been invoked.
 *
 * <p>This class is thread-safe: multiple threads can share a single
 * <tt>Timer</tt> object without the need for external synchronization.
 *
 * <p>This class does <i>not</i> offer real-time guarantees: it schedules
 * tasks using the <tt>Object.wait(long)</tt> method.
 *
 * <p>Java 5.0 introduced the {@code java.util.concurrent} package and
 * one of the concurrency utilities therein is the {@link
 * java.util.concurrent.ScheduledThreadPoolExecutor
 * ScheduledThreadPoolExecutor} which is a thread pool for repeatedly
 * executing tasks at a given rate or delay.  It is effectively a more
 * versatile replacement for the {@code Timer}/{@code TimerTask}
 * combination, as it allows multiple service threads, accepts various
 * time units, and doesn't require subclassing {@code TimerTask} (just
 * implement {@code Runnable}).  Configuring {@code
 * ScheduledThreadPoolExecutor} with one thread makes it equivalent to
 * {@code Timer}.
 *
 * <p>Implementation note: This class scales to large numbers of concurrently
 * scheduled tasks (thousands should present no problem).  Internally,
 * it uses a binary heap to represent its task queue, so the cost to schedule
 * a task is O(log n), where n is the number of concurrently scheduled tasks.
 *
 * <p>Implementation note: All constructors start a timer thread.
 *
 * @author  Josh Bloch
 * @see     java.util.TimerTask
 * @see     Object#wait(long)
 * @since   1.3
 */
public class SimpleTimer {

  private final Lock lock = new ReentrantLock();
  private final Condition condition = lock.newCondition();

  private final InternalClock clock;

  /**
   * The timer task queue.  This data structure is shared with the timer
   * thread.  The timer produces tasks, via its various schedule calls,
   * and the timer thread consumes, executing timer tasks as appropriate,
   * and removing them from the queue when they're obsolete.
   */
  private final TaskQueue queue = new TaskQueue();

  /**
   * The timer thread.
   */
  private final TimerThread thread;

  private final InternalClock.TimeReachedJob reachedJob;

  /**
   * This object causes the timer's task execution thread to exit
   * gracefully when there are no live references to the Timer object and no
   * tasks in the timer queue.  It is used in preference to a finalizer on
   * Timer as such a finalizer would be susceptible to a subclass's
   * finalizer forgetting to call it.
   */
  private final Object threadReaper = new Object() {
    protected void finalize() throws Throwable {
      lock.lock();
      try {
        thread.newTasksMayBeScheduled = false;
        condition.signal();
      } finally {
        lock.unlock();
      }
    }
  };
  /**
   * Creates a new timer whose associated thread has the specified name,
   * and may be specified to
   * {@linkplain Thread#setDaemon run as a daemon}.
   *
   * @param name the name of the associated thread
   * @param isDaemon true if the associated thread should run as a daemon
   * @throws NullPointerException if {@code name} is null
   * @since 1.5
   */
  public SimpleTimer(InternalClock c, String name, boolean isDaemon) {
    this.clock = c;
    thread = new TimerThread(clock, lock, condition, queue);
    if (!c.isJobSchedulable()) {
      thread.setName(name);
      thread.setDaemon(isDaemon);
      thread.start();
      reachedJob = null;
    } else {
      reachedJob = clock.createJob(new InternalClock.TimeReachedEvent() {
        @Override
        public void timeIsReached(final long _millis) {
          timeReachedEvent(_millis);
        }
      });
    }
  }

  /**
   * Schedule the specified timer task for execution at the specified
   * time with the specified period, in milliseconds.  If period is
   * positive, the task is scheduled for repeated execution; if period is
   * zero, the task is scheduled for one-time execution. Time is specified
   * in Date.getTime() format.  This method checks timer state, task state,
   * and initial execution time, but not period.
   *
   * @throws IllegalArgumentException if <tt>time</tt> is negative.
   * @throws IllegalStateException if task was already scheduled or
   *         cancelled, timer was cancelled, or timer thread terminated.
   * @throws NullPointerException if {@code task} is null
   */
  public void schedule(SimpleTask task, final long time) {
    if (time < 0) {
      throw new IllegalArgumentException("Illegal execution time.");
    }
    if (!task.schedule()) {
      throw new IllegalStateException(
        "Task already scheduled or cancelled");
    }
    task.executionTime = time;
    lock.lock();
    try {
      if (!thread.newTasksMayBeScheduled)
        throw new IllegalStateException("Timer already cancelled.");
      queue.add(task);
      if (queue.getMin() == task) {
        condition.signal();
        if (reachedJob != null) {
          clock.schedule(reachedJob, time);
        }
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Terminates this timer, discarding any currently scheduled tasks.
   * Does not interfere with a currently executing task (if it exists).
   * Once a timer has been terminated, its execution thread terminates
   * gracefully, and no more tasks may be scheduled on it.
   *
   * <p>Note that calling this method from within the run method of a
   * timer task that was invoked by this timer absolutely guarantees that
   * the ongoing task execution is the last task execution that will ever
   * be performed by this timer.
   *
   * <p>This method may be called repeatedly; the second and subsequent
   * calls have no effect.
   */
  public void cancel() {
    lock.lock();
    try {
      thread.newTasksMayBeScheduled = false;
      queue.clear();
      condition.signal();
      if (reachedJob != null) {
        clock.disableJob(reachedJob);
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Removes all cancelled tasks from this timer's task queue.  <i>Calling
   * this method has no effect on the behavior of the timer</i>, but
   * eliminates the references to the cancelled tasks from the queue.
   * If there are no external references to these tasks, they become
   * eligible for garbage collection.
   *
   * <p>Most programs will have no need to call this method.
   * It is designed for use by the rare application that cancels a large
   * number of tasks.  Calling this method trades time for space: the
   * runtime of the method may be proportional to n + c log n, where n
   * is the number of tasks in the queue and c is the number of cancelled
   * tasks.
   *
   * <p>Note that it is permissible to call this method from within a
   * a task scheduled on this timer.
   *
   * @return the number of tasks removed from the queue.
   * @since 1.5
   */
  public int purge() {
    lock.lock();
    try {
      int count = 0;
      for (int i = queue.size(); i > 0; i--) {
        if (queue.get(i).isCancelled()) {
          queue.quickRemove(i);
          count++;
        }
      }
      if (count != 0) {
        queue.heapify();
      }
      return count;
    } finally {
      lock.unlock();
    }
  }

  void timeReachedEvent(final long currentTime) {
    while (true) {
      SimpleTask task = null;
      lock.lock();
      try {
        while (true) {
          if (queue.isEmpty()) {
            return;
          }
          task = queue.getMin();
          long executionTime = task.executionTime;
          boolean fired = executionTime <= currentTime;
          if (fired) {
            queue.removeMin();
            if (!task.execute()) {
              continue;
            }
          }
          if (task.isCancelled()) {
            queue.removeMin();
            continue;
          }
          break;
        }
      } finally {
        lock.unlock();
      }
      if (task == null) {
        return;
      }
      if (!task.isScheduled()) {
        task.run();
      } else {
        clock.schedule(reachedJob, task.scheduledExecutionTime());
        break;
      }
    }
  }

/**
 * This "helper class" implements the timer's task execution thread, which
 * waits for tasks on the timer queue, executions them when they fire,
 * reschedules repeating tasks, and removes cancelled tasks and spent
 * non-repeating tasks from the queue.
 */
class TimerThread extends Thread {
  /**
   * This flag is set to false by the reaper to inform us that there
   * are no more live references to our Timer object.  Once this flag
   * is true and there are no more tasks in our queue, there is no
   * work left for us to do, so we terminate gracefully.  Note that
   * this field is protected by queue's monitor!
   */
  boolean newTasksMayBeScheduled = true;

  /**
   * Our Timer's queue.  We store this reference in preference to
   * a reference to the Timer so the reference graph remains acyclic.
   * Otherwise, the Timer would never be garbage-collected and this
   * thread would never go away.
   */
  private TaskQueue queue;

  private final InternalClock clock;

  private final Lock lock;
  private final Condition condition;

  TimerThread(InternalClock clock, Lock l, Condition c, TaskQueue queue) {
    lock = l;
    condition = c;
    this.clock = clock;
    this.queue = queue;
    setPriority(MAX_PRIORITY);
  }

  public void run() {
    try {
      mainLoop();
    } finally {
      lock.lock();
      try {
        newTasksMayBeScheduled = false;
        queue.clear();
      } finally {
        lock.unlock();
      }
    }

  }

  /**
   * The main timer loop.  (See class comment.)
   */
  private void mainLoop() {
    SimpleTask firedTask;
    while (newTasksMayBeScheduled) {
      lock.lock();
      try {
        while (true) {
          try {
            while (queue.isEmpty() && newTasksMayBeScheduled) {
              condition.await();
            }
            if (!newTasksMayBeScheduled) {
              return;
            }
            SimpleTask task = queue.getMin();
            if (task.isCancelled()) {
              queue.removeMin();
              continue;
            }
            long currentTime = clock.millis();
            long executionTime = task.executionTime;
            boolean fired = executionTime <= currentTime;
            if (fired) {
              queue.removeMin();
              if (!task.execute()) {
                continue;
              }
              firedTask = task;
              break;
            }
            condition.await(executionTime - currentTime, TimeUnit.MILLISECONDS);
          } catch(InterruptedException ignore) {
          }
        }
      } finally {
        lock.unlock();
      }
      firedTask.run();
    }
  }
}

/**
 * This class represents a timer task queue: a priority queue of TimerTasks,
 * ordered on nextExecutionTime.  Each Timer object has one of these, which it
 * shares with its TimerThread.  Internally this class uses a heap, which
 * offers log(n) performance for the add, removeMin and rescheduleMin
 * operations, and constant time performance for the getMin operation.
 */
class TaskQueue {
  /**
   * Priority queue represented as a balanced binary heap: the two children
   * of queue[n] are queue[2*n] and queue[2*n+1].  The priority queue is
   * ordered on the nextExecutionTime field: The TimerTask with the lowest
   * nextExecutionTime is in queue[1] (assuming the queue is nonempty).  For
   * each node n in the heap, and each descendant of n, d,
   * n.nextExecutionTime <= d.nextExecutionTime.
   */
  private SimpleTask[] queue = new SimpleTask[128];

  /**
   * The number of tasks in the priority queue.  (The tasks are stored in
   * queue[1] up to queue[size]).
   */
  private int size = 0;

  /**
   * Returns the number of tasks currently on the queue.
   */
  int size() {
    return size;
  }

  /**
   * Adds a new task to the priority queue.
   */
  void add(SimpleTask task) {
    if (size + 1 == queue.length)
      queue = Arrays.copyOf(queue, 2*queue.length);

    queue[++size] = task;
    fixUp(size);
  }

  /**
   * Return the "head task" of the priority queue.  (The head task is an
   * task with the lowest nextExecutionTime.)
   */
  SimpleTask getMin() {
    return queue[1];
  }

  /**
   * Return the ith task in the priority queue, where i ranges from 1 (the
   * head task, which is returned by getMin) to the number of tasks on the
   * queue, inclusive.
   */
  SimpleTask get(int i) {
    return queue[i];
  }

  /**
   * Remove the head task from the priority queue.
   */
  void removeMin() {
    queue[1] = queue[size];
    queue[size--] = null;
    fixDown(1);
  }

  /**
   * Removes the ith element from queue without regard for maintaining
   * the heap invariant.  Recall that queue is one-based, so
   * 1 <= i <= size.
   */
  void quickRemove(int i) {

    queue[i] = queue[size];
    queue[size--] = null;
  }

  /**
   * Returns true if the priority queue contains no elements.
   */
  boolean isEmpty() {
    return size==0;
  }

  /**
   * Removes all elements from the priority queue.
   */
  void clear() {
    for (int i=1; i<=size; i++)
      queue[i] = null;

    size = 0;
  }

  /**
   * Establishes the heap invariant (described above) assuming the heap
   * satisfies the invariant except possibly for the leaf-node indexed by k
   * (which may have a nextExecutionTime less than its parent's).
   *
   * This method functions by "promoting" queue[k] up the hierarchy
   * (by swapping it with its parent) repeatedly until queue[k]'s
   * nextExecutionTime is greater than or equal to that of its parent.
   */
  private void fixUp(int k) {
    while (k > 1) {
      int j = k >> 1;
      if (queue[j].executionTime <= queue[k].executionTime)
        break;
      SimpleTask tmp = queue[j];  queue[j] = queue[k]; queue[k] = tmp;
      k = j;
    }
  }

  /**
   * Establishes the heap invariant (described above) in the subtree
   * rooted at k, which is assumed to satisfy the heap invariant except
   * possibly for node k itself (which may have a nextExecutionTime greater
   * than its children's).
   *
   * This method functions by "demoting" queue[k] down the hierarchy
   * (by swapping it with its smaller child) repeatedly until queue[k]'s
   * nextExecutionTime is less than or equal to those of its children.
   */
  private void fixDown(int k) {
    int j;
    while ((j = k << 1) <= size && j > 0) {
      if (j < size &&
        queue[j].executionTime > queue[j+1].executionTime)
        j++; // j indexes smallest kid
      if (queue[k].executionTime <= queue[j].executionTime)
        break;
      SimpleTask tmp = queue[j];  queue[j] = queue[k]; queue[k] = tmp;
      k = j;
    }
  }

  /**
   * Establishes the heap invariant (described above) in the entire tree,
   * assuming nothing about the order of the elements prior to the call.
   */
  void heapify() {
    for (int i = size/2; i >= 1; i--)
      fixDown(i);
  }

}
}
