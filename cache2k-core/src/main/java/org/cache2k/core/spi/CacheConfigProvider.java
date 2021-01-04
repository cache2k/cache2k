package org.cache2k.core.spi;

/*
 * #%L
 * cache2k core implementation
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

import org.cache2k.CacheManager;
import org.cache2k.config.Cache2kConfig;

/**
 * Plugin interface for the configuration system. Provides a default configuration,
 * which can be different for each manager and additional configuration, for each cache.
 *
 * @author Jens Wilke
 */
public interface CacheConfigProvider {

  /**
   * Name for the default manager for the given class loader.
   */
  String getDefaultManagerName(ClassLoader classLoader);

  /**
   * A new configuration instance for mutation with default values. The default values
   * may differ per manager. The method gets called whenever a new cache is constructed
   * via the builder. If a cache is constructed from a configuration object the method
   * will not be called.
   *
   * @param mgr Manager the new cache will live in
   */
  Cache2kConfig getDefaultConfig(CacheManager mgr);

  /**
   * Called when {@link org.cache2k.Cache2kBuilder#build()} was called before the configuration
   * is used to create the cache. If no name was specified in the application, the name in the
   * configuration is null.
   *
   * @param mgr Manager the new cache will live in
   * @param cfg the cache configuration
   */
  <K, V> void augmentConfig(CacheManager mgr, Cache2kConfig<K, V> cfg);

  /**
   * List of cache names found in the configuration.
   */
  Iterable<String> getConfiguredCacheNames(CacheManager mgr);

}
