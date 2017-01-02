package org.cache2k.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2017 headissue GmbH, Munich
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
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 * Simple tests that utilize the cache eviction
 *
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class EvictionTest {

  @Test
  public void test30() {
    final int _SIZE = 30;
    Cache<Integer, Integer> c =
      Cache2kBuilder.of(Integer.class, Integer.class)
        .eternal(true)
        .entryCapacity(_SIZE)
        .build();
    for (int i = 0; i < _SIZE * 2; i++) {
      c.put(i, i);
    }
    for (int i = 0; i < _SIZE * 2; i++) {
      c.put(i, i);
    }
    int _count = 0;
    for (int k : c.keys()) {
      _count++;
    }
    assertEquals(_SIZE, _count);
    c.close();
  }

  @Test
  public void testEvictCold() {
    final int _SIZE = 30;
    Cache<Integer, Integer> c =
      Cache2kBuilder.of(Integer.class, Integer.class)
        .eternal(true)
        .entryCapacity(_SIZE)
        .build();
    for (int i = 0; i < _SIZE / 2; i++) {
      c.put(i, i);
    }
    for (int i = 0; i < _SIZE / 2; i++) {
      c.put(i, i);
    }
    for (int i = 0; i < _SIZE * 2; i++) {
      c.put(i, i);
    }
    int _count = 0;
    for (int k : c.keys()) {
      _count++;
    }
    assertEquals(_SIZE, _count);
    c.close();
  }

  @Test
  public void testEvictHot() {
    final int _SIZE = 30;
    Cache<Integer, Integer> c =
      Cache2kBuilder.of(Integer.class, Integer.class)
        .eternal(true)
        .entryCapacity(_SIZE)
        .build();
    for (int i = 0; i < _SIZE / 2; i++) {
      c.put(i, i);
    }
    for (int i = 0; i < _SIZE / 2; i++) {
      c.put(i, i);
    }
    for (int i = 0; i < _SIZE * 2; i++) {
      c.put(i, i);
    }
    for (int i = _SIZE / 2; i < _SIZE; i++) {
      c.put(i, i);
    }
    for (int i = _SIZE / 2; i < _SIZE; i++) {
      c.put(i, i);
    }
    for (int i = 0; i < _SIZE * 2; i++) {
      c.put(i, i);
    }
    int _count = 0;
    for (int k : c.keys()) {
      _count++;
    }
    assertEquals(_SIZE, _count);
    c.close();
  }

}
