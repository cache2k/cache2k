package org.cache2k.testsuite.stress;

/*-
 * #%L
 * cache2k testsuite on public API
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.io.BulkCacheLoader;
import org.cache2k.testing.category.SlowTests;
import org.cache2k.testsuite.support.Loaders;
import org.cache2k.testsuite.support.StaticUtil;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@RunWith(Parameterized.class)
@Category(SlowTests.class)
@SuppressWarnings({"NullAway", "nullness"})
public class LoadingPairwiseStressTest extends PairwiseTestingBase {

  public LoadingPairwiseStressTest(Object obj) {
    super(obj);
  }

  @Parameterized.Parameters
  public static Iterable<Object[]> data() {
    List l = new ArrayList();
    l.add(new Object[]{new BuilderAugmenter() {
      @Override
      public <K, V> Cache2kBuilder<K, V> augment(Cache2kBuilder<K, V> b) {
        return b.loader(Loaders.identLoader());
      }
    } });
    l.add(new Object[]{new BuilderAugmenter() {
      @Override
      public <K, V> Cache2kBuilder<K, V> augment(Cache2kBuilder<K, V> b) {
        b.loader(Loaders.identLoader());
        StaticUtil.enforceWiredCache(b); return b;
      }
    } });
    l.add(new Object[]{new BuilderAugmenter() {
      @Override
      public <K, V> Cache2kBuilder<K, V> augment(Cache2kBuilder<K, V> b) {
        b.bulkLoader(new BulkCacheLoader<K, V>() {
          @Override
          public Map<K, V> loadAll(Set<? extends K> keys) throws Exception {
            Map<K, V> result = new HashMap<>();
            for (K key : keys) {
              result.put(key, (V) key);
            }
            return result;
          }
        });
        StaticUtil.enforceWiredCache(b); return b;
      }
    } });
    return l;
  }

  static class GetAndLoad1 extends CacheKeyActorPair<Integer, Integer, Integer> {
    public void setup() { cache.remove(key); }
    public Integer actor1() { return cache.get(key); }
    public Integer actor2() { return cache.get(key); }
    public void check(Integer r1, Integer r2) {
      int value = value();
      assertEquals(value, (int) r1);
      assertEquals(value, (int) r2);
    }
  }

  private static void checkResult(List<Integer> keys, Map<Integer, Integer> result) {
    assertEquals("Size of result", keys.size(), result.size());
    for (Map.Entry<Integer, Integer> entry : result.entrySet()) {
      assertTrue("Requested key in result", keys.contains(entry.getKey()));
      assertNotNull("Value set for key " + entry.getKey(), entry.getValue());
      assertEquals((int) entry.getKey(), (int) entry.getValue());
    }
  }

  static class GetAllAndLoad1 extends CacheKeyActorPair<Void, Integer, Integer> {
    public void setup() { cache.removeAll(asList(key, key + 1, key + 2, key + 3, key + 4)); }
    public Void actor1() {
      List<Integer> keys = asList(key, key + 1, key + 2, key + 3);
      Map<Integer, Integer> result = cache.getAll(keys);
      checkResult(keys, result);
      return null;
    }

    public Void actor2() {
      cache.loadAll(asList(key, key + 1));
      List<Integer> keys = asList(key, key + 1, key + 2, key + 4);
      Map<Integer, Integer> result = cache.getAll(keys);
      checkResult(keys, result);
      return null;
    }
    public void check(Void r1, Void r2) {
      List<Integer> keys = asList(key, key + 1, key + 2, key + 3, key + 4);
      checkResult(keys, cache.peekAll(keys));
    }
  }

}
