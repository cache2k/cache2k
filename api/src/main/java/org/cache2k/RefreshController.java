package org.cache2k;

/*
 * #%L
 * cache2k api only package
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
 * Calculates the time when the object needs to be updated next.
 * 
 * @author Jens Wilke; created: 2010-06-24
 */
public abstract interface RefreshController<T> {

  /**
   * Return time of next refresh (expiry time) in milliseconds since epoch.
   * If 0 is returned, this means entry expires immediately, or is always
   * fetched from the source. If {@link Long#MAX_VALUE} is returned it means
   * there is no specific expiry time known or needed. In this case a reasonable
   * default can be assumed for the expiry, the cache will use the
   * configured expiry time.
   *
   * @param _oldObject the value currently in the cache. null if it is not
   *                   in the cache, is a null value (null is supported for values)
   *                   or the previous fetch operation yielded in an exception.
   * @param _timeOfLastRefresh time of the last cache refresh, a put or a fetch
   *                           from the cache source.
   * @param _newObject the value which will be put in the cache.
   * @param now this is the current time in millis. If a cache source was used to
   *            fetch the value, this is the time before the fetch was started.
   * @return time of next refresh in millis. 0 if it should not be cached at all.
   */
  public abstract long calculateNextRefreshTime(
    T _oldObject,
    T _newObject,
    long _timeOfLastRefresh,
    long now);

}
