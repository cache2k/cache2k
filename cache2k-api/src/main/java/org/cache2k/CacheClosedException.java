package org.cache2k;

/*-
 * #%L
 * cache2k API
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

/**
 * Consistently this exception is thrown, when an operation detects that the
 * cache is closed.
 *
 * <p>Rationale: It is a subtype of {@link java.lang.IllegalStateException}
 * and not a {@link org.cache2k.CacheException} since the JSR107 uses it
 * and it makes sense to logically a specialisation of it.
 *
 * @author Jens Wilke
 */
public class CacheClosedException extends IllegalStateException {

  public CacheClosedException() { }

  /**
   * This is the preferred constructor. Extracts the cache name and the manager name
   * to be more informative.
   */
  public CacheClosedException(Cache<?, ?> cache) {
    super(qualifiedName(cache));
  }

  private static String qualifiedName(Cache cache) {
    if (cache.getCacheManager().isDefaultManager()) {
      return cache.getName();
    }
    return cache.getName() + ", manager=" + cache.getCacheManager().getName();
  }

}
