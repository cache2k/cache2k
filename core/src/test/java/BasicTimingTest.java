/*
 * #%L
 * cache2k core package
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
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.junit.TimingTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 * Tests that need to run separately to test some assumption on timings of current machines.
 * These tests are not meant to run with the normal tests and should only run on a machine
 * without any other load.
 */
@Category(TimingTests.class)
public class BasicTimingTest {

  /**
   * Test the time to generate the toString() output on a cache that has 1M entries.
   * Needs 3 seconds on 2015 hardware.
   */
  @Test(timeout = 4000)
  public void testBigCacheTiming() {
    final int _CACHE_SIZE = 1000000;
    Cache<Integer,Integer> c =
            Cache2kBuilder.newCache(Integer.class, Integer.class).entryCapacity(_CACHE_SIZE).build();
    for (int i = 0; i < _CACHE_SIZE; i++) {
      c.put(i, i);
    }
    assertNotNull(c.toString());
  }

}
