package org.cache2k.core;

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
import org.cache2k.Weigher;
import org.cache2k.test.util.TestingBase;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Run simple access patterns that provide test coverage on the clock pro
 * eviction.
 *
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class WeigherTest extends TestingBase {

  protected Cache<Integer, Integer> provideCache(long size) {
    return builder(Integer.class, Integer.class)
      .eternal(true)
      .entryCapacity(-1)
      .weigher(new Weigher<Integer, Integer>() {
        @Override
        public int weigh(Integer key, Integer value) {
          return 1;
        }
      })
      .maximumWeight(size)
      .strictEviction(true)
      .build();
  }

  @Test
  public void removeOnEmptyCache() {
    Cache<Integer, Integer> c = provideCache(100);
    c.remove(123);
  }

  @Test
  public void weightAccountedFor() {
    long size = 1;
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .eternal(true)
      .entryCapacity(-1)
      .weigher(new Weigher<Integer, Integer>() {
        @Override
        public int weigh(Integer key, Integer value) {
          return 1;
        }
      })
      .maximumWeight(size)
      .strictEviction(true)
      .build();
    c.put(1, 1);
    c.put(2, 1);
    assertEquals(1, countEntriesViaIteration());
  }

  /**
   * The weight 0 is legal. Caffeine/Guava allows weight of 0 as well.
   * Maybe a minimum weight of 1 is a good idea, but better be compatible
   * to the other caches.
   */
  @Test
  public void minimumWeight() {
    long size = 1;
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .eternal(true)
      .entryCapacity(-1)
      .weigher(new Weigher<Integer, Integer>() {
        @Override
        public int weigh(Integer key, Integer value) {
          return 0;
        }
      })
      .maximumWeight(size)
      .strictEviction(true)
      .build();
    c.put(1, 1);
    c.put(2, 1);
    assertEquals(2, countEntriesViaIteration());
  }

  @Test
  public void unboundedWeight() {
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .eternal(true)
      .entryCapacity(-1)
      .weigher(new Weigher<Integer, Integer>() {
        @Override
        public int weigh(Integer key, Integer value) {
          return 0;
        }
      })
      .maximumWeight(Long.MAX_VALUE)
      .build();
    assertEquals(Long.MAX_VALUE, getInfo().getMaximumWeight());
  }

  @Test
  public void weightUpdated() {
    int size = 2;
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .eternal(true)
      .entryCapacity(-1)
      .weigher(new Weigher<Integer, Integer>() {
        @Override
        public int weigh(Integer key, Integer value) {
          return value;
        }
      })
      .maximumWeight(size)
      .strictEviction(true)
      .build();
    c.put(1, 1);
    c.put(2, 1);
    assertEquals(2, countEntriesViaIteration());
    c.put(2, 100);
    assertEquals(1, countEntriesViaIteration());
    assertTrue("the entry that is updated is never removed", c.containsKey(2));
    c.put(1, 1);
    assertEquals(1, countEntriesViaIteration());
    assertFalse("the other entry is removed", c.containsKey(2));
    assertEquals(101, getInfo().getEvictedWeight());
  }

  @Test
  public void weightUpdatedBig() {
    int size = 20000000;
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .eternal(true)
      .entryCapacity(-1)
      .weigher(new Weigher<Integer, Integer>() {
        @Override
        public int weigh(Integer key, Integer value) {
          return value;
        }
      })
      .maximumWeight(size)
      .strictEviction(true)
      .build();
    c.put(1, 1);
    c.put(2, 1);
    assertEquals(2, countEntriesViaIteration());
    c.put(2, size * 2);
    assertEquals(1, countEntriesViaIteration());
    assertTrue("the entry that is updated is never removed", c.containsKey(2));
    c.put(1, 1);
    assertEquals(1, countEntriesViaIteration());
    assertFalse("the other entry is removed", c.containsKey(2));
  }

  @Test
  public void putAndRemove() {
    int size = 20000000;
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .eternal(true)
      .entryCapacity(-1)
      .weigher(new Weigher<Integer, Integer>() {
        @Override
        public int weigh(Integer key, Integer value) {
          return value;
        }
      })
      .maximumWeight(size)
      .strictEviction(true)
      .build();
    final int numEntries = 30;
    for (int i = 0; i < numEntries; i++) {
      c.put(i, (1 << i) + 1);
    }
    for (int i = 0; i < numEntries; i++) {
      c.remove(i);
    }
    assertEquals(0, getInfo().getTotalWeight());
  }

  @Test
  public void weightUpdatedOnRemove() {
    long size = 2;
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .eternal(true)
      .entryCapacity(-1)
      .weigher(new Weigher<Integer, Integer>() {
        @Override
        public int weigh(Integer key, Integer value) {
          return value;
        }
      })
      .maximumWeight(size)
      .strictEviction(true)
      .build();
    c.put(1, 1);
    c.put(2, 1);
    assertEquals(2, countEntriesViaIteration());
    c.remove(2);
    c.put(1, 2);
    assertEquals(1, countEntriesViaIteration());
    assertTrue(c.containsKey(1));
    assertEquals(1, getInfo().getEvictedWeight());
  }

  @Test
  public void weightAccountedForWithLoader() {
    long size = 1;
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .eternal(true)
      .entryCapacity(-1)
      .weigher(new Weigher<Integer, Integer>() {
        @Override
        public int weigh(Integer key, Integer value) {
          return 1;
        }
      })
      .maximumWeight(size)
      .loader(new IdentIntSource())
      .strictEviction(true)
      .build();
    c.get(1);
    c.get(1);
    assertEquals(1, countEntriesViaIteration());
  }

  @Test
  public void weightUpdatedWithLoader() {
    long size = 2;
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .eternal(true)
      .entryCapacity(-1)
      .weigher(new Weigher<Integer, Integer>() {
        @Override
        public int weigh(Integer key, Integer value) {
          return value;
        }
      })
      .maximumWeight(size)
      .loader(new PatternLoader(1, 1, 100, 1))
      .strictEviction(true)
      .build();
    c.get(1);
    c.get(2);
    assertEquals(2, countEntriesViaIteration());
    reload(2); // 100
    assertEquals(1, countEntriesViaIteration());
    assertTrue("the entry that is updated is never removed", c.containsKey(2));
    reload(1);
    assertEquals(1, countEntriesViaIteration());
    assertFalse("the other entry is removed", c.containsKey(2));
  }

}
