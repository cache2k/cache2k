package org.cache2k.xmlConfig;

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
import org.cache2k.CacheMisconfigurationException;
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.jcache.JCacheConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test that XML configuration elements get picked up when building cache2k
 *
 * @author Jens Wilke
 */
public class IntegrationTest {

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

  @Test
  public void sectionIsThere() {
    Cache2kBuilder<String, String> b =
      new Cache2kBuilder<String, String>(){}.name("withSection");
    Cache2kConfiguration<String, String> cfg = b.toConfiguration();
    Cache<String, String> c = b.build();
    assertEquals("default is false", false, new JCacheConfiguration().isAlwaysFlushJmxStatistics());
    assertEquals("config applied", true, cfg.getSections().getSection(JCacheConfiguration.class).isAlwaysFlushJmxStatistics());
    c.close();
  }

  @Test(expected = ConfigurationException.class)
  public void failIfConfigurationIsMissing() {
    new Cache2kBuilder<String, String>(){}.name("missingDummy").build();
  }

  @Test(expected = CacheMisconfigurationException.class)
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
      assertTrue(ex.toString().contains("Unknown property"));
    }
  }

  @Test
  public void noManagerConfiguration() {
    new Cache2kBuilder<String, String>() { }
      .manager(CacheManager.getInstance("noManager"))
      .entryCapacity(1234);
  }

}
