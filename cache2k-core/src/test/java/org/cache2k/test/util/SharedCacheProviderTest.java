package org.cache2k.test.util;

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

import net.jcip.annotations.NotThreadSafe;
import org.cache2k.Cache;
import org.cache2k.testing.category.FastTests;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestRule;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jens Wilke
 */
@NotThreadSafe @Category(FastTests.class)
public class SharedCacheProviderTest {

  @SuppressWarnings("Convert2Diamond") // needs Java 9
  @ClassRule
  public static CacheRule<Integer, Integer> target = new CacheRule<Integer, Integer>() { };

  @Rule
  public TestRule alsoPerMethod = target;

  Cache<Integer, Integer> cache = target.cache();

  @Test
  public void test() {
    assertThat(target.cache().getName()).isEqualTo("org.cache2k.test.util.SharedCacheProviderTest");
  }

  @Test
  public void testEmptyAndPut() {
    assertThat(cache.entries().iterator().hasNext()).isFalse();
    cache.put(1,2);
  }

  @Test
  public void testEmptyAndPut2() {
    assertThat(cache.entries().iterator().hasNext()).isFalse();
    cache.put(1,2);
  }

}
