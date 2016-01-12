package org.cache2k;

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

/**
 * Interface to add to a value object if it is possible to derive the
 * expiry time from the value. If no explicit expiry calculator is set
 * and this interface is detected on the value, the expiry requested
 * from the value by the cache.
 *
 * @author Jens Wilke; created: 2014-10-15
 * @since 0.20
 */
public interface ValueWithExpiryTime {

  /**
   * Instance of an expiry calculator that uses the expiry value from the value
   * object, or returns the maximum value, if the interface is not implemented
   * by the value. This is useful if a cache contains different types of objects
   * which may need expiry at a specified time or not.
   *
   * @since 0.20
   */
  public final EntryExpiryCalculator<?, ?> AUTO_EXPIRY = new EntryExpiryCalculator<Object, Object>() {
    @Override
    public long calculateExpiryTime(
        Object _key, Object _value, long _fetchTime,
        CacheEntry<Object, Object> _oldEntry) {
      if (_value instanceof ValueWithExpiryTime) {
        return ((ValueWithExpiryTime) _value).getCacheExpiryTime();
      }
      return Long.MAX_VALUE;
    }
  };

  /**
   * Return time of next refresh (expiry time). A return value of 0 means the
   * entry expires immediately, or is always fetched from the source. A return value of
   * {@link Long#MAX_VALUE} means there is no specific expiry time
   * known or needed. In this case a reasonable default can be assumed for
   * the expiry, the cache will use the configured expiry time.
   *
   * @since 0.20
   */
  long getCacheExpiryTime();

}
