package org.cache2k.customization;

/*
 * #%L
 * cache2k API only package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import org.cache2k.CacheEntry;

/**
 * A custom policy which allows to calculate a dynamic expiry time for an entry after an
 * insert or update.
 *
 * <p>For some expiry calculations it is useful to know the previous entry, e.g. to detect
 * whether the stored data was really updated. If a previous value is present in the cache,
 * it is passed to this method. Expired old entries will be passed in also, if still present
 * in the cache.
 *
 * @author Jens Wilke; created: 2014-10-14
 * @since 0.20
 */
public interface ExpiryCalculator<K, V> {

  /**
   * Return value used to signal that the value should not be cached. In a read through
   * configuration the value will be loaded, when it is requested again.
   */
  long NO_CACHE = 0;

  /**
   * Return value signalling to keep the value forever in the cache, switching off expiry.
   * If the cache has a static expiry time configured, then this is used instead.
   */
  long ETERNAL = Long.MAX_VALUE;

  /**
   * Returns the time of expiry in milliseconds since epoch.
   *
   * <p>By default expiry itself happens lenient, zero or some milliseconds after
   * the returned value. If sharp expiry is requested, the value will not be
   * returned any more by the cache when the point in time is reached.
   * The cache parameters {@link org.cache2k.CacheBuilder#sharpExpiry(boolean)}
   * and {@link org.cache2k.CacheBuilder#backgroundRefresh(boolean)} influence the behaviour.
   *
   * <p><b>Inserts or updates:</b> It is possible to return different expiry times for
   * insert or updates. An update can be detected by the presence of the old entry.
   *
   * <p>The cache may call the method multiple times after an entry is inserted to
   * reflect possible configuration changes.
   *
   * <p><b>Mutation of values:</b> Mutating values within the expiry calculator may have undesired
   * effects and is not supported in general.
   *
   * <p><b>Cache access:</b> It is illegal to access the cache inside the method. Doing so, may
   * result in a deadlock.
   *
   * @param key the cache key used for inserting or loading
   * @param value the value to be cached, may be null
   * @param loadTime The time the entry was inserted or loaded. If a loader was used,
   *                 this is the time before the loader was called.
   * @param oldEntry entry representing the current mapping, if there is a value present.
   *                  If the current entry holds an exception, this is null. Expired entries will be
   *                  also passed.
   * @return time the time of expiry in millis since epoch. {@link #NO_CACHE} if it should not be cached.
   *              {@link #ETERNAL} means there is no specific expiry time known or needed.
   *              In any case the effective expiry duration will never be longer than the
   *              configured expiry value. If a negated value of the expiry time is returned,
   *              this means that sharp expiry is requested explicitly.
   *
   * @see ExceptionExpiryCalculator#calculateExpiryTime(Object, Throwable, long)
   */
  long calculateExpiryTime(
      K key,
      V value,
      long loadTime,
      CacheEntry<K, V> oldEntry);

}
