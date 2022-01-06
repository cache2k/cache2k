package org.cache2k.core;

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
import org.cache2k.CacheEntry;
import org.cache2k.ForwardingCache;
import org.cache2k.config.CacheBuildContext;
import org.cache2k.config.CacheWrapper;
import org.cache2k.event.CacheEntryCreatedListener;
import org.cache2k.event.CacheEntryEvictedListener;
import org.cache2k.event.CacheEntryExpiredListener;
import org.cache2k.event.CacheEntryRemovedListener;
import org.cache2k.event.CacheEntryUpdatedListener;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.atomic.AtomicReference;

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

  /**
   * Is wrapped cache reference send to listeners?
   */
  @Test
  public void eventsGetWrappedReference() {
    AtomicReference<Cache> createdEventCache = new AtomicReference<>();
    AtomicReference<Cache> updatedEventCache = new AtomicReference<>();
    AtomicReference<Cache> removedEventCache = new AtomicReference<>();
    AtomicReference<Cache> evictedEventCache = new AtomicReference<>();
    AtomicReference<Cache> expiredEventCache = new AtomicReference<>();
    Cache<Integer, Integer> cache =
      Cache2kBuilder.of(Integer.class, Integer.class)
        .addListener((CacheEntryCreatedListener<Integer, Integer>) (cache2, entry)
          -> createdEventCache.set(cache2))
        .addListener((CacheEntryUpdatedListener<Integer, Integer>) (cache2, currentEntry, newEntry)
          -> updatedEventCache.set(cache2))
        .addListener((CacheEntryRemovedListener<Integer, Integer>) (cache2, entry)
          -> removedEventCache.set(cache2))
        .addListener((CacheEntryEvictedListener<Integer, Integer>) (cache2, entry)
          -> evictedEventCache.set(cache2))
        .addListener((CacheEntryEvictedListener<Integer, Integer>) (cache2, entry)
          -> evictedEventCache.set(cache2))
        .addListener((CacheEntryExpiredListener<Integer, Integer>) (cache2, entry)
          -> expiredEventCache.set(cache2))
        .entryCapacity(10)
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
    cache.put(1, 2);
    cache.remove(1);
    for (int i = 0; i < 100; i++) {
      cache.put(i, i);
    }
    cache.put(1, 1);
    cache.invoke(1, entry -> entry.setExpiryTime(ExpiryTimeValues.NOW));
    assertEquals("general", cache.toString());
    assertSame(cache, cache.getCacheManager().getCache(cache.getName()));
    assertSame("created event got wrapped cache", cache, createdEventCache.get());
    assertSame("updated event got wrapped cache", cache, updatedEventCache.get());
    assertSame("removed event got wrapped cache", cache, removedEventCache.get());
    assertSame("evicted event got wrapped cache", cache, evictedEventCache.get());
    assertSame("expired event got wrapped cache", cache, expiredEventCache.get());
    cache.close();
  }

}
