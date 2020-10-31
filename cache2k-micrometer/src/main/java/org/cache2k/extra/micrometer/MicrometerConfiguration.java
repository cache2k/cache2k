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
import org.cache2k.configuration.ConfigurationSectionBuilder;
import org.cache2k.configuration.CustomizationReferenceSupplier;
import org.cache2k.configuration.CustomizationSupplier;
import org.cache2k.configuration.SingletonConfigurationSection;

/**
 * @author Jens Wilke
 */
public class MicrometerConfiguration
  implements SingletonConfigurationSection
    <MicrometerConfiguration, MicrometerConfiguration.Builder> {

  private CustomizationSupplier<MeterRegistry> meterRegistry;

  /**
   * See {@link Builder#meterRegistry(MeterRegistry)}
   */
  public CustomizationSupplier<MeterRegistry> getMeterRegistry() {
    return meterRegistry;
  }

  /**
   * See {@link Builder#meterRegistry(MeterRegistry)}
   */
  public void setMeterRegistry(CustomizationSupplier<MeterRegistry> meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  public MicrometerConfiguration.Builder builder() {
    return new Builder(this);
  }

  public static final class Builder implements ConfigurationSectionBuilder<MicrometerConfiguration> {

    private final MicrometerConfiguration config;

    private Builder(MicrometerConfiguration config) {
      this.config = config;
    }

    /**
     * Set the meter registry to use for the cache instance.
     */
    public Builder meterRegistry(MeterRegistry registry) {
      config.setMeterRegistry(new CustomizationReferenceSupplier<>(registry));
      return this;
    }

    /**
     * Bind this cache instance to the global meter registry.
     */
    public Builder useGlobalRegistry() {
      config.setMeterRegistry(SupplyGlobalMeterRegistry.INSTANCE);
      return this;
    }

    @Override
    public MicrometerConfiguration buildConfigurationSection() {
      return config;
    }

  }

}
