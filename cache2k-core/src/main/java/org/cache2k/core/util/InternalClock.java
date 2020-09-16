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

import org.cache2k.TimeReference;

/**
 * Abstraction for the system clock. A simulated clock implementation is used to
 * test timing related code in a faster way. A simulated clock needs also to implement
 * an {@link org.cache2k.core.timing.Scheduler} based on the other timing.
 *
 * @author Jens Wilke
 */
public interface InternalClock extends TimeReference {

  /**
   * Returns the milliseconds since epoch. When using a simulated clock
   * either this method or {@link #sleep(long)} needs to be called to make time pass by and
   * make this method return an increased number.
   */
  long millis();

  /**
   * Wait for the specified amount of time in milliseconds. The value of 0 means that
   * the thread may sleep some tiny amount of time or not at all.
   *
   * <p>When using a simulated clock either this method or {@link #millis} needs to be
   * called to make time pass and make {@link #millis} return an increased number.
   */
  void sleep(long millis) throws InterruptedException;

}
