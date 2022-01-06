package org.cache2k.core.api;

/*-
 * #%L
 * cache2k core implementation
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

import org.cache2k.CacheManager;

/**
 * Used to know cache and manager name during close for logging and throwing exceptions.
 * Also provided {@link #closeCustomization(Object, String)} to optionally close
 * a customization. Components using this implement {@link NeedsClose}
 *
 * @see NeedsClose
 * @author Jens Wilke
 */
public interface InternalCacheCloseContext {

  /**
   * The cache name
   */
  String getName();

  /**
   * The cache manager
   */
  CacheManager getCacheManager();

  /**
   * Call close on the customization if the {@link AutoCloseable} interface
   * is implemented
   *
   * @param name Name of customization like "expiryPolicy". This is used when an exception
   *             happens upon close.
   */
  void closeCustomization(Object customization, String name);

}
