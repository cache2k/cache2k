package org.cache2k.core;

/*
 * #%L
 * cache2k implementation
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
import org.cache2k.Weigher;
import org.cache2k.test.core.TestingParameters;
import org.cache2k.test.util.TestingBase;
import org.cache2k.test.util.ThreadingStressTester;
import org.cache2k.testing.category.SlowTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@Category(SlowTests.class)
public class WeigherStressTest extends TestingBase {

  /**
   * Insert and remove loop in parallel tasks
   */
  @Test
  public void testParallelOnce() {
    final int count = 100000;
    final int threads = 4;
    final int perThread = count / threads;
    final Cache<Integer, Integer> c =
      builder()
        .entryCapacity(-1)
        .weigher(new Weigher<Integer, Integer>() {
        @Override
        public int weigh(Integer key, Integer value) {
          return value;
        }
      })
      .maximumWeight(Long.MAX_VALUE)
      .build();
    final AtomicInteger offset = new AtomicInteger();
    Runnable inserter = new Runnable() {
      @Override
      public void run() {
        int start = offset.getAndAdd(perThread);
        int end = start + perThread;
        for (int i = start; i < end; i++) {
          c.put(i, i);
        }
        for (int i = start; i < end; i++) {
          c.remove(i);
        }
      }
    };
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.setOneShotMode(true);
    tst.setOneShotTimeoutMillis(TestingParameters.MAX_FINISH_WAIT_MILLIS);
    tst.addTask(threads, inserter);
    tst.run();
    assertEquals("total weight is 0", 0, getInfo().getTotalWeight());
  }

  /**
   * Insert remove in separate tasks and a cleanup run at the end
   */
  @Test
  public void testInsertRemoveSeparate() {
    final int count = 100000;
    final int threads = 4;
    final int perThread = count / threads;
    final Cache<Integer, Integer> c =
      builder()
        .entryCapacity(-1)
        .weigher(new Weigher<Integer, Integer>() {
          @Override
          public int weigh(Integer key, Integer value) {
            return value;
          }
        })
        .maximumWeight(Long.MAX_VALUE)
        .build();
    final AtomicInteger offsetInsert = new AtomicInteger();
    final AtomicInteger offsetRemove = new AtomicInteger();
    Runnable inserter = new Runnable() {
      @Override
      public void run() {
        int start = offsetInsert.getAndAdd(perThread);
        int end = start + perThread;
        for (int i = start; i < end; i++) {
          c.put(i, i);
        }
      }
    };
    Runnable remover = new Runnable() {
      @Override
      public void run() {
        int start = offsetRemove.getAndAdd(perThread);
        int end = start + perThread;
        for (int i = start; i < end; i++) {
          c.remove(i);
        }
      }
    };
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.setTestTimeMillis(4000);
    tst.addTask(threads, inserter);
    tst.addTask(threads, remover);
    tst.run();
    for (int k : c.keys()) {
      c.remove(k);
    }
    assertEquals("total weight is 0", 0, getInfo().getTotalWeight());
  }

  /**
   * Insert remove in separate tasks and a cleanup run at the end
   */
  @Test
  public void testInsertAndEvict() {
    final int count = 100000;
    final int threads = 4;
    final int perThread = count / threads;
    final Cache<Integer, Integer> c =
      builder()
        .entryCapacity(-1)
        .weigher(new Weigher<Integer, Integer>() {
          @Override
          public int weigh(Integer key, Integer value) {
            return value;
          }
        })
        .maximumWeight(100)
        .build();
    final AtomicInteger offsetInsert = new AtomicInteger();
    Runnable inserter = new Runnable() {
      @Override
      public void run() {
        int start = offsetInsert.getAndAdd(perThread);
        int end = start + perThread;
        for (int i = start; i < end; i++) {
          c.put(i, i);
        }
      }
    };
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.setTestTimeMillis(4000);
    tst.addTask(threads, inserter);
    tst.run();
    for (int k : c.keys()) {
      c.remove(k);
    }
    assertEquals("total weight is 0", 0, getInfo().getTotalWeight());
  }

}
