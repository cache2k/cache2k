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

import org.cache2k.config.CacheBuildContext;
import org.cache2k.config.CustomizationSupplier;
import org.cache2k.core.CacheClosedException;
import org.cache2k.core.HeapCache;
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
 */
public class DefaultSchedulerProvider implements CustomizationSupplier<Scheduler> {

  public static final DefaultSchedulerProvider INSTANCE = new DefaultSchedulerProvider();

  private final Executor pooledExecutor = HeapCache.SHARED_EXECUTOR;
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
        2, new DaemonThreadFactory());
    }
    usageCounter++;
    return new MyScheduler();
  }

  /**
   * Closing the last cache using the executor will shutdown the executor
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

  private class MyScheduler implements Scheduler, AutoCloseable {

    private boolean closed;

    /**
     * Wrap task to be executed in separate executor to not black the common
     * scheduler. Scheduling may race with closing of the cache. When shut down
     * the ScheduledExcecutorService is throwing a RejectedExecutionException.
     * Rethrow as CacheClosedException. When this exception happens internally, it
     * is masked, if its propagated to the client its the "correct" exception since
     * the client was issuing a close in parallel.
     */
    @Override
    public void schedule(Runnable task, long millis) {
      Runnable wrap = () -> pooledExecutor.execute(task);
      long delay = millis - System.currentTimeMillis();
      delay = Math.max(0, delay);
      try {
        scheduledExecutor.schedule(wrap, delay, TimeUnit.MILLISECONDS);
      } catch (RejectedExecutionException ex) {
        throw new CacheClosedException();
      }
    }

    @Override
    public void execute(Runnable command) {
      pooledExecutor.execute(command);
    }

    /**
     * Make sure usage counter is decreased exactly once.
     */
    @Override
    public synchronized void close() throws Exception {
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
      t.setName("cache2k-scheduler");
      t.setPriority(Thread.MAX_PRIORITY);
      return t;
    }
  }

}
