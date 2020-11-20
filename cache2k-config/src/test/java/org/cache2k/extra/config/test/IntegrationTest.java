package org.cache2k.extra.config.test;

/*
 * #%L
 * cache2k config file support
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
import org.cache2k.config.Cache2kConfig;
import org.cache2k.config.CustomizationSupplierByClassName;
import org.cache2k.core.spi.CacheConfigProvider;
import org.cache2k.extra.config.provider.CacheConfigProviderImpl;
import org.cache2k.extra.config.generic.ConfigurationException;
import org.cache2k.testing.category.FastTests;
import org.hamcrest.CoreMatchers;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.Serializable;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

/**
 * Test that XML configuration elements get picked up when building cache2k
 *
 * @author Jens Wilke
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Category(FastTests.class)
public class IntegrationTest {

  static final CacheConfigProvider PROVIDER = new CacheConfigProviderImpl();

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
    Cache2kConfig cfg = cacheCfgWithLoader();
    assertEquals("x.y.z",
      ((CustomizationSupplierByClassName) cfg.getLoader()).getClassName());
  }

  @Test
  public void listenerByClassName() {
    Cache2kConfig cfg = cacheCfgWithLoader();
    assertEquals(2, cfg.getListeners().size());
    assertEquals("a.b.c",
     ((CustomizationSupplierByClassName) cfg.getListeners().iterator().next()).getClassName());
  }

  private Cache2kConfig cacheCfgWithLoader() {
    CacheManager mgr = CacheManager.getInstance("customizationExample");
    Cache2kConfig cfg = Cache2kBuilder.forUnknownTypes()
      .manager(mgr)
      .name("withLoader").config();
    assertNull("no loader yet, default configuration returned", cfg.getLoader());
    PROVIDER.augmentConfig(mgr, cfg);
    assertNotNull("loader defined", cfg.getLoader());
    return cfg;
  }


  @Test
  public void defaultIsApplied() {
    assertEquals(5,
      new Cache2kBuilder<String, String>() { }.config().getLoaderThreadCount());
  }

  @Test
  public void defaultAndIndividualIsApplied() {
    Cache2kBuilder<String, String> b =
      new Cache2kBuilder<String, String>() { }
      .name("IntegrationTest-defaultAndIndividualIsApplied");
    Cache2kConfig<String, String> cfg = b.config();
    assertEquals(-1, cfg.getEntryCapacity());
    assertEquals(5, cfg.getLoaderThreadCount());
    assertNull(cfg.getExpireAfterWrite());
    Cache<String, String> c = b.build();
    assertEquals(-1, cfg.getEntryCapacity());
    assertEquals(47000, cfg.getExpireAfterWrite().toMillis());
    c.close();
  }

  @Test(expected = IllegalArgumentException.class)
  public void failIfConfigurationIsMissing() {
    new Cache2kBuilder<String, String>() { }
    .manager(CacheManager.getInstance("empty"))
    .name("missingDummy").build();
  }

  @Test(expected = ConfigurationException.class)
  public void failIfNameIsMissing() {
    new Cache2kBuilder<String, String>() { }
      .manager(CacheManager.getInstance("empty"))
      .build();
  }

  /**
   * As soon as we use the manager for building the cache the configuration
   * is read. This configuration file disables {@code skipCheckOnStartup}
   * so it should fail immediately.
   */
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

  /**
   * As of version 2.0 we allow duplicate listeners to simplify the configuration
   */
  @Test @Ignore
  public void duplicateListener() {
    Cache2kBuilder<String, String> builder =
      new Cache2kBuilder<String, String>() { }
        .manager(CacheManager.getInstance("specialCases"))
        .name("duplicateListener");
    Cache<String, String> cache = builder.build();
    assertEquals(2, builder.config().getListeners().size());
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
  public void featureEnabled() {
    Cache c =
      new Cache2kBuilder<String, String>() { }
        .manager(CacheManager.getInstance("specialCases"))
        .name("withFeature")
        .build();
    assertTrue(InspectionFeature.wasEnabled(c));
  }

  @Test
  public void featureDisabled() {
    Cache c =
      new Cache2kBuilder<String, String>() { }
        .manager(CacheManager.getInstance("specialCases"))
        .name("withFeatureDisabled")
        .build();
    assertFalse(InspectionFeature.wasEnabled(c));
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
    Cache2kConfig<String, String> cfg =
      new Cache2kBuilder<String, String>() { }
      .manager(CacheManager.getInstance("onlyDefault"))
      .name("anyCache")
      .config();
    assertEquals(1234, cfg.getEntryCapacity());
    assertEquals("PT12M", cfg.getExpireAfterWrite().toString());
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

}
