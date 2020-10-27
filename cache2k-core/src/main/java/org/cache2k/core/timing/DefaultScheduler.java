package org.cache2k.core.timing;

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

import org.cache2k.core.HeapCache;
import org.cache2k.core.api.Scheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * All caches use the singleton scheduler {@link DefaultScheduler#INSTANCE}.
 * A {@link ScheduledThreadPoolExecutor} executor is used with two threads to
 * execute the tasks. The actual processing is done via the common ForkJoinPool to
 * reach higher parallelism if many caches are active within a system.
 *
 * @author Jens Wilke
 */
public class DefaultScheduler implements Scheduler {

  public static final Scheduler INSTANCE = new DefaultScheduler();

  private Executor pooledExecutor = HeapCache.SHARED_EXECUTOR;
  private ScheduledExecutorService scheduledExecutor = new ScheduledThreadPoolExecutor(
    2, new DaemonThreadFactory());

  private DefaultScheduler() { }

  @Override
  public void schedule(final Runnable task, long millis) {
    Runnable wrap = new Runnable() {
      @Override
      public void run() {
        pooledExecutor.execute(task);
      }
    };
    long delay = millis - System.currentTimeMillis();
    delay = Math.max(0, delay);
    scheduledExecutor.schedule(wrap, delay, TimeUnit.MILLISECONDS);
  }

  @Override
  public void execute(Runnable command) {
    pooledExecutor.execute(command);
  }

  static final class DaemonThreadFactory implements ThreadFactory {
    public Thread newThread(Runnable r) {
      Thread t = new Thread(r);
      t.setDaemon(true);
      t.setName("cache2k-scheduler");
      t.setPriority(Thread.MAX_PRIORITY);
      return t;
    }
  }

}
