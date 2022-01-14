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
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.core.CacheClosedException;
import org.cache2k.testing.category.FastTests;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * More thorough iterator tests, needing a separate cache.
 *
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class IteratorTest {

  Cache<Integer, Integer> cache;

  @After
  public void tearDown() {
    if (cache != null) {
      cache.close();
    }
  }

  @Test
  public void testExpansion() {
    Cache<Integer, Integer> c = createCacheWith20Entries();
    Iterator<CacheEntry<Integer,Integer>> it = c.entries().iterator();
    Set<Integer> _keysSeen = new HashSet<>();
    while (it.hasNext()) {
      CacheEntry<Integer,Integer> e = it.next();
      _keysSeen.add(e.getKey());
      if (_keysSeen.size() == 10) {
        break;
      }
    }
    assertThat(_keysSeen.size()).isEqualTo(10);
    for (int i = 20; i < 5555; i++) {
      c.put(i,i);
    }
    while (it.hasNext()) {
      CacheEntry<Integer,Integer> e = it.next();
      _keysSeen.add(e.getKey());
    }
    assertThat(_keysSeen.contains(19)).isTrue();
  }

  @Test
  public void testIterateEmpty_hasNext() {
    Cache<Integer, Integer> c = createEmptyCache();
    Iterator<CacheEntry<Integer,Integer>> it = c.entries().iterator();
    assertThat(it.hasNext()).isFalse();
    assertThat(it.hasNext()).isFalse();
    assertThat(it.hasNext()).isFalse();
    assertThat(it.hasNext()).isFalse();
    assertThat(it.hasNext()).isFalse();
    assertThat(it.hasNext()).isFalse();
  }

  @Test(expected = NoSuchElementException.class)
  public void testIterateEmpty_next() {
    Cache<Integer, Integer> c = createEmptyCache();
    Iterator<CacheEntry<Integer,Integer>> it = c.entries().iterator();
    it.next();
  }

  private Cache<Integer, Integer> createEmptyCache() {
    return cache = Cache2kBuilder
      .of(Integer.class, Integer.class)
      .eternal(true)
      .entryCapacity(10000)
      .build();
  }

  private Cache<Integer, Integer> createCacheWith20Entries() {
    Cache<Integer, Integer> c = createEmptyCache();
    for (int i = 0; i < 20; i++) {
      c.put(i,i);
    }
    return c;
  }

  @Test
  public void keyIteration() {
    Cache<Integer, Integer> c = createCacheWith20Entries();
    Set<Integer> _keysSeen = new HashSet<>();
    for (Integer i : c.keys()) {
      _keysSeen.add(i);
    }
    assertThat(_keysSeen.size()).isEqualTo(20);
    c.close();
  }

  @Test(expected = CacheClosedException.class)
  public void testClose() {
    Cache<Integer, Integer> c = createCacheWith20Entries();
    Iterator<CacheEntry<Integer,Integer>> it = c.entries().iterator();
    Set<Integer> _keysSeen = new HashSet<>();
    while (it.hasNext()) {
      CacheEntry<Integer,Integer> e = it.next();
      _keysSeen.add(e.getKey());
      if (_keysSeen.size() == 10) {
        break;
      }
    }
    c.close();
    assertThat(it.hasNext()).isTrue();
    CacheEntry<Integer, Integer> e = it.next();
    assertThat(e).isNotNull();
  }

}
