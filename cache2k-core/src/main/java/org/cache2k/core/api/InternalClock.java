package org.cache2k.core.api;

/*
 * #%L
 * cache2k core implementation
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
 * Abstraction for the system clock. A simulated clock implementation is used to
 * test timing related code in a faster way. A simulated clock needs also to implement
 * an {@link Scheduler} based on the other timing.
 *
 * <p>Simulated clocks need to implement {@link Scheduler} as well to trigger
 * timed tasked.
 *
 * @author Jens Wilke
 */
public interface InternalClock {

  /**
   * Returns the milliseconds since epoch. In the simulated clock a call to this method
   * would make time pass in small increments.
   */
  long millis();

  /**
   * Wait for the specified amount of time in milliseconds.
   *
   * <p>The value of 0 means that the thread should pause and other processing should be
   * done. In the simulated clock this would wait for concurrent processing and, if
   * no processing is happening, advance the time to the next event.
   */
  void sleep(long millis) throws InterruptedException;

}
