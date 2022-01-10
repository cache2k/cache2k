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
import org.cache2k.testing.category.FastTests;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static java.util.Collections.singleton;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class CacheClosedTest {

  static final Integer VALUE = 1;
  static final Integer KEY = 1;
  static Cache<Integer, Integer> cache;

  @BeforeClass
  public static void setUp() {
    cache = Cache2kBuilder
      .of(Integer.class, Integer.class)
      .name(CacheClosedTest.class)
      .eternal(true)
      .entryCapacity(1000)
      .build();
    cache.close();
  }

  @Test(expected = IllegalStateException.class)
  public void get() {
    cache.get(KEY);
  }

  @Test(expected = IllegalStateException.class)
  public void peek() {
    cache.peek(KEY);
  }

  @Test(expected = IllegalStateException.class)
  public void peekAndRemove() {
    cache.peekAndRemove(KEY);
  }

  @Test(expected = IllegalStateException.class)
  public void peekAll() {
    cache.peekAll(singleton(KEY));
  }

  @Test(expected = IllegalStateException.class)
  public void peekAndPut() {
    cache.peekAndPut(KEY, VALUE);
  }

  @Test(expected = IllegalStateException.class)
  public void peekAndReplace() {
    cache.peekAndReplace(KEY, VALUE);
  }

  /*
   * TODO-B: add tests for remaining methods.
   */

}
