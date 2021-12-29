package org.cache2k.testsuite.stress;

/*-
 * #%L
 * cache2k testsuite on public API
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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
import org.cache2k.io.AsyncBulkCacheLoader;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@RunWith(Parameterized.class)
@Category(SlowTests.class)
@SuppressWarnings({"NullAway", "nullness"})
public class AsyncBulkLoadingPairwiseStressTest extends PairwiseTestingBase {

  public AsyncBulkLoadingPairwiseStressTest(Object obj) {
    super(obj);
  }

  @Parameterized.Parameters
  public static Iterable<Object[]> data() {
    List l = new ArrayList();
    l.add(new Object[]{new BuilderAugmenter() {
      @Override
      public <K, V> Cache2kBuilder<K, V> augment(Cache2kBuilder<K, V> b) {
        return b.bulkLoader((keys, context, callback) -> {
          context.getExecutor().execute(() -> {
            Map<K, V> map = new HashMap<>();
            for (K key : keys) {
              map.put(key, (V) key);
            }
            callback.onLoadSuccess(map);
          });
        });
      }
    } });
    l.add(new Object[]{new BuilderAugmenter() {
      @Override
      public <K, V> Cache2kBuilder<K, V> augment(Cache2kBuilder<K, V> b) {
        return b.bulkLoader((keys, context, callback) -> {
          context.getExecutor().execute(() -> {
            List<K> keyList = new ArrayList<>();
            keyList.addAll(keys);
            int idx = Math.abs(System.identityHashCode(keyList)) % keyList.size();
            K separateKey = keyList.get(idx);
            callback.onLoadSuccess(separateKey, (V) separateKey);
            keyList.remove(idx);
            Map<K, V> map = new HashMap<>();
            for (K key : keyList) {
              map.put(key, (V) key);
            }
            callback.onLoadSuccess(map);
          });
        });
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

  private static List<Integer> fromTo(int from, int toInclusive) {
    List<Integer> list = new ArrayList<>(toInclusive - from + 1);
    for (int i = from; i <= toInclusive; i++) {
      list.add(from++);
    }
    return list;
  }

  /** loadAll on identical set */
  static class LoadAll1 extends CacheKeyActorPair<CompletableFuture<Void>, Integer, Integer> {
    List<Integer> keySet;
    public void setup() {
      keySet = fromTo(key, key + 5);
      cache.removeAll(keySet);
    }
    public CompletableFuture<Void> actor1() { return cache.loadAll(keySet); }
    public CompletableFuture<Void> actor2() {
      return cache.loadAll(keySet);
    }
    public void check(CompletableFuture<Void> r1, CompletableFuture<Void> r2) {
      try {
        if (this.hashCode() % 2 == 0) {
          r1.get();
          r2.get();
        } else {
          r2.get();
          r1.get();
        }
      } catch (Exception e) {
        throw new Error(e);
      }
      checkResult(keySet, cache.peekAll(keySet));
    }
  }

  /** loadAll with partially overlapping set */
  static class LoadAll2 extends CacheKeyActorPair<CompletableFuture<Void>, Integer, Integer> {
    List<Integer> keySet;
    public void setup() {
      keySet = fromTo(key, key + 7);
      cache.removeAll(keySet);
    }
    public CompletableFuture<Void> actor1() {
      return cache.loadAll(fromTo(key, key + 5));
    }
    public CompletableFuture<Void> actor2() {
      return cache.loadAll(fromTo(key + 2, key + 7));
    }
    public void check(CompletableFuture<Void> r1, CompletableFuture<Void> r2) {
      try {
        if (this.hashCode() % 2 == 0) {
          r1.get();
          r2.get();
        } else {
          r2.get();
          r1.get();
        }
      } catch (Exception e) {
        throw new Error(e);
      }
      checkResult(keySet, cache.peekAll(keySet));
    }
  }

}
