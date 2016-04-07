package org.cache2k;

/*
 * #%L
 * cache2k API only package
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

/**
 * Object representing a cache entry. With the cache entry it can be
 * checked whether a mapping in the cache is present, even if the cache
 * holds null or an exception.
 *
 * <p>After retrieved the entry instance does not change the values, even
 * if the value for its key is updated in the cache.
 *
 * <p>Design remark: The cache is generally also aware of the time the
 * object will be refreshed next or when it expired. This is not exposed
 * to applications by intention.
 *
 * @author Jens Wilke; created: 2014-03-18
 */
public interface CacheEntry<K, T> {

  K getKey();

  T getValue();

  Throwable getException();

  /**
   * Time the entry was last updated either by a fetch via the CacheSource
   * or by a put. If the entry was never fetched yet 0 is returned.
   */
  long getLastModification();

}
