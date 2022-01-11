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
import org.cache2k.pinpoint.stress.pairwise.AbstractActorPair;
import org.cache2k.pinpoint.stress.pairwise.ActorPairSuite;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

/**
 * Test atomic operations on a {@link ConcurrentMap} concurrently.
 * At the moment this focuses on the compute functions.
 *
 * @author Jens Wilke
 */
@SuppressWarnings({"NullAway", "nullness"})
public class ConcurrentMapStressTest {

  public static final Set<Class<? extends MyActorPair>> ACTOR_PAIRS = new HashSet<>();

  @SuppressWarnings("unchecked")
  @Test
  public void test() throws Exception {
    ConcurrentMap<Integer, Integer> map;
    boolean useMap = false;
    if (useMap) {
      map = new ConcurrentHashMap<>();
    } else {
      map = Cache2kBuilder.of(Integer.class, Integer.class)
        .build().asMap();
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
    public Compute1(Target target) { super(target); }
    public void setup() {
      counter1.set(0);
      remove();
    }
    public Integer actor1() {
      return map.compute(key, (key, value) -> {
        counter1.getAndIncrement();
        return value == null ? 1 : 11;
      });
    }
    public Integer actor2() {
      return map.putIfAbsent(key, 2);
    }
    public void check(Integer result1, Integer result2) {
      assertEquals("compute called once", 1, counter1.get());
      assertNotNull(value());
      assertTrue(!(result2 == null) || (value() == 11 && result1 == 11));
      assertTrue(!(result1 == 1) || (value() == 1 && result2 == 1));
    }
  }
  static { ACTOR_PAIRS.add(Compute1.class); }

  public static class ComputeIfPresent1 extends MyActorPair {
    AtomicInteger counter1 = new AtomicInteger();
    public ComputeIfPresent1(Target target) { super(target); }
    public void setup() {
      counter1.set(0);
      put(0);
    }
    public Integer actor1() {
      return map.computeIfPresent(key, (k, v) -> {
        counter1.getAndIncrement();
        return 1;
      });
    }
    public Integer actor2() {
      return map.remove(key, 0) ? 1 : 0;
    }
    public void check(Integer result1, Integer result2) {
      assertThat(counter1.get()).isLessThanOrEqualTo(1);
      assertTrue(!(result2 == 1) || (value() == null && result1 == null));
      assertTrue(!(result1 != null) || (result1 == 1 && value() == 1 && result2 == 0));
    }
  }
  static { ACTOR_PAIRS.add(ComputeIfPresent1.class); }

  public static class ComputeIfAbsent1 extends MyActorPair {
    AtomicInteger counter1 = new AtomicInteger();
    public ComputeIfAbsent1(Target target) { super(target); }
    public void setup() {
      counter1.set(0);
      remove();
    }
    public Integer actor1() {
      return map.computeIfAbsent(key, (k) -> {
        counter1.getAndIncrement();
        return 10;
      });
    }
    public Integer actor2() {
      return map.putIfAbsent(key, 20);
    }
    public void check(Integer result1, Integer result2) {
      assertThat(counter1.get()).isLessThanOrEqualTo(1);
      int v = value();
      assertTrue(result1 == 20 || (result1 == 10 && v == 10 && counter1.get() == 1));
      assertTrue(result1 == 10 || (result1 == 20 && v == 20 && counter1.get() == 0));
      assertTrue(result2 == null || v == 10);
      assertTrue(result2 != null || v == 20);
    }
  }
  static { ACTOR_PAIRS.add(ComputeIfAbsent1.class); }

  public abstract static class MyActorPair extends AbstractActorPair<Integer, Target> {
    public final ConcurrentMap<Integer, Integer> map;
    public final Integer key;
    public MyActorPair(Target target) {
      super(target);
      this.key = target.key;
      this.map = target.map;
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
