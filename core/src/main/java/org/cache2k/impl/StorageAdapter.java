package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2015 headissue GmbH, Munich
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

import org.cache2k.ClosableIterator;
import org.cache2k.storage.StorageEntry;

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
  public abstract boolean checkStorageStillDisconnectedForClear();
  public abstract void disconnectStorageForClear();

  /** Starts the parallel clearing process, returns immediatly */
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
  public abstract ClosableIterator<Entry> iterateAll();

  /**
   * Return the total number of entries within the heap and
   * the storage. Should apply simple calculations to give and exact
   * number. No heavy operation e.g. checking for duplicates.
   *
   * @see org.cache2k.Cache#getTotalEntryCount()
   */
  public abstract int getTotalEntryCount();

  /** 0 means no alert, 1 orange, 2, red alert */
  public abstract int getAlert();
  public abstract void disable(Throwable t);

  /** Implemented by a storage user, a cache or aggregator */
  static interface Parent {

    /** Change the storage implementation to another one or null for a disconnect */
    void resetStorage(StorageAdapter _current, StorageAdapter _new);

  }

  protected static Throwable buildThrowable(String txt, Throwable ex) {
    if (ex instanceof Error || ex.getCause() instanceof Error) {
      return new CacheInternalError(txt, ex);
    }
    return new CacheStorageException(txt, ex);
  }

  protected static void rethrow(String txt, Throwable ex) {
    if (ex instanceof Error || ex.getCause() instanceof Error) {
      throw new CacheInternalError(txt, ex);
    }
    throw new CacheStorageException(txt, ex);
  }

}
