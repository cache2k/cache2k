package org.cache2k.core;

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
import org.cache2k.testing.category.FastTests;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test ConcurrentMap methods with cache.
 *
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class ConcurrentMapWithCacheNullValueTest {

  Cache<Integer, String> cache;
  ConcurrentMap<Integer, String> map;

  @Before
  public void setUp() {
    cache = Cache2kBuilder.of(Integer.class, String.class)
      .eternal(true).permitNullValues(true)
      .build();
    map = cache.asMap();
  }

  @After
  public void tearDown() {
    cache.close();
    map = null;
    cache = null;
  }

  @Test
  public void getOrDefault() {
    cache.put(1, null);
    cache.put(2, "abc");
    assertEquals("xy", map.getOrDefault(3, "xy"));
    assertEquals("abc", map.getOrDefault(2, "xy"));
    assertTrue(map.containsKey(1));
    assertEquals(null, map.getOrDefault(1, "xy"));
  }

}
