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
 * Retrieves or generates a value to load into the cache. The advanced loader interface
 * contains the current time and the previous cache entry. The previous cache entry
 * can be used for a more intelligent loading strategy, e.g. for HTTP based loading with
 * the <code>If-Modified-Since</code> header.
 *
 * @author Jens Wilke
 * @see CacheLoader
 * @since 0.24
 */
public abstract class AdvancedCacheLoader<K,V> {

  /**
   * Retrieves or generates data based on the key.
   *
   * @param key The non-null key to provide the value for.
   * @param currentTime Time in millis, retrieved before the call.
   * @param previousEntry entry currently in the cache, regardless whether expired or not.
   *                     There is no guarantee that an expired entry will be provided to the loader.
   *                     Depending und passed time and configuration expired entries may be purged.
   *                     Check the configuration parameters {@link CacheBuilder#keepDataAfterExpired(boolean)}
   *                      and {@link CacheBuilder#backgroundRefresh(boolean)}.
   * @return value to be associated with the key. If the cache permits null values
   *         a null is associated with the key.
   * @throws Exception Unhandled exception from the loader. The exception will be
   *         handled by the cache based on its configuration.
   */
  public abstract V load(K key, long currentTime, CacheEntry<K,V> previousEntry) throws Exception;

}
