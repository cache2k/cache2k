package org.cache2k.test.core;

/*
 * #%L
 * cache2k core
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
import org.cache2k.CacheEntry;
import org.cache2k.event.CacheEntryCreatedListener;
import org.cache2k.integration.CacheLoader;
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
 * @see org.cache2k.core.WiredCache
 */
@Category(FastTests.class)
public class CacheLoaderWiredCacheTest extends CacheLoaderTest {

  @Override
  protected Cache2kBuilder<Integer, Integer> builder() {
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
