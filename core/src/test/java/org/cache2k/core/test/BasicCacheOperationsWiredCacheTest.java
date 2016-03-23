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
 * Test the basic operations on the wired cache.
 *
 * @see BasicCacheOperationsTest
 * @see EntryProcessorCacheWrapper
 * @see org.cache2k.impl.WiredCache
 */
@Category(FastTests.class)
public class BasicCacheOperationsWiredCacheTest extends BasicCacheOperationsTest {

  static Cache<Integer, Integer> staticCache;

  /**
   * Add an empty removal listener to trigger the switch to the
   * wired cache implementation.
   */
  @BeforeClass
  public static void setUp() {
    CacheBuilder<Integer, Integer> _builder = CacheBuilder.newCache(Integer.class, Integer.class)
      .name(BasicCacheOperationsTest.class)
      .eternal(true)
      .entryCapacity(1000);
    StaticUtil.enforceWiredCache(_builder);
    Cache<Integer, Integer>  c = _builder.build();
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
