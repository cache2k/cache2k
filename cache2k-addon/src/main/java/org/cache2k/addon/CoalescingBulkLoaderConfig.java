package org.cache2k.addon;

/*
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

import org.cache2k.config.ConfigSection;
import org.cache2k.config.SectionBuilder;

import java.util.concurrent.TimeUnit;

/**
 * Configuration options for {@link CoalescingBulkLoaderSupport}.
 *
 * @author Jens Wilke
 */
public class CoalescingBulkLoaderConfig
  implements ConfigSection<CoalescingBulkLoaderConfig, CoalescingBulkLoaderConfig.Builder> {

  private long maxDelay = 100;
  private int maxBatchSize = 100;

  public long getMaxDelay() {
    return maxDelay;
  }

  /**
   * Delay in milliseconds.
   *
   * @see Builder#maxDelay(long, TimeUnit)
   */
  public void setMaxDelay(long maxDelay) {
    this.maxDelay = maxDelay;
  }

  public int getMaxBatchSize() {
    return maxBatchSize;
  }

  public void setMaxBatchSize(int maxBatchSize) {
    this.maxBatchSize = maxBatchSize;
  }

  @Override
  public Builder builder() {
    return new Builder(this);
  }

  public static class Builder implements SectionBuilder<Builder, CoalescingBulkLoaderConfig> {

    private final CoalescingBulkLoaderConfig config;

    public Builder(CoalescingBulkLoaderConfig config) {
      this.config = config;
    }

    /**
     * Maximum timespan a load request may be delayed before its sent to the loader.
     */
    public Builder maxDelay(long duration, TimeUnit unit) {
      config.setMaxDelay(unit.toMillis(duration));
      return this;
    }

    /**
     * If the number of entries waiting for sending to the loader is reached the
     * loader will be called even the delay timespan is not reached yet.
     */
    public Builder maxBatchSize(int v) {
      config.setMaxBatchSize(v);
      return this;
    }

    @Override
    public CoalescingBulkLoaderConfig config() {
      return config;
    }
  }

}
