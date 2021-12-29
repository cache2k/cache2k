package org.cache2k.pinpoint;

/*-
 * #%L
 * cache2k pinpoint
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Collect exceptions or set final success for tasks running in concurrent threads.
 *
 * @author Jens Wilke
 */
public class TaskSuccessGuardian {

  private final Semaphore completed = new Semaphore(0);
  private final ExceptionCollector collector = new ExceptionCollector();
  private final AtomicBoolean success = new AtomicBoolean(false);

  public void exception(Throwable t) { collector.exception(t); completed.release(); }
  public void success() { success.set(true); completed.release(); }
  public void assertSuccess() {
    boolean f = false;
    try {
      f = completed.tryAcquire(PinpointParameters.TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted before task completion");
    }
    if (!f) {
      throw new AssertionError("Timeout waiting for task completion");
    }
    if (success.get()) {
      if (collector.getExceptionCount() != 0) {
        throw new AssertionError("Error: Exception and success reported");
      }
      return;
    }
    if (collector.getExceptionCount() == 0) {
      throw new AssertionError("Error: No exception and no success reported");
    }
    collector.assertNoException();
  }

  private void release() {
    completed.release();
  }

}
