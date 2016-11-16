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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for Cache.
 * <p>
 * Testing
 * <pre>
 * boolean remove(Object key);
 * boolean remove(Object key, V oldValue);
 * V getAndRemove(Object key);
 * void removeAll(Collection<? extends K> keys);
 * void removeAll();
 * </pre>
 * </p>
 * When it matters whether the cache is stored by reference or by value, see {@link StoreByValueTest} and
 * {@link StoreByReferenceTest}.
 *
 * @author Yannis Cosmadopoulos
 * @since 1.0
 */
public class RemoveTest extends CacheTestSupport<Long, String> {

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
  public void remove_1arg_Closed() {
    cache.close();
    try {
      cache.remove(null);
      fail("should have thrown an exception - cache closed");
    } catch (IllegalStateException e) {
      //good
    }
  }

  @Test
  public void remove_1arg_NullKey() throws Exception {
    try {
      assertFalse(cache.remove(null));
      fail("should have thrown an exception - null key not allowed");
    } catch (NullPointerException e) {
      //expected
    }
  }

  @Test
  public void remove_1arg_NotExistent() throws Exception {
    Long existingKey = System.currentTimeMillis();
    String existingValue = "value" + existingKey;
    cache.put(existingKey, existingValue);

    Long keyNotExisting = existingKey + 1;
    assertFalse(cache.remove(keyNotExisting));
    assertEquals(existingValue, cache.get(existingKey));
  }

  @Test
  public void remove_1arg_Existing() {
    Long key1 = System.currentTimeMillis();
    String value1 = "value" + key1;
    cache.put(key1, value1);

    Long key2 = key1 + 1;
    String value2 = "value" + key2;
    cache.put(key2, value2);

    assertTrue(cache.remove(key1));
    assertFalse(cache.containsKey(key1));
    assertNull(cache.get(key1));
    assertEquals(value2, cache.get(key2));
  }

  @Test
  public void remove_1arg_EqualButNotSameKey() {
    Long key1 = System.currentTimeMillis();
    String value1 = "value" + key1;
    cache.put(key1, value1);

    Long key2 = key1 + 1;
    String value2 = "value" + key2;
    cache.put(key2, value2);

    Long key3 = new Long(key1);
    assertNotSame(key1, key3);
    assertTrue(cache.remove(key3));
    assertFalse(cache.containsKey(key1));
    assertNull(cache.get(key1));
    assertFalse(cache.containsKey(key3));
    assertNull(cache.get(key3));
    assertEquals(value2, cache.get(key2));
  }

  @Test
  public void remove_2arg_Closed() {
    cache.close();
    try {
      cache.remove(null, null);
      fail("should have thrown an exception - cache closed");
    } catch (IllegalStateException e) {
      //good
    }
  }

  @Test
  public void remove_2arg_NullKey() {
    try {
      cache.remove(null, "");
      fail("should have thrown an exception - null key");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void remove_2arg_NullValue() {
    try {
      cache.remove(1L, null);
      fail("should have thrown an exception - null value");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void remove_2arg_NotThere() {
    Long key = System.currentTimeMillis();
    assertFalse(cache.remove(key, ""));
  }

  @Test
  public void remove_2arg_Existing_SameValue() {
    Long key = System.currentTimeMillis();
    String value = "value" + key;
    cache.put(key, value);
    assertTrue(cache.remove(key, value));
  }

  @Test
  public void remove_2arg_Existing_EqualValue() {
    Long key = System.currentTimeMillis();
    String value = "value" + key;
    cache.put(key, value);
    assertTrue(cache.remove(key, new String(value)));
  }

  @Test
  public void remove_2arg_Existing_EqualKey() {
    Long key = System.currentTimeMillis();
    String value = "value" + key;
    cache.put(key, value);
    assertTrue(cache.remove(new Long(key), value));
  }

  @Test
  public void remove_2arg_Existing_EqualKey_EqualValue() {
    Long key = System.currentTimeMillis();
    String value = "value" + key;
    cache.put(key, value);
    assertTrue(cache.remove(new Long(key), new String(value)));
  }

  @Test
  public void remove_2arg_Existing_Different() {
    Long key = System.currentTimeMillis();
    String value = "value" + key;
    cache.put(key, value);
    assertFalse(cache.remove(key, value + 1));
  }

  @Test
  public void getAndRemove_Closed() {
    cache.close();
    try {
      cache.getAndRemove(null);
      fail("should have thrown an exception - cache closed");
    } catch (IllegalStateException e) {
      //good
    }
  }

  @Test
  public void getAndRemove_NullKey() throws Exception {
    try {
      assertNull(cache.getAndRemove(null));
      fail("should have thrown an exception - null key not allowed");
    } catch (NullPointerException e) {
      //expected
    }
  }

  @Test
  public void getAndRemove_NotExistent() throws Exception {
    Long existingKey = System.currentTimeMillis();
    String existingValue = "value" + existingKey;
    cache.put(existingKey, existingValue);

    Long keyNotExisting = existingKey + 1;
    assertNull(cache.getAndRemove(keyNotExisting));
    assertEquals(existingValue, cache.get(existingKey));
  }

  @Test
  public void getAndRemove_Existing() {
    Long key1 = System.currentTimeMillis();
    String value1 = "value" + key1;
    cache.put(key1, value1);

    Long key2 = key1 + 1;
    String value2 = "value" + key2;
    cache.put(key2, value2);

    assertEquals(value1, cache.getAndRemove(key1));
    assertFalse(cache.containsKey(key1));
    assertNull(cache.get(key1));
    assertEquals(value2, cache.get(key2));
  }

  @Test
  public void getAndRemove_EqualButNotSameKey() {
    Long key1 = System.currentTimeMillis();
    String value1 = "value" + key1;
    cache.put(key1, value1);

    Long key2 = key1 + 1;
    String value2 = "value" + key2;
    cache.put(key2, value2);

    Long key3 = new Long(key1);
    assertNotSame(key3, key1);
    assertEquals(value1, cache.getAndRemove(key3));
    assertNull(cache.get(key1));
    assertEquals(value2, cache.get(key2));
  }

  @Test
  public void removeAll_1arg_Closed() {
    cache.close();
    try {
      cache.removeAll(null);
      fail("should have thrown an exception - cache closed");
    } catch (IllegalStateException e) {
      //good
    }
  }

  @Test
  public void removeAll_1arg_Null() {
    try {
      cache.removeAll(null);
      fail("should have thrown an exception - null keys not allowed");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void removeAll_1arg_NullKey() {
    HashSet<Long> keys = new HashSet<Long>();
    keys.add(null);

    try {
      cache.removeAll(keys);
      fail("should have thrown an exception - null key not allowed");
    } catch (NullPointerException e) {
      //expected
    }
  }

  @Test
  public void removeAll_1arg() {
    Map<Long, String> data = createLSData(3);
    cache.putAll(data);

    Iterator<Map.Entry<Long, String>> it = data.entrySet().iterator();
    it.next();
    Map.Entry removedEntry = it.next();
    it.remove();

    cache.removeAll(data.keySet());
    for (Long key : data.keySet()) {
      assertFalse(cache.containsKey(key));
    }
    assertEquals(removedEntry.getValue(), cache.get((Long) removedEntry.getKey()));
  }

  @Test
  public void removeAll_0arg_Closed() {
    cache.close();
    try {
      cache.removeAll();
      fail("should have thrown an exception - cache closed");
    } catch (IllegalStateException e) {
      //good
    }
  }

  @Test
  public void removeAll_0arg() {
    Map<Long, String> data = createLSData(3);
    cache.putAll(data);
    cache.removeAll();
    for (Long key : data.keySet()) {
      assertFalse(cache.containsKey(key));
    }
  }
}
