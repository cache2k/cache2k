package org.cache2k.testsuite.stress;

/*
 * #%L
 * cache2k testsuite on public API
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
import org.cache2k.io.AsyncCacheLoader;
import org.cache2k.pinpoint.stress.ThreadingStressTester;
import org.cache2k.testing.category.SlowTests;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@Category(SlowTests.class)
public class AsyncLoaderStressTest {

  /**
   * Issue a reload and check whether the updated value is visible afterwards.
   */
  @Test
  public void testVisibility1() {
    Cache<Integer, Integer> c = Cache2kBuilder.of(Integer.class, Integer.class)
        .loader((AsyncCacheLoader<Integer, Integer>)
          (key, ctx, callback) -> ctx.getExecutor().execute(
            () -> callback.onLoadSuccess(key)
          )
        )
        .build();
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.addExceptionalTask(() -> {
      Object obj = c.peek(1802);
      c.reloadAll(asList(1802, 4, 5)).get();
      Object obj2 = c.peek(1802);
      assertTrue(obj != obj2);
    });
    tst.setDoNotInterrupt(true);
    tst.run();
    c.close();
  }

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

}
