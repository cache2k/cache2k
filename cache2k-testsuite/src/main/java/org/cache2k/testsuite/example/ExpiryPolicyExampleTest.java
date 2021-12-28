package org.cache2k.testsuite.example;

/*
 * #%L
 * cache2k testsuite on public API
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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.expiry.Expiry;
import org.cache2k.expiry.ValueWithExpiryTime;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Step by step examples for different scenarios of using a expiry policy.
 *
 * @author Jens Wilke
 */
public class ExpiryPolicyExampleTest {

  static class Data { }

  /** No expiry policy, just constant */
  @Test
  public void staticExample() {
    Cache<Key, Data> cache = new Cache2kBuilder<Key, Data>() {}
      .loader(k -> new Data())
      .expireAfterWrite(5, TimeUnit.MINUTES)
      .build();
    Key key1 = new Key();
    Data value = cache.get(key1);
    cache.close();
  }

  /** Sharp expiry with expiry policy */
  @Test
  public void sharpStaticExample() {
    Cache<Key, Data> cache = new Cache2kBuilder<Key, Data>() {}
      .loader(k -> new Data())
      .sharpExpiry(true)
      .expiryPolicy((key, value, startTime, currentEntry)
        -> startTime + TimeUnit.MINUTES.toMillis(5))
      .build();
    Key key1 = new Key();
    Data value = cache.get(key1);
    cache.close();
  }

  /**
   * Opaque key class just to make the example look batter and don't
   * have Integer and String everywhere.
   */
  static class Key { }

  /**
   * A cached value contains information how long it is considered fresh. After that,
   * it needs to be updated.
   */
  static class DataWithMaxAge {
    long getMaxAgeMillis() { return 4711; }
  }

  /**
   * Variable expiry based on duration derived from value.
   * For efficiency reasons the actual expiry is lagging a bit and might be up to a second
   * longer then the duration. It is possible to expire at an exact time, see next example.
   * The timer lag can be change as well, see {@link Cache2kBuilder#timerLag(long, TimeUnit)}
   *
   * <p>The entry is expired and removed from the cache at the same time.
   *
   * <p>Processing of expiry is very efficient and happens in O(1) time. To achieve that
   * the cache is using a timer wheel algorithm.
   */
  @Test
  public void maxAgeExample() {
    Cache<Key, DataWithMaxAge> cache = new Cache2kBuilder<Key, DataWithMaxAge>() {}
      .loader(k -> new DataWithMaxAge())
      .expiryPolicy((key, value, startTime, currentEntry) -> startTime + value.getMaxAgeMillis())
      .build();
    Key key1 = new Key();
    DataWithMaxAge value = cache.get(key1);
    cache.close();
  }

  /**
   * A cached value contains information that expires at an exact point in time.
   * Once that time is reached the value must not be returned by the cache
   * any more and it needs to be updated.
   *
   * <p>Let's take the price of a flight as an example. The booking system
   * might tell us exactly when it recalculating the offer prices.
   */
  static class DataWithPointInTime {
    Double getFlightPrice() { return 47.11; }
    ZonedDateTime getValidBefore() {
      return ZonedDateTime.parse("2007-12-03T10:15:30+01:00[Europe/Paris]");
    }
  }

  /**
   * Variable expiry based on a point in time derived from the value.
   * Since the business defines an exact time of expiry, the cache may never return an
   * expired value. This is specified via {@link Cache2kBuilder#sharpExpiry(boolean)}.
   *
   * <p>The entry expires based on the time comparison. Actual removal from the cache may
   * happen a bit later when the timer event triggers.
   *
   * <p>If the returned expiry time is already in the past or identical to the current time
   * the entry will expire immediately. If that happens upon a cache read through request
   * {@code Cache.get}) the request will return with the loaded data, the cache is not
   * doing a retry. The subsequent read through request will trigger a new load.
   *
   * <p>If no expiry time information is available, the data is expected not to
   * expire in this example. If no expiry is needed, {@link Expiry#ETERNAL} is returned,
   * which is identical {@link Long#MAX_VALUE}. It is not a good idea to return an arbitrary
   * large future time, because the cache will establish data structures to remember
   * that the data needs to expire in thousand years, if instructed so.
   */
  @Test
  public void pointInTimeExample() {
    Cache<Key, DataWithPointInTime> cache = new Cache2kBuilder<Key, DataWithPointInTime>() { }
      .loader(k -> new DataWithPointInTime())
      .sharpExpiry(true)
      .expiryPolicy((key, value, startTime, currentEntry) -> {
        ZonedDateTime time = value.getValidBefore();
        return time != null ? time.toInstant().toEpochMilli() : Expiry.ETERNAL;
      })
      .build();
    cache.close();
  }

  /**
   * Although we know that data needs to updated at a certain point in time, we might still
   * want to update it in a regular interval. Instead of enabling sharp timing globally
   * the expiry policy can return exact times, or times that are based on duration and may
   * have a lag
   */
  @Test
  public void pointInTimeAndDurationExample() {
    long minimumUpdateIntervalMillis = TimeUnit.MINUTES.toMillis(5);
    long maximumExpectedLagMillis = 10_000;
    Cache<Key, DataWithPointInTime> cache = new Cache2kBuilder<Key, DataWithPointInTime>() {}
      .loader(k -> new DataWithPointInTime())
      .expiryPolicy((key, value, startTime, currentEntry) -> {
        ZonedDateTime time = value.getValidBefore();
        if (time == null) {
          return Expiry.ETERNAL;
        }
        long pointInTime = time.toInstant().toEpochMilli();
        if (pointInTime - minimumUpdateIntervalMillis - maximumExpectedLagMillis < startTime) {
          return startTime + minimumUpdateIntervalMillis;
        }
        return Expiry.toSharpTime(pointInTime);
      })
      .build();
    cache.close();
  }

  /**
   * The interface {@link ValueWithExpiryTime} is provided by the cache API as a standard
   * way to retrieve the expiry time from a cache value.
   *
   * <p>Depending on preference it can make sense to keep expiry time calculations
   * in the data object rather than in the cache setup.
   */
  static class DataWithCacheExpiryTime implements ValueWithExpiryTime {
    Double getFlightPrice() { return 47.11; }
    ZonedDateTime getValidBefore() {
      return ZonedDateTime.parse("2007-12-03T10:15:30+01:00[Europe/Paris]");
    }
    @Override
    public long getCacheExpiryTime() {
      return getValidBefore().toInstant().toEpochMilli();
    }
  }

  /**
   * If the cache is build with a value type that implements {@link ValueWithExpiryTime} it
   * automatically adds an expiry policy using the value content. No additional configuration
   * is needed.
   */
  @Test
  public void expiryTimeFromValueExample() {
    Cache<Key, DataWithCacheExpiryTime> cache = new Cache2kBuilder<Key, DataWithCacheExpiryTime>() {}
      .loader(k -> new DataWithCacheExpiryTime())
      .build();
    cache.put(new Key(), new DataWithCacheExpiryTime());
    cache.close();
  }

  /**
   * The value contains some kind of gauge. For example, the value might represent
   * the current booking status of a flight and whether seats are available for booking
   * or not. We don't like to show how many seats are available on the airplane, however,
   * we must show to our potential customer whether whether seats are available at all.
   * In case there are a lot of seats available, we can update the data less frequently.
   */
  static class ValueWithGauge {
    int seatsAvailable = 4711;
    boolean isNoAvailability() { return seatsAvailable == 0; }
    boolean isLimitedAvailability() { return seatsAvailable > 0 && seatsAvailable < 10; }
    boolean isFullAvailable() { return seatsAvailable >= 10; }
  }

  /**
   * Straight forward approach: Once the seat count reaches below 20 update more frequently.
   */
  @Test
  public void gaugeSimpleApproach() {
    int threshold = 20;
    long relaxedMillis = TimeUnit.MINUTES.toMillis(5);
    long frequentMillis = TimeUnit.SECONDS.toMillis(27);
    Cache<Key, ValueWithGauge> cache = new Cache2kBuilder<Key, ValueWithGauge>() { }
      .loader(k -> new ValueWithGauge())
      .expiryPolicy((key, value, startTime, currentEntry) -> {
        if (value.seatsAvailable == 0 || value.seatsAvailable >= threshold) {
          return startTime + relaxedMillis;
        }
        return startTime + frequentMillis;
      })
      .build();
    Key key1 = new Key();
    ValueWithGauge value = cache.get(key1);
    cache.close();
  }

  /**
   * A good and fine adaption of refresh times (same as expiry) can save bandwidth and
   * system load, while still updating frequently enough in case it is needed.
   * The idea of delta expiry is to use the previous entry data to calculate a delta value
   * to determine how the expiry should be adjusted. In case of our example:
   * If the available seats drop from 50 to 25, we should already update more frequently since
   * the data will be in the critical range for uses soon. Only looking on the last data
   * value to adapt the expiry value would not be ideal.
   *
   * <p>The example is simple to illustrate the idea. A better solution could do a floating
   * calculation based on the time difference, data delta and tripping points.
   */
  @Test
  public void gaugeWithDeltaApproach() {
    int threshold = 10;
    long relaxedMillis = TimeUnit.MINUTES.toMillis(5);
    long frequentMillis = TimeUnit.SECONDS.toMillis(27);
    Cache<Key, ValueWithGauge> cache = new Cache2kBuilder<Key, ValueWithGauge>() {}
      .loader(k -> new ValueWithGauge())
      .keepDataAfterExpired(true)
      .refreshAhead(true)
      .expiryPolicy((key, value, startTime, currentEntry) -> {
        if (currentEntry == null || value.seatsAvailable == 0) {
          if (value.seatsAvailable == 0 || value.seatsAvailable >= threshold) {
            return startTime + relaxedMillis;
          }
          return startTime + frequentMillis;
        }
        int deltaSeats = currentEntry.getValue().seatsAvailable - value.seatsAvailable;
        if ((value.seatsAvailable - deltaSeats) >= threshold) {
          return startTime + relaxedMillis;
        }
        return startTime + frequentMillis;
      })
      .build();
    Key key1 = new Key();
    ValueWithGauge value = cache.get(key1);
    cache.close();
  }

}
