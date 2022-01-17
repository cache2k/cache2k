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

import static java.lang.Thread.currentThread;
import static java.util.Collections.EMPTY_LIST;
import static java.util.Collections.EMPTY_SET;
import static java.util.concurrent.Executors.newCachedThreadPool;

import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.io.AsyncBulkCacheLoader;
import org.cache2k.io.AsyncCacheLoader;
import org.cache2k.io.CacheLoaderException;
import org.cache2k.pinpoint.CaughtInterruptedException;
import org.cache2k.pinpoint.PinpointParameters;
import org.cache2k.pinpoint.TaskSuccessGuardian;
import org.cache2k.pinpoint.ExceptionCollector;
import org.cache2k.pinpoint.SupervisedExecutor;
import org.cache2k.processor.EntryProcessingException;
import org.cache2k.processor.EntryProcessingResult;
import org.cache2k.processor.MutableCacheEntry;
import org.cache2k.test.core.expiry.ExpiryTest;
import org.cache2k.test.util.CacheRule;
import org.cache2k.io.AdvancedCacheLoader;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.io.CacheLoader;
import org.cache2k.test.util.ExpectedException;
import org.cache2k.test.util.TestingBase;
import org.cache2k.testing.category.FastTests;
import org.cache2k.test.util.IntCacheRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.Timeout;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.concurrent.ForkJoinPool.commonPool;
import static java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.cache2k.core.concurrency.ThreadFactoryProvider.DEFAULT;
import static org.cache2k.expiry.ExpiryTimeValues.NOW;
import static org.cache2k.expiry.ExpiryTimeValues.REFRESH;
import static org.cache2k.test.core.TestingParameters.MAX_FINISH_WAIT_MILLIS;
import static org.cache2k.test.core.expiry.ExpiryTest.EnableExceptionCaching;

/**
 * Test the cache loader.
 *
 * @author Jens Wilke
 * @see CacheLoader
 * @see AdvancedCacheLoader
 * @see AsyncCacheLoader
 */
@SuppressWarnings("unchecked")
@Category(FastTests.class)
public class CacheLoaderTest extends TestingBase {

  @Rule
  public CacheRule<Integer, Integer> target = new IntCacheRule();

  @Rule
  public Timeout globalTimeout = new Timeout((int) TestingParameters.MAX_FINISH_WAIT_MILLIS * 2);
  volatile int loaderExecutionCount = 0;

  /**
   * Some tests expect that there are at least two loader threads.
   */
  @Test
  public void testThreadCount() {
    Cache<Integer, Integer> c = target.cache(b -> assertThat(b.config().getLoaderThreadCount())
      .describedAs("minim thread count")
      .isGreaterThanOrEqualTo(2));
  }

  @Test
  public void testSeparateLoaderExecutor() throws ExecutionException, InterruptedException {
    AtomicInteger executionCount = new AtomicInteger(0);
    Cache<Integer, Integer> c = target.cache(b -> {
      b.loader(key -> key * 2);
      b.loaderExecutor(command -> {
        executionCount.incrementAndGet();
        getLoaderExecutor().execute(command);
      });
    });
    assertThat(c.get(5)).isEqualTo((Integer) 10);
    assertThat(c.get(10)).isEqualTo((Integer) 20);
    assertThat(executionCount.get()).isEqualTo(0);
    c.loadAll(asList(1, 2, 3)).get();
    assertThat(executionCount.get())
      .as("executor is used")
      .isEqualTo(3);
  }

  @Test
  public void testSeparatePrefetchExecutor() throws ExecutionException, InterruptedException {
    AtomicInteger executionCount = new AtomicInteger(0);
    AtomicInteger prefetchExecutionCount = new AtomicInteger(0);
    Cache<Integer, Integer> c = target.cache(b -> {
      b.loader(key -> key * 2);
      b.loaderExecutor(command -> {
        executionCount.incrementAndGet();
        getLoaderExecutor().execute(command);
      });
      b.refreshExecutor(command -> {
        prefetchExecutionCount.incrementAndGet();
        getLoaderExecutor().execute(command);
      });
    });
    assertThat(c.get(5)).isEqualTo((Integer) 10);
    assertThat(c.get(10)).isEqualTo((Integer) 20);
    assertThat(executionCount.get()).isEqualTo(0);
    c.loadAll(asList(1, 2, 3)).get();
    assertThat(executionCount.get())
      .as("executor is used")
      .isEqualTo(3);
  }

  @Test
  public void testLoader() {
    Cache<Integer, Integer> c = target.cache(b -> b.loader(key -> key * 2));
    assertThat(c.get(5)).isEqualTo((Integer) 10);
    assertThat(c.get(10)).isEqualTo((Integer) 20);
    assertThat(c.containsKey(2)).isFalse();
    assertThat(c.containsKey(5)).isTrue();
  }

  @Test
  public void testLoadNull() {
    Cache<Integer, Integer> c = target.cache(b -> b.loader(key -> null)
      .permitNullValues(true));
    assertThat(c.get(5)).isNull();
    assertThat(c.containsKey(5)).isTrue();
  }

  static class MarkerException extends RuntimeException { }
  static class AlwaysFailException extends MarkerException { }

  /**
   * Test all aspects of when a loader throws an exception permanently.
   */
  @Test
  public void loadExceptionSyncLoader() {
    Cache<Integer, Integer> c = target.cache(b -> b
      .loader(k -> { throw new AlwaysFailException(); }));
    loadExceptionChecks(c);
  }

  @Test
  public void loadExceptionAsyncSyncLoaderImmediateFail() {
    Cache<Integer, Integer> c = target.cache(b -> b
      .loader((AsyncCacheLoader<Integer, Integer>) (key, context, callback) -> {
        throw new AlwaysFailException();
      }));
    loadExceptionChecks(c);
  }

  @Test
  public void loadExceptionAsyncSyncLoaderDelayedFail() {
    Cache<Integer, Integer> c = target.cache(b -> b
      .loader((AsyncCacheLoader<Integer, Integer>) (key, context, callback) -> context.getExecutor().execute(() -> callback.onLoadFailure(new AlwaysFailException()))));
    loadExceptionChecks(c);
  }

  @Test
  public void loadExceptionBulkSyncLoaderFail() {
    Cache<Integer, Integer> c = target.cache(b -> b
      .bulkLoader(keys -> { throw new AlwaysFailException(); })
    );
    loadExceptionChecks(c);
  }

  @Test
  public void loadExceptionBulkAsyncSyncLoaderImmediateFail() {
    Cache<Integer, Integer> c = target.cache(b -> b
      .bulkLoader((keys, contexts, callback) -> { throw new AlwaysFailException(); })
    );
    loadExceptionChecks(c);
  }

  @Test
  public void loadExceptionBulkAsyncSyncLoaderDelayedFail() {
    Cache<Integer, Integer> c = target.cache(b -> b
      .bulkLoader((keys, contexts, callback) -> contexts.getExecutor().execute(() -> callback.onLoadFailure(new AlwaysFailException())))
    );
    loadExceptionChecks(c);
  }

  private void loadExceptionChecks(Cache<Integer, Integer> c) {
    final Integer key = 6;
    assertThatCode(() -> c.get(5))
      .as("get() propagates loader exception")
      .isInstanceOf(CacheLoaderException.class)
      .getCause().isInstanceOf(AlwaysFailException.class);
    assertThatCode(() -> c.loadAll(asList(key)).get())
      .as("loadAll().get() single value propagates loader exception")
      .isInstanceOf(ExecutionException.class)
      .getCause()
      .isInstanceOf(CacheLoaderException.class)
      .getCause().isInstanceOf(AlwaysFailException.class);
    assertThatCode(() -> c.loadAll(asList(key, 7, 8)).get())
      .as("loadAll().get() propagates loader exception")
      .isInstanceOf(ExecutionException.class)
      .getCause()
      .isInstanceOf(CacheLoaderException.class)
      .as("contains number of exceptions")
      .hasMessageContaining("3")
      .getCause().isInstanceOf(AlwaysFailException.class);
    assertThatCode(() -> c.loadAll(asList(key)).get())
      .as("loadAll().get() single value propagates loader exception, if loaded before")
      .isInstanceOf(ExecutionException.class)
      .getCause()
      .isInstanceOf(CacheLoaderException.class)
      .getCause().isInstanceOf(AlwaysFailException.class);
    assertThatCode(() -> c.reloadAll(asList(key, 7, 8)).get())
      .as("reloadAll().get() propagates loader exception")
      .isInstanceOf(ExecutionException.class)
      .getCause()
      .isInstanceOf(CacheLoaderException.class)
      .as("contains number of exceptions and operations")
      .hasMessageContaining("3 out of 3")
      .getCause().isInstanceOf(AlwaysFailException.class);
    assertThat(c.peek(key))
      .as("expect nothing loaded and no exception present on entry")
      .isNull();
    c.put(key, 123);
    assertThat(c.peek(key))
      .as("entry value can be set")
      .isEqualTo(123);
  }

  @Test
  public void testLoadNull_Reject() {
    Cache<Integer, Integer> c = target.cache(b -> b.loader(key -> null));
    try {
      c.get(5);
      fail("exception expected");
    } catch (CacheLoaderException expected) { }
  }

  @Test
  public void testLoadNull_NoCache() {
    Cache<Integer, Integer> c = target.cache(b -> b.loader(key -> null)
      .expiryPolicy((key, value, startTime, currentEntry) -> NOW));
    assertThat(c.get(5)).isNull();
    assertThat(c.containsKey(5)).isFalse();
  }

  @Test
  public void testAdvancedLoader() {
    Cache<Integer, Integer> c = target.cache(b -> b.loader((key, startTime, e) -> key * 2));
    assertThat(c.get(5)).isEqualTo((Integer) 10);
    assertThat(c.get(10)).isEqualTo((Integer) 20);
    assertThat(c.containsKey(2)).isFalse();
    assertThat(c.containsKey(5)).isTrue();
  }

  @Test
  public void testLoadAll() throws ExecutionException, InterruptedException {
    AtomicInteger countLoad = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(b -> b.loader(key -> countLoad.incrementAndGet()));
    c.get(5);
    c.loadAll(asList(5, 6)).get();
    assertThat(countLoad.get()).isEqualTo(2);
    assertThat(c.get(6)).isEqualTo((Integer) 2);
    c.loadAll(asList(5, 6)).get();
    c.loadAll(EMPTY_SET);
  }

  @Test
  public void testReloadAll() throws ExecutionException, InterruptedException {
    AtomicInteger countLoad = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(b -> b.loader(key -> countLoad.incrementAndGet()));
    c.get(5);
    assertThat(countLoad.get()).isEqualTo(1);
    c.reloadAll(asList(5, 6)).get();
    assertThat(countLoad.get()).isEqualTo(3);
    c.reloadAll(asList(5, 6));
    c.reloadAll(EMPTY_SET);
  }

  /**
   * We should always have two loader threads.
   */
  @Test
  public void testTwoLoaderThreadsAndPoolInfo() throws Exception {
    CountDownLatch inLoader = new CountDownLatch(2);
    CountDownLatch releaseLoader = new CountDownLatch(1);
    String namePrefix = CacheLoader.class.getName() + ".testTwoLoaderThreadsAndPoolInfo";
    ThreadPoolExecutor pool = new ThreadPoolExecutor(0, 10,
      21, SECONDS,
      new SynchronousQueue<>(),
      DEFAULT.newThreadFactory(namePrefix),
      new AbortPolicy());
    Cache<Integer, Integer> c = target.cache(b -> {
      b.loaderExecutor(pool);
      b.loader(key -> {
        inLoader.countDown();
        releaseLoader.await();
        return key * 2;
      });
    });
    c.loadAll(asList(1));
    c.loadAll(asList(2));
    inLoader.await();
    assertThat(pool.getTaskCount()).isEqualTo(2);
    assertThat(pool.getActiveCount()).isEqualTo(2);
    assertThat(pool.getLargestPoolSize()).isEqualTo(2);
    /* old version
    assertEquals(2, latestInfo(c).getAsyncLoadsStarted());
    assertEquals(2, latestInfo(c).getAsyncLoadsInFlight());
    assertEquals(2, latestInfo(c).getLoaderThreadsMaxActive());
    */
    releaseLoader.countDown();
    pool.shutdown();
  }

  /**
   * Start two overlapping loads, expect that one is done in the caller thread,
   * since only one thread is available.
   */
  @Test
  public void testOneLoaderThreadsAndPoolInfo() throws Exception {
    Thread callingThread = currentThread();
    CountDownLatch inLoader = new CountDownLatch(1);
    CountDownLatch releaseLoader = new CountDownLatch(1);
    AtomicInteger asyncCount = new AtomicInteger();
    String namePrefix = CacheLoader.class.getName() + ".testOneLoaderThreadsAndPoolInfo";
    ThreadPoolExecutor pool = new ThreadPoolExecutor(0, 1,
      21, SECONDS,
      new SynchronousQueue<>(),
      DEFAULT.newThreadFactory(namePrefix),
      new AbortPolicy());
    Cache<Integer, Integer> c = target.cache(b -> b.loaderExecutor(pool)
      .loader(key -> {
        if (callingThread != currentThread()) {
          asyncCount.incrementAndGet();
          inLoader.countDown();
          releaseLoader.await();
        }
        return key * 2;
      }));
    c.loadAll(asList(1));
    c.loadAll(asList(2));
    inLoader.await();
    assertThat(pool.getTaskCount())
      .as("only one load is separate thread")
      .isEqualTo(1);
    assertThat(asyncCount.get())
      .as("only one load is separate thread")
      .isEqualTo(1);
    assertThat(pool.getActiveCount()).isEqualTo(1);
    assertThat(pool.getLargestPoolSize()).isEqualTo(1);
    /* old version
    assertEquals("only one load is separate thread", 1, latestInfo(c).getAsyncLoadsStarted());
    assertEquals("only one load is separate thread", 1, asyncCount.get());
    assertEquals(1, latestInfo(c).getAsyncLoadsInFlight());
    assertEquals(1, latestInfo(c).getLoaderThreadsMaxActive());
    */
    releaseLoader.countDown();
  }

  @Test
  public void multipleWaitersCompleteAfterLoad_noThreads_sync() {
    multipleWaitersCompleteAfterLoad(false, false, false);
  }

  @Test
  public void multipleWaitersCompleteAfterLoad_threads_sync() {
    multipleWaitersCompleteAfterLoad(true, false, false);
  }

  @Test
  public void multipleWaitersCompleteAfterLoad_noThreads_async() {
    multipleWaitersCompleteAfterLoad(false, true, false);
  }

  @Test
  public void multipleWaitersCompleteAfterLoad_threads_async() {
    multipleWaitersCompleteAfterLoad(true, true, false);
  }

  @Test
  public void multipleWaitersCompleteAfterLoad_threads_async_reload() {
    multipleWaitersCompleteAfterLoad(true, true, true);
  }

  /**
   * Test multiple threads waiting for a single load to complete. Calls to
   * {@link Cache#loadAll(Iterable)} are not allowed to
   * block. Multiple load requests only lead to one load. All requests are completed when the
   * load is completed.
   */
  private void multipleWaitersCompleteAfterLoad(boolean useThreads, boolean async, boolean reload) {
    final int anyKey = 1;
    final int waiterCount = MINIMAL_LOADER_THREADS;
    CountDownLatch complete = new CountDownLatch(waiterCount);
    CountDownLatch releaseLoader = new CountDownLatch(1);
    CountDownLatch threadsStarted = new CountDownLatch(waiterCount);
    CountDownLatch threadsCompleted = new CountDownLatch(waiterCount);
    AtomicInteger loaderCallCount = new AtomicInteger();
    Cache2kBuilder<Integer, Integer> b = builder(Integer.class, Integer.class);
    if (async) {
      b.loader((key, context, callback) -> {
        loaderCallCount.incrementAndGet();
        context.getLoaderExecutor().execute(() -> {
          try {
            releaseLoader.await();
          } catch (InterruptedException ex) {
            ex.printStackTrace();
          }
          callback.onLoadSuccess(123);
        });
      });
    } else {
      b.loader(key -> {
        loaderCallCount.incrementAndGet();
        releaseLoader.await();
        return 123;
      });
    }
    Cache<Integer, Integer> c = b.build();
    Thread[] ta = new Thread[waiterCount];
    ExceptionCollector exceptionCollector = new ExceptionCollector();
    for (int i = 0; i < waiterCount; i++) {
      Runnable action = () -> {
        if (reload) {
          c.reloadAll(Collections.singleton(anyKey))
            .handle((unused, throwable) -> {
              exceptionCollector.exception(throwable);
              complete.countDown(); return null; });
        } else {
          c.loadAll(Collections.singleton(anyKey))
            .handle((unused, throwable) -> {
              exceptionCollector.exception(throwable);
              complete.countDown(); return null; });
        }
      };
      if (useThreads) {
        ta[i] = new Thread(() -> {
          threadsStarted.countDown();
          action.run();
          threadsCompleted.countDown();
        });
        ta[i].start();
      } else {
        action.run();
      }
    }
    if (useThreads) {
      awaitCountdown(threadsStarted);
      awaitCountdown(threadsCompleted);
    }
    releaseLoader.countDown();
    awaitCountdown(complete);
    exceptionCollector.assertNoException();
    if (reload) {
      assertThat(loaderCallCount.get()).isEqualTo(waiterCount);
    } else {
      assertThat(loaderCallCount.get()).isEqualTo(1);
    }
  }

  void awaitCountdown(CountDownLatch latch) {
    try {
      boolean gotTimeout =
        !latch.await(TestingParameters.MAX_FINISH_WAIT_MILLIS / 2, TimeUnit.MILLISECONDS);
      if (gotTimeout) {
        fail("timeout");
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }

  }

  void completes(CompletableFuture<Void> future) {
    CountDownLatch complete = new CountDownLatch(1);
    CompletableFuture<Void> chained =
      future.handle((unused, throwable) -> {
        complete.countDown();
        return null;
      });
    boolean okay = false;
    try {
      okay = complete.await(3, SECONDS);
    } catch (InterruptedException e) {
      currentThread().interrupt();
    }
    if (chained.isCompletedExceptionally()) {
      try {
        chained.get();
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }
    assertThat(okay)
      .as("no timeout")
      .isTrue();
  }

  @Test
  public void loadAll_syncLoader_completes() {
    Cache<Integer, Integer> c = builder()
      .loader(key -> key)
      .build();
    completes(c.loadAll(asList(1)));
    completes(c.loadAll(asList(1, 2, 3)));
  }

  @Test
  public void loadAll_asyncLoader_completes() {
    Cache<Integer, Integer> c = builder()
      .loader((key, context, callback) -> {
        callback.onLoadSuccess(key); })
      .build();
    completes(c.loadAll(asList(1)));
    completes(c.loadAll(asList(1, 2, 3)));
  }

  /**
   * Execute loader in another thread.
   */
  @Test
  public void blockAndComplete() throws Exception {
    final int count = 5;
    AtomicInteger loaderCalled = new AtomicInteger();
    CountDownLatch complete = new CountDownLatch(count);
    AtomicInteger loaderExecuted = new AtomicInteger();
    CountDownLatch releaseLoader = new CountDownLatch(1);
    Cache<Integer, Integer> c = target.cache(b -> {
      b.loaderExecutor(commonPool());
      b.loader((key, ctx, callback) -> {
        loaderCalled.incrementAndGet();
        ctx.getLoaderExecutor().execute(() -> {
          try {
            releaseLoader.await();
          } catch (InterruptedException ex) {
            ex.printStackTrace();
          }
          loaderExecuted.incrementAndGet();
          callback.onLoadSuccess(key);
        });
      });
    });
    ExceptionCollector exceptionCollector = new ExceptionCollector();
    for (int i = 0; i < count; i++) {
      c.loadAll(asList(1 + (i / 2), 2 + (i / 2), 3 + (i / 2))).handle((unused, throwable) -> {
        exceptionCollector.exception(throwable);
        complete.countDown();
        return null;
      });
    }
    releaseLoader.countDown();
    boolean okay = complete.await(3, SECONDS);
    exceptionCollector.assertNoException();
    assertThat(okay)
      .as("no timeout")
      .isTrue();
  }

  /**
   * Execute loader in another thread.
   */
  @Test
  public void asyncLoaderLoadViaExecutor() {
    AtomicInteger loaderCalled = new AtomicInteger();
    AtomicInteger loaderExecuted = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(b -> b.loader((key, ctx, callback) -> {
      loaderCalled.incrementAndGet();
      ctx.getLoaderExecutor().execute(() -> {
        loaderExecuted.incrementAndGet();
        callback.onLoadSuccess(key);
      });
    }));
    Integer v = c.get(1);
    assertThat((int) v).isEqualTo(1);
  }

  /**
   * Call the callback within the loading thread.
   */
  @Test
  public void asyncLoaderLoadDirect() {
    AtomicInteger loaderCalled = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(b -> b.loader((key, ctx, callback) -> {
      loaderCalled.incrementAndGet();
      callback.onLoadSuccess(key);
    }));
    Integer v = c.get(1);
    assertThat((int) v).isEqualTo(1);
  }

  /**
   * Test whether no loader executor is used
   */
  @Test
  public void asyncLoader_noLoaderExecutorUsed() throws ExecutionException, InterruptedException {
    AtomicInteger executorUsed = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(b -> b
      .loader((AsyncCacheLoader<Integer, Integer>) (key, ctx, callback) -> callback.onLoadSuccess(key))
      .loaderExecutor(command -> {
        executorUsed.incrementAndGet();
        fail("loader executor use unexpected");
      })
      .refreshAhead(true)
      .expireAfterWrite(1, MILLISECONDS));
    Integer v = c.get(1);
    c.loadAll(asList(1, 2, 3, 4, 5)).get();
    c.reloadAll(asList(1, 2, 3, 4, 5)).get();
    c.invokeAll(asList(2, 3, 4), e -> e.setExpiryTime(REFRESH));
    assertThat(executorUsed.get() > 0).isFalse();
  }

  @Test
  public void asyncLoader_isRefreshAhead() throws ExecutionException, InterruptedException {
    CountDownLatch waitForRefresh = new CountDownLatch(1);
    Cache<Integer, Integer> c = target.cache(b -> b
      .loader((key, ctx, callback) -> {
        if (ctx.getCurrentEntry() != null) {
          waitForRefresh.countDown();
        }
        callback.onLoadSuccess(key);
      })
      .refreshAhead(true)
      .expireAfterWrite(1, TimeUnit.MILLISECONDS));
    Integer v = c.get(1);
    c.loadAll(asList(1, 2, 3, 4, 5)).get();
    c.invokeAll(asList(2, 3, 4), e -> e.setExpiryTime(ExpiryTimeValues.REFRESH));
    waitForRefresh.await();
  }

  @Test
  public void asyncLoaderContextProperties() throws ExecutionException, InterruptedException {
    AtomicInteger loaderCalled = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(b -> b.loader((key, ctx, callback) -> {
      int cnt = loaderCalled.getAndIncrement();
      if (cnt == 0) {
        assertThat(ctx.getCurrentEntry()).isNull();
      } else {
        assertThat(ctx.getCurrentEntry().getValue()).isEqualTo(key);
        assertThat(ctx.getCurrentEntry().getException()).isNull();
      }
      callback.onLoadSuccess(key);
    }));
    Integer v = c.get(1);
    assertThat((int) v).isEqualTo(1);
    c.reloadAll(asList(1)).get();
  }

  @Test(expected = IllegalStateException.class)
  public void exceptionOnEntryAccessOutSideProcessing() {
    AtomicReference<AsyncCacheLoader.Context<Integer, Integer>> contextRef = new AtomicReference<>();
    Cache<Integer, Integer> c = target.cache(b -> b.loader((key, ctx, callback) -> {
      contextRef.set(ctx);
      callback.onLoadSuccess(key);
    }));
    Integer v = c.get(1);
    contextRef.get().getCurrentEntry();
  }

  @Test
  public void testAsyncLoaderContextProperties_withException() {
    AtomicInteger loaderCalled = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(b -> {
      b.expireAfterWrite(MAX_FINISH_WAIT_MILLIS, MILLISECONDS);
      b.resiliencePolicy(new EnableExceptionCaching());
      b.loader((key, ctx, callback) -> {
        int cnt = loaderCalled.getAndIncrement();
        assertThat(ctx.getCurrentEntry()).isNull();
        callback.onLoadFailure(new ExpectedException());
      });
    });
    assertThatCode(() -> c.get(1))
      .getRootCause().isInstanceOf(ExpectedException.class);
    assertThat(c.peekEntry(1).getException())
      .as("exception cached")
      .isNotNull();
    assertThatCode(() -> c.reloadAll(asList(1)).get())
      .getRootCause().isInstanceOf(ExpectedException.class);
  }

  /**
   * Load exceptions, generally, are propagated when the entry is accessed.
   * When loading via loadAll or reloadAll a load exceptions is propagated
   * as well. Completing successful despite of load exception would be counter
   * intuitive.
   */
  @Test
  public void asyncLoader_loadAll_reloadAll_propagate_exception() {
    Cache<Integer, Integer> c = target.cache(b -> {
      b.expireAfterWrite(MAX_FINISH_WAIT_MILLIS, MILLISECONDS);
      b.resiliencePolicy(new EnableExceptionCaching());
      b.loader((key, ctx, callback) -> {
        callback.onLoadFailure(new ExpectedException());
      });
    });
    assertThatCode(() -> c.loadAll(asList(1)).get())
      .as("load exception propagated")
      .getRootCause().isInstanceOf(ExpectedException.class);
    assertThatCode(() -> c.get(1))
      .as("access yeilds exception")
      .isInstanceOf(CacheLoaderException.class)
      .getRootCause().isInstanceOf(ExpectedException.class);
    assertThatCode(() -> c.loadAll(asList(1)).get())
      .as("Previous load exception propagated")
      .getRootCause().isInstanceOf(ExpectedException.class);
    assertThat(c.peekEntry(1).getException())
      .as("exception cached")
      .isNotNull();
    assertThatCode(() -> c.reloadAll(asList(1)).get())
      .getRootCause().isInstanceOf(ExpectedException.class);
  }

  /**
   * Special case of partial success but presence of a cached exception.
   * In this case we propagate the exception, since all data is not completely
   * loaded.
   */
  @Test
  public void asyncLoader_loadAll_reloadAll_propagate_exception_failAnyWay() {
    Cache<Integer, Integer> c = target.cache(b -> {
      b.expireAfterWrite(TestingParameters.MAX_FINISH_WAIT_MILLIS, TimeUnit.MILLISECONDS);
      b.resiliencePolicy(new ExpiryTest.EnableExceptionCaching());
      b.loader((key, ctx, callback) -> {
        if (key % 2 == 1) {
          callback.onLoadFailure(new ExpectedException());
        } else {
          callback.onLoadSuccess(key);
        }
      });
    });
    assertThatCode(() -> c.loadAll(asList(1)).get())
      .as("load exception propagated")
      .getRootCause().isInstanceOf(ExpectedException.class);
    assertThatCode(() -> c.get(1))
      .as("access yeilds exception")
      .isInstanceOf(CacheLoaderException.class)
      .getRootCause().isInstanceOf(ExpectedException.class);
    assertThatCode(() -> c.loadAll(asList(1, 2)).get())
      .as("Previous load exception propagated")
      .getRootCause().isInstanceOf(ExpectedException.class);
    assertThatCode(() -> c.loadAll(asList(2)).get())
      .as("Successful")
      .doesNotThrowAnyException();
  }

  /**
   * Check that exception isn't blocking anything
   */
  @Test
  public void asyncLoader_ExceptionInCall() {
    AtomicInteger loaderCalled = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(b -> b.loader((AsyncCacheLoader<Integer, Integer>) (key, ctx, callback) -> {
      loaderCalled.incrementAndGet();
      throw new ExpectedException();
    }));
    try {
      c.get(1);
      fail("exception expected");
    } catch (CacheLoaderException expected) {
      assertThat(expected.getCause() instanceof ExpectedException).isTrue();
    }
    c.put(1, 1);
    assertThat(c.get(1)).isNotNull();
  }

  @Test
  public void asyncLoader_concurrentCacheClose() {
    TaskSuccessGuardian guardianOnSuccess = new TaskSuccessGuardian();
    TaskSuccessGuardian guardianOnFailure = new TaskSuccessGuardian();
    CountDownLatch waitForCloseLatch = new CountDownLatch(1);
    Cache<Integer, Integer> c = target.cache(b -> b
      .expireAfterWrite(PinpointParameters.TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .loader((key, ctx, callback) -> {
      ctx.getExecutor().execute(() -> {
        if (key == 1) {
          try {
            waitForCloseLatch.await();
            assertThatCode(() -> callback.onLoadSuccess(123)).as("onSuccess()").doesNotThrowAnyException();
            guardianOnSuccess.success();
          } catch (Throwable t) {
            guardianOnSuccess.exception(t);
          }
        } else {
          try {
            waitForCloseLatch.await();
            assertThatCode(() -> callback.onLoadFailure(new RuntimeException())).as("onLoadFailure()").doesNotThrowAnyException();
            guardianOnFailure.success();
          } catch (Throwable t) {
            guardianOnFailure.exception(t);
          }
        }
      });
    }));
    c.loadAll(asList(1));
    c.loadAll(asList(2));
    c.close();
    waitForCloseLatch.countDown();
    guardianOnSuccess.assertSuccess();
    guardianOnFailure.assertSuccess();
  }

  @Test
  public void asyncLoader_viaExecutor() throws ExecutionException, InterruptedException {
    AtomicInteger loaderCalled = new AtomicInteger();
    AtomicInteger loaderExecuted = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(b -> b.loader((key, ctx, callback) -> {
      loaderCalled.incrementAndGet();
      ctx.getExecutor().execute(() -> {
        loaderExecuted.incrementAndGet();
        callback.onLoadSuccess(key);
      });
    }));
    c.loadAll(asList(1, 2, 1802)).get();
    assertThat((int) c.peek(1)).isEqualTo(1);
    Object o1 = c.peek(1802);
    assertThat(c.peek(1802) == o1).isTrue();
    c.reloadAll(asList(1802, 4, 5)).get();
    assertThat(c.peek(1802)).isNotNull();
    assertThat(c.peek(1802) != o1).isTrue();
  }

  @Test
  public void testAsyncLoaderWithExecutorWithAsyncCopy() throws Exception {
    Cache<Integer, Integer> c = target.cache(b -> b.loader((AsyncCacheLoader<Integer, Integer>) (key, ctx, callback) ->
      ctx.getLoaderExecutor().execute(() -> callback.onLoadSuccess(key))));
    c.loadAll(asList(1, 2, 1802)).get();
    assertThat(c.peek(1802)).isNotNull();
    assertThat((int) c.peek(1)).isEqualTo(1);
    Object o1 = c.peek(1802);
    assertThat(c.peek(1802) == o1).isTrue();
    c.reloadAll(asList(1802, 4, 5)).get();
    assertThat(c.peek(1802)).isNotNull();
    assertThat(c.peek(1802) != o1).isTrue();
  }

  @Test
  public void asyncLoader_doubleCallback_yields_exception() throws ExecutionException, InterruptedException {
    AtomicInteger loaderCalled = new AtomicInteger();
    AtomicInteger loaderExecuted = new AtomicInteger();
    AtomicInteger gotException = new AtomicInteger();
    AtomicInteger gotNoException = new AtomicInteger();
    AtomicReference<Throwable> otherException = new AtomicReference<>();
    Cache<Integer, Integer> c = target.cache(b -> b.loader((key, ctx, callback) -> {
      ctx.getLoaderExecutor().execute(() -> {
        loaderExecuted.incrementAndGet();
        callback.onLoadSuccess(key);
        try {
          callback.onLoadSuccess(key);
          gotNoException.incrementAndGet();
        } catch (IllegalStateException ex) {
          gotException.incrementAndGet();
        } catch (Throwable ex) {
          ex.printStackTrace();
          otherException.set(ex);
        }
      });
      loaderCalled.incrementAndGet();
    }));
    c.loadAll(EMPTY_LIST).get();
    c.loadAll(asList(1, 2, 1802)).get();
    assertThat(otherException.get()).isNull();
    assertThat(loaderCalled.get())
      .as("loader called")
      .isEqualTo(3);
    assertThat(loaderExecuted.get())
      .as("loader Executed")
      .isEqualTo(3);
    await("wait for 3 exceptions", () -> gotException.get() == 3);
    assertThat(gotNoException.get())
      .as("always throws exception")
      .isEqualTo(0);
    c.loadAll(asList(1, 2, 1802)).get();
    assertThat((int) c.peek(1)).isEqualTo(1);
    Object o1 = c.peek(1802);
    assertThat(c.peek(1802) == o1).isTrue();
    c.reloadAll(asList(1802, 4, 5)).get();
    assertThat(c.peek(1802)).isNotNull();
    assertThat(c.peek(1802) != o1).isTrue();
  }

  @Test
  public void testAsyncLoaderDoubleCallbackDifferentThreads() throws ExecutionException, InterruptedException {
    AtomicInteger loaderCalled = new AtomicInteger();
    AtomicInteger loaderExecuted = new AtomicInteger();
    AtomicInteger gotException = new AtomicInteger();
    AtomicInteger gotNoException = new AtomicInteger();
    AtomicReference<Throwable> otherException = new AtomicReference<>();
    Cache<Integer, Integer> c = target.cache(b -> {
      b.loaderExecutor(newCachedThreadPool());
      b.loader((key, ctx, callback) -> {
        Runnable command = () -> {
          loaderExecuted.incrementAndGet();
          try {
            callback.onLoadSuccess(key);
            gotNoException.incrementAndGet();
          } catch (IllegalStateException ex) {
            gotException.incrementAndGet();
          } catch (Throwable ex) {
            ex.printStackTrace();
            otherException.set(ex);
          }
        };
        ctx.getLoaderExecutor().execute(command);
        ctx.getLoaderExecutor().execute(command);
        loaderCalled.incrementAndGet();
      });
    });
    c.loadAll(EMPTY_LIST).get();
    c.loadAll(asList(1, 2, 1802)).get();
    assertThat(otherException.get()).isNull();
    assertThat(loaderCalled.get())
      .as("loader called")
      .isEqualTo(3);
    await("wait for 6 executions", () -> loaderExecuted.get() == 6);
    await("wait for 3 exceptions", () -> gotException.get() == 3);
    await("wait for 3 successful executions", () -> gotNoException.get() == 3);
    c.loadAll(asList(1, 2, 1802)).get();
    assertThat((int) c.peek(1)).isEqualTo(1);
    Object o1 = c.peek(1802);
    assertThat(c.peek(1802) == o1).isTrue();
    c.reloadAll(asList(1802, 4, 5)).get();
    assertThat(c.peek(1802)).isNotNull();
    assertThat(c.peek(1802) != o1).isTrue();
  }

  protected Cache<Integer, Integer> cacheWithLoader() {
    return target.cache(b -> b.loader(key -> {
      loaderExecutionCount++;
      return key * 2;
    }));
  }

  @Test
  public void asyncBulkLoader_direct() throws Exception {
    final int assertKey = 987;
    final int exceptionKey = 789;
    Cache<Integer, Integer> c = target.cache(b ->
      b.loader((AsyncBulkCacheLoader<Integer, Integer>) (keys, context, callback) -> {
        int firstKey = keys.iterator().next();
        assertThat(firstKey).as("No assertion provoked").isNotEqualTo(assertKey);
        if (exceptionKey == firstKey) {
          throw new ExpectedException();
        }
        keys.forEach(key -> callback.onLoadSuccess(key, key));
      }
    ));
    
    c.loadAll(asList(1, 2, 1802)).get();
    assertThat(c.peek(1802)).isNotNull();
    assertThat((int) c.peek(1)).isEqualTo(1);
    Object o1 = c.peek(1802);
    assertThat(c.peek(1802) == o1).isTrue();
    c.reloadAll(asList(1802, 4, 5)).get();
    assertThat(c.peek(1802)).isNotNull();
    assertThat(c.peek(1802) != o1).isTrue();
  }

  public static class AsyncLoadBuffer<K, V> {
    private int startedLoadRequests = 0;
    private final Map<K, AsyncCacheLoader.Callback<V>> pending = new HashMap<>();
    private final Function<K, V> loader;
    public AsyncLoadBuffer(Function<K, V> loader) { this.loader = loader; }
    public synchronized void put(K key, AsyncCacheLoader.Callback<V> cb) {
      startedLoadRequests++;
      cb = pending.putIfAbsent(key, cb);
      assertThat(cb).as("no request pending for %s", key).isNull();
      notifyAll();
    }
    public void put(K key, AsyncBulkCacheLoader.BulkCallback<K, V> cb) {
      put(key, new BulkCallbackWrapper<>(key, cb));
    }
    public synchronized void complete(K key) {
      notifyAll();
      pending.compute(key, (k, vCallback) -> {
        assertThat(vCallback)
          .as("Expected pending. Exception?! Key: " + key)
          .isNotNull();
        vCallback.onLoadSuccess(loader.apply(k));
        return null;
      });
    }
    public void complete(K... keys) {
      for (K key : keys) {
        complete(key);
      }
    }
    public synchronized AsyncBulkCacheLoader.BulkCallback<K, V> getBulkCallback(K key) {
      return ((BulkCallbackWrapper) pending.get(key)).getOriginalCallback();
    }

    /**
     * Complete with bulk callback, expecting all keys are within a single bulk request.
     */
    public void bulkComplete(K... keys) {
      AsyncBulkCacheLoader.BulkCallback<K, V> cb;
      Map<K, V> map = new HashMap<>();
      synchronized (this) {
        K key = keys[0];
        cb = getBulkCallback(key);
        map.put(key, loader.apply(key));
        for (int i = 1; i < keys.length; i++) {
          key = keys[i];
          AsyncBulkCacheLoader.BulkCallback<K, V> cb2 = getBulkCallback(key);
          assertThat(cb2).as("belongs to same bulk request").isSameAs(cb2);
          map.put(key, loader.apply(key));
        }
        for (K k : keys) { pending.remove(k); }
      }
      cb.onLoadSuccess(map);
    }
    /** Expect that async load is started for the set of keys */
    public synchronized void assertStarted(K... keys) {
      for (K k : keys) {
        assertThat(pending.get(k))
          .as("load request pending for " + k)
          .isNotNull();
      }
    }
    /** Wait until async load is started for the set of keys */
    public synchronized void awaitStarted(K... keys) {
      long t0 = System.currentTimeMillis();
      long timeout = TestingParameters.MAX_FINISH_WAIT_MILLIS;
      for (K k : keys) {
        while (!pending.containsKey(k)) {
          try {
            long remainingTimeout = timeout - (System.currentTimeMillis() - t0);
            if (remainingTimeout <= 0) {
              throw new AssertionError(
                "Load for " + k + " not started within " + timeout + " millis");
            }
            wait(remainingTimeout);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CaughtInterruptedException(e);
          }
        }
      }
    }
    public synchronized int getStartedLoadRequests() { return startedLoadRequests; }

  }
  public static class BulkCallbackWrapper<K, V> implements AsyncCacheLoader.Callback<V> {
    private final K key;
    private final AsyncBulkCacheLoader.BulkCallback<K, V> cb;
    public BulkCallbackWrapper(K key, AsyncBulkCacheLoader.BulkCallback<K, V> cb) {
      this.key = key; this.cb = cb;
    }
    @Override
    public void onLoadSuccess(V value) {
      cb.onLoadSuccess(key, value);
    }
    /** This completes the whole bulk request. */
    @Override
    public void onLoadFailure(Throwable t) {
      cb.onLoadFailure(t);
    }
    public AsyncBulkCacheLoader.BulkCallback<K, V> getOriginalCallback() { return cb; }
  }

  @Test
  public void asyncBulkLoaderComplex() {
    AtomicInteger bulkRequests = new AtomicInteger();
    AsyncLoadBuffer<Integer, Integer> buffer = new AsyncLoadBuffer<>(k -> k);
    Cache<Integer, Integer> cache = target.cache(b -> b.bulkLoader((keys, contextSet, callback) -> {
      bulkRequests.incrementAndGet();
      keys.forEach(key -> buffer.put(key, callback));
    }));
    CompletableFuture<Void> reqA = cache.loadAll(asList(9));
    buffer.assertStarted(9);
    CompletableFuture<Void> reqB = cache.loadAll(asList(8, 9));
    buffer.bulkComplete(9);
    buffer.bulkComplete(8);
    assertThat(reqA.isDone())
      .as("completed in our thread")
      .isTrue();
    await("completed via executor", reqB::isDone);
    CompletableFuture<Void> req1 = cache.loadAll(asList(1, 2, 3));
    buffer.assertStarted(1, 2, 3);
    CompletableFuture<Void> req2 = cache.loadAll(asList(1, 2, 3));
    CompletableFuture<Void> req3 = cache.loadAll(asList(1, 2, 3, 4, 5));
    buffer.assertStarted(4, 5);
    buffer.bulkComplete(1, 2, 3);
    CompletableFuture<Void> req4 = cache.loadAll(asList(1, 2, 3, 4, 5, 6, 7));
    assertThat(req1.isDone())
      .as("completed in our thread")
      .isTrue();
    await("completed via executor", req2::isDone);
    assertThat(req3.isDone()).isFalse();
    buffer.complete(4);
    buffer.bulkComplete(5);
    assertThat(req3.isDone())
      .as("completed in our thread")
      .isTrue();
    Map<Integer, Integer> res = cache.getAll(asList(1, 2, 3, 4, 5));
    buffer.bulkComplete(6, 7);
    assertThat(buffer.getStartedLoadRequests()).isEqualTo(9);
    assertThat(bulkRequests.get()).isEqualTo(5);
  }

  @Test
  public void asyncBulkLoader_singleLoad() throws Exception {
    AsyncLoadBuffer<Integer, Integer> buffer = new AsyncLoadBuffer<>(k -> k);
    Cache<Integer, Integer> cache = target.cache(b -> b.bulkLoader((keys, context, callback) -> {
      assertThat(context.getContextMap().size()).isEqualTo(keys.size());
      keys.forEach(key -> {
        assertThat(keys.contains(key)).isTrue();
        buffer.put(key, callback);
      });
    }));
    CompletableFuture<Void> req1 = cache.loadAll(asList(1, 2, 3));
    assertThat(req1).hasNotFailed();
    CompletableFuture<Void> req2 = cache.loadAll(asList(1, 2, 3, 4));
    assertThat(req2).hasNotFailed();
    buffer.complete(1, 2, 3, 4);
    req1.get();
    req2.get();
  }

  @Test
  public void asyncBulkLoader_immediateException_loadAll_get_getAll() {
    Cache<Integer, Integer> cache = target.cache(b -> b.bulkLoader((keys, contextSet, callback) -> {
      throw new ExpectedException();
    }));
    asayncBulkLoader_loadAll_get_getAll_epxectException(cache);
  }

  @Test
  public void asyncBulkLoader_failAll1_loadAll_get_getAll() {
    Cache<Integer, Integer> cache = target.cache(b -> b.bulkLoader((keys, contextSet, callback) -> {
      callback.onLoadFailure(new ExpectedException());
    }));
    asayncBulkLoader_loadAll_get_getAll_epxectException(cache);
  }

  @Test
  public void asyncBulkLoader_failAll2_loadAll_get_getAll() {
    Cache<Integer, Integer> cache = target.cache(b -> b.bulkLoader((keys, contextSet, callback) -> {
      callback.onLoadFailure(keys, new ExpectedException());
    }));
    asayncBulkLoader_loadAll_get_getAll_epxectException(cache);
  }

  @Test
  public void asyncBulkLoader_failAll3_loadAll_get_getAll() {
    Cache<Integer, Integer> cache = target.cache(b -> b.bulkLoader((keys, contextSet, callback) -> {
      for (Integer key : keys) {
        callback.onLoadFailure(key, new ExpectedException());
      }
    }));
    asayncBulkLoader_loadAll_get_getAll_epxectException(cache);
  }

  private void asayncBulkLoader_loadAll_get_getAll_epxectException(Cache<Integer, Integer> cache) {
    CompletableFuture<Void> req1 = cache.loadAll(asList(1, 2, 3));
    assertThat(req1.isCompletedExceptionally())
          .as("exception expected")
          .isTrue();
    CompletableFuture<Void> req2 = cache.loadAll(asList(4));
    assertThat(req2.isCompletedExceptionally())
          .as("exception expected")
          .isTrue();
    for (int i = 1; i < 6; i++) {
      CacheEntry<Integer, Integer> entry = cache.getEntry(i);
      assertThat(entry.getException())
        .as("expect exception")
        .isNotNull();
      assertThat(entry.getException()).isInstanceOf(ExpectedException.class);
    }
    assertThatCode(() -> cache.getAll(asList(1, 2, 3))).describedAs("Expect exception if all requested keys yield and exception")
      .isInstanceOf(CacheLoaderException.class);
  }

  @Test
  public void asyncBulkLoader_enforceSingleLoad() {
    AtomicInteger bulkRequests = new AtomicInteger();
    AsyncLoadBuffer<Integer, Integer> buffer = new AsyncLoadBuffer<>(k -> k);
    Cache<Integer, Integer> cache = target.cache(b -> b.bulkLoader((keys, context, callback) -> {
      bulkRequests.incrementAndGet();
      keys.forEach(key -> buffer.put(key, callback));
    }));
    CompletableFuture<Void> req1 = cache.loadAll(asList(1, 2, 3));
    buffer.assertStarted(1, 2, 3);
    SupervisedExecutor exe = executor();
    exe.execute(() -> cache.getAll(asList(1, 2, 3, 4)));
    buffer.awaitStarted(1, 2, 3, 4);
    buffer.complete(2);
    buffer.bulkComplete(1, 3);
    buffer.complete(4);
    exe.join();
    assertThat(bulkRequests.get()).isEqualTo(2);
  }

  @Test
  public void bulkLoader_loadAll() throws Exception {
    AtomicInteger bulkRequests = new AtomicInteger();
    Cache<Integer, Integer> cache = target.cache(b -> b.bulkLoader(keys -> {
      bulkRequests.incrementAndGet();
      Map<Integer, Integer> result = buildIdentMap(keys);
      return result;
    }));
    CompletableFuture<Void> req1 = cache.loadAll(asList(1, 2, 3));
    CompletableFuture<Void> req2 = cache.loadAll(asList(1, 2, 3));
    CompletableFuture<Void> req3 = cache.loadAll(asList(1, 2, 3, 4, 5));
    CompletableFuture<Void> req4 = cache.loadAll(asList(1, 2, 3, 4, 5, 6, 7));
    req4.get();
    req1.get(); req2.get(); req3.get();
    assertThat(target.info().getLoadCount()).isEqualTo(7);
    int bulkRequests0 = bulkRequests.get();
    assertThat(bulkRequests.get())
      .describedAs("May as well be just one bulk request, if execution gets delayed")
      .isIn(1, 2, 3, 4);
    CompletableFuture<Void> req5 = cache.reloadAll(asList(2, 3, 9));
    req5.get();
    assertThat(target.info().getLoadCount()).isEqualTo(10);
    assertThat(bulkRequests.get()).isEqualTo(bulkRequests0 + 1);
  }

  @Test
  public void bulkLoader_getAll() throws Exception {
    AtomicInteger bulkRequests = new AtomicInteger();
    Cache<Integer, Integer> cache = target.cache(b -> b.bulkLoader(keys -> {
      bulkRequests.incrementAndGet();
      Map<Integer, Integer> result = buildIdentMap(keys);
      return result;
    }));
    CompletableFuture<Void> req1 = cache.loadAll(asList(1, 2, 3));
    Map<Integer, Integer> result = cache.getAll(asList(3, 4, 5));
    req1.get();
    Map<Integer, Integer> result2 = cache.getAll(asList(1, 2, 3, 4, 5));
    result2.forEach((k, v) -> assertThat(v).isEqualTo(k));
    assertThat(target.info().getLoadCount()).isEqualTo(5);
    assertThat(bulkRequests.get()).isEqualTo(2);
    Map<Integer, Integer> result3 = cache.getAll(asList(4, 5, 6, 7));
    result3.forEach((k, v) -> assertThat(v).isEqualTo(k));
  }

  @Test
  public void bulkLoader_getAllOnly() throws Exception {
    AtomicInteger bulkRequests = new AtomicInteger();
    Cache<Integer, Integer> cache = target.cache(b -> b.bulkLoader(keys -> {
      bulkRequests.incrementAndGet();
      Map<Integer, Integer> result = buildIdentMap(keys);
      return result;
    }));
    Map<Integer, Integer> result = cache.getAll(asList(3, 4, 5));
  }

  @Test
  public void bulkLoader_getAll_twoThreads() throws Exception {
    AtomicInteger bulkRequests = new AtomicInteger();
    Cache<Integer, Integer> cache = target.cache(b -> b.bulkLoader(keys -> {
      bulkRequests.incrementAndGet();
      Map<Integer, Integer> result = buildIdentMap(keys);
      return result;
    }));
    execute(() -> cache.getAll(asList(1, 2, 3)));
    Map<Integer, Integer> result2 = cache.getAll(asList(1, 2, 3));
    result2.forEach((k, v) -> assertThat(v).isEqualTo(k));
    join();
  }

  @Test
  public void bulkLoader_invokeAll() throws Exception {
    AtomicInteger bulkRequests = new AtomicInteger();
    Cache<Integer, Integer> cache = target.cache(b -> b.bulkLoader(keys -> {
      bulkRequests.incrementAndGet();
      Map<Integer, Integer> result = buildIdentMap(keys);
      return result;
    }));
    CompletableFuture<Void> req1 = cache.loadAll(asList(1, 2, 3));
    Map<Integer, EntryProcessingResult<Integer>> result =
      cache.invokeAll(asList(3, 4, 5), MutableCacheEntry::getValue);
    req1.get();
    Map<Integer, EntryProcessingResult<Integer>> result2 =
      cache.invokeAll(asList(1, 2, 3, 4, 5), MutableCacheEntry::getValue);
    assertThat(target.info().getLoadCount()).isEqualTo(5);
    assertThat(bulkRequests.get()).isEqualTo(2);
    assertThat(result2.size()).isEqualTo(5);
    assertThat(result2.get(2).getResult()).isEqualTo((Integer) 2);
    assertThat(result2.get(2).getException()).isNull();
    Map<Integer, EntryProcessingResult<Integer>> result3 =
      cache.invokeAll(asList(1, 2, 3), entry -> { throw new ExpectedException(); });
    assertThat(result3.size()).isEqualTo(3);
    assertThat(result3.get(2).getException())
      .as("Propagates exception")
      .isInstanceOf(ExpectedException.class);
    assertThatCode(() -> result3.get(2).getResult())
      .isInstanceOf(EntryProcessingException.class)
      .getCause()
      .isInstanceOf(ExpectedException.class);
  }

  public Map<Integer, Integer> buildIdentMap(Set<? extends Integer> keys) {
    Map<Integer, Integer> result = new HashMap<>();
    for (Integer key : keys) {
      result.put(key, key);
    }
    return result;
  }

  @Test
  public void asyncBulkLoader_invokeAll() throws Exception {
    AtomicInteger bulkRequests = new AtomicInteger();
    Cache<Integer, Integer> cache = target.cache(b -> b.bulkLoader((keys, context, callback) -> {
      bulkRequests.incrementAndGet();
      context.getExecutor().execute(() -> completeWithIdentMapping(keys, callback));
    }));
    checksWithInvokeAll(bulkRequests, cache);
  }

  private void checksWithInvokeAll(AtomicInteger bulkRequests, Cache<Integer, Integer> cache) throws InterruptedException, ExecutionException {
    CompletableFuture<Void> req1 = cache.loadAll(asList(1, 2, 3));
    Map<Integer, EntryProcessingResult<Integer>> result =
      cache.invokeAll(asList(3, 4, 5), MutableCacheEntry::getValue);
    req1.get();
    Map<Integer, EntryProcessingResult<Integer>> result2 =
      cache.invokeAll(asList(1, 2, 3, 4, 5), MutableCacheEntry::getValue);
    assertThat(target.info().getLoadCount()).isEqualTo(5);
    assertThat(bulkRequests.get())
      .as("number of bulk requests")
      .isEqualTo(2);
    assertThat(result2.size()).isEqualTo(5);
    assertThat(result2.get(2).getResult()).isEqualTo((Integer) 2);
    assertThat(result2.get(2).getException()).isNull();
    Map<Integer, EntryProcessingResult<Integer>> result3 =
      cache.invokeAll(asList(1, 2, 3), entry -> {
        throw new ExpectedException();
      });
    assertThat(result3.size()).isEqualTo(3);
    assertThat(result3.get(2).getException())
      .as("Propagates exception")
      .isInstanceOf(ExpectedException.class);
    assertThatCode(() -> result3.get(2).getResult())
      .isInstanceOf(EntryProcessingException.class)
      .getCause()
      .isInstanceOf(ExpectedException.class);
  }

  private void completeWithIdentMapping(java.util.Set<Integer> keys, AsyncBulkCacheLoader.BulkCallback<Integer, Integer> callback) {
    Map<Integer, Integer> result = buildIdentMap(keys);
    callback.onLoadSuccess(result);
  }

  @Test
  public void asyncBulkLoaderContext() throws ExecutionException, InterruptedException {
    AtomicInteger checkCount = new AtomicInteger();
    AtomicReference<Cache> cacheRef = new AtomicReference<>();
    long t = ticks();
    Cache<Integer, Integer> c = target.cache(new CacheRule.Context<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.bulkLoader((keys, context, callback) -> {
          assertThat(t <= context.getStartTime()).isTrue();
          assertThat(keys == context.getKeys()).isTrue();
          assertThat(context.getExecutor()).isNotNull();
          assertThat(context.getLoaderExecutor()).isNotNull();
          assertThat(context.getContextMap()).isNotNull();
          assertThat(context.getCache()).isSameAs(cacheRef.get());
          assertThat(context.getCallback()).isSameAs(callback);
          assertThat(context.isRefreshAhead()).isFalse();
          for (Integer key : keys) {
            callback.onLoadSuccess(key, key);
          }
          checkCount.incrementAndGet();
        });
      }
    });
    cacheRef.set(c);
    c.get(123);
    assertThat(checkCount.get())
      .as("Check context from default single load implementation in API")
      .isEqualTo(1);
    c.loadAll(asList(1, 2, 3)).get();
    assertThat(checkCount.get())
      .as("Check context of bulk request")
      .isEqualTo(2);
  }

  @Test
  public void asyncBulkLoaderDuplicateKeyRequests() {
    Cache<Integer, Integer> c = target.cache(new CacheRule.Context<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.bulkLoader((keys, context, callback) -> {
            throw new ExpectedException();
          })
          .resiliencePolicy(new ExpiryTest.EnableExceptionCaching());
      }
    });
    assertThatCode(() -> c.getAll(asList(1, 1, 1, 1)))
      .isInstanceOf(CacheLoaderException.class);
    assertThatCode(() -> c.getAll(asList(1, 1, 1, 1)))
      .isInstanceOf(CacheLoaderException.class);
  }

  @Test
  public void advancedLoaderEntryNotSetIfExpired() {
    Cache<Integer, Integer> c = target.cache(new CacheRule.Context<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader((key, startTime, currentEntry) -> {
          assertThat(currentEntry).isNull();
          return key;
        });
      }
    });
    c.get(123);
    c.expireAt(123, ExpiryTimeValues.NOW);
    c.get(123);
  }

  @Test
  public void advancedLoaderEntrySetIfExpiredWithKeepData() {
    AtomicBoolean expectEntry = new AtomicBoolean();
    Cache<Integer, Integer> c = target.cache(new CacheRule.Context<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.keepDataAfterExpired(true);
        b.loader((key, startTime, currentEntry) -> {
          if (expectEntry.get()) {
            assertThat(currentEntry).isNotNull();
          } else {
            assertThat(currentEntry).isNull();
          }
          return key;
        });
      }
    });
    c.get(123);
    c.expireAt(123, ExpiryTimeValues.NOW);
    expectEntry.set(true);
    c.get(123);
  }

}
