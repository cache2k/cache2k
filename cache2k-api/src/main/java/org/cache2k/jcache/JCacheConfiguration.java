package org.cache2k.jcache;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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

import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.CompleteConfiguration;

/**
 * Configuration section for the cache2k configuration to control additional
 * behavior related to JCache.
 *
 * @author Jens Wilke
 * @see <a href="https://cache2k.org/docs/latest/user-guide.html#jcache">JSR107 / JCache - cache2k User Guide</a>
 */
public class JCacheConfiguration implements SingletonConfigurationSection {

  private boolean copyAlwaysIfRequested = false;
  private boolean supportOnlineListenerAttachment = false;
  private boolean enableStatistics = false;
  private boolean enableManagement = false;

  public boolean isCopyAlwaysIfRequested() {
    return copyAlwaysIfRequested;
  }

  /**
   * @see JCacheConfiguration.Builder#copyAlwaysIfRequested
   */
  public void setCopyAlwaysIfRequested(final boolean f) {
    copyAlwaysIfRequested = f;
  }

  public boolean isSupportOnlineListenerAttachment() {
    return supportOnlineListenerAttachment;
  }

  /**
   * @see JCacheConfiguration.Builder#supportOnlineListenerAttachment(boolean)
   */
  public void setSupportOnlineListenerAttachment(final boolean f) {
    supportOnlineListenerAttachment = f;
  }

  public boolean isEnableStatistics() {
    return enableStatistics;
  }

  /**
   * @see Builder#enableStatistics
   */
  public void setEnableStatistics(final boolean f) {
    enableStatistics = f;
  }

  public boolean isEnableManagement() {
    return enableManagement;
  }

  /**
   * @see Builder#enableManagement
   */
  public void setEnableManagement(final boolean f) {
    enableManagement = f;
  }

  public static class Builder implements ConfigurationSectionBuilder<JCacheConfiguration> {

    private JCacheConfiguration config = new JCacheConfiguration();

    /**
     * When {@code true}, copy keys and values when entering and leaving
     * the cache in case {@link javax.cache.configuration.Configuration#isStoreByValue()}
     * is {@code true}.
     * This needs to be enabled for 100% JCache compatibility.
     * Default, if no cache2k configuration is present: {@code true}.
     * Default in cache2k configuration mode: {@code false}.
     */
    public Builder copyAlwaysIfRequested(boolean f) {
      config.setCopyAlwaysIfRequested(f);
      return this;
    }

    /**
     * Set to true, if online register and deregister of event listeners needs to be supported.
     * Default, if no cache2k configuration is present: {@code true}.
     * Default in cache2k configuration mode: {@code false}.
     *
     * @see javax.cache.Cache#registerCacheEntryListener(CacheEntryListenerConfiguration)
     */
    public Builder supportOnlineListenerAttachment(boolean f) {
      config.setSupportOnlineListenerAttachment(f);
      return this;
    }

    /**
     * When {@code true} makes the JMX management bean for the cache available.
     * Identical to the flag in the JCache configuration object.
     *
     * @see CompleteConfiguration#isManagementEnabled()
     */
    public Builder enableManagement(boolean f) {
      config.setEnableManagement(f);
      return this;
    }

    /**
     * When {@code true}, exposes cache statistics via JMX. Identical to the flag
     * in the JCache configuration object.
     *
     * @see CompleteConfiguration#isManagementEnabled()
     */
    public Builder enableStatistics(boolean f) {
      config.setEnableStatistics(f);
      return this;
    }

    @Override
    public JCacheConfiguration buildConfigurationSection() {
      return config;
    }

  }

}
