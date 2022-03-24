package org.cache2k.testsuite;

/*-
 * #%L
 * cache2k testsuite on public API
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

import org.cache2k.CacheEntry;
import org.cache2k.io.CacheLoaderException;
import org.cache2k.io.LoadExceptionInfo;
import org.cache2k.io.ResiliencePolicy;
import org.cache2k.pinpoint.ExpectedException;
import org.cache2k.processor.EntryProcessingException;
import org.cache2k.processor.MutableCacheEntry;
import org.cache2k.testsuite.support.AbstractCacheTester;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Check behavior of default exception propagator.
 *
 * @author Jens Wilke
 */
public class DefaultExceptionPropagationTest<K, V> extends AbstractCacheTester<K, V> {

  static final String FUTURE_TIME_STRING = "2047-05-25T09:30:12.123";
  static final long FUTURE_TIME_MILLIS = LocalDateTime.parse(FUTURE_TIME_STRING)
    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

  @Test
  public void setAndCheck() {
    init(b -> b.setup(this::resilienceCacheExceptions));
    invoke(k0, entry -> entry.setException(new ExpectedException()));
    checkAccessVariants();
  }

  @Test
  public void loaderExceptionAndCheck() {
    init(b -> b
      .loader(key -> { throw new ExpectedException(); } )
      .setup(this::resilienceCacheExceptions));
    loadAll(Collections.singletonList(k0));
    checkAccessVariants();
    assertThatCode(() -> get(k0))
      .isInstanceOf(CacheLoaderException.class)
      .hasMessageContaining("expiry=ETERNAL");
  }

  @Test
  public void loaderAndRefreshExceptionAndCheck() {
    init(b -> b
      .expireAfterWrite(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
      .refreshAhead(true)
      .loader(key -> { throw new ExpectedException(); } )
      .setup(this::resilienceCacheExceptions));
    loadAll(Collections.singletonList(k0));
    checkAccessVariants();
    assertThatCode(() -> get(k0))
      .isInstanceOf(CacheLoaderException.class)
      .hasMessageContaining("expiry=");
  }

  @Test
  public void loaderExceptionUntilFutureTime() {
    init(b -> b
      .refreshAhead(true)
      .loader(key -> { throw new ExpectedException(); } )
      .resiliencePolicy(new ResiliencePolicy<K, V>() {
        @Override
        public long suppressExceptionUntil(K key, LoadExceptionInfo<K, V> loadExceptionInfo, CacheEntry<K, V> cachedEntry) {
          return 0;
        }
        @Override
        public long retryLoadAfter(K key, LoadExceptionInfo<K, V> loadExceptionInfo) {
          return FUTURE_TIME_MILLIS;
        }
      }));
    loadAll(Collections.singletonList(k0));
    checkAccessVariants();
    assertThatCode(() -> get(k0))
      .isInstanceOf(CacheLoaderException.class)
      .hasMessageContaining("expiry=" + FUTURE_TIME_STRING);
  }

  @Test
  public void loaderExceptionImmediateExpiry() {
    init(b -> b
      .loader(key -> { throw new ExpectedException(); } ));
    assertThatCode(() -> get(k0))
      .isInstanceOf(CacheLoaderException.class)
      .hasMessageNotContainingAny("expiry=");
  }

  /**
   * Check every access variant correctly throws the exception
   */
  @SuppressWarnings("ConstantConditions")
  private void checkAccessVariants() {
    assertThatCode(() -> getEntry(k0).getValue())
      .isInstanceOf(CacheLoaderException.class);
    assertThatCode(() -> peekEntry(k0).getValue())
      .isInstanceOf(CacheLoaderException.class);
    assertThatCode(() -> get(k0))
      .isInstanceOf(CacheLoaderException.class);
    assertThatCode(() -> invoke(k0, MutableCacheEntry::getValue))
      .isInstanceOf(EntryProcessingException.class)
      .getCause().isInstanceOf(CacheLoaderException.class);
    assertThatCode(() -> peek(k0))
      .isInstanceOf(CacheLoaderException.class);
    assertThatCode(() -> entries().iterator().next().getValue())
      .isInstanceOf(CacheLoaderException.class);
    assertThatCode(() -> peekAndRemove(k0))
      .isInstanceOf(CacheLoaderException.class);
  }

}
