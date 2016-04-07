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
public class NoopStorageAdapter extends StorageAdapter {

  BaseCache cache;

  public NoopStorageAdapter(BaseCache cache) {
    this.cache = cache;
  }

  @Override
  public void open() {
  }

  @Override
  public Future<Void> cancelTimerJobs() {
    return null;
  }

  @Override
  public Future<Void> shutdown() {
   return new Futures.FinishedFuture<Void>();
  }

  @Override
  public void flush() { }

  @Override
  public void purge() { }

  @Override
  public boolean checkStorageStillDisconnectedForClear() {
    return true;
  }

  @Override
  public void disconnectStorageForClear() {

  }

  @Override
  public Future<Void> clearAndReconnect() {
    return new Futures.FinishedFuture<Void>(null);
  }

  @Override
  public void put(Entry e, long _nextRefreshTime) { }

  @Override
  public StorageEntry get(Object key) {
    return null;
  }

  @Override
  public boolean remove(Object key) { return false; }

  @Override
  public void evict(Entry e) { }

  @Override
  public void expire(Entry e) { }

  @Override
  public void disable(Throwable t) { }

  @SuppressWarnings("unchecked")
  @Override
  public ClosableIterator<Entry> iterateAll() {
    return new EmptyClosableIterator<Entry>();
  }

  @Override
  public int getTotalEntryCount() {
    synchronized (cache.lock) {
      return cache.getLocalSize();
    }
  }

  @Override
  public int getAlert() {
    return 0;
  }

  static class EmptyClosableIterator<E> implements ClosableIterator<E> {

    @Override
    public void close() { }

    @Override
    public boolean hasNext() { return false; }

    @Override
    public E next() { return null; }

    @Override
    public void remove() { }

  }

}
