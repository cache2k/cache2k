package org.cache2k.test.stress;

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
import org.cache2k.Cache2kBuilder;
import org.cache2k.pinpoint.stress.pairwise.ActorPairSuite;
import org.cache2k.test.util.TestingBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Jens Wilke
 */
public class PairwiseTestingBase extends TestingBase {

  private final BuilderAugmenter augmenter;

  public PairwiseTestingBase(Object obj) {
    augmenter = (BuilderAugmenter) obj;
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test() {
    Cache<Integer, Integer> c = augmenter.augment(builder(Integer.class, Integer.class))
      .build();
    ActorPairSuite suite = new ActorPairSuite();
    suite.maxParallel(5);
    int count = 0;
    for (int i = 0; i < 3; i++) {
      for (CacheKeyActorPair<?, Integer, Integer> p : actorPairs(this.getClass())) {
        suite.addPair(p.setCache(c).setKey(count++));
      }
    }
    suite.run();
  }

  Iterable<CacheKeyActorPair<?, Integer, Integer>> actorPairs(Class<?> c) {
    List<CacheKeyActorPair<?, Integer, Integer>> l =
      new ArrayList<CacheKeyActorPair<?, Integer, Integer>>();
    for (Class<?> inner : c.getDeclaredClasses()) {
      if (CacheKeyActorPair.class.isAssignableFrom(inner)) {
        try {
          l.add(((CacheKeyActorPair<?, Integer, Integer>) inner.newInstance()));
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
    return l;
  }

  interface BuilderAugmenter {
    <K, V> Cache2kBuilder<K, V> augment(Cache2kBuilder<K, V> b);
  }

}
