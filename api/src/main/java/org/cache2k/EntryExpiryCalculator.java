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
 * A custom policy which allows to calculate a specific expiry time per entry.
 *
 * @author Jens Wilke; created: 2014-10-14
 * @since 0.20
 */
public interface EntryExpiryCalculator<K, T> {

  /**
   * Returns the time of expiry in milliseconds since epoch.
   * If 0 is returned, this means entry expires immediately, or is always
   * fetched from the source. If {@link Long#MAX_VALUE} is returned it means
   * there is no specific expiry time known or needed. In any case the effective
   * expiry duration will never be longer than the configured expiry
   *
   * <p>The cache may call the method multiple times after an entry is inserted,
   * when the expiry time needs a recalculation. The reason for this is to react on
   * possible configuration changes properly. This may happen when an entry
   * is read back from storage.
   * </p>
   *
   * @param _key the cache key
   * @param _value the value to be cached, may be null
   * @param _fetchTime this is the current time in millis. If a cache source was used to
   *                   fetch the value, this is the time before the fetch was started.
   * @param _oldEntry entry representing the current mapping, if there is a value present.
   *                  If the current entry holds an exception, this is null.
   *
   * @return time the time of expiry in millis since epoch. 0 if it should not be cached.
   *              By default expiry itself happens lenient, zero or some milliseconds after
   *              the returned value. If sharp expiry is requested, the value will not be
   *              returned any more by the cache when the point in time is reached.
   *              The cache parameters {@link org.cache2k.CacheBuilder#sharpExpiry(boolean)}
   *              and {@link org.cache2k.CacheBuilder#backgroundRefresh(boolean)} influence the behaviour.
   *              If a negated value of the expiry time is returned, this means that sharp expiry is
   *              requested explicitly.
   */
  long calculateExpiryTime(
      K _key,
      T _value,
      long _fetchTime,
      CacheEntry<K, T> _oldEntry);

}
