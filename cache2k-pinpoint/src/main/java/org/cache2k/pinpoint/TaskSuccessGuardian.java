package org.cache2k.pinpoint;

/*-
 * #%L
 * cache2k pinpoint
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

import java.time.Duration;
import java.util.concurrent.Semaphore;

/**
 * Collect exceptions or set final success for tasks running in concurrent threads.
 *
 * @author Jens Wilke
 */
public class TaskSuccessGuardian {

  private final Semaphore completed = new Semaphore(0);
  private final ExceptionCollector collector = new ExceptionCollector();
  private final Duration timeout;

  public TaskSuccessGuardian() {
    this(PinpointParameters.TIMEOUT);
  }

  public TaskSuccessGuardian(Duration timeout) {
    this.timeout = timeout;
  }

  public void completedWithException(Throwable t) { collector.exception(t); completed.release(); }
  public void completedWithSuccess() { completed.release(); }
  public void awaitCompletionAndAssertSuccess() {
    SupervisedExecutor.acquireOrTimeout(completed, timeout);
    collector.assertNoException();
  }
  public void executeGuarded(ExceptionalRunnable runnable) {
    try {
      runnable.run();
      completedWithSuccess();
    } catch (Throwable ex) {
      completedWithException(ex);
    }
  }

  @FunctionalInterface
  public interface ExceptionalRunnable {
    void run() throws Exception;
  }

}
