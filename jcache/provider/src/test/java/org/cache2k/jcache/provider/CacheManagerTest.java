package org.cache2k.jcache.provider;

/*
 * #%L
 * cache2k JSR107 support
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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
import org.cache2k.jcache.MutableConfigurationForCache2k;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;
import java.math.BigDecimal;

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
    MutableConfiguration<String, BigDecimal> mc = new MutableConfigurationForCache2k<String, BigDecimal>();
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
    MutableConfigurationForCache2k<String, BigDecimal> mc = new MutableConfigurationForCache2k<String, BigDecimal>();
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

}
