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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheManager;
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.configuration.ConfigurationSection;
import org.cache2k.jcache.ExtendedMutableConfiguration;
import org.cache2k.jcache.JCacheConfiguration;
import org.cache2k.jcache.provider.generic.storeByValueSimulation.CopyCacheProxy;
import org.cache2k.schema.Constants;
import org.hamcrest.CoreMatchers;
import org.junit.Ignore;
import org.junit.Test;

import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.integration.CompletionListenerFuture;
import javax.cache.spi.CachingProvider;
import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Integration test for XML configuration.
 *
 * @author Jens Wilke
 */
public class XmlConfigurationTest {

  private static final String MANAGER_NAME = "jcacheExample";

  @Test
  public void sectionIsThere() {
    Cache2kBuilder<String, String> b =
      new Cache2kBuilder<String, String>(){}
        .manager(CacheManager.getInstance(MANAGER_NAME))
        .name("withSection");
    Cache2kConfiguration<String, String> cfg = b.toConfiguration();
    Cache<String, String> c = b.build();
    assertEquals("default is false", false, new JCacheConfiguration().isCopyAlwaysIfRequested());
    assertNotNull("section present", cfg.getSections().getSection(JCacheConfiguration.class));
    assertEquals("config applied", true, cfg.getSections().getSection(JCacheConfiguration.class).isCopyAlwaysIfRequested());
    c.close();
  }

  @Test
  public void sectionIsThereViaStandardElementName() {
    Cache2kBuilder<String, String> b =
      new Cache2kBuilder<String, String>(){}
        .manager(CacheManager.getInstance(MANAGER_NAME))
        .name("withJCacheSection");
    Cache2kConfiguration<String, String> cfg = b.toConfiguration();
    Cache<String, String> c = b.build();
    assertEquals("default is false", false, new JCacheConfiguration().isCopyAlwaysIfRequested());
    assertNotNull("section present", cfg.getSections().getSection(JCacheConfiguration.class));
    assertEquals("config applied", true, cfg.getSections().getSection(JCacheConfiguration.class).isCopyAlwaysIfRequested());
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
    javax.cache.CacheManager cm = p.getCacheManager(new URI(MANAGER_NAME), null);
    ExtendedMutableConfiguration<String, BigDecimal> mc = new ExtendedMutableConfiguration<String, BigDecimal>();
    mc.setTypes(String.class, BigDecimal.class);
    javax.cache.Cache<String, BigDecimal> c = cm.createCache("withExpiry", mc);
    assertFalse(mc.getCache2kConfiguration().isEternal());
    assertEquals(2000, mc.getCache2kConfiguration().getExpireAfterWrite().toMillis());
    c.close();
  }

  @Test(expected = IllegalArgumentException.class)
  public void configurationMissing() throws Exception {
    javax.cache.CacheManager _manager =
      Caching.getCachingProvider().getCacheManager(new URI(MANAGER_NAME), null);
    _manager.createCache("missing", new MutableConfiguration<Object,Object>());
  }

  @Test
  public void configurationPresent_defaults() throws Exception {
    javax.cache.CacheManager _manager =
      Caching.getCachingProvider().getCacheManager(new URI(MANAGER_NAME), null);
    JCacheBuilder b = new JCacheBuilder("default", (JCacheManagerAdapter) _manager);
    b.setConfiguration(new MutableConfiguration());
    assertEquals(false, b.getExtraConfiguration().isCopyAlwaysIfRequested());
  }

  @Test
  public void configurationPresent_changed() throws Exception {
    javax.cache.CacheManager _manager =
      Caching.getCachingProvider().getCacheManager(new URI(MANAGER_NAME), null);
    JCacheBuilder b = new JCacheBuilder("withJCacheSection", (JCacheManagerAdapter) _manager);
    b.setConfiguration(new MutableConfiguration());
    assertEquals(true, b.getExtraConfiguration().isCopyAlwaysIfRequested());
  }

  @Test
  public void getCache_creates() throws Exception {
    javax.cache.CacheManager _manager =
      Caching.getCachingProvider().getCacheManager(new URI(MANAGER_NAME), null);
  }

  @Test
  public void standardJCacheSemanticsIfNoExternalConfiguration() throws Exception {
    CachingProvider p = Caching.getCachingProvider();
    javax.cache.CacheManager cm = p.getCacheManager();
    javax.cache.Cache<String, BigDecimal> c =
      cm.createCache("test", new MutableConfiguration<String, BigDecimal>());
    assertTrue(c instanceof CopyCacheProxy);
  }

  @Test
  public void cache2kSemanticsIfEmptyConfigurationPresent() throws Exception {
    CachingProvider p = Caching.getCachingProvider();
    javax.cache.CacheManager cm = p.getCacheManager(new URI("empty"), null);
    javax.cache.Cache<String, BigDecimal> c =
      cm.createCache("test2", new MutableConfiguration<String, BigDecimal>());
    assertTrue(c instanceof JCacheAdapter);
  }

  @Test
  public void jcacheWithLoaderButNoReadThroughEnabled() throws Exception {
    javax.cache.CacheManager _manager =
      Caching.getCachingProvider().getCacheManager(new URI(MANAGER_NAME), null);
    javax.cache.Cache<Integer, String> _cache =
      _manager.getCache("withCache2kLoaderNoReadThrough");
    assertNull(_cache.get(123));
    CompletionListenerFuture _complete = new CompletionListenerFuture();
    Set<Integer> _toLoad = new HashSet<Integer>();
    _toLoad.add(123);
    _cache.loadAll(_toLoad, true, _complete);
    _complete.get();
    assertNotNull(_cache.get(123));
  }

  @Test
  public void jcacheWithLoaderReadThroughEnabled() throws Exception {
    javax.cache.CacheManager _manager =
      Caching.getCachingProvider().getCacheManager(new URI(MANAGER_NAME), null);
    javax.cache.Cache<Integer, String> _cache =
      _manager.getCache("withCache2kLoaderWithReadThrough");
    assertNotNull(_cache.get(123));
  }

  @Test
  public void readAllXml() {
    Cache c = new Cache2kBuilder<String, String>() { }
      .manager(CacheManager.getInstance("all"))
      .name("hello")
      .build();
    c.close();
  }

  @Test
  public void validateCoreXsd() throws Exception {
    Source cfg =
      new StreamSource(
        getClass().getResourceAsStream("/cache2k-all.xml"));
    SchemaFactory schemaFactory =
      SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    Schema schema = schemaFactory.newSchema(Constants.class.getResource(
      Constants.CORE_SCHEMA_LOCATION));
    schema.newValidator().validate(cfg);
  }

  @Test
  public void validateVariableExpansion() {
    Cache2kBuilder b = new Cache2kBuilder<String, String>() { }
      .manager(CacheManager.getInstance("all"))
      .name("jcache1");
    Cache2kConfiguration cfg = b.toConfiguration();
    Cache c = b.build();
    assertEquals(1153, cfg.getEntryCapacity());
    assertEquals(123000, cfg.getExpireAfterWrite().toMillis());
    c.close();
  }

  @Test
  public void checkSectionSetter() {
    Cache2kConfiguration cfg = new Cache2kConfiguration();
    cfg.setSections(Collections.singletonList(new JCacheConfiguration()));
    assertEquals(1, cfg.getSections().size());
    assertThat(
      cfg.getSections().toString(),
      CoreMatchers.containsString("ConfigurationSectionContainer"));
    assertNull(cfg.getSections().getSection(DummySection.class));
    assertTrue(cfg.getSections().iterator().hasNext());
  }

  interface DummySection extends ConfigurationSection { }

  @Test(expected = IllegalArgumentException.class)
  public void checkNoDuplicateSection() {
    Cache2kConfiguration cfg = new Cache2kConfiguration();
    cfg.setSections(Collections.singletonList(new JCacheConfiguration()));
    cfg.setSections(Collections.singletonList(new JCacheConfiguration()));
  }

}
