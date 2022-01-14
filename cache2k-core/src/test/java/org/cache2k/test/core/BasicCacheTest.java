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

import org.assertj.core.api.Assertions;
import org.cache2k.Cache;
import org.cache2k.CacheEntry;
import org.cache2k.CacheException;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.io.CacheLoader;
import org.cache2k.io.CacheLoaderException;
import org.cache2k.expiry.ValueWithExpiryTime;
import org.cache2k.core.api.InternalCacheInfo;
import org.cache2k.io.LoadExceptionInfo;
import org.cache2k.io.ResiliencePolicy;
import org.cache2k.processor.EntryProcessor;
import org.cache2k.processor.MutableCacheEntry;
import org.cache2k.test.util.TestingBase;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Long.MAX_VALUE;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.cache2k.test.core.Constants.resilienceCacheAndSuppressExceptions;
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
    assertThat(c.peekAndReplace(1, 1)).isNull();
    assertThat(c.containsKey(1)).isFalse();
    assertThat(c.peek(1)).isNull();
  }

  @Test
  public void testPeekAndReplaceWoExistingTwice() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    assertThat(c.peekAndReplace(1, 1)).isNull();
    assertThat(c.containsKey(1)).isFalse();
    assertThat(c.peek(1)).isNull();
    assertThat(c.peekAndReplace(1, 1)).isNull();
  }

  @Test
  public void testPeekAndReplaceWithExisting() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    assertThat(c.peekAndReplace(1, 1)).isNull();
    c.put(1, 1);
    assertThat(c.peekAndReplace(1, 2)).isEqualTo((Integer) 1);
    assertThat(c.containsKey(1)).isTrue();
    assertThat(c.peek(1)).isEqualTo((Integer) 2);
  }

  @Test
  public void testIteratorOnEmptyCacheCallHasNext() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    assertThat(c.entries().iterator().hasNext()).isFalse();
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
    assertThat(c.entries().iterator().next().getValue()).isEqualTo((Integer) 11);
  }

  @Test
  public void testReplace() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    assertThat(c.replace(1, 1)).isFalse();
    c.put(1, 1);
    assertThat(c.replace(1, 2)).isTrue();
    assertThat(c.replace(1, 1)).isTrue();
    assertThat(c.replace(2, 2)).isFalse();
  }

  @Test
  public void testCompareAndReplace() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    assertThat(c.replaceIfEquals(1, 1, 2)).isFalse();
    c.put(1, 1);
    assertThat(c.replaceIfEquals(1, 1, 2)).isTrue();
    assertThat(c.replaceIfEquals(1, 2, 1)).isTrue();
    assertThat(c.replaceIfEquals(1, 2, 3)).isFalse();
  }

  @Test
  public void testPeekAndRemove() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    c.put(1, 1);
    assertThat(c.peekAndRemove(1)).isEqualTo((Integer) 1);
    assertThat(c.peekAndRemove(1)).isNull();
  }

  @Test
  public void testCompareAndRemove() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    c.put(1, 1);
    assertThat(c.removeIfEquals(1, 2)).isFalse();
    assertThat(c.removeIfEquals(1, 1)).isTrue();
  }

  @Test
  public void testPutAndPeek() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    c.put(1, 1);
    assertThat(c.peek(1)).isEqualTo((Integer) 1);
  }

  @Test
  public void testPeekAndPut() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    c.put(1, 1);
    assertThat(c.peekAndPut(1, 2)).isEqualTo((Integer) 1);
    assertThat(c.peekAndPut(1, 1)).isEqualTo((Integer) 2);
  }

  @Test
  public void testBulkGetAllReadThrough() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, new IdentIntSource(), 100, -1);
    Set<Integer> _requestedKeys = new HashSet<>(asList(2, 3));
    Map<Integer, Integer> m = c.getAll(_requestedKeys);
    assertThat(m.size()).isEqualTo(2);
    assertThat((int) m.get(2)).isEqualTo(2);
    assertThat((int) m.get(3)).isEqualTo(3);
    assertThat(m.get(47)).isNull();
  }

  @Test
  public void testNBulkPeekAll() {
    Cache<Integer, Integer> c = freshCache(Integer.class, Integer.class, null, 100, -1);
    c.put(1, 1);
    c.put(2, 2);
    Set<Integer> _requestedKeys = new HashSet<>(asList(2, 3));
    Map<Integer, Integer> m = c.peekAll(_requestedKeys);
    assertThat(m.size()).isEqualTo(1);
    assertThat((int) m.get(2)).isEqualTo(2);
  }

  @Test
  public void testPutWithFaultyKeyimplementation() {
    Cache<FaultyKey, String> c =
      freshCache(FaultyKey.class, String.class, null, 100, -1);
    c.put(new FaultyKey("abc"), "abc");
    assertThat(c.peekEntry(new FaultyKey("abc"))).isNull();
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
    CacheLoader<String, String> cs = o -> o;
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
        .expireAfterWrite(0, MILLISECONDS)
        .expiryPolicy(_expiryCalc)
        .build();
    c.put("eternal", "eternal");
    c.put("nocache", "nocache");
    c.put("1234", "foo");
    assertThat(c.peek("1234")).isNull();
    assertThat(c.peek("nocache")).isNull();
    assertThat(c.peek("eternal")).isNull();
  }

  @Test
  public void testEntryExpiryCalculator8Min() {
    MyExpiryPolicy _expiryCalc = new MyExpiryPolicy();
    Cache<String, String> c = builder(String.class, String.class)
        .expireAfterWrite(8, MINUTES)
        .expiryPolicy(_expiryCalc)
        .build();
    c.put("eternal", "foo");
    c.put("nocache", "foo");
    c.put("8888", "foo");
    assertThat(c.peek("eternal")).isNotNull();
    assertThat(c.peek("nocache")).isNull();
    assertThat(c.peek("8888")).isNotNull();
  }

  @Test
  public void testEntryExpiryCalculator8MinKeepOldEntry() {
    MyExpiryPolicy _expiryCalc = new MyExpiryPolicy();
    Cache<String, String> c = builder(String.class, String.class)
      .expireAfterWrite(8, MINUTES)
      .expiryPolicy(_expiryCalc)
      .keepDataAfterExpired(true)
      .build();
    c.put("nocache", "foo");
    c.put("nocache", "foo");
    assertThat(_expiryCalc.oldEntrySeen.get()).isEqualTo(1);
  }

  @Test
  public void testPeekGetPutSequence() {
    MyExpiryPolicy _expiryCalc = new MyExpiryPolicy();
    Cache<String, String> c = builder(String.class, String.class)
      .expireAfterWrite(8, MINUTES)
      .expiryPolicy(_expiryCalc)
      .build();
    assertThat(c.containsKey("88")).isFalse();
    String s = c.peekAndReplace("88", "ab");
    assertNull("no existing value", s);
    assertThat(c.containsKey("88"))
      .as("still no mapping")
      .isFalse();
    c.put("88", "ab");
    assertThat(_expiryCalc.key2count.size()).isEqualTo(1);
  }

  public static class MyExpiryPolicy implements ExpiryPolicy<String, String> {
    public final Map<String, AtomicInteger> key2count = new HashMap<>();
    public final AtomicInteger oldEntrySeen = new AtomicInteger();
    @Override
    public long calculateExpiryTime(String _key, String _value, long startTime,
                                    CacheEntry<String, String> currentEntry) {
      AtomicInteger _count;
      synchronized (key2count) {
        _count = key2count.get(_key);
        if (_count == null) {
          _count = new AtomicInteger();
          key2count.put(_key, _count);
        }
        _count.incrementAndGet();
      }

      if (currentEntry != null) {
        oldEntrySeen.incrementAndGet();
      }

      if (_key.startsWith("nocache")) {
        return 0;
      }
      if (_key.startsWith("eternal")) {
        return Long.MAX_VALUE;
      }
      return startTime + Integer.parseInt(_key);
    }
  }

  @Test
  public void testValueWithExpiryTime() {
    Cache<String, MyValueWithExpiryTime> c =
      builder(String.class, MyValueWithExpiryTime.class)
        .build();
    c.put("nocache", new MyValueWithExpiryTime(0));
    final long _DISTANT_FUTURE = 500L * 365 * 24 * 60 * 60 * 1000;
    c.put("cache", new MyValueWithExpiryTime(_DISTANT_FUTURE));
    c.put("cache2", new MyValueWithExpiryTime(MAX_VALUE));
    assertThat(c.peek("nocache")).isNull();
    assertThat(c.peek("cache")).isNotNull();
    assertThat(c.peek("cache2")).isNotNull();
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
      .expireAfterWrite(0, MILLISECONDS)
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
    assertThat(_exceptionCount).isEqualTo(1);
    try {
      Object o = c.peek(_ETERNAL);
      assertThat(o).isNull();
    } catch (CacheLoaderException e) {
      _exceptionCount++;
    }
    assertThat(_exceptionCount)
      .as("eternal, but cache is switched off, no mapping at all")
      .isEqualTo(1);
    try {
      c.get(_ETERNAL);
    } catch (CacheLoaderException e) {
      _exceptionCount++;
    }
    assertThat(_exceptionCount).isEqualTo(2);
    assertNull(
      "caching is off, expiry calculator never asked",
      _expiryCalc.key2count.get(_ETERNAL));
  }

  @Test
  public void testResiliencePolicy8Min() {
    MyResiliencePolicy _expiryCalc = new MyResiliencePolicy();
    Cache<String, String> c = builder(String.class, String.class)
      .expireAfterWrite(8, MINUTES)
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
    assertThat(_exceptionCount).isEqualTo(1);
    try {
      c.peek(_ETERNAL);
    } catch (CacheLoaderException e) {
      _exceptionCount++;
    }
    assertThat(_exceptionCount)
      .as("expect exception is cached")
      .isEqualTo(2);
    try {
      c.get(_ETERNAL);
    } catch (CacheLoaderException e) {
      _exceptionCount++;
    }
    assertThat(_exceptionCount).isEqualTo(3);
    assertThat(_expiryCalc.key2count.get(_ETERNAL))
      .as("calculator was called")
      .isNotNull();
    assertThat(_expiryCalc.key2count.get(_ETERNAL).get())
      .as("fetched once since cached")
      .isEqualTo(1);
    final String _NO_CACHE = "nocache";
    _exceptionCount = 0;
    try {
      c.get(_NO_CACHE);
    } catch (CacheLoaderException e) {
      _exceptionCount++;
    }
    assertThat(_exceptionCount).isEqualTo(1);
    try {
      c.peek(_NO_CACHE);
    } catch (CacheLoaderException e) {
      _exceptionCount++;
    }
    assertThat(_exceptionCount)
      .as("expect exception is not cached")
      .isEqualTo(1);
  }

  @Test
  public void testTimestampIsSetForException() {
    OccasionalExceptionSource src = new OccasionalExceptionSource();
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expiryPolicy((key, value, loadTime, oldEntry) -> 0)
      .resiliencePolicy(resilienceCacheAndSuppressExceptions())
      .recordModificationTime(true)
      .keepDataAfterExpired(true)
      .loader(src)
      .build();
    long t0 = ticks();
    try {
      c.get(1);
      fail("exception expected");
    } catch (CacheLoaderException e) {
    }
    checkExistingAndTimeStampGreaterOrEquals(c, 1, t0);
    try {
      c.get(2);
      long refreshedBefore = ticks();
      assertThat(c.containsKey(2))
        .as("entry disappears since expiry=0")
        .isFalse();
      assertThat(getRefreshedTimeViaEntryProcessor(c, 2))
        .as("entry has no modification time since suppressed")
        .isEqualTo(0);
      sleep(3);
      c.get(2);
      c.invoke(2, (EntryProcessor<Integer, Integer, Long>) e -> {
        assertNull("exception suppressed", e.getException());
        assertThat(e.exists())
          .as("entry present")
          .isTrue();
        assertThat(e.getModificationTime())
          .as("modification time of entry, not when exception happened")
          .isLessThanOrEqualTo(refreshedBefore);
        return null;
      });

    } catch (CacheLoaderException e) {
      fail("no exception expected");
    }
    InternalCacheInfo inf = getInfo();
    assertThat(inf.getSuppressedExceptionCount()).isEqualTo(1);
    assertThat(inf.getLoadExceptionCount()).isEqualTo(2);
    assertThat(src.key2count.get(2)).isNotNull();
    assertThat(src.key2count.get(2).get()).isEqualTo(2);
  }

  void checkExistingAndTimeStampGreaterOrEquals(Cache<Integer, Integer> c, int key, long t) {
    assertThat(c.containsKey(key)).isTrue();
    c.invoke(key, (EntryProcessor<Integer, Integer, Long>) e -> {
      assertThat(e.exists())
        .as("entry present")
        .isTrue();
      assertThat(e.getModificationTime()).isGreaterThanOrEqualTo(t);
      return null;
    });
  }

  long getRefreshedTimeViaEntryProcessor(Cache<Integer, Integer> c, int key) {
    return c.invoke(key, MutableCacheEntry::getModificationTime);
  }

  @Test
  public void testExceptionExpirySuppressTwice() {
    OccasionalExceptionSource src = new PatternExceptionSource(false, true, true);
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expiryPolicy((key, value, loadTime, oldEntry) -> 0)
      .resiliencePolicy(resilienceCacheAndSuppressExceptions())
      .keepDataAfterExpired(true)
      .loader(src)
      .build();
    int _exceptionCount = 0;
    try {
      c.get(1);
    } catch (CacheLoaderException e) {
      _exceptionCount++;
    }
    assertThat(_exceptionCount)
      .as("no exception")
      .isEqualTo(0);
    _exceptionCount = 0;
    try {
      c.get(2); // value is loaded
      c.get(2); // exception gets suppressed
      c.get(2); // cache hit since exception expiry is active
    } catch (CacheLoaderException e) {
      _exceptionCount++;
    }
    InternalCacheInfo inf = getInfo();
    assertThat(_exceptionCount)
      .as("no exception expected")
      .isEqualTo(0);
    assertThat(inf.getSuppressedExceptionCount()).isEqualTo(1);
    assertThat(inf.getLoadExceptionCount()).isEqualTo(1);
    assertThat(src.key2count.get(2)).isNotNull();
    assertThat(src.key2count.get(2).get()).isEqualTo(2);
  }

  public static class MyResiliencePolicy implements ResiliencePolicy<String, String> {

    public final Map<String, AtomicInteger> key2count = new HashMap<>();

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
    public long retryLoadAfter(String key, LoadExceptionInfo<String, String> loadExceptionInfo) {
      return calculateExpiryTime(key, loadExceptionInfo.getException(), loadExceptionInfo.getLoadTime());
    }

    @Override
    public long suppressExceptionUntil(String key, LoadExceptionInfo<String,String> loadExceptionInfo,
                                       CacheEntry<String, String> cachedEntry) {
      return calculateExpiryTime(key, loadExceptionInfo.getException(), loadExceptionInfo.getLoadTime());
    }
  }

  private void freshCacheForInfoTest() {
    freshCache(String.class, String.class, null, 100, -1);
  }

  @Test
  public void testInfoPropertyStarted() {
    Instant t0 = now();
    freshCacheForInfoTest();
    Assertions.assertThat(getInfo().getStartedTime()).isAfterOrEqualTo(t0);
    Assertions.assertThat(getInfo().getInfoCreatedTime()).isAfterOrEqualTo(t0);
  }

  @Test
  public void testInfoPropertyCleared() {
    Instant t0 = now();
    freshCacheForInfoTest();
    assertThat(getInfo().getClearedTime()).isNull();
    cache.clear();
    assertThat(getInfo().getClearedTime()).isAfterOrEqualTo(t0);
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

  public static class AlwaysExceptionSource implements CacheLoader<Integer, Integer> {
    @Override
    public Integer load(Integer o) {
      throw new RuntimeException("always");
    }
  }

  /**
   * Throws an exception
   */
  public static class OccasionalExceptionSource implements CacheLoader<Integer, Integer> {

    public final Map<Integer, AtomicInteger> key2count = new HashMap<>();

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

  public static class AlwaysExceptionStringSource implements CacheLoader<String, String> {

    @Override
    public String load(String o) {
      throw new RuntimeException("always");
    }
  }

  public static class ExpiryPolicyThrowsException implements ExpiryPolicy<String, String> {
    @Override
    public long calculateExpiryTime(String _key, String _value, long startTime, CacheEntry<String, String> currentEntry) {
      if (_value != null && _value.startsWith("boo")) {
        throw new RuntimeException("got value: " + _value);
      }
      return Long.MAX_VALUE;
    }
  }

}
