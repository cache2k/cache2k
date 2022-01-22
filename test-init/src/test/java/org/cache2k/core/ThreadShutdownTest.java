package org.cache2k.core;

/*-
 * #%L
 * cache2k initialization tests
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
import org.junit.jupiter.api.Test;

import static java.lang.Thread.sleep;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.cache2k.Cache2kBuilder.forUnknownTypes;

/**
 * @author Jens Wilke
 */
public class ThreadShutdownTest {

  /*
   * This needs to be first for the other test
   */
  static {
    CacheManagerInitTest.setupOtherDefaultManagerName();
  }

  int countThreads() {
    int cache2kThreadCount = 0;
    Thread[] threads = new Thread[Thread.activeCount() * 10];
    int count = Thread.enumerate(threads);
    for (int i = 0; i < count; i++) {
      if (threads[i].getName().contains("cache2k")) {
        cache2kThreadCount++;
      }
    }
    return cache2kThreadCount;
  }

  @Test
  public void testWithExpiry() throws InterruptedException {
    Cache cache = forUnknownTypes().expireAfterWrite(5, MINUTES).build();
    cache.put(1, 3);
    assertThat(countThreads() > 0).isTrue();
    cache.close();
    if (countThreads() > 0) {
      sleep(5000);
      assertThat(countThreads())
        .as("all threads terminate")
        .isEqualTo(0);
    }
  }

}
