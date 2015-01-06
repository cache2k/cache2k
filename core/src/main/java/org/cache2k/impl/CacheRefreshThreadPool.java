package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2015 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
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
          new MyThreadFactory(),
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

  static class MyThreadFactory implements ThreadFactory {

    AtomicInteger count = new AtomicInteger();

    @Override
    public synchronized Thread newThread(Runnable r) {
      Thread t = new Thread(r, "cache-refresh-" + count.incrementAndGet());
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
