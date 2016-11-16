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

import domain.Identifier2;
import junit.framework.Assert;
import org.jsr107.tck.testutil.CacheTestSupport;
import org.jsr107.tck.testutil.ExcludeListExcluder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;

import javax.cache.Cache;
import javax.cache.configuration.MutableConfiguration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * <p>
 * Unit tests for Cache.
 * </p>
 * Testing
 * <pre>
 * V get(Object key);
 * Map<K, V> getAll(Collection<? extends K> keys);
 * </pre>
 * </p>
 * When it matters whether the cache is stored by reference or by value, see
 * {@link
 * StoreByValueTest} and
 * {@link StoreByReferenceTest}.
 *
 * @author Yannis Cosmadopoulos
 * @since 1.0
 */
public class GetTest extends CacheTestSupport<Long, String> {

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
  public void get_Closed() {
    cache.close();
    try {
      cache.get(null);
      fail("should have thrown an exception - cache closed");
    } catch (IllegalStateException e) {
      //good
    }
  }

  @Test
  public void get_NullKey() {
    try {
      assertNull(cache.get(null));
      fail("should have thrown an exception - null key not allowed");
    } catch (NullPointerException e) {
      //expected
    }
  }

  @Test
  public void get_NotExisting() {
    Long existingKey = System.currentTimeMillis();
    String existingValue = "value" + existingKey;
    cache.put(existingKey, existingValue);

    Long key1 = existingKey + 1;
    assertNull(cache.get(key1));
  }

  @Test
  public void get_Existing() {
    Long existingKey = System.currentTimeMillis();
    String existingValue = "value" + existingKey;
    cache.put(existingKey, existingValue);
    assertEquals(existingValue, cache.get(existingKey));
  }

  @Test
  public void get_Existing_NotSameKey() {
    Long existingKey = System.currentTimeMillis();
    String existingValue = "value" + existingKey;
    cache.put(existingKey, existingValue);
    assertEquals(existingValue, cache.get(new Long(existingKey)));
  }

  @Test
  public void getAll_Closed() {
    cache.close();
    try {
      cache.getAll(null);
      fail("should have thrown an exception - cache closed");
    } catch (IllegalStateException e) {
      //good
    }
  }

  @Test
  public void getAll_Null() {
    try {
      cache.getAll(null);
      fail("should have thrown an exception - null keys not allowed");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void getAll_NullKey() {
    HashSet<Long> keys = new HashSet<Long>();
    keys.add(1L);
    keys.add(null);
    keys.add(2L);
    try {
      cache.getAll(keys);
      fail("should have thrown an exception - null key in keys not allowed");
    } catch (NullPointerException e) {
      //expected
    }
  }

  @Test
  public void getAll() {
    ArrayList<Long> keysInCache = new ArrayList<Long>();
    keysInCache.add(1L);
    keysInCache.add(2L);
    for (Long k : keysInCache) {
      cache.put(k, "value" + k);
    }

    HashSet<Long> keysToGet = new HashSet<Long>();
    keysToGet.add(2L);
    keysToGet.add(3L);

    HashSet<Long> keysExpected = new HashSet<Long>();
    keysExpected.add(2L);

    Map<Long, String> map = cache.getAll(keysToGet);
    assertEquals("size", keysExpected.size(), map.size());
    for (Long key : keysExpected) {
      assertTrue(map.containsKey(key));
      assertEquals("key  : key=" + key, cache.get(key), map.get(key));
      assertEquals("value: key=" + key, "value" + key, map.get(key));
    }
  }

  /**
   * Identifier2 has transient fields that will not match. But equals is consulted
   * and will match. This test works because Identifier2 was correctly implemented
   * per the spec and transient fields are not used in equals or hashcode.
   */
  public void testGetUsesEqualityNotequalsequals() {

    Cache identifier2Cache = getCacheManager().createCache("identifierCache",newMutableConfiguration().setStoreByValue(false));

    Identifier2 one = new Identifier2("1");
    identifier2Cache.put(one, "something");
    Identifier2 one_ = new Identifier2("1");
    Assert.assertEquals(one, one_);
    Assert.assertEquals(one.hashCode(), one_.hashCode());
    assertEquals("something", identifier2Cache.get(one_));
  }

}
