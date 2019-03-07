package org.cache2k.core;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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
import org.cache2k.test.util.TestingBase;
import org.cache2k.testing.category.FastTests;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 * Run simple access patterns that provide test coverage on the clock pro
 * eviction.
 *
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class ClockProEvictionTest extends TestingBase {

  protected Cache<Integer, Integer> provideCache(long _size) {
    return builder(Integer.class, Integer.class)
      .eternal(true)
      .entryCapacity(_size)
      .build();
  }

  @Test
  public void test1() {
    final int _SIZE = 1;
    Cache<Integer, Integer> c = provideCache(_SIZE);
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
  }

  @Test
  public void test30() {
    final int _SIZE = 30;
    Cache<Integer, Integer> c = provideCache(_SIZE);
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
  }

  @Test
  public void testEvictCold() {
    final int _SIZE = 30;
    Cache<Integer, Integer> c = provideCache(_SIZE);
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
  }

  public void testEvictHot() {
    final int _SIZE = 30;
    Cache<Integer, Integer> c = provideCache(_SIZE);
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
  }

  /**
   * Additional test to extend test coverage
   */
  public void testEvictHot2() {
    final int _SIZE = 30;
    Cache<Integer, Integer> c = provideCache(_SIZE);
    for (int i = 0; i < _SIZE / 2; i++) {
      c.put(i, i);
    }
    for (int i = 0; i < _SIZE / 2; i++) {
      c.put(i, i);
    }
    for (int i = 0; i < _SIZE * 2; i++) {
      c.put(i, i);
    }
    assertEquals(_SIZE, countEntriesViaIteration());
    for (int i = _SIZE / 2; i < _SIZE; i++) {
      c.put(i, i);
    }
    for (int i = _SIZE / 2; i < _SIZE; i++) {
      c.put(i, i);
    }
    for (int i = 0; i < _SIZE / 3; i++) {
      c.put(i, i);
    }
    for (int j = 0; j < 1 << ClockProPlusEviction.TUNABLE_CLOCK_PRO.hitCounterDecreaseShift + 1; j++) {
      for (int i = 0; i < _SIZE / 4; i++) {
        c.put(i, i);
      }
    }
    for (int i = 0; i < _SIZE * 2; i++) {
      c.put(i, i);
    }
    assertEquals(_SIZE, countEntriesViaIteration());
  }

}
