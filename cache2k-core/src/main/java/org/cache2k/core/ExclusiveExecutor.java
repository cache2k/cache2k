package org.cache2k.core;

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

import java.io.Closeable;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Wraps a thread pool executor that is used by one cache exclusively.
 * Used to decide whether the {@link HeapCache#toString()} should include the
 * thread pool statistics.
 *
 * @author Jens Wilke
 */
public class ExclusiveExecutor implements Executor, Closeable {

  private final ThreadPoolExecutor threadPoolExecutor;

  public ExclusiveExecutor(int threadCount, String threadNamePrefix) {
    final int corePoolThreadSize = 0;
    threadPoolExecutor =
      new ThreadPoolExecutor(corePoolThreadSize, threadCount,
        21, TimeUnit.SECONDS,
        new SynchronousQueue<Runnable>(),
        HeapCache.TUNABLE.threadFactoryProvider.newThreadFactory(threadNamePrefix),
        new ThreadPoolExecutor.AbortPolicy());
  }

  @Override
  public void execute(final Runnable cmd) {
    threadPoolExecutor.execute(cmd);
  }

  public ThreadPoolExecutor getThreadPoolExecutor() {
    return threadPoolExecutor;
  }

  @Override
  public void close() {
    threadPoolExecutor.shutdown();
  }

}
