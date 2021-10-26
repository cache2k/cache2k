package org.cache2k.pinpoint;

/*
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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Collects exceptions happening in concurrent threads and propagates
 * them in the main thread. Right now only the first exception is kept.
 * This may be improved to collect all exceptions.
 *
 * @author Jens Wilke
 */
public class ExceptionCollector {

  private final AtomicReference<Throwable> firstException = new AtomicReference<>();
  private final AtomicInteger exceptionCount = new AtomicInteger();

  /**
   * Collect exception or ignore if null.
   */
  public void exception(Throwable t) {
    firstException.compareAndSet(null, t);
    if (t != null) {
      exceptionCount.incrementAndGet();
    }
  }

  public void assertNoException() {
    if (firstException.get() != null) {
      throw new AssertionError(
        "No exception expected, " + exceptionCount.get() +
        " exceptions, first one propagated", getFirstException());
    }
  }

  /**
   * Return the first exception that was collected, or null if no exception was collected.
   */
  public Throwable getFirstException() {
    return firstException.get();
  }

  public int getExceptionCount() {
    return exceptionCount.get();
  }

}
