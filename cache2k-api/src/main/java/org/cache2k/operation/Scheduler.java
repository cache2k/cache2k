package org.cache2k.operation;

/*-
 * #%L
 * cache2k API
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

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler to run timer tasks, e.g. for expiry and refresh.
 * A cache with expiry typically inserts one task in the scheduler
 * to execute pending expiry or refresh jobs. A task is usually not
 * executed more often then one second per cache
 * {@link org.cache2k.Cache2kBuilder#timerLag(long, TimeUnit)} ). Per task execution all
 * pending actions per cache are processed. The execution of tasks should run
 * on a different executor to improve scalability when many caches with expiry run
 * in one runtime.
 *
 * <p>An instance may implement {@link AutoCloseable} if resources need to be cleaned up.
 *
 * @author Jens Wilke
 */
public interface Scheduler extends Executor {

  /**
   * Schedule the task to be run after a given delay. This uses always millisecond
   * resolution. A negative or zero duration means immediate execution is requested.
   */
  void schedule(Runnable runnable, long delayMillis);

  /**
   * Run a task immediately, usually via the common ForkJoinPool.
   * This is used for tasks that are due in the past and should
   * execute as soon as possible. This is intended to run on the
   * same executor then the scheduled tasks.
   */
  @Override
  void execute(Runnable command);

}
