package org.cache2k.core.eviction;

/*-
 * #%L
 * cache2k core implementation
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
import org.cache2k.testing.category.FastTests;
import org.junit.experimental.categories.Category;

/**
 * Run simple access patterns that provide test coverage on the clock pro
 * eviction. Tests with weigher. A weigher returning a weight of 1 is expected
 * to produce the identical results like working with entry counts.
 *
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class ClockProEvictionWithWeigherTest extends ClockProEvictionTest {

  protected Cache<Integer, Integer> provideCache(long size) {
    return builder(Integer.class, Integer.class)
      .eternal(true)
      .entryCapacity(-1) // reset capacity, capacity is already set via builder()
      .weigher((key, value) -> 1)
      .maximumWeight(size)
      .build();
  }

}
