package org.cache2k.config;

/*
 * #%L
 * cache2k API
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
import org.cache2k.operation.TimeReference;

import java.util.concurrent.Executor;

/**
 * Access to configuration and cache manager properties during the construction of
 * the cache object.
 *
 * @author Jens Wilke
 */
public interface CacheBuildContext<K, V> {

  /**
   * Assigned cache manager. This can be useful to retrieve resources
   * via the properties {@link CacheManager#getProperties()}
   */
  CacheManager getCacheManager();

  /**
   * The cache name. Always identical to {@link Cache2kConfig#getName()}
   */
  String getName();

  /**
   * Time reference to use for this cache instance.
   *
   * @throws IllegalStateException if this method is called within the supplier of
   *                               the time reference or executor.
   */
  TimeReference getTimeReference();

  /**
   * Executor to use for this cache instance.
   *
   * @throws IllegalStateException if this method is called within the supplier of
   *                               the time reference or executor.
   */
  Executor getExecutor();

  /**
   * The effective cache configuration. The data is only valid within the call.
   * Customizations must copy the relevant configuration parameters and not hold a
   * reference to the configuration object.
   */
  Cache2kConfig<K, V> getConfig();

  <T> T createCustomization(CustomizationSupplier<T> supplier);

}
