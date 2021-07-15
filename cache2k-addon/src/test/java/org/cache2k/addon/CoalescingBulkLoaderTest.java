package org.cache2k.addon;

/*
 * #%L
 * cache2k addon
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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
import org.cache2k.io.AsyncBulkCacheLoader;
import org.cache2k.io.AsyncCacheLoader;
import org.cache2k.operation.TimeReference;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
public class CoalescingBulkLoaderTest {

  void endlessRefresh() {
    AsyncBulkCacheLoader<Integer, Integer> bulkLoader = (keys, context, callback) -> {
    };
    CoalescingBulkLoader<Integer, Integer> coalescingLoader = new CoalescingBulkLoader<>(
      bulkLoader,
      TimeReference.DEFAULT, // the cache might have a different time reference
      100, // delay milliseconds before sending the request
      50 // maximum batch size
    );
    AtomicReference<Cache<Integer, Integer>> cacheRef = new AtomicReference<>();
    Cache<Integer, Integer> cache = Cache2kBuilder.of(Integer.class, Integer.class)
      .loader((AsyncCacheLoader<Integer, Integer>) (key, context, callback) -> {
        if (context.getCurrentEntry() == null) {
          coalescingLoader.load(key, context, callback);
        } else {
          coalescingLoader.load(key, context, new AsyncCacheLoader.Callback<Integer>() {
            @Override
            public void onLoadSuccess(Integer value) { cacheRef.get().put(key, value); }
            @Override
            public void onLoadFailure(Throwable t) {
            }
          });
          callback.onLoadSuccess(context.getCurrentEntry().getValue());
        }
      })
      .refreshAhead(true)
      .expireAfterWrite(5, TimeUnit.MINUTES) // trigger a refresh every 5 minutes
      .expiryPolicy((key, value, loadTime, currentEntry) -> value == null ? 0 : Long.MAX_VALUE)
      .build();
    cacheRef.set(cache);
  }

  void exampleDeclarative() {
    Cache<Integer, Integer> cache = Cache2kBuilder.of(Integer.class, Integer.class)
      .bulkLoader((keys, context, callback) -> {
      })
      .refreshAhead(true)
      .enableWith(CoalescingBulkLoaderSupport.class, b -> b
        .maxLoadSize(50)
        .maxDelay(100, TimeUnit.MILLISECONDS))
      .build();
  }

  void exampleWrap() {
    AsyncBulkCacheLoader<Integer, Integer> bulkLoader = (keys, context, callback) -> {
    };
    CoalescingBulkLoader<Integer, Integer> coalescingLoader = new CoalescingBulkLoader<>(
      bulkLoader,
      TimeReference.DEFAULT,
      100, // delay milliseconds
      50 // batch size
    );
    Cache<Integer, Integer> cache = Cache2kBuilder.of(Integer.class, Integer.class)
      .bulkLoader(coalescingLoader)
      .refreshAhead(true)
      .expireAfterWrite(5, TimeUnit.MINUTES)
      .build();
  }

  /**
   * Init via declarative config scheme. We issue a load with only one key and
   * expect that the bulk loader is called with more than one key, so coalescing effective.
   */
  @Test
  public void testDeclarative() {
    final int maxLoadSize = 17;
    AtomicInteger maxBulkRequestSize = new AtomicInteger();
    Cache<Integer, Integer> cache = Cache2kBuilder.of(Integer.class, Integer.class)
      .bulkLoader((keys, context, callback) -> {
        int currentMax;
        do {
          currentMax = maxBulkRequestSize.get();
          if (keys.size() < currentMax) {
            break;
          }
        } while(!maxBulkRequestSize.compareAndSet(currentMax, keys.size()));
        Map<Integer, Integer> result = new HashMap<>();
        for (int k : keys) {
          result.put(k, k);
        }
        callback.onLoadSuccess(result);
      })
      .refreshAhead(true)
      .expireAfterWrite(5, TimeUnit.MINUTES)
      .enableWith(CoalescingBulkLoaderSupport.class, b -> b
        .maxLoadSize(maxLoadSize)
        .maxDelay(2000, TimeUnit.MILLISECONDS))
      .build();
    for (int i = 0; i < maxLoadSize; i++) {
      cache.loadAll(asList(i));
    }
    assertTrue(maxBulkRequestSize.get() > 1);
    cache.close();
  }

  /**
   * Test single threaded via wrapping. We call the CoalescingBulkLoader directly
   * for checks.
   */
  @Test
  public void coalescingAsyncBulkLoader_singleThread() throws Exception {
    AsyncBulkCacheLoader<Integer, Integer> loader = (keys, context, callback) -> {
      Map<Integer, Integer> result = new HashMap<>();
      for (int k : keys) {
        result.put(k, k);
      }
      callback.onLoadSuccess(result);
    };
    final int maxLoadSize = 5;
    CoalescingBulkLoader<Integer, Integer> coalescingLoader = new CoalescingBulkLoader<>(
      loader, TimeReference.DEFAULT, Long.MAX_VALUE, maxLoadSize
    );
    Cache<Integer, Integer> cache = Cache2kBuilder.of(Integer.class, Integer.class)
      .bulkLoader(coalescingLoader)
      .build();
    CompletableFuture<Void> req1 = cache.loadAll(asList(1, 2));
    assertThat(req1).hasNotFailed();
    assertFalse(req1.isDone());
    CompletableFuture<Void> req2 = cache.loadAll(asList(3));
    assertThat(req2).hasNotFailed();
    assertFalse(req2.isDone());
    assertEquals("requests are queued", 3, coalescingLoader.getQueueSize());
    coalescingLoader.doLoad();
    assertTrue("both client requests are completed",
      req1.isDone() && req2.isDone());
    req1 = cache.loadAll(asList(5, 6));
    assertThat(req1).hasNotFailed();
    assertFalse(req1.isDone());
    req2 = cache.loadAll(asList(7, 8, 9));
    assertThat(req2).hasNotFailed();
    assertTrue("request is forwarded when max size reached",
      req1.isDone() && req2.isDone());
    req1 = cache.loadAll(asList(15, 16));
    assertThat(req1).hasNotFailed();
    assertFalse(req1.isDone());
    req2 = cache.loadAll(asList(17, 18, 19, 20));
    assertThat(req2).hasNotFailed();
    assertTrue("request is forwarded when max size reached", req1.isDone());
    assertFalse("request 2 is not completed yet", req2.isDone());
    coalescingLoader.doLoad();
    assertTrue("both client requests are completed",
      req1.isDone() && req2.isDone());
    req2 = cache.loadAll(asList(33, 34));
    assertEquals("requests are queued", 2, coalescingLoader.getQueueSize());
    cache.close();
    assertTrue("queue emptied", coalescingLoader.getQueueSize() <= 0);
  }

}
