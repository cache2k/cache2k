package org.cache2k.configuration;

/*
 * #%L
 * cache2k API
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

import org.cache2k.CacheManager;

/**
 * Supplies a cache customizations like {@code ExpiryPolicy} or {@code CacheLoader}.
 * An implementation must implement proper a {@code hashCode} and {@code equals} method.
 *
 * @author Jens Wilke
 */
public interface CustomizationSupplier<T> {

  /**
   * Create or return an existing customization instance.
   *
   * @param manager The manager can be used to retrieve the class loader or
   *                additional properties needed to configure the customization.
   * @throws Exception the method may throw arbitrary exceptions. The exception
   *                   is wrapped into a {@link org.cache2k.CacheException} and
   *                   rethrown. When the creation of a customization fails, the
   *                   cache will not be usable.
   */
  T supply(CacheManager manager) throws Exception;

}
