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

import org.cache2k.test.util.IntCountingCacheSource;
import org.cache2k.test.util.TestingBase;
import org.cache2k.Cache;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Test corner cases for statistics. We do check statistics in
 * {@link org.cache2k.test.core.BasicCacheOperationsWithoutCustomizationsTest},
 * probably some duplications should be sorted out.
 *
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class StatisticsTest extends TestingBase {

  @Test
  public void testPutAndRemove() {
    final Cache<Integer, Integer> c = freshCache(null, 100);
    c.put(1, 2);
    assertEquals(0, getInfo().getMissCount());
    assertEquals(0, getInfo().getGetCount());
    assertEquals(1, getInfo().getPutCount());
    c.put(1, 2);
    assertEquals(0, getInfo().getMissCount());
    assertEquals(0, getInfo().getGetCount());
    assertEquals(2, getInfo().getPutCount());
    c.containsAndRemove(1);
    assertEquals(0, getInfo().getMissCount());
    assertEquals(0, getInfo().getGetCount());
    assertEquals(2, getInfo().getPutCount());
  }

  @Test
  public void testPeekMiss() {
    final Cache<Integer, Integer> c = freshCache(null, 100);
    c.peek(123);
    c.peek(4985);
    assertEquals(2, getInfo().getMissCount());
    assertEquals(2, getInfo().getGetCount());
  }

  @Test
  public void testPeekHit() {
    final Cache<Integer, Integer> c = freshCache(null, 100);
    c.peek(123);
    c.put(123, 3);
    c.peek(123);
    assertEquals(1, getInfo().getMissCount());
    assertEquals(2, getInfo().getGetCount());
  }

  @Test
  public void testPut() {
    final Cache<Integer, Integer> c = freshCache(null, 100);
    c.put(123, 32);
    assertEquals(0, getInfo().getMissCount());
    assertEquals(0, getInfo().getGetCount());
    assertEquals(1, getInfo().getPutCount());
  }

  @Test
  public void testContainsMissHit() {
    final Cache<Integer, Integer> c = freshCache(null, 100);
    c.containsKey(123);
    assertEquals(0, getInfo().getMissCount());
    assertEquals(0, getInfo().getGetCount());
    assertEquals(0, getInfo().getPutCount());
    c.put(123, 3);
    c.containsKey(123);
    assertEquals(0, getInfo().getMissCount());
    assertEquals(0, getInfo().getGetCount());
    assertEquals(1, getInfo().getPutCount());
  }

  @Test
  public void testPutIfAbsentHit() {
    final Cache<Integer, Integer> c = freshCache(null, 100);
    c.put(123, 3);
    c.putIfAbsent(123, 3);
    assertEquals(0, getInfo().getMissCount());
    assertEquals(1, getInfo().getGetCount());
    assertEquals(1, getInfo().getPutCount());
  }

  @Test
  public void testPutIfAbsentMissHit() {
    final Cache<Integer, Integer> c = freshCache(null, 100);
    c.putIfAbsent(123, 3);
    assertEquals(1, getInfo().getMissCount());
    assertEquals(1, getInfo().getGetCount());
    assertEquals(1, getInfo().getPutCount());
    c.putIfAbsent(123, 3);
    assertEquals(1, getInfo().getMissCount());
    assertEquals(2, getInfo().getGetCount());
    assertEquals(1, getInfo().getPutCount());
  }

  @Test
  public void testPeekAndPut() {
    final Cache<Integer, Integer> c = freshCache(null, 100);
    c.peekAndPut(123, 3);
    assertEquals(1, getInfo().getMissCount());
    assertEquals(1, getInfo().getGetCount());
    assertEquals(1, getInfo().getPutCount());
    c.peekAndPut(123, 3);
    assertEquals(2, getInfo().getPutCount());
    assertEquals(1, getInfo().getMissCount());
    assertEquals(2, getInfo().getGetCount());
  }

  @Test
  public void testGetFetch() {
    final Cache<Integer, Integer> c =
        freshCache(new IdentIntSource(), 100);
    c.get(3);
    assertEquals(1, getInfo().getMissCount());
  }

  @Test
  public void testGetFetchHit() {
    final Cache<Integer, Integer> c =
        freshCache(new IdentIntSource(), 100);
    c.get(3);
    c.get(3);
    assertEquals(1, getInfo().getMissCount());
  }

  @Test
  public void testGetPutHit() {
    final Cache<Integer, Integer> c =
        freshCache(new IdentIntSource(), 100);
    c.put(3, 3);
    c.get(3);
    assertEquals(0, getInfo().getMissCount());
  }

  @Test
  public void testGetFetchAlwaysOneGet() {
    IntCountingCacheSource g = new IntCountingCacheSource();
    final Cache<Integer, Integer> c =
        builder(Integer.class, Integer.class)
            .loader(g)
            .expireAfterWrite(0, TimeUnit.SECONDS).build();
    assertEquals("no miss yet", 0, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("one miss yet", 1, g.getLoaderCalledCount());
    assertEquals(1, getInfo().getMissCount());
    assertEquals(1, getInfo().getGetCount());
  }

  @Test
  public void testGetFetchAlwaysTwoGets() {
    IntCountingCacheSource g = new IntCountingCacheSource();
    final Cache<Integer, Integer> c =
        builder(Integer.class, Integer.class)
            .loader(g)
            .expireAfterWrite(0, TimeUnit.SECONDS).build();
    assertEquals("no miss yet", 0, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("one miss yet", 1, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals(2, getInfo().getMissCount());
    assertEquals(2, getInfo().getGetCount());
  }

  public void testGetFetchAndRefresh(boolean keepData) throws Exception {
    long _EXPIRY_MILLIS = TestingParameters.MINIMAL_TICK_MILLIS;
    final IntCountingCacheSource g = new IntCountingCacheSource();
    final Cache<Integer, Integer> c =
        builder(Integer.class, Integer.class)
            .keepDataAfterExpired(keepData)
            .loader(g)
            .refreshAhead(true)
            .expireAfterWrite(_EXPIRY_MILLIS, TimeUnit.MILLISECONDS).build();
    assertEquals("no miss yet", 0, g.getLoaderCalledCount());
    within(_EXPIRY_MILLIS)
      .perform(new Runnable() {
        @Override
        public void run() {
          c.get(1802);
        }
      })
      .expectMaybe(new Runnable() {
        @Override
        public void run() {
          assertEquals(1, getInfo().getMissCount());
          assertEquals(1, getInfo().getGetCount());
          assertEquals(1, getInfo().getLoadCount());
          assertEquals(1, g.getLoaderCalledCount());
        }
    });
    sleep(_EXPIRY_MILLIS * 3);
    c.get(1802);
    if (g.getLoaderCalledCount() >= 3) {
      assertEquals(2, getInfo().getMissCount());
    }
    assertEquals("two get() counted", 2, getInfo().getGetCount());
  }

  @Test
  public void testGetFetchAndRefresh() throws Exception {
    testGetFetchAndRefresh(false);
  }

  @Test
  public void testGetFetchAndRefresh_keepData() throws Exception {
    testGetFetchAndRefresh(true);
  }

  @Test
  public void testFetchAlways() {
    IntCountingCacheSource g = new IntCountingCacheSource();
    final Cache<Integer, Integer> c =
        builder(Integer.class, Integer.class)
          .loader(g)
          .expireAfterWrite(0, TimeUnit.SECONDS).build();
    assertEquals("no miss yet", 0, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("one miss yet", 1, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("additional miss", 2, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("additional miss", 3, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("additional miss", 4, g.getLoaderCalledCount());
    assertEquals(4, getInfo().getGetCount());
    assertEquals(4, getInfo().getLoadCount());
    assertEquals(4, getInfo().getMissCount());
  }

  @Test
  public void testReload() throws Exception {
    IntCountingCacheSource g = new IntCountingCacheSource();
    final Cache<Integer, Integer> c =
        builder(Integer.class, Integer.class)
          .entryCapacity(1)
          .loader(g)
          .sharpExpiry(true)
          .expireAfterWrite(2, TimeUnit.MILLISECONDS).build();
    c.get(1802); // new entry and load miss
    c.get(1803);
    c.get(1804);
    c.get(1802); // new entry and load hit, (if within < 2 ms)
    c.get(1802); // direct hit
    sleep(2);
    c.get(1802); // hit, but needs fetch
  }

  void testUsageCounter(int[] _accessPattern, int _size) throws Exception {
    IntCountingCacheSource g = new IntCountingCacheSource();
    final Cache<Integer, Integer> c =
        builder(Integer.class, Integer.class)
            .entryCapacity(_size)
            .loader(g)
            .eternal(true)
            .build();
    for (int i = 0; i < _accessPattern.length; i++) {
      int v = _accessPattern[i];
      c.get(v);

    }
    assertEquals(_accessPattern.length, getInfo().getGetCount());
    c.clear();
    assertEquals(_accessPattern.length, getInfo().getGetCount());
  }

  int[] accessPattern1 = new int[]{
    1, 2, 3, 1, 1, 1, 2, 3, 4, 5, 5, 5, 2, 3, 4, 5, 6, 7, 8, 9
  };

  @Test
  public void testUsageCounter1() throws Exception {
    testUsageCounter(accessPattern1, 2);
  }

  int[] accessPattern2 = new int[]{
    1, 2, 3, 1, 1, 4, 5, 1, 2, 3, 4, 4, 4
  };

  @Test
  public void testUsageCounter2() throws Exception {
    testUsageCounter(accessPattern2, 2);
  }

  @Test
  public void toStringWithEmptyCache() {
    IntCountingCacheSource g = new IntCountingCacheSource();
    final Cache<Integer, Integer> c =
      builder(Integer.class, Integer.class)
        .loader(g)
        .eternal(true)
        .build();
    assertEquals(0, getInfo().getLoadCount());
    c.toString();
  }

  @Test
  public void disableStatistics() {
    IntCountingCacheSource g = new IntCountingCacheSource();
    final Cache<Integer, Integer> c =
      builder(Integer.class, Integer.class)
        .loader(g)
        .disableStatistics(true)
        .eternal(true)
        .build();
    c.get(1);
    c.remove(1);
    assertEquals(0, getInfo().getLoadCount());
    c.toString();
  }

}
