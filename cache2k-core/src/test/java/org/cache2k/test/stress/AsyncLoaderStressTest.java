package org.cache2k.test.stress;

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
import org.cache2k.io.AsyncCacheLoader;
import org.cache2k.test.core.CacheLoaderTest;
import org.cache2k.test.util.TestingBase;
import org.cache2k.test.util.ThreadingStressTester;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
public class AsyncLoaderStressTest extends TestingBase {

  /**
   * Issue a reload and check whether the updated value is visible afterwards.
   */
  @Test
  public void testVisibility1() {
    Cache<Integer, Integer> c =
      builder()
        .loader((AsyncCacheLoader<Integer, Integer>)
          (key, ctx, callback) -> ctx.getLoaderExecutor().execute(
            () -> callback.onLoadSuccess(key)
          )
        )
        .build();
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.addTask(() -> {
      for (;;) {
        Object obj = c.peek(1802);
        CacheLoaderTest.CompletionWaiter waiter = new CacheLoaderTest.CompletionWaiter();
        c.reloadAll(TestingBase.keys(1802, 4, 5), waiter);
        waiter.awaitCompletion();
        if (Thread.currentThread().isInterrupted()) {
          return;
        }
        assertNotSame(obj, c.peek(1802));
      }
    });
    tst.run();
  }

  /**
   * Issue a reload and check whether the updated value is visible afterwards.
   */
  @Test @Ignore("interrupts don't get through")
  public void testVisibility2() {
    Cache<Integer, Integer> c =
      builder()
        .loader((AsyncCacheLoader<Integer, Integer>)
          (key, ctx, callback) -> ctx.getLoaderExecutor().execute(
            () -> callback.onLoadSuccess(key)
          )
        )
        .build();
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.addExceptionalTask(() -> {
      Object obj = c.peek(1802);
      c.reloadAll(TestingBase.keys(1802, 4, 5)).get();
      assertNotSame(obj, c.peek(1802));
    });
    tst.run();
  }

  @Test @Ignore("Java 11 bug")
  public void testCompletableFuture() {
    ThreadingStressTester tst = new ThreadingStressTester();
    Executor ex = Executors.newCachedThreadPool();
    tst.addExceptionalTask(() -> {
      CompletableFuture<Void> f = new CompletableFuture<>();
      ex.execute(() -> f.complete(null));
      f.get();
    });
    tst.run();
  }

  @Test
  public void testVisibility_CompletableFuture() {
    Cache<Integer, Integer> c =
      builder()
        .loader((AsyncCacheLoader<Integer, Integer>)
          (key, ctx, callback) -> ctx.getLoaderExecutor().execute(
            () -> callback.onLoadSuccess(key)
          )
        )
        .build();
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.setDoNotInterrupt(true);
    tst.addExceptionalTask(() -> {
      Object obj = c.peek(1802);
      c.reloadAll(TestingBase.keys(1802, 4, 5)).get();
      Object obj2 = c.peek(1802);
      assertNotNull(obj2);
      assertNotSame(obj, obj2);
    });
    tst.run();
  }

  @Test
  public void testVisibility_WithExtraCPULoad() {
    Cache<Integer, Integer> c =
      builder()
        .loader((AsyncCacheLoader<Integer, Integer>)
          (key, ctx, callback) -> ctx.getLoaderExecutor().execute(
            () -> callback.onLoadSuccess(key)
          )
        )
        .build();
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.setDoNotInterrupt(true);
    tst.addTask(3, () -> new Random().nextFloat());
    tst.addExceptionalTask(() -> {
      Object obj = c.peek(1802);
      c.reloadAll(TestingBase.keys(1802, 4, 5)).get();
      Object obj2 = c.peek(1802);
      assertNotNull(obj2);
      assertNotSame(obj, obj2);
    });
    tst.run();
  }

  @Test
  public void testVisibility_WithExtraCPULoad_Multi() {
    Cache<Integer, Integer> c =
      builder()
        .loader((AsyncCacheLoader<Integer, Integer>)
          (key, ctx, callback) -> ctx.getLoaderExecutor().execute(
            () -> callback.onLoadSuccess(key)
          )
        )
        .build();
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.setDoNotInterrupt(true);
    tst.addTask(3, () -> new Random().nextFloat());
    tst.addExceptionalTask(3, () -> {
      Object obj = c.peek(1802);
      c.reloadAll(TestingBase.keys(1802, 4, 5)).get();
      Object obj2 = c.peek(1802);
      assertNotNull(obj2);
      assertNotSame(obj, obj2);
    });
    tst.run();
  }

  @Test
  public void testInsertVisibility_WithExtraCPULoad_Multi() {
    Cache<Integer, Integer> c =
      builder()
        .loader((AsyncCacheLoader<Integer, Integer>)
          (key, ctx, callback) -> ctx.getLoaderExecutor().execute(
            () -> callback.onLoadSuccess(key)
          )
        )
        .build();
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.setDoNotInterrupt(true);
    tst.addTask(3, () -> new Random().nextFloat());
    tst.addExceptionalTask(() -> {
      c.reloadAll(TestingBase.keys(1802, 4, 5)).get();
      assertNotNull(c.peek(1802));
      c.remove(1802);
    });
    tst.run();
  }

  @Test @Ignore("Java 11 bug")
  public void testCompletableFutureGet() throws InterruptedException {
    Executor executor = Executors.newCachedThreadPool();
    Thread t = new Thread(() -> {
      while (!Thread.interrupted()) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        executor.execute(() -> future.complete(null));
        try {
          future.get();
        } catch (InterruptedException e) {
          return;
        } catch (ExecutionException e) {
          e.printStackTrace();
        }
      }
    });
    t.start();
    Thread.sleep(1000);
    t.interrupt();
    t.join();
  }

}
