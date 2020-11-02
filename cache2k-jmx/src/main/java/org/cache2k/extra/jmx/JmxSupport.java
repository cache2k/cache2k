package org.cache2k.extra.jmx;

/*
 * #%L
 * cache2k JMX support
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.config.Cache2kConfig;
import org.cache2k.config.CacheBuildContext;
import org.cache2k.config.ToggleCacheFeature;

/**
 * Adds optional support for JMX.
 *
 * <p>Registering a name may fail because cache manager names may be identical in different
 * class loaders.
 */
public final class JmxSupport extends ToggleCacheFeature {

  /**
   * Enable JMX monitoring.
   */
  public static void enable(Cache2kBuilder<?, ?> builder) {
    builder.enable(JmxSupport.class);
  }

  /**
   * Disable JMX monitoring.
   */
  public static void disable(Cache2kBuilder<?, ?> builder) {
    builder.disable(JmxSupport.class);
  }

  /**
   * If enabled register lifecycle listeners so we get called as soon as the cache
   * is build or closed.
   */
  @Override
  public void doEnlist(CacheBuildContext<?, ?> ctx) {
    Cache2kConfig<?, ?> cfg = ctx.getConfig();
    if (cfg.isDisableMonitoring()) {
      return;
    }
    cfg.getLifecycleListeners().add(LifecycleListener.SUPPLIER);
  }

}
