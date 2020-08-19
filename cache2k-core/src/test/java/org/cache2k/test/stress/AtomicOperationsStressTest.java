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
import org.cache2k.test.core.StaticUtil;
import org.cache2k.test.util.TestingBase;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;

import static org.junit.runners.Parameterized.Parameters;

/**
 * Concurrent test suite for (some) atomic operations
 *
 * @author Jens Wilke
 */
@RunWith(Parameterized.class)
public class AtomicOperationsStressTest extends TestingBase {

  @Parameters
  public static Iterable<Object[]> data() {
    List l = new ArrayList();
    l.add(new Object[]{new BuilderAugmenter() {
      @Override
      public <K, V> Cache2kBuilder<K, V> augment(final Cache2kBuilder<K, V> b) { return b; }
    }});
    l.add(new Object[]{new BuilderAugmenter() {
      @Override
      public <K, V> Cache2kBuilder<K, V> augment(final Cache2kBuilder<K, V> b) {
        StaticUtil.enforceWiredCache(b); return b;
      }
    }});
    return l;
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

  static class ReplaceIfEquals1 extends CacheKeyActorPair<Boolean, Integer, Integer> {
    public void setup() {
      cache.put(key, 10);
    }
    public Boolean actor1() {
      return cache.replaceIfEquals(key, 10, 20);
    }
    public Boolean actor2() {
      return cache.replaceIfEquals(key, 10, 30);
    }
    public void check(final Boolean r1, final Boolean r2) {
      SuccessTuple r = new SuccessTuple(r1, r2);
      assertTrue("one actor wins", r.isOneSucceeds());
      assertEquals("cached value", r.isSuccess1() ? 20 : 30, (int) value());
    }
  }

  static class ReplaceIfEquals2 extends CacheKeyActorPair<Boolean, Integer, Integer> {
    public void setup() {
      cache.put(key, 10);
    }
    public Boolean actor1() {
      return cache.replaceIfEquals(key, 10, 20);
    }
    public Boolean actor2() {
      return cache.replaceIfEquals(key, 20, 30);
    }
    public void check(final Boolean r1, final Boolean r2) {
      SuccessTuple r = new SuccessTuple(r1, r2);
      assertTrue(!r.isBothSucceed() || value() == 30);
      assertTrue(!r.isOneSucceeds() || (value() == 20 && r1));
    }
  }

  static class PutIfAbsent1 extends CacheKeyActorPair<Boolean, Integer, Integer> {
    public void setup() {
      cache.remove(key);
    }
    public Boolean actor1() {
      return cache.putIfAbsent(key, 10);
    }
    public Boolean actor2() {
      return cache.putIfAbsent(key, 20);
    }
    public void check(final Boolean r1, final Boolean r2) {
      assertTrue(new SuccessTuple(r1, r2).isOneSucceeds());
      assertTrue(!r1 || value() == 10);
      assertTrue(!r2 || value() == 20);
    }
  }

  static class PeekAndReplace1 extends CacheKeyActorPair<Integer, Integer, Integer> {
    public void setup() {
      cache.put(key, 10);
    }
    public Integer actor1() {
      return cache.peekAndReplace(key, 20);
    }
    public Integer actor2() {
      return cache.peekAndReplace(key, 30);
    }
    public void check(final Integer r1, final Integer r2) {
      assertTrue(!(r1 == 30) || (value() == 20 && r2 == 10));
      assertTrue(!(r1 == 10) || (value() == 30 && r2 == 20));
      assertTrue(!(r2 == 20) || (value() == 30 && r1 == 10));
      assertTrue(!(r2 == 10) || (value() == 20 && r1 == 30));
    }
  }

  private BuilderAugmenter augmenter;

  public AtomicOperationsStressTest(Object obj) {
    augmenter = (BuilderAugmenter) obj;
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test() {
    final Cache<Integer, Integer> c = augmenter.augment(builder(Integer.class, Integer.class))
      .build();
    ActorPairSuite suite = new ActorPairSuite();
    suite.maxParallel(5);
    int count = 0;
    for (int i = 0; i <3; i++) {
      for (CacheKeyActorPair<?, Integer, Integer> p : actorPairs(this.getClass())) {
        suite.addPair(p.setCache(c).setKey(count++));
      }
    }
    suite.run();
  }

  interface BuilderAugmenter {
    <K, V> Cache2kBuilder<K, V> augment(Cache2kBuilder<K, V> b);
  }

}
