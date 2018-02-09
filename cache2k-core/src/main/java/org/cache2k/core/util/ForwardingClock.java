package org.cache2k.core.util;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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
import java.io.IOException;

/**
 * @author Jens Wilke
 */
public class ForwardingClock implements InternalClock, Closeable {

  private final InternalClock clock;

  public ForwardingClock(final InternalClock _clock) {
    clock = _clock;
  }

  @Override
  public long millis() {
    return clock.millis();
  }

  @Override
  public void waitMillis(final long _millis) throws InterruptedException {
    clock.waitMillis(_millis);
  }

  @Override
  public Notifier createNotifier() {
    return clock.createNotifier();
  }

  @Override
  public void waitMillis(final Notifier n, final long _millis) throws InterruptedException {
    clock.waitMillis(n, _millis);
  }

  @Override
  public void runExclusive(final Notifier n, final Runnable r) {
    clock.runExclusive(n, r);
  }

  @Override
  public <T, R> R runExclusive(final Notifier n, final T v, final ExclusiveFunction<T, R> r) {
    return runExclusive(n, v, r);
  }

  @Override
  public void close() throws IOException {
    if (clock instanceof Closeable) {
      ((Closeable) clock).close();
    }
  }
}
