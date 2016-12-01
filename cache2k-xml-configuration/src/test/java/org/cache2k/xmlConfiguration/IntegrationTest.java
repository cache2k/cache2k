package org.cache2k.xmlConfiguration;

/*
 * #%L
 * cache2k XML configuration
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
import org.cache2k.configuration.CustomizationSupplierByClassName;
import org.cache2k.core.Cache2kCoreProviderImpl;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

/**
 * Test that XML configuration elements get picked up when building cache2k
 *
 * @author Jens Wilke
 */
public class IntegrationTest {

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
    Cache2kConfiguration cfg = Cache2kBuilder.forUnknownTypes().name("withLoader").toConfiguration();
    Cache2kCoreProviderImpl.augmentConfiguration(CacheManager.getInstance(), cfg);
    return cfg;
  }

  /**
   * The name of the default manager is set by the configuration.
   */
  @Test
  public void defaultManagerName() {
    Cache c = Cache2kBuilder.forUnknownTypes().name("flowers").build();
    assertTrue("default manager", c.getCacheManager().isDefaultManager());
    assertEquals("myApp", c.getCacheManager().getName());
    c.close();
  }

  @Test
  public void defaultIsApplied() {
    assertEquals(1234, new Cache2kBuilder<String, String>(){}.toConfiguration().getEntryCapacity());
  }

  @Test
  public void defaultAndIndividualIsApplied() {
    Cache2kBuilder<String, String> b =
      new Cache2kBuilder<String, String>(){}.name("flowers");
    Cache2kConfiguration<String, String> cfg = b.toConfiguration();
    assertEquals(1234, cfg.getEntryCapacity());
    assertEquals(-1, cfg.getExpireAfterWrite());
    Cache<String, String> c = b.build();
    assertEquals(1234, cfg.getEntryCapacity());
    assertEquals(47000, cfg.getExpireAfterWrite());
    c.close();
  }

  @Test(expected = ConfigurationException.class)
  public void failIfConfigurationIsMissing() {
    new Cache2kBuilder<String, String>(){}.name("missingDummy").build();
  }

  @Test(expected = ConfigurationException.class)
  public void failIfNameIsMissing() {
    new Cache2kBuilder<String, String>(){}.build();
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
      assertThat(ex.getMessage(), containsString("Value '2d' rejected: eternal was set, refusing to reset to expire time span at"));
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
      assertThat(ex.toString(), containsString("section start expected"));
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
  }

  @Test (expected = ConfigurationException.class)
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
