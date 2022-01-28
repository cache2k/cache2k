package org.cache2k.jcache.provider;

/*-
 * #%L
 * cache2k JCache provider
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
import org.cache2k.io.LoadExceptionInfo;
import org.cache2k.io.ResiliencePolicy;
import org.cache2k.jcache.ExtendedMutableConfiguration;
import org.cache2k.jcache.JCacheConfig;
import org.cache2k.pinpoint.ExpectedException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListener;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;

import static javax.cache.Caching.getCachingProvider;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * @author Jens Wilke
 */
@SuppressWarnings("unchecked")
public class ListenerTest {

  static final MutableCacheEntryListenerConfiguration<Object, Object> SYNC_LISTENER =
    new MutableCacheEntryListenerConfiguration<>(null, null, true, true);
  static final String CACHE_NAME = ListenerTest.class.getSimpleName();
  Cache<Object, Object> cache;

  @Test
  public void missingListenerFactory() {
    CacheManager mgr = getCachingProvider().getCacheManager();
    cache = mgr.createCache(CACHE_NAME, new MutableConfiguration<>());
    assertThatCode(() -> cache.registerCacheEntryListener(SYNC_LISTENER))
      .isInstanceOf(IllegalArgumentException.class);
    cache.close();
  }

  @Test
  public void eventsWithExceptions() {
    CacheManager mgr = getCachingProvider().getCacheManager();
    cache = mgr.createCache(CACHE_NAME, ExtendedMutableConfiguration.of(
      Cache2kBuilder.forUnknownTypes()
        .with(JCacheConfig.class, b -> b
          .supportOnlineListenerAttachment(true)
        )
        .resiliencePolicy(new ResiliencePolicy<Object, Object>() {
          @Override
          public long suppressExceptionUntil(Object key, LoadExceptionInfo<Object, Object> loadExceptionInfo, CacheEntry<Object, Object> cachedEntry) {
            return 0;
          }

          @Override
          public long retryLoadAfter(Object key, LoadExceptionInfo<Object, Object> loadExceptionInfo) {
            return Long.MAX_VALUE;
          }
        })
    ));
    ListenerCalls calls = Mockito.mock(ListenerCalls.class);
    register((CacheEntryCreatedListener<Object, Object>) cacheEntryEvents -> calls.created());
    register((CacheEntryUpdatedListener<Object, Object>) cacheEntryEvents -> calls.updated());
    register((CacheEntryRemovedListener<Object, Object>) cacheEntryEvents -> calls.removed());
    register((CacheEntryExpiredListener<Object, Object>) cacheEntryEvents -> calls.expired());
    cache.put(1, 1); Mockito.verify(calls).created();
    cache.put(1, 1); Mockito.verify(calls).updated();
    cache.remove(1); Mockito.verify(calls).removed();

    cache.unwrap(org.cache2k.Cache.class).invoke(1, entry -> entry.setException(new ExpectedException()));
    Mockito.verifyNoMoreInteractions(calls);

    Mockito.reset(calls);
    cache.put(1, 1); Mockito.verify(calls).created();
    cache.unwrap(org.cache2k.Cache.class).invoke(1, entry -> entry.setException(new ExpectedException()));
    Mockito.verify(calls).removed();

    Mockito.reset(calls);
    cache.put(1, 1); Mockito.verify(calls).created();
    cache.unwrap(org.cache2k.Cache.class).expireAt(1, 0);
    Mockito.verify(calls).expired();

    Mockito.reset(calls);
    cache.put(1, 1); Mockito.verify(calls).created();
    cache.unwrap(org.cache2k.Cache.class).invoke(1, entry -> entry.setException(new ExpectedException()));
    cache.unwrap(org.cache2k.Cache.class).expireAt(1, 0);
    Mockito.verify(calls).removed();

    cache.close();
  }

  /**
   * Register synchronous listener
   */
  private void register(CacheEntryListener<Object, Object> listener) {
    cache.registerCacheEntryListener(
      new MutableCacheEntryListenerConfiguration<>(SYNC_LISTENER)
        .setCacheEntryListenerFactory(new FactoryBuilder.SingletonFactory<>(listener)));
  }

  interface ListenerCalls {
    void created();
    void updated();
    void removed();
    void expired();
  }

}
