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

import org.cache2k.core.api.NeedsClose;

/**
 * Timer for triggering expiry or refresh.
 *
 * @author Jens Wilke
 */
public interface Timer extends NeedsClose {

  /**
   * Schedule the specified timer task for execution at the specified
   * time, in milliseconds. If the execution time is reached already
   * the task is executed immediately in a separate thread.
   *
   * @param time the time when the task should be run. must be positive or 0.
   *             The value {@value Long#MAX_VALUE} is illegal since this would
   *             represent eternal timer and does not need to be scheduled.
   */
  void schedule(TimerTask task, long time);

  /**
   * Cancel the timer task.
   */
  void cancel(TimerTask t);

  /**
   * Terminates all timer tasks currently pending. This does not mark the timer tasks
   * as cancelled, just drops all tasks from the timer data structure.
   */
  void cancelAll();

  /**
   * The lag time tasks may lag behind.
   */
  long getLagTicks();

}
