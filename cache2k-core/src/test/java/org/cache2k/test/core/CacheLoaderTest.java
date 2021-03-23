package org.cache2k.test.core;

/*
 * #%L
 * cache2k core implementation
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

import static org.assertj.core.api.Assertions.assertThat;

import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.integration.FunctionalCacheLoader;
import org.cache2k.io.AsyncBulkCacheLoader;
import org.cache2k.io.AsyncCacheLoader;
import org.cache2k.io.CacheLoaderException;
import org.cache2k.pinpoint.CaughtInterruptedException;
import org.cache2k.pinpoint.ExceptionCollector;
import org.cache2k.pinpoint.SupervisedExecutor;
import org.cache2k.processor.EntryProcessingException;
import org.cache2k.processor.EntryProcessingResult;
import org.cache2k.test.core.expiry.ExpiryTest;
import org.cache2k.test.util.CacheRule;
import org.cache2k.test.util.Condition;
import org.cache2k.io.AdvancedCacheLoader;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.io.CacheLoader;
import org.cache2k.CacheOperationCompletionListener;
import org.cache2k.test.util.ExpectedException;
import org.cache2k.test.util.TestingBase;
import org.cache2k.testing.category.FastTests;
import org.cache2k.test.util.IntCacheRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.Timeout;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.Assert.*;
import static org.cache2k.test.core.StaticUtil.*;

/**
 * Test the cache loader.
 *
 * @author Jens Wilke
 * @see CacheLoader
 * @see AdvancedCacheLoader
 * @see CacheOperationCompletionListener
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
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        assertThat(b.config().getLoaderThreadCount())
          .describedAs("minim thread count")
          .isGreaterThanOrEqualTo(2);
      }
    });
  }

  @Test
  public void testFunctionalLoader() {
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader(new FunctionalCacheLoader<Integer, Integer>() {
          @Override
          public Integer load(Integer key) {
            return key * 7;
          }
        });
      }
    });
    int v = c.get(1);
    assertEquals(7, v);
  }

  @Test
  public void testSeparateLoaderExecutor() throws ExecutionException, InterruptedException {
    AtomicInteger executionCount = new AtomicInteger(0);
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(Integer key) {
            return key * 2;
          }
        });
        b.loaderExecutor(new Executor() {
          @Override
          public void execute(Runnable command) {
            executionCount.incrementAndGet();
            getLoaderExecutor().execute(command);
          }
        });
      }
    });
    assertEquals((Integer) 10, c.get(5));
    assertEquals((Integer) 20, c.get(10));
    assertEquals(0, executionCount.get());
    CompletionWaiter waiter = new CompletionWaiter();
    c.loadAll(asList(1, 2, 3)).get();
    assertEquals("executor is used", 3, executionCount.get());
  }

  @Test
  public void testSeparatePrefetchExecutor() {
    AtomicInteger executionCount = new AtomicInteger(0);
    AtomicInteger prefetchExecutionCount = new AtomicInteger(0);
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(Integer key) {
            return key * 2;
          }
        });
        b.loaderExecutor(new Executor() {
          @Override
          public void execute(Runnable command) {
            executionCount.incrementAndGet();
            getLoaderExecutor().execute(command);
          }
        });
        b.refreshExecutor(new Executor() {
          @Override
          public void execute(Runnable command) {
            prefetchExecutionCount.incrementAndGet();
            getLoaderExecutor().execute(command);
          }
        });
      }
    });
    assertEquals((Integer) 10, c.get(5));
    assertEquals((Integer) 20, c.get(10));
    assertEquals(0, executionCount.get());
    CompletionWaiter waiter = new CompletionWaiter();
    c.loadAll(asList(1, 2, 3), waiter);
    waiter.awaitCompletion();
    assertEquals("executor is used", 3, executionCount.get());
  }

  @Test
  public void testLoader() {
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(Integer key) {
            return key * 2;
          }
        });
      }
    });
    assertEquals((Integer) 10, c.get(5));
    assertEquals((Integer) 20, c.get(10));
    assertFalse(c.containsKey(2));
    assertTrue(c.containsKey(5));
  }

  @Test
  public void testLoadNull() {
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(Integer key) {
            return null;
          }
        })
          .permitNullValues(true);
      }
    });
    assertNull(c.get(5));
    assertTrue(c.containsKey(5));
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
      .loader((AsyncCacheLoader<Integer, Integer>) (key, context, callback) -> {
        context.getExecutor().execute(() -> {
          callback.onLoadFailure(new AlwaysFailException());
        });
      }));
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

  @Test @Ignore // FIXME: change interface
  public void loadExceptionBulkAsyncSyncLoaderDelayedFail() {
    Cache<Integer, Integer> c = target.cache(b -> b
      .bulkLoader((keys, contexts, callback) -> {

      })
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
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(Integer key) {
            return null;
          }
        });
      }
    });
    try {
      c.get(5);
      fail();
    } catch (CacheLoaderException expected) { }
  }

  @Test
  public void testLoadNull_NoCache() {
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(Integer key) {
            return null;
          }
        })
          .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
            @Override
            public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                            CacheEntry<Integer, Integer> currentEntry) {
              return NOW;
            }
          });
      }
    });
    assertNull(c.get(5));
    assertFalse(c.containsKey(5));
  }

  @Test
  public void testAdvancedLoader() {
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader(new AdvancedCacheLoader<Integer, Integer>() {
          @Override
          public Integer load(Integer key, long startTime, CacheEntry<Integer, Integer> e) {
            return key * 2;
          }
        });
      }
    });
    assertEquals((Integer) 10, c.get(5));
    assertEquals((Integer) 20, c.get(10));
    assertFalse(c.containsKey(2));
    assertTrue(c.containsKey(5));
  }

  @Test
  public void testLoadAll() {
    AtomicInteger countLoad = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(Integer key) {
            return countLoad.incrementAndGet();
          }
        });
      }
    });
    c.get(5);
    CompletionWaiter w = new CompletionWaiter();
    c.loadAll(asList(5, 6), w);
    w.awaitCompletion();
    assertEquals(2, countLoad.get());
    assertEquals((Integer) 2, c.get(6));
    c.loadAll(asList(5, 6), null);
    c.loadAll(Collections.EMPTY_SET, null);
  }

  @Test
  public void testReloadAll() throws ExecutionException, InterruptedException {
    AtomicInteger countLoad = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(Integer key) {
            return countLoad.incrementAndGet();
          }
        });
      }
    });
    c.get(5);
    assertEquals(1, countLoad.get());
    c.reloadAll(asList(5, 6)).get();
    assertEquals(3, countLoad.get());
    c.reloadAll(asList(5, 6));
    c.reloadAll(Collections.EMPTY_SET);
  }

  /**
   * getAsyncLoadsStarted uses the task count from the executor which is not
   * exact. We use is since we only want to know whether the loader will
   * be invoked, testing for the enqueued task is sufficient and faster.
   * Await the execution of the loader as fallback.
   */
  private boolean isLoadStarted(Cache<Integer, Integer> cache) {
    if (latestInfo(cache).getAsyncLoadsStarted() > 0) {
      return true;
    }
    await("Await loader execution", new Condition() {
      @Override
      public boolean check() {
        return loaderExecutionCount > 0;
      }
    });
    return true;
  }

  /**
   * We should always have two loader threads.
   */
  @Test
  public void testTwoLoaderThreadsAndPoolInfo() throws Exception {
    CountDownLatch inLoader = new CountDownLatch(2);
    CountDownLatch releaseLoader = new CountDownLatch(1);
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(Integer key) throws Exception {
            inLoader.countDown();
            releaseLoader.await();
            return key * 2;
          }
        });
      }
    });
    c.loadAll(asList(1), null);
    c.loadAll(asList(2), null);
    inLoader.await();
    assertEquals(2, latestInfo(c).getAsyncLoadsStarted());
    assertEquals(2, latestInfo(c).getAsyncLoadsInFlight());
    assertEquals(2, latestInfo(c).getLoaderThreadsMaxActive());
    releaseLoader.countDown();
  }

  /**
   * Start two overlapping loads, expect that one is done in the caller thread,
   * since only one thread is available.
   */
  @Test
  public void testOneLoaderThreadsAndPoolInfo() throws Exception {
    Thread callingThread = Thread.currentThread();
    CountDownLatch inLoader = new CountDownLatch(1);
    CountDownLatch releaseLoader = new CountDownLatch(1);
    AtomicInteger asyncCount = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loaderThreadCount(1)
          .loader(new CacheLoader<Integer, Integer>() {
            @Override
            public Integer load(Integer key) throws Exception {
              if (callingThread != Thread.currentThread()) {
                asyncCount.incrementAndGet();
                inLoader.countDown();
                releaseLoader.await();
              }
              return key * 2;
            }
          });
      }
    });
    c.loadAll(asList(1), null);
    c.loadAll(asList(2), null);
    inLoader.await();
    assertEquals("only one load is separate thread", 1, latestInfo(c).getAsyncLoadsStarted());
    assertEquals("only one load is separate thread", 1, asyncCount.get());
    assertEquals(1, latestInfo(c).getAsyncLoadsInFlight());
    assertEquals(1, latestInfo(c).getLoaderThreadsMaxActive());
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
      b.loader((AsyncCacheLoader<Integer, Integer>) (key, context, callback) -> {
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
              exceptionCollector.collect(throwable);
              complete.countDown(); return null; });
        } else {
          c.loadAll(Collections.singleton(anyKey))
            .handle((unused, throwable) -> {
              exceptionCollector.collect(throwable);
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
      assertEquals(waiterCount, loaderCallCount.get());
    } else {
      assertEquals(1, loaderCallCount.get());
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
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loaderExecutor(ForkJoinPool.commonPool());
        b.loader((AsyncCacheLoader<Integer, Integer>) (key, ctx, callback) -> {
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
      }
    });
    ExceptionCollector exceptionCollector = new ExceptionCollector();
    for (int i = 0; i < count; i++) {
      c.loadAll(asList(1 + (i / 2), 2 + (i / 2), 3 + (i / 2))).handle((unused, throwable) -> {
        exceptionCollector.collect(throwable);
        complete.countDown();
        return null;
      });
    }
    releaseLoader.countDown();
    boolean okay = complete.await(3, TimeUnit.SECONDS);
    exceptionCollector.assertNoException();
    assertTrue("no timeout", okay);
  }

  /**
   * Execute loader in another thread.
   */
  @Test
  public void testAsyncLoaderLoadViaExecutor() {
    AtomicInteger loaderCalled = new AtomicInteger();
    AtomicInteger loaderExecuted = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader((AsyncCacheLoader<Integer, Integer>) (key, ctx, callback) -> {
          loaderCalled.incrementAndGet();
          ctx.getLoaderExecutor().execute(new Runnable() {
            @Override
            public void run() {
              loaderExecuted.incrementAndGet();
              callback.onLoadSuccess(key);
            }
          });
        });
      }
    });
    Integer v = c.get(1);
    assertEquals(1, (int) v);
  }

  /**
   * Call the callback within the loading thread.
   */
  @Test
  public void testAsyncLoaderLoadDirect() {
    AtomicInteger loaderCalled = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader(new AsyncCacheLoader<Integer, Integer>() {
          @Override
          public void load(Integer key, Context<Integer, Integer> ctx, Callback<Integer> callback) {
            loaderCalled.incrementAndGet();
            callback.onLoadSuccess(key);
          }
        });
      }
    });
    Integer v = c.get(1);
    assertEquals(1, (int) v);
  }

  @Test
  public void testAsyncLoaderContextProperties() {
    AtomicInteger loaderCalled = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader((AsyncCacheLoader<Integer, Integer>) (key, ctx, callback) -> {
          int cnt = loaderCalled.getAndIncrement();
          if (cnt == 0) {
            assertNull(ctx.getCurrentEntry());
          } else {
            assertEquals(key, ctx.getCurrentEntry().getValue());
            assertNull(ctx.getCurrentEntry().getException());
          }
          callback.onLoadSuccess(key);
        });
      }
    });
    Integer v = c.get(1);
    assertEquals(1, (int) v);
    reload(c, 1);
  }

  @Test
  public void testAsyncLoaderContextProperties_withException() {
    AtomicInteger loaderCalled = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.expireAfterWrite(TestingParameters.MAX_FINISH_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        b.resiliencePolicy(new ExpiryTest.EnableExceptionCaching());
        b.loader(new AsyncCacheLoader<Integer, Integer>() {
          @Override
          public void load(Integer key, Context<Integer, Integer> ctx, Callback<Integer> callback) {
            int cnt = loaderCalled.getAndIncrement();
            if (cnt == 0) {
              assertNull(ctx.getCurrentEntry());
            } else {
              assertNull(ctx.getCurrentEntry());
            }
            callback.onLoadFailure(new ExpectedException());
          }
        });
      }
    });
    assertThatCode(() -> c.get(1))
      .getRootCause().isInstanceOf(ExpectedException.class);
    assertNotNull("exception cached", c.peekEntry(1).getException());
    assertThatCode(() -> c.reloadAll(asList(1)).get())
      .getRootCause().isInstanceOf(ExpectedException.class);
  }

  /**
   * Check that exception isn't blocking anything
   */
  @Test
  public void testAsyncLoaderException() {
    AtomicInteger loaderCalled = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader(new AsyncCacheLoader<Integer, Integer>() {
          @Override
          public void load(Integer key, Context<Integer, Integer> ctx, Callback<Integer> callback) {
            loaderCalled.incrementAndGet();
            throw new ExpectedException();
          }
        });
      }
    });
    try {
      c.get(1);
      fail("exception expected");
    } catch (CacheLoaderException expected) {
      assertTrue(expected.getCause() instanceof ExpectedException);
    }
    c.put(1, 1);
    assertNotNull(c.get(1));
  }

  @Test
  public void testAsyncLoaderWithExecutorWithAsync() {
    AtomicInteger loaderCalled = new AtomicInteger();
    AtomicInteger loaderExecuted = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader((AsyncCacheLoader<Integer, Integer>) (key, ctx, callback) -> {
          loaderCalled.incrementAndGet();
          ctx.getLoaderExecutor().execute(() -> {
            loaderExecuted.incrementAndGet();
            callback.onLoadSuccess(key);
          });
        });
      }
    });
    CompletionWaiter w = new CompletionWaiter();
    c.loadAll(asList(1, 2, 1802), w);
    w.awaitCompletion();
    assertEquals(1, (int) c.peek(1));
    Object o1 = c.peek(1802);
    assertTrue(c.peek(1802) == o1);
    w = new CompletionWaiter();
    c.reloadAll(asList(1802, 4, 5), w);
    w.awaitCompletion();
    assertNotNull(c.peek(1802));
    assertTrue(c.peek(1802) != o1);
  }

  @Test
  public void testAsyncLoaderWithExecutorWithAsyncCopy() throws Exception {
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader((AsyncCacheLoader<Integer, Integer>) (key, ctx, callback) ->
          ctx.getLoaderExecutor().execute(() -> callback.onLoadSuccess(key)));
      }
    });
    c.loadAll(asList(1, 2, 1802)).get();
    assertNotNull(c.peek(1802));
    assertEquals(1, (int) c.peek(1));
    Object o1 = c.peek(1802);
    assertTrue(c.peek(1802) == o1);
    c.reloadAll(asList(1802, 4, 5)).get();
    assertNotNull(c.peek(1802));
    assertTrue(c.peek(1802) != o1);
  }

  @Test
  public void testAsyncLoaderDoubleCallback() {
    AtomicInteger loaderCalled = new AtomicInteger();
    AtomicInteger loaderExecuted = new AtomicInteger();
    AtomicInteger gotException = new AtomicInteger();
    AtomicInteger gotNoException = new AtomicInteger();
    AtomicReference<Throwable> otherException = new AtomicReference<Throwable>();
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader(new AsyncCacheLoader<Integer, Integer>() {
          @Override
          public void load(Integer key, Context<Integer, Integer> ctx,
                           Callback<Integer> callback) {
            ctx.getLoaderExecutor().execute(new Runnable() {
              @Override
              public void run() {
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
              }
            });
            loaderCalled.incrementAndGet();
          }
        });
      }
    });
    CompletionWaiter w = new CompletionWaiter();
    c.loadAll(Collections.EMPTY_LIST, w);
    w.awaitCompletion();
    w = new CompletionWaiter();
    c.loadAll(asList(1, 2, 1802), w);
    w.awaitCompletion();
    assertNull(otherException.get());
    assertEquals("loader called", 3, loaderCalled.get());
    assertEquals("loader Executed", 3, loaderExecuted.get());
    await("wait for 3 exceptions", new Condition() {
      @Override
      public boolean check() {
        return gotException.get() == 3;
      }
    });
    assertEquals("always throws exception", 0, gotNoException.get());
    w = new CompletionWaiter();
    c.loadAll(asList(1, 2, 1802), w);
    w.awaitCompletion();
    assertEquals(1, (int) c.peek(1));
    Object o1 = c.peek(1802);
    assertTrue(c.peek(1802) == o1);
    w = new CompletionWaiter();
    c.reloadAll(asList(1802, 4, 5), w);
    w.awaitCompletion();
    assertNotNull(c.peek(1802));
    assertTrue(c.peek(1802) != o1);
  }

  @Test
  public void testAsyncLoaderDoubleCallbackDifferentThreads() {
    AtomicInteger loaderCalled = new AtomicInteger();
    AtomicInteger loaderExecuted = new AtomicInteger();
    AtomicInteger gotException = new AtomicInteger();
    AtomicInteger gotNoException = new AtomicInteger();
    AtomicReference<Throwable> otherException = new AtomicReference<Throwable>();
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loaderExecutor(Executors.newCachedThreadPool());
        b.loader((AsyncCacheLoader<Integer, Integer>) (key, ctx, callback) -> {
          Runnable command = new Runnable() {
            @Override
            public void run() {
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
            }
          };
          ctx.getLoaderExecutor().execute(command);
          ctx.getLoaderExecutor().execute(command);
          loaderCalled.incrementAndGet();
        });
      }
    });
    CompletionWaiter w = new CompletionWaiter();
    c.loadAll(Collections.EMPTY_LIST, w);
    w.awaitCompletion();
    w = new CompletionWaiter();
    c.loadAll(asList(1, 2, 1802), w);
    w.awaitCompletion();
    if (otherException.get() != null) {
      otherException.get().printStackTrace();
      assertNull(otherException.get().toString(), otherException.get());
    }
    assertEquals("loader called", 3, loaderCalled.get());
    await("wait for 6 executions", new Condition() {
      @Override
      public boolean check() {
        return loaderExecuted.get() == 6;
      }
    });
    await("wait for 3 exceptions", new Condition() {
      @Override
      public boolean check() {
        return gotException.get() == 3;
      }
    });
    await("wait for 3 successful executions", new Condition() {
      @Override
      public boolean check() {
        return gotNoException.get() == 3;
      }
    });
    w = new CompletionWaiter();
    c.loadAll(asList(1, 2, 1802), w);
    w.awaitCompletion();
    assertEquals(1, (int) c.peek(1));
    Object o1 = c.peek(1802);
    assertTrue(c.peek(1802) == o1);
    w = new CompletionWaiter();
    c.reloadAll(asList(1802, 4, 5), w);
    w.awaitCompletion();
    assertNotNull(c.peek(1802));
    assertTrue(c.peek(1802) != o1);
  }

  protected Cache<Integer, Integer> cacheWithLoader() {
    return target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(Integer key) {
            loaderExecutionCount++;
            return key * 2;
          }
        });
      }
    });
  }

  @Test
  public void asyncBulkLoader_direct() throws Exception {
    final int assertKey = 987;
    final int exceptionKey = 789;
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader((AsyncBulkCacheLoader<Integer, Integer>) (keys, context, callback) ->
          {
            int firstKey = keys.iterator().next();
            assertNotEquals("No assertion provoked", assertKey, firstKey);
            if (exceptionKey == firstKey) {
              throw new ExpectedException();
            }
            keys.forEach(key -> callback.onLoadSuccess(key, key));
          }
        );
      }
    });
    
    c.loadAll(asList(1, 2, 1802)).get();
    assertNotNull(c.peek(1802));
    assertEquals(1, (int) c.peek(1));
    Object o1 = c.peek(1802);
    assertTrue(c.peek(1802) == o1);
    c.reloadAll(asList(1802, 4, 5)).get();
    assertNotNull(c.peek(1802));
    assertTrue(c.peek(1802) != o1);
  }

  public static class AsyncLoadBuffer<K, V> {
    private int startedLoadRequests = 0;
    private final Map<K, AsyncCacheLoader.Callback<V>> pending = new HashMap<>();
    private final Function<K, V> loader;
    public AsyncLoadBuffer(Function<K, V> loader) { this.loader = loader; }
    public synchronized void put(K key, AsyncCacheLoader.Callback<V> cb) {
      startedLoadRequests++;
      cb = pending.putIfAbsent(key, cb);
      assertNull("no request pending for " + key, cb);
    }
    public void put(K key, AsyncBulkCacheLoader.BulkCallback<K, V> cb) {
      put(key, new BulkCallbackWrapper<K, V>(key, cb));
    }
    public synchronized void complete(K key) {
      notifyAll();
      pending.compute(key, (k, vCallback) -> {
        assertNotNull("Expected pending. Exception?! Key: " + key, vCallback);
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
      Map<K, V> map = new HashMap<>();
      K key = keys[0];
      AsyncBulkCacheLoader.BulkCallback<K, V> cb = getBulkCallback(key);
      map.put(key, loader.apply(key));
      for (int i = 1; i < keys.length; i++) {
        key = keys[i];
        AsyncBulkCacheLoader.BulkCallback<K, V> cb2 = getBulkCallback(key);
        assertSame("belongs to same bulk request", cb, cb2);
        map.put(key, loader.apply(key));
      }
      cb.onLoadSuccess(map);
    }
    /** Expect that async load is started for the set of keys */
    public synchronized void assertStarted(K... keys) {
      for (K k : keys) {
        assertNotNull("load request pending for " + k, pending.get(k));
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
    private AsyncBulkCacheLoader.BulkCallback<K, V> cb;
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
    Cache<Integer, Integer> cache = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.bulkLoader((keys, contextSet, callback) -> {
          bulkRequests.incrementAndGet();
          keys.forEach(key -> buffer.put(key, callback));
        });
      }
    });
    CompletableFuture<Void> req1 = cache.loadAll(asList(1, 2, 3));
    buffer.assertStarted(1, 2, 3);
    CompletableFuture<Void> req2 = cache.loadAll(asList(1, 2, 3));
    CompletableFuture<Void> req3 = cache.loadAll(asList(1, 2, 3, 4, 5));
    buffer.assertStarted(4, 5);
    buffer.bulkComplete(1, 2, 3);
    CompletableFuture<Void> req4 = cache.loadAll(asList(1, 2, 3, 4, 5, 6, 7));
    assertTrue("completed in our thread", req1.isDone());
    await("completed via executor", () -> req2.isDone());
    assertFalse(req3.isDone());
    buffer.complete(4);
    buffer.bulkComplete(5);
    assertTrue("completed in our thread", req3.isDone());
    Map<Integer, Integer> res = cache.getAll(asList(1, 2, 3, 4, 5));
    buffer.bulkComplete(6, 7);
    assertEquals(7, buffer.getStartedLoadRequests());
    assertEquals(3, bulkRequests.get());
  }

  @Test
  public void asyncBulkLoader_singleLoad() throws Exception {
    AsyncLoadBuffer<Integer, Integer> buffer = new AsyncLoadBuffer<>(k -> k);
    Cache<Integer, Integer> cache = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.bulkLoader((keys, context, callback) -> {
          assertEquals(keys.size(), context.getContextMap().size());
          keys.forEach(key -> {
            assertTrue(keys.contains(key));
            buffer.put(key, callback);
          });
        });
      }
    });
    CompletableFuture<Void> req1 = cache.loadAll(asList(1, 2, 3));
    assertThat(req1).hasNotFailed();
    CompletableFuture<Void> req2 = cache.loadAll(asList(1, 2, 3, 4));
    assertThat(req2).hasNotFailed();
    buffer.complete(1, 2, 3, 4);
    req1.get();
    req2.get();
  }

  @Test
  public void asyncBulkLoader_immediateException_loadAll_get_getAll() throws Exception {
    Cache<Integer, Integer> cache = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.bulkLoader((keys, contextSet, callback) -> {
          throw new ExpectedException();
        });
      }
    });
    CompletableFuture<Void> req1 = cache.loadAll(asList(1, 2, 3));
    assertTrue("exception expected", req1.isCompletedExceptionally());
    CompletableFuture<Void> req2 = cache.loadAll(asList(4));
    assertTrue("exception expected", req2.isCompletedExceptionally());
    for (int i = 1; i < 6; i++) {
      CacheEntry<Integer, Integer> entry = cache.getEntry(i);
      assertNotNull("expect exception", entry.getException());
      assertThat(entry.getException()).isInstanceOf(ExpectedException.class);
    }
    assertThatCode(() -> {
      cache.getAll(asList(1, 2, 3));
    }).describedAs("Expect exception if all requested keys yield and exception")
      .isInstanceOf(CacheLoaderException.class);
  }

  @Test @Ignore
  public void asyncBulkLoader_enforceSingleLoad() throws InterruptedException {
    AtomicInteger bulkRequests = new AtomicInteger();
    AsyncLoadBuffer<Integer, Integer> buffer = new AsyncLoadBuffer<>(k -> k);
    Cache<Integer, Integer> cache = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.bulkLoader((keys, context, callback) -> {
          bulkRequests.incrementAndGet();
          keys.forEach(key -> buffer.put(key, callback));
        });
      }
    });
    CompletableFuture<Void> req1 = cache.loadAll(asList(1, 2, 3));
    buffer.assertStarted(1, 2, 3);
    SupervisedExecutor exe = executor();
    exe.execute(() -> {
      cache.getAll(asList(1, 2, 3, 4));
    });
    buffer.awaitStarted(1, 2, 3, 4);
    buffer.complete(2);
    buffer.bulkComplete(1, 3);
    buffer.complete(4);
    exe.join();
  }

  @Test
  public void bulkLoader_loadAll() throws Exception {
    AtomicInteger bulkRequests = new AtomicInteger();
    Cache<Integer, Integer> cache = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.bulkLoader(keys -> {
          bulkRequests.incrementAndGet();
          Map<Integer, Integer> result = new HashMap<>();
          for (Integer key : keys) {
            result.put(key, key);
          }
          return result;
        });
      }
    });
    CompletableFuture<Void> req1 = cache.loadAll(asList(1, 2, 3));
    CompletableFuture<Void> req2 = cache.loadAll(asList(1, 2, 3));
    CompletableFuture<Void> req3 = cache.loadAll(asList(1, 2, 3, 4, 5));
    CompletableFuture<Void> req4 = cache.loadAll(asList(1, 2, 3, 4, 5, 6, 7));
    req4.get();
    assertEquals(7, target.info().getLoadCount());
    int bulkRequests0 = bulkRequests.get();
    assertThat(bulkRequests.get())
      .describedAs("May as well be just one bulk request, if execution gets delayed")
      .isIn(1, 2, 3, 4);
    CompletableFuture<Void> req5 = cache.reloadAll(asList(2, 3, 9));
    req5.get();
    assertEquals(10, target.info().getLoadCount());
    assertEquals(bulkRequests0 + 1, bulkRequests.get());
  }

  @Test
  public void bulkLoader_getAll() throws Exception {
    AtomicInteger bulkRequests = new AtomicInteger();
    Cache<Integer, Integer> cache = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.bulkLoader(keys -> {
          bulkRequests.incrementAndGet();
          Map<Integer, Integer> result = new HashMap<>();
          for (Integer key : keys) {
            result.put(key, key);
          }
          return result;
        });
      }
    });
    CompletableFuture<Void> req1 = cache.loadAll(asList(1, 2, 3));
    Map<Integer, Integer> result = cache.getAll(asList(3, 4, 5));
    req1.get();
    Map<Integer, Integer> result2 = cache.getAll(asList(1, 2, 3, 4, 5));
    result2.forEach((k, v) -> assertEquals((int) k, (int) v));
    assertEquals(5, target.info().getLoadCount());
    assertEquals(2, bulkRequests.get());
    Map<Integer, Integer> result3 = cache.getAll(asList(4, 5, 6, 7));
    result3.forEach((k, v) -> assertEquals((int) k, (int) v));
  }

  @Test
  public void bulkLoader_getAll_twoThreads() throws Exception {
    AtomicInteger bulkRequests = new AtomicInteger();
    Cache<Integer, Integer> cache = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.bulkLoader(keys -> {
          bulkRequests.incrementAndGet();
          Map<Integer, Integer> result = new HashMap<>();
          for (Integer key : keys) {
            result.put(key, key);
          }
          return result;
        });
      }
    });
    execute(() -> cache.getAll(asList(1, 2, 3)));
    Map<Integer, Integer> result2 = cache.getAll(asList(1, 2, 3));
    result2.forEach((k, v) -> assertEquals((int) k, (int) v));
    join();
  }

  @Test
  public void bulkLoader_invokeAll() throws Exception {
    AtomicInteger bulkRequests = new AtomicInteger();
    Cache<Integer, Integer> cache = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.bulkLoader(keys -> {
          bulkRequests.incrementAndGet();
          Map<Integer, Integer> result = new HashMap<>();
          for (Integer key : keys) {
            result.put(key, key);
          }
          return result;
        });
      }
    });
    CompletableFuture<Void> req1 = cache.loadAll(asList(1, 2, 3));
    Map<Integer, EntryProcessingResult<Integer>> result =
      cache.invokeAll(asList(3, 4, 5), entry -> entry.getValue());
    req1.get();
    Map<Integer, EntryProcessingResult<Integer>> result2 =
      cache.invokeAll(asList(1, 2, 3, 4, 5), entry -> entry.getValue());
    assertEquals(5, target.info().getLoadCount());
    assertEquals(2, bulkRequests.get());
    assertEquals(5, result2.size());
    assertEquals((Integer) 2, result2.get(2).getResult());
    assertNull(result2.get(2).getException());
    Map<Integer, EntryProcessingResult<Integer>> result3 =
      cache.invokeAll(asList(1, 2, 3), entry -> { throw new ExpectedException(); });
    assertEquals(3, result3.size());
    assertThat(result3.get(2).getException())
      .as("Propagates exception")
      .isInstanceOf(ExpectedException.class);
    assertThatCode(() -> result3.get(2).getResult())
      .isInstanceOf(EntryProcessingException.class)
      .getCause()
      .isInstanceOf(ExpectedException.class);
  }

  @Test
  public void advancedLoaderEntryNotSetIfExpired() {
    Cache<Integer, Integer> c = target.cache(new CacheRule.Context<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader(new AdvancedCacheLoader<Integer, Integer>() {
          @Override
          public Integer load(Integer key, long startTime,
                              CacheEntry<Integer, Integer> currentEntry) {
            assertNull(currentEntry);
            return key;
          }
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
        b.loader(new AdvancedCacheLoader<Integer, Integer>() {
          @Override
          public Integer load(Integer key, long startTime,
                              CacheEntry<Integer, Integer> currentEntry) {
            if (expectEntry.get()) {
              assertNotNull(currentEntry);
            } else {
              assertNull(currentEntry);
            }
            return key;
          }
        });
      }
    });
    c.get(123);
    c.expireAt(123, ExpiryTimeValues.NOW);
    expectEntry.set(true);
    c.get(123);
  }

  public static class CompletionWaiter implements CacheOperationCompletionListener {

    CountDownLatch latch = new CountDownLatch(1);
    volatile Throwable exception;

    @Override
    public void onCompleted() {
      latch.countDown();
    }

    @Override
    public void onException(Throwable exception) {
      this.exception = exception;
      latch.countDown();
    }

    public void awaitCompletion() {
      while (latch.getCount() > 0) {
        try {
          latch.await();
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
      }
      if (exception != null) {
        throw new RuntimeException(exception);
      }
    }

    public Throwable getException() {
      return exception;
    }

  }

}
