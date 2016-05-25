package org.cache2k.test.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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
import org.cache2k.junit.FastTests;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * More thorough iterator tests, needing a separate cache.
 *
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class IteratorTest {

  @Test
  public void testExpansion() {
    Cache<Integer, Integer> c = setupCache();
    Iterator<CacheEntry<Integer,Integer>> it = c.entries().iterator();
    Set<Integer> _keysSeen = new HashSet<Integer>();
    while (it.hasNext()) {
      CacheEntry<Integer,Integer> e = it.next();
      _keysSeen.add(e.getKey());
      if (_keysSeen.size() == 10) {
        break;
      }
    }
    assertEquals(10, _keysSeen.size());
    for (int i = 20; i < 5555; i++) {
      c.put(i,i);
    }
    while (it.hasNext()) {
      CacheEntry<Integer,Integer> e = it.next();
      _keysSeen.add(e.getKey());
    }
    assertTrue(_keysSeen.contains(19));
    c.close();
  }

  private Cache<Integer, Integer> setupCache() {
    Cache<Integer, Integer> c = Cache2kBuilder
      .of(Integer.class, Integer.class)
      .eternal(true)
      .entryCapacity(10000)
      .build();
    for (int i = 0; i < 20; i++) {
      c.put(i,i);
    }
    return c;
  }

  @Test
  public void keyIteration() {
    Cache<Integer, Integer> c = setupCache();
    Set<Integer> _keysSeen = new HashSet<Integer>();
    for (Integer i : c.keys()) {
      _keysSeen.add(i);
    }
    assertEquals(20, _keysSeen.size());
    c.close();
  }

  @Test(expected = CacheClosedException.class)
  @Ignore("iterator needs to detect close()")
  public void testClose() {
    Cache<Integer, Integer> c = setupCache();
    Iterator<CacheEntry<Integer,Integer>> it = c.entries().iterator();
    Set<Integer> _keysSeen = new HashSet<Integer>();
    while (it.hasNext()) {
      CacheEntry<Integer,Integer> e = it.next();
      _keysSeen.add(e.getKey());
      if (_keysSeen.size() == 10) {
        break;
      }
    }
    c.close();
    it.hasNext();
  }

}
