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

/**
 * Bind to micrometer automatically if enabled by configuration. Will be used by cache2k
 * internally, if present. Not for direct usage.
 *
 * @author Jens Wilke
 */
public class MicroMeterSupport implements CacheLifeCycleListener {

  public static final String MICROMETER_REGISTRY_MANAGER_PROPERTY = "micrometer.registry";

  @Override
  public void cacheCreated(final Cache c, final Cache2kConfiguration cfg) {
    if (cfg.getSections().stream()
        .filter(v -> v instanceof MicroMeterConfiguration)
        .findFirst().map(v -> (MicroMeterConfiguration) v)
        .filter(v -> v.isEnable()).isPresent()) {
      MeterRegistry registry =
        (MeterRegistry)
          c.getCacheManager().getProperties().get(MICROMETER_REGISTRY_MANAGER_PROPERTY);
      if (registry == null) {
        registry = Metrics.globalRegistry;
      }
      Cache2kCacheMetrics.monitor(registry, c);
    }
  }

  /**
   * Don't unbind at the moment. Its possible to delete meters, but that's a relative
   * new feature.
   */
  @Override
  public void cacheDestroyed(final Cache c) { }

}
