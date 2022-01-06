package org.cache2k.jcache;

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

import org.cache2k.config.Cache2kConfig;

import javax.cache.configuration.CompleteConfiguration;

/**
 * Extends the JCache complete configuration with an additional cache2k configuration.
 *
 * @author Jens Wilke
 */
public interface ExtendedConfiguration<K, V> extends CompleteConfiguration<K, V> {

  /**
   * Retrieve the extended cache2k configuration.
   *
   * @return cache2k configuration or null, if not needed
   */
  Cache2kConfig<K, V> getCache2kConfiguration();

}
