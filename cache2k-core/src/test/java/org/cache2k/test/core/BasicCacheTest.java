package org.cache2k.test.core;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
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
import org.cache2k.CacheException;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.integration.CacheLoader;
import org.cache2k.integration.CacheLoaderException;
import org.cache2k.expiry.ValueWithExpiryTime;
import org.cache2k.core.InternalCacheInfo;
import org.cache2k.integration.ExceptionInformation;
import org.cache2k.integration.ResiliencePolicy;
import org.cache2k.processor.EntryProcessor;
import org.cache2k.processor.MutableCacheEntry;
import org.cache2k.test.util.TestingBase;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.*;

/**
 * A mix of cache tests
 *
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class BasicCacheTest extends TestingBase {

  { enableFastClock(); }

  @Test
  public void testPeekAndReplaceWoExisting() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    assertNull(c.peekAndReplace(1, 1));
    assertFalse(c.containsKey(1));
    assertNull(c.peek(1));
  }

  @Test
  public void testPeekAndReplaceWoExistingTwice() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    assertNull(c.peekAndReplace(1, 1));
    assertFalse(c.containsKey(1));
    assertNull(c.peek(1));
    assertNull(c.peekAndReplace(1, 1));
  }

  @Test
  public void testPeekAndReplaceWithExisting() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    assertNull(c.peekAndReplace(1, 1));
    c.put(1, 1);
    assertEquals((Integer) 1, c.peekAndReplace(1, 2));
    assertTrue(c.containsKey(1));
    assertEquals((Integer) 2, c.peek(1));
  }

  @Test
  public void testIteratorOnEmptyCacheCallHasNext() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    assertFalse(c.entries().iterator().hasNext());
  }

  @Test(expected = NoSuchElementException.class)
  public void testIteratorOnEmptyCacheCallNext() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    c.entries().iterator().next();
  }

  @Test
  public void testIteratorOnOneEntryCallNext() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    c.put(47, 11);
    assertEquals((Integer) 11, c.entries().iterator().next().getValue());
  }

  @Test
  public void testReplace() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    assertFalse(c.replace(1, 1));
    c.put(1, 1);
    assertTrue(c.replace(1, 2));
    assertTrue(c.replace(1, 1));
    assertFalse(c.replace(2, 2));
  }

  @Test
  public void testCompareAndReplace() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    assertFalse(c.replaceIfEquals(1, 1, 2));
    c.put(1, 1);
    assertTrue(c.replaceIfEquals(1, 1, 2));
    assertTrue(c.replaceIfEquals(1, 2, 1));
    assertFalse(c.replaceIfEquals(1, 2, 3));
  }

  @Test
  public void testPeekAndRemove() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    c.put(1, 1);
    assertEquals((Integer) 1, c.peekAndRemove(1));
    assertNull(c.peekAndRemove(1));
  }

  @Test
  public void testCompareAndRemove() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    c.put(1, 1);
    assertFalse(c.removeIfEquals(1, 2));
    assertTrue(c.removeIfEquals(1, 1));
  }

  @Test
  public void testPutAndPeek() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    c.put(1, 1);
    assertEquals((Integer) 1, c.peek(1));
  }

  @Test
  public void testPeekAndPut() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    c.put(1, 1);
    assertEquals((Integer) 1, c.peekAndPut(1, 2));
    assertEquals((Integer) 2, c.peekAndPut(1, 1));
  }

  @Test
  public void testBulkGetAllReadThrough() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, new IdentIntSource(), 100, -1);
    Set<Integer> _requestedKeys = new HashSet<Integer>(Arrays.asList(2, 3));
    Map<Integer, Integer> m = c.getAll(_requestedKeys);
    assertEquals(2, m.size());
    assertEquals(2, (int) m.get((int) 2));
    assertEquals(3, (int) m.get((int) 3));
    assertNull(m.get(47));
  }

  @Test
  public void testNBulkPeekAll() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    c.put(1, 1);
    c.put(2, 2);
    Set<Integer> _requestedKeys = new HashSet<Integer>(Arrays.asList(2, 3));
    Map<Integer, Integer> m = c.peekAll(_requestedKeys);
    assertEquals(1, m.size());
    assertEquals(2, (int) m.get((int) 2));
  }

  @Test
  public void testPutWithFaultyKeyimplementation() {
    Cache<FaultyKey, String> c =
      freshCache(FaultyKey.class, String.class, null, 100, -1);
    c.put(new FaultyKey("abc"), "abc");
    assertNull(c.peekEntry(new FaultyKey("abc")));
  }

  /**
   * Key that is never equals....
   */
  static class FaultyKey {

    String value;

    FaultyKey(String value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      return false;
    }

    @Override
    public int hashCode() {
      return value != null ? value.hashCode() : 0;
    }
  }

  @Test
  public void testClear0() {
    Cache<String, String> c =
      freshCache(String.class, String.class, null, 100, -1);
    c.clear();
  }

  @Test
  public void testClear1() {
    CacheLoader<String, String> cs = new CacheLoader<String, String>() {
      @Override
      public String load(String o) {
        return o;
      }
    };
    Cache<String, String> c =
      freshCache(String.class, String.class, cs, 100, -1);
    c.get("xy");
    c.clear();
  }

  @Test
  public void testPutTwiceEternal() {
    Cache<String, String> c =
      freshCache(String.class, String.class, null, 100, -1);
    c.put("xy", "foo");
    c.put("xy", "foo");
  }

  @Test
  public void testPutTwiceExpiry8Min() {
    Cache<String, String> c =
      freshCache(String.class, String.class, null, 100, 60 * 8);
    c.put("xy", "foo");
    c.put("xy", "foo");
  }

  @Test
  public void testInitRemove() {
    Cache<String, String> c =
      freshCache(String.class, String.class, null, 100, -1);
    c.remove("test");
  }

  @Test
  public void testEntryExpiryCalculatorNoCache() {
    MyExpiryPolicy _expiryCalc = new MyExpiryPolicy();
    Cache<String, String> c = builder(String.class, String.class)
        .expireAfterWrite(0, TimeUnit.MILLISECONDS)
        .expiryPolicy(_expiryCalc)
        .build();
    c.put("eternal", "eternal");
    c.put("nocache", "nocache");
    c.put("1234", "foo");
    assertNull(c.peek("1234"));
    assertNull(c.peek("nocache"));
    assertNull(c.peek("eternal"));
  }

  @Test
  public void testEntryExpiryCalculator8Min() {
    MyExpiryPolicy _expiryCalc = new MyExpiryPolicy();
    Cache<String, String> c = builder(String.class, String.class)
        .expireAfterWrite(8, TimeUnit.MINUTES)
        .expiryPolicy(_expiryCalc)
        .build();
    c.put("eternal", "foo");
    c.put("nocache", "foo");
    c.put("8888", "foo");
    assertNotNull(c.peek("eternal"));
    assertNull(c.peek("nocache"));
    assertNotNull(c.peek("8888"));
  }

  @Test
  public void testEntryExpiryCalculator8MinKeepOldEntry() {
    MyExpiryPolicy _expiryCalc = new MyExpiryPolicy();
    Cache<String, String> c = builder(String.class, String.class)
      .expireAfterWrite(8, TimeUnit.MINUTES)
      .expiryPolicy(_expiryCalc)
      .keepDataAfterExpired(true)
      .build();
    c.put("nocache", "foo");
    c.put("nocache", "foo");
    assertEquals(1, _expiryCalc.oldEntrySeen.get());
  }

  @Test
  public void testPeekGetPutSequence() {
    MyExpiryPolicy _expiryCalc = new MyExpiryPolicy();
    Cache<String, String> c = builder(String.class, String.class)
        .expireAfterWrite(8, TimeUnit.MINUTES)
        .expiryPolicy(_expiryCalc)
        .build();
    assertFalse(c.containsKey("88"));
    String s = c.peekAndReplace("88", "ab");
    assertNull("no existing value", s);
    assertFalse("still no mapping", c.containsKey("88"));
    c.put("88", "ab");
    assertEquals(1, _expiryCalc.key2count.size());
  }

  public static class MyExpiryPolicy implements ExpiryPolicy<String, String> {
    public final Map<String, AtomicInteger> key2count = new HashMap<String, AtomicInteger>();
    public final AtomicInteger oldEntrySeen = new AtomicInteger();
    @Override
    public long calculateExpiryTime(String _key, String _value, long _loadTime,
                                    CacheEntry<String, String> _oldEntry) {
      AtomicInteger _count;
      synchronized (key2count) {
        _count = key2count.get(_key);
        if (_count == null) {
          _count = new AtomicInteger();
          key2count.put(_key, _count);
        }
        _count.incrementAndGet();
      }

      if (_oldEntry != null) {
        oldEntrySeen.incrementAndGet();
      }

      if (_key.startsWith("nocache")) {
        return 0;
      }
      if (_key.startsWith("eternal")) {
        return Long.MAX_VALUE;
      }
      return _loadTime + Integer.parseInt(_key);
    }
  }

  @Test
  public void testValueWithExpiryTime() {
    Cache<String, MyValueWithExpiryTime> c =
      builder(String.class, MyValueWithExpiryTime.class)
        .eternal(true)
        .build();
    c.put("nocache", new MyValueWithExpiryTime(0));
    final long _DISTANT_FUTURE = Long.MAX_VALUE - 42;
    c.put("cache", new MyValueWithExpiryTime(_DISTANT_FUTURE));
    assertNull(c.peek("nocache"));
    assertNotNull(c.peek("cache"));
  }

  static class MyValueWithExpiryTime implements ValueWithExpiryTime {

    long expiryTime;

    MyValueWithExpiryTime(long v) {
      this.expiryTime = v;
    }

    @Override
    public long getCacheExpiryTime() {
      return expiryTime;
    }
  }

  @Test
  public void testExceptionExpiryCalculatorNoCache() {
    MyResiliencePolicy _expiryCalc = new MyResiliencePolicy();
    Cache<String, String> c = builder(String.class, String.class)
      .expireAfterWrite(0, TimeUnit.MILLISECONDS)
      .loader(new AlwaysExceptionStringSource())
      .resiliencePolicy(_expiryCalc)
      .keepDataAfterExpired(true)
      .build();
    int _exceptionCount = 0;
    final String _ETERNAL = "eternal";
    try {
      c.get(_ETERNAL);
    } catch (CacheLoaderException e) {
      _exceptionCount++;
    }
    assertNull(
        "caching is off, expiry calculator never asked",
        _expiryCalc.key2count.get(_ETERNAL));
    assertEquals(1, _exceptionCount);
    try {
      Object o = c.peek(_ETERNAL);
      assertNull(o);
    } catch (CacheLoaderException e) {
      _exceptionCount++;
    }
    assertEquals("eternal, but cache is switched off, no mapping at all", 1, _exceptionCount);
    try {
      c.get(_ETERNAL);
    } catch (CacheLoaderException e) {
      _exceptionCount++;
    }
    assertEquals(2, _exceptionCount);
    assertNull(
        "caching is off, expiry calculator never asked",
        _expiryCalc.key2count.get(_ETERNAL));
  }

  @Test
  public void testResiliencePolicy8Min() {
    MyResiliencePolicy _expiryCalc = new MyResiliencePolicy();
    Cache<String, String> c = builder(String.class, String.class)
      .expireAfterWrite(8, TimeUnit.MINUTES)
      .loader(new AlwaysExceptionStringSource())
      .resiliencePolicy(_expiryCalc)
      .build();
    int _exceptionCount = 0;
    final String _ETERNAL = "eternal";
    try {
      c.get(_ETERNAL);
    } catch (CacheLoaderException e) {
      _exceptionCount++;
    }
    assertEquals(1, _exceptionCount);
    try {
      c.peek(_ETERNAL);
    } catch (CacheLoaderException e) {
      _exceptionCount++;
    }
    assertEquals("expect exception is cached", 2, _exceptionCount);
    try {
      c.get(_ETERNAL);
    } catch (CacheLoaderException e) {
      _exceptionCount++;
    }
    assertEquals(3, _exceptionCount);
    assertNotNull("calculator was called", _expiryCalc.key2count.get(_ETERNAL));
    assertEquals("fetched once since cached",
      1, _expiryCalc.key2count.get(_ETERNAL).get());
    final String _NO_CACHE = "nocache";
    _exceptionCount = 0;
    try {
      c.get(_NO_CACHE);
    } catch (CacheLoaderException e) {
      _exceptionCount++;
    }
    assertEquals(1, _exceptionCount);
    try {
      c.peek(_NO_CACHE);
    } catch (CacheLoaderException e) {
      _exceptionCount++;
    }
    assertEquals("expect exception is not cached", 1, _exceptionCount);
  }

  @Test
  public void testTimestampIsSetForException() {
    OccasionalExceptionSource src = new OccasionalExceptionSource();
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(0, TimeUnit.MINUTES)
      .retryInterval(8, TimeUnit.MINUTES)
      .resilienceDuration(33, TimeUnit.HOURS)
      .recordRefreshedTime(true)
      .keepDataAfterExpired(true)
      .loader(src)
      .build();
    long t0 = millis();
    try {
      c.get(1);
      fail("exception expected");
    } catch (CacheLoaderException e) {
    }
    checkExistingAndTimeStampGreaterOrEquals(c, 1, t0);
    try {
      c.get(2);
      final long refreshedBefore = millis();
      assertFalse("entry disappears since expiry=0", c.containsKey(2));
      assertEquals("entry has no refreshed time since suppressed", 0, getRefreshedTimeViaEntryProcessor(c, 2));
      sleep(3);
      c.get(2);
      c.invoke(2, new EntryProcessor<Integer, Integer, Long>() {
        @Override
        public Long process(final MutableCacheEntry<Integer, Integer> e) {
          assertNull("exception suppressed", e.getException());
          assertTrue("entry present", e.exists());
          assertThat("refresh time of entry, not when exception happened",
            e.getRefreshedTime(),
            lessThanOrEqualTo(refreshedBefore));
          return null;
        }
      });

    } catch (CacheLoaderException e) {
      fail("no exception expected");
    }
    InternalCacheInfo inf = getInfo();
    assertEquals(1, inf.getSuppressedExceptionCount());
    assertEquals(2, inf.getLoadExceptionCount());
    assertNotNull(src.key2count.get(2));
    assertEquals(2, src.key2count.get(2).get());
  }

  void checkExistingAndTimeStampGreaterOrEquals(Cache<Integer, Integer> c, int key, final long t) {
    assertTrue(c.containsKey(key));
    c.invoke(key, new EntryProcessor<Integer, Integer, Long>() {
      @Override
      public Long process(final MutableCacheEntry<Integer, Integer> e) throws Exception {
        assertTrue("entry present", e.exists());
        assertThat(e.getRefreshedTime(), greaterThanOrEqualTo(t));
        return null;
      }
    });
  }

  long getRefreshedTimeViaEntryProcessor(Cache<Integer, Integer> c, int key) {
    return c.invoke(key, new EntryProcessor<Integer, Integer, Long>() {
      @Override
      public Long process(final MutableCacheEntry<Integer, Integer> e) throws Exception {
        return e.getRefreshedTime();
      }
    });
  }

  @Test
  public void testExceptionExpirySuppressTwice() {
    OccasionalExceptionSource src = new PatternExceptionSource(false, true, true);
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(0, TimeUnit.MINUTES)
      .retryInterval(8, TimeUnit.MINUTES)
      .resilienceDuration(33, TimeUnit.HOURS)
      .keepDataAfterExpired(true)
      .loader(src)
      .build();
    int _exceptionCount = 0;
    try {
      c.get(1);
    } catch (CacheLoaderException e) {
      _exceptionCount++;
    }
    assertEquals("no exception", 0, _exceptionCount);
    _exceptionCount = 0;
    try {
      c.get(2); // value is loaded
      c.get(2); // exception gets suppressed
      c.get(2); // cache hit since exception expiry is active
    } catch (CacheLoaderException e) {
      _exceptionCount++;
    }
    InternalCacheInfo inf = getInfo();
    assertEquals("no exception expected", 0, _exceptionCount);
    assertEquals(1, inf.getSuppressedExceptionCount());
    assertEquals(1, inf.getLoadExceptionCount());
    assertNotNull(src.key2count.get(2));
    assertEquals(2, src.key2count.get(2).get());
  }

  public static class MyResiliencePolicy extends ResiliencePolicy<String, String> {

    public final Map<String, AtomicInteger> key2count = new HashMap<String, AtomicInteger>();

    public long calculateExpiryTime(String key, Throwable _throwable, long _fetchTime) {
      AtomicInteger _count;
      synchronized (key2count) {
        _count = key2count.get(key);
        if (_count == null) {
          _count = new AtomicInteger();
          key2count.put(key, _count);
        }
        _count.incrementAndGet();
      }
      if (key.startsWith("nocache")) {
        return 0;
      }
      if (key.startsWith("eternal")) {
        return Long.MAX_VALUE;
      }
      return _fetchTime + Integer.parseInt(key);
    }

    @Override
    public long retryLoadAfter(final String key, final ExceptionInformation exceptionInformation) {
      return calculateExpiryTime(key, exceptionInformation.getException(), exceptionInformation.getLoadTime());
    }

    @Override
    public long suppressExceptionUntil(final String key, final ExceptionInformation exceptionInformation, final CacheEntry<String, String> cachedContent) {
      return calculateExpiryTime(key, exceptionInformation.getException(), exceptionInformation.getLoadTime());
    }
  }

  private void freshCacheForInfoTest() {
    freshCache(String.class, String.class, null, 100, -1);
  }

  @Test
  public void testInfoPropertyStarted() {
    long t = millis();
    freshCacheForInfoTest();
    assertTrue("started set", getInfo().getStartedTime() >= t);
  }

  @Test
  public void testInfoPropertyCleared() {
    long t = millis();
    freshCacheForInfoTest();
    assertEquals("not yet cleared", 0, getInfo().getClearedTime());
    cache.clear();
    assertTrue("cleared set", getInfo().getClearedTime() >= t);
  }

  @Test(expected = CacheException.class)
  public void testExpiryCalculatorThrowsException() {
    Cache<String, String> c =
      builder(String.class, String.class)
        .expiryPolicy(new ExpiryPolicyThrowsException())
        .build();
    c.put("123", "hello");
    c.put("123", "boom");
  }

  public static class AlwaysExceptionSource extends CacheLoader<Integer, Integer> {
    @Override
    public Integer load(Integer o) {
      throw new RuntimeException("always");
    }
  }

  /**
   * Throws an exception
   */
  public static class OccasionalExceptionSource extends CacheLoader<Integer, Integer> {

    public final Map<Integer, AtomicInteger> key2count = new HashMap<Integer, AtomicInteger>();

    protected void maybeThrowException(Integer _key, int _count) {
      if (_count % _key == 0) {
        throw new RuntimeException("every " + _key + " times");
      }
    }

    @Override
    public Integer load(Integer _key) throws Exception {
      AtomicInteger _count;
      synchronized (key2count) {
        _count = key2count.get(_key);
        if (_count == null) {
          _count = new AtomicInteger();
          key2count.put(_key, _count);
        }
        _count.incrementAndGet();
      }
      maybeThrowException(_key, _count.get());
      return _key;
    }

  }

  public static class PatternExceptionSource extends OccasionalExceptionSource {

    boolean[] pattern;

    public PatternExceptionSource(boolean... _pattern) {
      pattern = _pattern;
    }

    @Override
    protected void maybeThrowException(Integer _key, int _count) {
      boolean _willThrow = pattern[(_count - 1) % pattern.length];
      if (_willThrow) {
        throw new RuntimeException("Access number " + _count);
      }
    }

  }

  public static class AlwaysExceptionStringSource extends CacheLoader<String, String> {

    @Override
    public String load(String o) {
      throw new RuntimeException("always");
    }
  }

  public static class ExpiryPolicyThrowsException implements ExpiryPolicy<String, String> {
    @Override
    public long calculateExpiryTime(String _key, String _value, long _loadTime, CacheEntry<String, String> _oldEntry) {
      if (_value != null && _value.startsWith("boo")) {
        throw new RuntimeException("got value: " + _value);
      }
      return Long.MAX_VALUE;
    }
  }

}
