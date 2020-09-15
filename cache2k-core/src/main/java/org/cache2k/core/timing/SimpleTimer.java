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

/**
 * @author Jens Wilke
 */
public interface SimpleTimer {

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
  void schedule(SimpleTimerTask task, long time);

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
  void cancel();

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
  int purge();

  void cancel(SimpleTimerTask t);

}
