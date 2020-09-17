package org.cache2k.core.timing;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
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

/**
 * Heap queue implementation from java.util.Timer.
 *
 * @author Jens Wilke
 */
public class QueueTimerStructure extends TimerStructure {

  static final int PURGE_INTERVAL = 10000;

  /**
   * The timer task queue.  This data structure is shared with the timer
   * thread.  The timer produces tasks, via its various schedule calls,
   * and the timer thread consumes, executing timer tasks as appropriate,
   * and removing them from the queue when they're obsolete.
   */
  private final TaskQueue queue = new TaskQueue();
  private long timerCancelCount = 0;

  @Override
  public boolean schedule(SimpleTimerTask task, long time) {
    task.executionTime = time;
    queue.add(task);
    return queue.getMin() == task;
  }

  @Override
  public void cancel(SimpleTimerTask t) {
    if (t.cancel()) {
      timerCancelCount++;
      if (timerCancelCount >= PURGE_INTERVAL) {
        purge();
        timerCancelCount = 0;
      }
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
  int purge() {
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
  }

  @Override
  public void cancel() {
    queue.clear();
  }

  @Override
  public SimpleTimerTask removeNextToRun(long now) {
    SimpleTimerTask task;
    while (true) {
      if (queue.isEmpty()) {
        return null;
      }
      task = queue.getMin();
      if (task.isCancelled()) {
        queue.removeMin();
        continue;
      }
      long executionTime = task.executionTime;
      boolean reached = executionTime <= now;
      if (!reached) {
        return null;
      }
      queue.removeMin();
      if (!task.execute()) {
        continue;
      }
      return task;
    }
  }

  @Override
  public long nextRun() {
    SimpleTimerTask task;
    while (true) {
      if (queue.isEmpty()) {
        return -1;
      }
      task = queue.getMin();
      if (task.isCancelled()) {
        queue.removeMin();
        continue;
      }
      return task.executionTime;
    }
  }

  /**
   * This class represents a timer task queue: a priority queue of TimerTasks,
   * ordered on nextExecutionTime.  Each Timer object has one of these, which it
   * shares with its TimerThread.  Internally this class uses a heap, which
   * offers log(n) performance for the add, removeMin and rescheduleMin
   * operations, and constant time performance for the getMin operation.
   */
  static class TaskQueue {
    /**
     * Priority queue represented as a balanced binary heap: the two children
     * of queue[n] are queue[2*n] and queue[2*n+1].  The priority queue is
     * ordered on the nextExecutionTime field: The TimerTask with the lowest
     * nextExecutionTime is in queue[1] (assuming the queue is nonempty).  For
     * each node n in the heap, and each descendant of n, d,
     * n.nextExecutionTime <= d.nextExecutionTime.
     */
    private SimpleTimerTask[] queue = new SimpleTimerTask[128];

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
    void add(SimpleTimerTask task) {
      if (size + 1 == queue.length)
        queue = Arrays.copyOf(queue, 2 * queue.length);

      queue[++size] = task;
      fixUp(size);
    }

    /**
     * Return the "head task" of the priority queue.  (The head task is an
     * task with the lowest nextExecutionTime.)
     */
    SimpleTimerTask getMin() {
      return queue[1];
    }

    /**
     * Return the ith task in the priority queue, where i ranges from 1 (the
     * head task, which is returned by getMin) to the number of tasks on the
     * queue, inclusive.
     */
    SimpleTimerTask get(int i) {
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
      return size == 0;
    }

    /**
     * Removes all elements from the priority queue.
     */
    void clear() {
      for (int i = 1; i <= size; i++)
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
        SimpleTimerTask tmp = queue[j];  queue[j] = queue[k]; queue[k] = tmp;
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
          queue[j].executionTime > queue[j + 1].executionTime)
          j++; // j indexes smallest kid
        if (queue[k].executionTime <= queue[j].executionTime)
          break;
        SimpleTimerTask tmp = queue[j];  queue[j] = queue[k]; queue[k] = tmp;
        k = j;
      }
    }

    /**
     * Establishes the heap invariant (described above) in the entire tree,
     * assuming nothing about the order of the elements prior to the call.
     */
    void heapify() {
      for (int i = size / 2; i >= 1; i--)
        fixDown(i);
    }

  }

}
