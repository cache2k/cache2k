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

import org.cache2k.RootAnyBuilder;
import org.cache2k.StorageConfiguration;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import org.cache2k.storage.FreeSpaceMap.Slot;
import org.cache2k.impl.util.TunableConstants;
import org.cache2k.impl.util.TunableFactory;

import javax.annotation.concurrent.GuardedBy;


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
 * <p/>Possible optimizations: More specialized data structures, compaction.
 *
 * @author Jens Wilke; created: 2014-03-27
 */
public class ImageFileStorage
  implements CacheStorage, FlushableStorage, EntryExpiryUpdateableStorage {

  /** Number of bytes we used for on disk disk checksum of our descriptor */
  final static int CHECKSUM_BYTES = 16;

  final static int DESCRIPTOR_COUNT = 2;

  final static String DESCRIPTOR_MAGIC = "CACHE2K STORAGE 00";

  final static Marshaller DESCRIPTOR_MARSHALLER = new StandardMarshaller();

  final static Marshaller DEFAULT_MARSHALLER = DESCRIPTOR_MARSHALLER;

  boolean dataLost = false;
  Tunable tunable = TunableFactory.get(Tunable.class);
  Marshaller keyMarshaller = DEFAULT_MARSHALLER;
  Marshaller valueMarshaller = DEFAULT_MARSHALLER;
  Marshaller universalMarshaller = DEFAULT_MARSHALLER;
  Marshaller exceptionMarshaller = DEFAULT_MARSHALLER;
  RandomAccessFile file;
  ByteBuffer buffer;

  public FreeSpaceMap freeMap = new FreeSpaceMap();

  @GuardedBy("valuesLock")
  Map<Object, HeapEntry> values;

  final Object valuesLock = new Object();
  final Object commitLock = new Object();

  /** buffer entries added since last commit */
  HashMap<Object, HeapEntry> newBufferEntries;

  /** entries deleted since last commit */
  HashMap<Object, HeapEntry> deletedBufferEntries;

  /**
   * Entries still needed originating from the earliest index file. Every commit
   * entries will be removed that are newly written. Each commit also
   * partially rewrites some of the entries here to make the file redundant.
   */
  HashMap<Object, HeapEntry> entriesInEarliestIndex;

  /**
   * All entries committed to the current index file. Updated within commit phase.
   * This is used to fill up {@link #entriesInEarliestIndex} when starting a
   * new index file. committedEntries is subset of values.
   */
  HashMap<Object, HeapEntry> committedEntries;

  /**
   *
   */
  SlotBucket justUnusedSlots = new SlotBucket();

  /**
   * Protected by commitLock
   */
  Queue<SlotBucket> slotsToFreeQueue = new ArrayDeque<>();

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
  long evictCount = 0;
  long removeCount = 0;
  long freedLastCommit = 0;
  CacheStorageContext context;

  public ImageFileStorage(Tunable t) throws IOException, ClassNotFoundException {
    tunable = t;
  }

  public ImageFileStorage() {
  }

  @Override
  public void open(CacheStorageContext ctx, StorageConfiguration cfg) throws IOException {
    context = ctx;
    if (ctx.getProperties() != null) {
      tunable = TunableFactory.get(ctx.getProperties(), Tunable.class);
    }
    if (fileName == null) {
      fileName =
        "cache2k-storage:" + ctx.getManagerName() + ":" + ctx.getCacheName();
      if (cfg.getLocation() != null) {
        File f = new File(cfg.getLocation());
        if (!f.isDirectory()) {
          throw new IllegalArgumentException("location is not directory");
        }
        fileName = f.getPath() + File.separator + fileName;
      }
    }
    entryCapacity = cfg.getEntryCapacity();
    reopen();
  }

  public void reopen() throws IOException {
    if (freeMap == null) { freeMap = new FreeSpaceMap(); }
    try {
      file = new RandomAccessFile(fileName + ".img", "rw");
      resetBufferFromFile();
      synchronized (freeMap) {
        freeMap.init();
        freeMap.freeSpace(0, (int) file.length());
      }
      if (entryCapacity == Integer.MAX_VALUE) {
        values = new HashMap<>();
      } else {
        values = new LinkedHashMap<Object, HeapEntry>(100, .75F, true) {

          @Override
          protected boolean removeEldestEntry(Map.Entry<Object, HeapEntry> _eldest) {
            if (getEntryCount() > entryCapacity) {
              evict(_eldest.getValue());
              return true;
            }
            return false;
          }

        };
      }
      newBufferEntries = new HashMap<>();
      deletedBufferEntries = new HashMap<>();
      justUnusedSlots = new SlotBucket();
      slotsToFreeQueue = new ArrayDeque<>();
      entriesInEarliestIndex = new HashMap<>();
      committedEntries = new HashMap<>();
      BufferDescriptor d = readLatestIntactBufferDescriptor();
      if (d != null) {
        try {
          descriptor = d;
          initializeFromDisk();
        } catch (IOException ex) {
          System.err.println(fileName + " got IOException: " + ex);
          descriptor = d = null;
        }
      }
      if (d == null) {
        if (buffer.capacity() > 0) {
          dataLost = true;
        }
        initializeNewStorage();
      }
    } catch (ClassNotFoundException e) {
      throw new IOException(e);
    }

  }

  private void initializeFromDisk() throws IOException, ClassNotFoundException {
    MarshallerFactory _factory = context.getMarshallerFactory();
    keyMarshaller = _factory.createMarshaller(descriptor.keyMarshallerParameters);
    valueMarshaller = _factory.createMarshaller(descriptor.valueMarshallerParameters);
    exceptionMarshaller = _factory.createMarshaller(descriptor.exceptionMarshallerParameters);
    readIndex();
  }

  private void initializeNewStorage() throws IOException {
    descriptor = new BufferDescriptor();
    descriptor.storageCreated = System.currentTimeMillis();
    CacheStorageContext ctx = context;
    keyMarshaller = ctx.getMarshallerFactory().createMarshaller(ctx.getKeyType());
    valueMarshaller = ctx.getMarshallerFactory().createMarshaller(ctx.getValueType());
    exceptionMarshaller = ctx.getMarshallerFactory().createMarshaller(Throwable.class);
  }

  public void close() throws Exception {
    synchronized (commitLock) {
      if (file == null) {
        return;
      }
      synchronized (valuesLock) {
        boolean _empty = values.size() == 0;
        fastClose();
        if (_empty) {
          removeFiles();
        }
      }
    }
  }

  /**
   * Remove the files. We do no synchronize here, since cache guarantees we are alone.
   */
  public void clear() throws IOException {
    long _counters = putCount + missCount + hitCount + removeCount + evictCount;
    synchronized (commitLock) {
      synchronized (valuesLock) {
        synchronized (freeMap) {
          if (file != null) {
            fastClose();
          }
          removeFiles();
          reopen();
        }
      }
    }
    try {
      Thread.sleep(7);
    } catch (InterruptedException e) {
    }
    long _counters2 = putCount + missCount + hitCount + removeCount + evictCount;
    if (_counters2 != _counters) {
      throw new IllegalStateException("detected operations while clearing.");
    }
  }

  /**
   * When no entry is in the storage or when clear is
   * called, then remove all files from the filesystem.
   */
  private void removeFiles() {
    for (int i = 0; i < DESCRIPTOR_COUNT; i++) {
      new File(fileName + "-" + i + ".dsc").delete();
    }
    for (int i = descriptor.lastIndexFile; i >= 0; i--) {
      new File(fileName + "-" + i + ".idx").delete();
    }
    new File(fileName + ".img").delete();
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
      values = null;
      freeMap = null;
      buffer = null;
      file.close();
      file = null;
      justUnusedSlots = null;
      slotsToFreeQueue = null;
    }
  }

  public StorageEntry get(Object key)
    throws IOException, ClassNotFoundException {
    HeapEntry be;
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
    HeapEntry be;
    synchronized (valuesLock) {
      be = values.remove(key);
      if (be == null) {
        return null;
      }
      reallyRemove(be);
      removeCount++;
    }
    return returnEntry(be);
  }

  /**
   * Called by remove and when an eviction needs to be done.
   */
  private void reallyRemove(HeapEntry be) {
    deletedBufferEntries.put(be.key, be);
    newBufferEntries.remove(be.key);
    justUnusedSlots.add(be);
  }

  private void evict(HeapEntry be) {
    reallyRemove(be);
    evictCount++;
  }

  private DiskEntry returnEntry(HeapEntry be) throws IOException, ClassNotFoundException {
    ByteBuffer bb = buffer.duplicate();
    bb.position((int) be.position);
    DiskEntry e = new DiskEntry();
    e.entryExpiryTime = be.entryExpireTime;
    e.key = be.key;
    e.readMetaInfo(bb, descriptor.storageCreated);
    int _type = e.getValueTypeNumber();
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
   * space will be made available later in time.
   *
   * <p/>Parallel reads on the same storage entry still read on the now
   * freed space. This is okay since the space will be reallocated later in
   * time. There is no real protection against a put and get race. However,
   * we only get in trouble if the get will need several seconds to
   * finish.
   */
  public void put(StorageEntry e) throws IOException, ClassNotFoundException {
    Object o = e.getValueOrException();
    byte[] _marshalledValue = ZERO_LENGTH_BYTE_ARRAY;
    int _neededSize = 0;
    byte _type;
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
    _neededSize += DiskEntry.calculateMetaInfoSize(e, descriptor.storageCreated, _type);
    ByteBuffer bb;
    HeapEntry _newEntry;
    FreeSpaceMap.Slot s = reserveSpace(_neededSize);
    bb = buffer.duplicate();
    bb.position((int) s.position);
    DiskEntry.writeMetaInfo(bb, e, descriptor.storageCreated, _type);
    int _usedSize = (int) (bb.position() - s.position) + _marshalledValue.length;
    _newEntry = new HeapEntry(e.getKey(), s.position, _usedSize, e.getEntryExpiryTime());
    if (s.size != _usedSize) {
      s.size -=  _usedSize;
      s.position += _usedSize;
      synchronized (freeMap) {
        freeMap.put(s);
      }
    }
    bb.put(_marshalledValue);
    synchronized (valuesLock) {
      HeapEntry be = values.get(e.getKey());
      if (be != null) {
        justUnusedSlots.add(be);
      }
      deletedBufferEntries.remove(e.getKey());
      newBufferEntries.put(e.getKey(), _newEntry);
      values.put(e.getKey(), _newEntry);
      putCount++;
    }
  }

  long calcSize(Collection<HeapEntry> set) {
    long v = 0;
    if (set != null) {
      for (HeapEntry e: set) {
        v += e.size;
      }
    }
    return v;
  }

  long calculateSpaceToFree() {
    long s = justUnusedSlots.getSpaceToFree();
    for (SlotBucket b : slotsToFreeQueue) {
      s += b.getSpaceToFree();
    }
    return s;
  }

  long calculateUsedSpace() {
    long s = 0;
    s += calcSize(values.values());
    s += calculateSpaceToFree();
    return s;
  }

  @Override
  public int getEntryCount() {
    synchronized (valuesLock) {
      return values.size();
    }
  }

  public long getFreeSpace() {
    return freeMap.getFreeSpace();
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

  /**
   * Called when there is no more space available. Allocates new space and
   * returns the area in a free slot. The free slot needs to be inserted
   * in the maps by the caller.
   */
  Slot reserveSpace(int _neededSpace) throws IOException {
    synchronized (freeMap) {
      Slot s = freeMap.findFree(_neededSpace);
      if (s != null) {
        return s;
      }
      long _length = file.length();
      s = freeMap.reserveSlotEndingAt(_length);
      if (s != null) {
        _neededSpace -= s.size;
        s.size += _neededSpace;
      } else {
        s = new Slot(_length, _neededSpace);
      }
      if (tunable.extensionSize >= 2) {
        s.size += tunable.extensionSize - 1;
        s.size -= s.size % tunable.extensionSize;
      }
      file.setLength(s.getNextPosition());
      resetBufferFromFile();
      return s;
    }
  }

  void readIndex() throws IOException, ClassNotFoundException {
    KeyIndexReader r = new KeyIndexReader();
    r.readKeyIndex();
    recalculateFreeSpaceMapAndRemoveDeletedEntries();
  }

  BufferDescriptor readLatestIntactBufferDescriptor() throws IOException, ClassNotFoundException {
    BufferDescriptor bd = null;
    for (int i = 0; i < DESCRIPTOR_COUNT; i++) {
      try {
        BufferDescriptor bd2 = readDescriptor(i);
        if (bd2 != null && (bd == null || bd.descriptorVersion < bd2.descriptorVersion)) {
          bd = bd2;
        }
      } catch (IOException ex) {
      }
    }
    return bd;
  }

  BufferDescriptor readDescriptor(int idx) throws IOException, ClassNotFoundException {
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
    synchronized (freeMap) {
      HashSet<Object> _deletedKey = new HashSet<>();
      for (HeapEntry e : values.values()) {
        if (e.position < 0) {
          _deletedKey.add(e.key);
          continue;
        }
        freeMap.allocateSpace(e.position, e.size);
      }
      for (Object k : _deletedKey) {
        values.remove(k);
      }
    }
  }

  public void flush(FlushContext ctx, long now) throws IOException {
    commit(now);
  }

  public void commit() throws IOException {
    commit(0);
  }

  /**
   * Write key to object index to disk for all modified entries. The implementation only works
   * single threaded.
   *
   * @throws IOException
   */
  public void commit(long now) throws IOException {
    synchronized (commitLock) {
      CommitWorker _worker;
      synchronized (valuesLock) {
        if (newBufferEntries.size() == 0 && deletedBufferEntries.size() == 0) {
          return;
        }
        _worker = new CommitWorker();
        _worker.timestamp = now;
        _worker.newEntries = newBufferEntries;
        _worker.deletedEntries = deletedBufferEntries;
        _worker.workerFreeSlots = justUnusedSlots;
        justUnusedSlots = new SlotBucket();
        newBufferEntries = new HashMap<>();
        deletedBufferEntries = new HashMap<>();
        descriptor.entryCount = getEntryCount();
      }
      _worker.write();
      if (descriptor.keyMarshallerParameters == null) {
        descriptor.keyMarshallerParameters = keyMarshaller.getFactoryParameters();
        descriptor.valueMarshallerParameters = valueMarshaller.getFactoryParameters();
        descriptor.exceptionMarshallerParameters = exceptionMarshaller.getFactoryParameters();
        descriptor.keyType = context.getKeyType().getName();
        descriptor.valueType = context.getValueType().getName();
      }
      writeDescriptor();
      _worker.freeSpace();
    }
  }

  /**
    * Don't write out the oldest entries that we also have in our updated lists.
    */
  static void sortOut(Map<Object, HeapEntry> map, Set<Object> _keys) {
    for (Object k: _keys) {
      map.remove(k);
    }
  }

  class CommitWorker {

    long timestamp;
    RandomAccessFile randomAccessFile;
    HashMap<Object, HeapEntry> newEntries;
    HashMap<Object, HeapEntry> deletedEntries;
    HashMap<Object, HeapEntry> rewriteEntries = new HashMap<>();
    SlotBucket workerFreeSlots;
    int indexFileNo;
    long position;

    boolean forceNewFile = false;

    void write() throws IOException {
      indexFileNo = descriptor.lastIndexFile;
      checkForEntriesToRewrite();
      checkStartNewIndex();
      if (forceNewFile) {
        entriesInEarliestIndex = committedEntries;
        committedEntries = new HashMap<>();
        descriptor.indexEntries = 0;
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
      descriptor.indexEntries +=
        totalEntriesToWrite();
      descriptor.lastKeyIndexPosition = position;
      descriptor.lastIndexFile = indexFileNo;
    }

    private int totalEntriesToWrite() {
      return newEntries.size() + deletedEntries.size() + rewriteEntries.size();
    }

    void writeIndexChunk() throws IOException {
      IndexChunkDescriptor d = new IndexChunkDescriptor();
      d.lastIndexFile = descriptor.lastIndexFile;
      d.lastKeyIndexPosition = descriptor.lastKeyIndexPosition;
      d.elementCount = totalEntriesToWrite();
      d.write(randomAccessFile);
      FileOutputStream out = new FileOutputStream(randomAccessFile.getFD());
      ObjectOutput oos = keyMarshaller.startOutput(out);
      for (HeapEntry e : newEntries.values()) {
        e.write(oos);
      }
      for (HeapEntry e : deletedEntries.values()) {
        e.writeDeleted(oos);
      }
      for (HeapEntry e : rewriteEntries.values()) {
        e.write(oos);
      }
      oos.close();
      out.close();
    }

    void openFile() throws IOException {
      if (indexFileNo == -1 || forceNewFile) {
        position = 0;
        indexFileNo++;
        String _name = generateIndexFileName(indexFileNo);
        randomAccessFile = new RandomAccessFile(_name, "rw");
        randomAccessFile.seek(0);
        randomAccessFile.setLength(0);
      } else {
        String _name = generateIndexFileName(indexFileNo);
        randomAccessFile = new RandomAccessFile(_name, "rw");
        position = randomAccessFile.length();
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
        if (_writeCnt * tunable.rewriteCompleteFactor >= entriesInEarliestIndex.size()) {
          rewriteEntries = entriesInEarliestIndex;
          entriesInEarliestIndex = new HashMap<>();
        } else {
          rewriteEntries = new HashMap<>();
          int cnt = _writeCnt * tunable.rewritePartialFactor;
          Iterator<HeapEntry> it = entriesInEarliestIndex.values().iterator();
          while (cnt > 0 && it.hasNext()) {
            HeapEntry e = it.next();
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
      int _totalEntriesInIndexFile = descriptor.indexEntries + totalEntriesToWrite();
      if (_totalEntriesInIndexFile > descriptor.entryCount * tunable.indexFileFactor) {
        forceNewFile = true;
      }
    }

    void updateCommittedEntries() {
      committedEntries.putAll(newEntries);
      for (Object k : deletedEntries.keySet()) {
        committedEntries.put(k, new HeapEntry(k, 0, -1, 0));
      }
      committedEntries.putAll(rewriteEntries);
    }

    /**
     * Free the used space.
     */
    void freeSpace() {
      workerFreeSlots.time = timestamp;
      slotsToFreeQueue.add(workerFreeSlots);
      SlotBucket b = slotsToFreeQueue.peek();
      long _before = freeMap.getFreeSpace();
      while ((b.time + tunable.freeSpaceAfterMillis) <= timestamp) {
        b = slotsToFreeQueue.remove();
        synchronized (freeMap) {
          for (Slot s : b) {
            freeMap.freeSpace(s);
          }
        }
        b = slotsToFreeQueue.peek();
      }
      freedLastCommit = _before - freeMap.getFreeSpace();
    }

  }

  String generateIndexFileName(int _fileNo) {
    return fileName + "-" + _fileNo + ".idx";
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
  public void visit(final VisitContext ctx, final EntryFilter f, final EntryVisitor v) throws Exception {
    ArrayList<HeapEntry> _allEntries;
    synchronized (valuesLock) {
      _allEntries = new ArrayList<>(values.size());
      for (HeapEntry e : values.values()) {
        if (f == null || f.shouldInclude(e.key)) {
          _allEntries.add(e);
        }
      }
    }
    ExecutorService ex = ctx.getExecutorService();
    for (HeapEntry e : _allEntries) {
      if (ctx.shouldStop()) {
        break;
      }
      final HeapEntry be = e;
      Callable<Object> r = new Callable<Object>() {
        @Override
        public Object call() throws Exception {
          v.visit(returnEntry(be));
          return be.key;
        }
      };
      ex.submit(r);
    }
  }

  @Override
  public void updateEntryExpireTime(Object key, long _millis) throws Exception {
    synchronized (valuesLock) {
      HeapEntry e = values.get(key);
      if (e != null) {
        e.entryExpireTime = _millis;
      }
    }
  }

  @Override
  public void purge(PurgeContext ctx,
                    long _valueExpireTime,
                    long _entryExpireTime) throws Exception {
    throw new UnsupportedOperationException();
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

  public long getUsedSpace() {
    return getTotalValueSpace() - getFreeSpace();
  }

  @Override
  public String toString() {
    return "DirectFileStorage(" + fileName + ", " +
      "entryCapacity=" + entryCapacity + ", " +
      "entryCnt=" + values.size() + ", " +
      "totalSpace=" + getTotalValueSpace() + ", " +
      "usedSpace=" + getUsedSpace() + ", " +
      "calculatedUsedSpace=" + calculateUsedSpace() + ", " +
      "freeSpace=" + freeMap.getFreeSpace() + ", " +
      "spaceToFree=" + calculateSpaceToFree() + ", " +
      "freeSlots=" + freeMap.getSlotCount() + ", " +
      "smallestSlot=" + freeMap.getSizeOfSmallestSlot() + ", " +
      "largestSlot=" + freeMap.getSizeOfLargestSlot() + ", " +
      "lastIndexNo=" + descriptor.lastIndexFile +", "+
      "hitCnt=" + hitCount + ", " +
      "missCnt=" + missCount + ", " +
      "putCnt=" + putCount + ", " +
      "evictCnt=" + evictCount + ", " +
      "removeCnt=" + removeCount + ", " +
      "bufferDescriptor=" + descriptor + ")";
  }

  class KeyIndexReader {

    int currentlyReadingIndexFile = -1802;
    RandomAccessFile randomAccessFile;
    ByteBuffer indexBuffer;
    Set<Object> readKeys = new HashSet<>();

    void readKeyIndex() throws IOException, ClassNotFoundException {
      entriesInEarliestIndex = committedEntries = new HashMap<>();
      int _fileNo = descriptor.lastIndexFile;
      long _keyPosition = descriptor.lastKeyIndexPosition;
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

    IndexChunkDescriptor readChunk(int _fileNo, long _position)
      throws IOException, ClassNotFoundException {
      if (currentlyReadingIndexFile != _fileNo) {
        openFile(_fileNo);
      }
      indexBuffer.position((int) _position);
      IndexChunkDescriptor d = new IndexChunkDescriptor();
      d.read(indexBuffer);
      ObjectInput in = keyMarshaller.startInput(new ByteBufferInputStream(indexBuffer));
      int cnt = d.elementCount;
      int _readCnt = readKeys.size();
      do {
        HeapEntry e = new HeapEntry(in);
        if (!readKeys.contains(e.key)) {
          readKeys.add(e.key);
          entriesInEarliestIndex.put(e.key, e);
          if (!e.isDeleted()) {
            values.put(e.key, e);
          }
          if (readCompleted()) {
            break;
          }
        }
        cnt--;
      } while (cnt > 0);
      in.close();
      if (_readCnt == readKeys.size()) {
        throw new IOException("no new data, at index: " + _fileNo + "/" + _position);
      }
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

  static class BufferDescriptor implements Serializable {

    boolean clean = false;
    int lastIndexFile = -1;
    long lastKeyIndexPosition = -1;
    /** Count of entries in the last index */
    int indexEntries = 0;
    int entryCount = 0;
    int freeSpace = 0;
    long storageCreated;
    long descriptorVersion = 0;
    long writtenTime;

    MarshallerFactory.Parameters keyMarshallerParameters;
    MarshallerFactory.Parameters valueMarshallerParameters;
    MarshallerFactory.Parameters exceptionMarshallerParameters;

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
        ", keyType='" + keyType + '\'' +
        ", keyMarshallerType='" + keyMarshallerType + '\'' +
        ", valueType='" + valueType + '\'' +
        ", valueMarshallerType='" + valueMarshallerType + '\'' +
        '}';
    }
  }

  static class IndexChunkDescriptor {
    int lastIndexFile;
    long lastKeyIndexPosition;
    int elementCount;

    void read(ByteBuffer buf) {
      lastIndexFile = buf.getInt();
      lastKeyIndexPosition = buf.getLong();
      elementCount = buf.getInt();
    }

    void write(DataOutput buf) throws IOException {
      buf.writeInt(lastIndexFile);
      buf.writeLong(lastKeyIndexPosition);
      buf.writeInt(elementCount);
    }
  }

  /**
   * Entry data kept in the java heap.
   */
  static class HeapEntry {

    Object key;
    long position;
    int size; // size or -1 if deleted
    long entryExpireTime;

    HeapEntry(ObjectInput in) throws IOException, ClassNotFoundException {
      position = in.readLong();
      size = in.readInt();
      key = in.readObject();
      entryExpireTime = in.readLong();
    }

    HeapEntry(Object key, long position, int size, long entryExpireTime) {
      this.key = key;
      this.position = position;
      this.size = size;
      this.entryExpireTime = entryExpireTime;
    }

    void write(ObjectOutput out) throws IOException {
      out.writeLong(position);
      out.writeInt(size);
      out.writeObject(key);
      out.writeLong(entryExpireTime);
    }

    void writeDeleted(ObjectOutput out) throws IOException {
      out.writeLong(0);
      out.writeInt(-1);
      out.writeObject(key);
      out.writeLong(0);
    }

    /**
     * marks if this key mapping was deleted, so later index entries should not be used.
     * this is never set for in-memory deleted objects.
     */
    boolean isDeleted() {
      return size < 0;
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
  final static int FLAG_HAS_VALUE_EXPIRY_TIME = 4;
  final static int FLAG_HAS_LAST_USED = 8;
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

  /**
   * This object represents the data that is written and read from the disk.
   */
  static class DiskEntry implements StorageEntry {

    Object key;
    Object value;

    int flags;

    /* set from the buffer entry */
    long valueExpiryTime;
    long createdOrUpdated;
    long entryExpiryTime;

    public int getValueTypeNumber() {
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
    public long getValueExpiryTime() {
      return valueExpiryTime;
    }

    @Override
    public long getEntryExpiryTime() {
      return entryExpiryTime;
    }

    void readMetaInfo(ByteBuffer bb, long _timeReference) {
      flags = bb.get();
      if ((flags & FLAG_HAS_CREATED_OR_UPDATED) > 0) {
        createdOrUpdated = readCompressedLong(bb) + _timeReference;
      }
      if ((flags & FLAG_HAS_VALUE_EXPIRY_TIME) > 0) {
        valueExpiryTime = readCompressedLong(bb) + _timeReference;
      }
    }

    static void writeMetaInfo(ByteBuffer bb, StorageEntry e, long _timeReference, int _type) {
      int _flags =
        _type |
        (e.getEntryExpiryTime() != 0 ? FLAG_HAS_LAST_USED : 0) |
        (e.getCreatedOrUpdated() != 0 ? FLAG_HAS_CREATED_OR_UPDATED : 0);
      bb.put((byte) _flags);
      if ((_flags & FLAG_HAS_CREATED_OR_UPDATED) > 0) {
         writeCompressedLong(bb, e.getCreatedOrUpdated() - _timeReference);
      }
      if ((_flags & FLAG_HAS_VALUE_EXPIRY_TIME) > 0) {
        writeCompressedLong(bb, e.getValueExpiryTime() - _timeReference);
      }
    }

    static int calculateMetaInfoSize(StorageEntry e, long _timeReference, int _type) {
      int _flags =
        _type |
        (e.getValueExpiryTime() != 0 ? FLAG_HAS_VALUE_EXPIRY_TIME : 0) |
        (e.getCreatedOrUpdated() != 0 ? FLAG_HAS_CREATED_OR_UPDATED : 0);
      int cnt = 1;
      if ((_flags & FLAG_HAS_CREATED_OR_UPDATED) > 0) {
        cnt += calculateCompressedLongSize(e.getCreatedOrUpdated() - _timeReference);
      }
      if ((_flags & FLAG_HAS_VALUE_EXPIRY_TIME) > 0) {
        cnt += calculateCompressedLongSize(e.getValueExpiryTime() - _timeReference);
      }
      return cnt;
    }

  }

  public static class SlotBucket implements Iterable<Slot> {

    long time;
    Collection<Slot> slots = new ArrayList<>();

    public void add(HeapEntry be) {
      add(be.position, be.size);
    }

    public void add(Slot s) {
      slots.add(s);
    }

    public void add(long _position, int _size) {
      add(new Slot(_position, _size));
    }

    public long getSpaceToFree() {
      long n = 0;
      for (Slot s : slots) {
        n += s.size;
      }
      return n;
    }

    @Override
    public Iterator<Slot> iterator() {
      return slots.iterator();
    }
  }

  /**
   * Some parameters factored out, which may be modified if needed.
   * All these parameters have no effect on the written data format.
   * Usually there is no need to change some of the values. This
   * is basically provided for documentary reason and to have all
   * "magic values" in a central place.
   */
  public static class Tunable extends TunableConstants {

    /**
     * Factor of the entry count in the storage to limit the index
     * file size. After the limit a new file is started.
     * Old entries are rewritten time after time to make the last
     * file redundant and to free the disk space.
     */
    public int indexFileFactor = 3;

    public int rewriteCompleteFactor = 3;

    public int rewritePartialFactor = 2;

    /**
     * The storage is expanded by the given increment, if set to 0 it
     * is only expanded by the object size, each time to space is needed.
     * Allocating space for each object separately is a big power drain.
     */
    public int extensionSize = 4096;

    /**
     * Time after unused space is finally freed and maybe reused.
     * We cannot reuse space immediately or do an update in place, since
     * there may be ongoing read requests.
     */
    public int freeSpaceAfterMillis = 15 * 1000;

  }

  public static class Provider<R extends RootAnyBuilder<R, T>, T>
    extends CacheStorageBaseWithVoidConfig<R, T>
    implements SimpleSingleFileStorage<R, T> {

    @Override
    public ImageFileStorage create(CacheStorageContext ctx, StorageConfiguration cfg) throws IOException {
      ImageFileStorage img = new ImageFileStorage();
      img.open(ctx, cfg);
      return img;
    }

  }

}
