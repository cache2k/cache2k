package org.cache2k.core.storageApi;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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

import org.cache2k.core.CacheInternalError;
import org.cache2k.core.CacheStorageException;
import org.cache2k.core.Entry;

import java.util.Iterator;
import java.util.concurrent.Future;

/**
* @author Jens Wilke; created: 2014-05-08
*/
public abstract class StorageAdapter {

  public abstract void open();

  /**
   * Cancel all schedules timer jobs in the storage.
   */
  public abstract Future<Void> cancelTimerJobs();

  public abstract Future<Void> shutdown();
  public abstract void flush();
  public abstract void purge();

  public abstract void clear();

  public abstract boolean checkStorageStillDisconnectedForClear();
  public abstract void disconnectStorageForClear();

  /** Starts the parallel clearing process, returns immediately */
  public abstract Future<Void> clearAndReconnect();

  /**
   *
   * @param _nextRefreshTime value expiry time in millis, 0: expire immediately, {@link Long#MAX_VALUE}: no expiry
   */
  public abstract void put(Entry e, long _nextRefreshTime);
  public abstract StorageEntry get(Object key);
  public abstract boolean remove(Object key);
  public abstract void evict(Entry e);

  public abstract void expire(Entry e);
  public abstract Iterator<Entry> iterateAll();

  /**
   * Return the total number of entries within the heap and
   * the storage. Should apply simple calculations to give and exact
   * number. No heavy operation e.g. checking for duplicates.
   */
  public abstract long getTotalEntryCount();

  /** 0 means no alert, 1 orange, 2, red alert */
  public abstract int getAlert();
  public abstract void disable(Throwable t);

  /** Implemented by a storage user, a cache or aggregator */
  public interface Parent {

    /** Change the storage implementation to another one or null for a disconnect */
    void resetStorage(StorageAdapter _current, StorageAdapter _new);

  }

  protected static Throwable buildThrowable(String txt, Throwable ex) {
    if (ex instanceof Error || ex.getCause() instanceof Error) {
      return new CacheInternalError(txt, ex);
    }
    return new CacheStorageException(txt, ex);
  }

  public static void rethrow(String txt, Throwable ex) {
    if (ex instanceof Error || ex.getCause() instanceof Error) {
      throw new CacheInternalError(txt, ex);
    }
    throw new CacheStorageException(txt, ex);
  }

}
