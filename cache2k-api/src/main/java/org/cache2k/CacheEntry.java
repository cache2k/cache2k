package org.cache2k;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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
import org.cache2k.processor.MutableCacheEntry;

/**
 * Object representing a cache entry. With the cache entry, it can be
 * checked whether a mapping in the cache is present, even if the cache
 * contains {@code null} or contains an exception. Entries can be retrieved by
 * {@link Cache#peekEntry(Object)} or {@link Cache#getEntry(Object)} or
 * via iterated via {@link Cache#entries()}.
 *
 * <p>After retrieved, the entry instance does not change its values, even
 * if the value for its key is updated in the cache.
 *
 * <p>Design note: The cache is generally also aware of the time the
 * object will be refreshed next or when it will expire. This is not exposed
 * to applications by intention.
 *
 * @author Jens Wilke
 * @see Cache#peekEntry(Object)
 * @see Cache#getEntry(Object)
 * @see Cache#entries()
 */
public interface CacheEntry<K, V> {

  /**
   * Key associated with this entry.
   */
  K getKey();

  /**
   * Value of the entry. The value may be {@code null} if permitted for this cache
   * via {@link Cache2kBuilder#permitNullValues(boolean)}. If the
   * entry had a loader exception which is not suppressed, this exception will be
   * propagated. This can be customized with
   * {@link Cache2kBuilder#exceptionPropagator(ExceptionPropagator)}
   *
   * <p>For usage within the {@link org.cache2k.processor.EntryProcessor}:
   * If a loader is present and the entry is not yet loaded or expired, a
   * load is triggered. See the details at: {@link MutableCacheEntry#getValue()}
   *
   * @throws CacheLoaderException if loading produced an exception
   */
  V getValue();

  /**
   * The exception happened when the value was loaded and
   * the exception could not be suppressed. {@code null} if no exception
   * happened or it was suppressed. If {@code null} then {@link #getValue}
   * returns a value and does not throw an exception.
   */
  Throwable getException();

  /**
   * Permanently throws {@link UnsupportedOperationException}.
   *
   * <p>Functionality was present in version 1.0, but was removed
   * for version 1.2. To access the last modification time it is possible to
   * use {@link MutableCacheEntry#getRefreshedTime()}
   *
   * <p>More rationale see
   *  <a href="https://github.com/cache2k/cache2k/issues/84">GH#84</a>.
   * The method is planed to be removed for version 2.0.
   *
   * <p>{@link AbstractCacheEntry} can be used for implementations of this class to avoid implementing this method.
   *
   * @throws UnsupportedOperationException always thrown
   * @deprecated permanently not supported any more, you may use {@link MutableCacheEntry#getRefreshedTime()}
   */
  long getLastModification();

}
