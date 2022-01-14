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

import org.cache2k.Cache;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jens Wilke
 */
public class PerMethodCacheProviderTest {

  @SuppressWarnings("Convert2Diamond") // needs Java 9
  @Rule
  public CacheRule<Integer, Integer> target = new CacheRule<Integer, Integer>() { };

  @Test
  public void testName() {
    Cache<Integer, Integer> c = target.cache();
    assertThat(c.getName()).isEqualTo("org.cache2k.test.util.PerMethodCacheProviderTest.testName");
  }

  @Test
  public void testClose() {
    target.config(b -> b.entryCapacity(123));
    Cache<Integer, Integer> c = target.cache();
    c.close();
  }

}
