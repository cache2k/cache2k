package org.cache2k.extra.config.test;

/*-
 * #%L
 * cache2k config file support
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

import org.cache2k.Cache;
import org.cache2k.config.CacheBuildContext;
import org.cache2k.config.ToggleFeature;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Toggle feature for testing
 *
 * @author Jens Wilke
 */
public class InspectionFeature extends ToggleFeature {

  private static final Map<String, Map<String, Boolean>>
    MAP_MANAGER_CACHE_FLAG = new ConcurrentHashMap<>();

  public static boolean wasEnabled(Cache<?, ?> cache) {
    return
      MAP_MANAGER_CACHE_FLAG.getOrDefault(cache.getCacheManager().getName(), Collections.emptyMap())
        .getOrDefault(cache.getName(), false);
  }

  @Override
  protected <K, V> void doEnlist(CacheBuildContext<K, V> ctx) {
    MAP_MANAGER_CACHE_FLAG
      .computeIfAbsent(ctx.getCacheManager().getName(), s -> new ConcurrentHashMap<>())
      .put(ctx.getName(), true);
  }

}
