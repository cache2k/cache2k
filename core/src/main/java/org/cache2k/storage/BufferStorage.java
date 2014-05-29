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

import org.cache2k.impl.ExceptionWrapper;

import java.io.DataOutput;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;


/**
 * Implements a robust storage on a file or a byte buffer.
 *
 * <p>
 * The storage tries to be robust and does not require a clean shutdown, so it
 * will survive machine crashes. In this case it uses the latest data possible that
 * is known to be intact. The amount of data loss can be controlled by specifying
 * a commit interval.
 * </p>
 *
 * @author Jens Wilke; created: 2014-03-27
 */
public class BufferStorage implements CacheStorage {

  /** Number of bytes we used for on disk disk checksum of our descriptor */
  final static int CHECKSUM_BYTES = 16;

  final static int DESCRIPTOR_COUNT = 2;

  final static String DESCRIPTOR_MAGIC = "CACHE2K STORAGE 00";

  final static Marshaller DESCRIPTOR_MARSHALLER = new StandardMarshaller();

  boolean dataLost = false;
  Tunables tunables = new Tunables();
  Marshaller keyMarshaller = new StandardMarshaller();
  Marshaller valueMarshaller = new StandardMarshaller();
  Marshaller universalMarshaller = new StandardMarshaller();
  Marshaller exceptionMarshaller = new StandardMarshaller();
  RandomAccessFile file;
  ByteBuffer buffer;
  final TreeSet<FreeSlot> freeSet = new TreeSet<>();
  TreeMap<Long, FreeSlot> pos2slot = new TreeMap<>();
  Map<Object, BufferEntry> values;

  final Object valuesLock = new Object();
  final Object commitLock = new Object();

  /** buffer entries added since last commit */
  HashMap<Object, BufferEntry> newBufferEntries;

  /** entries deleted since last commit */
  HashMap<Object, BufferEntry> deletedBufferEntries;

  /**
   * Entries still needed originating from the earliest index file. Every commit
   * entries will be removed that are newly written. Each commit also
   * partially rewrites some of the entries here to make the file redundant.
   */
  HashMap<Object, BufferEntry> entriesInEarliestIndex;

  /**
   * All entries committed to the current index file. Updated within commit phase.
   * This is used to fill up {@link #entriesInEarliestIndex} when starting a
   * new index file.
   */
  HashMap<Object, BufferEntry> committedEntries;

  /**
   * List of entry that got rewritten or deleted. The storage space will
   * be freed after a commit.
   */
  ArrayList<BufferEntry> spaceToFree;

  List<BufferEntry> freeSecondNextCommit = null;

  List<BufferEntry> freeNextCommit = null;

  /**
   * Counter of entries committed to the current index. This is more
   * then the size of {@link #committedEntries} since entries for
   * the same key may be written multiple times. This counter is used
   * to determine to start a new index file within
   * {@link org.cache2k.storage.BufferStorage.KeyIndexWriter#checkStartNewIndex()}
   */
  int committedEntriesCount;

  BufferDescriptor descriptor;
  String fileName;

  /**
   * capacity is by default unlimited.
   */
  int entryCapacity = Integer.MAX_VALUE;
  long bytesCapacity;

  long missCount = 0;
  long hitCount = 0;
  long putCount = 0;
  long freeSpace = 0;

  public BufferStorage(Tunables t, String _fileName) throws IOException, ClassNotFoundException {
    fileName = _fileName;
    tunables = t;
    reopen();
  }

  public BufferStorage(String _fileName) throws IOException, ClassNotFoundException {
    fileName = _fileName;
    reopen();
  }

  public BufferStorage() {
  }

  public void setFileName(String v) {
    fileName = v;
  }

  public void reopen() throws IOException {
    try {
      file = new RandomAccessFile(fileName + ".img", "rw");
      resetBufferFromFile();
      if (values != null) {
        values.clear();
      }
      if (entryCapacity == Integer.MAX_VALUE) {
        values = new HashMap<>();
      } else {
        values = new LinkedHashMap<Object, BufferEntry>(100, .75F, true) {

          @Override
          protected boolean removeEldestEntry(Map.Entry<Object, BufferEntry> _eldest) {
            if (getEntryCount() > entryCapacity) {
              reallyRemove(_eldest.getValue());
              return true;
            }
            return false;
          }

        };
      }
      newBufferEntries = new HashMap<>();
      deletedBufferEntries = new HashMap<>();
      spaceToFree = new ArrayList<>();
      entriesInEarliestIndex = new HashMap<>();
      committedEntries = new HashMap<>();
      BufferDescriptor d = readLatestIntactBufferDescriptor();
      if (d == null) {
        if (buffer.capacity() > 0) {
          dataLost = true;
        }
        initializeFreeSpaceMap();
        descriptor = new BufferDescriptor();
        descriptor.storageCreated = System.currentTimeMillis();
      } else {
        descriptor = d;
        readIndex();
      }
    } catch (ClassNotFoundException e) {
      throw new IOException(e);
    }
  }

  public void close() throws IOException {
    synchronized (commitLock) {
      if (file == null) {
        return;
      }
      commit();
      fastClose();
    }
  }

  public void clear() throws IOException {
    synchronized (valuesLock) {
      synchronized (commitLock) {
        if (file != null) {
          fastClose();
        }
        for (int i = 0; i < DESCRIPTOR_COUNT; i++) {
          new File(fileName + "-" + i + ".dsc").delete();
        }
        for (int i = descriptor.lastIndexFile; i >= 0; i--) {
          new File(fileName + "-" + i + ".idx").delete();
        }
        reopen();
        System.err.println("cleared!!!");
      }
    }
  }

  private void resetBufferFromFile() throws IOException {
    buffer =
      file.getChannel().map(
        FileChannel.MapMode.READ_WRITE,
        0, file.length());
  }

  /**
   * Close immediately without doing a commit.
   */
  public void fastClose() throws IOException {
    synchronized (valuesLock) {
      values.clear();
      pos2slot.clear();
      buffer = null;
      file.close();
      file = null;
    }
  }

  public StorageEntry get(Object key)
    throws IOException, ClassNotFoundException {
    BufferEntry be;
    synchronized (valuesLock) {
      be = values.get(key);
      if (be == null) {
        missCount++;
        return null;
      }
      hitCount++;
    }
    return returnEntry(be);
  }

  public boolean contains(Object key) throws IOException {
    synchronized (valuesLock) {
      if (!values.containsKey(key)) {
        missCount++;
        return false;
      }
      hitCount++;
      return true;
    }
  }

  public StorageEntry remove(Object key) throws IOException, ClassNotFoundException {
    BufferEntry be;
    synchronized (valuesLock) {
      be = values.remove(key);
      if (be == null) {
        missCount++;
        return null;
      }
      reallyRemove(be);
      hitCount++;
    }
    return returnEntry(be);
  }

  /**
   * Called by remove and when an eviction needs to be done.
   */
  private void reallyRemove(BufferEntry be) {
    deletedBufferEntries.put(be.key, be);
    newBufferEntries.remove(be.key);
    spaceToFree.add(be);
  }

  private MyEntry returnEntry(BufferEntry be) throws IOException, ClassNotFoundException {
    ByteBuffer bb = buffer.duplicate();
    bb.position((int) be.position);
    MyEntry e = new MyEntry();
    e.key = be.key;
    e.readMetaInfo(bb, descriptor.storageCreated);
    int _type = e.getTypeNumber();
    if (_type == TYPE_NULL) {
      return e;
    }
    bb.limit((int) (be.position + be.size));
    if (_type == TYPE_VALUE) {
      e.value = valueMarshaller.unmarshall(bb);
    } else {
      e.value = new ExceptionWrapper((Throwable) exceptionMarshaller.unmarshall(bb));
    }
    return e;
  }

  final static byte[] ZERO_LENGTH_BYTE_ARRAY = new byte[0];

  /**
   * Store a new entry. To achieve robustness without implementing a WAL there
   * is no update in place. Each entry gets a newly allocated space. The freed
   * space will be available for reallocation until the index is committed twice.
   * BTW: there is a theoretical race condition in this, because there is no
   * real protection against the case that the read is reading an in between
   * reallocated space. However, this will only be a problem if the read
   * fails to get CPU time for several seconds.
   */
  public boolean put(StorageEntry e) throws IOException, ClassNotFoundException {
    Object o = e.getValueOrException();
    byte[] _marshalledValue = ZERO_LENGTH_BYTE_ARRAY;
    int _neededSize = 0;
    byte _type;
    boolean _overwritten = false;
    if (o == null) {
      _type = TYPE_NULL;
    } else {
      if (o instanceof ExceptionWrapper) {
        _type = TYPE_EXCEPTION;
        _marshalledValue = exceptionMarshaller.marshall(((ExceptionWrapper) o).getException());
      } else if (valueMarshaller.supports(o)) {
        _type = TYPE_VALUE;
        _marshalledValue = valueMarshaller.marshall(o);
      } else {
        _type = TYPE_UNIVERSAL;
        _marshalledValue = universalMarshaller.marshall(o);
      }
      _neededSize = _marshalledValue.length;
    }
    _neededSize += MyEntry.calculateMetaInfoSize(e, descriptor.storageCreated, _type);
    ByteBuffer bb;
    BufferEntry _newEntry;
    synchronized(freeSet) {
      FreeSlot s = reservePosition(_neededSize);
      bb = buffer.duplicate();
      bb.position((int) s.position);
      MyEntry.writeMetaInfo(bb, e, descriptor.storageCreated, _type);
      int _usedSize = (int) (bb.position() - s.position) + _marshalledValue.length;
      _newEntry = new BufferEntry(e.getKey(), s.position, _usedSize);
      s.size -=  _usedSize;
      if (s.size > 0) {
        freeSpace += s.size;
        s.position += _usedSize;
        freeSet.add(s);
        pos2slot.put(s.position, s);
      }
    }
    bb.put(_marshalledValue);
    synchronized (valuesLock) {
      BufferEntry be = values.get(e.getKey());
      if (be != null) {
        _overwritten = true;
        spaceToFree.add(be);
      }
      newBufferEntries.put(e.getKey(), _newEntry);
      values.put(e.getKey(), _newEntry);
      putCount++;
    }
    return _overwritten;
  }

  /**
   *
   */
  FreeSlot reservePosition(int _size) throws IOException {
    FreeSlot s = findFreeSlot(_size);
    if (s == null) {
      s = extendBuffer(_size);
    }
    return s;
  }

  final FreeSlot reusedFreeSlotUnderLock = new FreeSlot(0, 0);

  /**
   * Find a slot which size is greater then the needed size and
   * remove it from the free space map.
   */
  FreeSlot findFreeSlot(int size) {
    reusedFreeSlotUnderLock.size = size;
    FreeSlot s = freeSet.ceiling(reusedFreeSlotUnderLock);
    if (s != null) {
      freeSet.remove(s);
      freeSpace -= s.size;
      pos2slot.remove(s.position);
      return s;
    }
    return null;
  }
  
  long calcSize(Collection<BufferEntry> set) {
    long v = 0;
    if (set != null) {
      for (BufferEntry e: set) {
        v += e.size;
      }
    }
    return v;
  }

  long calculateUsedSpace() {
    long s = 0;
    s += calcSize(values.values());
    s += calcSize(spaceToFree);
    s += calcSize(freeSecondNextCommit);
    s += calcSize(freeNextCommit);
    return s;
  }

  long calculateFreeSpace() {
    long s = 0;
    for (FreeSlot fs : freeSet) {
      s += fs.size;
    }
    return s;
  }

  @Override
  public int getEntryCount() {
    synchronized (valuesLock) {
      return values.size();
    }
  }

  public long getFreeSpace() {
    return freeSpace;
  }

  public long getTotalValueSpace() {
    return buffer.capacity();
  }

  public int getUncommittedEntryCount() {
    return newBufferEntries.size() + deletedBufferEntries.size();
  }

  /**
   * Flag there was a problem at the last startup and probably some data was lost.
   * This is an indicator for an unclean shutdown, crash, etc.
   */
  public boolean isDataLost() {
    return dataLost;
  }

  public int getFreeSpaceInLargestFreeSlot() {
    if (freeSet.size() == 0) {
      return 0;
    }
    return freeSet.last().size;
  }

  /**
   * Called when there is no more space available. Allocates new space and
   * returns the area in a free slot. The free slot needs to be inserted
   * in the maps by the caller.
   */
  FreeSlot extendBuffer(int _neededSpace) throws IOException {
    int pos = (int) file.length();
    Map.Entry<Long, FreeSlot> e = pos2slot.floorEntry((long) pos);
    FreeSlot s = null;
    if (e != null) {
      s = e.getValue();
      if (s.getNextPosition() == pos) {
        freeSet.remove(s);
        pos2slot.remove(s.position);
        _neededSpace -= s.size;
        freeSpace -= s.size;
        s.size += _neededSpace;
      } else {
        s = null;
      }
    }
    if (s == null) {
      s = new FreeSlot(pos, _neededSpace);
    }
    if (tunables.extensionSize >= 2) {
      s.size += tunables.extensionSize - 1;
      s.size -= s.size % tunables.extensionSize;
    }
    file.setLength(s.getNextPosition());
    resetBufferFromFile();
    return s;
  }

  void readIndex() throws IOException, ClassNotFoundException {
    descriptor = readLatestIntactBufferDescriptor();
    if (descriptor == null) {
      return;
    }
    KeyIndexReader r = new KeyIndexReader();
    r.readKeyIndex();
    recalculateFreeSpaceMapAndRemoveDeletedEntries();
  }

  BufferDescriptor readLatestIntactBufferDescriptor() throws IOException, ClassNotFoundException {
    BufferDescriptor bd = null;
    for (int i = 0; i < DESCRIPTOR_COUNT; i++) {
      try {
        BufferDescriptor bd2 = readDesicptor(i);
        if (bd2 != null && (bd == null || bd.descriptorVersion < bd2.descriptorVersion)) {
          bd = bd2;
        }
      } catch (IOException ex) {
      }
    }
    return bd;
  }

  BufferDescriptor readDesicptor(int idx) throws IOException, ClassNotFoundException {
    File f = new File(fileName + "-" + idx + ".dsc");
    if (!f.exists()) {
      return null;
    }
    RandomAccessFile raf = new RandomAccessFile(f, "r");
    try {
      for (int i = 0; i < DESCRIPTOR_MAGIC.length(); i++) {
        if (DESCRIPTOR_MAGIC.charAt(i) != raf.read()) {
          return null;
        }
      }
      byte[] _checkSumFirstBytes = new byte[CHECKSUM_BYTES];
      raf.read(_checkSumFirstBytes);
      byte[] _serializedDescriptorObject = new byte[(int) (raf.length() - raf.getFilePointer())];
      raf.read(_serializedDescriptorObject);
      byte[] _refSum = calcCheckSum(_serializedDescriptorObject);
      for (int i = 0; i < CHECKSUM_BYTES; i++) {
        if (_checkSumFirstBytes[i] != _refSum[i]) {
          return null;
        }
      }
      return
        (BufferDescriptor) DESCRIPTOR_MARSHALLER.unmarshall(_serializedDescriptorObject);
    } finally {
      raf.close();
    }
  }

  void writeDescriptor() throws IOException {
    int idx = (int) (descriptor.descriptorVersion % DESCRIPTOR_COUNT);
    RandomAccessFile raf = new RandomAccessFile(fileName + "-" + idx + ".dsc", "rw");
    raf.setLength(0);
    for (int i = 0; i < DESCRIPTOR_MAGIC.length(); i++) {
      raf.write(DESCRIPTOR_MAGIC.charAt(i));
    }
    byte[] _serializedDescriptorObject = DESCRIPTOR_MARSHALLER.marshall(descriptor);
    byte[] _checkSum = calcCheckSum(_serializedDescriptorObject);
    raf.write(_checkSum, 0, CHECKSUM_BYTES);
    raf.write(_serializedDescriptorObject);
    raf.close();
    descriptor.descriptorVersion++;
  }

  /**
   * Recalculate the slots of empty space, by iterating over all buffer entries
   * and cutting out the allocated areas
   */
  void recalculateFreeSpaceMapAndRemoveDeletedEntries() {
    synchronized (freeSet) {
      initializeFreeSpaceMap();
      TreeMap<Long, FreeSlot> _pos2slot = pos2slot;
      HashSet<Object> _deletedKey = new HashSet<>();
      for (BufferEntry e : values.values()) {
        if (e.position < 0) {
          _deletedKey.add(e.key);
          continue;
        }
        allocateEntrySpace(e, _pos2slot);
      }
      for (Object k : _deletedKey) {
        values.remove(k);
      }
      rebuildFreeSet();
    }
  }

  /**
   * Rebuild the free set from the elements in {@link #pos2slot}
   */
  private void rebuildFreeSet() {
    TreeMap<Long, FreeSlot> _pos2slot = pos2slot;
    Set<FreeSlot> m = freeSet;
    m.clear();
    for (FreeSlot s : _pos2slot.values()) {
      m.add(s);
    }
  }

  private void initializeFreeSpaceMap() {
    freeSpace = buffer.capacity();
    FreeSlot s = new FreeSlot(0, buffer.capacity());
    pos2slot = new TreeMap<>();
    pos2slot.put(s.position, s);
    freeSet.clear();
    freeSet.add(s);
  }

  /**
   * Used to rebuild the free space map. Entry has already got a position.
   * The used space will be removed of the free space map.
   */
  static void allocateEntrySpace(BufferEntry e, TreeMap<Long, FreeSlot> _pos2slot) {
    Map.Entry<Long, FreeSlot> me = _pos2slot.floorEntry(e.position);
    if (me == null) {
      throw new IllegalStateException("data structure mismatch, internal error");
    }
    FreeSlot s = _pos2slot.remove(me.getKey());
    if (s.position < e.position) {
      FreeSlot _preceding = new FreeSlot();
      _preceding.position = s.position;
      _preceding.size = (int) (e.position - s.position);
      _pos2slot.put(_preceding.position, _preceding);
      s.size -= _preceding.size;
    }
    if (s.size > e.size) {
      s.position = e.size + e.position;
      s.size -= e.size;
      _pos2slot.put(s.position, s);
    }
  }

  /**
   * Used to rebuild the free space map. The entry was deleted or rewritten
   * now the space needs to be freed. Actually we can just put a slot with
   * position and size of the entry in the map, but we better merge it with
   * neighboring slots.
   */
  static void freeEntrySpace(BufferEntry e, TreeMap<Long, FreeSlot> _pos2slot, Set<FreeSlot> _freeSet) {
    FreeSlot s = _pos2slot.get(e.size + e.position);
    if (s != null) {
      _pos2slot.remove(s.position);
      _freeSet.remove(s);
      s.position = e.position;
      s.size += e.size;
    } else {
      s = new FreeSlot(e.position, e.size);
    }
    Map.Entry<Long, FreeSlot> me = _pos2slot.lowerEntry(e.position);
    if (me != null && me.getValue().getNextPosition() == e.position) {
      FreeSlot s2 = _pos2slot.remove(me.getKey());
      _freeSet.remove(s2);
      s2.size += s.size;
      s = s2;
    }
    _pos2slot.put(s.position, s);
    _freeSet.add(s);
  }

  /**
   * Write key to object index to disk for all modified entries. The implementation only works
   * single threaded.
   *
   * @throws IOException
   */
  public void commit() throws IOException {
    synchronized (commitLock) {
      KeyIndexWriter _writer;
      synchronized (valuesLock) {
        if (newBufferEntries.size() == 0 && deletedBufferEntries.size() == 0) {
          return;
        }
        _writer = new KeyIndexWriter();
        _writer.newEntries = newBufferEntries;
        _writer.deletedEntries = deletedBufferEntries;
        _writer.spaceToFree = spaceToFree;
        spaceToFree = new ArrayList<>();
        newBufferEntries = new HashMap<>();
        deletedBufferEntries = new HashMap<>();
        descriptor.entryCount = getEntryCount();
      }
      _writer.write();
      writeDescriptor();
      _writer.freeSpace();
    }
  }

  /**
    * Don't write out the oldest entries that we also have in our updated lists.
    */
   static void sortOut(Map<Object, BufferEntry> map, Set<Object> _keys) {
     for (Object k: _keys) {
       map.remove(k);
     }
   }

  class KeyIndexWriter {

    RandomAccessFile randomAccessFile;
    HashMap<Object, BufferEntry> newEntries;
    HashMap<Object, BufferEntry> deletedEntries;
    HashMap<Object, BufferEntry> rewriteEntries = new HashMap<>();
    List<BufferEntry> spaceToFree = null;
    int indexFileNo;
    int position;

    boolean forceNewFile = false;

    void write() throws IOException {
      indexFileNo = descriptor.lastIndexFile;
      checkForEntriesToRewrite();
      checkStartNewIndex();
      if (forceNewFile) {
        entriesInEarliestIndex = committedEntries;
        committedEntries = new HashMap<>();
        committedEntriesCount = 0;
      }
      try {
        openFile();
        writeIndexChunk();
      } finally {
        if (randomAccessFile != null) {
          randomAccessFile.close();
        }
      }
      updateCommittedEntries();
      descriptor.lastKeyIndexPosition = position;
      descriptor.lastIndexFile = indexFileNo;
    }

    void writeIndexChunk() throws IOException {
      IndexChunkDescriptor d = new IndexChunkDescriptor();
      d.lastIndexFile = descriptor.lastIndexFile;
      d.lastKeyIndexPosition = descriptor.lastKeyIndexPosition;
      d.elementCount = newEntries.size() + deletedEntries.size() + rewriteEntries.size();
      d.write(randomAccessFile);
      FileOutputStream out = new FileOutputStream(randomAccessFile.getFD());
      ObjectOutput oos = keyMarshaller.startOutput(out);
      for (BufferEntry e : newEntries.values()) {
        e.write(oos);
      }
      for (BufferEntry e : deletedEntries.values()) {
        e.writeDeleted(oos);
      }
      for (BufferEntry e : rewriteEntries.values()) {
        e.write(oos);
      }
      oos.close();
      out.close();
    }

    void openFile() throws IOException {
      if (descriptor.lastIndexFile < 0 || forceNewFile) {
        position = 0;
        indexFileNo = 0;
        String _name = generateIndexFileName(indexFileNo);
        randomAccessFile = new RandomAccessFile(_name, "rw");
        randomAccessFile.seek(0);
        randomAccessFile.setLength(0);
      } else {
        String _name = generateIndexFileName(indexFileNo);
        randomAccessFile = new RandomAccessFile(_name, "rw");
        position = (int) randomAccessFile.length();
        randomAccessFile.seek(position);
      }
    }

    /**
     * Partially/fully rewrite the entries within {@link #entriesInEarliestIndex}
     * to be able to remove the earliest index file in the future.
     */
    private void checkForEntriesToRewrite() {
      if (entriesInEarliestIndex.size() > 0) {
        sortOut(entriesInEarliestIndex, newEntries.keySet());
        sortOut(entriesInEarliestIndex, deletedEntries.keySet());
        int _writeCnt = newEntries.size() + deletedEntries.size();
        if (_writeCnt * tunables.rewriteCompleteFactor >= entriesInEarliestIndex.size()) {
          rewriteEntries = entriesInEarliestIndex;
          entriesInEarliestIndex = new HashMap<>();
        } else {
          rewriteEntries = new HashMap<>();
          int cnt = _writeCnt * tunables.rewritePartialFactor;
          Iterator<BufferEntry> it = entriesInEarliestIndex.values().iterator();
          while (cnt > 0 && it.hasNext()) {
            BufferEntry e = it.next();
            rewriteEntries.put(e.key, e);
            cnt--;
          }
          sortOut(entriesInEarliestIndex, rewriteEntries.keySet());
        }
      }
    }

    /**
     * Should we start writing a new index file?
     */
    void checkStartNewIndex() {
      if (committedEntriesCount * tunables.indexFileEntryCapacityFactor
        > committedEntries.size()) {
        forceNewFile = true;
      }
    }

    void updateCommittedEntries() {
      committedEntriesCount -= committedEntries.size();
      committedEntries.putAll(newEntries);
      for (Object k : deletedEntries.keySet()) {
        committedEntries.put(k, new BufferEntry(k, 0, -1));
      }
      committedEntries.putAll(rewriteEntries);
      committedEntriesCount += committedEntries.size();
    }

    /**
     * Free the used space.
     */
    void freeSpace() {
      List<BufferEntry> _freeNow = freeNextCommit;
      freeNextCommit = freeSecondNextCommit;
      freeSecondNextCommit = spaceToFree;
      if (_freeNow != null) {
        final Set<FreeSlot> _freeSet = freeSet;
        synchronized (_freeSet) {
          TreeMap<Long, FreeSlot> _pos2slot = pos2slot;
          for (BufferEntry e : _freeNow) {
            freeEntrySpace(e, _pos2slot, _freeSet);
            freeSpace += e.size;
          }
        }
      }
    }

  }

  String generateIndexFileName(int _fileNo) {
    return fileName + "-" + _fileNo + ".idx";
  }

  class KeyIndexReader {

    int currentlyReadingIndexFile = -1802;
    RandomAccessFile randomAccessFile;
    ByteBuffer indexBuffer;
    Set<Object> readKeys = new HashSet<>();

    void readKeyIndex() throws IOException, ClassNotFoundException {
      entriesInEarliestIndex = committedEntries = new HashMap<>();
      int _fileNo = descriptor.lastIndexFile;
      int _keyPosition = descriptor.lastKeyIndexPosition;
      for (;;) {
        IndexChunkDescriptor d = readChunk(_fileNo, _keyPosition);
        if (readCompleted()) {
          break;
        }
        _fileNo = d.lastIndexFile;
        _keyPosition = d.lastKeyIndexPosition;
      }
      if (randomAccessFile != null) {
        randomAccessFile.close();
      }
      if (entriesInEarliestIndex == committedEntries) {
        entriesInEarliestIndex = new HashMap<>();
      }
      deleteEarlierIndex(_fileNo);
    }

    /**
     * We read until capacity limit is reached or all stored index entries
     * are read. The capacity may be lower then before.
     */
    private boolean readCompleted() {
      return
        values.size() >= descriptor.entryCount ||
        values.size() >= entryCapacity;
    }

    void openFile(int _fileNo) throws IOException {
      if (randomAccessFile != null) {
        randomAccessFile.close();
      }
      entriesInEarliestIndex = new HashMap<>();
      randomAccessFile = new RandomAccessFile(generateIndexFileName(_fileNo), "r");
      indexBuffer = randomAccessFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, randomAccessFile.length());
      currentlyReadingIndexFile = _fileNo;
    }

    IndexChunkDescriptor readChunk(int _fileNo, int _position)
      throws IOException, ClassNotFoundException {
      if (currentlyReadingIndexFile != _fileNo) {
        openFile(_fileNo);
      }
      indexBuffer.position(_position);
      IndexChunkDescriptor d = new IndexChunkDescriptor();
      d.read(indexBuffer);
      ObjectInput in = keyMarshaller.startInput(new ByteBufferInputStream(indexBuffer));
      int cnt = d.elementCount;
      do {
        BufferEntry e = new BufferEntry();
        e.read(in);
        if (!readKeys.contains(e.key)) {
          readKeys.add(e.key);
          entriesInEarliestIndex.put(e.key, e);
          if (!e.isDeleted()) {
            values.put(e.key, e);
          }
          if (readCompleted()) {
            break;
          }
        } else {
          System.err.println("Seen, skipping: " + e);
        }
        cnt--;
      } while (cnt > 0);
      in.close();
      return d;
    }

    /**
     * Remove an earlier index during the read phase. If an index read
     * fails, we may have the option to use an older version.
     */
    void deleteEarlierIndex(int _fileNo) {
      if (_fileNo > 0) {
        String n = generateIndexFileName(_fileNo - 1);
        File f = new File(n);
        if (f.exists()) {
          f.delete();
        }
      }
    }

  }

  /**
   * Calculates an sha1 checksum used for the descriptor. Expected
   * to be always at least {@link #CHECKSUM_BYTES} bytes long.
   */
  byte[] calcCheckSum(byte[] ba) throws IOException {
    try {
      MessageDigest md = MessageDigest.getInstance("sha1");
      byte[] out = md.digest(ba);
      return out;
    } catch (NoSuchAlgorithmException ex) {
      throw new IOException("sha1 missing, never happens?!");
    }
  }

  @Override
  public void setEntryCapacity(int entryCapacity) {
    this.entryCapacity = entryCapacity;
  }

  @Override
  public int getEntryCapacity() {
    return this.entryCapacity;
  }

  @Override
  public void setBytesCapacity(long bytesCapacity) {
    this.bytesCapacity = bytesCapacity;
  }

  static class BufferDescriptor implements Serializable {

    boolean clean = false;
    int lastIndexFile = -1;
    int lastKeyIndexPosition = -1;
    int entryCount = 0;
    int freeSpace = 0;
    long storageCreated;
    long descriptorVersion = 0;
    long writtenTime;
    long highestExpiryTime;

    String keyType;
    String keyMarshallerType;
    String valueType;
    String valueMarshallerType;

    @Override
    public String toString() {
      return "BufferDescriptor{" +
        "clean=" + clean +
        ", lastIndexFile=" + lastIndexFile +
        ", lastKeyIndexPosition=" + lastKeyIndexPosition +
        ", elementCount=" + entryCount +
        ", freeSpace=" + freeSpace +
        ", descriptorVersion=" + descriptorVersion +
        ", writtenTime=" + writtenTime +
        ", highestExpiryTime=" + highestExpiryTime +
        ", keyType='" + keyType + '\'' +
        ", keyMarshallerType='" + keyMarshallerType + '\'' +
        ", valueType='" + valueType + '\'' +
        ", valueMarshallerType='" + valueMarshallerType + '\'' +
        '}';
    }
  }

  static class IndexChunkDescriptor {
    int lastIndexFile;
    int lastKeyIndexPosition;
    int elementCount;

    void read(ByteBuffer buf) {
      lastIndexFile = buf.getInt();
      lastKeyIndexPosition = buf.getInt();
      elementCount = buf.getInt();
    }

    void write(DataOutput buf) throws IOException {
      buf.writeInt(lastIndexFile);
      buf.writeInt(lastKeyIndexPosition);
      buf.writeInt(elementCount);
    }
  }

  static class FreeSlot implements Comparable<FreeSlot> {

    long position;
    int size;

    FreeSlot() { }

    FreeSlot(long position, int size) {
      this.position = position;
      this.size = size;
    }

    long getNextPosition() {
      return position + size;
    }

    @Override
    public int compareTo(FreeSlot o) {
      int d = size - o.size;
      if (d != 0) {
        return d;
      }
      return (position < o.position) ? -1 : ((position == o.position) ? 0 : 1);
    }

  }

  static class BufferEntry {

    Object key;
    long position;
    int size; // size or -1 if deleted

    BufferEntry() {
    }

    BufferEntry(Object key, long position, int size) {
      this.key = key;
      this.position = position;
      this.size = size;
    }

    void write(ObjectOutput out) throws IOException {
      out.writeLong(position);
      out.writeInt(size);
      out.writeObject(key);
    }

    void writeDeleted(ObjectOutput out) throws IOException {
      out.writeLong(0);
      out.writeInt(-1);
      out.writeObject(key);
    }

    /**
     * marks if this key mapping was deleted, so later index entries should not be used.
     * this is never set for in-memory deleted objects.
     */
    boolean isDeleted() {
      return size < 0;
    }

    void read(ObjectInput in) throws IOException, ClassNotFoundException {
      position = in.readLong();
      size = in.readInt();
      key = in.readObject();
    }

    @Override
    public String toString() {
      return "IndexEntry{" +
        "key=" + key +
        ", position=" + position +
        ", size=" + size +
        '}';
    }
  }

  final static int TYPE_MASK = 0x03;
  final static int TYPE_NULL = 0;
  /** Value is marshalled with the value marshaller */
  final static int TYPE_VALUE = 1;
  final static int TYPE_EXCEPTION = 2;
  /** Value is marshalled with the universal marshaller */
  final static int TYPE_UNIVERSAL = 3;
  final static int FLAG_HAS_EXPIRY_TIME = 4;
  final static int FLAG_HAS_LAST_USED = 8;
  final static int FLAG_HAS_MAX_IDLE_TIME = 16;
  final static int FLAG_HAS_CREATED_OR_UPDATED = 32;

  public static long readCompressedLong(ByteBuffer b) {
    short s = b.getShort();
    if (s >= 0) {
      return s;
    }
    long v = s & 0x7fff;
    s = b.getShort();
    if (s >= 0) {
      return v | s << 15;
    }
    v |= (s & 0x07fff) << 15;
    s = b.getShort();
    if (s >= 0) {
      return v | s << 30;
    }
    v |= (s & 0x07fff) << 30;
    s = b.getShort();
    if (s >= 0) {
      return v | s << 45;
    }
    v |= (s & 0x07fff) << 45;
    s = b.getShort();
    return v | s << 60;
  }

  /**
   * Write a long as multiple short values. The msb in the short means
   * that there is another short coming. No support for negative values,
   * when a negative value comes in this method will not terminate
   * and produce a buffer overflow.
   */
  public static void writeCompressedLong(ByteBuffer b, long v) {
    long s = v & 0x07fff;
    while (s != v) {
      b.putShort((short) (s | 0x8000));
      v >>>= 15;
      s = v & 0x07fff;
    }
    b.putShort((short) v);
  }

  public static int calculateCompressedLongSize(long v) {
    int cnt = 1;
    long s = v & 0x07fff;
    while (s != v) {
      cnt++;
      v >>>= 15;
      s = v & 0x07fff;
    }
    return cnt << 1;
  }

  static class MyEntry implements StorageEntry {

    Object key;
    Object value;

    int flags;
    long createdOrUpdated;
    long expiryTime;
    long lastUsed;
    long maxIdleTime;

    public int getTypeNumber() {
      return flags & TYPE_MASK;
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

    void readMetaInfo(ByteBuffer bb, long _timeReference) {
      flags = bb.get();
      if ((flags & FLAG_HAS_CREATED_OR_UPDATED) > 0) {
        createdOrUpdated = readCompressedLong(bb) + _timeReference;
      }
      if ((flags & FLAG_HAS_EXPIRY_TIME) > 0) {
        expiryTime = readCompressedLong(bb) + _timeReference;
      }
      if ((flags & FLAG_HAS_LAST_USED) > 0) {
        lastUsed = readCompressedLong(bb) + _timeReference;
      }
      if ((flags & FLAG_HAS_MAX_IDLE_TIME) > 0) {
        maxIdleTime = readCompressedLong(bb);
      }
    }

    static void writeMetaInfo(ByteBuffer bb, StorageEntry e, long _timeReference, int _type) {
      int _flags =
        _type |
        (e.getExpiryTime() != 0 ? FLAG_HAS_EXPIRY_TIME : 0) |
        (e.getLastUsed() != 0 ? FLAG_HAS_LAST_USED : 0) |
        (e.getMaxIdleTime() != 0 ? FLAG_HAS_MAX_IDLE_TIME : 0) |
        (e.getCreatedOrUpdated() != 0 ? FLAG_HAS_CREATED_OR_UPDATED : 0);
      bb.put((byte) _flags);
      if ((_flags & FLAG_HAS_CREATED_OR_UPDATED) > 0) {
         writeCompressedLong(bb, e.getCreatedOrUpdated() - _timeReference);
      }
      if ((_flags & FLAG_HAS_EXPIRY_TIME) > 0) {
        writeCompressedLong(bb, e.getExpiryTime() - _timeReference);
      }
      if ((_flags & FLAG_HAS_LAST_USED) > 0) {
        writeCompressedLong(bb, e.getLastUsed() - _timeReference);
      }
      if ((_flags & FLAG_HAS_MAX_IDLE_TIME) > 0) {
        writeCompressedLong(bb, e.getMaxIdleTime());
      }
    }

    static int calculateMetaInfoSize(StorageEntry e, long _timeReference, int _type) {
      int _flags =
        _type |
        (e.getExpiryTime() != 0 ? FLAG_HAS_EXPIRY_TIME : 0) |
        (e.getLastUsed() != 0 ? FLAG_HAS_LAST_USED : 0) |
        (e.getMaxIdleTime() != 0 ? FLAG_HAS_MAX_IDLE_TIME : 0) |
        (e.getCreatedOrUpdated() != 0 ? FLAG_HAS_CREATED_OR_UPDATED : 0);
      int cnt = 1;
      if ((_flags & FLAG_HAS_CREATED_OR_UPDATED) > 0) {
        cnt += calculateCompressedLongSize(e.getCreatedOrUpdated() - _timeReference);
      }
      if ((_flags & FLAG_HAS_EXPIRY_TIME) > 0) {
        cnt += calculateCompressedLongSize(e.getExpiryTime() - _timeReference);
      }
      if ((_flags & FLAG_HAS_LAST_USED) > 0) {
        cnt += calculateCompressedLongSize(e.getLastUsed() - _timeReference);
      }
      if ((_flags & FLAG_HAS_MAX_IDLE_TIME) > 0) {
        cnt += calculateCompressedLongSize(e.getMaxIdleTime());
      }
      return cnt;
    }

  }

  /**
   * Some parameters factored out, which may be modified if needed.
   * All these parameters have no effect on the written data format.
   * Usually there is no need to change some of the values. This
   * is basically provided for documentary reason and to have all
   * "magic values" in a central place.
   */
  public static class Tunables {

    /**
     * After more then two index entries are written to an index file,
     * a new index file is started. Old entries are rewritten time after time
     * to make the last file redundant and to free the disk space.
     */
    public int indexFileEntryCapacityFactor = 2;

    public int rewriteCompleteFactor = 3;

    public int rewritePartialFactor = 2;

    /**
     * The storage is expanded by the given increment, if set to 0 it
     * is only expanded by the object size, each time to space is needed.
     * Allocating space for each object separately is a big power drain.
     */
    public int extensionSize = 4096;

  }

}
