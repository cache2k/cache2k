package org.cache2k.core.timing;

/*-
 * #%L
 * cache2k core implementation
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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.core.HeapCache;
import org.cache2k.testing.category.FastTests;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class DefaultResiliencePolicyTest {

  Cache<Integer, Integer> cache;

  @After
  public void tearDown() {
    if (cache != null) {
      cache.close();
    }
  }

  Timing extractHandler() {
    return cache.requestInterface(HeapCache.class).getTiming();
  }

  /**
   * Values do not expire, exceptions are not suppressed and an immediately
   * retry is done.
   */
  @Test
  public void eternal() {
    cache = new Cache2kBuilder<Integer, Integer>() { }
      .eternal(true)
      /* ... set loader ... */
      .build();
    assertTrue(extractHandler() instanceof TimeAgnosticTiming.EternalImmediate);
  }

  @Test
  public void expiry0() {
    cache = new Cache2kBuilder<Integer, Integer>() { }
      .expireAfterWrite(0, TimeUnit.MINUTES)
      /* ... set loader ... */
      .build();
    assertTrue(extractHandler() instanceof TimeAgnosticTiming.Immediate);
  }

}
