/*
 * #%L
 * cache2k core package
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
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.CacheSource;
import org.cache2k.junit.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 * Basic sanity checks and examples.
 *
 * @author Jens Wilke; created: 2013-12-17
 */
@Category(FastTests.class)
public class CacheTest {

  @Test
  public void testPeekAndPut() {
    Cache<String,String> c =
      Cache2kBuilder.newCache(String.class, String.class)
        .eternal(true)
        .build();
    String val = c.peek("something");
    assertNull(val);
    c.put("something", "hello");
    val = c.get("something");
    assertNotNull(val);
    c.close();
  }

  @Test
  public void testGetWithSource() {
    CacheSource<String,Integer> _lengthCountingSource = new CacheSource<String, Integer>() {
      @Override
      public Integer get(String o) throws Throwable {
        return o.length();
      }
    };
    Cache<String,Integer> c =
      Cache2kBuilder.newCache(String.class, Integer.class)
        .source(_lengthCountingSource)
        .eternal(true)
        .build();
    int v = c.get("hallo");
    assertEquals(5, v);
    v = c.get("long string");
    assertEquals(11, v);
    c.close();
  }

  @Test
  public void testGetEntry() {
    Cache<String,String> c =
      Cache2kBuilder.newCache(String.class, String.class)
        .eternal(true)
        .build();
    String val = c.peek("something");
    assertNull(val);
    c.put("something", "hello");
    CacheEntry<String, String> e = c.getEntry("something");
    assertNotNull(e);
    assertEquals("hello", e.getValue());
    c.close();
  }

  @Test
  public void testContains() {
    Cache<String,String> c =
      Cache2kBuilder.newCache(String.class, String.class)
        .eternal(true)
        .build();
    String val = c.peek("something");
    assertNull(val);
    c.put("something", "hello");
    assertTrue(c.contains("something"));
    assertFalse(c.contains("dsaf"));
    c.close();
  }

}
