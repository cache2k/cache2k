package org.cache2k.addon;

/*-
 * #%L
 * cache2k addon
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
import org.cache2k.CustomizationException;
import org.cache2k.io.AsyncBulkCacheLoader;
import org.cache2k.io.AsyncCacheLoader;
import org.cache2k.operation.TimeReference;
import org.cache2k.pinpoint.ExpectedException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

import static java.lang.Long.MAX_VALUE;
import static java.lang.Thread.currentThread;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.cache2k.Cache2kBuilder.of;
import static org.cache2k.operation.TimeReference.DEFAULT;

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
      50, // maximum batch size
      false // refresh only
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
      50, // batch size
      false // refresh only
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

  @Test
  public void missingBulkLoader() {
    assertThatCode(() -> {
      Cache2kBuilder.of(Integer.class, Integer.class)
        .setup(CoalescingBulkLoaderSupport::enable).build();
    }).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void nonBulkLoader() {
    assertThatCode(() -> {
      Cache2kBuilder.of(Integer.class, Integer.class)
        .loader((AsyncCacheLoader<Integer, Integer>) (key, context, callback) -> {})
        .setup(CoalescingBulkLoaderSupport::enable).build();
      }
    ).isInstanceOf(CustomizationException.class);
  }

  /**
   * Init via declarative config scheme. We issue multiple loads with one key and
   * expect that the bulk loader is called with more than one key, so coalescing effective.
   */
  @Test
  public void testDeclarative() throws Exception {
    final int maxLoadSize = 3;
    IdentBulkLoader bulkLoader = new IdentBulkLoader();
    Cache<Integer, Integer> cache = of(Integer.class, Integer.class)
      .bulkLoader(bulkLoader)
      .enableWith(CoalescingBulkLoaderSupport.class, b -> b
        .maxBatchSize(maxLoadSize)
        .maxDelay(2000, MILLISECONDS)
        .refreshOnly(false)
      )
      .build();
    Set<CompletableFuture<Void>> waitSet = new HashSet<>();
    for (int i = 0; i < maxLoadSize; i++) {
      waitSet.add(cache.loadAll(asList(i)));
    }
    for (CompletableFuture<Void> future : waitSet) {
      future.get();
    }
    assertThat(bulkLoader.getMaxBulkRequestSize() > 1).isTrue();
    cache.close();
  }

  @Test
  public void testDeclarative_refreshOnly() throws Exception {
    final int maxLoadSize = 3;
    IdentBulkLoader bulkLoader = new IdentBulkLoader();
    Cache<Integer, Integer> cache = of(Integer.class, Integer.class)
      .bulkLoader(bulkLoader)
      .enableWith(CoalescingBulkLoaderSupport.class, b -> b
        .maxBatchSize(maxLoadSize)
        .maxDelay(2000, MILLISECONDS)
        .refreshOnly(true)
      )
      .build();
    Set<CompletableFuture<Void>> waitSet = new HashSet<>();
    for (int i = 0; i < maxLoadSize; i++) {
      waitSet.add(cache.loadAll(asList(i)));
    }
    for (CompletableFuture<Void> future : waitSet) {
      future.get();
    }
    assertThat(bulkLoader.getMaxBulkRequestSize()).isEqualTo(1);
    cache.close();
  }

  public static class IdentBulkLoader implements AsyncBulkCacheLoader<Integer, Integer> {
    private final AtomicInteger maxBulkRequestSize = new AtomicInteger();
    @Override
    public void loadAll(Set<Integer> keys, BulkLoadContext<Integer, Integer> context, BulkCallback<Integer, Integer> callback) {
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
    Thread mainThread = currentThread();
    AtomicInteger timerThreadCounter = new AtomicInteger();
    Cache<Integer, Integer> cache = of(Integer.class, Integer.class)
      .bulkLoader((keys, context, callback) -> {
        if (currentThread() != mainThread) {
          timerThreadCounter.incrementAndGet();
          assertThat(keys.size())
            .as("Batch size 2, when run by timer it is one remaining")
            .isEqualTo(1);
          assertThat(keys.stream().findFirst().get()).isIn(1, 13);
        }
        bulkLoader.loadAll(keys, context, callback);
      })
      .enableWith(CoalescingBulkLoaderSupport.class, b -> b
        .maxBatchSize(maxBatchSize)
        .maxDelay(0, MILLISECONDS)
        .refreshOnly(false)
      )
      .build();
    CompletableFuture<Void> req1 = cache.loadAll(asList(11, 12, 13));
    req1.get();
    assertThat(bulkLoader.getMaxBulkRequestSize()).isEqualTo(maxBatchSize);
    assertThat(timerThreadCounter.get())
      .as("Timer run twice, for key 13")
      .isEqualTo(1);
    cache.close();
  }

  @Test
  public void constructor() {
    assertThatCode(() -> {
      CoalescingBulkLoader<Integer, Integer> coalescingLoader = new CoalescingBulkLoader<>(
        null, TimeReference.DEFAULT, Long.MAX_VALUE, 100, false);
      }
    ).isInstanceOf(NullPointerException.class);
  }

  @Test
  public void config() {
    CoalescingBulkLoaderConfig cfg = new CoalescingBulkLoaderConfig();
    assertThat(cfg == cfg.builder().config()).isTrue();
  }

  @Test
  public void checkLoaderContext() throws ExecutionException, InterruptedException {
    TimeReference clock = DEFAULT;
    AtomicBoolean checked = new AtomicBoolean();
    AtomicReference<Cache> cacheRef = new AtomicReference<>();
    CountDownLatch release = new CountDownLatch(1);
    long startMillis = clock.ticks();
    AsyncBulkCacheLoader<Integer, Integer> loader = (keys, context, callback) -> {
      assertThat(keys.size()).isEqualTo(2);
      assertThat(context.getStartTime() >= startMillis).isTrue();
      assertThat(context.getKeys() == keys).isTrue();
      assertThat(context.getCache() == cacheRef.get()).isTrue();
      assertThat(context.getContextMap()).isNotNull();
      assertThat(context.getContextMap().size() == keys.size()).isTrue();
      assertThat(context.getLoaderExecutor()).isNotNull();
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
      new CoalescingBulkLoader<>(loader, MAX_VALUE, maxLoadSize, false);
    Cache<Integer, Integer> cache = of(Integer.class, Integer.class)
      .bulkLoader(coalescingLoader)
      .build();
    cacheRef.set(cache);
    CompletableFuture<Void> req1 = cache.loadAll(asList(1, 2));
    coalescingLoader.flush();
    assertThat(checked.get()).isTrue();
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
        } else {
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
      new CoalescingBulkLoader<>(loader, MAX_VALUE, maxLoadSize, false);
    Cache<Integer, Integer> cache = of(Integer.class, Integer.class)
      .bulkLoader(coalescingLoader)
      .build();
    CompletableFuture<Void> req1 = cache.loadAll(asList(1, 2));
    assertThat(req1).hasNotFailed();
    assertThat(req1.isDone()).isFalse();
    CompletableFuture<Void> req2 = cache.loadAll(asList(3));
    assertThat(req2).hasNotFailed();
    assertThat(req2.isDone()).isFalse();
    assertThat(coalescingLoader.getQueueSize())
      .as("requests are queued")
      .isEqualTo(3);
    coalescingLoader.flush();
    assertThat(req1.isDone() && req2.isDone())
      .as("both client requests are completed")
      .isTrue();
    req1 = cache.loadAll(asList(5, 6));
    assertThat(req1).hasNotFailed();
    assertThat(req1.isDone()).isFalse();
    req2 = cache.loadAll(asList(7, 8, 9));
    assertThat(req2).hasNotFailed();
    assertThat(req1.isDone() && req2.isDone())
      .as("request is forwarded when max size reached")
      .isTrue();
    req1 = cache.loadAll(asList(15, 16));
    assertThat(req1).hasNotFailed();
    assertThat(req1.isDone()).isFalse();
    req2 = cache.loadAll(asList(17, 18, 19, 20));
    assertThat(req2).hasNotFailed();
    assertThat(req1.isDone())
      .as("request is forwarded when max size reached")
      .isTrue();
    assertThat(req2.isDone())
      .as("request 2 is not completed yet")
      .isFalse();
    coalescingLoader.flush();
    assertThat(req1.isDone() && req2.isDone())
      .as("both requests are completed")
      .isTrue();
    req1 = cache.loadAll(asList(31, 32, 33, 34));
    assertThat(coalescingLoader.getQueueSize())
      .as("requests are queued")
      .isEqualTo(4);
    req2 = cache.loadAll(asList(IMMEDIATE_EXCEPTION));
    assertThat(req1.isDone() && req2.isDone())
      .as("both requests are completed")
      .isTrue();
    assertThat(req1.isCompletedExceptionally() && req2.isCompletedExceptionally())
      .as("both requests have exception")
      .isTrue();
    req1 = cache.loadAll(asList(41, 42, 43, 44));
    assertThat(coalescingLoader.getQueueSize())
      .as("requests are queued")
      .isEqualTo(4);
    req2 = cache.loadAll(asList(PARTIAL_EXCEPTION_START));
    assertThat(req1.isDone() && req2.isDone())
      .as("both requests are completed")
      .isTrue();
    assertThat(req1.isCompletedExceptionally())
      .as("completed w/o exception")
      .isFalse();
    assertThat(req2.isCompletedExceptionally())
      .as("has exception")
      .isTrue();
    cache.loadAll(asList(51, 52));
    boolean res = coalescingLoader.forwardRequests(true, false);
    assertThat(res)
      .as("stop processing")
      .isFalse();
    res = coalescingLoader.forwardRequests(false, true);
    assertThat(res)
      .as("stop processing")
      .isFalse();
    cache.close();
    assertThat(coalescingLoader.getQueueSize() <= 0)
      .as("queue emptied")
      .isTrue();
  }

}
