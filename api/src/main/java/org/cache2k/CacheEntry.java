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
 * Object representing a cache entry. With the cache entry it can be
 * checked whether a mapping in the cache is present, even if the cache
 * holds null or an exception.
 *
 * <p>After retrieved the entry instance does not change the values, even
 * if the value for its key is updated in the cache.
 *
 * <p>Design remark: The cache is generally also aware of the time the
 * object will be refreshed next or when it expired. This is not exposed
 * to applications by intention.
 *
 * @author Jens Wilke; created: 2014-03-18
 */
public interface CacheEntry<K, V> {

  K getKey();

  V getValue();

  Throwable getException();

  /**
   * Time in millis the entry was last updated either by a fetch via the CacheSource
   * or by a put. If the entry was never fetched 0 is returned.
   */
  long getLastModification();

}
