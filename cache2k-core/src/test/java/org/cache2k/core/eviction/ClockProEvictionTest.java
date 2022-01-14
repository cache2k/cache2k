package org.cache2k.core.eviction;

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
import org.cache2k.test.util.TestingBase;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static java.lang.Math.max;
import static org.assertj.core.api.Assertions.assertThat;
import static org.cache2k.core.eviction.ClockProPlusEviction.HIT_COUNTER_DECREASE_SHIFT;

/**
 * Run simple access patterns that provide test coverage on the clock pro
 * eviction.
 *
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class ClockProEvictionTest extends TestingBase {

  protected Cache<Integer, Integer> provideCache(long size) {
    return builder(Integer.class, Integer.class)
      .eternal(true)
      .entryCapacity(size)
      .build();
  }

  @Test
  public void testChunking() {
    final int maxSize = 10000;
    final int minChunkSize = 1;
    Cache<Integer, Integer> c = provideCache(maxSize);
    int evictionChunk = 1;
    int previousSize = 0;
    for (int i = 0; i < maxSize * 2; i++) {
      c.put(i, 1);
      int size = c.asMap().size();
      if (size < previousSize + 1) {
        evictionChunk = max((previousSize + 1) - size, evictionChunk);
      }
      if (evictionChunk > minChunkSize) {
        break;
      }
      previousSize = size;
    }
    assertThat(evictionChunk)
      .as("chunked eviction happened")
      .isGreaterThan(minChunkSize);
  }

  @Test
  public void test1() {
    final int size = 1;
    Cache<Integer, Integer> c = provideCache(size);
    for (int i = 0; i < size * 2; i++) {
      c.put(i, i);
    }
    for (int i = 0; i < size * 2; i++) {
      c.put(i, i);
    }
    int count = 0;
    for (int k : c.keys()) {
      count++;
    }
    assertThat(count).isEqualTo(size);
  }

  @Test
  public void test30() {
    final int size = 30;
    Cache<Integer, Integer> c = provideCache(size);
    for (int i = 0; i < size * 2; i++) {
      c.put(i, i);
    }
    for (int i = 0; i < size * 2; i++) {
      c.put(i, i);
    }
    int count = 0;
    for (int k : c.keys()) {
      count++;
    }
    assertThat(count).isEqualTo(size);
  }

  @Test
  public void testEvictCold() {
    final int size = 30;
    Cache<Integer, Integer> c = provideCache(size);
    for (int i = 0; i < size / 2; i++) {
      c.put(i, i);
    }
    for (int i = 0; i < size / 2; i++) {
      c.put(i, i);
    }
    for (int i = 0; i < size * 2; i++) {
      c.put(i, i);
    }
    int count = 0;
    for (int k : c.keys()) {
      count++;
    }
    assertThat(count).isEqualTo(size);
  }

  @Test
  public void testEvictHot() {
    final int size = 30;
    Cache<Integer, Integer> c = provideCache(size);
    for (int i = 0; i < size / 2; i++) {
      c.put(i, i);
    }
    for (int i = 0; i < size / 2; i++) {
      c.put(i, i);
    }
    for (int i = 0; i < size * 2; i++) {
      c.put(i, i);
    }
    for (int i = size / 2; i < size; i++) {
      c.put(i, i);
    }
    for (int i = size / 2; i < size; i++) {
      c.put(i, i);
    }
    for (int i = 0; i < size * 2; i++) {
      c.put(i, i);
    }
    int count = 0;
    for (int k : c.keys()) {
      count++;
    }
    assertThat(count).isEqualTo(size);
  }

  /**
   * Additional test to extend test coverage
   */
  @Test
  public void testEvictHot2() {
    final int size = 30;
    Cache<Integer, Integer> c = provideCache(size);
    for (int i = 0; i < size / 2; i++) {
      c.put(i, i);
    }
    for (int i = 0; i < size / 2; i++) {
      c.put(i, i);
    }
    for (int i = 0; i < size * 2; i++) {
      c.put(i, i);
    }
    assertThat(countEntriesViaIteration()).isEqualTo(size);
    for (int i = size / 2; i < size; i++) {
      c.put(i, i);
    }
    for (int i = size / 2; i < size; i++) {
      c.put(i, i);
    }
    for (int i = 0; i < size / 3; i++) {
      c.put(i, i);
    }
    int hitCounterDecreaseShift = HIT_COUNTER_DECREASE_SHIFT;
    for (int j = 0; j < 1 << hitCounterDecreaseShift + 1; j++) {
      for (int i = 0; i < size / 4; i++) {
        c.put(i, i);
      }
    }
    for (int i = 0; i < size * 2; i++) {
      c.put(i, i);
    }
    assertThat(countEntriesViaIteration()).isEqualTo(size);
  }

}
