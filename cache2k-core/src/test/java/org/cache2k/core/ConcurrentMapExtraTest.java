package org.cache2k.core;

/*
 * #%L
 * cache2k core implementation
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
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Check special cases that the cache does in contrast to a
 * ConcurrentHashMap.
 *
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class ConcurrentMapExtraTest {

  @Test
  public void checkForNull() {
    Cache<Integer, String> cache = Cache2kBuilder.of(Integer.class, String.class)
      .eternal(true)
      .permitNullValues(true)
      .build();
    ConcurrentMap<Integer, String> map = cache.asMap();
    assertFalse(map.containsValue(null));
    cache.put(123, "hello");
    assertFalse(map.containsValue(null));
    cache.put(4711, null);
    assertTrue(map.containsValue(null));
    cache.close();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void entrySetValueUnsupported() {
    Cache<Integer, String> cache = Cache2kBuilder.of(Integer.class, String.class)
      .eternal(true)
      .permitNullValues(true)
      .build();
    try {
      ConcurrentMap<Integer, String> map = cache.asMap();
      cache.put(123, "hello");
      map.entrySet().iterator().next().setValue("boo");
    } finally {
      cache.close();
    }
  }

}
