package org.cache2k.test.core;

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

import org.cache2k.AbstractCache;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.ForwardingCache;
import org.cache2k.CacheClosedException;
import org.cache2k.core.api.InternalCache;
import org.cache2k.core.api.InternalCacheInfo;
import org.cache2k.event.CacheEntryExpiredListener;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.io.CacheLoaderException;
import org.cache2k.pinpoint.ExpectedException;
import org.cache2k.processor.EntryProcessingException;
import org.cache2k.processor.EntryProcessingResult;
import org.cache2k.processor.MutableCacheEntry;
import org.cache2k.testing.category.FastTests;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.*;
import static org.cache2k.expiry.ExpiryTimeValues.NOW;
import static org.cache2k.operation.CacheControl.of;

/**
 * Test basic cache operations on a shared cache in a simple configuration.
 * The cache may hold 1000 entries and has no expiry.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Category(FastTests.class) @RunWith(Parameterized.class)
public class BasicCacheOperationsWithoutCustomizationsTest {

  static final ConcurrentMap<Pars, Cache> PARS2CACHE = new ConcurrentHashMap<>();

  @SuppressWarnings("ThrowableInstanceNeverThrown")
  static final Exception OUCH = new Exception("ouch");
  static final Integer KEY = 1;
  static final Integer OTHER_KEY = 2;
  static final Integer VALUE = 1;
  static final Integer OTHER_VALUE = 2;

  static final long START_TIME = System.currentTimeMillis();

  static Pars.Builder pars() { return new Pars.Builder(); }

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    List<Object[]> l = new ArrayList<>();
    for (Pars o : new TestVariants()) {
      l.add(new Object[]{o});
    }
    return l;
  }

  /**
   * Used cache is a class field. We may subclass this class and run the tests with a different
   * configuration.
   */
  Cache<Integer, Integer> cache;

  Statistics statistics;

  Pars pars;

  boolean refreshTimeAvailable;

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
    refreshTimeAvailable = pars.recordRefreshTime;
  }

  protected Cache<Integer, Integer> createCache() {
    Cache2kBuilder b;
    if (pars.useObjectKey) {
      b = Cache2kBuilder.forUnknownTypes();
    } else {
      b = Cache2kBuilder.of(Integer.class, Integer.class);
    }
    b.name(this.getClass().getSimpleName() + "-" + pars.toString().replace('=', '~'))
      .entryCapacity(1000)
      .permitNullValues(true)
      .keepDataAfterExpired(pars.keepDataAfterExpired)
      .recordModificationTime(pars.recordRefreshTime)
      .disableStatistics(pars.disableStatistics);
    if (pars.keepExceptions) {
        b.resiliencePolicy(Constants.resilienceCacheExceptions());
    }
    if (pars.withExpiryAfterWrite) {
      b.expireAfterWrite(TestingParameters.MAX_FINISH_WAIT_MILLIS, TimeUnit.MILLISECONDS);
    } else {
      b.eternal(true);
    }
    if (pars.withWiredCache) {
      StaticUtil.enforceWiredCache(b);
    }
    if (pars.withExpiryListener) {
      b.addListener((CacheEntryExpiredListener) (cache, entry) -> {

      });
    }
    Cache<Integer, Integer> c = b.build();
    if (pars.withEntryProcessor) {
      c = new EntryProcessorCacheWrapper<>(c);
    }
    if (pars.withForwardingAndAbstract) {
      c = wrapAbstractAndForwarding(c);
    }
    return c;
  }

  /**
   * Wrap into a proxy and check the exceptions on the abstract cache and then use the
   * forwarding cache.
   */
  protected Cache<Integer, Integer> wrapAbstractAndForwarding(Cache<Integer, Integer> c) {
    Cache<Integer, Integer> forwardingCache = new ForwardingCache<Integer, Integer>() {
      @Override
      protected Cache<Integer, Integer> delegate() {
        return c;
      }
    };
    Cache<Integer, Integer> abstractCache = new AbstractCache<>();
    InvocationHandler h = (proxy, method, args) -> {
      try {
        method.invoke(abstractCache, args);
        if (!method.getName().equals("toString")) {
          fail("exception expected for method: " + method);
        }
      } catch (InvocationTargetException ex) {
        assertThat(ex.getTargetException().getClass())
          .as("expected exception")
          .isEqualTo(UnsupportedOperationException.class);
      }
      try {
        return method.invoke(forwardingCache, args);
      } catch (InvocationTargetException ex) {
        throw ex.getTargetException();
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
    return cache.requestInterface(InternalCache.class).getConsistentInfo();
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
    assertThat(cache)
      .as("Tests are not allowed to create private caches")
      .isSameAs(PARS2CACHE.get(pars));
    cache.requestInterface(InternalCache.class).checkIntegrity();
    cache.clear();
  }

  @AfterClass
  public static void tearDown() {
    for (Cache c : PARS2CACHE.values()) {
      of(c).destroy();
      c.close();
      assertThat(c.isClosed()).isTrue();
      assertThat(c.getName())
        .as("getName working in closed state")
        .isNotNull();
      String txt = c.toString();
      assertThat(txt).contains(c.getName());
      assertThat(txt).contains("closed");
      try {
        c.get(KEY);
        fail("CacheClosedException expected");
      } catch (CacheClosedException expected) {
      }
      try {
        c.peek(KEY);
        fail("CacheClosedException expected");
      } catch (CacheClosedException expected) {
      }
      try {
        c.put(KEY, VALUE);
        fail("CacheClosedException expected");
      } catch (CacheClosedException expected) {
      }
    }
  }

  /*
   * initial: Tests on the initial state of the cache.
   */

  @Test
  public void initial_Static_Stuff() {
    assertThat(cache.isClosed()).isFalse();
    assertThat(cache.getName()).isNotNull();
    assertThat(cache.getCacheManager()).isNotNull();
    assertThat(cache.toString()).isNotNull();
  }

  @Test
  public void initial_Iterator() {
    assertThat(cache.entries().iterator().hasNext()).isFalse();
  }

  @Test
  public void initial_Peek() {
    assertThat(cache.peek(KEY)).isNull();
    assertThat(cache.peek(OTHER_KEY)).isNull();
    assertThat(size()).isEqualTo(0);
  }

  @Test
  public void initial_Contains() {
    assertThat(cache.containsKey(KEY)).isFalse();
    assertThat(cache.containsKey(OTHER_KEY)).isFalse();
  }

  @Test
  public void initial_Get() {
    Object obj = cache.get(KEY);
    assertThat(obj).isNull();
  }

  @Test
  public void initial_Size() {
    assertThat(size()).isEqualTo(0);
    assertThat(cache.asMap().size()).isEqualTo(0);
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
    assertThat(cache.containsKey(KEY)).isTrue();
    assertThat(cache.get(KEY)).isEqualTo(VALUE);
    assertThat(cache.peek(KEY)).isEqualTo(VALUE);
    checkRefreshTime(cache.peekEntry(KEY));
  }

  void checkRefreshTime(CacheEntry<Integer, Integer> e) {
    long t = cache.invoke(e.getKey(), MutableCacheEntry::getModificationTime);
    if (refreshTimeAvailable) {
      assertThat(t)
        .as("Timestamp beyond start")
        .isGreaterThanOrEqualTo(START_TIME);
    } else {
      assertThat(t)
        .as("No time set")
        .isEqualTo(0);
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
    assertThat(cache.containsKey(KEY)).isTrue();
    assertThat(cache.get(KEY)).isEqualTo(OTHER_VALUE);
    assertThat(cache.peek(KEY)).isEqualTo(OTHER_VALUE);
  }

  @Test
  public void put_Null() {
    cache.put(KEY, null);
    assertThat(cache.containsKey(KEY)).isTrue();
    assertThat(cache.peek(KEY)).isNull();
    assertThat(cache.get(KEY)).isNull();
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
    cache.putAll(emptyMap());
    Map<Integer, Integer> map = new HashMap<>();
    map.put(KEY, VALUE);
    map.put(OTHER_KEY, null);
    cache.putAll(map);
    assertThat(cache.containsKey(KEY)).isTrue();
    assertThat(cache.containsKey(OTHER_KEY)).isTrue();
    assertThat(cache.peek(OTHER_KEY)).isNull();
    assertThat(cache.peek(KEY)).isEqualTo(VALUE);
    checkRefreshTime(cache.peekEntry(KEY));
  }

  @Test
  public void putAllChm() {
    Map<Integer, Integer> map = new ConcurrentHashMap<>();
    map.put(KEY, VALUE);
    map.put(OTHER_KEY, OTHER_VALUE);
    cache.putAll(map);
    assertThat(cache.containsKey(KEY)).isTrue();
    assertThat(cache.containsKey(OTHER_KEY)).isTrue();
    assertThat(cache.peek(OTHER_KEY)).isEqualTo(OTHER_VALUE);
    assertThat(cache.peek(KEY)).isEqualTo(VALUE);
    checkRefreshTime(cache.peekEntry(KEY));
  }

  @Test(expected = NullPointerException.class)
  public void putAll_NullKey() {
    Map<Integer, Integer> map = new HashMap<>();
    map.put(null, VALUE);
    cache.putAll(map);
  }

  /*
   * computeIfAbsent
   */

  @Test
  public void computeIfAbsent() {
    Integer v = cache.computeIfAbsent(KEY, key -> VALUE);
    statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .putCount.expect(1)
      .expectAllZero();
    assertThat(v).isEqualTo(VALUE);
    assertThat(cache.containsKey(KEY)).isTrue();
    assertThat(cache.peek(KEY)).isEqualTo(KEY);
    statistics()
      .getCount.expect(1)
      .expectAllZero();
    v = cache.computeIfAbsent(KEY, key -> OTHER_VALUE);
    statistics()
      .getCount.expect(1)
      .missCount.expect(0)
      .putCount.expect(0)
      .expectAllZero();
    assertThat(v).isEqualTo(VALUE);
    assertThat(cache.containsKey(KEY)).isTrue();
    assertThat(cache.peek(KEY)).isEqualTo(VALUE);
    checkRefreshTime(cache.peekEntry(KEY));
    cache.put(KEY, VALUE);
  }

  @Test
  public void computeIfAbsent_Null() {
    cache.computeIfAbsent(KEY, key -> null);
    assertThat(cache.containsKey(KEY)).isTrue();
    assertThat(cache.peek(KEY)).isNull();
    cache.computeIfAbsent(KEY, key -> 123);
    assertThat(cache.peek(KEY)).isNull();
  }

  @Test
  public void computeIfAbsent_Exception() {
    try {
      cache.computeIfAbsent(KEY, key ->  {
          throw new ExpectedException();
        });
      fail("ExpectedException expected");
    } catch (ExpectedException ex) {
    }
    statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .putCount.expect(0)
      .expectAllZero();
    assertThat(cache.containsKey(KEY)).isFalse();
  }

  @Test
  public void computeIfAbsent_RuntimeException() {
    try {
      cache.computeIfAbsent(KEY, key -> {
          throw new IllegalArgumentException("for testing");
      });
      fail("RuntimeException expected");
    } catch (RuntimeException ex) {
      assertThat(ex instanceof IllegalArgumentException).isTrue();
    }
    statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .putCount.expect(0)
      .expectAllZero();
    assertThat(cache.containsKey(KEY)).isFalse();
  }

  /*
   * peek
   */

  @Test
  public void peek_Miss() {
    assertThat(cache.peek(KEY)).isNull();
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
    assertThat(cache.peek(KEY)).isNotNull();
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
    cache.expireAt(KEY, NOW);
    assertThat(cache.peek(KEY)).isNull();
    statistics()
      .getCount.expect(pars.keepDataAfterExpired && pars.withWiredCache ? 2 : 1)
      .missCount.expect(1)
      .expiredCount.expect(1)
      .expectAllZero();
  }

  /*
   * get
   */

  @Test
  public void get_Miss() {
    assertThat(cache.get(KEY)).isNull();
    statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .expectAllZero();
  }

  @Test
  public void get_Hit() {
    cache.put(KEY, VALUE);
    statistics()
      .putCount.expect(1)
      .expectAllZero();
    assertThat(cache.get(KEY)).isNotNull();
    statistics()
      .getCount.expect(1)
      .missCount.expect(0)
      .expectAllZero();
  }

  @Test
  public void get_NotFresh() {
    cache.put(KEY, VALUE);
    statistics()
      .putCount.expect(1)
      .expectAllZero();
    cache.expireAt(KEY, NOW);
    assertThat(cache.get(KEY)).isNull();
    statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .expiredCount.expect(1)
      .expectAllZero();
  }

  /*
   * contains
   */

  @Test
  public void contains() {
    assertThat(cache.containsKey(KEY)).isFalse();
    cache.put(KEY, VALUE);
    assertThat(cache.containsKey(KEY)).isTrue();
  }

  @Test
  public void contains_Null() {
    assertThat(cache.containsKey(KEY)).isFalse();
    cache.put(KEY, null);
    assertThat(cache.containsKey(KEY)).isTrue();
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
    assertThat(cache.containsKey(KEY)).isTrue();
    assertThat(cache.peek(KEY)).isEqualTo(KEY);
    statistics()
      .getCount.expect(1)
      .expectAllZero();
    cache.putIfAbsent(KEY, OTHER_VALUE);
    statistics()
      .getCount.expect(1)
      .missCount.expect(0)
      .putCount.expect(0)
      .expectAllZero();
    assertThat(cache.containsKey(KEY)).isTrue();
    assertThat(cache.peek(KEY)).isEqualTo(VALUE);
    checkRefreshTime(cache.peekEntry(KEY));
  }

  @Test
  public void putIfAbsent_Null() {
    cache.putIfAbsent(KEY, null);
    assertThat(cache.containsKey(KEY)).isTrue();
    assertThat(cache.peek(KEY)).isNull();
  }

  /*
   * peekAndPut
   */

  @Test
  public void peekAndPut() {
    Integer v = cache.peekAndPut(KEY, VALUE);
    assertThat(v).isNull();
    statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .putCount.expect(1)
      .expectAllZero();
    v = cache.peekAndPut(KEY, VALUE);
    assertThat(v).isNotNull();
    assertThat(v).isEqualTo(VALUE);
    statistics()
      .getCount.expect(1)
      .missCount.expect(0)
      .putCount.expect(1)
      .expectAllZero();
    checkRefreshTime(cache.peekEntry(KEY));
  }

  @Test(expected = NullPointerException.class)
  public void peekAndPut_NullKey() {
    cache.peekAndPut(null, VALUE);
    statistics().expectAllZero();
  }

  @Test
  public void peekAndPut_Null() {
    Integer v = cache.peekAndPut(KEY, null);
    assertThat(v).isNull();
    assertThat(cache.containsKey(KEY)).isTrue();
    statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .putCount.expect(1)
      .expectAllZero();
    v = cache.peekAndPut(KEY, VALUE);
    assertThat(v).isNull();
    assertThat(cache.containsKey(KEY)).isTrue();
    statistics()
      .getCount.expect(1)
      .missCount.expect(0)
      .putCount.expect(1)
      .expectAllZero();
    v = cache.peekAndPut(KEY, null);
    assertThat(v).isNotNull();
    assertThat(v).isEqualTo(VALUE);
    v = cache.peekAndPut(KEY, null);
    assertThat(v).isNull();
  }

  @Test
  public void peekAndPut_Exception() {
    if (!pars.keepExceptions) {
      return;
    }
    assertThatCode(() -> {
      assignException(KEY);
      cache.peekAndPut(KEY, VALUE);
    }).isInstanceOf(CacheLoaderException.class);
  }

  @Test
  public void peekAndPut_NotFresh() {
    cache.put(KEY, VALUE);
    cache.expireAt(KEY, NOW);
    statistics().reset();
    Integer v = cache.peekAndPut(KEY, VALUE);
    assertThat(v).isNull();
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
    assertThat(v).isNull();
    assertThat(cache.containsKey(KEY)).isFalse();
    cache.put(KEY, VALUE);
    assertThat(cache.containsKey(KEY)).isTrue();
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
    assertThat(v).isNotNull();
    assertThat(cache.containsKey(KEY)).isFalse();
  }

  @Test
  public void peekAndRemove_Null() {
    cache.put(KEY, null);
    assertThat(cache.containsKey(KEY)).isTrue();
    Integer v = cache.peekAndRemove(KEY);
    assertThat(v).isNull();
    assertThat(cache.containsKey(KEY)).isFalse();
  }

  @Test(expected = NullPointerException.class)
  public void peekAndRemove_NullKey() {
    cache.peekAndRemove(null);
  }

  @Test
  public void peekAndRemove_Exception() {
    assignException(KEY);
    if (pars.keepExceptions) {
      try {
        cache.peekAndRemove(KEY);
        fail("exception expected");
      } catch (CacheLoaderException ex) {
      }
    } else {
      cache.peekAndRemove(KEY);
    }
  }

  @Test
  public void peekAndRemove_NotFresh() {
    cache.put(KEY, VALUE);
    cache.expireAt(KEY, NOW);
    statistics().reset();
    Integer v = cache.peekAndRemove(KEY);
    assertThat(v).isNull();
    statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .removeCount.expect(pars.keepDataAfterExpired && pars.withWiredCache ? 1 : 0)
      .expiredCount.expect(pars.keepDataAfterExpired && !pars.withWiredCache ? 1 : 0)
      .expectAllZero();
  }

  /*
   * peekAndReplace
   */

  @Test
  public void peekAndReplace() {
    Integer v = cache.peekAndReplace(KEY, VALUE);
    assertThat(v).isNull();
    assertThat(cache.containsKey(KEY)).isFalse();
    cache.put(KEY, VALUE);
    v = cache.peekAndReplace(KEY, OTHER_VALUE);
    assertThat(v).isNotNull();
    assertThat(cache.containsKey(KEY)).isTrue();
    assertThat(v).isEqualTo(VALUE);
    assertThat(cache.peek(KEY)).isEqualTo(OTHER_VALUE);
  }

  @Test
  public void peekAndReplace_Null() {
    Integer v = cache.peekAndReplace(KEY, null);
    assertThat(v).isNull();
    assertThat(cache.containsKey(KEY)).isFalse();
    cache.put(KEY, VALUE);
    v = cache.peekAndReplace(KEY, null);
    assertThat(v).isNotNull();
    assertThat(cache.containsKey(KEY)).isTrue();
    assertThat(v).isEqualTo(VALUE);
    assertThat(cache.peek(KEY)).isNull();
  }

  @Test(expected = NullPointerException.class)
  public void peekAndReplace_NullKey() {
    cache.peekAndReplace(null, VALUE);
  }

  @Test
  public void peekAndReplace_Exception() {
    if (!pars.keepExceptions) {
      return;
    }
    assertThatCode(() -> {
      assignException(KEY);
      cache.peekAndReplace(KEY, VALUE);
    }).isInstanceOf(CacheLoaderException.class);
  }

  /*
   * peekEntry
   */

  @Test
  public void peekEntry_Initial() {
    CacheEntry<Integer, Integer> e = cache.peekEntry(KEY);
    assertThat(e).isNull();
    assertThat(size()).isEqualTo(0);
  }

  @Test
  public void peekEntry() {
    CacheEntry<Integer, Integer> e = cache.peekEntry(KEY);
    assertThat(e).isNull();
    cache.put(KEY, VALUE);
    e = cache.peekEntry(KEY);
    assertThat(e.getKey()).isEqualTo(KEY);
    assertThat(e.getValue()).isEqualTo(VALUE);
    assertThat(e.getException()).isNull();
    checkRefreshTime(e);
  }

  @Test
  public void peekEntry_Null() {
    CacheEntry<Integer, Integer> e = cache.peekEntry(KEY);
    assertThat(e).isNull();
    cache.put(KEY, null);
    e = cache.peekEntry(KEY);
    assertThat(e.getKey()).isEqualTo(KEY);
    assertThat(e.getValue()).isNull();
    checkRefreshTime(e);
  }

  @Test(expected = NullPointerException.class)
  public void peekEntry_NullKey() {
    cache.peekEntry(null);
  }

  @Test
  public void peekEntry_Exception() {
    assignException(KEY);
    CacheEntry<Integer, Integer> e = cache.peekEntry(KEY);
    maybeEntryHasException(e, OUCH);
  }

  /*
   * getEntry
   */

  @Test
  public void getEntry() {
    cache.put(KEY, VALUE);
    CacheEntry<Integer, Integer> e = cache.getEntry(KEY);
    assertThat(e.getKey()).isEqualTo(KEY);
    assertThat(e.getValue()).isEqualTo(VALUE);
    assertThat(e.getException()).isNull();
    checkRefreshTime(e);
  }

  @Test
  public void getEntry_Null() {
    cache.put(KEY, null);
    CacheEntry<Integer, Integer> e = cache.getEntry(KEY);
    assertThat(e.getKey()).isEqualTo(KEY);
    assertThat(e.getValue()).isNull();
    checkRefreshTime(e);
  }

  @Test(expected = NullPointerException.class)
  public void getEntry_NullKey() {
    cache.getEntry(null);
  }

  @Test
  public void getEntry_Exception() {
    assignException(KEY);
    CacheEntry<Integer, Integer> e = cache.getEntry(KEY);
    maybeEntryHasException(e, OUCH);
  }

  private void maybeEntryHasException(CacheEntry<Integer, Integer> e, Throwable exception) {
    if (!pars.keepExceptions) {
      return;
    }
    try {
      e.getValue();
      fail("exception expected");
    } catch (CacheLoaderException ex) {
    }
    assertThat(e.getException()).isNotNull();
    assertThat(e.getException()).isEqualTo(exception);
    assertThat(exception).isEqualTo(e.getExceptionInfo().getException());
    assertThat(e.getExceptionInfo().getKey()).isNotNull();
    assertThat(e.getExceptionInfo().toString()).isNotNull();
  }

  /*
   * peek all
   */
  @Test
  public void peekAll() {
    Map<Integer, Integer> m = cache.peekAll(asList(KEY, OTHER_KEY));
    assertThat(m.size()).isEqualTo(0);
    assertThat(m.isEmpty()).isTrue();
    cache.put(KEY, VALUE);
    m = cache.peekAll(asList(KEY, OTHER_KEY));
    assertThat(m.size()).isEqualTo(1);
    assertThat(m.get(KEY)).isEqualTo(VALUE);
    assertThat(m.containsKey(KEY)).isTrue();
    assertThat(m.containsValue(VALUE)).isTrue();
    assertThat(m.get(OTHER_KEY)).isNull();
  }

  @Test
  public void peekAll_Null() {
    cache.put(KEY, null);
    Map<Integer, Integer> m = cache.peekAll(asList(KEY, OTHER_KEY));
    assertThat(m.size()).isEqualTo(1);
    assertThat(m.get(KEY)).isNull();
  }

  @Test(expected = NullPointerException.class)
  public void peekAll_NullKey() {
    cache.peekAll(asList(new Integer[]{null}));
  }

  @Test
  public void peekAll_Exception() {
    if (!pars.keepExceptions) {
      return;
    }
    assignException(KEY);
    Map<Integer, Integer> m = cache.peekAll(asList(KEY, OTHER_KEY));
    assertThat(m.size()).isEqualTo(1);
    assertThat(m.values().size()).isEqualTo(1);
    assertThat(m.keySet().size()).isEqualTo(1);
    assertThat(m.entrySet().size()).isEqualTo(1);
    try {
      m.get(KEY);
      fail("Exception expected");
    } catch (CacheLoaderException ex) {
    }
    Iterator<Integer> it = m.keySet().iterator();
    assertThat(it.hasNext()).isTrue();
    assertThat(it.next()).isEqualTo(KEY);
    assertThat(it.hasNext())
      .as("one entry")
      .isFalse();
    it = m.values().iterator();
    assertThat(it.hasNext()).isTrue();
    try {
      assertThat(it.next()).isEqualTo(KEY);
      fail("Exception expected");
    } catch (CacheLoaderException ex) {
    }
    Iterator<Map.Entry<Integer, Integer>> ei = m.entrySet().iterator();
    assertThat(ei.hasNext()).isTrue();
    Map.Entry<Integer, Integer> e = ei.next();
    assertThat(e.getKey()).isEqualTo(KEY);
    try {
      e.getValue();
      fail("Exception expected");
    } catch (CacheLoaderException ex) {
    }
  }

  @Test
  public void peekAll_MutationMethodsUnsupported() {
    cache.put(KEY, VALUE);
    Map<Integer, Integer> m = cache.peekAll(asList(KEY, OTHER_KEY));
    assertThat(m.size()).isEqualTo(1);
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
    Map<Integer, Integer> m = cache.getAll(asList(KEY, OTHER_KEY));
    assertThat(m.size()).isEqualTo(2);
    assertThat(m.get(KEY)).isEqualTo(VALUE);
    assertThat(m.containsKey(KEY)).isTrue();
    assertThat(m.containsValue(VALUE)).isTrue();
  }

  @Test(expected = NullPointerException.class)
  public void getAll_NullKey() {
    cache.getAll((asList(new Integer[]{null})));
  }

  @Test
  public void getAll_not_present_no_loader() {
    Map<Integer, Integer> m = cache.getAll(asList(KEY, OTHER_KEY));
    assertThat(m.size()).isEqualTo(0);
  }

  /*
   * remove(k)
   */

  @Test
  public void remove_NotExisting() {
    cache.remove(KEY);
    statistics().expectAllZero();
    assertThat(cache.containsKey(KEY)).isFalse();
  }

  @Test
  public void remove() {
    cache.put(KEY, VALUE);
    assertThat(cache.containsKey(KEY)).isTrue();
    statistics().reset();
    cache.remove(KEY);
    statistics().removeCount.expect(1).expectAllZero();
    assertThat(cache.containsKey(KEY)).isFalse();
  }

  @Test
  public void remove_Null() {
    cache.put(KEY, null);
    cache.remove(KEY);
    assertThat(cache.containsKey(KEY)).isFalse();
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
    statistics()
      .missCount.expect(0)
      .expectAllZero();
    assertThat(f).isFalse();
    assertThat(cache.containsKey(KEY)).isFalse();
    cache.put(KEY, VALUE);
    assertThat(cache.containsKey(KEY)).isTrue();
    f = cache.containsAndRemove(KEY);
    assertThat(f).isTrue();
    assertThat(cache.containsKey(KEY)).isFalse();
    statistics()
      .putCount.expect(1)
      .removeCount.expect(1)
      .expectAllZero();
  }

  @Test
  public void containsAndRemove_Null() {
    cache.put(KEY, null);
    boolean f = cache.containsAndRemove(KEY);
    assertThat(f).isTrue();
    assertThat(cache.containsKey(KEY)).isFalse();
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
    assertThat(f).isFalse();
    assertThat(cache.containsKey(KEY)).isFalse();
    statistics()
      .missCount.expect(1)
      .getCount.expect(1)
      .expectAllZero();
    cache.put(KEY, VALUE);
    assertThat(cache.containsKey(KEY)).isTrue();
    statistics().reset();
    f = cache.removeIfEquals(KEY, OTHER_VALUE);
    statistics()
      .missCount.expect(0)
      .getCount.expect(1)
      .expectAllZero();
    assertThat(f).isFalse();
    f = cache.removeIfEquals(KEY, VALUE);
    statistics()
      .missCount.expect(0)
      .getCount.expect(1)
      .removeCount.expect(1)
      .expectAllZero();
    assertThat(cache.containsKey(KEY)).isFalse();
    assertThat(f).isTrue();
    f = cache.removeIfEquals(KEY, VALUE);
    assertThat(f).isFalse();
  }

  @Test
  public void removeIfEquals_Null() {
    boolean f = cache.removeIfEquals(KEY, null);
    assertThat(f).isFalse();
    cache.put(KEY, null);
    f = cache.removeIfEquals(KEY, OTHER_VALUE);
    assertThat(f).isFalse();
    f = cache.removeIfEquals(KEY, null);
    assertThat(f).isTrue();
    assertThat(cache.containsKey(KEY)).isFalse();
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
    assertThat(cache.replaceIfEquals(KEY, VALUE, OTHER_VALUE)).isFalse();
    assertThat(cache.containsKey(KEY)).isFalse();
    cache.put(KEY, VALUE);
    assertThat(cache.replaceIfEquals(KEY, VALUE, OTHER_VALUE)).isTrue();
    assertThat(cache.peek(KEY)).isEqualTo(OTHER_VALUE);
  }

  @Test
  public void replaceIfEquals_Different() {
    cache.put(KEY, VALUE);
    assertThat(cache.peek(KEY)).isEqualTo(VALUE);
    assertThat(cache.replaceIfEquals(KEY, OTHER_VALUE, OTHER_VALUE)).isFalse();
    assertThat(cache.peek(KEY)).isEqualTo(VALUE);
  }

  @Test
  public void replaceIfEquals_NoMap() {
    cache.put(KEY, VALUE);
    assertThat(cache.replaceIfEquals(OTHER_KEY, OTHER_VALUE, OTHER_VALUE)).isFalse();
    assertThat(cache.peek(KEY)).isEqualTo(VALUE);
    assertThat(cache.peek(OTHER_KEY)).isNull();
    assertThat(cache.containsKey(OTHER_KEY)).isFalse();
  }

  @Test
  public void replaceIfEquals_Null() {
    boolean f = cache.replaceIfEquals(KEY, null, null);
    assertThat(f).isFalse();
    cache.put(KEY, null);
    f = cache.replaceIfEquals(KEY, null, VALUE);
    assertThat(f).isTrue();
    assertThat(cache.peek(KEY)).isEqualTo(VALUE);
    cache.replaceIfEquals(KEY, OTHER_VALUE, null);
    assertThat(cache.peek(KEY)).isEqualTo(VALUE);
    cache.replaceIfEquals(KEY, null, null);
    assertThat(cache.peek(KEY)).isEqualTo(VALUE);
    cache.replaceIfEquals(KEY, VALUE, null);
    assertThat(cache.containsKey(KEY)).isTrue();
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
    assertThat(f).isFalse();
    statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .expectAllZero();
    cache.put(KEY, VALUE);
    f = cache.replace(KEY, OTHER_VALUE);
    assertThat(f).isTrue();
    statistics()
      .getCount.expect(1)
      .missCount.expect(0)
      .putCount.expect(2)
      .expectAllZero();
    assertThat(cache.peek(KEY)).isEqualTo(OTHER_VALUE);
    statistics()
      .getCount.expect(1)
      .missCount.expect(0)
      .putCount.expect(0)
      .expectAllZero();
    checkRefreshTime(cache.peekEntry(KEY));
  }

  @Test
  public void replace_NoMap() {
    cache.put(KEY, VALUE);
    assertThat(cache.replace(OTHER_KEY, OTHER_VALUE)).isFalse();
    assertThat(cache.peek(KEY)).isEqualTo(VALUE);
    assertThat(cache.peek(OTHER_KEY)).isNull();
    assertThat(cache.containsKey(OTHER_KEY)).isFalse();
  }

  @Test
  public void replace_Null() {
    boolean f = cache.replace(KEY, null);
    assertThat(f).isFalse();
    cache.put(KEY, VALUE);
    f = cache.replace(KEY, null);
    assertThat(f).isTrue();
    assertThat(cache.peek(KEY)).isNull();
    assertThat(cache.containsKey(KEY)).isTrue();
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
    assertThat(cache.entries().iterator().hasNext()).isFalse();
    cache.put(KEY, VALUE);
    cache.put(OTHER_KEY, OTHER_VALUE);
    statistics().reset();
    Map<Integer, Integer> map = new HashMap<>();
    for (CacheEntry<Integer, Integer> ce : cache.entries()) {
      map.put(ce.getKey(), ce.getValue());
    }
    assertThat(map.size()).isEqualTo(2);
    assertThat(map.containsKey(KEY)).isTrue();
    assertThat(map.containsKey(OTHER_KEY)).isTrue();
    statistics().expectAllZero();
  }

  @Test(expected = NoSuchElementException.class)
  public void iterator_Next_Exception() {
    Iterator it = cache.entries().iterator();
    assertThat(it.hasNext()).isFalse();
    it.next();
  }

  /** Iteration stops if cleared. */
  @Test
  public void iterator_clear() {
    cache.put(KEY, VALUE);
    cache.put(OTHER_KEY, OTHER_VALUE);
    Iterator it = cache.entries().iterator();
    assertThat(it.hasNext()).isTrue();
    it.next();
    cache.clear();
    assertThat(it.hasNext()).isFalse();
  }

  /*
   * Entry processor
   */

  @Test
  public void invoke_exists() {
    cache.put(KEY, VALUE);
    boolean f = cache.invoke(KEY, MutableCacheEntry::exists);
    assertThat(f).isTrue();
  }

  @Test
  public void invoke_mutateWithExpiry() {
    cache.invoke(KEY, e -> {
      e.setValue(VALUE);
      e.setExpiryTime(ExpiryTimeValues.ETERNAL);
      return null;
    });
    checkRefreshTime(cache.getEntry(KEY));
  }

  @Test
  public void invoke_mutateWithImmediateExpiry() {
    cache.invoke(KEY, e -> {
      e.setValue(VALUE);
      e.setExpiryTime(NOW);
      return null;
    });
    assertThat(cache.containsKey(KEY)).isFalse();
  }

  static final long MILLIS_IN_FUTURE = (2345 - 1970) * 365L * 24 * 60 * 60 * 1000;

  @Test
  public void invoke_mutateWithRealExpiry() {
    boolean gotException = false;
    try {
      cache.invoke(KEY, e -> {
        e.setValue(VALUE);
        e.setExpiryTime(MILLIS_IN_FUTURE);
        return null;
      });
    } catch (IllegalArgumentException ex) {
      gotException = true;
    }
    boolean resilienceEnabledTimer = pars.keepExceptions;
    if (pars.withExpiryAfterWrite || resilienceEnabledTimer) {
      assertThat(cache.containsKey(KEY)).isTrue();
    } else {
      assertThat(gotException).isTrue();
    }
  }

  @Test
  public void expireAt_mutateWithRealExpiry() {
    boolean gotException = false;
    try {
      cache.put(KEY, VALUE);
      cache.expireAt(KEY, MILLIS_IN_FUTURE);
    } catch (IllegalArgumentException ex) {
      gotException = true;
    }
    boolean resilienceEnabledTimer = pars.keepExceptions;
    if (pars.withExpiryAfterWrite || resilienceEnabledTimer) {
      assertThat(cache.containsKey(KEY)).isTrue();
    } else {
      assertThat(gotException).isTrue();
    }
  }

  @Test
  public void invokeAll() {
    cache.put(KEY, VALUE);
    Map<Integer, EntryProcessingResult<Boolean>> res =
      cache.invokeAll(cache.keys(), MutableCacheEntry::exists);
    assertThat(res.size()).isEqualTo(1);
    assertThat(res.get(KEY).getException()).isNull();
    assertThat(res.get(KEY).getResult()).isTrue();
  }

  @Test
  public void invoke_NullKey() {
    assertThatCode(() -> cache.invoke(null, entry -> null))
      .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void invoke_NullProcessor() {
    assertThatCode(() -> cache.invoke(KEY, null))
      .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void invoke_NullValue() {
    cache.invoke(KEY, entry -> entry.setValue(null));
  }

  /*
   * Misc
   */

  @Test
  public void removeAll() {
    cache.put(KEY, VALUE);
    cache.put(OTHER_KEY, OTHER_VALUE);
    cache.removeAll();
    assertThat(cache.keys().iterator().hasNext()).isFalse();
  }

  @Test
  public void removeAllSortCircuit() {
    cache.put(KEY, VALUE);
    cache.put(OTHER_KEY, OTHER_VALUE);
    cache.removeAll(cache.keys());
    assertThat(cache.keys().iterator().hasNext()).isFalse();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void loadAll() {
    cache.loadAll(asList(KEY, OTHER_KEY));
  }

  @Test(expected = UnsupportedOperationException.class)
  public void reloadAll() {
    cache.reloadAll(asList(KEY, OTHER_KEY));
  }

  @Test
  public void getEntryState() {
    if (!(cache instanceof InternalCache)) {
      return;
    }
    InternalCache<Integer, Integer> c = (InternalCache<Integer, Integer>) cache;
    String s = c.getEntryState(KEY);
    assertThat(s).isNull();
    cache.put(KEY, VALUE);
    s = c.getEntryState(KEY);
    assertThat(s).isNotNull();
  }

  @Test
  public void getEntryState_Exception() {
    if (!pars.keepExceptions) {
      return;
    }
    if (!(cache instanceof InternalCache)) {
      return;
    }
    Integer k = KEY;
    assignException(k);
    InternalCache<Integer, Integer> c = (InternalCache<Integer, Integer>) cache;
    String s = c.getEntryState(KEY);
    assertThat(s.contains("exception=")).isTrue();
  }

  private void assignException(Integer key) {
    cache.invoke(key, e -> {
      e.setException(OUCH);
      return null;
    });
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void requestInterface() {
    assertThatCode(() -> cache.requestInterface(Integer.class))
      .isInstanceOf(UnsupportedOperationException.class);
    assertThat(cache.requestInterface(Map.class) instanceof Map).isTrue();
  }

  static class Pars {

    boolean strictEviction = false;
    boolean recordRefreshTime = false;
    boolean disableStatistics = false;
    boolean withEntryProcessor = false;
    boolean withWiredCache = false;
    boolean withForwardingAndAbstract = false;
    boolean keepDataAfterExpired = false;
    boolean withExpiryAfterWrite = false;
    boolean keepExceptions = false;
    boolean useObjectKey = false;
    boolean withExpiryListener = false;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Pars pars = (Pars) o;

      if (strictEviction != pars.strictEviction) return false;
      if (recordRefreshTime != pars.recordRefreshTime) return false;
      if (disableStatistics != pars.disableStatistics) return false;
      if (withEntryProcessor != pars.withEntryProcessor) return false;
      if (withWiredCache != pars.withWiredCache) return false;
      if (withForwardingAndAbstract != pars.withForwardingAndAbstract) return false;
      if (keepDataAfterExpired != pars.keepDataAfterExpired) return false;
      if (keepExceptions != pars.keepExceptions) return false;
      if (withExpiryAfterWrite != pars.withExpiryAfterWrite) return false;
      if (useObjectKey != pars.useObjectKey) return false;
      return withExpiryListener == pars.withExpiryListener;
    }

    @Override
    public int hashCode() {
      int result = (strictEviction ? 1 : 0);
      result = 31 * result + (recordRefreshTime ? 1 : 0);
      result = 31 * result + (disableStatistics ? 1 : 0);
      result = 31 * result + (withEntryProcessor ? 1 : 0);
      result = 31 * result + (withWiredCache ? 1 : 0);
      result = 31 * result + (withForwardingAndAbstract ? 1 : 0);
      result = 31 * result + (keepDataAfterExpired ? 1 : 0);
      result = 31 * result + (keepExceptions ? 1 : 0);
      result = 31 * result + (withExpiryAfterWrite ? 1 : 0);
      result = 31 * result + (useObjectKey ? 1 : 0);
      result = 31 * result + (withExpiryListener ? 1 : 0);
      return result;
    }

    @Override
    public String toString() {
      return
        "strict=" + strictEviction +
        ", recordRefresh=" + recordRefreshTime +
        ", disableStats=" + disableStatistics +
        ", entryProcessor=" + withEntryProcessor +
        ", wired=" + withWiredCache +
        ", forwarding=" + withForwardingAndAbstract +
        ", keepData=" + keepDataAfterExpired +
        ", keepExceptions=" + keepExceptions +
        ", expiry=" + withExpiryAfterWrite +
        ", useObjectKey=" + useObjectKey +
        ", withExpiryListener=" + withExpiryListener;
    }

    @SuppressWarnings({"SameParameterValue"})
    static class Builder {

      Pars pars = new Pars();

      Pars build() { return pars; }

      Pars.Builder recordRefreshTime(boolean v) {
        pars.recordRefreshTime = v; return this;
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

      Pars.Builder keepExceptions(boolean v) {
        pars.keepExceptions = v; return this;
      }

      public Builder withForwardingAndAbstract(boolean v) {
        pars.withForwardingAndAbstract = v; return this;
        }

      public Builder withExpiryAfterWrite(boolean v) {
        pars.withExpiryAfterWrite = v; return this;
      }

      public Builder useObjectKey(boolean v) {
        pars.useObjectKey = v; return this;
      }

      public Builder withExpiryListener(boolean v) {
        pars.withExpiryListener = v; return this;
      }

    }

  }

  static class TestVariants extends HashSet<Pars> {

    {
      addAll(new VariantIterator<Pars>() {

        @Override
        protected Pars generate() {
          return new Pars.Builder()
            .recordRefreshTime(nextBoolean())
            .disableStatistics(nextBoolean())
            .withWiredCache(nextBoolean())
            .keepDataAfterExpired(nextBoolean())
            .keepExceptions(nextBoolean())
            .withExpiryAfterWrite(nextBoolean())
            .build();
        }

      });
      add(pars().withEntryProcessor(true).build());
      add(pars().withEntryProcessor(true).withWiredCache(true).build());
      add(pars().useObjectKey(true).build());
      add(pars().useObjectKey(true).withWiredCache(true).build());
      add(pars().withForwardingAndAbstract(true).build());
      add(pars().withExpiryListener(true).build());
      add(pars().strictEviction(true).build());
    }

  }

  abstract static class VariantIterator<T> extends AbstractCollection<T> {

    private long variant = 0;
    private long shiftRight;
    private final Set<T> collection = new HashSet<>();

    {
      while (shiftRight == 0) {
        shiftRight = variant;
        variant++;
        collection.add(generate());
      }
    }

    protected final boolean nextBoolean() {
      boolean v = (shiftRight & 0x01) == 1L;
      shiftRight >>>= 1;
      return v;
    }

    protected abstract T generate();

    @Override
    public Iterator<T> iterator() {
      return collection.iterator();
    }

    @Override
    public int size() {
      return collection.size();
    }

  }

}
