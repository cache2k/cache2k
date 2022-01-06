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

import org.cache2k.Cache;
import org.cache2k.core.eviction.AbstractEviction;
import org.cache2k.core.api.InternalCache;
import org.cache2k.test.util.TestingBase;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class ChangeCapacityOrResizeTest extends TestingBase {

  @Test
  public void checkResize() {
    Cache<Integer, Integer> cache = builder().entryCapacity(10).build();
    cache.put(1, 2);
    cache.put(2, 2);
    cache.put(3, 2);
    ((InternalCache) cache).getEviction().changeCapacity(1);
    cache.put(1,1);
    cache.put(2,1);
    assertFalse("caching at capacity 1, previous insert is evicted", cache.containsKey(1));
    ((InternalCache) cache).getEviction().changeCapacity(10);
    cache.put(1, 2);
    cache.put(2, 2);
    cache.put(3, 2);
    assertTrue("caching all again", cache.asMap().size() >= 3);
  }

  @Test
  public void checkResizeBigCache() {
    final long size = 12003;
    assertTrue(size > AbstractEviction.MINIMUM_CAPACITY_FOR_CHUNKING);
    Cache<Integer, Integer> cache = buildAndPopulate(size);
    ((InternalCache) cache).getEviction().changeCapacity(1);
    assertTrue("Size is low, but typically not 1", cache.asMap().size() < 1000);
  }

  private Cache<Integer, Integer> buildAndPopulate(long size) {
    Cache<Integer, Integer> cache = builder().entryCapacity(size).build();
    for (int i = 0; i < size; i++) {
      cache.put(i, i);
    }
    return cache;
  }

  @Test
  public void checkResizeSmallCache() {
    final long size = AbstractEviction.MINIMUM_CAPACITY_FOR_CHUNKING - 1;
    Cache<Integer, Integer> cache = buildAndPopulate(size);
    ((InternalCache) cache).getEviction().changeCapacity(1);
    assertEquals(1, cache.asMap().size());
  }

  @Test(expected = IllegalArgumentException.class)
  public void resizeTo0() {
    final long size = 1;
    Cache<Integer, Integer> cache = buildAndPopulate(size);
    ((InternalCache) cache).getEviction().changeCapacity(0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void resizeNegative() {
    final long size = 1;
    Cache<Integer, Integer> cache = buildAndPopulate(size);
    ((InternalCache) cache).getEviction().changeCapacity(-4711);
  }

}
