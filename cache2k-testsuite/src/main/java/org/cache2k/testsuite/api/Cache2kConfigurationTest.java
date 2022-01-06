package org.cache2k.testsuite.api;

/*-
 * #%L
 * cache2k testsuite on public API
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.config.Cache2kConfig;
import org.cache2k.config.CustomizationSupplier;
import org.cache2k.config.CustomizationSupplierByClassName;
import org.cache2k.config.ToggleFeature;
import org.cache2k.event.CacheEntryOperationListener;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class Cache2kConfigurationTest {

  @Test
  public void checkListenerSetters() {
    CustomizationSupplier sup =
      new CustomizationSupplierByClassName<CacheEntryOperationListener>("xy");
    Cache2kConfig cfg = new Cache2kConfig();
    cfg.setAsyncListeners(Collections.singletonList(sup));
    assertEquals(1, cfg.getAsyncListeners().size());
    cfg.setListeners(Collections.singletonList(sup));
    assertEquals(1, cfg.getListeners().size());
    cfg.setLifecycleListeners(Collections.singletonList(sup));
    assertEquals(1, cfg.getLifecycleListeners().size());
    assertTrue(cfg.getLifecycleListeners().iterator().hasNext());
  }

  @Test
  public void toggleFeature() {
    Cache2kBuilder<Integer, Integer> b =
      Cache2kBuilder.of(Integer.class, Integer.class);
    ToggleFeature.enable(b, DummyToggleFeature.class);
    assertTrue(ToggleFeature.isEnabled(b, DummyToggleFeature.class));
    ToggleFeature.extract(b, DummyToggleFeature.class).setEnabled(false);
    assertFalse(ToggleFeature.isEnabled(b, DummyToggleFeature.class));
    ToggleFeature.enable(b, DummyToggleFeature.class);
    assertTrue(ToggleFeature.isEnabled(b, DummyToggleFeature.class));
    DummyToggleFeature other = new DummyToggleFeature();
    b.config().getFeatures().add(other);
    assertNotSame(other, ToggleFeature.extract(b, DummyToggleFeature.class));
  }

  @Test
  public void testEternalConfig() {
    assertTrue("max val means eternal",
      Cache2kBuilder.of(Integer.class, Integer.class)
        .expireAfterWrite(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
        .config().getExpireAfterWrite().equals(Cache2kConfig.EXPIRY_ETERNAL));
  }

}
