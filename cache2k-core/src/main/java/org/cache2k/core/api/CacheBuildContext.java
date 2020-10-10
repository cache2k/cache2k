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

import org.cache2k.CacheManager;
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.configuration.CustomizationSupplier;

/**
 * Context information when a cache is build.
 *
 * @author Jens Wilke
 */
public interface CacheBuildContext<K, V> {

  /**
   * The time reference for the cache.
   */
  InternalClock getClock();

  /**
   * Cache configuration.
   */
  Cache2kConfiguration<K, V> getConfiguration();

  /**
   * The cache manager.
   */
  CacheManager getCacheManager();

  /**
   * Create the customization
   */
  <T> T createCustomization(CustomizationSupplier<T> supplier);

  /**
   * Create the customization. Return fallback if supplier is null.
   */
  <T> T createCustomization(CustomizationSupplier<T> supplier, T fallback);

}
