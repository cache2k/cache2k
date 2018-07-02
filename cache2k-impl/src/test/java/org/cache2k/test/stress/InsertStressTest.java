package org.cache2k.test.stress;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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
import org.cache2k.core.AbstractEviction;
import org.cache2k.test.core.TestingParameters;
import org.cache2k.testing.category.SlowTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple stress test that does a parallel insert with 4 threads.
 *
 * @author Jens Wilke
 */
@Category(SlowTests.class)
public class InsertStressTest extends CacheTest {

  @Test
  public void test() {
    final int _COUNT = 100000;
    final long _SIZE = 10000;
    final int _THREADS = 4;
    final int _PER_THREAD = _COUNT / _THREADS;
    final Cache<Integer, Integer> c =
      freshCache(Integer.class, Integer.class, null, _SIZE, -1);
    final AtomicInteger _offset = new AtomicInteger();
    Runnable _inserter = new Runnable() {
      @Override
      public void run() {
        int _start = _offset.getAndAdd(_PER_THREAD);
        int _end = _start + _PER_THREAD;
        for (int i = _start; i < _end; i++) {
          c.put(i, i);
        }
      }
    };
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.setOneShotMode(true);
    tst.setOneShotTimeoutMillis(TestingParameters.MAX_FINISH_WAIT_MILLIS);
    tst.addTask(_THREADS, _inserter);
    tst.run();
    assertThat("Within size bound", getInfo().getSize(), lessThanOrEqualTo(
      _SIZE +
      Runtime.getRuntime().availableProcessors() * (AbstractEviction.MAXIMAL_CHUNK_SIZE / 2)));
  }

}
