package org.cache2k.core.util;

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

/**
 * Default clock implementation just using {@link System#currentTimeMillis()} as
 * time reference.
 *
 * @author Jens Wilke
 */
public final class ClockDefaultImpl implements InternalClock {

  public static final ClockDefaultImpl INSTANCE = new ClockDefaultImpl();

  private ClockDefaultImpl() { }

  @Override
  public boolean isJobSchedulable() {
    return false;
  }

  @Override
  public TimeReachedJob createJob(TimeReachedEvent ev) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void schedule(TimeReachedJob j, long millis) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void disableJob(TimeReachedJob j) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long millis() {
    return System.currentTimeMillis();
  }

  @Override
  public void sleep(long millis) throws InterruptedException {
    Thread.sleep(millis);
  }

}
