package org.cache2k.test.core;

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
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheManager;
import org.cache2k.testing.category.FastTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cache2k.Cache2kBuilder.forUnknownTypes;
import static org.cache2k.CacheManager.getInstance;

import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class CacheNameTest {

  /**
   * Needed by JSR107 TCK tests, e.g.: org.jsr107.tck.CacheManagerTest@6fc6f14e
   */
  @Test
  public void atCharacter() {
    Cache2kBuilder.forUnknownTypes().name("CacheNameTest@").build().close();
  }

  /**
   * Needed by JSR107 annotations that create a cache name with
   * {@code org.example.KeyClass,org.example.ValueClass}
   */
  @Test
  public void commaCharacter() {
    Cache2kBuilder.forUnknownTypes().name("CacheNameTest,").build().close();
  }

  /**
   * Needed by JSR107 TCK
   */
  @Test
  public void spaceCharacter() {
    Cache2kBuilder.forUnknownTypes().name("CacheNameTest space").build().close();
  }

  @Test
  public void managerNameInToString() {
    final String _MANAGER_NAME = "managerNameInToString123";
    CacheManager cm = getInstance(_MANAGER_NAME);
    Cache c = forUnknownTypes().manager(cm).build();
    assertThat(c.toString()).contains(_MANAGER_NAME);
    cm.close();
  }

}
