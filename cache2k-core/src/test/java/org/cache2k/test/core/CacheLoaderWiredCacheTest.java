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
import org.cache2k.CacheEntry;
import org.cache2k.event.CacheEntryCreatedListener;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.io.AsyncCacheLoader;
import org.cache2k.io.CacheLoader;
import org.cache2k.testing.category.FastTests;
import org.cache2k.test.util.CacheRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Additional loader tests with listeners. Generally add always a dummy listener
 * to switch to the wiredcache implementation.
 *
 * @author Jens Wilke
 * @see org.cache2k.core.WiredCache
 */
@Category(FastTests.class)
public class CacheLoaderWiredCacheTest extends CacheLoaderTest {

  {
    target.enforceWiredCache();
  }

  @Test
  public void testLoaderWithListener() {
    final AtomicInteger _countCreated =  new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b .loader(new CacheLoader<Integer, Integer>() {
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
          });
      }
    });
    assertEquals(0, _countCreated.get());
    assertEquals((Integer) 10, c.get(5));
    assertEquals(1, _countCreated.get());
    assertEquals((Integer) 20, c.get(10));
    assertFalse(c.containsKey(2));
    assertTrue(c.containsKey(5));
    c.close();
  }

  /**
   * @see CacheLoaderTest#advancedLoaderEntryNotSetIfExpired()
   */
  @Test
  public void asyncLoaderEntryNotSetIfExpired() {
    Cache<Integer, Integer> c = target.cache(new CacheRule.Context<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader(new AsyncCacheLoader<Integer, Integer>() {
          @Override
          public void load(Integer key, Context<Integer, Integer> context,
                           Callback<Integer> callback) {
            assertNull(context.getCurrentEntry());
            callback.onLoadSuccess(key);
          }
        });
      }
    });
    c.get(123);
    c.expireAt(123, ExpiryTimeValues.NOW);
    c.get(123);
  }

  /**
   * @see CacheLoaderTest#advancedLoaderEntrySetIfExpiredWithKeepData()
   */
  @Test
  public void asyncLoaderEntrySetIfExpiredWithKeepData() {
    final AtomicBoolean expectEntry = new AtomicBoolean();
    Cache<Integer, Integer> c = target.cache(new CacheRule.Context<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.keepDataAfterExpired(true);
        b.loader(new AsyncCacheLoader<Integer, Integer>() {
          @Override
          public void load(Integer key, Context<Integer, Integer> context,
                           Callback<Integer> callback) {
            if (expectEntry.get()) {
              assertNotNull(context.getCurrentEntry());
            } else {
              assertNull(context.getCurrentEntry());
            }
            callback.onLoadSuccess(key);
          }
        });
      }
    });
    c.get(123);
    c.expireAt(123, ExpiryTimeValues.NOW);
    expectEntry.set(true);
    c.get(123);
  }

}
