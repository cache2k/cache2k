package org.cache2k.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.test.util.TimeBox;
import org.cache2k.Cache;
import org.cache2k.junit.TimingTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Tests around creation of the cache info.
 *
 * @author Jens Wilke
 */
@Category(TimingTests.class)
public class CacheInfoTest extends CacheTest {

  @Test
  public void testInfoCreationIsDelayed() throws Exception {
    Cache<Integer, Integer> c = freshCache(null, 1000);
    final InternalCache ic = (InternalCache) c;
    final Set<String> s = new HashSet<String>();
    final int _INVOCATION_COUNT = 10;
    TimeBox.millis(123)
      .work(new Runnable() {
        @Override
        public void run() {
          for (int i = 0; i < _INVOCATION_COUNT; i ++) {
            Thread.yield();
            s.add(ic.getInfo().toString());
          }
        }
      })
      .check(new Runnable() {
        @Override
        public void run() {
          assertEquals(1, s.size());
        }
      });
  }

}
