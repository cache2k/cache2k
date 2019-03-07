package org.cache2k.test.core;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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
import org.cache2k.LongCache;
import org.cache2k.core.InternalCache;
import org.cache2k.core.InternalCacheInfo;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.testing.category.FastTests;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Test basic cache operations on a shared cache in a simple configuration.
 * The cache may hold 1000 entries and has no expiry.
 */
@Category(FastTests.class)
public class BasicLongCacheOperationsTest {

  final static long KEY = 1;
  final static long OTHER_KEY = 2;
  final static Integer VALUE = 1;
  final static Integer OTHER_VALUE = 2;

  static LongCache<Integer> staticCache;

  @BeforeClass
  public static void setUp() {
    staticCache = Cache2kBuilder
            .of(Long.class, Integer.class)
            .name(BasicLongCacheOperationsTest.class)
            .retryInterval(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
            .eternal(true)
            .entryCapacity(1000)
            .permitNullValues(true)
            .keepDataAfterExpired(true)
            .buildForLongKey();
  }

  /**
   * Used cache is a class field. We may subclass this class and run the tests with a different
   * configuration.
   */
  LongCache<Integer> cache;

  Statistics statistics = new Statistics();

  public Statistics statistics() {
    statistics.sample(cache);
    return statistics;
  }

  /**
   * Use for assertions on absolute values.
   */
  public InternalCacheInfo info() {
    return cache.requestInterface(InternalCache.class).getLatestInfo();
  }

  /**
   * Number of entries in the cache.
   */
  public long size() {
    return info().getSize();
  }

  @Before
  public void initCache() {
    cache = staticCache;
    statistics().reset();
  }

  @After
  public void cleanupCache() {
    assertSame("Tests are not allowed to create private caches", staticCache, cache);
    ((InternalCache) cache).checkIntegrity();
    cache.clear();
  }

  @AfterClass
  public static void tearDown() {
    staticCache.close();
  }

  /*
   * initial: Tests on the initial state of the cache.
   */

  @Test
  public void initial_Iterator() {
    assertFalse(cache.entries().iterator().hasNext());
  }

  @Test
  public void initial_Peek() {
    assertNull(cache.peek(KEY));
    assertNull(cache.peek(OTHER_KEY));
    assertEquals(0, size());
  }

  @Test
  public void initial_Contains() {
    assertFalse(cache.containsKey(KEY));
    assertFalse(cache.containsKey(OTHER_KEY));
    assertEquals(0, size());
  }

  /**
   * Yields "org.cache2k.PropagatedCacheException: (expiry=none) org.cache2k.impl.CacheUsageException: source not set".
   * This is intentional, but maybe we change it in the future. At least check that we are consistent for now.
   */
  @Test
  public void initial_Get() {
    Object obj = cache.get(KEY);
    assertNull(obj);
  }

  /*
   * put
   */

  @Test
  public void put() {
    cache.put(KEY, VALUE);
    statistics()
      .getCount.expect(0)
      .missCount.expect(0)
      .putCount.expect(1)
      .expectAllZero();
    assertTrue(cache.containsKey(KEY));
    assertEquals(VALUE, cache.get(KEY));
    assertEquals(VALUE, cache.peek(KEY));
  }

  @Test
  public void putTwice() {
    cache.put(KEY, VALUE);
    cache.put(KEY, OTHER_VALUE);
    statistics()
      .getCount.expect(0)
      .missCount.expect(0)
      .putCount.expect(2)
      .expectAllZero();
    assertTrue(cache.containsKey(KEY));
    assertEquals(OTHER_VALUE, cache.get(KEY));
    assertEquals(OTHER_VALUE, cache.peek(KEY));
  }

  @Test
  public void put_Null() {
    cache.put(KEY, null);
    assertTrue(cache.containsKey(KEY));
    assertNull(cache.peek(KEY));
    assertNull(cache.get(KEY));
  }

  @Test(expected = NullPointerException.class)
  public void put_NullKey() {
    cache.put(null, VALUE);
  }

  /*
   * contains
   */

  @Test
  public void contains() {
    assertFalse(cache.containsKey(KEY));
    cache.put(KEY, VALUE);
    assertTrue(cache.containsKey(KEY));
  }

  @Test
  public void contains_Null() {
    assertFalse(cache.containsKey(KEY));
    cache.put(KEY, null);
    assertTrue(cache.containsKey(KEY));
  }

  /*
   * remove(k)
   */

  @Test
  public void remove_NotExisting() {
    statistics().reset();
    cache.remove(KEY);
    statistics().expectAllZero();
    assertFalse(cache.containsKey(KEY));
  }

  @Test
  public void remove() {
    cache.put(KEY, VALUE);
    assertTrue(cache.containsKey(KEY));
    statistics().reset();
    cache.remove(KEY);
    statistics().removeCount.expect(1).expectAllZero();
    assertFalse(cache.containsKey(KEY));
  }

  @Test
  public void remove_Null() {
    cache.put(KEY, null);
    cache.remove(KEY);
    assertFalse(cache.containsKey(KEY));
  }

  @Test(expected = NullPointerException.class)
  public void remove_NullKey() {
    cache.remove(null);
  }

  /*
   * peek
   */

  @Test
  public void peek_Miss() {
    assertNull(cache.peek(KEY));
    statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .expectAllZero();
  }

  @Test
  public void peek_Hit() {
    cache.put(KEY, VALUE);
    statistics()
      .putCount.expect(1)
      .expectAllZero();
    assertNotNull(cache.peek(KEY));
    statistics()
      .getCount.expect(1)
      .missCount.expect(0)
      .expectAllZero();
  }

  @Test
  public void peek_NotFresh() {
    cache.put(KEY, VALUE);
    statistics()
      .putCount.expect(1)
      .expectAllZero();
    cache.expireAt(KEY, ExpiryTimeValues.NOW);
    assertNull(cache.peek(KEY));
    statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .expectAllZero();
  }

}
