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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheManager;
import org.cache2k.config.Cache2kConfig;
import org.cache2k.jcache.ExtendedMutableConfiguration;
import org.cache2k.jcache.JCacheConfig;
import org.cache2k.jcache.provider.generic.storeByValueSimulation.CopyCacheProxy;
import org.cache2k.schema.Constants;
import org.junit.jupiter.api.Test;

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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.singletonList;
import static javax.cache.Caching.getCachingProvider;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.cache2k.CacheManager.getInstance;

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
      new Cache2kBuilder<String, String>() {
      }
        .manager(getInstance(MANAGER_NAME))
        .name("withSection");
    Cache2kConfig<String, String> cfg = b.config();
    Cache<String, String> c = b.build();
    assertThat(new JCacheConfig().isCopyAlwaysIfRequested())
      .as("default is false")
      .isEqualTo(false);
    assertThat(cfg.getSections().getSection(JCacheConfig.class))
      .as("section present")
      .isNotNull();
    assertThat(cfg.getSections().getSection(JCacheConfig.class).isCopyAlwaysIfRequested())
      .as("config applied")
      .isEqualTo(true);
    c.close();
  }

  @Test
  public void sectionIsThereViaStandardElementName() {
    Cache2kBuilder<String, String> b =
      new Cache2kBuilder<String, String>() {
      }
        .manager(getInstance(MANAGER_NAME))
        .name("withJCacheSection");
    Cache2kConfig<String, String> cfg = b.config();
    Cache<String, String> c = b.build();
    assertThat(new JCacheConfig().isCopyAlwaysIfRequested())
      .as("default is false")
      .isEqualTo(false);
    assertThat(cfg.getSections().getSection(JCacheConfig.class))
      .as("section present")
      .isNotNull();
    assertThat(cfg.getSections().getSection(JCacheConfig.class).isCopyAlwaysIfRequested())
      .as("config applied")
      .isEqualTo(true);
    c.close();
  }

  @Test
  public void xmlConfigurationIsNotApplied() {
    CachingProvider p = getCachingProvider();
    javax.cache.CacheManager cm = p.getCacheManager();
    ExtendedMutableConfiguration<String, BigDecimal> mc = new ExtendedMutableConfiguration<String, BigDecimal>();
    mc.setTypes(String.class, BigDecimal.class);
    javax.cache.Cache<String, BigDecimal> c = cm.createCache("unknownCache", mc);
    assertThat(mc.getCache2kConfiguration().isEternal()).isFalse();
    c.close();
  }

  @Test
  public void xmlConfigurationIsApplied() throws Exception {
    CachingProvider p = getCachingProvider();
    javax.cache.CacheManager cm = p.getCacheManager(new URI(MANAGER_NAME), null);
    ExtendedMutableConfiguration<String, BigDecimal> mc = new ExtendedMutableConfiguration<String, BigDecimal>();
    mc.setTypes(String.class, BigDecimal.class);
    javax.cache.Cache<String, BigDecimal> c = cm.createCache("withExpiry", mc);
    assertThat(mc.getCache2kConfiguration().isEternal()).isFalse();
    assertThat(mc.getCache2kConfiguration().getExpireAfterWrite().toMillis()).isEqualTo(2000);
    c.close();
  }

  @Test
  public void configurationMissing() {
    assertThatCode(() -> {
      javax.cache.CacheManager manager =
        Caching.getCachingProvider().getCacheManager(new URI(MANAGER_NAME), null);
      manager.createCache("missing", new MutableConfiguration<Object,Object>());
    }).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void configurationPresent_defaults() throws Exception {
    javax.cache.CacheManager manager =
      getCachingProvider().getCacheManager(new URI(MANAGER_NAME), null);
    JCacheBuilder b = new JCacheBuilder("default", (JCacheManagerAdapter) manager);
    b.setConfiguration(new MutableConfiguration());
    assertThat(b.getExtraConfiguration().isCopyAlwaysIfRequested()).isEqualTo(false);
  }

  @Test
  public void configurationPresent_changed() throws Exception {
    javax.cache.CacheManager manager =
      getCachingProvider().getCacheManager(new URI(MANAGER_NAME), null);
    JCacheBuilder b = new JCacheBuilder("withJCacheSection", (JCacheManagerAdapter) manager);
    b.setConfiguration(new MutableConfiguration());
    assertThat(b.getExtraConfiguration().isCopyAlwaysIfRequested()).isEqualTo(true);
  }

  @Test
  public void getCache_creates() throws Exception {
    javax.cache.CacheManager _manager =
      Caching.getCachingProvider().getCacheManager(new URI(MANAGER_NAME), null);
  }

  @Test
  public void standardJCacheSemanticsIfNoExternalConfiguration() throws Exception {
    CachingProvider p = getCachingProvider();
    javax.cache.CacheManager cm = p.getCacheManager();
    javax.cache.Cache<String, BigDecimal> c =
      cm.createCache("test", new MutableConfiguration<String, BigDecimal>());
    assertThat(c instanceof CopyCacheProxy).isTrue();
  }

  @Test
  public void cache2kSemanticsIfEmptyConfigurationPresent() throws Exception {
    CachingProvider p = getCachingProvider();
    javax.cache.CacheManager cm = p.getCacheManager(new URI("empty"), null);
    javax.cache.Cache<String, BigDecimal> c =
      cm.createCache("test2", new MutableConfiguration<String, BigDecimal>());
    assertThat(c instanceof JCacheAdapter).isTrue();
  }

  @Test
  public void jcacheWithLoaderButNoReadThroughEnabled() throws Exception {
    javax.cache.CacheManager _manager =
      getCachingProvider().getCacheManager(new URI(MANAGER_NAME), null);
    javax.cache.Cache<Integer, String> _cache =
      _manager.getCache("withCache2kLoaderNoReadThrough");
    assertThat(_cache.get(123)).isNull();
    CompletionListenerFuture _complete = new CompletionListenerFuture();
    Set<Integer> _toLoad = new HashSet<Integer>();
    _toLoad.add(123);
    _cache.loadAll(_toLoad, true, _complete);
    _complete.get();
    assertThat(_cache.get(123)).isNotNull();
  }

  @Test
  public void jcacheWithLoaderReadThroughEnabled() throws Exception {
    javax.cache.CacheManager _manager =
      getCachingProvider().getCacheManager(new URI(MANAGER_NAME), null);
    javax.cache.Cache<Integer, String> _cache =
      _manager.getCache("withCache2kLoaderWithReadThrough");
    assertThat(_cache.get(123)).isNotNull();
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
      Constants.SCHEMA_LOCATION));
    schema.newValidator().validate(cfg);
  }

  @Test
  public void validateVariableExpansion() {
    Cache2kBuilder b = new Cache2kBuilder<String, String>() { }
      .manager(getInstance("all"))
      .name("jcache1");
    Cache2kConfig cfg = b.config();
    Cache c = b.build();
    assertThat(cfg.getEntryCapacity()).isEqualTo(1153);
    assertThat(cfg.getExpireAfterWrite().toMillis()).isEqualTo(123000);
    c.close();
  }

  @Test
  public void checkSectionSetter() {
    Cache2kConfig cfg = new Cache2kConfig();
    cfg.setSections(singletonList(new JCacheConfig()));
    assertThat(cfg.getSections().size()).isEqualTo(1);
    assertThat(cfg.getSections().toString()).contains("SectionContainer");
    assertThat(cfg.getSections().iterator().hasNext()).isTrue();
  }

  @Test
  public void checkNoDuplicateSection() {
    assertThatCode(() -> {
      Cache2kConfig cfg = new Cache2kConfig();
      cfg.setSections(Collections.singletonList(new JCacheConfig()));
      cfg.setSections(Collections.singletonList(new JCacheConfig()));
    }).isInstanceOf(IllegalArgumentException.class);
  }

}
