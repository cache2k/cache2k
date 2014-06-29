package org.cache2k.impl;

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

import org.cache2k.storage.StorageEntry;

import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Jens Wilke; created: 2014-06-02
 */
public class NoopStorageAdapter extends StorageAdapter {

  @Override
  public void open() {
  }

  @Override
  public void shutdown() {
  }

  @Override
  public Future<Void> checkStorageStillUnconnectedForClear() {
    return null;
  }

  @Override
  public void disconnectStorageForClear() {

  }

  @Override
  public Future<Void> clearWithoutOngoingEntryOperations() {
    return new Future<Void>() {
      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
      }

      @Override
      public boolean isCancelled() {
        return false;
      }

      @Override
      public boolean isDone() {
        return true;
      }

      @Override
      public Void get() throws InterruptedException, ExecutionException {
        return null;
      }

      @Override
      public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return null;
      }
    };
  }

  @Override
  public void put(BaseCache.Entry e) {

  }

  @Override
  public StorageEntry get(Object key) {
    return null;
  }

  @Override
  public void remove(Object key) {

  }

  @Override
  public void evict(BaseCache.Entry e) {

  }

  @Override
  public void expire(BaseCache.Entry e) {

  }

  @Override
  public void disableOnFailure(Throwable t) {

  }

  @Override
  public Iterator<BaseCache.Entry> iterateAll() {
    return null;
  }

  @Override
  public int getTotalEntryCount() {
    return 0;
  }

  @Override
  public int getAlert() {
    return 0;
  }
}
