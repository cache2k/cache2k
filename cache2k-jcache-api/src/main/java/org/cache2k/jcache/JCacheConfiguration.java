package org.cache2k.jcache;

/*
 * #%L
 * cache2k JCache provider API
 * %%
 * Copyright (C) 2000 - 2017 headissue GmbH, Munich
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
 * Configuration section for the cache2k configuration to control additional
 * behavior related to JCache.
 *
 * @author Jens Wilke
 */
public class JCacheConfiguration implements SingletonConfigurationSection {

  private boolean copyAlwaysIfRequested = false;
  private boolean alwaysFlushJmxStatistics = false;

  public boolean isAlwaysFlushJmxStatistics() {
    return alwaysFlushJmxStatistics;
  }

  /**
   * @see JCacheConfiguration.Builder#alwaysFlushJmxStatistics
   */
  public void setAlwaysFlushJmxStatistics(final boolean f) {
    alwaysFlushJmxStatistics = f;
  }

  public boolean isCopyAlwaysIfRequested() {
    return copyAlwaysIfRequested;
  }

  /**
   * @see JCacheConfiguration.Builder#copyAlwaysIfRequested
   */
  public void setCopyAlwaysIfRequested(final boolean f) {
    copyAlwaysIfRequested = f;
  }

  public static class Builder implements ConfigurationSectionBuilder<JCacheConfiguration> {

    private JCacheConfiguration config = new JCacheConfiguration();

    /**
     * Enabling this was needed for passing the JCache 1.0 tests.
     * Sine JCache 1.1 this is no longer needed. This flat has no
     * effect since cache2k version 1.0.2.
     */
    public Builder alwaysFlushJmxStatistics(boolean f) {
      config.setAlwaysFlushJmxStatistics(f);
      return this;
    }

    /**
     * Copy keys and values when entering and leaving the cache in case
     * store by value semantics are requested by the application.
     * Default in JCache configuration mode: {@code true}.
     * Default in cache2k configuration mode: {@code false}.
     */
    public Builder copyAlwaysIfRequested(boolean f) {
      config.setCopyAlwaysIfRequested(f);
      return this;
    }

    @Override
    public JCacheConfiguration buildConfigurationSection() {
      return config;
    }

  }

}
