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

import org.cache2k.Cache;
import org.cache2k.testing.category.SlowTests;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;

/**
 * @author Jens Wilke
 */
@SuppressWarnings({"NullAway", "nullness"})
@Category(SlowTests.class)
public class EntryProcessorPairwiseStressTest extends AtomicOperationsPairwiseStressTest {

  public EntryProcessorPairwiseStressTest(Object obj) {
    super(obj);
  }

  private static void increment(Cache<Integer, Integer> cache, int key, int count) {
    for (int i = 0; i < count; i++) {
      cache.mutate(key, entry -> entry.setValue(entry.getValue() + 1));
    }
  }

  static class EntryProcessorAtomicIncrement extends CacheKeyActorPair<Void, Integer, Integer> {
    int countPerActor = 100;
    public void setup() {
      cache.put(key, 0); }
    public Void actor1() { increment(cache, key, countPerActor); return null; }
    public Void actor2() { increment(cache, key, countPerActor); return null; }
    public void check(Void r1, Void r2) {
      assertEquals("result after concurrent increments", countPerActor * 2, (int) value());
    }
  }

}
