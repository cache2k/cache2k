package org.cache2k.addon;

/*-
 * #%L
 * cache2k addon
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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
import org.cache2k.config.CacheBuildContext;
import org.cache2k.config.CustomizationSupplier;
import org.cache2k.config.ToggleFeature;
import org.cache2k.config.WithSection;
import org.cache2k.io.AsyncBulkCacheLoader;
import org.cache2k.io.AsyncCacheLoader;

/**
 * Wraps a configured {@link AsyncBulkCacheLoader} with a {@link CoalescingBulkLoader}
 * and configures it with {@link CoalescingBulkLoaderConfig}.
 *
 * @author Jens Wilke
 */
public class CoalescingBulkLoaderSupport extends ToggleFeature
  implements WithSection<CoalescingBulkLoaderConfig, CoalescingBulkLoaderConfig.Builder>  {

  private static final CoalescingBulkLoaderConfig DEFAULT_CONFIG = new CoalescingBulkLoaderConfig();

  public static CoalescingBulkLoaderSupport enable(Cache2kBuilder<?, ?> b) {
    return ToggleFeature.enable(b, CoalescingBulkLoaderSupport.class);
  }

  public static void disable(Cache2kBuilder<?, ?> b) {
    b.disable(CoalescingBulkLoaderSupport.class);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected <K, V> void doEnlist(CacheBuildContext<K, V> ctx) {
    CustomizationSupplier<? extends AsyncCacheLoader<?, ?>> originalLoaderSupplier =
      ctx.getConfig().getAsyncLoader();
    if (originalLoaderSupplier == null) {
      bulkLoaderNeeded();
    }
    CustomizationSupplier<AsyncCacheLoader<K, V>> xy = buildContext -> {
      AsyncCacheLoader<?, ?> loader = buildContext.createCustomization(originalLoaderSupplier);
      if (!(loader instanceof AsyncBulkCacheLoader)) {
        bulkLoaderNeeded();
      }
      CoalescingBulkLoaderConfig config =
        ctx.getConfig().getSections().getSection(CoalescingBulkLoaderConfig.class, DEFAULT_CONFIG);
      return new CoalescingBulkLoader<K, V>((AsyncBulkCacheLoader<K, V>) loader, buildContext.getTimeReference(),
        config.getMaxDelay(), config.getMaxBatchSize(), config.isRefreshOnly());
    };
    ctx.getConfig().setAsyncLoader(xy);
  }

  private void bulkLoaderNeeded() {
    throw new IllegalArgumentException(this.getClass().getName()
      + " requires a configured bulk loader");
  }

  @Override
  public Class<CoalescingBulkLoaderConfig> getConfigClass() {
    return CoalescingBulkLoaderConfig.class;
  }

}
