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
import org.cache2k.CacheEntry;
import org.cache2k.CacheEntryCreatedListener;
import org.cache2k.CacheLoader;
import org.cache2k.junit.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Additional loader tests with listeners. Generally add always a dummy listener
 * to switch to the wiredcache implementation.
 *
 * @author Jens Wilke
 * @see org.cache2k.impl.WiredCache
 */
@Category(FastTests.class)
public class CacheLoaderWiredCacheTest extends CacheLoaderTest {

  @Override
  protected CacheBuilder<Integer, Integer> builder() {
    return StaticUtil.enforceWiredCache(super.builder());
  }

  @Test
  public void testLoaderWithListener() {
    final AtomicInteger _countCreated =  new AtomicInteger();
    Cache<Integer,Integer> c = builder()
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          return key * 2;
        }
      })
      .addListener(new CacheEntryCreatedListener<Integer, Integer>() {
        @Override
        public void onEntryCreated(final Cache<Integer, Integer> c, final CacheEntry<Integer, Integer> e) {
          _countCreated.incrementAndGet();
        }
      })
      .build();
    assertEquals(0, _countCreated.get());
    assertEquals((Integer) 10, c.get(5));
    assertEquals(1, _countCreated.get());
    assertEquals((Integer) 20, c.get(10));
    assertFalse(c.contains(2));
    assertTrue(c.contains(5));
    c.close();
  }

}
