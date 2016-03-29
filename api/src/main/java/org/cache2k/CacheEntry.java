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
 * Object representing a cache entry. With the cache entry, it can be
 * checked whether a mapping in the cache is present, even if the cache
 * holds null or an exception. Entries can be retrieved by
 * {@link Cache#peekEntry(Object)} or {@link Cache#getEntry(Object)} or
 * via {@link Cache#iterator()}.
 *
 * <p>After retrieved, the entry instance does not change its values, even
 * if the value for its key is updated in the cache.
 *
 * <p>Design note: The cache is generally also aware of the time the
 * object will be refreshed next or when it will expire. This is not exposed
 * to applications by intention.
 *
 * @author Jens Wilke; created: 2014-03-18
 * @see Cache#peekEntry(Object)
 * @see Cache#getEntry(Object)
 * @see Cache#iterator()
 */
public interface CacheEntry<K, V> {

  /**
   * Key associated with this entry.
   */
  K getKey();

  /**
   * Value of the entry. The value may be null if nulls are allowed, or,
   * if an exception was thrown by the loader.
   */
  V getValue();

  /**
   * If not null a exception happened when the value was loaded and
   * the exception cannot be suppressed.
   */
  Throwable getException();

  /**
   * Time in millis the entry was last updated either by loading
   * or by a put.
   */
  long getLastModification();

}
