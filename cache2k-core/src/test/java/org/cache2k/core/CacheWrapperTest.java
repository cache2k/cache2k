package org.cache2k.core;

/*
 * #%L
 * cache2k core implementation
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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.ForwardingCache;
import org.cache2k.config.CacheBuildContext;
import org.cache2k.config.CacheWrapper;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class CacheWrapperTest {

  @Test
  public void tracing() {
    Cache<Integer, Integer> cache =
      Cache2kBuilder.of(Integer.class, Integer.class)
        .setup(b -> b.config().setTraceCacheWrapper(new CacheWrapper() {
          @Override
          public <K, V> Cache<K, V> wrap(CacheBuildContext<K, V> context, Cache<K, V> cache) {
            return new ForwardingCache<K, V>() {
              @Override
              protected Cache<K, V> delegate() {
                return cache;
              }

              @Override
              public String toString() {
                return "tracing";
              }
            };
          }
        })).build();
    cache.put(1, 1);
    assertEquals("tracing", cache.toString());
    assertSame(cache, cache.getCacheManager().getCache(cache.getName()));
    cache.close();
  }

  @Test
  public void general() {
    Cache<Integer, Integer> cache =
      Cache2kBuilder.of(Integer.class, Integer.class)
        .setup(b -> b.config().setCacheWrapper(new CacheWrapper() {
          @Override
          public <K, V> Cache<K, V> wrap(CacheBuildContext<K, V> context, Cache<K, V> cache) {
            return new ForwardingCache<K, V>() {
              @Override
              protected Cache<K, V> delegate() {
                return cache;
              }

              @Override
              public String toString() {
                return "general";
              }
            };
          }
        })).build();
    cache.put(1, 1);
    assertEquals("general", cache.toString());
    assertSame(cache, cache.getCacheManager().getCache(cache.getName()));
    cache.close();
  }

}
