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

/**
 * @author Jens Wilke
 */
public class RangeCheckClock extends ForwardingClock {

  private final long maxWaitMillis;

  public RangeCheckClock(final long _maxWaitMillis, final InternalClock _clock) {
    super(_clock);
    maxWaitMillis = _maxWaitMillis;
  }

  @Override
  public void waitMillis(final long _millis) throws InterruptedException {
    checkMaxTime(_millis);
    super.waitMillis(_millis);
  }

  @Override
  public void waitMillis(final Notifier n, final long _millis) throws InterruptedException {
    checkMaxTime(_millis);
    super.waitMillis(n, _millis);
  }

  private void checkMaxTime(final long _millis) {
    if (_millis > maxWaitMillis && _millis != Long.MAX_VALUE) {
      throw new IllegalArgumentException("max wait time exceeded: " + _millis + " > " + maxWaitMillis);
    }
  }

}
