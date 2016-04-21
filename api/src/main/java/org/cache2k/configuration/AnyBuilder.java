package org.cache2k.configuration;

/*
 * #%L
 * cache2k API
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
import org.cache2k.Cache2kBuilder;
import org.cache2k.configuration.CacheConfiguration;

/**
 *
 *
 * B type of the root builder
 * P parent builder
 *
 */
public interface AnyBuilder<K, T, C> {

  /**
   * Create the configuration of this submodule (not the complete
   * configuration).
   */
  C createConfiguration();

  /**
   * Goes back to the root builder, to add more configuration nodes.
   */
  Cache2kBuilder<K, T> root();

  /**
   * Builds the instance which is the target of nested builders. This
   * is either a {@link Cache} or a {@link CacheConfiguration}. The method is
   * always identical to root().build().
   */
  Cache<K, T> build();

}
