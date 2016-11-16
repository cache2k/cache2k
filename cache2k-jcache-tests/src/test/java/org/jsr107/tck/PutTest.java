/**
 *  Copyright 2011-2013 Terracotta, Inc.
 *  Copyright 2011-2013 Oracle, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jsr107.tck;

import org.jsr107.tck.testutil.CacheTestSupport;
import org.jsr107.tck.testutil.ExcludeListExcluder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;

import javax.cache.configuration.MutableConfiguration;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * <p>
 * Unit tests for Cache.
 * </p>
 * Testing
 * <pre>
 * void put(K key, V value);
 * V getAndPut(K key, V value);
 * boolean putIfAbsent(K key, V value);
 * void putAll(java.util.Map<? extends K, ? extends V> map);
 * </pre>
 * <p>
 * When it matters whether the cache is stored by reference or by value, see {@link StoreByValueTest} and
 * {@link StoreByReferenceTest}.
 * </p>
 * @author Yannis Cosmadopoulos
 * @since 1.0
 */
public class PutTest extends CacheTestSupport<Long, String> {

  /**
   * Rule used to exclude tests
   */
  @Rule
  public MethodRule rule = new ExcludeListExcluder(this.getClass());


  @Before
  public void moreSetUp() {
    cache = getCacheManager().getCache(getTestCacheName(), Long.class, String.class);
  }

  @Override
  protected MutableConfiguration<Long, String> newMutableConfiguration() {
    return new MutableConfiguration<Long, String>().setTypes(Long.class, String.class);
  }

  @Test
  public void put_Closed() {
    cache.close();
    try {
      cache.put(null, null);
      fail("should have thrown an exception - cache closed");
    } catch (IllegalStateException e) {
      //good
    }
  }

  @Test
  public void put_NullKey() throws Exception {
    try {
      cache.put(null, "");
      fail("should have thrown an exception - null key not allowed");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void put_NullValue() throws Exception {
    try {
      cache.put(1L, null);
      fail("should have thrown an exception - null value not allowed");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void put_Existing_NotSameKey() throws Exception {
    Long key1 = System.currentTimeMillis();
    String value1 = "value" + key1;
    cache.put(key1, value1);
    Long key2 = new Long(key1);
    String value2 = "value" + key2;
    cache.put(key2, value2);
    assertEquals(value2, cache.get(key2));
  }

  @Test
  public void put_Existing_DifferentKey() throws Exception {
    Long key1 = System.currentTimeMillis();
    String value1 = "value" + key1;
    cache.put(key1, value1);
    Long key2 = key1 + 1;
    String value2 = "value" + key2;
    cache.put(key2, value2);
    assertEquals(value2, cache.get(key2));
  }

  @Test
  public void getAndPut_Closed() {
    cache.close();
    try {
      cache.getAndPut(null, null);
      fail("should have thrown an exception - cache closed");
    } catch (IllegalStateException e) {
      //good
    }
  }

  @Test
  public void getAndPut_NullKey() throws Exception {
    try {
      cache.getAndPut(null, "");
      fail("should have thrown an exception - null key not allowed");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void getAndPut_NullValue() throws Exception {
    try {
      cache.getAndPut(1L, null);
      fail("should have thrown an exception - null value not allowed");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void getAndPut_NotThere() {
    Long existingKey = System.currentTimeMillis();
    String existingValue = "value" + existingKey;
    assertNull(cache.getAndPut(existingKey, existingValue));
    assertEquals(existingValue, cache.get(existingKey));
  }

  @Test
  public void getAndPut_Existing() throws Exception {
    Long existingKey = System.currentTimeMillis();
    String value1 = "value1";
    cache.getAndPut(existingKey, value1);
    String value2 = "value2";
    assertEquals(value1, cache.getAndPut(existingKey, value2));
    assertEquals(value2, cache.get(existingKey));
  }

  @Test
  public void getAndPut_Existing_NonSameKey() throws Exception {
    Long key1 = System.currentTimeMillis();
    String value1 = "value1";
    assertNull(cache.getAndPut(key1, value1));
    Long key2 = new Long(key1);
    String value2 = "value2";
    assertEquals(value1, cache.getAndPut(key2, value2));
    assertEquals(value2, cache.get(key1));
    assertEquals(value2, cache.get(key2));
  }

  @Test
  public void putIfAbsent_Closed() {
    cache.close();
    try {
      cache.putIfAbsent(null, null);
      fail("should have thrown an exception - cache closed");
    } catch (IllegalStateException e) {
      //good
    }
  }

  @Test
  public void putIfAbsent_NullKey() throws Exception {
    try {
      assertFalse(cache.putIfAbsent(null, ""));
      fail("should have thrown an exception - null key not allowed");
    } catch (NullPointerException e) {
      //expected
    }
  }

  @Test
  public void putIfAbsent_NullValue() {
    try {
      cache.putIfAbsent(1L, null);
      fail("should have thrown an exception - null value not allowed");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void putIfAbsent_Missing() {
    Long key = System.currentTimeMillis();
    String value = "value" + key;
    assertTrue(cache.putIfAbsent(key, value));
    assertEquals(value, cache.get(key));
  }

  @Test
  public void putIfAbsent_Same() {
    Long key = System.currentTimeMillis();
    String value = "valueA" + key;
    String oldValue = "valueB" + key;
    cache.put(key, oldValue);
    assertFalse(cache.putIfAbsent(key, value));
    assertEquals(oldValue, cache.get(key));
  }

  @Test
  public void putAll_Closed() {
    cache.close();
    try {
      cache.putAll(null);
      fail("should have thrown an exception - cache closed");
    } catch (IllegalStateException e) {
      //good
    }
  }

  @Test
  public void putAll_Null() {
    try {
      cache.putAll(null);
      fail("should have thrown an exception - null map not allowed");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void putAll_NullKey() {
    Map<Long, String> data = createLSData(3);
    // note: using LinkedHashMap, we have made an effort to ensure the null
    // be added after other "good" values.
    data.put(null, "");
    try {
      cache.putAll(data);
      fail("should have thrown an exception - null key not allowed");
    } catch (NullPointerException e) {
      //expected
    }
    for (Map.Entry<Long, String> entry : data.entrySet()) {
      if (entry.getKey() != null) {
        assertNull(cache.get(entry.getKey()));
      }
    }
  }

  @Test
  public void putAll_NullValue() {
    Map<Long, String> data = createLSData(3);
    // note: using LinkedHashMap, we have made an effort to ensure the null
    // be added after other "good" values.
    data.put(System.currentTimeMillis(), null);
    try {
      cache.putAll(data);
      fail("should have thrown an exception - null value not allowed");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void putAll() {
    Map<Long, String> data = createLSData(3);
    cache.putAll(data);
    for (Map.Entry<Long, String> entry : data.entrySet()) {
      assertEquals(entry.getValue(), cache.get(entry.getKey()));
    }
  }
}
