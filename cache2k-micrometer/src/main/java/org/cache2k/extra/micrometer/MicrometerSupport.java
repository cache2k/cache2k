package org.cache2k.extra.micrometer;

/*-
 * #%L
 * cache2k micrometer monitoring support
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
import org.cache2k.config.CacheBuildContext;
import org.cache2k.config.CustomizationReferenceSupplier;
import org.cache2k.config.ToggleFeature;
import org.cache2k.config.WithSection;
import org.cache2k.event.CacheCreatedListener;

import java.util.concurrent.CompletableFuture;

/**
 * Automatically binds to the micrometer registry supplied by the configuration.
 * The binding is omitted if monitoring is disabled.
 *
 * @author Jens Wilke
 */
public class MicrometerSupport
  extends ToggleFeature implements WithSection<MicrometerConfig, MicrometerConfig.Builder> {

  public static MicrometerSupport enable(Cache2kBuilder<?, ?> b) {
    return ToggleFeature.enable(b, MicrometerSupport.class);
  }

  public static void disable(Cache2kBuilder<?, ?> b) {
    b.disable(MicrometerSupport.class);
  }

  @Override
  protected <K, V> void doEnlist(CacheBuildContext<K, V> ctx) {
    if (ctx.getConfig().isDisableMonitoring()) {
      return;
    }
    ctx.getConfig().getLifecycleListeners().add(new CustomizationReferenceSupplier<>(LISTENER));
  }

  @Override
  public Class<MicrometerConfig> getConfigClass() {
    return MicrometerConfig.class;
  }

  private static final MicrometerConfig DEFAULT_CONFIG = new MicrometerConfig();
  private static final CacheCreatedListener LISTENER = new CacheCreatedListener() {

    @Override
    public <K, V> CompletableFuture<Void> onCacheCreated(Cache<K, V> cache,
                                                         CacheBuildContext<K, V> ctx) {
      MicrometerConfig config =
        ctx.getConfig().getSections().getSection(MicrometerConfig.class, DEFAULT_CONFIG);
      Cache2kCacheMetrics.monitor(ctx.createCustomization(config.getRegistry()), cache);
      return null;
    }
  };

}
