package org.cache2k.operation;

/*-
 * #%L
 * cache2k API
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
import java.time.Instant;

/**
 * Time reference for a cache. By default, the current time is retrieved with
 * {@link System#currentTimeMillis()}. Another time reference can be specified
 * if the application uses a different time source or when a simulated clock should be used.
 *
 * <p>For efficiency reasons cache2k always uses a long to store a time value internally.
 * The resolution is expected to be milliseconds or higher. There is currently no
 * intensive testing carried out with different timer resolutions.
 *
 * <p>An instance may implement {@link AutoCloseable} if resources need to be cleaned up.
 *
 * @author Jens Wilke
 */
public interface TimeReference {

  /**
   * Every time reference is expected to produce times higher or equal to this value.
   * cache2k uses lower values for expired or invalid entries, which must be guaranteed
   * in the past.
   */
  long MINIMUM_TICKS = 100;

  /**
   * Default implementation using {@link System#currentTimeMillis()} as time reference.
   */
  TimeReference DEFAULT = new Default();

  /**
   * Returns the timer ticks since a reference point, typically milliseconds since epoch.
   * Expected to be always equal or higher than {@value MINIMUM_TICKS}.
   */
  long ticks();

  /**
   * Wait for the specified amount of time. This is only executed by tests and never
   * by the cache itself.
   *
   * <p>The value of 0 means that the thread should pause and other processing should be
   * done. In a simulated clock this would wait for concurrent processing and, if
   * no processing is happening, advance the time to the next event.
   */
  void sleep(long ticks) throws InterruptedException;

  /**
   * Convert a duration in ticks to milliseconds, rounding up to the next full
   * millisecond.
   *
   * <p>This can be overridden in case another timescale is used.
   * Conversion is needed for correctly scheduling a timer task that regularly process
   * the expiry tasks.
   */
  long ticksToMillisCeiling(long ticks);

  /**
   * Convert a duration to ticks. Used to convert configuration durations.
   */
  long toTicks(Duration v);

  /**
   * Convert a time value in ticks to an instant object. Needed for display
   * purposes.
   */
  Instant ticksToInstant(long timeInTicks);

  abstract class Milliseconds implements TimeReference {

    @Override
    public long ticksToMillisCeiling(long ticks) {
      return ticks;
    }

    @Override
    public long toTicks(Duration v) { return v.toMillis(); }

    @Override
    public Instant ticksToInstant(long ticks) {
      return Instant.ofEpochMilli(ticks);
    }
  }

  final class Default extends Milliseconds {

    private Default() { }

    @Override
    public long ticks() {
      return System.currentTimeMillis();
    }

    @Override
    public void sleep(long ticks) throws InterruptedException {
      Thread.sleep(ticks);
    }

  }

}
