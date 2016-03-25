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

import java.util.List;

/**
 * @author Jens Wilke; created: 2014-03-18
 * @deprecated use {@link org.cache2k.integration.CacheLoader}
 */
public interface BulkCacheSource<K, V> {

  /**
   * Retrieve the values for the given cache entries. The cache
   * entry list contains all keys for the entries to retrieve.
   * If an exception is thrown this may affect all entries, that
   * have currently no valid or expired data.
   *
   * <p/>The entry key is never null. Returned list must be
   * of identical length then entries list.
   *
   * @param entries list of entries / keys we want the data for
   * @param currentTime time in millis just before the call to this method
   * @return
   * @throws Throwable
   */
  public List<V> getValues(
    List<CacheEntry<K, V>> entries,
    long currentTime) throws Throwable;

}
