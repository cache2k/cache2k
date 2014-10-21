package org.cache2k;

/*
 * #%L
 * cache2k API only package
 * %%
 * Copyright (C) 2000 - 2014 headissue GmbH, Munich
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
