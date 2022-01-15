package org.cache2k.extra.spring;

/*-
 * #%L
 * cache2k Spring framework support
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheManager;

import java.util.function.Supplier;

/**
 * Defines the defaults of cache2k configuration within the Spring or Spring Boot context.
 * If the CacheManager is created via Spring Boot, providing the a
 * {@link SpringCache2kDefaultSupplier} can be used to supply a default configuration.
 *
 * @author Jens Wilke
 */
@FunctionalInterface
public interface SpringCache2kDefaultSupplier extends Supplier<Cache2kBuilder<?, ?>> {

  /**
   * Construct a supplier with defaults and applying the customizer.
   */
  static SpringCache2kDefaultSupplier of(SpringCache2kDefaultCustomizer customizer) {
    return () -> {
      Cache2kBuilder<?, ?> builder = supplyDefaultBuilder();
      customizer.customize(builder);
      return builder;
    };
  }

  /**
   * Default name used for the cache2k cache manager controlled by the spring cache
   * manager.
   */
  String DEFAULT_SPRING_CACHE_MANAGER_NAME = "springDefault";

  /**
   * Defaults for cache2k caches within Spring context.
   * Spring caches allow the storage of null values by default.
   */
  static void applySpringDefaults(Cache2kBuilder<?, ?> builder) {
    builder.permitNullValues(true);
  }

  /**
   * Supplies a builder bound to the default manager and with Spring defaults applied.
   */
  static Cache2kBuilder<?, ?> supplyDefaultBuilder() {
    return
      Cache2kBuilder.forUnknownTypes()
        .manager(CacheManager.getInstance(DEFAULT_SPRING_CACHE_MANAGER_NAME))
        .setup(SpringCache2kDefaultSupplier::applySpringDefaults);
  }

}
