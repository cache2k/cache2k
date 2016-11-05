package org.cache2k.core;

/*
 * #%L
 * cache2k core
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.expiry.Expiry;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.junit.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@SuppressWarnings("unchecked")
@Category(FastTests.class)
public class TimingHandlerTest {

  final long POINT_IN_TIME = 10000000;

  @Test
  public void eternalSpecified() {
    TimingHandler h = TimingHandler.of(
      Cache2kBuilder.forUnknownTypes()
        .eternal(true)
        .toConfiguration()
    );
    assertEquals(TimingHandler.ETERNAL_IMMEDIATE.getClass(), h.getClass());
  }

  @Test
  public void eternalNotSpecified() {
    TimingHandler h = TimingHandler.of(
      Cache2kBuilder.forUnknownTypes()
        .toConfiguration()
    );
    assertEquals(TimingHandler.ETERNAL_IMMEDIATE.getClass(), h.getClass());
  }

  /**
   * Sharp is honored in the calculation phase.
   */
  @Test
  public void policy_sharp() {
    TimingHandler h = TimingHandler.of(
      Cache2kBuilder.forUnknownTypes()
        .expiryPolicy(new ExpiryPolicy() {
          @Override
          public long calculateExpiryTime(Object key, Object value, long loadTime, CacheEntry oldEntry) {
            return loadTime + 1;
          }
        })
        .sharpExpiry(true)
        .toConfiguration()
    );
    Entry e = new Entry();
    long t = h.calculateNextRefreshTime(e, null, POINT_IN_TIME);
    assertEquals(-POINT_IN_TIME - 1, t);
  }

  /**
   * The configuration setting serves as a time limit.
   */
  @Test
  public void expireAfterWrite_policy_limit() {
    TimingHandler h = TimingHandler.of(
      Cache2kBuilder.forUnknownTypes()
        .expiryPolicy(new ExpiryPolicy() {
          @Override
          public long calculateExpiryTime(Object key, Object value, long loadTime, CacheEntry oldEntry) {
            return ETERNAL;
          }
        })
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .toConfiguration()
    );
    Entry e = new Entry();
    long t = h.calculateNextRefreshTime(e, null, POINT_IN_TIME);
    assertNotEquals(Long.MAX_VALUE, t);
    assertEquals(POINT_IN_TIME + TimeUnit.MINUTES.toMillis(5), t);
    t = h.calculateNextRefreshTime(e, null, POINT_IN_TIME);
    assertEquals(POINT_IN_TIME + TimeUnit.MINUTES.toMillis(5), t);
  }

  /**
   * Maximum expiry is limited when sharp expiry requested.
   */
  @Test
  public void expireAfterWrite_policy_limit_sharp() {
    long _DURATION = 1000000;
    final long _SHARP_POINT_IN_TIME = POINT_IN_TIME + 5000000;
    TimingHandler h = TimingHandler.of(
      Cache2kBuilder.forUnknownTypes()
        .expiryPolicy(new ExpiryPolicy() {
          @Override
          public long calculateExpiryTime(Object key, Object value, long loadTime, CacheEntry oldEntry) {
            return -_SHARP_POINT_IN_TIME;
          }
        })
        .expireAfterWrite(_DURATION, TimeUnit.MILLISECONDS)
        .toConfiguration()
    );
    Entry e = new Entry();
    long t = h.calculateNextRefreshTime(e, null, POINT_IN_TIME);
    assertNotEquals(Long.MAX_VALUE, t);
    assertEquals("max expiry, but not sharp", POINT_IN_TIME + _DURATION , t);
    long _later = POINT_IN_TIME + _DURATION;
    t = h.calculateNextRefreshTime(e, null, _later);
    assertEquals(_later + _DURATION, t);
    _later = _SHARP_POINT_IN_TIME - _DURATION / 2;
    t = h.calculateNextRefreshTime(e, null, _later);
    assertEquals("requested expiry via duration too close", -_SHARP_POINT_IN_TIME, t);
    _later = _SHARP_POINT_IN_TIME - _DURATION - 1;
    t = h.calculateNextRefreshTime(e, null, _later);
    if (HeapCache.TUNABLE.sharpExpirySafetyGapMillis < _DURATION) {
      assertEquals("keep gap to point in time ", _SHARP_POINT_IN_TIME - HeapCache.TUNABLE.sharpExpirySafetyGapMillis, t);
    }
  }

  /**
   * Maximum expiry is limited when sharp expiry requested.
   * Corner case if expiry will happen close to requested point in time.
   */
  @Test
  public void expireAfterWrite_policy_limit_sharp_close() {
    long _DURATION = 100;
    final long _SHARP_POINT_IN_TIME = POINT_IN_TIME + 5000;
    TimingHandler h = TimingHandler.of(
      Cache2kBuilder.forUnknownTypes()
        .expiryPolicy(new ExpiryPolicy() {
          @Override
          public long calculateExpiryTime(Object key, Object value, long loadTime, CacheEntry oldEntry) {
            return -_SHARP_POINT_IN_TIME;
          }
        })
        .expireAfterWrite(_DURATION, TimeUnit.MILLISECONDS)
        .toConfiguration()
    );
    Entry e = new Entry();
    long  _later = _SHARP_POINT_IN_TIME - _DURATION - 1;
    long t = h.calculateNextRefreshTime(e, null, _later);
    assertTrue("expect gap bigger then duration", HeapCache.TUNABLE.sharpExpirySafetyGapMillis > _DURATION);
    assertNotEquals(_SHARP_POINT_IN_TIME - HeapCache.TUNABLE.sharpExpirySafetyGapMillis, t);
    assertTrue(Math.abs(t) > POINT_IN_TIME);
    assertTrue(Math.abs(t) < _SHARP_POINT_IN_TIME);
  }

}
