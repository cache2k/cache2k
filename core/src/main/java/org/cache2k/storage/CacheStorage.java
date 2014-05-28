package org.cache2k.storage;

/*
 * #%L
 * cache2k core package
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

import java.io.Closeable;
import java.io.IOException;

/**
 * An interface for persistent or off-heap storage for a cache.
 *
 * @author Jens Wilke; created: 2014-04-03
 */
public interface CacheStorage extends Closeable {

  /**
   * Retrieve the entry from the storage. If there is no mapping for the
   * key, null is returned.
   */
  public StorageEntry get(Object key) throws IOException, ClassNotFoundException;

  public void put(StorageEntry e) throws IOException;

  public StorageEntry remove(Object key) throws IOException, ClassNotFoundException;

  public boolean contains(Object key) throws IOException;

  /**
   * Remove all entries from the cache and free resources.
   */
  public void clear() throws IOException;

  /**
   * Write everything  to disk and free all resources. Multiple calls
   * to close have no effect.
   */
  public void close() throws IOException;

}
