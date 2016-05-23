package org.cache2k.expiry;

/*
 * #%L
 * cache2k API
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;

import java.util.concurrent.TimeUnit;

/**
 * A custom policy which allows to calculate a dynamic expiry time for an entry after an
 * insert or update.
 *
 * <p>For some expiry calculations it is useful to know the previous entry, e.g. to detect
 * whether the stored data was really updated. If a previous value is present in the cache,
 * it is passed to this method. Expired old entries will be passed in also, if still present
 * in the cache.
 *
 *
 *
 * @author Jens Wilke; created: 2014-10-14
 * @since 0.20
 */
public interface ExpiryPolicy<K, V> extends ExpiryTimeValues {

  /**
   * Returns the time of expiry in milliseconds since epoch.
   *
   * <p>By default expiry itself happens lenient, zero or some milliseconds after
   * the returned value. If sharp expiry is requested, the value will not be
   * returned any more by the cache when the point in time is reached.
   * The cache parameters {@link Cache2kBuilder#sharpExpiry(boolean)}
   * and {@link Cache2kBuilder#refreshAhead(boolean)} influence the behaviour.
   *
   * <p><b>Inserts or updates:</b> It is possible to return different expiry times for
   * inserts or updates. An update can be detected by the presence of the old entry.
   *
   * <p><b>Calling cache operations:</b> It is illegal to call any
   * cache methods from this method. This may have an undesired effect
   * and can cause a deadlock.
   *
   * <p><b>null values:</b></p> If the loader returns a null value, the expiry
   * policy will be called, regardless of the {@link Cache2kBuilder#permitNullValues} setting.
   * If the expiry policy returns a {@link #NO_CACHE} the entry will be removed. If the expiry
   * policy returns a different time value, a {@code NullPointerException} will be propagated
   * if null values not permitted.
   *
   * @param key the cache key used for inserting or loading
   * @param value the value to be cached. May be null if the loader returns null, regardless of the
   *               {@link Cache2kBuilder#permitNullValues} setting.
   * @param loadTime The time the entry was inserted or loaded. If a loader was used,
   *                 this is the time before the loader was called.
   * @param oldEntry entry representing the current mapping, if there is a value present.
   *                  If the current entry holds an exception, this is null. Expired entries will be
   *                 passed in as well.
   * @return time the time of expiry in millis since epoch. {@link #NO_CACHE} if it should not cached.
   *         If {@link Cache2kBuilder#refreshAhead} is enabled the return value {@link #NO_CACHE} will remove
   *         the entry from the cache and trigger a immediate refresh, while other return values will not remove
   *         the entry. The return value {@link #ETERNAL} means that there is no specific expiry time known or needed.
   *         The effective expiry duration will never be longer than the
   *         configured expiry value via {@link Cache2kBuilder#expireAfterWrite(long, TimeUnit)}.
   *         If a negative value is returned, the negated value will be the expiry time
   *         used, but sharp expiry is requested always,
   *         ignoring {@link Cache2kBuilder#sharpExpiry(boolean)}.
   *
   * @see ValueWithExpiryTime#getCacheExpiryTime()
   */
  long calculateExpiryTime(
      K key,
      V value,
      long loadTime,
      CacheEntry<K, V> oldEntry);

}
