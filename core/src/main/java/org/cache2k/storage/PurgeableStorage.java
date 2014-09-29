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

/**
 * @author Jens Wilke; created: 2014-06-21
 */
public interface PurgeableStorage {

  /**
   * Removes all entries which have an expiry time before or equal to the
   * given time. The time for the value expiry may be not identical to the
   * current time, if the cache wants to keep some entries that are recently
   * expired, e.g. if a CacheSource is present and a scheme like
   * if-modified-since is supported by it.
   *
   * <p/>The storage implementation may choose to implement only one or no
   * expiry variant by time.
   *
   * @param ctx Provides a multi-threaded context. Thread resources for purge
   *            operations may be more limited or may have lower priority.
   * @param _valueExpiryTime request to remove entries with lower value of
   *           {@link StorageEntry#getValueExpiryTime()}
   * @param _entryExpiryTime request to remove entries with with lower value of
   *           {@link StorageEntry#getEntryExpiryTime()}
   * @return statistical result of the operation, if nothing was done null.
   */
  public PurgeResult purge(PurgeContext ctx, long _valueExpiryTime, long _entryExpiryTime) throws Exception;

  public static interface PurgeContext extends CacheStorage.MultiThreadedContext {

    /**
     * Runs the action under the entry lock for the key. The actual purge
     * needs to be done within the runnable to avoid races. This is important
     * if the storage relies on the entry locking of the cache and has no
     * locking for the entry I/O itself.
     */
    void lockAndRun(Object key, Runnable _action);

  }

  /**
   * Statistics
   */
  public static interface PurgeResult {

    /**
     * Free space reclaimed. -1 if not supported
     */
    long getBytesFreed();

    /**
     * Number of entries purged
     */
    int getEntriesPurged();

    /**
     * Number of entries scanned. This may be less than the total number
     * of entries in the storage, if a partial purge is done.
     */
    int getEntriesScanned();

  }

}
