package org.cache2k.test.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2017 headissue GmbH, Munich
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
import org.cache2k.test.util.CacheRule;
import org.cache2k.test.util.IntCacheRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Simply test with different segment sizes.
 *
 * @author Jens Wilke
 */
public class EvictionSegmentCountTest {

  /** Provide unique standard cache per method */
  @Rule
  public IntCacheRule target = new IntCacheRule();

  @Test
  public void test_123_10k() {
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b .entryCapacity(10000)
          .evictionSegmentCount(123);
      }
    });
    for (int i = 0; i < 100; i++) {
      c.put(i, 123);
    }
  }

  @Test
  public void test_1_10k() {
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b .entryCapacity(10000)
          .evictionSegmentCount(1);
      }
    });
    for (int i = 0; i < 100; i++) {
      c.put(i, 123);
    }
  }

}
