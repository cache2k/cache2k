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
import org.cache2k.storage.StorageEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Record cache operation during a storage clear.
 *
 * <p/>By decoupling we still have fast concurrent access on the cache
 * while the storage "does its thing". This is now just used for the clear()
 * operation, so it is assumed that the initial state of the storage is
 * empty.

 * @author Jens Wilke; created: 2014-04-20
 */
public class CacheStorageBuffer implements CacheStorage {

  List<Op> operations = new ArrayList<>();
  Map<Object, StorageEntry> key2entry = new HashMap<>();

  CacheStorage forwardingStorage;

  @Override
  public void open(CacheStorageContext ctx, StorageConfiguration cfg) throws IOException {
  }

  @Override
  public synchronized void close() throws IOException {
    if (forwardingStorage != null) {
      forwardingStorage.close();
    }
    operations.add(new OpClose());
  }

  @Override
  public synchronized void clear() throws IOException {
    if (forwardingStorage != null) {
      forwardingStorage.clear();
    }
    key2entry.clear();
    operations.add(new OpClear());
  }

  @Override
  public synchronized void put(StorageEntry e) throws IOException, ClassNotFoundException {
    if (forwardingStorage != null) {
      forwardingStorage.put(e);
    }
    e = new CopyStorageEntry(e);
    key2entry.put(e.getKey(), e);
    operations.add(new OpPut(e));
  }

  @Override
  public synchronized StorageEntry get(Object key) throws IOException, ClassNotFoundException {
    if (forwardingStorage != null) {
      return forwardingStorage.get(key);
    }
    operations.add(new OpGet(key));
    return key2entry.get(key);
  }

  @Override
  public synchronized StorageEntry remove(Object key) throws IOException, ClassNotFoundException {
    if (forwardingStorage != null) {
      forwardingStorage.remove(key);
    }
    operations.add(new OpRemove(key));
    return key2entry.remove(key);
  }

  @Override
  public boolean contains(Object key) throws IOException {
    if (forwardingStorage != null) {
      forwardingStorage.contains(key);
    }
    operations.add(new OpContains(key));
    return key2entry.containsKey(key);
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

  public void transfer(CacheStorage _target) throws IOException, ClassNotFoundException {
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
        op.execute(_target);
      }
    }

  }

  static abstract class Op {

    abstract void execute(CacheStorage _target) throws IOException, ClassNotFoundException;

  }

  static class OpPut extends Op {

    StorageEntry entry;

    OpPut(StorageEntry entry) {
      this.entry = entry;
    }

    @Override
    void execute(CacheStorage _target) throws IOException, ClassNotFoundException {
      _target.put(entry);
    }

  }

  static class OpRemove extends Op {

    Object key;

    OpRemove(Object key) {
      this.key = key;
    }

    @Override
    void execute(CacheStorage _target) throws IOException, ClassNotFoundException {
      _target.remove(key);
    }

  }

  static class OpContains extends Op {

    Object key;

    OpContains(Object key) {
      this.key = key;
    }

    @Override
    void execute(CacheStorage _target) throws IOException {
      _target.contains(key);
    }

  }

  static class OpGet extends Op {

    Object key;

    OpGet(Object key) {
      this.key = key;
    }

    @Override
    void execute(CacheStorage _target) throws IOException, ClassNotFoundException {
      _target.get(key);
    }

  }

  static class OpClose extends Op {

    @Override
    void execute(CacheStorage _target) throws IOException {
      _target.close();
    }

  }

  static class OpClear extends Op {

    @Override
    void execute(CacheStorage _target) throws IOException {
      _target.clear();
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
