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
import org.cache2k.CustomizationException;
import org.cache2k.io.AsyncBulkCacheLoader;
import org.cache2k.io.AsyncCacheLoader;
import org.cache2k.operation.TimeReference;
import org.cache2k.pinpoint.ExpectedException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.Assert.*;

/**
 * Tests for coalescing bulk loader
 *
 * @author Jens Wilke
 */
public class CoalescingBulkLoaderTest {

  /**
   * Example for question in issue https://github.com/cache2k/cache2k/issues/116
   */
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

  /**
   * Example for documentation
   */
  void exampleDeclarative() {
    Cache<Integer, Integer> cache = Cache2kBuilder.of(Integer.class, Integer.class)
      .bulkLoader((keys, context, callback) -> {
      })
      .refreshAhead(true)
      .enableWith(CoalescingBulkLoaderSupport.class, b -> b
        .maxBatchSize(50)
        .maxDelay(100, TimeUnit.MILLISECONDS))
      .build();
  }

  /**
   * Example for documentation
   */
  void exampleWrap() {
    AsyncBulkCacheLoader<Integer, Integer> bulkLoader = (keys, context, callback) -> {
    };
    CoalescingBulkLoader<Integer, Integer> coalescingLoader = new CoalescingBulkLoader<>(
      bulkLoader,
      100, // delay milliseconds
      50 // batch size
    );
    Cache<Integer, Integer> cache = Cache2kBuilder.of(Integer.class, Integer.class)
      .bulkLoader(coalescingLoader)
      .refreshAhead(true)
      .expireAfterWrite(5, TimeUnit.MINUTES)
      .build();
  }

  @Test
  public void enableDisable() {
    Cache2kBuilder.of(Integer.class, Integer.class)
      .setup(CoalescingBulkLoaderSupport::enable)
      .setup(CoalescingBulkLoaderSupport::disable);
  }

  @Test(expected = IllegalArgumentException.class)
  public void missingBulkLoader() {
    Cache2kBuilder.of(Integer.class, Integer.class)
      .setup(CoalescingBulkLoaderSupport::enable).build();
  }

  @Test(expected = CustomizationException.class)
  public void nonBulkLoader() {
    Cache2kBuilder.of(Integer.class, Integer.class)
      .loader((AsyncCacheLoader<Integer, Integer>) (key, context, callback) -> {})
      .setup(CoalescingBulkLoaderSupport::enable).build();
  }

  /**
   * Init via declarative config scheme. We issue multiple loads with one key and
   * expect that the bulk loader is called with more than one key, so coalescing effective.
   */
  @Test
  public void testDeclarative() {
    final int maxLoadSize = 17;
    IdentBulkLoader bulkLoader = new IdentBulkLoader();
    Cache<Integer, Integer> cache = Cache2kBuilder.of(Integer.class, Integer.class)
      .bulkLoader(bulkLoader)
      .enableWith(CoalescingBulkLoaderSupport.class, b -> b
        .maxBatchSize(maxLoadSize)
        .maxDelay(2000, TimeUnit.MILLISECONDS))
      .build();
    for (int i = 0; i < maxLoadSize; i++) {
      cache.loadAll(asList(i));
    }
    assertTrue(bulkLoader.getMaxBulkRequestSize() > 1);
    cache.close();
  }

  class IdentBulkLoader implements AsyncBulkCacheLoader<Integer, Integer> {
    private final AtomicInteger maxBulkRequestSize = new AtomicInteger();
    @Override
    public void loadAll(Set<Integer> keys, BulkLoadContext<Integer, Integer> context, BulkCallback<Integer, Integer> callback) throws Exception {
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
    }
    public int getMaxBulkRequestSize() { return maxBulkRequestSize.get(); }
  }

  @Test
  public void testWithTimer() throws ExecutionException, InterruptedException {
    int maxBatchSize = 2;
    IdentBulkLoader bulkLoader = new IdentBulkLoader();
    Thread mainThread = Thread.currentThread();
    AtomicInteger timerThreadCounter = new AtomicInteger();
    Cache<Integer, Integer> cache = Cache2kBuilder.of(Integer.class, Integer.class)
      .bulkLoader((keys, context, callback) -> {
        if (Thread.currentThread() != mainThread) {
          timerThreadCounter.incrementAndGet();
          assertEquals(1, keys.size());
          assertThat(keys.stream().findFirst().get()).isIn(1, 13);
        }
        bulkLoader.loadAll(keys, context, callback);
      })
      .enableWith(CoalescingBulkLoaderSupport.class, b -> b
        .maxBatchSize(maxBatchSize)
        .maxDelay(0, TimeUnit.MILLISECONDS))
      .build();
    CompletableFuture<Void> req1 = cache.loadAll(asList(1));
    req1.get();
    req1 = cache.loadAll(asList(11, 12, 13));
    req1.get();
    assertEquals(maxBatchSize, bulkLoader.getMaxBulkRequestSize());
    assertEquals("Timer run twice, for key 1 and 13",2, timerThreadCounter.get());
    cache.close();
  }

  @Test(expected = NullPointerException.class)
  public void constructor() {
    CoalescingBulkLoader<Integer, Integer> coalescingLoader = new CoalescingBulkLoader<>(
      null, TimeReference.DEFAULT, Long.MAX_VALUE, 100);
  }

  @Test
  public void config() {
    CoalescingBulkLoaderConfig cfg = new CoalescingBulkLoaderConfig();
    assertTrue(cfg == cfg.builder().config());
  }

  @Test
  public void checkLoaderContext() throws ExecutionException, InterruptedException {
    TimeReference clock = TimeReference.DEFAULT;
    AtomicBoolean checked = new AtomicBoolean();
    AtomicReference<Cache> cacheRef = new AtomicReference<>();
    CountDownLatch release = new CountDownLatch(1);
    long startMillis = clock.millis();
    AsyncBulkCacheLoader<Integer, Integer> loader = (keys, context, callback) -> {
      assertEquals(2, keys.size());
      assertTrue(context.getStartTime() >= startMillis);
      assertTrue(context.getKeys() == keys);
      assertTrue(context.getCache() == cacheRef.get());
      assertNotNull(context.getContextMap());
      assertTrue(context.getContextMap().size() == keys.size());
      assertNotNull(context.getLoaderExecutor());
      assertThatCode(() -> callback.onLoadSuccess(4711, 4711))
        .isInstanceOf(IllegalStateException.class);
      checked.set(true);
      context.getExecutor().execute(() -> {
        try {
          release.await();
        } catch (InterruptedException e) { }
        for (Integer key : keys) {
          callback.onLoadSuccess(key, key);
        }
      });
    };
    final int maxLoadSize = 5;
    CoalescingBulkLoader<Integer, Integer> coalescingLoader =
      new CoalescingBulkLoader<>(loader, Long.MAX_VALUE, maxLoadSize);
    Cache<Integer, Integer> cache = Cache2kBuilder.of(Integer.class, Integer.class)
      .bulkLoader(coalescingLoader)
      .build();
    cacheRef.set(cache);
    CompletableFuture<Void> req1 = cache.loadAll(asList(1, 2));
    coalescingLoader.doLoad();
    assertTrue(checked.get());
    cache.close();
    release.countDown();
    req1.get();
  }

  public static final int IMMEDIATE_EXCEPTION = 4711;
  public static final int PARTIAL_EXCEPTION_START = 1000;
  public static final int PARTIAL_EXCEPTION_END = 1100;
  public static List<Integer> integerRange(int start, int endExclusive) {
    ArrayList<Integer> ints = new ArrayList<>();
    for (int i = start; i < endExclusive; i++) {
      ints.add(i);
    }
    return ints;
  }

  /**
   * Test single threaded via wrapping. We call the CoalescingBulkLoader directly
   * for checks.
   */
  @Test
  public void coalescingAsyncBulkLoader_singleThread() throws Exception {
    AsyncBulkCacheLoader<Integer, Integer> loader = (keys, context, callback) -> {
      boolean partialException = false;
      Map<Integer, Integer> result = new HashMap<>();
      for (int k : keys) {
        if (k == IMMEDIATE_EXCEPTION) {
          throw new ExpectedException();
        }
        if (k >= PARTIAL_EXCEPTION_START && k <= PARTIAL_EXCEPTION_END) {
          partialException = true;
        } else{
         result.put(k, k);
        }
      }
      callback.onLoadSuccess(result);
      if (partialException) {
        callback.onLoadFailure(new ExpectedException());
      }
    };
    final int maxLoadSize = 5;
    CoalescingBulkLoader<Integer, Integer> coalescingLoader =
      new CoalescingBulkLoader<>(loader, Long.MAX_VALUE, maxLoadSize);
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
    assertTrue("both requests are completed", req1.isDone() && req2.isDone());
    req1 = cache.loadAll(asList(31, 32, 33, 34));
    assertEquals("requests are queued", 4, coalescingLoader.getQueueSize());
    req2 = cache.loadAll(asList(IMMEDIATE_EXCEPTION));
    assertTrue("both requests are completed", req1.isDone() && req2.isDone());
    assertTrue("both requests have exception",
      req1.isCompletedExceptionally() && req2.isCompletedExceptionally());
    req1 = cache.loadAll(asList(41, 42, 43, 44));
    assertEquals("requests are queued", 4, coalescingLoader.getQueueSize());
    req2 = cache.loadAll(asList(PARTIAL_EXCEPTION_START));
    assertTrue("both requests are completed", req1.isDone() && req2.isDone());
    assertFalse("completed w/o exception", req1.isCompletedExceptionally());
    assertTrue("has exception", req2.isCompletedExceptionally());
    cache.close();
    assertTrue("queue emptied", coalescingLoader.getQueueSize() <= 0);
  }

}
