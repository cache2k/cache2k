package org.cache2k;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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

import org.cache2k.integration.CacheLoaderException;
import org.cache2k.integration.ExceptionPropagator;

/**
 * Object representing a cache entry. With the cache entry, it can be
 * checked whether a mapping in the cache is present, even if the cache
 * contains {@code null} or contains an exception. The simple entry does not
 * provide a timestamp of the last modification, which allows the entry
 * to be retrieved at a lower cost.
 *
 * Entries can be retrieved by
 * {@link Cache#peekSimpleEntry(Object)} or {@link Cache#getSimpleEntry(Object)}.
 *
 * <p>After retrieved, the entry instance does not change its values, even
 * if the value for its key is updated in the cache.
 *
 * @author Jens Wilke
 * @see Cache#peekSimpleEntry(Object)
 * @see Cache#getSimpleEntry(Object)
 * @author Jens Wilke
 */
public interface SimpleCacheEntry<K,V> {

  /**
   * Key associated with this entry.
   */
  K getKey();

  /**
   * Value of the entry. The value may be {@code null} if permitted for this cache
   * via {@link Cache2kBuilder#permitNullValues(boolean)}. If the
   * entry had a loader exception which is not suppressed, this exception will be
   * propagated. This method does ignore a custom exception propagator set via
   * {@link Cache2kBuilder#exceptionPropagator(ExceptionPropagator)}
   *
   * @throws CacheLoaderException if the loading produced an exception .
   */
  V getValue();

  /**
   * The exception happened when the value was loaded and
   * the exception could not be suppressed. {@code null} if no exception
   * happened or it was suppressed. If {@code null} then {@link #getValue}
   * returns a value and does not throw an exception.
   */
  Throwable getException();

}
