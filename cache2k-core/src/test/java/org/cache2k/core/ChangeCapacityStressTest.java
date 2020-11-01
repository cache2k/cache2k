package org.cache2k.core;

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
import org.cache2k.operation.CacheInfo;
import org.cache2k.test.util.TestingBase;
import org.cache2k.pinpoint.stress.ThreadingStressTester;
import org.cache2k.testing.category.SlowTests;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@Category(SlowTests.class)
public class ChangeCapacityStressTest extends TestingBase {

  @Test
  public void test() {
    final Random random = new Random(1802);
    final int count = 10000;
    final long maximumSize = count / 2;
    final int threads = 4;
    final int perThread = count / threads;
    final Cache<Integer, Integer> c =
      builder()
        .entryCapacity(maximumSize)
      .build();
    final AtomicInteger offset = new AtomicInteger();
    final AtomicLong shrinkCount = new AtomicLong();
    for (int i = 0; i < maximumSize + 123; i++) {
      c.put(i, 1);
    }
    Runnable putAndCheck = new Runnable() {
      @Override
      public void run() {
        int start = offset.getAndAdd(perThread);
        int end = start + perThread;
        for (int i = start; i < end; i++) {
          c.put(i, 1);
          c.containsKey(i);
        }
      }
    };
    Runnable changeCapacity = new Runnable() {
      @Override
      public void run() {
        long currentCapacity = CacheInfo.of(c).getEntryCapacity();
        long newCapacity = currentCapacity
          + maximumSize / 5 - random.nextInt((int) maximumSize / 2);
        newCapacity = Math.max(maximumSize / 4, newCapacity);
        if (newCapacity < currentCapacity) {
          shrinkCount.incrementAndGet();
        }
        long sizeBeforeCapacityChange = c.asMap().size();
        getInternalCache().getEviction().changeCapacity(newCapacity);
        long effectiveCapacity = getInfo().getHeapCapacity();
      }
    };
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.addTask(4, putAndCheck);
    tst.addTask(changeCapacity);
    tst.setTestTimeMillis(5000);
    tst.run();
    long effectiveCapacity = getInfo().getHeapCapacity();
    assertThat(
      "cache meeting cap limit (flaky)",
      (long) getInternalCache().getTotalEntryCount(),
      Matchers.lessThanOrEqualTo(effectiveCapacity + threads));
    assertEquals(getInfo().getSize(), countEntriesViaIteration());
  }

}
