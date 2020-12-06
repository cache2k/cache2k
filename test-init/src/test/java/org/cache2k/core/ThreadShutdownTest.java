package org.cache2k.core;

/*
 * #%L
 * cache2k initialization tests
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
import org.cache2k.Cache2kBuilder;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Jens Wilke
 */
public class ThreadShutdownTest {

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
  public void test() {
    Cache cache = Cache2kBuilder.forUnknownTypes().expireAfterWrite(5, TimeUnit.MINUTES).build();
    cache.put(1, 3);
    assertTrue(countThreads() > 0);
    cache.close();
    assertEquals("all threads terminate", 0, countThreads());
  }

}
