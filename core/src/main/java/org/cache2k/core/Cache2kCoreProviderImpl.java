package org.cache2k.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.Cache;
import org.cache2k.configuration.CacheConfiguration;
import org.cache2k.CacheManager;
import org.cache2k.spi.Cache2kCoreProvider;
import org.cache2k.spi.Cache2kManagerProvider;

/**
 * @author Jens Wilke; created: 2014-04-20
 */
public class Cache2kCoreProviderImpl extends Cache2kCoreProvider {

  Cache2kManagerProviderImpl provider;

  @Override
  public synchronized Cache2kManagerProvider getManagerProvider() {
    if (provider == null) {
      provider = new Cache2kManagerProviderImpl();
    }
    return provider;
  }

  @Override
  public <K, V> Cache<K, V> createCache(final CacheManager m, final CacheConfiguration<K, V> cfg) {
    return new InternalCache2kBuilder<K,V>(cfg, m).build();
  }

}
