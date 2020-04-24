package org.cache2k.extra.spring;

/*
 * #%L
 * cache2k JCache provider
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
import org.cache2k.CacheEntry;
import org.cache2k.integration.AdvancedCacheLoader;
import org.cache2k.integration.CacheLoaderException;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.Callable;

import static org.junit.Assert.*;

/**
 * Some extra tests not covered by {@link SpringCache2kCacheTests}
 *
 * @author Jens Wilke
 */
public class ExtraSpringCache2kCacheTest {

  SpringCache2kCache cache;

  @After
  public void tearDown() {
    if (cache != null) {
      cache.getNativeCache().close();
    }
  }
  protected SpringCache2kCache getCache() {
    return cache =
      new SpringCache2kCacheManager()
        .defaultSetup(b->b.entryCapacity(10_000))
        .addCaches(b->b.name(SpringCache2kCacheTests.class.getSimpleName()))
        .getCache(SpringCache2kCacheTests.class.getSimpleName());
  }

  @Test
  public void testNoLoadingCache() {
    assertFalse(getCache().isLoaderPresent());
  }

  /**
   * Missing from the generic tests
   */
  @Test
  public void testEvict() {
    SpringCache2kCache cache = getCache();
    String key = AbstractCacheTests.createRandomKey();
    Object value = "george";
    cache.put(key, value);
    assertEquals(value, cache.get(key).get());
    cache.evict(key);
    assertNull(cache.get(key));
  }

  /**
   * Missing from the generic tests
   */
  @Test(expected = IllegalStateException.class)
  public void testTypeCheck() {
    SpringCache2kCache cache = getCache();
    String key = AbstractCacheTests.createRandomKey();
    Object value = "george";
    cache.put(key, value);
    cache.get(key, Integer.class);
  }

  @Test
  public void testLoadingCache() {
    SpringCache2kCache cacheWithLoader =
      new SpringCache2kCacheManager().addCache(
        Cache2kBuilder.forUnknownTypes()
          .name(ExtraSpringCache2kCacheTest.class.getSimpleName() + "-withLoader")
          .loader(key -> "123"));
    assertTrue(cacheWithLoader.isLoaderPresent());
    cacheWithLoader.getNativeCache().close();
  }

  @Test(expected = CacheLoaderException.class)
  public void testLoadingCacheWithException() throws Exception {
    SpringCache2kCache cacheWithLoader =
      new SpringCache2kCacheManager().addCache(
        Cache2kBuilder.forUnknownTypes()
          .name(ExtraSpringCache2kCacheTest.class.getSimpleName() + "-withLoaderException")
          .loader(key -> { throw new IOException("ouch"); }));
    try {
      cacheWithLoader.get("123", (Callable) null);
    } finally {
      cacheWithLoader.getNativeCache().close();
    }
  }

  @Test
  public void testLoadingCacheAdvancedLoader() {
    SpringCache2kCache cacheWithLoader =
      new SpringCache2kCacheManager().addCache(
        Cache2kBuilder.forUnknownTypes()
          .name(ExtraSpringCache2kCacheTest.class.getSimpleName() + "-withAdvancedLoader")
          .loader(new AdvancedCacheLoader() {
            @Override
            public Object load(
              final Object key, final long startTime, final CacheEntry currentEntry) {
              return "123";
            }
          }));
    assertTrue(cacheWithLoader.isLoaderPresent());
    Object v = cacheWithLoader.get("321", new Callable<Object>() {
      @Override
      public Object call() throws Exception {
        fail("this is never called");
        return null;
      }
    });
    assertEquals("123", v);
    cacheWithLoader.getNativeCache().close();
  }

}
