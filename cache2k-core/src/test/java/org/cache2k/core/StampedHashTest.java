package org.cache2k.core;

/*-
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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

import static org.junit.Assert.*;

import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * @author Jens Wilke
 */
@SuppressWarnings("rawtypes")
@Category(FastTests.class)
public class StampedHashTest {

  /**
   * Test expansion and possible integer overflow when calculating maxfill
   *
   * @see <a href="https://github.com/cache2k/cache2k/issues/111">GH#111</a>
   */
  @Test
  public void testExpansion() {
    final StampedHash ht = new StampedHash("test");
    for (int i = 0; i < 20; i++) {
      ht.runTotalLocked(() -> {
        ht.rehash();
        return null;
      });
      assertTrue(ht.getSegmentMaxFill() >= 0);
    }
  }

}
