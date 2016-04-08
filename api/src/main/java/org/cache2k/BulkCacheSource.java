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

import java.util.List;

/**
 * @author Jens Wilke; created: 2014-03-18
 * @deprecated use {@link org.cache2k.integration.CacheLoader}
 */
public interface BulkCacheSource<K, V> {

  /**
   * Retrieve the values for the given cache entries. The cache
   * entry list contains all keys for the entries to retrieve.
   * If an exception is thrown this may affect all entries, that
   * have currently no valid or expired data.
   *
   * <p/>The entry key is never null. Returned list must be
   * of identical length then entries list.
   *
   * @param entries list of entries / keys we want the data for
   * @param currentTime time in millis just before the call to this method
   * @throws Throwable
   */
  List<V> getValues(
    List<CacheEntry<K, V>> entries,
    long currentTime) throws Throwable;

}
