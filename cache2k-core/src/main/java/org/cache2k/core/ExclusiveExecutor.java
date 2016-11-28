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

import java.io.Closeable;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Wraps a thread pool executor that is used by one cache exclusively.
 *
 * @author Jens Wilke
 */
public class ExclusiveExecutor implements Executor, Closeable {

  private ThreadPoolExecutor threadPoolExecutor;

  public ExclusiveExecutor(int _threadCount, String _threadNamePrefix) {
    final int _corePoolThreadSize = _threadCount;
    threadPoolExecutor =
      new ThreadPoolExecutor(_corePoolThreadSize, _threadCount,
        21, TimeUnit.SECONDS,
        new LinkedBlockingQueue<Runnable>(),
        HeapCache.TUNABLE.threadFactoryProvider.newThreadFactory(_threadNamePrefix),
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
