package org.cache2k.jcache.provider;

/*-
 * #%L
 * cache2k JCache provider
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

import javax.cache.Cache;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.management.CacheMXBean;

/**
 * @author Jens Wilke
 */
public class JCacheJmxCacheMXBean implements CacheMXBean {

  private Cache<?, ?> cache;

  public JCacheJmxCacheMXBean(Cache cache) {
    this.cache = cache;
  }

  @Override
  public String getKeyType() {
    return configuration().getKeyType().getName();
  }

  @Override
  public String getValueType() {
    return configuration().getValueType().getName();
  }

  @Override
  public boolean isManagementEnabled() {
    return completeConfiguration().isManagementEnabled();
  }

  @Override
  public boolean isReadThrough() {
    return completeConfiguration().isReadThrough();
  }

  @Override
  public boolean isStatisticsEnabled() {
    return completeConfiguration().isStatisticsEnabled();
  }

  @Override
  public boolean isStoreByValue() {
    return configuration().isStoreByValue();
  }

  @Override
  public boolean isWriteThrough() {
    return completeConfiguration().isWriteThrough();
  }

  @SuppressWarnings("unchecked")
  private Configuration<?, ?> configuration() {
    return cache.getConfiguration(Configuration.class);
  }

  @SuppressWarnings("unchecked")
  CompleteConfiguration<?, ?> completeConfiguration() {
    return cache.getConfiguration(CompleteConfiguration.class);
  }

}
