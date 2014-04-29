package org.cache2k.storage;

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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/* TODO: BufferStorage features...

 * eviction
 * clear
 * bulk?

 */

/**
 * @author Jens Wilke; created: 2014-03-27
 */
public class BufferStorage {

  /**
   * Write the key to the buffer. If we use memory buffer for a off heap
   * cache, we don't need this
   */
  boolean writeKey;
  Marshaller marshaller;
  ByteBuffer buffer;
  Map<Object, StorageEntry> inUpdateMap = new HashMap<>();
  TreeMap<Integer, FreeSlot> freeMap = new TreeMap<>();
  Map<Object, BufferEntry> values = new LinkedHashMap<>();

  long concurrentUpdateCount = 0;
  long missCount = 0;
  long hitCount = 0;
  long putCount = 0;

  public StorageEntry getEntry(Object key, long now, StorageEntry _previousFetchedEntry) {
    BufferEntry be;
    synchronized(values) {
      StorageEntry e = inUpdateMap.get(key);
      if (e != null) {
        concurrentUpdateCount++;
        return e;
      }
      be = values.get(key);
      if (be == null) {
        missCount++;
        return null;
      }
      hitCount++;
    }
    try {
      be.lock();
      ByteBuffer bb = buffer.duplicate();
      bb.position(be.position);
      MyEntry e = new MyEntry();
      e.createdOrUpdated = bb.getLong();
      e.expiryTime = bb.getLong();
      e.lastUsed = bb.getLong();
      e.maxIdleTime = bb.getLong();
      bb.limit(be.position - MyEntry.TIMING_BYTES + be.size);
      e.value = marshaller.unmarshall(bb);
      e.key = key;
      return e;
    } finally {
      be.unlock();
    }
  }

  public void putEntry(StorageEntry e) throws Exception {
    byte[] _marshalledValue = marshaller.marshall(e.getValueOrException());
    byte[] _marshalledKey = null;
    int _expectedSize = _marshalledValue.length + MyEntry.TIMING_BYTES;
    if (writeKey) {
      _marshalledKey = marshaller.marshall(e.getKey());
      _expectedSize += _marshalledKey.length;
    }
    int pos;
    BufferEntry _bufferEntry = null;
    synchronized(values) {
       _bufferEntry = values.remove(e.getKey());
      if (_bufferEntry != null) {
        inUpdateMap.put(e.getKey(), e);
      }
    }
    if (_bufferEntry != null) {
      _bufferEntry.waitUnlocked();
      synchronized(freeMap) {
        freeBufferSpace(_bufferEntry);
        pos = findEmptyPosition(_expectedSize);
      }
    } else {
      synchronized(freeMap) {
        pos = findEmptyPosition(_expectedSize);
      }
    }
    ByteBuffer bb = buffer.duplicate();
    bb.position(pos);
    bb.putLong(e.getCreatedOrUpdated());
    bb.putLong(e.getExpiryTime());
    bb.putLong(e.getLastUsed());
    bb.putLong(e.getMaxIdleTime());
    bb.put(_marshalledValue);
    if (writeKey) {
      bb.put(_marshalledKey);
    }
    synchronized(values) {
      inUpdateMap.remove(e.getKey());
      BufferEntry be =
        new BufferEntry(e.getKey(), pos, _expectedSize);
      values.put(e.getKey(), be);
      putCount++;
    }
  }

  int findEmptyPosition(int _expectedSize) {
    FreeSlot s = findFreeSlot(_expectedSize);
    if (s == null) {
      s = extendBuffer(_expectedSize);
    }
    FreeSlot s2 = new FreeSlot(s.position + _expectedSize, s.size - _expectedSize);
    freeMap.put(s2.size, s2);
    return s.position;
  }

  FreeSlot findFreeSlot(int size) {
    Map.Entry<Integer, FreeSlot>  e = freeMap.ceilingEntry(size);
    if (e != null) {
      return freeMap.remove(e.getKey());
    }
    return null;
  }

  void freeBufferSpace(BufferEntry e) {
    Map.Entry<Integer,FreeSlot> me = freeMap.lowerEntry(e.position);
    FreeSlot s;
    if (me != null && me.getValue().getNextPosition() == e.position) {
      s = me.getValue();
      s.size += e.size;
    } else {
      s = new FreeSlot(e.position, e.size);
      freeMap.put(e.position, s);
    }
    int _nextPosition = s.getNextPosition();
    me = freeMap.ceilingEntry(_nextPosition);
    if (me != null && me.getKey() == _nextPosition) {
      s.size += me.getValue().size;
      freeMap.remove(_nextPosition);
    }
  }

  /** just allocate as many as needed by now */
  FreeSlot extendBuffer(int _neededSpace) {
    int pos = buffer.limit();
    buffer.limit(pos + _neededSpace);
    return new FreeSlot(pos, _neededSpace);
  }

  void writeBufferEntry(ObjectOutput out, BufferEntry e) throws IOException {
    out.writeInt(e.position);
    out.writeInt(e.size);
    out.writeObject(e.key);
  }

  BufferEntry readBufferEntry(ObjectInput di) throws IOException, ClassNotFoundException {
    BufferEntry e = new BufferEntry();
    e.position = di.readInt();
    e.size = di.readInt();
    e.key = di.readObject();
    return e;
  }

  /**
   * Recalculate the slots of empty space, by iterating over all buffer entry
   * and cutting out the allocated areas
   */
  void recalculateFreeSpaceMap() {
    freeMap = new TreeMap<>();
    freeMap.put(0, new FreeSlot(0, buffer.capacity()));
    for (BufferEntry e : values.values()) {
      Map.Entry<Integer, FreeSlot> me = freeMap.ceilingEntry(e.position);
      if (me == null) {
        throw new IllegalStateException("data structure mismatch, never happens");
      }
      if (me.getKey() == e.position) {
        FreeSlot s = freeMap.remove(me.getKey());
        if (s.size > e.size) {
          s.position += e.size;
          s.size -= e.size;
          freeMap.put(s.position, s);
        }
      } else {
        FreeSlot s = me.getValue();
        if (s.getNextPosition() > (e.position + e.size)) {
          FreeSlot s2 = new FreeSlot();
          s2.position = e.position + e.size;
          s2.size = s.getNextPosition() - s2.position;
          freeMap.put(s2.position, s2);
          s.size -= s2.size + e.size;
        } else {
          s.size -= e.size;
        }
      }
    }
  }

  static class FreeSlot {

    int position;
    int size;

    FreeSlot() { }

    FreeSlot(int position, int size) {
      this.position = position;
      this.size = size;
    }

    int getNextPosition() {
      return position + size;
    }

  }

  static class BufferEntry {

    Object key;
    final static int BUFFER_ENTRY_BYTES = 4 * 2;
    int position;
    int size;

    int readCnt;

    BufferEntry() {
    }

    BufferEntry(Object key, int position, int size) {
      this.key = key;
      this.position = position;
      this.size = size;
    }

    public synchronized boolean isLocked() {
       return readCnt > 0;
    }

    public synchronized void lock() {
       readCnt++;
    }

    public synchronized void unlock() {
       readCnt--;
       if (readCnt == 0) {
         notifyAll();
       }
    }

    public synchronized void waitUnlocked() {
      while (readCnt > 0) {
        try {
          wait();
        } catch (InterruptedException e) {
        }
      }
    }
  }

  static class MyEntry implements StorageEntry {

    Object key;
    Object value;

    final static int TIMING_BYTES = 8 * 4;
    long createdOrUpdated;
    long expiryTime;
    long lastUsed;
    long maxIdleTime;

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
