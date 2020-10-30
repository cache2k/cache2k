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
public class MicrometerConfiguration implements SingletonConfigurationSection {

  private CustomizationSupplier<MeterRegistry> meterRegistry;

  public CustomizationSupplier<MeterRegistry> getMeterRegistry() {
    return meterRegistry;
  }

  public void setMeterRegistry(CustomizationSupplier<MeterRegistry> meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public static final class Builder implements ConfigurationSectionBuilder<MicrometerConfiguration> {

    private final MicrometerConfiguration config = new MicrometerConfiguration();

    public Builder meterRegistry(MeterRegistry registry) {
      config.setMeterRegistry(new CustomizationReferenceSupplier<>(registry));
      return this;
    }

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
