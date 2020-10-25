package org.cache2k.testsuite.stress;

/*
 * #%L
 * cache2k testsuite on public API
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.testing.category.SlowTests;
import org.cache2k.testsuite.support.Loaders;
import org.cache2k.testsuite.support.StaticUtil;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Jens Wilke
 */
@RunWith(Parameterized.class)
@Category(SlowTests.class)
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

}
