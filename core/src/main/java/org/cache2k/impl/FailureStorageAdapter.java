package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.ClosableIterator;
import org.cache2k.impl.threading.Futures;
import org.cache2k.storage.StorageEntry;

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
  public void put(Entry e, long _nextRefreshTime) {
    throwException();

  }

  @Override
  public StorageEntry get(Object key) {
    throwException();
    return null;
  }

  @Override
  public boolean remove(Object key) {
    throwException();
    return false;
  }

  @Override
  public void evict(Entry e) {
    throwException();

  }

  @Override
  public void expire(Entry e) {
    throwException();

  }

  @Override
  public ClosableIterator<Entry> iterateAll() {
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
