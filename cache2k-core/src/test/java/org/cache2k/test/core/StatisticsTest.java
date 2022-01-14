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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import org.cache2k.test.util.IntCountingCacheSource;
import org.cache2k.test.util.TestingBase;
import org.cache2k.Cache;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.TimeUnit;

import static org.cache2k.test.core.TestingParameters.MINIMAL_TICK_MILLIS;

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
    Cache<Integer, Integer> c = freshCache(null, 100);
    c.put(1, 2);
    assertThat(getInfo().getMissCount()).isEqualTo(0);
    assertThat(getInfo().getGetCount()).isEqualTo(0);
    assertThat(getInfo().getPutCount()).isEqualTo(1);
    c.put(1, 2);
    assertThat(getInfo().getMissCount()).isEqualTo(0);
    assertThat(getInfo().getGetCount()).isEqualTo(0);
    assertThat(getInfo().getPutCount()).isEqualTo(2);
    c.containsAndRemove(1);
    assertThat(getInfo().getMissCount()).isEqualTo(0);
    assertThat(getInfo().getGetCount()).isEqualTo(0);
    assertThat(getInfo().getPutCount()).isEqualTo(2);
  }

  @Test
  public void testPeekMiss() {
    Cache<Integer, Integer> c = freshCache(null, 100);
    c.peek(123);
    c.peek(4985);
    assertThat(getInfo().getMissCount()).isEqualTo(2);
    assertThat(getInfo().getGetCount()).isEqualTo(2);
  }

  @Test
  public void testPeekHit() {
    Cache<Integer, Integer> c = freshCache(null, 100);
    c.peek(123);
    c.put(123, 3);
    c.peek(123);
    assertThat(getInfo().getMissCount()).isEqualTo(1);
    assertThat(getInfo().getGetCount()).isEqualTo(2);
  }

  @Test
  public void testPut() {
    Cache<Integer, Integer> c = freshCache(null, 100);
    c.put(123, 32);
    assertThat(getInfo().getMissCount()).isEqualTo(0);
    assertThat(getInfo().getGetCount()).isEqualTo(0);
    assertThat(getInfo().getPutCount()).isEqualTo(1);
  }

  @Test
  public void testContainsMissHit() {
    Cache<Integer, Integer> c = freshCache(null, 100);
    c.containsKey(123);
    assertThat(getInfo().getMissCount()).isEqualTo(0);
    assertThat(getInfo().getGetCount()).isEqualTo(0);
    assertThat(getInfo().getPutCount()).isEqualTo(0);
    c.put(123, 3);
    c.containsKey(123);
    assertThat(getInfo().getMissCount()).isEqualTo(0);
    assertThat(getInfo().getGetCount()).isEqualTo(0);
    assertThat(getInfo().getPutCount()).isEqualTo(1);
  }

  @Test
  public void testPutIfAbsentHit() {
    Cache<Integer, Integer> c = freshCache(null, 100);
    c.put(123, 3);
    c.putIfAbsent(123, 3);
    assertThat(getInfo().getMissCount()).isEqualTo(0);
    assertThat(getInfo().getGetCount()).isEqualTo(1);
    assertThat(getInfo().getPutCount()).isEqualTo(1);
  }

  @Test
  public void testPutIfAbsentMissHit() {
    Cache<Integer, Integer> c = freshCache(null, 100);
    c.putIfAbsent(123, 3);
    assertThat(getInfo().getMissCount()).isEqualTo(1);
    assertThat(getInfo().getGetCount()).isEqualTo(1);
    assertThat(getInfo().getPutCount()).isEqualTo(1);
    c.putIfAbsent(123, 3);
    assertThat(getInfo().getMissCount()).isEqualTo(1);
    assertThat(getInfo().getGetCount()).isEqualTo(2);
    assertThat(getInfo().getPutCount()).isEqualTo(1);
  }

  @Test
  public void testPeekAndPut() {
    Cache<Integer, Integer> c = freshCache(null, 100);
    c.peekAndPut(123, 3);
    assertThat(getInfo().getMissCount()).isEqualTo(1);
    assertThat(getInfo().getGetCount()).isEqualTo(1);
    assertThat(getInfo().getPutCount()).isEqualTo(1);
    c.peekAndPut(123, 3);
    assertThat(getInfo().getPutCount()).isEqualTo(2);
    assertThat(getInfo().getMissCount()).isEqualTo(1);
    assertThat(getInfo().getGetCount()).isEqualTo(2);
  }

  @Test
  public void testGetFetch() {
    Cache<Integer, Integer> c =
        freshCache(new IdentIntSource(), 100);
    c.get(3);
    assertThat(getInfo().getMissCount()).isEqualTo(1);
  }

  @Test
  public void testGetFetchHit() {
    Cache<Integer, Integer> c =
        freshCache(new IdentIntSource(), 100);
    c.get(3);
    c.get(3);
    assertThat(getInfo().getMissCount()).isEqualTo(1);
  }

  @Test
  public void testGetPutHit() {
    Cache<Integer, Integer> c =
        freshCache(new IdentIntSource(), 100);
    c.put(3, 3);
    c.get(3);
    assertThat(getInfo().getMissCount()).isEqualTo(0);
  }

  @Test
  public void testGetFetchAlwaysOneGet() {
    IntCountingCacheSource g = new IntCountingCacheSource();
    Cache<Integer, Integer> c =
      builder(Integer.class, Integer.class)
        .loader(g)
        .expireAfterWrite(0, SECONDS).build();
    assertThat(g.getLoaderCalledCount())
      .as("no miss yet")
      .isEqualTo(0);
    c.get(1802);
    assertThat(g.getLoaderCalledCount())
      .as("one miss yet")
      .isEqualTo(1);
    assertThat(getInfo().getMissCount()).isEqualTo(1);
    assertThat(getInfo().getGetCount()).isEqualTo(1);
  }

  @Test
  public void testGetFetchAlwaysTwoGets() {
    IntCountingCacheSource g = new IntCountingCacheSource();
    Cache<Integer, Integer> c =
      builder(Integer.class, Integer.class)
        .loader(g)
        .expireAfterWrite(0, SECONDS).build();
    assertThat(g.getLoaderCalledCount())
      .as("no miss yet")
      .isEqualTo(0);
    c.get(1802);
    assertThat(g.getLoaderCalledCount())
      .as("one miss yet")
      .isEqualTo(1);
    c.get(1802);
    assertThat(getInfo().getMissCount()).isEqualTo(2);
    assertThat(getInfo().getGetCount()).isEqualTo(2);
  }

  public void testGetFetchAndRefresh(boolean keepData) throws Exception {
    long expiryMillis = MINIMAL_TICK_MILLIS;
    IntCountingCacheSource g = new IntCountingCacheSource();
    Cache<Integer, Integer> c =
      builder(Integer.class, Integer.class)
        .keepDataAfterExpired(keepData)
        .loader(g)
        .refreshAhead(true)
        .expireAfterWrite(expiryMillis, MILLISECONDS).build();
    assertThat(g.getLoaderCalledCount())
      .as("no miss yet")
      .isEqualTo(0);
    within(expiryMillis)
      .perform(() -> c.get(1802))
      .expectMaybe(() -> {
        assertThat(getInfo().getMissCount()).isEqualTo(1);
        assertThat(getInfo().getGetCount()).isEqualTo(1);
        assertThat(getInfo().getLoadCount()).isEqualTo(1);
        assertThat(g.getLoaderCalledCount()).isEqualTo(1);
      });
    sleep(expiryMillis * 3);
    c.get(1802);
    if (g.getLoaderCalledCount() >= 3) {
      assertThat(getInfo().getMissCount()).isEqualTo(2);
    }
    assertThat(getInfo().getGetCount())
      .as("two get() counted")
      .isEqualTo(2);
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
    Cache<Integer, Integer> c =
      builder(Integer.class, Integer.class)
        .loader(g)
        .expireAfterWrite(0, SECONDS).build();
    assertThat(g.getLoaderCalledCount())
      .as("no miss yet")
      .isEqualTo(0);
    c.get(1802);
    assertThat(g.getLoaderCalledCount())
      .as("one miss yet")
      .isEqualTo(1);
    c.get(1802);
    assertThat(g.getLoaderCalledCount())
      .as("additional miss")
      .isEqualTo(2);
    c.get(1802);
    assertThat(g.getLoaderCalledCount())
      .as("additional miss")
      .isEqualTo(3);
    c.get(1802);
    assertThat(g.getLoaderCalledCount())
      .as("additional miss")
      .isEqualTo(4);
    assertThat(getInfo().getGetCount()).isEqualTo(4);
    assertThat(getInfo().getLoadCount()).isEqualTo(4);
    assertThat(getInfo().getMissCount()).isEqualTo(4);
  }

  @Test
  public void testReload() throws Exception {
    IntCountingCacheSource g = new IntCountingCacheSource();
    Cache<Integer, Integer> c =
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

  void testUsageCounter(int[] accessPattern, int size) throws Exception {
    IntCountingCacheSource g = new IntCountingCacheSource();
    Cache<Integer, Integer> c =
        builder(Integer.class, Integer.class)
            .entryCapacity(size)
            .loader(g)
            .eternal(true)
            .build();
    for (int v : accessPattern) {
      c.get(v);

    }
    assertThat(getInfo().getGetCount()).isEqualTo(accessPattern.length);
    c.clear();
    assertThat(getInfo().getGetCount()).isEqualTo(accessPattern.length);
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
    Cache<Integer, Integer> c =
      builder(Integer.class, Integer.class)
        .loader(g)
        .eternal(true)
        .entryCapacity(200)
        .build();
    assertThat(getInfo().getLoadCount()).isEqualTo(0);
    assertThat(c.toString())
      .contains("capacity=200")
      .contains("coldHits=0");
  }

  @Test
  public void disableStatistics() {
    IntCountingCacheSource g = new IntCountingCacheSource();
    Cache<Integer, Integer> c =
      builder(Integer.class, Integer.class)
        .loader(g)
        .disableStatistics(true)
        .entryCapacity(200)
        .eternal(true)
        .build();
    c.get(1);
    c.remove(1);
    assertThat(getInfo().getLoadCount()).isEqualTo(0);
    assertThat(c.toString())
      .contains("capacity=200")
      .contains("coldHits=0");
  }

}
