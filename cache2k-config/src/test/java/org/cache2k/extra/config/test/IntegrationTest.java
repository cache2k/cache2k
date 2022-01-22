package org.cache2k.extra.config.test;

/*-
 * #%L
 * cache2k config file support
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
import org.cache2k.config.CustomizationSupplierByClassName;
import org.cache2k.core.spi.CacheConfigProvider;
import org.cache2k.extra.config.provider.CacheConfigProviderImpl;
import org.cache2k.extra.config.generic.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.assertj.core.api.Assertions.*;
import static org.cache2k.Cache2kBuilder.forUnknownTypes;
import static org.cache2k.CacheManager.getInstance;
import static org.cache2k.extra.config.test.InspectionFeature.wasEnabled;

/**
 * Test that XML configuration elements get picked up when building cache2k
 *
 * @author Jens Wilke
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class IntegrationTest {

  static final CacheConfigProvider PROVIDER = new CacheConfigProviderImpl();

  @Test
  public void listNames() {
    CacheManager mgr = CacheManager.getInstance("customizationExample");
    assertThat(mgr.getConfiguredCacheNames()).contains("flowers", "withLoader", "withLoaderShort");
  }

  @Test
  public void listNamesEmpty() {
    CacheManager mgr = getInstance("unknown");
    assertThat(mgr.getConfiguredCacheNames().iterator().hasNext())
      .as("no names")
      .isFalse();
  }

  @Test
  public void loaderByClassName() {
    Cache2kConfig cfg = cacheCfgWithLoader();
    assertThat(((CustomizationSupplierByClassName) cfg.getLoader()).getClassName()).isEqualTo("x.y.z");
  }

  @Test
  public void listenerByClassName() {
    Cache2kConfig cfg = cacheCfgWithLoader();
    assertThat(cfg.getListeners().size()).isEqualTo(2);
    assertThat(((CustomizationSupplierByClassName) cfg.getListeners().iterator().next()).getClassName()).isEqualTo("a.b.c");
  }

  private Cache2kConfig cacheCfgWithLoader() {
    CacheManager mgr = getInstance("customizationExample");
    Cache2kConfig cfg = forUnknownTypes()
      .manager(mgr)
      .name("withLoader").config();
    assertThat(cfg.getLoader())
      .as("no loader yet, default configuration returned")
      .isNull();
    PROVIDER.augmentConfig(mgr, cfg);
    assertThat(cfg.getLoader())
      .as("loader defined")
      .isNotNull();
    return cfg;
  }


  @Test
  public void defaultIsApplied() {
    assertThat(new Cache2kBuilder<String, String>() { }.config().getLoaderThreadCount()).isEqualTo(5);
  }

  @Test
  public void defaultAndIndividualIsApplied() {
    Cache2kBuilder<String, String> b =
      new Cache2kBuilder<String, String>() { }
      .name("IntegrationTest-defaultAndIndividualIsApplied");
    Cache2kConfig<String, String> cfg = b.config();
    assertThat(cfg.getEntryCapacity()).isEqualTo(-1);
    assertThat(cfg.getLoaderThreadCount()).isEqualTo(5);
    assertThat(cfg.getExpireAfterWrite()).isNull();
    Cache<String, String> c = b.build();
    assertThat(cfg.getEntryCapacity()).isEqualTo(-1);
    assertThat(cfg.getExpireAfterWrite().toMillis()).isEqualTo(47000);
    c.close();
  }

  @Test
  public void failIfConfigurationIsMissing() {
    assertThatCode(() -> {
      new Cache2kBuilder<String, String>() { }
        .manager(CacheManager.getInstance("empty"))
        .name("missingDummy").build();
      }
    ).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void failIfNameIsMissing() {
    assertThatCode(() -> {
        new Cache2kBuilder<String, String>() {
        }
          .manager(CacheManager.getInstance("empty"))
          .build();
      }).isInstanceOf(ConfigurationException.class);
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
        .manager(getInstance("unknownProperty"))
        .entryCapacity(1234);
      fail("exception expected");
    } catch (Exception ex) {
      assertThat(ex.toString().contains("Unknown property")).isTrue();
    }
  }

  @Test
  public void unknownPropertyYieldsExceptionOnBuild() {
    try {
      new Cache2kBuilder<String, String>() { }
        .manager(getInstance("specialCases"))
        .name("unknownProperty")
        .build();
      fail("exception expected");
    } catch (Exception ex) {
      assertThat(ex.toString()).contains("Unknown property");
    }
  }

  @Test
  public void sectionTypeNotFound() {
    try {
      new Cache2kBuilder<String, String>() { }
        .manager(getInstance("specialCases"))
        .name("sectionTypeNotFound")
        .build();
      fail("exception expected");
    } catch (Exception ex) {
      assertThat(ex.toString()).contains("class not found 'tld.some.class'");
    }
  }

  @Test
  public void sectionTypeMissing() {
    try {
      new Cache2kBuilder<String, String>() { }
        .manager(getInstance("specialCases"))
        .name("sectionTypeMissing")
        .build();
      fail("exception expected");
    } catch (Exception ex) {
      assertThat(ex.toString()).contains("type missing");
    }
  }

  @Test
  public void templateNotFound() {
    try {
      new Cache2kBuilder<String, String>() { }
        .manager(getInstance("specialCases"))
        .name("templateNotFound")
        .build();
      fail("exception expected");
    } catch (Exception ex) {
      assertThat(ex.toString()).contains("Template not found 'notThere'");
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

  @Test
  public void illegalBoolean() {
    assertThatCode(() -> {
      Cache c =
        new Cache2kBuilder<String, String>() { }
          .manager(CacheManager.getInstance("specialCases"))
          .name("illegalBoolean")
          .build();
      c.close();
    }).isInstanceOf(ConfigurationException.class);
  }

  /**
   * As of version 2.0 we allow duplicate listeners to simplify the configuration
   */
  public void duplicateListener() {
    Cache2kBuilder<String, String> builder =
      new Cache2kBuilder<String, String>() { }
        .manager(getInstance("specialCases"))
        .name("duplicateListener");
    Cache<String, String> cache = builder.build();
    assertThat(builder.config().getListeners().size()).isEqualTo(2);
  }

  @Test
  public void typeMismatch() {
    assertThatCode(() -> {
      Cache c =
        new Cache2kBuilder<String, String>() { }
          .manager(CacheManager.getInstance("specialCases"))
          .name("typeMismatch")
          .build();
      c.close();
    }).isInstanceOf(ConfigurationException.class);
  }


  @Test
  public void featureEnabled() {
    Cache c =
      new Cache2kBuilder<String, String>() { }
        .manager(getInstance("specialCases"))
        .name("withFeature")
        .build();
    assertThat(wasEnabled(c)).isTrue();
  }

  @Test
  public void featureDisabled() {
    Cache c =
      new Cache2kBuilder<String, String>() { }
        .manager(getInstance("specialCases"))
        .name("withFeatureDisabled")
        .build();
    assertThat(wasEnabled(c)).isFalse();
  }

  @Test
  public void notSerializableSection() {
    try {
      new Cache2kBuilder<String, String>() { }
        .manager(getInstance("notSerializable"))
        .name("notSerializable")
        .build();
      fail("exception expected");
    } catch (Exception ex) {
      assertThat(ex.toString()).contains("Copying default cache configuration");
      assertThat(ex.getCause()).isInstanceOf(Serializable.class);
    }
  }

  @Test
  public void parseError() {
    try {
      new Cache2kBuilder<String, String>() { }
        .manager(getInstance("parseError"))
        .entryCapacity(1234);
      fail("exception expected");
    } catch (Exception ex) {
      assertThat(ex.toString()).contains("type missing or unknown");
    }
  }

  @Test
  public void parseErrorWrongXml() {
    assertThatCode(() -> {
      new Cache2kBuilder<String, String>() { }
        .manager(CacheManager.getInstance("parseErrorWrongXml"))
        .entryCapacity(1234);
    }).isInstanceOf(ConfigurationException.class);
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
      .manager(getInstance("onlyDefault"))
      .name("anyCache")
      .config();
    assertThat(cfg.getEntryCapacity()).isEqualTo(1234);
    assertThat(cfg.getExpireAfterWrite().toString()).isEqualTo("PT12M");
    assertThat(cfg.isExternalConfigurationPresent()).isTrue();
  }

  @Test
  public void empty() {
    assertThatCode(() -> {
      Cache c = new Cache2kBuilder<String, String>() { }
        .manager(CacheManager.getInstance("empty"))
        .name("anyCache")
        .build();
      c.close();
    }).isInstanceOf(IllegalArgumentException.class);
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
