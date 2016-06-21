package org.cache2k.expiry;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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
 * Utility methods and constants for expiry.
 *
 * @author Jens Wilke
 */
public class Expiry implements ExpiryTimeValues {

  /**
   * Convert the time value to a time representing an a sharp expiry.
   * This essentially negates the time value and is provided for a more speaking coding style.
   *
   * @param millis
   * @throws IllegalArgumentException if the time value is negative
   * @return a negated time representing sharp expiry
   */
  public static long toSharpTime(long millis) {
    if (millis < 0) {
      throw new IllegalArgumentException("Positive time value expected");
    }
    return -millis;
  }

  /**
   * Helper to calculate the next expiry out of two expiry times that
   * may be up next. Return the time value that is closest or equal to
   * the current time. Time values in the past are ignored. If all times
   * are in the past, returns {@link #ETERNAL}.
   *
   * @param now the current time in millis since epoch
   * @param candidate1 candidate time for next expiry
   * @param candidate2 candidate time for next expiry
   * @return either first or second candidate or {@link #ETERNAL}
   */
  public static long earliestTime(long now, long candidate1, long candidate2) {
    if (candidate1 >= now) {
      if (candidate1 < candidate2 || candidate2 < now) {
        return candidate1;
      }
    }
    if (candidate2 >= now) {
      return candidate2;
    }
    return ETERNAL;
  }

}
