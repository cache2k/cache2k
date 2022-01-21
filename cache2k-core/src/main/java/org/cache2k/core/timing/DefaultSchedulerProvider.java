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

import org.cache2k.config.CacheBuildContext;
import org.cache2k.config.CustomizationSupplier;
import org.cache2k.CacheClosedException;
import org.cache2k.operation.Scheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Provides scheduler instances for caches which are backed by a common
 * {@link ScheduledThreadPoolExecutor} and two threads. The actual processing is done
 * via the common ForkJoinPool to reach higher parallelism if many caches are active
 * within a system.
 *
 * <p>When all caches are closed, this will also stop the daemon threads. This is needed
 * in case cache2k is used with separate classloaders.
 *
 * @author Jens Wilke
 * @see Scheduler
 */
public class DefaultSchedulerProvider implements CustomizationSupplier<Scheduler> {

  private static final int THREAD_COUNT = 2;
  private static final String THREAD_PREFIX = "cache2k-scheduler";
  public static final DefaultSchedulerProvider INSTANCE = new DefaultSchedulerProvider();

  private ScheduledExecutorService scheduledExecutor = null;
  private int usageCounter = 0;

  /**
   * Singleton, non private scope for testing only.
   */
  DefaultSchedulerProvider() { }

  @Override
  public synchronized Scheduler supply(CacheBuildContext<?, ?> buildContext) {
    if (usageCounter == 0) {
      scheduledExecutor = new ScheduledThreadPoolExecutor(
        THREAD_COUNT, new DaemonThreadFactory());
    }
    usageCounter++;
    return new DefaultScheduler(buildContext.getExecutor());
  }

  /**
   * Closing the last cache using the executor will shut down the executor
   * and free all remaining resources held by it.
   */
  synchronized void cacheClientClosed() {
    if (--usageCounter == 0) {
      scheduledExecutor.shutdownNow();
      try {
        scheduledExecutor.awaitTermination(1, TimeUnit.DAYS);
      } catch (InterruptedException ignore) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private class DefaultScheduler implements Scheduler, AutoCloseable {

    private final Executor executor;
    private boolean closed;

    private DefaultScheduler(Executor executor) {
      this.executor = executor;
    }

    /**
     * Wrap task to be executed in separate executor to not block the common
     * scheduler. Scheduling may race with closing of the cache in this
     * case RejectedExecutionException is thrown
     */
    @Override
    public void schedule(Runnable task, long delayMillis) {
      Runnable wrap = () -> executor.execute(task);
      scheduledExecutor.schedule(wrap, delayMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void execute(Runnable command) {
      executor.execute(command);
    }

    /**
     * Make sure usage counter is decreased exactly once.
     */
    @Override
    public synchronized void close() {
      if (!closed) {
        cacheClientClosed();
        closed = true;
      }
    }
  }

  private static final class DaemonThreadFactory implements ThreadFactory {
    public Thread newThread(Runnable r) {
      Thread t = new Thread(r);
      t.setDaemon(true);
      t.setName(THREAD_PREFIX);
      t.setPriority(Thread.MAX_PRIORITY);
      return t;
    }
  }

}
