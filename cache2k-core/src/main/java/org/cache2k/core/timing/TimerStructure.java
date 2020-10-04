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
public interface TimerStructure {

  /**
   * Insert task. Scheduling might be not possible if tasks for the requested
   * time have already be run.
   *
   * @return true if scheduled successfully, false if scheduling was not possible
   *              because tasks have been run already
   */
  boolean schedule(TimerTask task, long time);

  /**
   * Cancel this timer task
   */
  void cancel(TimerTask t);

  /**
   * Cancel all tasks
   */
  void cancel();

  /**
   * Return a task that is supposed to execute at the given time or earlier.
   * This also moves the clock hand of the timer structure. Once a time slot
   * is reached and execution has started the structure does not except scheduling tasks
   *
   * @return a task or null, if no more tasks are scheduled for the given time
   */
  TimerTask removeNextToRun(long time);

  /**
   * Time of next run, or -1 if no more tasks are scheduled
   */
  long nextRun();

}
