package org.cache2k.test.core;

/*
 * #%L
 * cache2k core package
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
import org.cache2k.CacheEntry;
import org.cache2k.event.CacheEntryRemovedListener;
import org.cache2k.impl.InternalCache;
import org.cache2k.impl.InternalCacheInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Jens Wilke
 */
public class StaticUtil {

  public static <T> Set<T> asSet(T... keys) {
    return new HashSet<T>(Arrays.asList(keys));
  }

  public static InternalCacheInfo latestInfo(Cache c) {
    return ((InternalCache) c).getLatestInfo();
  }

  /**
   * Enforce the usage of wired cache by registering a dummy listener.
   */
  @SuppressWarnings("unchecked")
  public static <K,V> Cache2kBuilder<K,V> enforceWiredCache(Cache2kBuilder<K,V> b) {
    return b.addListener(new CacheEntryRemovedListener() {
      @Override
      public void onEntryRemoved(final Cache c, final CacheEntry entry) {

      }
    });
  }

}
