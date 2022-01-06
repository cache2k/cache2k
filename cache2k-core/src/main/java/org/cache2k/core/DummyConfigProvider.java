package org.cache2k.core;

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
import org.cache2k.config.Cache2kConfig;
import org.cache2k.core.spi.CacheConfigProvider;

import java.util.Collections;

/**
 * The configuration via XML can be removed (e.g. via ProGuard in Android environments).
 * This is a dummy placeholder.
 *
 * @author Jens Wilke
 */
public class DummyConfigProvider implements CacheConfigProvider {

  @Override
  public String getDefaultManagerName(ClassLoader classLoader) {
    return null;
  }

  @Override
  public Cache2kConfig getDefaultConfig(CacheManager mgr) {
    return new Cache2kConfig();
  }

  @Override
  public <K, V> void augmentConfig(CacheManager mgr, Cache2kConfig<K, V> cfg) { }

  @Override
  public Iterable<String> getConfiguredCacheNames(CacheManager mgr) {
    return Collections.emptyList();
  }

}
