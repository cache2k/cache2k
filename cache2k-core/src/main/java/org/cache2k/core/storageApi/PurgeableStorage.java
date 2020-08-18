package org.cache2k.core.storageApi;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
   * <p>The storage implementation may choose to implement only one or no
   * expiry variant by time.
   *
   * @param ctx Provides a multi-threaded context. Thread resources for purge
   *            operations may be more limited or may have lower priority.
   * @param valueExpiryTime request to remove entries with lower value of
   *           {@link StorageEntry#getValueExpiryTime()}
   * @param entryExpiryTime request to remove entries with with lower value of
   *           {@link StorageEntry#getEntryExpiryTime()}
   * @return statistical result of the operation, if nothing was done null.
   */
  PurgeResult purge(PurgeContext ctx, long valueExpiryTime, long entryExpiryTime) throws Exception;

  interface PurgeContext extends CacheStorage.MultiThreadedContext {

    /**
     * Runs the action under the entry lock for the key. The actual purge
     * needs to be done within the runnable to avoid races. This is important
     * if the storage relies on the entry locking of the cache and has no
     * locking for the entry I/O itself.
     */
    void lockAndRun(Object key, PurgeAction action);

  }

  /**
   * Statistics
   */
  interface PurgeResult {

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

  interface PurgeAction {

    /**
     * Check storage whether entry for the key is still expired. If yes,
     * remove it. Otherwise the entry is returned.
     */
    StorageEntry checkAndPurge(Object key);

  }
}
