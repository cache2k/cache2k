package org.cache2k.core.timing;

/*-
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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
 * Interface of the timer task data structure.
 *
 * <p>The interface allows different implementations for timer data structures, like
 * tree, heap oder timer wheels. The interface is not 100% abstracted, since
 * the implementation makes use of TimerTask internals (prev and next pointers).
 *
 * <p>The timer data structure is not supposed to be thread safe, it is called with
 * proper locking from the timer code.
 *
 * @author Jens Wilke
 */
public interface TimerStructure {

  /**
   * Insert task. Scheduling might be not possible if tasks for the requested
   * time have already be run.
   *
   * @return 0, if time is already in the past. Or a positive value
   *         indicating the execution time of the timer slot
   */
  long schedule(TimerTask task, long time);

  /**
   * Cancel all tasks
   */
  void cancelAll();

  /**
   * Return a task that is supposed to execute at the given time or earlier.
   * This also moves the clock hand of the timer structure.
   *
   * <p>It may rarely happen that a subsequent method call has an earlier
   * time, in case the operating system schedule delays a thread until the
   * next scheduler event happens.
   *
   * @return a task or null, if no more tasks are scheduled for the given time
   */
  TimerTask removeNextToRun(long time);

  /**
   * Time of next run, or MAX_VALUE if no more tasks are scheduled
   */
  long nextRun();

}
