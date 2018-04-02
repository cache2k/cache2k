package org.cache2k.core.util;

/*
 * #%L
 * cache2k implementation
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
  public boolean isJobSchedulable() {
    return clock.isJobSchedulable();
  }

  @Override
  public TimeReachedJob createJob(final TimeReachedEvent ev) {
    return clock.createJob(ev);
  }

  @Override
  public void schedule(final TimeReachedJob j, final long _millis) {
    clock.schedule(j, _millis);
  }

  @Override
  public void disableJob(final TimeReachedJob j) {
    clock.disableJob(j);
  }

  @Override
  public long millis() {
    return clock.millis();
  }

  @Override
  public void sleep(final long _millis) throws InterruptedException {
    clock.sleep(_millis);
  }

  @Override
  public void close() throws IOException {
    if (clock instanceof Closeable) {
      ((Closeable) clock).close();
    }
  }
}
