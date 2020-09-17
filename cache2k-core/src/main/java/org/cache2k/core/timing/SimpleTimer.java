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
   * time, in milliseconds.
   *
   * @throws IllegalArgumentException if <tt>time</tt> is negative.
   * @throws IllegalStateException if task was already scheduled or
   *         cancelled, timer was cancelled, or timer thread terminated.
   * @throws NullPointerException if {@code task} is null
   */
  void schedule(SimpleTimerTask task, long time);

  /**
   * Cancel a timer task.
   */
  void cancel(SimpleTimerTask t);

  /**
   * Terminates all timer tasks currently pending.
   */
  void cancel();

  /**
   * Time tasks may lag behind.
   */
  long getLag();

}
