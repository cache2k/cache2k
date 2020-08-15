/*
 * #%L
 * cache2k micrometer monitoring support
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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheManager;
import org.cache2k.extra.micrometer.Cache2kCacheMetrics;
import static org.junit.Assert.*;

import org.cache2k.extra.micrometer.MicroMeterSupport;
import org.junit.Test;

/**
 * Does test the various bind option.
 * TODO: test that metrics are exported correctly
 *
 * @author Jens Wilke
 */
public class MicroMeterTest {

  @Test
  public void programmaticBind() {
    MeterRegistry registry = new SimpleMeterRegistry();
    Cache cache = Cache2kBuilder.forUnknownTypes().build();
    Cache2kCacheMetrics.monitor(registry, cache);
    assertTrue(registry.get("cache.puts").functionCounter().count() >= 0);
    cache.close();
  }

  @Test
  public void notBoundToGlobalRegistryWhenDisabled() {
    Cache cache = Cache2kBuilder.forUnknownTypes()
      .name("bindToGlobalRegistryWhenDisabled")
      .disableMonitoring(true)
      .build();
    try {
      Metrics.globalRegistry.get("cache.puts")
        .tag("cache", cache.getName()).meters();
      fail("exception expected");
    } catch (MeterNotFoundException expected) {}
  }

  @Test
  public void bindToGlobalRegistryWhenEnabled() {
    Cache cache = Cache2kBuilder.forUnknownTypes()
      .name("bindToGlobalRegistryWhenEnabled")
      .disableMonitoring(false)
      .build();
    assertTrue(Metrics.globalRegistry.get("cache.puts")
      .tag("cache", cache.getName())
      .functionCounter().count() >= 0);
  }

  @Test
  public void bindToGlobalRegistryWhenEnabledByDefault() {
    Cache cache = Cache2kBuilder.forUnknownTypes()
      .name("bindToGlobalRegistryWhenEnabledByDefault")
      .build();
    assertTrue(Metrics.globalRegistry.get("cache.puts")
      .tag("cache", cache.getName())
      .functionCounter().count() >= 0);
  }

  @Test
  public void bindWhenStatisticsDisabled() {
    Cache cache = Cache2kBuilder.forUnknownTypes()
      .disableStatistics(true)
      .name("bindWhenStatisticsDisabled")
      .build();
    assertTrue(Metrics.globalRegistry.get("cache.puts")
      .tag("cache", cache.getName())
      .functionCounter().count() >= 0);
  }

  @Test
  public void bindToRegistryInCacheManager() {
    CacheManager mgm = CacheManager.getInstance("another");
    MeterRegistry registry = new SimpleMeterRegistry();
    mgm.getProperties().put(MicroMeterSupport.MICROMETER_REGISTRY_MANAGER_PROPERTY, registry);
    Cache cache = Cache2kBuilder.forUnknownTypes()
      .manager(mgm)
      .build();
    try {
      Metrics.globalRegistry.get("cache.puts").tag("cache", cache.getName()).meters();
      fail("not in global registry");
    } catch (MeterNotFoundException expected) { }
    assertTrue(registry.get("cache.puts").
      tag("cache", cache.getName()).functionCounter().count() >= 0);
  }

}
