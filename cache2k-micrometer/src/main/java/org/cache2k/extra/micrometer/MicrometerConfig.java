package org.cache2k.extra.micrometer;

/*
 * #%L
 * cache2k micrometer monitoring support
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

import io.micrometer.core.instrument.MeterRegistry;
import org.cache2k.config.SectionBuilder;
import org.cache2k.config.CustomizationReferenceSupplier;
import org.cache2k.config.CustomizationSupplier;
import org.cache2k.config.ConfigSection;

/**
 * Configuration section for micrometer. This allows configuring the registry
 * in a global configuration and then switch on and off micrometer reporting
 * on a individual cache basis.
 *
 * @author Jens Wilke
 */
public class MicrometerConfig
  implements ConfigSection<MicrometerConfig, MicrometerConfig.Builder> {

  private CustomizationSupplier<MeterRegistry> registry =
    GlobalRegistrySupplier.INSTANCE;

  /**
   * See {@link Builder#registry(MeterRegistry)}
   */
  public CustomizationSupplier<MeterRegistry> getRegistry() {
    return registry;
  }

  /**
   * See {@link Builder#registry(MeterRegistry)}
   */
  public void setRegistry(CustomizationSupplier<MeterRegistry> meterRegistry) {
    this.registry = meterRegistry;
  }

  @Override
  public MicrometerConfig.Builder builder() {
    return new Builder(this);
  }

  public static final class Builder implements SectionBuilder<Builder, MicrometerConfig> {

    private final MicrometerConfig config;

    private Builder(MicrometerConfig config) {
      this.config = config;
    }

    /**
     * Set the meter registry to use for the cache instance.
     */
    public Builder registry(MeterRegistry registry) {
      config.setRegistry(new CustomizationReferenceSupplier<>(registry));
      return this;
    }

    /**
     * Bind this cache instance to the global meter registry.
     */
    public Builder useGlobalRegistry() {
      config.setRegistry(GlobalRegistrySupplier.INSTANCE);
      return this;
    }

    @Override
    public MicrometerConfig config() {
      return config;
    }

  }

}
