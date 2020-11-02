package org.cache2k.extra.micrometer.impl;

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
import org.cache2k.Cache;
import org.cache2k.config.Cache2kConfig;
import org.cache2k.core.api.InternalCacheBuildContext;
import org.cache2k.core.api.InternalCacheCloseContext;
import org.cache2k.core.spi.CacheLifeCycleListener;
import org.cache2k.extra.micrometer.Cache2kCacheMetrics;
import org.cache2k.extra.micrometer.MicrometerConfig;

/**
 * Automatically binds to the micrometer registry supplied by the configuration.
 * The binding is omitted if monitoring is disabled.
 *
 * @author Jens Wilke
 */
public class MicrometerSupport implements CacheLifeCycleListener {

  @Override
  public <K, V> void cacheCreated(Cache<K, V> c, InternalCacheBuildContext<K, V> ctx) {
    Cache2kConfig<K, V> cfg = ctx.getConfig();
    if (cfg.isDisableMonitoring()) { return; }
    MicrometerConfig ourCfg = cfg.getSections().getSection(MicrometerConfig.class);
    if (ourCfg == null) { return; }
    MeterRegistry registry = ctx.createCustomization(ourCfg.getMeterRegistry());
    if (registry == null) { return; }
    Cache2kCacheMetrics.monitor(registry, c);
  }

  /**
   * Don't unbind. Deleting meters is a fresh feature in micrometer.
   */
  @Override
  public <K, V> void cacheClosed(Cache<K, V> cache, InternalCacheCloseContext ctx) { }

}
