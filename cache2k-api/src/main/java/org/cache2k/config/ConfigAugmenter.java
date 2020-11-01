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

import org.cache2k.Cache2kBuilder;
import org.cache2k.Customization;

/**
 * This is called when {@link Cache2kBuilder#build()} is called but before
 * the actual build is done. May inspect and modify configuration parameters.
 *
 * @author Jens Wilke
 */
public interface ConfigAugmenter<K, V> extends Customization<K, V> {

  /**
   * Inspect and change the cache configuration.
   *
   * @param context The build context
   * @param config The configuration, same as {@link CacheBuildContext#getConfiguration()}
   *               but with generic type information
   */
  void augment(CacheBuildContext context, Cache2kConfig<K, V> config);

}
