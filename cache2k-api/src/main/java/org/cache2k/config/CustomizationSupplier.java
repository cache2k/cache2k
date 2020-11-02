package org.cache2k.config;

/*
 * #%L
 * cache2k API
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

import java.io.Serializable;

/**
 * Supplies a cache customizations like {@code ExpiryPolicy} or {@code CacheLoader}.
 * An implementation must implement proper a {@code hashCode} and {@code equals} method.
 *
 * <p>Implementations of this class that should be usable within the XML configuration
 * need to implement {@link Serializable}. This is not done so in this interface since
 * there may be suppliers which are used within the program that are intentionally not
 * serializable, such as {@link CustomizationReferenceSupplier}.
 *
 * @param <T> the type for the customization. Typically extends
 *           {@link org.cache2k.Customization}, but can be
 *           {@link java.util.concurrent.Executor} as well
 * @author Jens Wilke
 */
  @FunctionalInterface
public interface CustomizationSupplier<T> {

  /**
   * Create or return an existing customization instance.
   *
   * @return created customization, never {@code null}
   */
  T supply(CacheBuildContext<?, ?> buildContext);

}
