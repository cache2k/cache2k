package org.cache2k.pinpoint;

/*
 * #%L
 * cache2k pinpoint
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
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps track on executed tasks and collects exceptions.
 * When testing we may want to use a thread pool for fast execution of
 * parallel tasks, and want to make sure that these tasks a completed without
 * exception at the end of the test.
 *
 * @author Jens Wilke
 */
public class SupervisedExecutor implements Executor, AutoCloseable {

  private final Executor executor;
  private final ExceptionCollector exceptionCollector = new ExceptionCollector();
  private final AtomicInteger notYetFinished = new AtomicInteger();
  private final Semaphore allFinished = new Semaphore(1);

  public SupervisedExecutor(Executor executor) {
    this.executor = executor;
  }

  @Override
  public void execute(Runnable command) {
    if (notYetFinished.incrementAndGet() == 1) {
      try {
        allFinished.acquire();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new CaughtInterruptedException(e);
      }
    }
    executor.execute(() -> {
      try {
        command.run();
      } catch (Throwable t) {
        t.printStackTrace();
        exceptionCollector.collect(t);
      } finally {
        if (notYetFinished.decrementAndGet() == 0) {
          allFinished.release();
        }
      }
    });
  }

  /**
   * Wait for all executed tasks to finish and propagate exceptions.
   */
  public void join() {
    try {
      allFinished.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CaughtInterruptedException(e);
    }
    exceptionCollector.assertNoException();
  }

  /**
   * Alias to join to use it with try with resources.
   */
  @Override
  public void close() {
    join();
  }

}
