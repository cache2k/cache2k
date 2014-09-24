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

import org.cache2k.StorageConfiguration;
import org.cache2k.storage.CacheStorage;
import org.cache2k.storage.CacheStorageContext;
import org.cache2k.storage.FlushableStorage;
import org.cache2k.storage.PurgeableStorage;
import org.cache2k.storage.StorageEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * Record cache operations during a storage clear and playback later.
 *
 * <p/>By decoupling we still have fast concurrent access on the cache
 * while the storage "does its thing" and remove files, etc. This is
 * now just used for the clear() operation, so it is assumed that
 * the initial state of the storage is empty.
 *
 * <p/>After playback starts, and if the requests come in faster than
 * the storage can handle it we will end up queuing up requests forever,
 * which means we will loose the cache properties and run out of heap space.
 * To avoid this, we slow down the acceptance of new requests as soon as
 * the storage playback starts.
 *
 * <p/>The normal operations like (get, put and remove) fill the playback
 * queue. They also check within the lock whether the storage is online again.
 * This is needed to make the reconnect of the storage atomically. It must
 * be assured that all operations are sent and that the order is kept intact.
 *
 * <p/>Right now, the storage is only operated single threaded by the buffer
 * playback.
 *
 * <p>TODO-C: Multi threaded storage playback
 *
 * @author Jens Wilke; created: 2014-04-20
 */
public class ClearStorageBuffer implements CacheStorage, FlushableStorage, PurgeableStorage {

  long operationsCnt = 0;
  long operationsAtTransferStart = 0;
  long sentOperationsCnt = 0;
  long sendingStart = 0;

  /** Average time for one storage operation in microseconds */
  long microRate = 0;

  /** Added up rest of microseconds to wait */
  long microWaitRest = 0;

  boolean shouldStop = false;

  List<Op> operations = new ArrayList<>();
  Map<Object, StorageEntry> key2entry = new HashMap<>();

  CacheStorage forwardStorage;

  CacheStorage nextStorage;

  Future<Void> clearThreadFuture;

  Throwable exception;

  /**
   * Stall this thread until the op is executed. However, the
   * op is doing nothing and just notifies us. The flush
   * on the storage can run in parallel with other tasks.
   * This method is only allowed to finish when the flush is done.
   */
  @Override
  public void flush(final FlushableStorage.FlushContext ctx, long now) throws Exception {
    Op op = null;
    boolean _forward;
    synchronized (this) {
      _forward = forwardStorage != null;
      if (!_forward) {
        op = new OpFlush();
        addOp(op);
      }
    }
    if (_forward) {
      ((FlushableStorage) forwardStorage).flush(ctx, now);
    }
    synchronized (op) {
      op.wait();
      if (exception != null) {
        throw new CacheStorageException(exception);
      }
    }
    ((FlushableStorage) forwardStorage).flush(ctx, System.currentTimeMillis());
  }

  /**
   * Does nothing. We were cleared lately anyway. Next purge may go to the storage.
   */
  @Override
  public void purge(PurgeContext ctx, long _valueExpiryTime, long _entryExpiryTime) { }

  @Override
  public void close() throws Exception {
    boolean _forward;
    synchronized (this) {
      _forward = forwardStorage != null;
      if (!_forward) {
        addOp(new OpClose());
      }
    }
    if (_forward) {
      forwardStorage.close();
    }
  }

  @Override
  public void open(CacheStorageContext ctx, StorageConfiguration cfg) throws IOException {
  }

  @Override
  public void clear() throws Exception {
    throw new IllegalStateException("never called");
  }

  @Override
  public void put(StorageEntry e) throws Exception {
    boolean _forward;
    synchronized (this) {
      _forward = forwardStorage != null;
      if (!_forward) {
        e = new CopyStorageEntry(e);
        key2entry.put(e.getKey(), e);
        addOp(new OpPut(e));
      }
    }
    if (_forward) {
      forwardStorage.put(e);
    } else {
      throttle();
    }
  }

  @Override
  public StorageEntry get(Object key) throws Exception {
    boolean _forward;
    StorageEntry e = null;
    synchronized (this) {
      _forward = forwardStorage != null;
      if (!_forward) {
        addOp(new OpGet(key));
        e = key2entry.get(key);
      }
    }
    if (_forward) {
      e = forwardStorage.get(key);
    } else {
      throttle();
    }
    return e;
  }

  @Override
  public StorageEntry remove(Object key) throws Exception {
    StorageEntry e = null;
    boolean _forward;
    synchronized (this) {
      _forward = forwardStorage != null;
      if (!_forward) {
        addOp(new OpRemove(key));
        e = key2entry.remove(key);
      }
    }
    if (_forward) {
      e = forwardStorage.remove(key);
    } else {
      throttle();
    }
    return e;
  }

  @Override
  public boolean contains(Object key) throws Exception {
    boolean b = false;
    boolean _forward;
    synchronized (this) {
      _forward = forwardStorage != null;
      if (!_forward) {
        addOp(new OpContains(key));
        b = key2entry.containsKey(key);
      }
    }
    if (_forward) {
      b = forwardStorage.contains(key);
    } else {
      throttle();
    }
    return b;
  }

  @Override
  public void visit(VisitContext ctx, EntryFilter f, EntryVisitor v) throws Exception {
    List<StorageEntry> l = null;
    boolean _forward;
    synchronized (this) {
      _forward = forwardStorage != null;
      if (!_forward) {
        l = new ArrayList<>(key2entry.size());
        for (StorageEntry e : key2entry.values()) {
          if (f == null || f.shouldInclude(e.getKey())) {
            l.add(e);
          }
        }
      }
    }
    if (_forward) {
      forwardStorage.visit(ctx, f, v);
    } else {
      for (StorageEntry e : l) {
        if (ctx.shouldStop()) {
          return;
        }
        v.visit(e);
      }
    }
  }

  @Override
  public void setEntryCapacity(int v) {

  }

  @Override
  public void setBytesCapacity(long v) {

  }

  @Override
  public int getEntryCapacity() {
    return -1;
  }

  @Override
  public int getEntryCount() {
    return key2entry.size();
  }

  private void addOp(Op op) {
    operationsCnt++;
    operations.add(op);
  }

  /**
   * Throttle
   */
  private void throttle() {
    if (sentOperationsCnt == 0) {
      return;
    }
    long _waitMicros;
    synchronized (this) {
      if (operationsCnt % 7 == 0) {
        long _factor = 1000000;
        long _refilledSinceTransferStart = operationsCnt - operationsAtTransferStart;
        if (_refilledSinceTransferStart > sentOperationsCnt) {
          _factor = _refilledSinceTransferStart * 1000000 / sentOperationsCnt;
        }
        long _delta = System.currentTimeMillis() - sendingStart;
        microRate = _delta * _factor / sentOperationsCnt;
      }
      _waitMicros = microRate + microRate * 3 / 100 + microWaitRest;
      microWaitRest = _waitMicros % 1000000;
    }
    try {
      long _millis = _waitMicros / 1000000;
      Thread.sleep(_millis);
    } catch (InterruptedException e) {
    }
  }

  public void startTransfer() {
    synchronized (this) {
      sendingStart = System.currentTimeMillis();
      operationsAtTransferStart = operationsCnt;
    }
  }

  public boolean isTransferringToStorage() {
    return sendingStart > 0;
  }

  public void transfer() throws Exception {
    CacheStorage _target = getOriginalStorage();
    List<Op> _workList;
    while (true) {
      synchronized (this) {
        if (shouldStop) {
          return;
        }
        if (operations.size() == 0) {
          forwardStorage = nextStorage;
          return;
        }
        _workList = operations;
        operations = new ArrayList<>();
      }
      for (Op op : _workList) {
        sentOperationsCnt++;
        op.execute(_target);
        if (shouldStop) {
          if (exception != null) {
            throw new CacheStorageException(exception);
          }
        }
      }
    }
  }

  public void disableOnFailure(Throwable t) {
    exception = t;
    shouldStop = true;
    for (Op op : operations) {
      op.notifyAll();
    }
    CacheStorage _storage = nextStorage;
    if (_storage instanceof ClearStorageBuffer) {
      ((ClearStorageBuffer) _storage).disableOnFailure(t);
    }
  }

  CacheStorage getOriginalStorage() {
    if (nextStorage instanceof ClearStorageBuffer) {
      return ((ClearStorageBuffer) nextStorage).getOriginalStorage();
    }
    return nextStorage;
  }

  void waitForAll() throws Exception {
    if (clearThreadFuture != null && !clearThreadFuture.isDone()) {
      clearThreadFuture.get();
    }
    if (nextStorage instanceof ClearStorageBuffer) {
      ((ClearStorageBuffer) nextStorage).waitForAll();
    }
  }

  CacheStorage getNextStorage() {
    return nextStorage;
  }

  static abstract class Op {

    abstract void execute(CacheStorage _target) throws Exception;

  }

  static class OpPut extends Op {

    StorageEntry entry;

    OpPut(StorageEntry entry) {
      this.entry = entry;
    }

    @Override
    void execute(CacheStorage _target) throws Exception {
      _target.put(entry);
    }

    @Override
    public String toString() {
      return "OpPut(key=" + entry.getKey() + ", value=" + entry.getValueOrException() + ")";
    }
  }

  static class OpRemove extends Op {

    Object key;

    OpRemove(Object key) {
      this.key = key;
    }

    @Override
    void execute(CacheStorage _target) throws Exception {
      _target.remove(key);
    }

    @Override
    public String toString() {
      return "OpRemove(key=" + key + ")";
    }

  }

  static class OpContains extends Op {

    Object key;

    OpContains(Object key) {
      this.key = key;
    }

    @Override
    void execute(CacheStorage _target) throws Exception {
      _target.contains(key);
    }

    @Override
    public String toString() {
      return "OpContains(key=" + key + ")";
    }

  }

  static class OpGet extends Op {

    Object key;

    OpGet(Object key) {
      this.key = key;
    }

    @Override
    void execute(CacheStorage _target) throws Exception {
      _target.get(key);
    }

    @Override
    public String toString() {
      return "OpGet(key=" + key + ")";
    }

  }

  static class OpClose extends Op {

    @Override
    void execute(CacheStorage _target) throws Exception {
      _target.close();
    }

    @Override
    public String toString() {
      return "OpClose";
    }

  }

  static class OpFlush extends Op {

    @Override
    void execute(CacheStorage _target) throws Exception {
      synchronized (this) {
        notify();
      }
    }

    @Override
    public String toString() {
      return "OpFlush";
    }

  }

  static class CopyStorageEntry implements StorageEntry {

    Object key;
    Object value;
    long expiryTime;
    long createdOrUpdated;
    long lastUsed;
    long maxIdleTime;

    CopyStorageEntry(StorageEntry e) {
      key = e.getKey();
      value = e.getValueOrException();
      expiryTime = e.getValueExpiryTime();
      createdOrUpdated = e.getCreatedOrUpdated();
      lastUsed = e.getEntryExpiryTime();
    }

    @Override
    public Object getKey() {
      return key;
    }

    @Override
    public Object getValueOrException() {
      return value;
    }

    @Override
    public long getCreatedOrUpdated() {
      return createdOrUpdated;
    }

    @Override
    public long getValueExpiryTime() {
      return expiryTime;
    }

    @Override
    public long getEntryExpiryTime() {
      return lastUsed;
    }

  }

}
