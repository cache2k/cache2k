package org.cache2k.test.core;

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
import org.cache2k.core.InternalCache;
import org.cache2k.junit.FastTests;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Category;

/**
 * Reuse the basic operations test with the wrapper to the entry processor.
 *
 * @see BasicCacheOperationsTest
 * @see EntryProcessorCacheWrapper
 */
@Category(FastTests.class)
public class BasicCacheOperationsWithEntryProcessorTest extends BasicCacheOperationsTest {

  static EntryProcessorCacheWrapper<Integer, Integer> staticCache;

  /*
   * Disable statistics, statistics are counted differently with use of the entry processor.
   * With the entry processor usage, an invoke*() always counts as read, so a put() via entry processor
   * has always other statistics results.
   */
  {
    statistics = new Statistics(true);
  }

  @BeforeClass
  public static void setUp() {
    Cache<Integer, Integer>  c = Cache2kBuilder
            .of(Integer.class, Integer.class)
            .name(BasicCacheOperationsTest.class)
            .eternal(true)
            .entryCapacity(1000)
            .build();
    staticCache = new EntryProcessorCacheWrapper<Integer, Integer>(c);
  }

  @Before
  public void initCache() {
    cache = staticCache;
    statistics().reset();
  }

  @After
  public void cleanupCache() {
    ((InternalCache) staticCache.getWrappedCache()).checkIntegrity();
    cache.clear();
  }

  @AfterClass
  public static void tearDown() {
    staticCache.close();
  }

}
