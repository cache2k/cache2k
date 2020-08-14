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

import org.cache2k.configuration.ConfigurationSectionBuilder;
import org.cache2k.configuration.SingletonConfigurationSection;

/**
 * @author Jens Wilke
 */
public class MicroMeterConfiguration implements SingletonConfigurationSection {

  private boolean enable;

  /**
   * @see Builder#enable(boolean)
   */
  public boolean isEnable() {
    return enable;
  }

  public void setEnable(final boolean enable) {
    this.enable = enable;
  }

  public static class Builder implements ConfigurationSectionBuilder<MicroMeterConfiguration> {

    private MicroMeterConfiguration config = new MicroMeterConfiguration();

    /**
     * Binds cache2k metrics to the default registry or the registry instance
     * found in the manager property {@value MicroMeterSupport#MICROMETER_REGISTRY_MANAGER_PROPERTY}
     */
    public Builder enable(boolean f) {
      config.setEnable(f);
      return this;
    }

    @Override
    public MicroMeterConfiguration buildConfigurationSection() {
      return config;
    }

  }

}
