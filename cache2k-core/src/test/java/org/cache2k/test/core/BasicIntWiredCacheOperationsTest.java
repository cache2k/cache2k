package org.cache2k.test.core;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.IntCache;
import org.cache2k.core.InternalCache;
import org.cache2k.testing.category.FastTests;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Category;

import java.util.concurrent.TimeUnit;

/**
 * Variant on top of wired cache.
 */
@Category(FastTests.class)
public class BasicIntWiredCacheOperationsTest extends BasicIntCacheOperationsTest {

  static IntCache<Integer> staticCache;

  /**
   * Add an empty removal listener to trigger the switch to the
   * wired cache implementation.
   */
  @BeforeClass
  public static void setUp() {
    Cache2kBuilder<Integer, Integer> _builder = Cache2kBuilder.of(Integer.class, Integer.class)
      .name(BasicIntWiredCacheOperationsTest.class)
      .eternal(true)
      .retryInterval(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
      .permitNullValues(true)
      .entryCapacity(1000);
    StaticUtil.enforceWiredCache(_builder);
    IntCache<Integer>  c = _builder.buildForIntKey();
    staticCache = c;
  }

  @Before
  public void initCache() {
    cache = staticCache;
    statistics().reset();
  }

  @After
  public void cleanupCache() {
    staticCache.requestInterface(InternalCache.class).checkIntegrity();
    cache.clear();
  }

  @AfterClass
  public static void tearDown() {
    staticCache.close();
  }

}
