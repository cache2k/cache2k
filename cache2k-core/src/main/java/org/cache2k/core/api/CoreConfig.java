package org.cache2k.core.api;

/*
 * #%L
 * cache2k core implementation
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

import org.cache2k.config.SectionBuilder;
import org.cache2k.config.CustomizationReferenceSupplier;
import org.cache2k.config.CustomizationSupplier;
import org.cache2k.config.ConfigSection;

/**
 * Extra configuration for internals usually not needed by normal applications.
 *
 * @author Jens Wilke
 */
public class CoreConfig
  implements ConfigSection<CoreConfig, CoreConfig.Builder> {

  public static final CoreConfig DEFAULT = new CoreConfig();

  private CustomizationSupplier<InternalClock> timeReference;

  public CustomizationSupplier<InternalClock> getTimeReference() {
    return timeReference;
  }

  public void setTimeReference(CustomizationSupplier<InternalClock> timeReference) {
    this.timeReference = timeReference;
  }

  @Override
  public Builder builder() {
    return new Builder(this);
  }

  public static class Builder implements SectionBuilder<CoreConfig> {

    private final CoreConfig config;

    private Builder(CoreConfig config) {
      this.config = config;
    }

    public Builder timerReference(InternalClock clock) {
      config.setTimeReference(new CustomizationReferenceSupplier<>(clock));
      return this;
    }

    @Override
    public CoreConfig config() {
      return config;
    }
  }

}
