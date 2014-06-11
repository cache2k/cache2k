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
import org.cache2k.impl.timer.BaseTimerTask;
import org.cache2k.impl.timer.TimerService;
import org.cache2k.storage.CacheStorage;
import org.cache2k.storage.CacheStorageContext;
import org.cache2k.storage.StorageEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Record cache operations during a storage clear.
 *
 * <p/>By decoupling we still have fast concurrent access on the cache
 * while the storage "does its thing". This is now just used for the clear()
 * operation, so it is assumed that the initial state of the storage is
 * empty.
 *
 * <p/>If the requests come in faster than the storage can handle it
 * we will  end up queuing up requests forever (... and run out of
 * heap space). So when starting the transfer we slow down in taking
 * new entries.
 *
 * <p/>A problem is, that the storage is only operated single threaded.
 *
 * @author Jens Wilke; created: 2014-04-20
 */
public class CacheStorageBuffer implements CacheStorage {

  long operationsCnt = 0;
  long operationsAtTransferStart = 0;
  long sentOperationsCnt = 0;
  long sendingStart = 0;

  /** Average time for one storage operation in microseconds */
  long microRate = 0;

  /** Added up rest of microseconds to wait */
  long microWaitRest = 0;
  TimerService.CancelHandle tt;

  List<Op> operations = new ArrayList<>();
  Map<Object, StorageEntry> key2entry = new HashMap<>();

  CacheStorage forwardingStorage;

  /**
   * Stall this thread until the op is executed. However, the
   * op is doing nothing and just notifies us. The flush
   * on the storage can run in parallel with other tasks.
   * This method is only allowed to finish when the flush is done.
   */
  @Override
  public void flush(long now, final FlushContext ctx) throws Exception {
    synchronized (this) {
      if (forwardingStorage != null) {
        forwardingStorage.flush(now, ctx);
      }
      Op op = new OpFlush();
      addOp(op);
      synchronized (op) {
        op.wait();
      }
      forwardingStorage.flush(System.currentTimeMillis(), ctx);
    }
  }

  @Override
  public void close() throws IOException {
    synchronized (this) {
      if (forwardingStorage != null) {
        forwardingStorage.close();
      }
      addOp(new OpClose());
    }
  }

  @Override
  public void open(CacheStorageContext ctx, StorageConfiguration cfg) throws IOException {
  }

  @Override
  public void clear() throws IOException {
    synchronized (this) {
      if (forwardingStorage != null) {
        forwardingStorage.clear();
      }
      key2entry.clear();
      addOp(new OpClear());
    }
    throttle();
  }

  @Override
  public void put(StorageEntry e) throws IOException, ClassNotFoundException {
    synchronized (this) {
      if (forwardingStorage != null) {
        forwardingStorage.put(e);
      }
      e = new CopyStorageEntry(e);
      key2entry.put(e.getKey(), e);
      addOp(new OpPut(e));
    }
    throttle();
  }

  @Override
  public StorageEntry get(Object key) throws IOException, ClassNotFoundException {
    StorageEntry e;
    synchronized (this) {
      if (forwardingStorage != null) {
        return forwardingStorage.get(key);
      }
      addOp(new OpGet(key));
      e = key2entry.get(key);
    }
    throttle();
    return e;
  }

  @Override
  public StorageEntry remove(Object key) throws IOException, ClassNotFoundException {
    StorageEntry e;
    synchronized (this) {
      if (forwardingStorage != null) {
        forwardingStorage.remove(key);
      }
      addOp(new OpRemove(key));
      e = key2entry.remove(key);
    }
    throttle();
    return e;
  }

  @Override
  public boolean contains(Object key) throws IOException {
    boolean b;
    synchronized (this) {
      if (forwardingStorage != null) {
        forwardingStorage.contains(key);
      }
      addOp(new OpContains(key));
      b = key2entry.containsKey(key);
    }
    return b;
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

  public void transfer(CacheStorage _target) throws Exception {
    synchronized (this) {
      sendingStart = System.currentTimeMillis();
      operationsAtTransferStart = operationsCnt;
    }
    List<Op> _workList;
    while (true) {
      synchronized (this) {
        if (operations.size() == 0) {
          forwardingStorage = _target;
          return;
        }
        _workList = operations;
        operations = new ArrayList<>();
      }
      for (Op op : _workList) {
        sentOperationsCnt++;
        op.execute(_target);
      }
    }
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

  static class OpClear extends Op {

    @Override
    void execute(CacheStorage _target) throws Exception {
      _target.clear();
    }

    @Override
    public String toString() {
      return "OpClear";
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
      expiryTime = e.getExpiryTime();
      createdOrUpdated = e.getCreatedOrUpdated();
      lastUsed = e.getLastUsed();
      maxIdleTime = e.getMaxIdleTime();
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
    public long getExpiryTime() {
      return expiryTime;
    }

    @Override
    public long getLastUsed() {
      return lastUsed;
    }

    @Override
    public long getMaxIdleTime() {
      return maxIdleTime;
    }

  }

}
