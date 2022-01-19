package org.cache2k.extra.spring;

/*-
 * #%L
 * cache2k Spring framework support
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

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cache.Cache.ValueRetrievalException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.cache.Cache;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Copy of the generic spring framework cache test. This should be kept in sync with the
 * spring tests, so we only do minimal modifications here but don't add other stuff.
 *
 * @author Stephane Nicoll
 * @author Jens Wilke
 */
@SuppressWarnings("ConstantConditions")
public abstract class AbstractCacheTests<T extends Cache> {

  protected static final String CACHE_NAME = "testCache";

  protected abstract T getCache();

  protected abstract Object getNativeCache();

  @Test
  public void testCacheName() {
    assertThat(getCache().getName()).isEqualTo(CACHE_NAME);
  }

  @Test
  public void testNativeCache() {
    assertThat(getCache().getNativeCache()).isSameAs(getNativeCache());
  }

  @Test
  public void testCachePut() {
    T cache = getCache();

    String key = createRandomKey();
    Object value = "george";

    assertThat(cache.get(key)).isNull();
    assertThat(cache.get(key, String.class)).isNull();
    assertThat(cache.get(key, Object.class)).isNull();

    cache.put(key, value);
    assertThat(cache.get(key).get()).isEqualTo(value);
    assertThat(cache.get(key, String.class)).isEqualTo(value);
    assertThat(cache.get(key, Object.class)).isEqualTo(value);
    assertThat(cache.get(key, (Class<?>) null)).isEqualTo(value);

    cache.put(key, null);
    Assertions.assertNotNull(cache.get(key));
    assertThat(cache.get(key).get()).isNull();
    assertThat(cache.get(key, String.class)).isNull();
    assertThat(cache.get(key, Object.class)).isNull();
  }

  @Test
  public void testCachePutIfAbsent() {
    T cache = getCache();

    String key = createRandomKey();
    Object value = "initialValue";

    assertThat(cache.get(key)).isNull();
    assertThat(cache.putIfAbsent(key, value)).isNull();
    assertThat(cache.get(key).get()).isEqualTo(value);
    assertThat(cache.putIfAbsent(key, "anotherValue").get()).isEqualTo("initialValue");
    assertThat(cache.get(key).get()).isEqualTo(value); // not changed
  }

  @Test
  public void testCacheRemove() {
    T cache = getCache();

    String key = createRandomKey();
    Object value = "george";

    assertThat(cache.get(key)).isNull();
    cache.put(key, value);
  }

  @Test
  public void evictIfPresent() {
    T cache = getCache();

    String key = createRandomKey();
    assertThat(cache.evictIfPresent(key)).isFalse();
    Object value = "george";

    assertThat(cache.get(key)).isNull();
    cache.put(key, value);
    assertThat(cache.evictIfPresent(key)).isTrue();
  }

  @Test
  public void testCacheClear() {
    T cache = getCache();

    assertThat(cache.get("enescu")).isNull();
    cache.put("enescu", "george");
    assertThat(cache.get("vlaicu")).isNull();
    cache.put("vlaicu", "aurel");
    cache.clear();
    assertThat(cache.get("vlaicu")).isNull();
    assertThat(cache.get("enescu")).isNull();
  }

  @Test
  public void invalidate() {
    T cache = getCache();
    assertThat(cache.invalidate()).isFalse();
    assertThat(cache.get("enescu")).isNull();
    cache.put("enescu", "george");
    assertThat(cache.get("vlaicu")).isNull();
    cache.put("vlaicu", "aurel");
    assertThat(cache.invalidate()).isTrue();
    assertThat(cache.get("vlaicu")).isNull();
    assertThat(cache.get("enescu")).isNull();
  }

  @Test
  public void testCacheGetCallable() {
    doTestCacheGetCallable("test");
  }

  @Test
  public void testCacheGetCallableWithNull() {
    doTestCacheGetCallable(null);
  }

  private void doTestCacheGetCallable(Object returnValue) {
    T cache = getCache();

    String key = createRandomKey();

    assertThat(cache.get(key)).isNull();
    Object value = cache.get(key, () -> returnValue);
    assertThat(value).isEqualTo(returnValue);
    assertThat(cache.get(key).get()).isEqualTo(value);
  }

  @Test
  public void testCacheGetCallableNotInvokedWithHit() {
    doTestCacheGetCallableNotInvokedWithHit("existing");
  }

  @Test
  public void testCacheGetCallableNotInvokedWithHitNull() {
    doTestCacheGetCallableNotInvokedWithHit(null);
  }

  private void doTestCacheGetCallableNotInvokedWithHit(Object initialValue) {
    T cache = getCache();

    String key = createRandomKey();
    cache.put(key, initialValue);

    Object value = cache.get(key, () -> {
      throw new IllegalStateException("Should not have been invoked");
    });
    assertThat(value).isEqualTo(initialValue);
  }

  @Test
  public void testCacheGetCallableFail() {
    T cache = getCache();

    String key = createRandomKey();
    assertThat(cache.get(key)).isNull();

    try {
      cache.get(key, () -> {
        throw new UnsupportedOperationException("Expected exception");
      });
    }
    catch (ValueRetrievalException ex) {
      Assertions.assertNotNull(ex.getCause());
      assertThat(ex.getCause().getClass()).isEqualTo(UnsupportedOperationException.class);
    }
  }

  /**
   * Test that a call to get with a Callable concurrently properly synchronize the
   * invocations.
   */
  @Test
  public void testCacheGetSynchronized() throws InterruptedException {
    T cache = getCache();
    AtomicInteger counter = new AtomicInteger();
    List<Object> results = new CopyOnWriteArrayList<>();
    CountDownLatch latch = new CountDownLatch(10);

    String key = createRandomKey();
    Runnable run = () -> {
      try {
        Integer value = cache.get(key, () -> {
          sleep(50); // make sure the thread will overlap
          return counter.incrementAndGet();
        });
        results.add(value);
      }
      finally {
        latch.countDown();
      }
    };

    for (int i = 0; i < 10; i++) {
      new Thread(run).start();
    }
    latch.await();

    assertThat(results.size()).isEqualTo(10);
    results.forEach(r -> Assertions.assertEquals(1, r)); // Only one method got invoked
  }

  protected static String createRandomKey() {
    return UUID.randomUUID().toString();
  }

}
