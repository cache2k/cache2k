package org.cache2k.expiry;

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

import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;

import java.util.concurrent.TimeUnit;

/**
 * A custom policy which allows to calculate a dynamic expiry time for an entry after an
 * insert or update.
 *
 * <p>For some expiry calculations it is useful to know the previous entry, e.g. to detect
 * whether the stored data was updated. If a previous value is present in the cache,
 * it is passed to this method. Expired old entries will be passed in also, if still present
 * in the cache.
 *
 * <p>The expiry policy is also used for refresh ahead, determining the time when an entry should
 * be automatically refreshed.
 *
 * @author Jens Wilke
 * @see <a href="https://cache2k.org/docs/latest/user-guide.html#expiry-and-refresh">
 *   cache2k user guide - Expiry and Refresh</a>
 * @see Cache2kBuilder#expiryPolicy(ExpiryPolicy)
 * @see Cache2kBuilder#refreshAhead(boolean)
 * @see Cache2kBuilder#expireAfterWrite(long, TimeUnit)
 */
public interface ExpiryPolicy<K, V> extends ExpiryTimeValues {

  /**
   * Returns the time of expiry in milliseconds since epoch.
   *
   * <p>By default expiry itself happens lenient, which means the expiry happens
   * zero or some milliseconds after the obtained time. If sharp expiry is requested,
   * the value will not be returned any more by the cache when the point in time is reached.
   * The cache parameters {@link Cache2kBuilder#sharpExpiry(boolean)}
   * and {@link Cache2kBuilder#refreshAhead(boolean)} influence the behaviour.
   * It is also possible to request a sharp timeout for some entries. This is done
   * by returning a negative time value, see the further comments for the return
   * value below.
   *
   * <p><b>Inserts or updates:</b> It is possible to return different expiry times for
   * inserts or updates. An update can be detected by the presence of the old entry.
   *
   * <p><b>Calling cache operations:</b> It is illegal to call any
   * cache methods from this method. The outcome is undefined and it can
   * cause a deadlock.
   *
   * <p><b>Calling time:</b></p>The method is called from the cache whenever a
   * cache entry is updated. However, it is legal that the cache calls the
   * method at arbitrary times during the entry lifecycle.
   *
   * <p><b>{@code null} values:</b> If the loader returns a {@code null} value, the expiry
   * policy will be called, regardless of the {@link Cache2kBuilder#permitNullValues} setting.
   * If the expiry policy returns a {@link #NOW} the entry will be removed. If the expiry
   * policy returns a different time value, a {@code NullPointerException} will be propagated
   * if {@code null} values are not permitted.
   *
   * <p><b>Loader exceptions</b></p> The expiry policy is only called for a successful load
   * operation.
   *
   * <p><b>API rationale:</b> The recently loaded or inserted data is not passed in via a cache
   * entry object. Using a cache entry is desirable for API design reasons to have less parameter.
   * But the "real" entry can only be filled after the expiry policy has been run, passing
   * in an entry object would mean to build a temporary object, increasing GC load. Second, the
   * properties that are needed by the implementation are available directly. The downside, OTOH,
   * 4-arity breaks Java 8 lambdas. <b>Time values:</b> For performance reasons the long type
   * is used to represent the time and not an object. Also it allows a simple offset calculation.
   *
   * @param key the cache key used for inserting or loading
   * @param value the value to be cached. May be {@code null} if the loader returns {@code null},
   *              regardless of the {@link Cache2kBuilder#permitNullValues} setting.
   * @param loadTime The time the entry was inserted or loaded. If a loader was used,
   *                 this is the time before the loader was called.
   * @param currentEntry entry representing the current mapping, if there is a value present.
   *                     {@code null} if there is no current mapping, or, if the previous load
   *                     operation had thrown an exception. This can be used for adapting the
   *                     expiry time to the amount of data changes.
   * @return the time of expiry in millis since epoch. {@link #NOW} if it should not
   *         cached. If {@link Cache2kBuilder#refreshAhead} is enabled the return value
   *         {@link #NOW} will trigger an immediate refresh.
   *         The return value {@link #ETERNAL} means that there is no specific expiry time
   *         known or needed. The effective expiry duration will never be longer than the configured
   *         expiry value via {@link Cache2kBuilder#expireAfterWrite(long, TimeUnit)}.
   *         If a negative value is returned, the negated value will be the expiry time
   *         used, but sharp expiry is requested. Use {@link Expiry#toSharpTime(long)} to have a
   *         more expressive code. Switching on
   *         {@link Cache2kBuilder#sharpExpiry(boolean)} means always sharp expiry.
   *
   * @see ValueWithExpiryTime#getCacheExpiryTime()
   */
  long calculateExpiryTime(
      K key,
      V value,
      long loadTime,
      CacheEntry<K, V> currentEntry);

}
