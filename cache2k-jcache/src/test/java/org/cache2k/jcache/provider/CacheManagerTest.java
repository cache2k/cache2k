package org.cache2k.jcache.provider;

/*
 * #%L
 * cache2k JCache provider
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.core.WiredCache;
import org.cache2k.jcache.ExtendedMutableConfiguration;
import org.cache2k.jcache.JCacheConfiguration;
import org.cache2k.jcache.provider.generic.storeByValueSimulation.CopyCacheProxy;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

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
import java.util.concurrent.TimeUnit;

/**
 * @author Jens Wilke; created: 2015-03-29
 */
public class CacheManagerTest {

  @Test
  public void testSameProvider() {
    CachingProvider p1 = Caching.getCachingProvider();
    CachingProvider p2 = Caching.getCachingProvider();
    assertTrue(p1 == p2);
  }

  /**
   * Test to ensure provider coordinates keep constant.
   */
  @Test
  public void testGetExplicitProvider() {
    CachingProvider cachingProvider =
      Caching.getCachingProvider(
        "org.cache2k.jcache.provider.JCacheProvider");
    assertTrue(cachingProvider == Caching.getCachingProvider());
  }

  @Test
  public void testSameCacheManager() {
    CachingProvider p = Caching.getCachingProvider();
    CacheManager cm1 = p.getCacheManager();
    CacheManager cm2 = p.getCacheManager();
    assertTrue(cm1 == cm2);
  }

  @Test
  public void create_empty_config() {
    CachingProvider p = Caching.getCachingProvider();
    CacheManager cm = p.getCacheManager();
    MutableConfiguration<String, BigDecimal> mc = new ExtendedMutableConfiguration<String, BigDecimal>();
    mc.setTypes(String.class, BigDecimal.class);
    Cache<String, BigDecimal> c = cm.createCache("aCache", mc);
    assertEquals("aCache", c.getName());
    assertEquals(String.class, c.getConfiguration(Configuration.class).getKeyType());
    assertEquals(BigDecimal.class, c.getConfiguration(Configuration.class).getValueType());
    c.close();
  }

  @Test @Ignore("not yet")
  public void create_config_cache2k_types() {
    CachingProvider p = Caching.getCachingProvider();
    CacheManager cm = p.getCacheManager();
    ExtendedMutableConfiguration<String, BigDecimal> mc = new ExtendedMutableConfiguration<String, BigDecimal>();
    mc.setCache2kConfiguration(
      new Cache2kBuilder<String, BigDecimal>(){}
        .toConfiguration()
    );
    Cache<String, BigDecimal> c = cm.createCache("aCache", mc);
    assertEquals("aCache", c.getName());
    assertEquals(String.class, c.getConfiguration(Configuration.class).getKeyType());
    assertEquals(BigDecimal.class, c.getConfiguration(Configuration.class).getValueType());
    c.close();
  }

  @Test
  public void create_cache2k_config_nowrap() {
    CachingProvider p = Caching.getCachingProvider();
    CacheManager cm = p.getCacheManager();
    Cache<Long, Double> cache = cm.createCache("aCache", ExtendedMutableConfiguration.of(
      new Cache2kBuilder<Long, Double>(){}
        .entryCapacity(10000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
    ));
    assertFalse(cache instanceof CopyCacheProxy);
    assertNull(cache.unwrap(org.cache2k.Cache.class).requestInterface(WiredCache.class));
    cache.close();
  }

  @Test
  public void create_cache2k_config_wrap() {
    CachingProvider p = Caching.getCachingProvider();
    CacheManager cm = p.getCacheManager();
    Cache<Long, Double> cache = cm.createCache("aCache", ExtendedMutableConfiguration.of(
      new Cache2kBuilder<Long, Double>(){}
        .entryCapacity(10000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .with(new JCacheConfiguration.Builder()
          .copyAlwaysIfRequested(true)
        )
    ));
    assertTrue(cache instanceof CopyCacheProxy);
    cache.close();
  }

  @Test(expected = IllegalArgumentException.class)
  public void create_cache2k_config_key_type_mismatch() {
    CachingProvider p = Caching.getCachingProvider();
    CacheManager cm = p.getCacheManager();
    MutableConfiguration cfg = ExtendedMutableConfiguration.of(new Cache2kBuilder<Long, Double>(){});
    Cache<Integer, Double> cache = cm.createCache("aCache",  cfg.setTypes(Integer.class, Double.class));
    cache.close();
  }

  @Test(expected = IllegalArgumentException.class)
  public void create_cache2k_config_value_type_mismatch() {
    CachingProvider p = Caching.getCachingProvider();
    CacheManager cm = p.getCacheManager();
    MutableConfiguration cfg = ExtendedMutableConfiguration.of(new Cache2kBuilder<Long, Double>(){});
    Cache<Integer, Double> cache = cm.createCache("aCache",  cfg.setTypes(Long.class, Float.class));
    cache.close();
  }

  @Test(expected=UnsupportedOperationException.class)
  public void no_online_listener_attachment_with_cache2k_defaults() {
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
  }

  @Test(expected = IllegalArgumentException.class)
  public void testIllegalURI1() throws Exception {
    CachingProvider p = Caching.getCachingProvider();
    CacheManager cm = p.getCacheManager(new URI("file://hello.xml"), null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testIllegalURI2() throws Exception {
    CachingProvider p = Caching.getCachingProvider();
    CacheManager cm = p.getCacheManager(new URI("/hello.xml"), null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testIllegalURI3() throws Exception {
    CachingProvider p = Caching.getCachingProvider();
    CacheManager cm = p.getCacheManager(new URI("hello.xml"), null);
  }

}
