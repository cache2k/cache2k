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
import org.cache2k.pinpoint.stress.pairwise.AbstractActorPair;
import org.cache2k.pinpoint.stress.pairwise.ActorPairSuite;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test atomic operations on a {@link ConcurrentMap} concurrently.
 *
 * @author Jens Wilke
 */
public class ConcurrentMapStressTest {

  public static final Set<Class<? extends MyActorPair>> ACTOR_PAIRS = new HashSet<>();

  @SuppressWarnings("unchecked")
  @Test
  public void test() throws Exception {
    ConcurrentMap<Integer, Integer> map;
    boolean useMap = true;
    if (useMap) {
      map = new ConcurrentHashMap<>();
    } else {
      map = Cache2kBuilder.of(Integer.class, Integer.class).build().asMap();
    }
    ActorPairSuite suite = new ActorPairSuite();
    suite.maxParallel(5);
    int count = 0;
    for (int i = 0; i < 3; i++) {
      for (Class<? extends MyActorPair> c : ACTOR_PAIRS) {
        Target target = new Target(map, count++);
        suite.addPair(c.getConstructor(Target.class).newInstance(target));
      }
    }
    suite.run();
  }

  public static class Compute1 extends MyActorPair {
    AtomicInteger counter1 = new AtomicInteger();
    AtomicInteger counter2 = new AtomicInteger();
    public Compute1(Target target) { super(target); }
    public void setup() {
      counter1.set(0);
      counter2.set(0);
      remove();
    }
    public Integer actor1() {
      return map.compute(key, (key, value) -> {
        counter1.getAndIncrement(); return value == null ? 1 : 11;
      });
    }
    public Integer actor2() {
      return map.compute(key, (key, value) -> {
        counter2.getAndIncrement(); return value == null ? 2 : 22;
      });
    }
    public void check(Integer result1, Integer result2) {
      assertEquals("actor1 compute called once", 1, counter1.get());
      assertEquals("actor2 compute called once", 1, counter2.get());
      assertTrue(value() > 10);
      assertTrue(!(result1 == 1) || value() == 22);
      assertTrue(!(result2 == 2) || value() == 11);
    }
  }
  static { ACTOR_PAIRS.add(Compute1.class); }

  public static class Compute2 extends MyActorPair {
    AtomicInteger counter1 = new AtomicInteger();
    public Compute2(Target target) { super(target); }
    public void setup() {
      counter1.set(0);
      remove();
    }
    public Integer actor1() {
      return map.compute(key, (key, value) -> {
        counter1.getAndIncrement(); return value == null ? 1 : 11;
      });
    }
    public Integer actor2() {
      return map.putIfAbsent(key, 2);
    }
    public void check(Integer result1, Integer result2) {
      assertEquals("actor1 compute called once", 1, counter1.get());
      assertThat(result2).isIn(null, 1);
    }
  }
  static { ACTOR_PAIRS.add(Compute2.class); }

  public static class Compute3 extends MyActorPair {
    AtomicInteger counter1 = new AtomicInteger();
    AtomicInteger counter2 = new AtomicInteger();
    public Compute3(Target target) { super(target); }
    public void setup() {
      counter1.set(0); counter2.set(0); put(0);
    }
    public Integer actor1() {
      return map.compute(key, (key, value) -> {
        counter1.getAndIncrement(); return value == 0 ? 1 : 11;
      });
    }
    public Integer actor2() {
      return map.compute(key, (key, value) -> {
        counter2.getAndIncrement(); return value == 0 ? 2 : 22;
      });
    }
    public void check(Integer result1, Integer result2) {
      assertEquals("actor1 compute called once", 1, counter1.get());
      assertEquals("actor2 compute called once", 1, counter2.get());
      assertTrue(value() > 10);
      assertTrue(!(result1 == 1) || value() == 22);
      assertTrue(!(result2 == 2) || value() == 11);
    }
  }
  static { ACTOR_PAIRS.add(Compute3.class); }

  /** Value changes only once */
  public static class Compute4 extends MyActorPair {
    AtomicInteger counter1 = new AtomicInteger();
    public Compute4(Target target) { super(target); }
    public void setup() {
      counter1.set(0); put(0);
    }
    public Integer actor1() {
      Integer result = map.compute(key, (key, value) -> {
        counter1.getAndIncrement(); return value == 0 ? 1 : 11;
      });
      assertEquals(1, (int) result);
      assertEquals(result, value());
      return result;
    }
    public Integer actor2() {
      Integer v1 = value();
      Integer v2 = value();
      assertTrue(!(v1 == 1) || (v2 == 1));
      assertThat(v1).isIn(0, 1);
      assertThat(v2).isIn(0, 1);
      return 0;
    }
    public void check(Integer result1, Integer result2) {
      assertEquals("actor1 compute called once", 1, counter1.get());
      assertEquals(1, (int) result1);
    }
  }
  static { ACTOR_PAIRS.add(Compute4.class); }

  public abstract static class MyActorPair extends AbstractActorPair<Integer, Target> {
    public final ConcurrentMap<Integer, Integer> map;
    public final Integer key;
    public MyActorPair(Target target) {
      super(target);
      this.key = target.key;
      this.map = target.map;;
    }
    Integer value() { return map.get(key); }
    public void put(Integer value) { map.put(key, value); }
    public void remove() { map.remove(key); }
  }

  public static class Target {
    ConcurrentMap<Integer, Integer> map;
    Integer key;
    public Target(ConcurrentMap<Integer, Integer> map, Integer key) {
      this.map = map;
      this.key = key;
    }
  }

}
