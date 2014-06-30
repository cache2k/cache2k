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

import java.util.Collections;
import java.util.Iterator;
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
  public void shutdown() {
  }

  @Override
  public boolean checkStorageStillDisconnectedForClear() {
    return true;
  }

  @Override
  public void disconnectStorageForClear() {

  }

  @Override
  public Future<Void> startClearingAndReconnection() {
    return new Futures.FinishedFuture<>(null);
  }

  @Override
  public void put(BaseCache.Entry e) { }

  @Override
  public StorageEntry get(Object key) {
    return null;
  }

  @Override
  public void remove(Object key) { }

  @Override
  public void evict(BaseCache.Entry e) { }

  @Override
  public void expire(BaseCache.Entry e) { }

  @Override
  public void disable(Throwable t) { }

  @SuppressWarnings("unchecked")
  @Override
  public ClosableIterator<BaseCache.Entry> iterateAll() {
    return new EmptyClosableIterator<>();
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
