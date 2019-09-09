package org.cache2k.impl.xmlConfiguration;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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
import org.cache2k.configuration.CustomizationSupplierByClassName;
import org.cache2k.core.Cache2kCoreProviderImpl;
import org.cache2k.impl.xmlConfiguration.generic.ConfigurationException;
import org.cache2k.schema.Constants;
import org.cache2k.testing.category.FastTests;
import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.Serializable;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

/**
 * Test that XML configuration elements get picked up when building cache2k
 *
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class IntegrationTest {

  @Test
  public void listNames() {
    CacheManager mgr = CacheManager.getInstance("customizationExample");
    assertThat(mgr.getConfiguredCacheNames(), hasItems("flowers", "withLoader", "withLoaderShort"));
  }

  @Test
  public void listNamesEmpty() {
    CacheManager mgr = CacheManager.getInstance("unknown");
    assertFalse("no names", mgr.getConfiguredCacheNames().iterator().hasNext());
  }

  @Test
  public void loaderByClassName() {
    Cache2kConfiguration cfg = cacheCfgWithLoader();
    assertEquals("x.y.z", ((CustomizationSupplierByClassName) cfg.getLoader()).getClassName());
  }

  @Test
  public void listenerByClassName() {
    Cache2kConfiguration cfg = cacheCfgWithLoader();
    assertEquals(2, cfg.getListeners().size());
    assertEquals("a.b.c", ((CustomizationSupplierByClassName) cfg.getListeners().iterator().next()).getClassName());
  }

  private Cache2kConfiguration cacheCfgWithLoader() {
    CacheManager mgr = CacheManager.getInstance("customizationExample");
    Cache2kConfiguration cfg = Cache2kBuilder.forUnknownTypes()
      .manager(mgr)
      .name("withLoader").toConfiguration();
    assertNull("no loader yet, default configuration returned", cfg.getLoader());
    Cache2kCoreProviderImpl.CACHE_CONFIGURATION_PROVIDER.augmentConfiguration(mgr, cfg);
    assertNotNull("loader defined", cfg.getLoader());
    return cfg;
  }

  /**
   * The name of the default manager is set by the configuration.
   * Not working, will be moved.
   */
  public void defaultManagerName() {
    Cache c = Cache2kBuilder.forUnknownTypes().name("flowers").build();
    assertTrue("default manager", c.getCacheManager().isDefaultManager());
    assertEquals("myApp", c.getCacheManager().getName());
    c.close();
  }

  @Test
  public void defaultIsApplied() {
    assertEquals(5, new Cache2kBuilder<String, String>(){}.toConfiguration().getLoaderThreadCount());
  }

  @Test
  public void defaultAndIndividualIsApplied() {
    Cache2kBuilder<String, String> b =
      new Cache2kBuilder<String, String>(){}.name("IntegrationTest-defaultAndIndividualIsApplied");
    Cache2kConfiguration<String, String> cfg = b.toConfiguration();
    assertEquals(-1, cfg.getEntryCapacity());
    assertEquals(5, cfg.getLoaderThreadCount());
    assertEquals(-1, cfg.getExpireAfterWrite());
    Cache<String, String> c = b.build();
    assertEquals(-1, cfg.getEntryCapacity());
    assertEquals(47000, cfg.getExpireAfterWrite());
    c.close();
  }

  @Test(expected = IllegalArgumentException.class)
  public void failIfConfigurationIsMissing() {
    new Cache2kBuilder<String, String>(){}
    .manager(CacheManager.getInstance("empty"))
    .name("missingDummy").build();
  }

  @Test(expected = ConfigurationException.class)
  public void failIfNameIsMissing() {
    new Cache2kBuilder<String, String>(){}
      .manager(CacheManager.getInstance("empty"))
      .build();
  }

  @Test
  public void unknownPropertyYieldsExceptionOnStartup() {
    try {
      new Cache2kBuilder<String, String>() { }
        .manager(CacheManager.getInstance("unknownProperty"))
        .entryCapacity(1234);
      fail("expect exception");
    } catch (Exception ex) {
      assertTrue(ex.toString().contains("Unknown property"));
    }
  }

  @Test
  public void unknownPropertyYieldsExceptionOnBuild() {
    try {
      new Cache2kBuilder<String, String>() { }
        .manager(CacheManager.getInstance("specialCases"))
        .name("unknownProperty")
        .build();
      fail("expect exception");
    } catch (Exception ex) {
      assertThat(ex.toString(), containsString("Unknown property"));
    }
  }

  @Test
  public void sectionTypeNotFound() {
    try {
      new Cache2kBuilder<String, String>() { }
        .manager(CacheManager.getInstance("specialCases"))
        .name("sectionTypeNotFound")
        .build();
      fail("expect exception");
    } catch (Exception ex) {
      assertThat(ex.toString(), containsString("class not found 'tld.some.class'"));
    }
  }

  @Test
  public void sectionTypeMissing() {
    try {
      new Cache2kBuilder<String, String>() { }
        .manager(CacheManager.getInstance("specialCases"))
        .name("sectionTypeMissing")
        .build();
      fail("expect exception");
    } catch (Exception ex) {
      assertThat(ex.toString(), containsString("type missing"));
    }
  }

  @Test
  public void templateNotFound() {
    try {
      new Cache2kBuilder<String, String>() { }
        .manager(CacheManager.getInstance("specialCases"))
        .name("templateNotFound")
        .build();
      fail("expect exception");
    } catch (Exception ex) {
      assertThat(ex.toString(), containsString("Template not found 'notThere'"));
    }
  }

  @Test
  public void ignoreAnonymousCache() {
    Cache c =
    new Cache2kBuilder<String, String>() { }
      .manager(CacheManager.getInstance("specialCases"))
      .build();
    c.close();
  }

  @Test
  public void ignoreMissingCacheConfiguration() {
    Cache c =
      new Cache2kBuilder<String, String>() { }
        .manager(CacheManager.getInstance("specialCases"))
        .name("missingConfiguration")
        .build();
    c.close();
  }

  @Test(expected = ConfigurationException.class)
  public void illegalBoolean() {
    Cache c =
      new Cache2kBuilder<String, String>() { }
        .manager(CacheManager.getInstance("specialCases"))
        .name("illegalBoolean")
        .build();
    c.close();
  }

  @Test(expected = ConfigurationException.class)
  public void duplicateListener() {
    Cache c =
      new Cache2kBuilder<String, String>() { }
        .manager(CacheManager.getInstance("specialCases"))
        .name("duplicateListener")
        .build();
    c.close();
  }

  @Test(expected = ConfigurationException.class)
  public void typeMismatch() {
    Cache c =
      new Cache2kBuilder<String, String>() { }
        .manager(CacheManager.getInstance("specialCases"))
        .name("typeMismatch")
        .build();
    c.close();
  }

  @Test
  public void eternal_configExpire() {
    try {
      Cache c =
        new Cache2kBuilder<String, String>() { }
          .manager(CacheManager.getInstance("specialCases"))
          .name("withExpiry")
          .eternal(true)
          .build();
      c.close();
      fail("expect exception");
    } catch (ConfigurationException ex) {
      assertThat(ex.getMessage(), containsString("Value '2d' rejected: eternal enabled explicitly, refusing to enable expiry"));
    }
  }

  @Test
  public void notSerializableSection() {
    try {
      new Cache2kBuilder<String, String>() { }
        .manager(CacheManager.getInstance("notSerializable"))
        .name("notSerializable")
        .build();
      fail("expect exception");
    } catch (Exception ex) {
      assertThat(ex.toString(), containsString("Copying default cache configuration"));
      assertThat(ex.getCause(), CoreMatchers.<Throwable>instanceOf(Serializable.class));
    }
  }

  @Test
  public void parseError() {
    try {
      new Cache2kBuilder<String, String>() { }
        .manager(CacheManager.getInstance("parseError"))
        .entryCapacity(1234);
      fail("expect exception");
    } catch (Exception ex) {
      assertThat(ex.toString(), containsString("type missing or unknown"));
    }
  }

  @Test(expected = ConfigurationException.class)
  public void parseErrorWrongXml() {
      new Cache2kBuilder<String, String>() { }
        .manager(CacheManager.getInstance("parseErrorWrongXml"))
        .entryCapacity(1234);
  }

  @Test
  public void noManagerConfiguration() {
    new Cache2kBuilder<String, String>() { }
      .manager(CacheManager.getInstance("noManager"))
      .entryCapacity(1234);
  }

  @Test
  public void noManagerConfigurationAndBuild() {
    Cache c = new Cache2kBuilder<String, String>() { }
      .manager(CacheManager.getInstance("noManager"))
      .entryCapacity(1234)
      .build();
    c.close();
  }

  @Test
  public void onlyDefault() {
    Cache c = new Cache2kBuilder<String, String>() { }
      .manager(CacheManager.getInstance("onlyDefault"))
      .name("anyCache")
      .build();
    c.close();
    Cache2kConfiguration<String, String> cfg =
      new Cache2kBuilder<String, String>() { }
      .manager(CacheManager.getInstance("onlyDefault"))
      .name("anyCache")
      .toConfiguration();
    assertEquals(1234, cfg.getEntryCapacity());
    assertTrue(cfg.isExternalConfigurationPresent());
  }

  @Test (expected = IllegalArgumentException.class)
  public void empty() {
    Cache c = new Cache2kBuilder<String, String>() { }
      .manager(CacheManager.getInstance("empty"))
      .name("anyCache")
      .build();
    c.close();
  }

  @Test
  public void notPresent() {
    Cache c = new Cache2kBuilder<String, String>() { }
      .manager(CacheManager.getInstance("notPresent"))
      .name("anyCache")
      .build();
    c.close();
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
    Schema schema = schemaFactory.newSchema(getClass().getResource(
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
    assertEquals(123000, cfg.getMaxRetryInterval());
    c.close();
  }

}
