package org.cache2k.test.core;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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

import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.configuration.ConfigurationSection;
import org.cache2k.configuration.CustomizationSupplier;
import org.cache2k.configuration.CustomizationSupplierByClassName;
import org.cache2k.event.CacheEntryOperationListener;
import org.cache2k.jcache.JCacheConfiguration;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
public class Cache2kConfigurationTest {

  @Test
  public void checkListenerSetters() {
    CustomizationSupplier sup = new CustomizationSupplierByClassName<CacheEntryOperationListener>("xy");
    Cache2kConfiguration cfg = new Cache2kConfiguration();
    cfg.setAsyncListeners(Collections.singletonList(sup));
    assertEquals(1, cfg.getAsyncListeners().size());
    cfg.setListeners(Collections.singletonList(sup));
    assertEquals(1, cfg.getListeners().size());
    cfg.setCacheClosedListeners(Collections.singletonList(sup));
    assertEquals(1, cfg.getCacheClosedListeners().size());
    assertTrue(cfg.getCacheClosedListeners().iterator().hasNext());
    assertThat(
      cfg.getCacheClosedListeners().toString(),
      CoreMatchers.containsString("DefaultCustomizationCollection"));
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

  @Test(expected = IllegalArgumentException.class)
  public void checkNoDuplicateSection() {
    Cache2kConfiguration cfg = new Cache2kConfiguration();
    cfg.setSections(Collections.singletonList(new JCacheConfiguration()));
    cfg.setSections(Collections.singletonList(new JCacheConfiguration()));
  }

  interface DummySection extends ConfigurationSection { }

}
