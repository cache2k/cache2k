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

import org.cache2k.ClosableIterator;
import org.cache2k.impl.threading.Futures;
import org.cache2k.storage.StorageEntry;

import java.util.Iterator;
import java.util.concurrent.Future;

/**
 * @author Jens Wilke; created: 2014-06-02
 */
public class FailureStorageAdapter extends StorageAdapter {

  Throwable exception;

  public FailureStorageAdapter(Throwable exception) {
    this.exception = exception;
  }

  void throwException() {
    rethrow("failure in past", exception);
  }

  @Override
  public void open() {

  }

  @Override
  public void flush() {
    throwException();
  }

  @Override
  public void purge() {
    throwException();
  }

  @Override
  public boolean checkStorageStillDisconnectedForClear() {
    throwException();
    return false;
  }

  @Override
  public void disconnectStorageForClear() {
    throwException();
  }

  @Override
  public Future<Void> clearAndReconnect() {
    throwException();
    return null;
  }

  @Override
  public void put(BaseCache.Entry e) {
    throwException();

  }

  @Override
  public StorageEntry get(Object key) {
    throwException();
    return null;
  }

  @Override
  public void remove(Object key) {
    throwException();
  }

  @Override
  public void evict(BaseCache.Entry e) {
    throwException();

  }

  @Override
  public void expire(BaseCache.Entry e) {
    throwException();

  }

  @Override
  public ClosableIterator<BaseCache.Entry> iterateAll() {
    throwException();
    return null;
  }

  @Override
  public int getTotalEntryCount() {
    return 0;
  }

  @Override
  public int getAlert() {
    return 3;
  }

  @Override
  public void disable(Throwable t) {
  }

  @Override
  public Future<Void> cancelTimerJobs() {
    return new Futures.FinishedFuture<Void>();
  }

  @Override
  public Future<Void> shutdown() {
    return new Futures.ExceptionFuture<Void>(
      buildThrowable("shutdown impossible, exception in past", exception));
  }

}
