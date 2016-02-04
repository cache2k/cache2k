package org.cache2k.core.test;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import org.cache2k.Cache;
import org.cache2k.CacheBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.CacheException;
import org.cache2k.PropagatedCacheException;
import org.cache2k.impl.ExceptionWrapper;
import org.cache2k.impl.InternalCache;
import org.cache2k.junit.FastTests;
import org.junit.*;
import org.junit.experimental.categories.Category;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.*;

import static org.cache2k.core.test.StaticUtil.*;

/**
 * Test basic cache operations on a shared cache in a simple configuration.
 * The cache may hold 1000 entries and has no expiry.
 */
@Category(FastTests.class)
public class BasicCacheOperationsTest {

  @SuppressWarnings("ThrowableInstanceNeverThrown")
  final static Exception OUCH = new Exception("ouch");
  final static Integer KEY = 1;
  final static Integer OTHER_KEY = 2;
  final static Integer VALUE = 1;
  final static Integer OTHER_VALUE = 2;

  static Cache<Integer, Integer> staticCache;

  @BeforeClass
  public static void setUp() {
    staticCache = CacheBuilder
            .newCache(Integer.class, Integer.class)
            .name(BasicCacheOperationsTest.class)
            .eternal(true)
            .entryCapacity(1000)
            .build();
  }

  /**
   * Used cache is a class field. We may subclass this class and run the tests with a different
   * configuration.
   */
  Cache<Integer, Integer> cache;

  final Statistics statistics = new Statistics();

  public Statistics statistics() {
    statistics.sample(cache);
    return statistics;
  }

  @Before
  public void initCache() {
    cache = staticCache;
    statistics().reset();
  }

  @After
  public void cleanupCache() {
    assertTrue("Tests are not allowed to create private caches", staticCache == cache);
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
    assertFalse(cache.iterator().hasNext());
  }

  @Test
  public void initial_Peek() {
    assertNull(cache.peek(KEY));
    assertNull(cache.peek(OTHER_KEY));
  }

  @Test
  public void initial_Contains() {
    assertFalse(cache.contains(KEY));
    assertFalse(cache.contains(OTHER_KEY));
  }

  /**
   * Yields "org.cache2k.PropagatedCacheException: (expiry=none) org.cache2k.impl.CacheUsageExcpetion: source not set".
   * This is intentional, but maybe we change it in the future. At least check that we are consistent for now.
   */
  @Test(expected = CacheException.class)
  public void initial_Get() {
    cache.get(KEY);
  }

  /*
   * put
   */

  @Test
  public void put() {
    cache.put(KEY, VALUE);
    assertTrue(cache.contains(KEY));
    assertEquals(VALUE, cache.get(KEY));
    assertEquals(VALUE, cache.peek(KEY));
  }

  @Test
  public void put_Null() {
    cache.put(KEY, null);
    assertTrue(cache.contains(KEY));
    assertEquals(null, cache.peek(KEY));
    assertEquals(null, cache.get(KEY));
  }

  @Test(expected = NullPointerException.class)
  public void put_NullKey() {
    cache.put(null, VALUE);
  }

  /*
   * putAll
   */
  @Test
  public void putAll() {
    cache.putAll(Collections.<Integer, Integer>emptyMap());
    Map<Integer, Integer> map = new HashMap<Integer, Integer>();
    map.put(KEY, VALUE);
    map.put(OTHER_KEY, null);
    cache.putAll(map);
    assertTrue(cache.contains(KEY));
    assertTrue(cache.contains(OTHER_KEY));
    assertNull(cache.peek(OTHER_KEY));
    assertEquals(VALUE, cache.peek(KEY));
  }

  /*
   * peek
   */
  @Test
  public void peek_Miss() {
    assertNull(cache.peek(KEY));
    statistics()
      .readCount.expect(1)
      .missCount.expect(1)
      .expectAllZero();
  }

  /*
   * contains
   */
  @Test
  public void contains() {
    assertFalse(cache.contains(KEY));
    cache.put(KEY, VALUE);
    assertTrue(cache.contains(KEY));
  }

  @Test
  public void contains_Null() {
    assertFalse(cache.contains(KEY));
    cache.put(KEY, null);
    assertTrue(cache.contains(KEY));
  }

  /*
   * putIfAbsent()
   */
  @Test
  public void putIfAbsent() {
    cache.putIfAbsent(KEY, VALUE);
    assertTrue(cache.contains(KEY));
    assertEquals(KEY, cache.peek(KEY));
    cache.putIfAbsent(KEY, OTHER_VALUE);
    assertTrue(cache.contains(KEY));
    assertEquals(VALUE, cache.peek(KEY));
  }

  /*
   * putIfAbsent()
   */
  @Test
  public void putIfAbsent_Null() {
    cache.putIfAbsent(KEY, null);
    assertTrue(cache.contains(KEY));
    assertNull(cache.peek(KEY));
  }

  /*
   * peekAndPut
   */
  @Test
  public void peekAndPut() {
    Integer v = cache.peekAndPut(KEY, VALUE);
    assertNull(v);
    v = cache.peekAndPut(KEY, VALUE);
    assertNotNull(v);
    assertEquals(VALUE, v);
  }

  @Test(expected = NullPointerException.class)
  public void peekAndPut_NullKey() {
    cache.peekAndPut(null, VALUE);
  }

  @Test
  public void peekAndPut_Null() {
    Integer v = cache.peekAndPut(KEY, null);
    assertNull(v);
    assertTrue(cache.contains(KEY));
    v = cache.peekAndPut(KEY, VALUE);
    assertNull(v);
    assertTrue(cache.contains(KEY));
    v = cache.peekAndPut(KEY, null);
    assertNotNull(v);
    assertEquals(VALUE, v);
    v = cache.peekAndPut(KEY, null);
    assertNull(v);
  }

  @Test(expected = PropagatedCacheException.class)
  public void peekAndPut_Exception() {
    ((Cache) cache).put(KEY, new ExceptionWrapper(OUCH));
    cache.peekAndPut(KEY, VALUE);
  }

  /*
   * peekAndRemove
   */

  @Test
  public void peekAndRemove() {
    Integer v = cache.peekAndRemove(KEY);
    assertNull(v);
    assertFalse(cache.contains(KEY));
    cache.put(KEY, VALUE);
    assertTrue(cache.contains(KEY));
    v = cache.peekAndRemove(KEY);
    assertNotNull(v);
    assertFalse(cache.contains(KEY));
  }

  @Test
  public void peekAndRemove_Null() {
    cache.put(KEY, null);
    assertTrue(cache.contains(KEY));
    Integer v = cache.peekAndRemove(KEY);
    assertNull(v);
    assertFalse(cache.contains(KEY));
  }

  @Test(expected = NullPointerException.class)
  public void peekAndRemove_NullKey() {
    cache.peekAndRemove(null);
  }

  @Test(expected = PropagatedCacheException.class)
  public void peekAndRemove_Exception() {
    ((Cache) cache).put(KEY, new ExceptionWrapper(OUCH));
    cache.peekAndRemove(KEY);
  }

  /*
   * peekAndReplace
   */

  @Test
  public void peekAndReplace() {
    Integer v = cache.peekAndReplace(KEY, VALUE);
    assertNull(v);
    assertFalse(cache.contains(KEY));
    cache.put(KEY, VALUE);
    v = cache.peekAndReplace(KEY, OTHER_VALUE);
    assertNotNull(v);
    assertTrue(cache.contains(KEY));
    assertEquals(VALUE, v);
    assertEquals(OTHER_VALUE, cache.peek(KEY));
  }

  @Test
  public void peekAndReplace_Null() {
    Integer v = cache.peekAndReplace(KEY, null);
    assertNull(v);
    assertFalse(cache.contains(KEY));
    cache.put(KEY, VALUE);
    v = cache.peekAndReplace(KEY, null);
    assertNotNull(v);
    assertTrue(cache.contains(KEY));
    assertEquals(VALUE, v);
    assertNull(cache.peek(KEY));
  }

  @Test(expected = NullPointerException.class)
  public void peekAndReplace_NullKey() {
    cache.peekAndReplace(null, VALUE);
  }

  @Test(expected = PropagatedCacheException.class)
  public void peekAndReplace_Exception() {
    ((Cache) cache).put(KEY, new ExceptionWrapper(OUCH));
    cache.peekAndReplace(KEY, VALUE);
  }

  /*
   * peekEntry
   */
  @Test
  public void peekEntry() {
    long t0 = System.currentTimeMillis();
    CacheEntry<Integer, Integer> e = cache.peekEntry(KEY);
    assertNull(e);
    cache.put(KEY, VALUE);
    e = cache.peekEntry(KEY);
    assertEquals(KEY, e.getKey());
    assertEquals(VALUE, e.getValue());
    assertTrue(e.getLastModification() >= t0);
  }

  @Test
  public void peekEntry_Null() {
    long t0 = System.currentTimeMillis();
    CacheEntry<Integer, Integer> e = cache.peekEntry(KEY);
    assertNull(e);
    cache.put(KEY, null);
    e = cache.peekEntry(KEY);
    assertEquals(KEY, e.getKey());
    assertNull(e.getValue());
    assertTrue(e.getLastModification() >= t0);
  }

  @Test(expected = NullPointerException.class)
  public void peekEntry_NullKey() {
    cache.peekEntry(null);
  }

  @Test
  public void peekEntry_Exception() {
    ((Cache) cache).put(KEY, new ExceptionWrapper(OUCH));
    CacheEntry<Integer, Integer> e = cache.peekEntry(KEY);
    assertEquals(KEY, e.getKey());
    assertNull(e.getValue());
    assertEquals(OUCH, e.getException());
  }

  /*
   * getEntry
   */
  @Test
  public void getEntry() {
    long t0 = System.currentTimeMillis();
    cache.put(KEY, VALUE);
    CacheEntry<Integer, Integer> e = cache.getEntry(KEY);
    assertEquals(KEY, e.getKey());
    assertEquals(VALUE, e.getValue());
    assertTrue(e.getLastModification() >= t0);
  }

  @Test
  public void getEntry_Null() {
    long t0 = System.currentTimeMillis();
    cache.put(KEY, null);
    CacheEntry<Integer, Integer> e = cache.getEntry(KEY);
    assertEquals(KEY, e.getKey());
    assertNull(e.getValue());
    assertTrue(e.getLastModification() >= t0);
  }

  @Test(expected = NullPointerException.class)
  public void getEntry_NullKey() {
    cache.getEntry(null);
  }

  @Test
  public void getEntry_Exception() {
    ((Cache) cache).put(KEY, new ExceptionWrapper(OUCH));
    CacheEntry<Integer, Integer> e = cache.getEntry(KEY);
    assertEquals(KEY, e.getKey());
    assertNull(e.getValue());
    assertEquals(OUCH, e.getException());
  }

  /*
   * peek all
   */
  @Test
  public void peekAll() {
    Map<Integer, Integer> m = cache.peekAll(asSet(KEY, OTHER_KEY));
    assertEquals(0, m.size());
    assertTrue(m.isEmpty());
    cache.put(KEY, VALUE);
    m = cache.peekAll(asSet(KEY, OTHER_KEY));
    assertEquals(1, m.size());
    assertEquals(VALUE, m.get(KEY));
    assertTrue(m.containsKey(KEY));
    assertTrue(m.containsValue(VALUE));
    assertNull(m.get(OTHER_KEY));
  }

  @Test
  public void peekAll_Null() {
    cache.put(KEY, null);
    Map<Integer, Integer> m = cache.peekAll(asSet(KEY, OTHER_KEY));
    assertEquals(1, m.size());
    assertNull(m.get(KEY));
  }

  @Test(expected = NullPointerException.class)
  public void peekAll_NullKey() {
    cache.peekAll(asSet(new Integer[]{null}));
  }

  @Test
  public void peekAll_Exception() {
    ((Cache) cache).put(KEY, new ExceptionWrapper(OUCH));
    Map<Integer, Integer> m = cache.peekAll(asSet(KEY, OTHER_KEY));
    assertEquals(1, m.size());
    assertEquals(1, m.values().size());
    assertEquals(1, m.keySet().size());
    assertEquals(1, m.entrySet().size());
    try {
      m.get(KEY);
      fail("Exception expected");
    } catch (PropagatedCacheException ex) {
    }
    Iterator<Integer> it = m.keySet().iterator();
    assertTrue(it.hasNext());
    assertEquals(KEY, it.next());
    assertFalse("one entry", it.hasNext());
    it = m.values().iterator();
    assertTrue(it.hasNext());
    try {
      assertEquals(KEY, it.next());
      fail("Exception expected");
    } catch (PropagatedCacheException ex) {
    }
    Iterator<Map.Entry<Integer, Integer>> ei = m.entrySet().iterator();
    assertTrue(ei.hasNext());
    Map.Entry<Integer,Integer> e = ei.next();
    assertEquals(KEY, e.getKey());
    try {
      e.getValue();
      fail("Exception expected");
    } catch (PropagatedCacheException ex) {
    }
  }

  @Test
  public void peekAll_MutationMethodsUnsupported() {
    cache.put(KEY, VALUE);
    Map<Integer, Integer> m = cache.peekAll(asSet(KEY, OTHER_KEY));
    assertEquals(1, m.size());
    try {
      m.clear();
      fail("Exception expected");
    } catch (UnsupportedOperationException ex) {
    }
    try {
      m.put(KEY, VALUE);
      fail("Exception expected");
    } catch (UnsupportedOperationException ex) {
    }
    try {
      m.remove(KEY);
      fail("Exception expected");
    } catch (UnsupportedOperationException ex) {
    }
    try {
      m.clear();
      fail("Exception expected");
    } catch (UnsupportedOperationException ex) {
    }
    try {
      m.putAll(null);
      fail("Exception expected");
    } catch (UnsupportedOperationException ex) {
    }
    try {
      m.values().add(4711);
      fail("Exception expected");
    } catch (UnsupportedOperationException ex) {
    }
    try {
      m.entrySet().iterator().next().setValue(4711);
      fail("Exception expected");
    } catch (UnsupportedOperationException ex) {
    }
    try {
      m.entrySet().iterator().remove();
      fail("Exception expected");
    } catch (UnsupportedOperationException ex) {
    }
    try {
      m.values().iterator().remove();
      fail("Exception expected");
    } catch (UnsupportedOperationException ex) {
    }
  }

  /*
   * getAll()
   */
  @Test
  public void getAll() {
    cache.put(KEY, VALUE);
    cache.put(OTHER_KEY, VALUE);
    Map<Integer, Integer> m = cache.getAll(asSet(KEY, OTHER_KEY));
    assertEquals(2, m.size());
    assertEquals(VALUE, m.get(KEY));
    assertTrue(m.containsKey(KEY));
    assertTrue(m.containsValue(VALUE));
  }

  @Test(expected = NullPointerException.class)
  public void getAll_NullKey() {
    cache.getAll((asSet(new Integer[]{null})));
  }

  /*
   * remove(k)
   */

  @Test
  public void remove_NotExisting() {
    statistics().reset();
    cache.remove(KEY);
    statistics().expectAllZero();
    assertFalse(cache.contains(KEY));
  }

  @Test
  public void remove() {
    cache.put(KEY, VALUE);
    assertTrue(cache.contains(KEY));
    statistics().reset();
    cache.remove(KEY);
    statistics().removeCount.expect(1).expectAllZero();
    assertFalse(cache.contains(KEY));
  }

  @Test
  public void remove_Null() {
    cache.put(KEY, null);
    cache.remove(KEY);
    assertFalse(cache.contains(KEY));
  }

  @Test(expected = NullPointerException.class)
  public void remove_NullKey() {
    cache.remove(null);
  }

  /*
   * containsAndRemove(k)
   */

  @Test
  public void containsAndRemove() {
    boolean f = cache.containsAndRemove(KEY);
    assertFalse(f);
    assertFalse(cache.contains(KEY));
    cache.put(KEY, VALUE);
    assertTrue(cache.contains(KEY));
    f = cache.containsAndRemove(KEY);
    assertFalse(cache.contains(KEY));
    assertTrue(f);
  }

  @Test
  public void containsAndRemove_Null() {
    cache.put(KEY, null);
    cache.containsAndRemove(KEY);
    assertFalse(cache.contains(KEY));
  }

  @Test(expected = NullPointerException.class)
  public void containsAndRemove_NullKey() {
    cache.containsAndRemove(null);
  }

  /*
   * remove(k, v)
   */

  @Test
  public void removeIfEquals() {
    boolean f = cache.removeIfEquals(KEY, VALUE);
    assertFalse(f);
    assertFalse(cache.contains(KEY));
    cache.put(KEY, VALUE);
    assertTrue(cache.contains(KEY));
    f = cache.removeIfEquals(KEY, OTHER_VALUE);
    assertFalse(f);
    f = cache.removeIfEquals(KEY, VALUE);
    assertFalse(cache.contains(KEY));
    assertTrue(f);
    f = cache.removeIfEquals(KEY, VALUE);
    assertFalse(f);
  }

  @Test
  public void removeIfEquals_Null() {
    boolean f = cache.removeIfEquals(KEY, null);
    assertFalse(f);
    cache.put(KEY, null);
    f = cache.removeIfEquals(KEY, OTHER_VALUE);
    assertFalse(f);
    f = cache.removeIfEquals(KEY, null);
    assertTrue(f);
    assertFalse(cache.contains(KEY));
  }

  @Test(expected = NullPointerException.class)
  public void removeIfEquals_NullKey() {
    cache.removeIfEquals(null, OTHER_VALUE);
  }

  /*
   * replaceIfEquals
   */

  @Test
  public void replaceIfEquals() {
    assertFalse(cache.replaceIfEquals(KEY, VALUE, OTHER_VALUE));
    assertFalse(cache.contains(KEY));
    cache.put(KEY, VALUE);
    assertTrue(cache.replaceIfEquals(KEY, VALUE, OTHER_VALUE));
    assertEquals(OTHER_VALUE, cache.peek(KEY));
  }

  @Test
  public void replaceIfEquals_Different() {
    cache.put(KEY, VALUE);
    assertEquals(VALUE, cache.peek(KEY));
    assertFalse(cache.replaceIfEquals(KEY, OTHER_VALUE, OTHER_VALUE));
    assertEquals(VALUE, cache.peek(KEY));
  }

  @Test
  public void replaceIfEquals_NoMap() {
    cache.put(KEY, VALUE);
    assertFalse(cache.replaceIfEquals(OTHER_KEY, OTHER_VALUE, OTHER_VALUE));
    assertEquals(VALUE, cache.peek(KEY));
    assertNull(cache.peek(OTHER_KEY));
    assertFalse(cache.contains(OTHER_KEY));
  }

  @Test
  public void replaceIfEquals_Null() {
    cache.replaceIfEquals(KEY, null, null);
    cache.put(KEY, null);
    cache.replaceIfEquals(KEY, null, VALUE);
    assertEquals(VALUE, cache.peek(KEY));
    cache.replaceIfEquals(KEY, OTHER_VALUE, null);
    assertEquals(VALUE, cache.peek(KEY));
    cache.replaceIfEquals(KEY, VALUE, null);
    assertTrue(cache.contains(KEY));
  }

  @Test(expected = NullPointerException.class)
  public void replaceIfEquals_NullKey() {
    cache.replaceIfEquals(null, OTHER_VALUE, OTHER_VALUE);
  }

  /*
   * replace
   */

  @Test
  public void replace() {
    boolean f = cache.replace(KEY, VALUE);
    assertFalse(f);
    cache.put(KEY, VALUE);
    f = cache.replace(KEY, OTHER_VALUE);
    assertTrue(f);
    assertEquals(OTHER_VALUE, cache.peek(KEY));
  }

  @Test
  public void replace_NoMap() {
    cache.put(KEY, VALUE);
    assertFalse(cache.replace(OTHER_KEY, OTHER_VALUE));
    assertEquals(VALUE, cache.peek(KEY));
    assertNull(cache.peek(OTHER_KEY));
    assertFalse(cache.contains(OTHER_KEY));
  }

  @Test
  public void replace_Null() {
    boolean f = cache.replace(KEY, null);
    assertFalse(f);
    cache.put(KEY, VALUE);
    f = cache.replace(KEY, null);
    assertTrue(f);
    assertNull(cache.peek(KEY));
    assertTrue(cache.contains(KEY));
  }

  @Test(expected = NullPointerException.class)
  public void replace_NullKey() {
    cache.replace(null, VALUE);
  }

  /*
   * iterator()
   */

  @Test
  public void iterator() {
    assertFalse(cache.iterator().hasNext());
    cache.put(KEY, VALUE);
    cache.put(OTHER_KEY, OTHER_VALUE);
    statistics().reset();
    Map<Integer,Integer> map = new HashMap<Integer, Integer>();
    for (CacheEntry<Integer, Integer> ce : cache) {
      map.put(ce.getKey(), ce.getValue());
    }
    assertEquals(2, map.size());
    assertTrue(map.containsKey(KEY));
    assertTrue(map.containsKey(OTHER_KEY));
    statistics().expectAllZero();
  }

}
