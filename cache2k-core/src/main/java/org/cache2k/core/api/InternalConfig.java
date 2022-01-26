package org.cache2k.core.api;

/*-
 * #%L
 * cache2k core implementation
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

import org.cache2k.config.Cache2kConfig;
import org.cache2k.config.ConfigSection;
import org.cache2k.config.CustomizationReferenceSupplier;
import org.cache2k.config.CustomizationSupplier;
import org.cache2k.config.SectionBuilder;
import org.cache2k.core.StandardCommonMetrics;
import org.cache2k.core.concurrency.ThreadFactoryProvider;

/**
 * @author Jens Wilke
 */
public class InternalConfig implements ConfigSection<InternalConfig, InternalConfig.Builder> {

  private static final CommonMetrics.Updater METRICS_BLACKHOLE = new CommonMetrics.BlackHole();

  private int evictionSegmentCount = Cache2kConfig.UNSET_INT;
  private CustomizationSupplier<ThreadFactoryProvider> threadFactoryProvider =
    new CustomizationReferenceSupplier<>(ThreadFactoryProvider.DEFAULT);
  private CustomizationSupplier<CommonMetrics.Updater> commonMetrics = buildContext -> {
    if (buildContext.getConfig().isDisableStatistics()) {
      return METRICS_BLACKHOLE;
    }
    return new StandardCommonMetrics();
  };

  public CustomizationSupplier<ThreadFactoryProvider> getThreadFactoryProvider() {
    return threadFactoryProvider;
  }

  public void setThreadFactoryProvider(CustomizationSupplier<ThreadFactoryProvider> v) {
    this.threadFactoryProvider = v;
  }

  public int getEvictionSegmentCount() {
    return evictionSegmentCount;
  }

  /**
   * @see Builder#evictionSegmentCount(int)
   */
  public void setEvictionSegmentCount(int evictionSegmentCount) {
    this.evictionSegmentCount = evictionSegmentCount;
  }

  public CustomizationSupplier<CommonMetrics.Updater> getCommonMetrics() {
    return commonMetrics;
  }

  public void setCommonMetrics(CustomizationSupplier<CommonMetrics.Updater> commonMetrics) {
    this.commonMetrics = commonMetrics;
  }

  @Override
  public Builder builder() {
    return new Builder(this);
  }

  public static class Builder implements SectionBuilder<Builder, InternalConfig> {

    private final InternalConfig cfg;

    public Builder(InternalConfig cfg) {
      this.cfg = cfg;
    }

    /**
     * Segmentation count for eviction to use instead of the automatic one derived from
     * the system CPU count. Has to be power of two, e.g. 2, 4, 8, etc.
     * Invalid numbers will be replaced by the next higher power of two. Default is 0, no override.
     */
    public Builder evictionSegmentCount(int v) {
      cfg.setEvictionSegmentCount(v);
      return this;
    }

    public Builder threadFactoryProvider(ThreadFactoryProvider v) {
      cfg.setThreadFactoryProvider(new CustomizationReferenceSupplier<>(v));
      return this;
    }

    public Builder commonMetrics(CommonMetrics.Updater v) {
      cfg.setCommonMetrics(new CustomizationReferenceSupplier<CommonMetrics.Updater>(v));
      return this;
    }

    @Override
    public InternalConfig config() {
      return cfg;
    }
  }

}
