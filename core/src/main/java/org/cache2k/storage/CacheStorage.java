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

import org.cache2k.StorageConfiguration;

import java.io.Closeable;
import java.io.IOException;

/**
 * An interface for persistent or off-heap storage for a cache.
 *
 * @author Jens Wilke; created: 2014-04-03
 */
public interface CacheStorage extends Closeable {

  public void open(CacheStorageContext ctx, StorageConfiguration cfg) throws IOException;

  /**
   * Retrieve the entry from the storage. If there is no mapping for the
   * key, null is returned.
   *
   * <p>An exception on get is not severe. The cache will try other sources or
   * return null.
   */
  public StorageEntry get(Object key) throws IOException, ClassNotFoundException;

  /**
   * Stores the entry in the storage. The entry instance is solely for transferring
   * the data, no reference may be hold within the storage to it. The callee takes
   * care that the entry data is consistent during the put method call.
   *
   * <p>An exception on put is severe. The storage will be disconnected from the cache.
   *
   * @return true, if this entry was present in the storage before and got overwritten
   * @throws IOException may be thrown if hope is lost
   */
  public void put(StorageEntry e) throws IOException, ClassNotFoundException;

  public StorageEntry remove(Object key) throws IOException, ClassNotFoundException;

  /**
   * Returns true if there is a mapping for the key.
   *
   * <p>An exception on contains is not severe. The cache will try other sources or
   * return null.
   */
  public boolean contains(Object key) throws IOException;

  /**
   * Remove all entries from the cache and free resources. This operation is called
   * when there is no other operation concurrently executed on this storage instance.
   *
   * <p/>When a Cache.clear() is initiated there is no obligation to send a
   * CacheStorage.clear() to the persisted storage. Alternatively, all objects can
   * be removed via remove().
   */
  public void clear() throws IOException;

  /**
   * Flush any unwritten information to disk. This is a long running operation
   * in the current thread. There are concurrent read and write going on.
   * The flush thread may be interrupted and should check its interrupt
   * status at save points.
   */
  public void flush(long now, FlushContext ctx) throws Exception;

  /**
   * Write everything to disk and free all in-memory resources. Multiple calls
   * to close have no effect.
   */
  public void close() throws IOException;

  /**
   * Called by the cache at regular intervals, but only if data was
   * changed.
   */

  /**
   * Called by the cache to remove expired entries.
   */

  /**
   * Sets the maximum number of entries in the storage.
   * This configuration maybe called during runtime. If the
   * size exceeds the capacity entries are evicted.
   */
  public void setEntryCapacity(int v);

  /**
   * Storage capacity in bytes used on the storage media. There
   * is no exact guarantee that the storage implementation will met this
   * constraint exactly.
   */
  public void setBytesCapacity(long v);

  public int getEntryCapacity();

  public int getEntryCount();

  public static interface FlushContext {

  }

  public static interface PurgeContext {

  }

  public static final FlushContext DEFAULT_FLUSH_CONTEXT = new FlushContext() { };

}
