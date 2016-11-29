package org.cache2k.jcache.provider;

/*
 * #%L
 * cache2k JCache provider
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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheManager;
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.jcache.ExtendedMutableConfiguration;
import org.cache2k.jcache.JCacheConfiguration;
import org.junit.Test;

import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import java.math.BigDecimal;
import java.net.URI;

import static org.junit.Assert.*;

/**
 * Integration test for XML configuration.
 *
 * @author Jens Wilke
 */
public class XmlConfigurationTest {

  @Test
  public void sectionIsThere() {
    Cache2kBuilder<String, String> b =
      new Cache2kBuilder<String, String>(){}
        .manager(CacheManager.getInstance("xmlConfiguration"))
        .name("withSection");
    Cache2kConfiguration<String, String> cfg = b.toConfiguration();
    Cache<String, String> c = b.build();
    assertEquals("default is false", false, new JCacheConfiguration().isAlwaysFlushJmxStatistics());
    assertNotNull("section present", cfg.getSections().getSection(JCacheConfiguration.class));
    assertEquals("config applied", true, cfg.getSections().getSection(JCacheConfiguration.class).isAlwaysFlushJmxStatistics());
    c.close();
  }

  @Test
  public void sectionIsThereViaStandardElementName() {
    Cache2kBuilder<String, String> b =
      new Cache2kBuilder<String, String>(){}
        .manager(CacheManager.getInstance("xmlConfiguration"))
        .name("withJCacheSection");
    Cache2kConfiguration<String, String> cfg = b.toConfiguration();
    Cache<String, String> c = b.build();
    assertEquals("default is false", false, new JCacheConfiguration().isAlwaysFlushJmxStatistics());
    assertNotNull("section present", cfg.getSections().getSection(JCacheConfiguration.class));
    assertEquals("config applied", true, cfg.getSections().getSection(JCacheConfiguration.class).isAlwaysFlushJmxStatistics());
    c.close();
  }

  @Test
  public void xmlConfigurationIsNotApplied() {
    CachingProvider p = Caching.getCachingProvider();
    javax.cache.CacheManager cm = p.getCacheManager();
    ExtendedMutableConfiguration<String, BigDecimal> mc = new ExtendedMutableConfiguration<String, BigDecimal>();
    mc.setTypes(String.class, BigDecimal.class);
    javax.cache.Cache<String, BigDecimal> c = cm.createCache("unknownCache", mc);
    assertTrue(mc.getCache2kConfiguration().isEternal());
    c.close();
  }

  @Test
  public void xmlConfigurationIsApplied() throws Exception {
    CachingProvider p = Caching.getCachingProvider();
    javax.cache.CacheManager cm = p.getCacheManager(new URI("xmlConfiguration"), null);
    ExtendedMutableConfiguration<String, BigDecimal> mc = new ExtendedMutableConfiguration<String, BigDecimal>();
    mc.setTypes(String.class, BigDecimal.class);
    javax.cache.Cache<String, BigDecimal> c = cm.createCache("withExpiry", mc);
    assertFalse(mc.getCache2kConfiguration().isEternal());
    assertEquals(2000, mc.getCache2kConfiguration().getExpireAfterWrite());
    c.close();
  }

}
