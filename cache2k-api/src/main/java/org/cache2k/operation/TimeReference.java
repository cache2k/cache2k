package org.cache2k.operation;

/*-
 * #%L
 * cache2k API
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

import org.cache2k.CacheEntry;
import org.cache2k.processor.MutableCacheEntry;

/**
 * Time reference for a cache. By default the current time is retrieved with
 * {@link System#currentTimeMillis()}. Another time reference can be specified
 * if the application uses a different time source or when a simulated clock should be used.
 *
 *  <p>An instance may implement {@link AutoCloseable} if resources need to be cleaned up.
 *
 * @author Jens Wilke
 */
public interface TimeReference {

  /**
   * Default implementation using {@link System#currentTimeMillis()} as time reference.
   */
  TimeReference DEFAULT = new TimeReference() {
    @Override
    public long millis() {
      return System.currentTimeMillis();
    }

    @Override
    public void sleep(long millis) throws InterruptedException {
      Thread.sleep(millis);
    }
  };

  /**
   * Returns the milliseconds since epoch. In the simulated clock a call to this method
   * would make time pass in small increments.
   *
   * <p>It is possible to use other time scales and references (e.g. nano seconds). In
   * this case the method {@link #toMillis(long)} needs to be implemented. All times in the
   * cache API, e.g. {@link MutableCacheEntry#getExpiryTime()} or in
   * {@link org.cache2k.io.AdvancedCacheLoader#load(Object, long, CacheEntry)} are based on the
   * time defined here.
   */
  long millis();

  /**
   * Wait for the specified amount of time in milliseconds.
   *
   * <p>The value of 0 means that the thread should pause and other processing should be
   * done. In a simulated clock this would wait for concurrent processing and, if
   * no processing is happening, advance the time to the next event.
   */
  void sleep(long millis) throws InterruptedException;

  /**
   * Convert a value returned by {@link #millis()} to milliseconds since epoch.
   * This can be overridden in case another time scale and or reference is used.
   * Conversion is needed for correctly scheduling timer task that regularly process
   * the expiry tasks.
   */
  default long toMillis(long millis) {
    return millis;
  }

}
