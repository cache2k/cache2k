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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for Cache.
 * <p>
 * Testing
 * <pre>
 * V get(Object key);
 * </pre>
 * </p>
 * When it matters whether the cache is stored by reference or by value, see {@link StoreByValueTest} and
 * {@link StoreByReferenceTest}.
 *
 * @author Yannis Cosmadopoulos
 * @since 1.0
 */
public class ReplaceTest extends CacheTestSupport<Long, String> {

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
  public void replace_3arg_Closed() {
    cache.close();
    try {
      cache.replace(null, null, null);
      fail("should have thrown an exception - cache closed");
    } catch (IllegalStateException e) {
      //good
    }
  }

  @Test
  public void replace_3arg_NullKey() {
    try {
      assertFalse(cache.replace(null, "1", "2"));
      fail("should have thrown an exception - null key not allowed");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void replace_3arg_NullValue1() {
    try {
      assertFalse(cache.replace(1L, null, "2"));
      fail("should have thrown an exception - null value not allowed");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void replace_3arg_NullValue2() {
    try {
      assertFalse(cache.replace(1L, "1", null));
      fail("should have thrown an exception - null value not allowed");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void replace_3arg_Missing() {
    Long key = System.currentTimeMillis();
    assertFalse(cache.replace(key, "1", "2"));
    assertFalse(cache.containsKey(key));
  }

  @Test
  public void replace_3arg() throws Exception {
    Long key = System.currentTimeMillis();
    String value = "value" + key;
    cache.put(key, value);
    String nextValue = "value" + key + 1;
    assertTrue(cache.replace(key, value, nextValue));
    assertEquals(nextValue, cache.get(key));
  }

  @Test
  public void replace_3arg_Equal() {
    Long key = System.currentTimeMillis();
    String value = "value" + key;
    cache.put(key, value);
    String nextValue = "value" + key + 1;
    assertTrue(cache.replace(new Long(key), new String(value), new String(nextValue)));
    assertEquals(nextValue, cache.get(key));
  }

  @Test
  public void replace_3arg_Different() {
    Long key = System.currentTimeMillis();
    String value = "value" + key;
    cache.put(key, value);
    String nextValue = "valueN" + key;
    String desiredOldValue = "valueB" + key;
    assertFalse(cache.replace(key, desiredOldValue, nextValue));
    assertEquals(value, cache.get(key));
  }

  @Test
  public void replace_2arg_Closed() {
    cache.close();
    try {
      cache.replace(null, null);
      fail("should have thrown an exception - cache closed");
    } catch (IllegalStateException e) {
      //good
    }
  }

  @Test
  public void replace_2arg_NullKey() {
    try {
      assertFalse(cache.replace(null, ""));
      fail("should have thrown an exception - null key not allowed");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void replace_2arg_NullValue() {
    try {
      assertFalse(cache.replace(1L, null));
      fail("should have thrown an exception - null value not allowed");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void replace_2arg_Missing() throws Exception {
    Long key = System.currentTimeMillis();
    assertFalse(cache.replace(key, ""));
    assertFalse(cache.containsKey(key));
  }

  @Test
  public void replace_2arg() {
    Long key = System.currentTimeMillis();
    String value = "value" + key;
    cache.put(key, value);
    String nextValue = "valueA" + key;
    assertTrue(cache.replace(key, nextValue));
    assertEquals(nextValue, cache.get(key));
  }

  @Test
  public void getAndReplace_Closed() {
    cache.close();
    try {
      cache.getAndReplace(null, null);
      fail("should have thrown an exception - cache closed");
    } catch (IllegalStateException e) {
      //good
    }
  }

  @Test
  public void getAndReplace_NullKey() {
    try {
      assertNull(cache.getAndReplace(null, ""));
      fail("should have thrown an exception - null key not allowed");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void getAndReplace_NullValue() {
    try {
      assertNull(cache.getAndReplace(1L, null));
      fail("should have thrown an exception - null value not allowed");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void getAndReplace_Missing() {
    Long key = System.currentTimeMillis();
    assertNull(cache.getAndReplace(key, ""));
    assertFalse(cache.containsKey(key));
  }

  @Test
  public void getAndReplace() {
    Long key = System.currentTimeMillis();
    String value = "value" + key;
    cache.put(key, value);
    String nextValue = "valueB" + key;
    assertEquals(value, cache.getAndReplace(key, nextValue));
    assertEquals(nextValue, cache.get(key));
  }
}
