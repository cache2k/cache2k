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
import org.cache2k.CacheEntry;
import org.cache2k.io.CacheLoader;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cache2k.Cache2kBuilder.of;

/**
 * Basic sanity checks and examples.
 *
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class CacheTest {

  @Test
  public void testPeekAndPut() {
    Cache<String, String> c =
      of(String.class, String.class)
        .eternal(true)
        .build();
    String val = c.peek("something");
    assertThat(val).isNull();
    c.put("something", "hello");
    val = c.get("something");
    assertThat(val).isNotNull();
    c.close();
  }

  @Test
  public void testGetWithLoader() {
    CacheLoader<String, Integer> lengthCountingSource = String::length;
    Cache<String, Integer> c =
      of(String.class, Integer.class)
        .loader(lengthCountingSource)
        .eternal(true)
        .build();
    int v = c.get("hallo");
    assertThat(v).isEqualTo(5);
    v = c.get("long string");
    assertThat(v).isEqualTo(11);
    c.close();
  }

  @Test
  public void testGetEntry() {
    Cache<String, String> c =
      of(String.class, String.class)
        .eternal(true)
        .build();
    String val = c.peek("something");
    assertThat(val).isNull();
    c.put("something", "hello");
    CacheEntry<String, String> e = c.getEntry("something");
    assertThat(e).isNotNull();
    assertThat(e.getValue()).isEqualTo("hello");
    c.close();
  }

  @Test
  public void testContains() {
    Cache<String, String> c =
      of(String.class, String.class)
        .eternal(true)
        .build();
    String val = c.peek("something");
    assertThat(val).isNull();
    c.put("something", "hello");
    assertThat(c.containsKey("something")).isTrue();
    assertThat(c.containsKey("dsaf")).isFalse();
    c.close();
  }

  @Test
  public void testEntryToString() {
    Cache<Integer, Integer> c =
      of(Integer.class, Integer.class)
        .eternal(true)
        .build();
    c.put(1, 2);
    assertThat(c.getEntry(1).toString()).isEqualTo("CacheEntry(key=1, valueHashCode=2)");
  }

}
