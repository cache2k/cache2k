package org.cache2k.testsuite.support;

/*
 * #%L
 * cache2k testsuite on public API
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.event.CacheEntryRemovedListener;

/**
 * @author Jens Wilke
 */
public class StaticUtil {

  /**
   * Enforce the usage of wired cache by registering a dummy listener.
   */
  public static <K, V> Cache2kBuilder<K, V> enforceWiredCache(Cache2kBuilder<K, V> b) {
    return b.addListener((CacheEntryRemovedListener<K, V>) (cache, entry) -> { });
  }

  public static int count(Iterable<?> iterable) {
    int count = 0;
    for (Object ignored : iterable) {
      count++;
    }
    return count;
  }

}
