package org.cache2k.core.api;

/*
 * #%L
 * cache2k core implementation
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

import java.util.concurrent.Executor;

/**
 * Simple interface the cache uses to schedule tasks for timer
 * jobs that are handling expiry or triggering a refresh.
 *
 * @author Jens Wilke
 */
public interface Scheduler extends Executor {

  /**
   * Schedule a task to be run at the given time
   */
  void schedule(Runnable runnable, long millis);

  /**
   * Run a task immediately, usually via the common ForkJoinPool.
   * This is used for tasks that are due in the past and should
   * executed as soon as possible. Since this is intended to run on the
   * same executor then the scheduled tasks this method is provided
   * here.
   */
  @Override
  void execute(Runnable command);

}
