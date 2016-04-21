package org.cache2k.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides a shared thread pool used by all caches for background refreshes of expired
 * entries. The maximum thread size is the processor count times two.
 *
 * @author Jens Wilke; created: 2013-06-13
 */
public class CacheRefreshThreadPool {

  public static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors() * 2;
  private static ThreadPoolExecutor executorForAll;
  private static int leasedPoolInstances = 0;
  private static MyStatus status;

  /**
   * Get an instance of the pool. When the consumer is destroyed it must
   * call {@link #destroy()} in turn to free resources.
   */
  public synchronized static CacheRefreshThreadPool getInstance() {
    if (executorForAll == null) {
      executorForAll =
        new ThreadPoolExecutor(0, THREAD_COUNT,
          21, TimeUnit.SECONDS,
          new SynchronousQueue<Runnable>(),
          new LoaderThreadFactory(),
          new ThreadPoolExecutor.AbortPolicy());
    }
    leasedPoolInstances++;
    CacheRefreshThreadPool p = new CacheRefreshThreadPool();
    p.executor = executorForAll;
    return p;
  }

  synchronized static void disposeOne() {
    leasedPoolInstances--;
    if (leasedPoolInstances == 0) {
      executorForAll.shutdown();
      executorForAll = null;
    }
  }

  private ThreadPoolExecutor executor;

  private CacheRefreshThreadPool() {
  }

  public boolean submit(Runnable r) {
    try {
      executor.execute(r);
    } catch (RejectedExecutionException e) {
      return false;
    }
    return true;
  }

  public void destroy() {
    disposeOne();
    executor = null;
  }

  static class LoaderThreadFactory implements ThreadFactory {

    AtomicInteger count = new AtomicInteger();

    @Override
    public synchronized Thread newThread(Runnable r) {
      Thread t = new Thread(r, "cache2k-loader-" + count.incrementAndGet());
      t.setDaemon(true);
      return t;
    }

  }

  static class MyStatus {
    public String toString() {
      return "CacheRefreshThreadPool(" +
              "size=" + executorForAll.getPoolSize() + ", " +
              "sizeLargest=" + executorForAll.getLargestPoolSize() + ", " +
              "sizeMax=" + executorForAll.getMaximumPoolSize() + ", " +
              "taskCount=" + executorForAll.getTaskCount() + ")";
    }
  }

}
