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

import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.io.LoadExceptionInfo;
import org.cache2k.io.ResiliencePolicy;
import org.cache2k.operation.Scheduler;
import org.cache2k.operation.TimeReference;
import org.cache2k.test.util.TestingBase;
import org.cache2k.annotation.Nullable;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * @author Jens Wilke
 */
public class CustomizationClosedTest extends TestingBase {

  AtomicInteger unclosed = new AtomicInteger();

  @Test
  public void resiliencePolicy() {
    check(builder().resiliencePolicy(new MyResiliencePolicy()));
  }

  @Test
  public void expiryPolicy() {
    check(builder().expiryPolicy(new MyExpiryPolicy()));
  }

  @Test
  public void expiryAndResiliencePolicy() {
    check(builder()
      .expiryPolicy(new MyExpiryPolicy())
      .resiliencePolicy(new MyResiliencePolicy())
    );
  }

  @Test
  public void scheduler() {
    check(builder()
      .expireAfterWrite(5, TimeUnit.MINUTES)
      .scheduler(new MyScheduler()));
  }

  @Test
  public void timeReference() {
    check(builder()
      .expireAfterWrite(5, TimeUnit.MINUTES)
      .timeReference(new MyTimeReference()));
  }

  void check(Cache2kBuilder b) {
    b.build().close();
    assertEquals("all closed", 0, unclosed.get());
  }

  class Common implements AutoCloseable {
    { unclosed.getAndIncrement(); }
    @Override
    public void close() {
      unclosed.decrementAndGet();
    }
  }

  class MyResiliencePolicy extends Common implements ResiliencePolicy {
    @Override
    public long suppressExceptionUntil(Object key, LoadExceptionInfo loadExceptionInfo,
                                       CacheEntry cachedEntry) {
      return 0;
    }
    @Override
    public long retryLoadAfter(Object key, LoadExceptionInfo loadExceptionInfo) {
      return 0;
    }
  }

  class MyExpiryPolicy extends Common implements ExpiryPolicy {
    @Override
    public long calculateExpiryTime(Object key, Object value, long startTime,
                                    @Nullable CacheEntry currentEntry) {
      return 0;
    }
  }

  class MyTimeReference extends Common implements TimeReference {
    @Override
    public long millis() {
      return 0;
    }
    @Override
    public void sleep(long millis) { }
  }

  class MyScheduler extends Common implements Scheduler {
    @Override
    public void schedule(Runnable runnable, long millis) { }

    @Override
    public void execute(Runnable command) { }
  }

}
