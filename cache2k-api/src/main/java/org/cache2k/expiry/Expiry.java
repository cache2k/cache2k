package org.cache2k.expiry;

/*
 * #%L
 * cache2k API
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

import org.cache2k.io.ResiliencePolicy;

/**
 * Utility methods and constants to use inside expire policy and friends.
 *
 * @author Jens Wilke
 * @see ExpiryPolicy
 * @see ResiliencePolicy
 * @see org.cache2k.Cache#expireAt
 */
public class Expiry implements ExpiryTimeValues {

  /**
   * Convert the time value to a time representing an a sharp expiry.
   * This essentially negates the time value and is provided for a more speaking coding style.
   *
   * @param millis expiry time since the milliseconds since epoch or {@link #ETERNAL} if no
   *               expiry is requested.
   * @throws IllegalArgumentException if the time value is negative
   */
  public static long toSharpTime(long millis) {
    if (millis == ETERNAL) {
      return ETERNAL;
    }
    if (millis < 0) {
      return millis;
    }
    return -millis;
  }

  /**
   * Helper to calculate the next expiry out of two expiry times that
   * may be up next. Return the time value that is closest or equal to
   * the current time. Time values in the past are ignored. If all times
   * are in the past, returns {@link #ETERNAL}.
   *
   * @param loadTime the current time in millis since epoch
   * @param candidate1 candidate time for next expiry
   * @param candidate2 candidate time for next expiry
   * @return either first or second candidate or {@link #ETERNAL}
   */
  public static long earliestTime(long loadTime, long candidate1, long candidate2) {
    if (candidate1 >= loadTime) {
      if (candidate1 < candidate2 || candidate2 < loadTime) {
        return candidate1;
      }
    }
    if (candidate2 >= loadTime) {
      return candidate2;
    }
    return ETERNAL;
  }

  /**
   * Combine a refresh time span and an expiry at a specified point in time.
   *
   * <p>If the expiry time is far ahead of time the refresh time span takes
   * precedence. If the point in time is near, this time takes precedence.
   * If the refresh time is too close to the requested point an earlier refresh
   * time is used to keep maximum distance to the requested point in time, which is
   * {@code abs(pointInTime) - refreshAfter}
   *
   * <p>Rationale: Usually the expiry is allowed to lag behind. This is okay
   * when a normal expiry interval is used. If sharp expiry is requested an
   * old value may not be visible at and after the expiry time. Refresh ahead
   * implies lagging expiry, since the refresh is triggered when the value would
   * usually expire. The two concepts can be combined in the expiry policy, e.g.
   * using an interval for triggering refresh ahead and requesting a sharp expiry
   * only when needed. If effective expiry time for the lagging variant and the
   * for the sharp variant are in close proximity, conflicts need to be resolved.
   *
   * @param loadTime time when the load was started
   * @param refreshAfter time span in milliseconds when the next refresh should happen
   * @param pointInTime time in milliseconds since epoch for the next expiry. Can be negative
   *             if sharp expiry is requested, or {@link #ETERNAL} if no point in time
   *             expiry is needed.
   */
  public static long mixTimeSpanAndPointInTime(long loadTime, long refreshAfter, long pointInTime) {
    long refreshTime = loadTime + refreshAfter;
    if (refreshTime < 0) {
      refreshTime = ETERNAL;
    }
    if (pointInTime == ETERNAL) {
      return refreshTime;
    }
    if (pointInTime > refreshTime) {
      return refreshTime;
    }
    long absPointInTime = Math.abs(pointInTime);
    if (absPointInTime <= refreshTime) {
      return pointInTime;
    }
    long pointInTimeMinusDelta = absPointInTime - refreshAfter;
    if (pointInTimeMinusDelta < refreshTime) {
      return pointInTimeMinusDelta;
    }
    return refreshTime;
  }

}
