package org.cache2k.io;

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

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * @author Jens Wilke
 * @since 2
 */
public interface BulkCacheLoader<K, V> {

  /**
   * Loads multiple values to the cache.
   *
   * <p>From inside this method it is illegal to call methods on the same cache. This
   * may cause a deadlock.
   *
   * <p>The method is provided to complete the API. At the moment cache2k is not
   * using it. Please see the road map.
   *
   * @param keys set of keys for the values to be loaded
   * @param executor an executor for concurrent loading
   * @return The loaded values. A key may map to {@code null} if the cache permits
   *         {@code null} values.
   * @throws Exception Unhandled exception from the loader. Exceptions are suppressed or
   *                   wrapped and rethrown via a {@link CacheLoaderException}.
   *                   If an exception happens the cache may retry the load with the
   *                   single value load method.
   */
  Map<K, V> loadAll(Iterable<? extends K> keys, Executor executor) throws Exception;

}
