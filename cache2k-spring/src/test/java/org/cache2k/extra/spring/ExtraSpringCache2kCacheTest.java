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

import org.cache2k.Cache2kBuilder;
import org.cache2k.io.CacheLoaderException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;
import static org.cache2k.extra.spring.AbstractCacheTests.createRandomKey;

/**
 * Some extra tests not covered by {@link SpringCache2kCacheTests}
 *
 * @author Jens Wilke
 */
@SuppressWarnings("ConstantConditions")
public class ExtraSpringCache2kCacheTest {

  SpringCache2kCache cache;

  @AfterEach
  public void tearDown() {
    if (cache != null) {
      cache.getNativeCache().close();
    }
  }
  protected SpringCache2kCache getCache() {
    return cache =
      new SpringCache2kCacheManager()
        .defaultSetup(b -> b.entryCapacity(10_000))
        .addCaches(b -> b.name(SpringCache2kCacheTests.class.getSimpleName()))
        .getCache(SpringCache2kCacheTests.class.getSimpleName());
  }

  @Test
  public void testNoLoadingCache() {
    assertThat(getCache().isLoaderPresent()).isFalse();
  }

  /**
   * Missing from the generic tests
   */
  @Test
  public void testEvict() {
    SpringCache2kCache cache = getCache();
    String key = createRandomKey();
    Object value = "george";
    cache.put(key, value);
    assertThat(cache.get(key).get()).isEqualTo(value);
    cache.evict(key);
    assertThat(cache.get(key)).isNull();
  }

  /**
   * Missing from the generic tests
   */
  @Test
  public void testTypeCheck() {
    SpringCache2kCache cache = getCache();
    String key = AbstractCacheTests.createRandomKey();
    Object value = "george";
    cache.put(key, value);
    assertThatCode(() -> cache.get(key, Integer.class))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void testLoadingCache() {
    String cacheName = ExtraSpringCache2kCacheTest.class.getSimpleName() + "-withLoader";
    SpringCache2kCache cacheWithLoader = constructCache(cacheName, b ->
        b.loader(key -> "123")
      );
    assertThat(cacheWithLoader.isLoaderPresent()).isTrue();
    cacheWithLoader.getNativeCache().close();
  }

  SpringCache2kCache constructCache(String cacheName,
      Function<Cache2kBuilder<Object, Object>, Cache2kBuilder<Object, Object>> fun) {
    return new SpringCache2kCacheManager()
      .addCaches(b ->
        fun.apply(b.keyType(Object.class).valueType(Object.class).name(cacheName)))
      .getCache(cacheName);
  }

  @Test
  public void testLoadingCacheWithException() {
    String cacheName =
      ExtraSpringCache2kCacheTest.class.getSimpleName() + "-withLoaderException";
    SpringCache2kCache cacheWithLoader = constructCache(cacheName, b -> b
      .loader(key -> { throw new IOException("ouch"); })
    );
    assertThatCode(() ->
      cacheWithLoader.get("123", (Callable<Object>) null))
      .isInstanceOf(CacheLoaderException.class);
  }

  @Test
  public void testLoadingCacheAdvancedLoader() {
    String cacheName =
      ExtraSpringCache2kCacheTest.class.getSimpleName() + "-withAdvancedLoader";
    SpringCache2kCache cacheWithLoader = constructCache(cacheName, b -> b
      .loader((key, startTime, currentEntry) -> "123"));
    assertThat(cacheWithLoader.isLoaderPresent()).isTrue();
    Object v = cacheWithLoader.get("321", () -> {
      fail("this is never called");
      return null;
    });
    assertThat(v).isEqualTo("123");
    cacheWithLoader.getNativeCache().close();
  }

}
