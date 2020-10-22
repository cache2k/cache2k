package org.cache2k.extra.micrometer;

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
import org.cache2k.Cache;
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.core.spi.CacheLifeCycleListener;
import org.cache2k.core.util.TunableConstants;
import org.cache2k.core.util.TunableFactory;

/**
 * Binds to micrometer. Binding is omitted if monitoring is disabled.
 *
 * <p>The binding is done when the cache manager property
 * {@link #MICROMETER_REGISTRY_MANAGER_PROPERTY} is set with the registry to
 * use. Alternatively, binding to the global registry can be enabled
 * via tunables.
 *
 * @author Jens Wilke
 */
public class MicroMeterSupport implements CacheLifeCycleListener {

  public static final String MICROMETER_REGISTRY_MANAGER_PROPERTY =
    MicroMeterSupport.class.getName() + ".registry";
  private static final boolean REGISTER_AT_GLOBAL_REGISTRY =
    TunableFactory.get(Tunable.class).registerAtGlobalRegistry;

  @Override
  public void cacheCreated(Cache c, Cache2kConfiguration cfg) {
    if (cfg.isDisableMonitoring()) {
      return;
    }
    MeterRegistry registry =
      (MeterRegistry)
        c.getCacheManager().getProperties().get(MICROMETER_REGISTRY_MANAGER_PROPERTY);
    if (registry == null) {
      if (!REGISTER_AT_GLOBAL_REGISTRY) {
        return;
      }
      registry = Metrics.globalRegistry;
    }
    Cache2kCacheMetrics.monitor(registry, c);
  }

  /**
   * Don't unbind at the moment. Its possible to delete meters, but that's a relative
   * new feature.
   */
  @Override
  public void cacheDestroyed(Cache c) { }

  public static class Tunable extends TunableConstants {

    /**
     * If true every cache is registered at the global micrometer registry in
     * case monitoring is not disabled.
     */
    public boolean registerAtGlobalRegistry = false;

  }

}
