package org.cache2k.core.test;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import org.cache2k.Cache;
import org.cache2k.CacheBuilder;
import org.cache2k.impl.InternalCache;
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

  @BeforeClass
  public static void setUp() {
    Cache<Integer, Integer>  c = CacheBuilder
            .newCache(Integer.class, Integer.class)
            .name(BasicCacheOperationsTest.class)
            .eternal(true)
            .entryCapacity(1000)
            .build();
    staticCache = new EntryProcessorCacheWrapper<Integer, Integer>(c);
  }

  @Before
  public void initCache() {
    cache = staticCache;
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
