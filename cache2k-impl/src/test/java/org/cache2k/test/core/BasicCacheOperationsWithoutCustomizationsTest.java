package org.cache2k.test.core;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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

import org.cache2k.AbstractCache;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.ForwardingCache;
import org.cache2k.core.ExceptionWrapper;
import org.cache2k.core.InternalCache;
import org.cache2k.core.InternalCacheInfo;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.integration.CacheLoaderException;
import org.cache2k.processor.EntryProcessingResult;
import org.cache2k.processor.EntryProcessor;
import org.cache2k.processor.MutableCacheEntry;
import org.cache2k.testing.category.FastTests;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.cache2k.test.core.StaticUtil.toIterable;
import static org.junit.Assert.*;

/**
 * Test basic cache operations on a shared cache in a simple configuration.
 * The cache may hold 1000 entries and has no expiry.
 */
@Category(FastTests.class) @RunWith(Parameterized.class)
public class BasicCacheOperationsWithoutCustomizationsTest {

  final static Map<Pars, Cache> PARS2CACHE = new ConcurrentHashMap<Pars, Cache>();

  @SuppressWarnings("ThrowableInstanceNeverThrown")
  final static Exception OUCH = new Exception("ouch");
  final static Integer KEY = 1;
  final static Integer OTHER_KEY = 2;
  final static Integer VALUE = 1;
  final static Integer OTHER_VALUE = 2;

  final static long START_TIME = System.currentTimeMillis();

  static Pars.Builder pars() { return new Pars.Builder(); }

  static void extend(List<Object[]> l, Pars.Builder... parameters) {
    for (Pars.Builder o : parameters) {
      l.add(new Object[]{o.build()});
    }
  }

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    List<Object[]> l = new ArrayList<Object[]>();
    for (Pars o : new Pars.TestVariants()) {
      l.add(new Object[]{o});
    }
    extend(l,
      pars().withForwardingAndAbstract(true)
      );
    return l;

  }

  /**
   * Used cache is a class field. We may subclass this class and run the tests with a different
   * configuration.
   */
  Cache<Integer, Integer> cache;

  Statistics statistics;

  Pars pars;

  public BasicCacheOperationsWithoutCustomizationsTest(Pars p) {
    pars = p;
    synchronized (PARS2CACHE) {
      cache = PARS2CACHE.get(p);
      if (cache == null) {
        cache = createCache();
        PARS2CACHE.put(p, cache);
      }
    }
    statistics = new Statistics(pars.disableStatistics || pars.withEntryProcessor);
  }

  protected Cache<Integer,Integer> createCache() {
    Cache2kBuilder b =
      Cache2kBuilder.of(Integer.class, Integer.class)
        .name(this.getClass().getSimpleName() + "-" + pars.toString().replace('=','~'))
        .retryInterval(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
        .eternal(true)
        .entryCapacity(1000)
        .permitNullValues(true)
        .keepDataAfterExpired(pars.keepDataAfterExpired)
        .disableLastModificationTime(pars.disableLastModified)
        .disableStatistics(pars.disableStatistics);
    if (pars.withWiredCache) {
      StaticUtil.enforceWiredCache(b);
    }
    Cache<Integer,Integer> c = b.build();
    if (pars.withEntryProcessor) {
      c = new EntryProcessorCacheWrapper<Integer, Integer>(c);
    }
    if (pars.withForwardingAndAbstract) {
      c = wrapAbstractAndForwarding(c);
    }
    return c;
  }

  /**
   * Wrap into a proxy and check the exceptions on the abstract cache and then use the forwarding cache.
   */
  protected Cache<Integer, Integer> wrapAbstractAndForwarding(final Cache<Integer, Integer> c) {
    final Cache<Integer, Integer> _forwardingCache = new ForwardingCache<Integer, Integer>() {
      @Override
      protected Cache<Integer, Integer> delegate() {
        return c;
      }
    };
    final Cache<Integer, Integer> _abstractCache = new AbstractCache<Integer, Integer>();
    InvocationHandler h = new InvocationHandler() {
      @Override
      public Object invoke(final Object _proxy, final Method _method, final Object[] _args) throws Throwable {
        try {
          _method.invoke(_abstractCache, _args);
          if (!_method.getName().equals("toString")) {
            fail("exception expected for method: " + _method);
          }
        } catch(InvocationTargetException ex) {
          assertEquals("expected exception",
            UnsupportedOperationException.class, ex.getTargetException().getClass());
        }
        try {
          return _method.invoke(_forwardingCache, _args);
        } catch(InvocationTargetException ex) {
          throw ex.getTargetException();
        }
      }
    };
    return (Cache<Integer, Integer>)
      Proxy.newProxyInstance(
        getClass().getClassLoader(),
        new Class<?>[]{Cache.class}, h);
  }

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
    statistics().reset();
  }

  @After
  public void cleanupCache() {
    assertTrue("Tests are not allowed to create private caches",
      PARS2CACHE.get(pars) == cache);
    cache.requestInterface(InternalCache.class).checkIntegrity();
    cache.clear();
  }

  @AfterClass
  public static void tearDown() {
    for (Cache c : PARS2CACHE.values()) {
      c.clearAndClose();
      c.close();
      assertTrue(c.isClosed());
    }
  }

  /*
   * initial: Tests on the initial state of the cache.
   */

  @Test
  public void intital_Static_Stuff() {
    assertFalse(cache.isClosed());
    assertNotNull(cache.getName());
    assertNotNull(cache.getCacheManager());
    assertNotNull(cache.toString());
  }

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

  @Test
  public void initial_Size() {
    assertEquals(0, size());
    assertEquals(0, cache.asMap().size());
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
    checkLastModified(cache.peekEntry(KEY));
  }

  void checkLastModified(CacheEntry e) {
    if (!pars.disableLastModified) {
      assertTrue(cache.peekEntry(KEY).getLastModification() >= START_TIME);
    } else {
      try {
        long t = cache.peekEntry(KEY).getLastModification();
        fail("expected UnsupportedOperationException");
      } catch (UnsupportedOperationException ex) {
      }
    }
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
    assertTrue(cache.containsKey(KEY));
    assertTrue(cache.containsKey(OTHER_KEY));
    assertNull(cache.peek(OTHER_KEY));
    assertEquals(VALUE, cache.peek(KEY));
    checkLastModified(cache.peekEntry(KEY));
  }

  /*
   * computeIfAbsent
   */

  @Test
  public void computeIfAbsent() {
    Integer v = cache.computeIfAbsent(KEY, new Callable<Integer>() {
      @Override
      public Integer call() throws Exception {
        return VALUE;
      }
    });
    statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .putCount.expect(1)
      .expectAllZero();
    assertEquals(VALUE, v);
    assertTrue(cache.containsKey(KEY));
    assertEquals(KEY, cache.peek(KEY));
    statistics()
      .getCount.expect(1)
      .expectAllZero();
    v = cache.computeIfAbsent(KEY, new Callable<Integer>() {
      @Override
      public Integer call() throws Exception {
        return OTHER_VALUE;
      }
    });
    statistics()
      .getCount.expect(1)
      .missCount.expect(0)
      .putCount.expect(0)
      .expectAllZero();
    assertEquals(VALUE, v);
    assertTrue(cache.containsKey(KEY));
    assertEquals(VALUE, cache.peek(KEY));
    checkLastModified(cache.peekEntry(KEY));
    cache.put(KEY, VALUE);
  }

  @Test
  public void computeIfAbsent_Null() {
    cache.computeIfAbsent(KEY, new Callable<Integer>() {
      @Override
      public Integer call() throws Exception {
        return null;
      }
    });
    assertTrue(cache.containsKey(KEY));
    assertNull(cache.peek(KEY));
  }

  @Test
  public void computeIfAbsent_Exception() {
    try {
      cache.computeIfAbsent(KEY, new Callable<Integer>() {
        @Override
        public Integer call() throws Exception {
          throw new IOException("for testing");
        }
      });
      fail("CacheLoaderException expected");
    } catch (CacheLoaderException ex) {
      assertTrue(ex.getCause() instanceof IOException);
    }
    statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .putCount.expect(0)
      .expectAllZero();
    assertFalse(cache.containsKey(KEY));
  }

  @Test
  public void computeIfAbsent_RuntimeException() {
    try {
      cache.computeIfAbsent(KEY, new Callable<Integer>() {
        @Override
        public Integer call() throws Exception {
          throw new IllegalArgumentException("for testing");
        }
      });
      fail("RuntimeException expected");
    } catch (RuntimeException ex) {
      assertTrue(ex instanceof IllegalArgumentException);
    }
    statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .putCount.expect(0)
      .expectAllZero();
    assertFalse(cache.containsKey(KEY));
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
      .getCount.expect(pars.keepDataAfterExpired && pars.withWiredCache ? 2 : 1)
      .missCount.expect(1)
      .expectAllZero();
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
   * putIfAbsent()
   */

  @Test
  public void putIfAbsent() {
    cache.putIfAbsent(KEY, VALUE);
    statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .putCount.expect(1)
      .expectAllZero();
    assertTrue(cache.containsKey(KEY));
    assertEquals(KEY, cache.peek(KEY));
    statistics()
      .getCount.expect(1)
      .expectAllZero();
    cache.putIfAbsent(KEY, OTHER_VALUE);
    statistics()
      .getCount.expect(1)
      .missCount.expect(0)
      .putCount.expect(0)
      .expectAllZero();
    assertTrue(cache.containsKey(KEY));
    assertEquals(VALUE, cache.peek(KEY));
    checkLastModified(cache.peekEntry(KEY));
  }

  @Test
  public void putIfAbsent_Null() {
    cache.putIfAbsent(KEY, null);
    assertTrue(cache.containsKey(KEY));
    assertNull(cache.peek(KEY));
  }

  /*
   * peekAndPut
   */

  @Test
  public void peekAndPut() {
    Integer v = cache.peekAndPut(KEY, VALUE);
    assertNull(v);
    statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .putCount.expect(1)
      .expectAllZero();
    v = cache.peekAndPut(KEY, VALUE);
    assertNotNull(v);
    assertEquals(VALUE, v);
    statistics()
      .getCount.expect(1)
      .missCount.expect(0)
      .putCount.expect(1)
      .expectAllZero();
    checkLastModified(cache.peekEntry(KEY));
  }

  @Test(expected = NullPointerException.class)
  public void peekAndPut_NullKey() {
    cache.peekAndPut(null, VALUE);
    statistics().expectAllZero();
  }

  @Test
  public void peekAndPut_Null() {
    Integer v = cache.peekAndPut(KEY, null);
    assertNull(v);
    assertTrue(cache.containsKey(KEY));
    statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .putCount.expect(1)
      .expectAllZero();
    v = cache.peekAndPut(KEY, VALUE);
    assertNull(v);
    assertTrue(cache.containsKey(KEY));
    statistics()
      .getCount.expect(1)
      .missCount.expect(0)
      .putCount.expect(1)
      .expectAllZero();
    v = cache.peekAndPut(KEY, null);
    assertNotNull(v);
    assertEquals(VALUE, v);
    v = cache.peekAndPut(KEY, null);
    assertNull(v);
  }

  @Test(expected = CacheLoaderException.class)
  public void peekAndPut_Exception() {
    ((Cache) cache).put(KEY, new ExceptionWrapper(OUCH));
    cache.peekAndPut(KEY, VALUE);
  }

  @Test
  public void peekAndPut_NotFresh() {
    cache.put(KEY, VALUE);
    cache.expireAt(KEY, ExpiryTimeValues.NOW);
    statistics().reset();
    Integer v = cache.peekAndPut(KEY, VALUE);
    assertNull(v);
    statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .putCount.expect(1)
      .expectAllZero();
  }

  /*
   * peekAndRemove
   */

  @Test
  public void peekAndRemove() {
    Integer v = cache.peekAndRemove(KEY);
    statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .putCount.expect(0)
      .expectAllZero();
    assertNull(v);
    assertFalse(cache.containsKey(KEY));
    cache.put(KEY, VALUE);
    assertTrue(cache.containsKey(KEY));
    statistics()
      .getCount.expect(0)
      .missCount.expect(0)
      .removeCount.expect(0)
      .putCount.expect(1)
      .expectAllZero();
    v = cache.peekAndRemove(KEY);
    statistics()
      .getCount.expect(1)
      .missCount.expect(0)
      .removeCount.expect(1)
      .putCount.expect(0)
      .expectAllZero();
    assertNotNull(v);
    assertFalse(cache.containsKey(KEY));
  }

  @Test
  public void peekAndRemove_Null() {
    cache.put(KEY, null);
    assertTrue(cache.containsKey(KEY));
    Integer v = cache.peekAndRemove(KEY);
    assertNull(v);
    assertFalse(cache.containsKey(KEY));
  }

  @Test(expected = NullPointerException.class)
  public void peekAndRemove_NullKey() {
    cache.peekAndRemove(null);
  }

  @Test
  public void peekAndRemove_Exception() {
    ((Cache) cache).put(KEY, new ExceptionWrapper(OUCH));
    try {
      cache.peekAndRemove(KEY);
      fail("exception expected");
    } catch (CacheLoaderException ex) {
    }
  }

  @Test
  public void peekAndRemove_NotFresh() {
    cache.put(KEY, VALUE);
    cache.expireAt(KEY, ExpiryTimeValues.NOW);
    statistics().reset();
    Integer v = cache.peekAndRemove(KEY);
    assertNull(v);
    statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .removeCount.expect(pars.keepDataAfterExpired && pars.withWiredCache ? 1: 0)
      .expectAllZero();
  }

  /*
   * peekAndReplace
   */

  @Test
  public void peekAndReplace() {
    Integer v = cache.peekAndReplace(KEY, VALUE);
    assertNull(v);
    assertFalse(cache.containsKey(KEY));
    cache.put(KEY, VALUE);
    v = cache.peekAndReplace(KEY, OTHER_VALUE);
    assertNotNull(v);
    assertTrue(cache.containsKey(KEY));
    assertEquals(VALUE, v);
    assertEquals(OTHER_VALUE, cache.peek(KEY));
  }

  @Test
  public void peekAndReplace_Null() {
    Integer v = cache.peekAndReplace(KEY, null);
    assertNull(v);
    assertFalse(cache.containsKey(KEY));
    cache.put(KEY, VALUE);
    v = cache.peekAndReplace(KEY, null);
    assertNotNull(v);
    assertTrue(cache.containsKey(KEY));
    assertEquals(VALUE, v);
    assertNull(cache.peek(KEY));
  }

  @Test(expected = NullPointerException.class)
  public void peekAndReplace_NullKey() {
    cache.peekAndReplace(null, VALUE);
  }

  @Test(expected = CacheLoaderException.class)
  public void peekAndReplace_Exception() {
    ((Cache) cache).put(KEY, new ExceptionWrapper(OUCH));
    cache.peekAndReplace(KEY, VALUE);
  }

  /*
   * peekEntry
   */

  @Test
  public void peekEntry_Initial() {
    CacheEntry<Integer, Integer> e = cache.peekEntry(KEY);
    assertNull(e);
    assertEquals(0, size());
  }

  @Test
  public void peekEntry() {
    CacheEntry<Integer, Integer> e = cache.peekEntry(KEY);
    assertNull(e);
    cache.put(KEY, VALUE);
    e = cache.peekEntry(KEY);
    assertEquals(KEY, e.getKey());
    assertEquals(VALUE, e.getValue());
    assertNull(e.getException());
    checkLastModified(e);
  }

  @Test
  public void peekEntry_Null() {
    CacheEntry<Integer, Integer> e = cache.peekEntry(KEY);
    assertNull(e);
    cache.put(KEY, null);
    e = cache.peekEntry(KEY);
    assertEquals(KEY, e.getKey());
    assertNull(e.getValue());
    checkLastModified(e);
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
    entryHasException(e);
    assertEquals(OUCH, e.getException());
  }

  /*
   * getEntry
   */

  @Test
  public void getEntry() {
    cache.put(KEY, VALUE);
    CacheEntry<Integer, Integer> e = cache.getEntry(KEY);
    assertEquals(KEY, e.getKey());
    assertEquals(VALUE, e.getValue());
    assertNull(e.getException());
    checkLastModified(e);
  }

  @Test
  public void getEntry_Null() {
    cache.put(KEY, null);
    CacheEntry<Integer, Integer> e = cache.getEntry(KEY);
    assertEquals(KEY, e.getKey());
    assertNull(e.getValue());
    checkLastModified(e);
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
    entryHasException(e);
    assertEquals(OUCH, e.getException());
  }

  private static void entryHasException(final CacheEntry<Integer, Integer> e) {
    try {
      e.getValue();
      fail("exception expected");
    } catch (CacheLoaderException ex) {
    }
    assertNotNull(e.getException());
  }

  /*
   * peek all
   */
  @Test
  public void peekAll() {
    Map<Integer, Integer> m = cache.peekAll(toIterable(KEY, OTHER_KEY));
    assertEquals(0, m.size());
    assertTrue(m.isEmpty());
    cache.put(KEY, VALUE);
    m = cache.peekAll(toIterable(KEY, OTHER_KEY));
    assertEquals(1, m.size());
    assertEquals(VALUE, m.get(KEY));
    assertTrue(m.containsKey(KEY));
    assertTrue(m.containsValue(VALUE));
    assertNull(m.get(OTHER_KEY));
  }

  @Test
  public void peekAll_Null() {
    cache.put(KEY, null);
    Map<Integer, Integer> m = cache.peekAll(toIterable(KEY, OTHER_KEY));
    assertEquals(1, m.size());
    assertNull(m.get(KEY));
  }

  @Test(expected = NullPointerException.class)
  public void peekAll_NullKey() {
    cache.peekAll(toIterable(new Integer[]{null}));
  }

  @Test
  public void peekAll_Exception() {
    ((Cache) cache).put(KEY, new ExceptionWrapper(OUCH));
    Map<Integer, Integer> m = cache.peekAll(toIterable(KEY, OTHER_KEY));
    assertEquals(1, m.size());
    assertEquals(1, m.values().size());
    assertEquals(1, m.keySet().size());
    assertEquals(1, m.entrySet().size());
    try {
      m.get(KEY);
      fail("Exception expected");
    } catch (CacheLoaderException ex) {
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
    } catch (CacheLoaderException ex) {
    }
    Iterator<Map.Entry<Integer, Integer>> ei = m.entrySet().iterator();
    assertTrue(ei.hasNext());
    Map.Entry<Integer,Integer> e = ei.next();
    assertEquals(KEY, e.getKey());
    try {
      e.getValue();
      fail("Exception expected");
    } catch (CacheLoaderException ex) {
    }
  }

  @Test
  public void peekAll_MutationMethodsUnsupported() {
    cache.put(KEY, VALUE);
    Map<Integer, Integer> m = cache.peekAll(toIterable(KEY, OTHER_KEY));
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
    Map<Integer, Integer> m = cache.getAll(toIterable(KEY, OTHER_KEY));
    assertEquals(2, m.size());
    assertEquals(VALUE, m.get(KEY));
    assertTrue(m.containsKey(KEY));
    assertTrue(m.containsValue(VALUE));
  }

  @Test(expected = NullPointerException.class)
  public void getAll_NullKey() {
    cache.getAll((toIterable(new Integer[]{null})));
  }

  @Test
  public void getAll_not_present_no_loader() {
    Map<Integer, Integer> m = cache.getAll(toIterable(KEY, OTHER_KEY));
    assertEquals(0, m.size());
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
   * containsAndRemove(k)
   */

  @Test
  public void containsAndRemove() {
    boolean f = cache.containsAndRemove(KEY);
    assertFalse(f);
    assertFalse(cache.containsKey(KEY));
    cache.put(KEY, VALUE);
    assertTrue(cache.containsKey(KEY));
    f = cache.containsAndRemove(KEY);
    assertFalse(cache.containsKey(KEY));
    assertTrue(f);
  }

  @Test
  public void containsAndRemove_Null() {
    cache.put(KEY, null);
    cache.containsAndRemove(KEY);
    assertFalse(cache.containsKey(KEY));
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
    assertFalse(cache.containsKey(KEY));
    cache.put(KEY, VALUE);
    assertTrue(cache.containsKey(KEY));
    f = cache.removeIfEquals(KEY, OTHER_VALUE);
    assertFalse(f);
    f = cache.removeIfEquals(KEY, VALUE);
    assertFalse(cache.containsKey(KEY));
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
    assertFalse(cache.containsKey(KEY));
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
    assertFalse(cache.containsKey(KEY));
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
    assertFalse(cache.containsKey(OTHER_KEY));
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
    assertTrue(cache.containsKey(KEY));
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
    statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .expectAllZero();
    cache.put(KEY, VALUE);
    f = cache.replace(KEY, OTHER_VALUE);
    assertTrue(f);
    statistics()
      .getCount.expect(1)
      .missCount.expect(0)
      .putCount.expect(2)
      .expectAllZero();
    assertEquals(OTHER_VALUE, cache.peek(KEY));
    statistics()
      .getCount.expect(1)
      .missCount.expect(0)
      .putCount.expect(0)
      .expectAllZero();
    checkLastModified(cache.peekEntry(KEY));
  }

  @Test
  public void replace_NoMap() {
    cache.put(KEY, VALUE);
    assertFalse(cache.replace(OTHER_KEY, OTHER_VALUE));
    assertEquals(VALUE, cache.peek(KEY));
    assertNull(cache.peek(OTHER_KEY));
    assertFalse(cache.containsKey(OTHER_KEY));
  }

  @Test
  public void replace_Null() {
    boolean f = cache.replace(KEY, null);
    assertFalse(f);
    cache.put(KEY, VALUE);
    f = cache.replace(KEY, null);
    assertTrue(f);
    assertNull(cache.peek(KEY));
    assertTrue(cache.containsKey(KEY));
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
    assertFalse(cache.entries().iterator().hasNext());
    cache.put(KEY, VALUE);
    cache.put(OTHER_KEY, OTHER_VALUE);
    statistics().reset();
    Map<Integer,Integer> map = new HashMap<Integer, Integer>();
    for (CacheEntry<Integer, Integer> ce : cache.entries()) {
      map.put(ce.getKey(), ce.getValue());
    }
    assertEquals(2, map.size());
    assertTrue(map.containsKey(KEY));
    assertTrue(map.containsKey(OTHER_KEY));
    statistics().expectAllZero();
  }

  @Test(expected = NoSuchElementException.class)
  public void iterator_Next_Exception() {
    Iterator it = cache.entries().iterator();
    assertFalse(it.hasNext());
    it.next();
  }

  /** Iteration stops if cleared. */
  @Test
  public void iterator_clear() {
    cache.put(KEY, VALUE);
    cache.put(OTHER_KEY, OTHER_VALUE);
    Iterator it = cache.entries().iterator();
    assertTrue(it.hasNext());
    it.next();
    cache.clear();
    assertFalse(it.hasNext());
  }

  /*
   * Misc
   */

  @Test
  public void removeAll() {
    cache.put(KEY, VALUE);
    cache.put(OTHER_KEY, OTHER_VALUE);
    cache.removeAll();
    assertFalse(cache.keys().iterator().hasNext());
  }

  @Test
  public void removeAllSortCircuit() {
    cache.put(KEY, VALUE);
    cache.put(OTHER_KEY, OTHER_VALUE);
    cache.removeAll(cache.keys());
    assertFalse(cache.keys().iterator().hasNext());
  }

  @Test(expected=UnsupportedOperationException.class)
  public void loadAll() {
    cache.loadAll(toIterable(KEY, OTHER_KEY), null);
  }

  @Test(expected=UnsupportedOperationException.class)
  public void reloadAll() {
    cache.reloadAll(toIterable(KEY, OTHER_KEY), null);
  }

  @Test
  public void prefetch() {
    cache.prefetch(KEY);
  }

  @Test
  public void prefetchAll()  {
    cache.prefetchAll(toIterable(KEY, OTHER_KEY), null);
  }

  @Test
  public void getEntryState() {
    if (!(cache instanceof InternalCache)) {
      return;
    }
    InternalCache c = (InternalCache) cache;
    String s = c.getEntryState(KEY);
    assertNull(s);
    cache.put(KEY, VALUE);
    s = c.getEntryState(KEY);
    assertNotNull(s);
  }

  @Test
  public void getEntryState_Exception() {
    if (!(cache instanceof InternalCache)) {
      return;
    }
    ((Cache) cache).put(KEY, new ExceptionWrapper(OUCH));
    InternalCache c = (InternalCache) cache;
    String s = c.getEntryState(KEY);
    assertTrue(s.contains("exception="));
  }

  @Test
  public void requstInterface() {
    assertNull(cache.requestInterface(Integer.class));
    assertTrue(cache.requestInterface(Map.class) instanceof Map);
  }

  @Test
  public void invoke() {
    cache.put(KEY, VALUE);
    boolean f = cache.invoke(KEY, new EntryProcessor<Integer, Integer, Boolean>() {
      @Override
      public Boolean process(final MutableCacheEntry<Integer, Integer> e) throws Exception {
        return e.exists();
      }
    });
    assertTrue(f);
  }

  @Test
  public void invokeAll() {
    cache.put(KEY, VALUE);
    Map<Integer, EntryProcessingResult<Boolean>> res =
      cache.invokeAll(cache.keys(), new EntryProcessor<Integer, Integer, Boolean>() {
      @Override
      public Boolean process(final MutableCacheEntry<Integer, Integer> e) throws Exception {
        return e.exists();
      }
    });
    assertEquals(1, res.size());
    assertNull(res.get(KEY).getException());
    assertTrue(res.get(KEY).getResult());
  }

  static class Pars {

    boolean strictEviction = false;
    boolean disableLastModified = false;
    boolean disableStatistics = false;
    boolean withEntryProcessor = false;
    boolean withWiredCache = false;
    boolean withForwardingAndAbstract = false;
    boolean keepDataAfterExpired = false;

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Pars p = (Pars) o;

      if (strictEviction != p.strictEviction) return false;
      if (disableLastModified != p.disableLastModified) return false;
      if (disableStatistics != p.disableStatistics) return false;
      if (withEntryProcessor != p.withEntryProcessor) return false;
      if (withWiredCache != p.withWiredCache) return false;
      if (withForwardingAndAbstract != p.withForwardingAndAbstract) return false;
      return keepDataAfterExpired == p.keepDataAfterExpired;
    }

    @Override
    public int hashCode() {
      int _result = (strictEviction ? 1 : 0);
      _result = 31 * _result + (disableLastModified ? 1 : 0);
      _result = 31 * _result + (disableStatistics ? 1 : 0);
      _result = 31 * _result + (withEntryProcessor ? 1 : 0);
      _result = 31 * _result + (withWiredCache ? 1 : 0);
      _result = 31 * _result + (withForwardingAndAbstract ? 1 : 0);
      _result = 31 * _result + (keepDataAfterExpired ? 1 : 0);
      return _result;
    }

    @Override
    public String toString() {
      return
        "strictEviction=" + strictEviction +
        ", disableLastModified=" + disableLastModified +
        ", disableStatistics=" + disableStatistics +
        ", withEntryProcessor=" + withEntryProcessor +
        ", withWiredCache=" + withWiredCache +
        ", withForwardingAndAbstract=" + withForwardingAndAbstract +
        ", keepDataAfterExpired=" + keepDataAfterExpired;
    }

    static class Builder {

      Pars pars = new Pars();

      Pars build() { return pars; }

      Pars.Builder disableLastModified(boolean v) {
        pars.disableLastModified = v; return this;
      }

      Pars.Builder disableStatistics(boolean v) {
        pars.disableStatistics = v; return this;
      }

      Pars.Builder withEntryProcessor(boolean v) {
        pars.withEntryProcessor = v; return this;
      }

      Pars.Builder withWiredCache(boolean v) {
        pars.withWiredCache = v; return this;
      }

      Pars.Builder strictEviction(boolean v) {
        pars.strictEviction = v; return this;
      }

      Pars.Builder keepDataAfterExpired(boolean v) {
        pars.keepDataAfterExpired = v; return this;
      }

      public Builder withForwardingAndAbstract(final boolean v) {
        pars.withForwardingAndAbstract = v; return this;
        }
    }

    static class TestVariants implements Iterable<Pars> {

      @Override
      public Iterator<Pars> iterator() {
        return new Iterator<Pars>() {

          long currentVariant;
          long shiftRight;

          @Override
          public boolean hasNext() {
            return shiftRight == 0;
          }

          @Override
          public Pars next() {
            shiftRight = currentVariant;
            currentVariant++;
            return new Builder()
              .disableLastModified(nextBoolean())
              .disableStatistics(nextBoolean())
              .withEntryProcessor(nextBoolean())
              .withWiredCache(nextBoolean())
              .keepDataAfterExpired(nextBoolean())
              .build();
          }

          protected boolean nextBoolean() {
            boolean v = (shiftRight & 0x01) == 1L;
            shiftRight >>>= 1;
            return v;
          }

        };
      }

    }

  }

}
