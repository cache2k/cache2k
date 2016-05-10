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
import org.cache2k.customization.ExpiryCalculator;
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

  /**
   * The configuration setting serves as a time limit.
   */
  @Test
  public void expireAfterWrite_policy_limit() {
    TimingHandler h = TimingHandler.of(
      Cache2kBuilder.forUnknownTypes()
        .expiryCalculator(new ExpiryCalculator() {
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
   * Maximum expiry is also limited when sharp expiry requested.
   */
  @Test
  public void expireAfterWrite_policy_limit_sharp() {
    TimingHandler h = TimingHandler.of(
      Cache2kBuilder.forUnknownTypes()
        .expiryCalculator(new ExpiryCalculator() {
          @Override
          public long calculateExpiryTime(Object key, Object value, long loadTime, CacheEntry oldEntry) {
            return - (loadTime + TimeUnit.MINUTES.toMillis(99));
          }
        })
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .toConfiguration()
    );
    Entry e = new Entry();
    long t = h.calculateNextRefreshTime(e, null, POINT_IN_TIME);
    assertNotEquals(Long.MAX_VALUE, t);
    assertEquals(-POINT_IN_TIME - TimeUnit.MINUTES.toMillis(5), t);
  }

}
