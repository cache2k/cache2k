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
import org.cache2k.core.WiredCache;
import org.cache2k.jcache.ExtendedMutableConfiguration;
import org.cache2k.jcache.JCacheConfig;
import org.cache2k.jcache.provider.generic.storeByValueSimulation.CopyCacheProxy;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static javax.cache.Caching.getCachingProvider;
import static org.assertj.core.api.Assertions.*;
import static org.cache2k.jcache.ExtendedMutableConfiguration.of;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.Factory;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryListener;
import javax.cache.spi.CachingProvider;
import java.math.BigDecimal;
import java.net.URI;

/**
 * @author Jens Wilke; created: 2015-03-29
 */
public class CacheManagerTest {

  @Test
  public void testSameProvider() {
    CachingProvider p1 = getCachingProvider();
    CachingProvider p2 = getCachingProvider();
    assertThat(p1 == p2).isTrue();
  }

  /**
   * Test to ensure provider coordinates keep constant.
   */
  @Test
  public void testGetExplicitProvider() {
    CachingProvider cachingProvider =
      getCachingProvider(
        "org.cache2k.jcache.provider.JCacheProvider");
    assertThat(cachingProvider == getCachingProvider()).isTrue();
  }

  @Test
  public void testSameCacheManager() {
    CachingProvider p = getCachingProvider();
    CacheManager cm1 = p.getCacheManager();
    CacheManager cm2 = p.getCacheManager();
    assertThat(cm1 == cm2).isTrue();
  }

  @Test
  public void create_empty_config() {
    CachingProvider p = getCachingProvider();
    CacheManager cm = p.getCacheManager();
    MutableConfiguration<String, BigDecimal> mc = new ExtendedMutableConfiguration<String, BigDecimal>();
    mc.setTypes(String.class, BigDecimal.class);
    Cache<String, BigDecimal> c = cm.createCache("aCache", mc);
    assertThat(c.getName()).isEqualTo("aCache");
    assertThat(c.getConfiguration(Configuration.class).getKeyType()).isEqualTo(String.class);
    assertThat(c.getConfiguration(Configuration.class).getValueType()).isEqualTo(BigDecimal.class);
    c.close();
  }

  @Test
  public void create_config_cache2k_types() {
    CachingProvider p = getCachingProvider();
    CacheManager cm = p.getCacheManager();
    ExtendedMutableConfiguration<String, BigDecimal> mc = new ExtendedMutableConfiguration<String, BigDecimal>();
    mc.setCache2kConfiguration(
      new Cache2kBuilder<String, BigDecimal>(){}
        .config()
    );
    Cache<String, BigDecimal> c = cm.createCache("aCache", mc);
    assertThat(c.getName()).isEqualTo("aCache");
    assertThat(c.getConfiguration(Configuration.class).getKeyType()).isEqualTo(String.class);
    assertThat(c.getConfiguration(Configuration.class).getValueType()).isEqualTo(BigDecimal.class);
    c.close();
  }

  @Test
  public void create_cache2k_config_nowrap() {
    CachingProvider p = getCachingProvider();
    CacheManager cm = p.getCacheManager();
    Cache<Long, Double> cache = cm.createCache("aCache", of(
      new Cache2kBuilder<Long, Double>(){}
        .entryCapacity(10000)
        .expireAfterWrite(5, MINUTES)
    ));
    assertThat(cache instanceof CopyCacheProxy).isFalse();
    org.cache2k.Cache c2kcache = cache.unwrap(org.cache2k.Cache.class);
    try {
      c2kcache.requestInterface(WiredCache.class);
      fail("exception");
    } catch (UnsupportedOperationException expected) { }
    cache.close();
  }

  @Test
  public void create_cache2k_config_wrap() {
    CachingProvider p = getCachingProvider();
    CacheManager cm = p.getCacheManager();
    Cache<Long, Double> cache = cm.createCache("aCache", of(
      new Cache2kBuilder<Long, Double>() { }
        .entryCapacity(10000)
        .expireAfterWrite(5, MINUTES)
        .with(JCacheConfig.class, b -> b
          .copyAlwaysIfRequested(true)
        )
    ));
    assertThat(cache instanceof CopyCacheProxy).isTrue();
    cache.close();
  }

  @Test
  public void create_cache2k_config_key_type_mismatch() {
    assertThatCode(() -> {
      CachingProvider p = Caching.getCachingProvider();
      CacheManager cm = p.getCacheManager();
      MutableConfiguration cfg = ExtendedMutableConfiguration.of(new Cache2kBuilder<Long, Double>(){});
      Cache<Integer, Double> cache = cm.createCache("aCache",  cfg.setTypes(Integer.class, Double.class));
      cache.close();
    }).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void create_cache2k_config_value_type_mismatch() {
    assertThatCode(() -> {
      CachingProvider p = Caching.getCachingProvider();
      CacheManager cm = p.getCacheManager();
      MutableConfiguration cfg = ExtendedMutableConfiguration.of(new Cache2kBuilder<Long, Double>(){});
      Cache<Integer, Double> cache = cm.createCache("aCache",  cfg.setTypes(Long.class, Float.class));
      cache.close();
    }).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void no_online_listener_attachment_with_cache2k_defaults() {
    assertThatCode(() -> {
      CachingProvider p = Caching.getCachingProvider();
      CacheManager cm = p.getCacheManager();
      MutableConfiguration cfg = ExtendedMutableConfiguration.of(new Cache2kBuilder<Long, Double>(){});
      Cache cache = cm.createCache("mute",  cfg);
      cache.registerCacheEntryListener(new CacheEntryListenerConfiguration() {
        @Override
        public Factory<CacheEntryListener> getCacheEntryListenerFactory() {
          fail("not expected to be called");
          return null;
        }

        @Override
        public boolean isOldValueRequired() {
          return false;
        }

        @Override
        public Factory<CacheEntryEventFilter> getCacheEntryEventFilterFactory() {
          return null;
        }

        @Override
        public boolean isSynchronous() {
          return false;
        }
      });
      cache.close();
    }).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void testIllegalURI1() {
    assertThatCode(() -> {
      CachingProvider p = Caching.getCachingProvider();
      CacheManager cm = p.getCacheManager(new URI("file://hello.xml"), null);
    }).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void testIllegalURI2() {
    assertThatCode(() -> {
      CachingProvider p = Caching.getCachingProvider();
      CacheManager cm = p.getCacheManager(new URI("/hello.xml"), null);
    }).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void testIllegalURI3() {
    assertThatCode(() -> {
      CachingProvider p = Caching.getCachingProvider();
      CacheManager cm = p.getCacheManager(new URI("hello.xml"), null);
    }).isInstanceOf(IllegalArgumentException.class);
  }

}
