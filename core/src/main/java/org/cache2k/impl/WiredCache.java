package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.CacheEntry;
import org.cache2k.CacheEntryProcessor;
import org.cache2k.CacheException;
import org.cache2k.CacheManager;
import org.cache2k.CacheSourceWithMetaInfo;
import org.cache2k.CacheWriter;
import org.cache2k.ClosableIterator;
import org.cache2k.EntryProcessingResult;
import org.cache2k.FetchCompletedListener;
import org.cache2k.experimentalApi.AsyncCacheLoader;
import org.cache2k.experimentalApi.AsyncCacheWriter;
import org.cache2k.impl.util.Log;
import org.cache2k.storage.StorageCallback;
import org.cache2k.storage.StorageEntry;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * @author Jens Wilke
 */
public class WiredCache<K, V> implements InternalCache<K, V>, StorageAdapter.Parent {

  BaseCache<K,V> heapCache;
  StorageAdapter storage;
  CacheSourceWithMetaInfo<K, V> source;
  CacheWriter<K, V> writer;

  @Override
  public Future<Void> cancelTimerJobs() {
    return heapCache.cancelTimerJobs();
  }

  @Override
  public Log getLog() {
    return heapCache.getLog();
  }

  /** For testing */
  public BaseCache getHeapCache() {
    return heapCache;
  }

  @Override
  public void resetStorage(final StorageAdapter _current, final StorageAdapter _new) {
    heapCache.resetStorage(_current, _new);
  }

  @Override
  public V peekAndPut(final K key, final V value) {
    return execute(key, new UpdateExisting<K, V, V>() {
      @Override
      public void update(EntryProgress<V, V> c, Entry<K, V> e) {
        if (e.hasFreshData()) {
          c.result(e.getValueOrException());
        }
        c.put(value);
      }
    });
  }

  @Override
  public V peekAndRemove(K key) {
    return heapCache.peekAndRemove(key);
  }

  @Override
  public V peekAndReplace(K key, V _value) {
    return heapCache.peekAndReplace(key, _value);
  }

  @Override
  public CacheEntry<K, V> peekEntry(K key) {
    return heapCache.peekEntry(key);
  }

  @Override
  public void prefetch(K key) {
    heapCache.prefetch(key);
  }

  @Override
  public void prefetch(List<K> keys, int _startIndex, int _afterEndIndex) {
    heapCache.prefetch(keys, _startIndex, _afterEndIndex);
  }

  @Override
  public void prefetch(Set<K> keys) {
    heapCache.prefetch(keys);
  }

  @Override
  public void put(K key, V value) {
    heapCache.put(key, value);
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    heapCache.putAll(m);
  }

  @Override
  public boolean putIfAbsent(K key, V value) {
    return heapCache.putIfAbsent(key, value);
  }

  @Override
  public void remove(K key) {
    heapCache.remove(key);
  }

  @Override
  public boolean remove(K key, V value) {
    return heapCache.remove(key, value);
  }

  @Override
  public boolean removeWithFlag(K key) {
    return heapCache.removeWithFlag(key);
  }

  @Override
  public void removeAll() {
    heapCache.removeAll();
  }

  @Override
  public void removeAllAtOnce(Set<K> key) {
    heapCache.removeAllAtOnce(key);
  }

  @Override
  public boolean replace(K key, V _newValue) {
    return heapCache.replace(key, _newValue);
  }

  @Override
  public boolean replace(K key, V _oldValue, V _newValue) {
    return heapCache.replace(key, _oldValue, _newValue);
  }

  @Override
  public String getName() {
    return heapCache.getName();
  }

  @Override
  public StorageAdapter getStorage() {
    return heapCache.getStorage();
  }

  @Override
  public Class<?> getValueType() {
    return heapCache.getValueType();
  }

  @Override
  public CacheEntry<K, V> replaceOrGet(K key, V _oldValue, V _newValue, CacheEntry<K, V> _dummyEntry) {
    return heapCache.replaceOrGet(key, _oldValue, _newValue, _dummyEntry);
  }

  @Override
  public boolean contains(K key) {
    return heapCache.contains(key);
  }

  @Override
  public void fetchAll(Set<? extends K> keys, boolean replaceExistingValues, FetchCompletedListener l) {
    heapCache.fetchAll(keys, replaceExistingValues, l);
  }

  @Override
  public V get(K key) {
    Entry<K, V> e;
    final int hc =  heapCache.modifiedHash(key.hashCode());
    e = heapCache.lookupEntryUnsynchronized(key, hc);
    if (e != null && e.hasFreshData()) {
      return heapCache.returnValue(e.getValueOrException());
    }
    return heapCache.returnValue(execute(key, e, new ExamineExisting<K, V, V>() {

      @Override
      public void examine(EntryProgress<V, V> c, Entry<K, V> e) {
        if (e.hasFreshData()) {
          c.result(e.getValueOrException());
        } else {
          c.wantMutation();
        }
      }

      @Override
      public void update(final EntryProgress<V, V> c, final Entry<K, V> e) {
        c.load();
      }
    }));
   }

  @Override
  public Map<K, V> getAll(Set<? extends K> keys) {
    return heapCache.getAll(keys);
  }

  @Override
  public CacheManager getCacheManager() {
    return heapCache.getCacheManager();
  }

  @Override
  public CacheEntry<K, V> getEntry(K key) {
    return heapCache.getEntry(key);
  }

  @Override
  public int getTotalEntryCount() {
    return heapCache.getTotalEntryCount();
  }

  @Override
  public <R> R invoke(K key, CacheEntryProcessor<K, V, R> entryProcessor, Object... _args) {
    return heapCache.invoke(key, entryProcessor, _args);
  }

  @Override
  public <R> Map<K, EntryProcessingResult<R>> invokeAll(Set<? extends K> keys, CacheEntryProcessor<K, V, R> p, Object... _objs) {
    return heapCache.invokeAll(keys, p, _objs);
  }

  @Override
  public boolean isClosed() {
    return heapCache.isClosed();
  }

  @Override
  public ClosableIterator<CacheEntry<K, V>> iterator() {
    return heapCache.iterator();
  }

  @Override
  public V peek(K key) {
    final int hc =  heapCache.modifiedHash(key.hashCode());
    Entry<K, V> e = heapCache.lookupEntryUnsynchronized(key, hc);
    if (e != null && e.hasFreshData()) {
      return heapCache.returnValue(e.getValueOrException());
    }
    return heapCache.returnValue(execute(key, new ExamineExisting<K, V, V>() {
      @Override
      public void examine(EntryProgress<V, V> c, Entry<K, V> e) {
        if (e.hasFreshData()) {
          c.result(e.getValueOrException());
        }
      }
    }));
  }

  @Override
  public Map<K, V> peekAll(Set<? extends K> keys) {
    return heapCache.peekAll(keys);
  }

  @Override
  public InternalCacheInfo getLatestInfo() {
    return heapCache.getLatestInfo();
  }

  @Override
  public Class<?> getKeyType() {
    return heapCache.getKeyType();
  }

  @Override
  public InternalCacheInfo getInfo() {
    return heapCache.getInfo();
  }

  @Override
  public void clearTimingStatistics() {
    heapCache.clearTimingStatistics();
  }

  @Override
  public void checkIntegrity() {
    heapCache.checkIntegrity();
  }

  @Override
  public void purge() {
    heapCache.purge();
  }

  @Override
  public void flush() {
    heapCache.flush();
  }

  @Override
  public void clear() {
    heapCache.clear();
  }

  @Override
  public void destroy() {
    heapCache.destroy();
  }

  @Override
  public void close() {
    heapCache.close();
  }

  @Override
  public String toString() {
    return heapCache.toString();
  }

  @Override
  public <X> X requestInterface(Class<X> _type) {
    return heapCache.requestInterface(_type);
  }

  interface ExecutableEntryOperation<K, V, R> {
    void setup(EntryProgress<V, R> c);
    void examine(EntryProgress<V, R> c, Entry<K,V> e);
    void update(EntryProgress<V, R> c, Entry<K,V> e);
    /** Called after the load completes. The entry does is not yet altered. */
    void loaded(EntryProgress<V, R> c, Entry<K,V> e, V v);
  }

  static abstract class AbstractExecutableEntryOperation<K, V, R> implements ExecutableEntryOperation<K, V, R> {

    /**
     * By default a load returns always the value as result. May be overridden to change.
     *
     * @param e the entry containing the old data
     * @param _newValueOrException  value returned from the loader
     */
    @Override
    public void loaded(final EntryProgress<V, R> c, final Entry<K, V> e, V _newValueOrException) {
      c.result((R) _newValueOrException);
    }

  }

  static abstract class UpdateExisting<K, V, R> extends AbstractExecutableEntryOperation<K, V, R> {

    @Override
    public void examine(EntryProgress<V, R> c, Entry<K, V> e) {
      c.wantMutation();
    }

    @Override
    public void setup(EntryProgress<V, R> c) {
      c.wantData();
    }

  }

  static abstract class ExamineExisting<K, V, R> extends AbstractExecutableEntryOperation<K, V, R> {

    @Override
    public void setup(EntryProgress<V, R> c) {
      c.wantData();
    }

    @Override
    public void update(EntryProgress<V, R> c, Entry<K, V> e) { }

  }
  <R> R execute(K key, Entry<K, V> e, ExecutableEntryOperation<K, V, R> op) {
    EntryAction<R> _action = new EntryAction<R>(op, key, e);
    op.setup(_action);
    Throwable t = _action.exceptionToPropagate;
    if (t != null) {
      if (t instanceof RuntimeException) {
        throw ((RuntimeException) t);
      } else {
        throw new CacheException(t);
      }
    }
    return _action.result;
  }

  <R> R execute(K key, ExecutableEntryOperation<K, V, R> op) {
    return execute(key, null, op);
  }

  interface EntryProgress<V, R> {

    void wantData();
    void wantMutation();
    void result(R result);
    void finish();
    void finish(R result);
    void load();
    void remove();
    void put(V value);

  }

  static final Entry NON_FRESH_DUMMY = new Entry();

  @SuppressWarnings("SynchronizeOnNonFinalField")
  class EntryAction<R> implements StorageCallback, AsyncCacheLoader.CompletedListener<V>, AsyncCacheWriter.CompletedListener, EntryProgress<V, R> {

    K key;
    ExecutableEntryOperation<K,V,R> operation;
    Entry<K, V> entry;
    V newValueOrException;
    R result;
    long lastModificationTime;
    long loadStartedTime;
    long loadCompletedTime;
    Throwable exceptionToPropagate;
    boolean remove;
    long expiry = 0;
    /** We locked the entry, don't lock it again. */
    boolean entryLocked = false;
    boolean needsFinish = true;

    boolean storageRead = false;
    boolean storageMiss = false;
    boolean storageNonFresh = false;
    boolean heapHitButNotFresh = false;
    boolean heapMiss = false;

    public EntryAction(ExecutableEntryOperation<K, V, R> op, K k, Entry<K,V> e) {
      operation = op;
      key = k;
      if (e != null) {
        entry = e;
      } else {
        entry = NON_FRESH_DUMMY;
      }
    }

    @Override
    public void wantData() {
      if (storage == null) {
        retrieveDataFromHeap();
        return;
      }
      lockEntryForStorageRead();
    }

    public void retrieveDataFromHeap() {
      Entry<K, V> e = entry;
      if (e == NON_FRESH_DUMMY) {
        e = heapCache.lookupEntrySynchronized(key);
        if (e == null) {
          heapMiss();
          return;
        }
      }
      if (!e.hasFreshData()) {
        heapHitButNotFresh(e);
        return;
      }
      heapHitAndFresh(e);
    }

    public void lockEntryForStorageRead() {
      int _spinCount = BaseCache.TUNABLE.maximumEntryLockSpins;
      Entry<K,V> e = entry;
      boolean _needStorageRead = false;
      if (e == NON_FRESH_DUMMY) {
        e = heapCache.lookupOrNewEntrySynchronized(key);
      }
      for (;;) {
        if (_spinCount-- <= 0) {
          throw new CacheLockSpinsExceededError();
        }
        if (e.hasFreshData()) {
          heapHitAndFresh(e);
          return;
        }
        synchronized (e) {
          e.waitForFetch();
          if (!e.isRemovedState()) {
            if (e.isVirgin()) {
              storageRead = _needStorageRead = true;
              e.startFetch(Entry.ProcessingState.READ);
              entryLocked = true;
            }
            break;
          }
        }
        e = heapCache.lookupOrNewEntrySynchronized(key);
      }
      if (_needStorageRead) {
        entry = e;
        storageRead();
        return;
      }
      heapHitButNotFresh(e);
    }

    public void storageRead() {
      StorageEntry se;
      try {
         se = storage.get(entry.key);
      } catch (Throwable ex) {
        readException(ex);
        return;
      }
      readComplete(se);
    }

    @Override
    public void readException(Throwable t) {
      examinationAbort(t);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void readComplete(StorageEntry se) {
      if (se == null) {
        storageReadMiss();
        return;
      }
      storageReadHit(se);
    }

    public void storageReadMiss() {
      Entry<K, V> e = entry;
      e.nextRefreshTime = Entry.READ_NON_VALID;
      storageMiss = true;
      examine();
    }

    public void storageReadHit(StorageEntry se) {
      Entry<K, V> e = entry;
      e.setLastModificationFromStorage(se.getCreatedOrUpdated());
      long now = System.currentTimeMillis();
      V v = (V) se.getValueOrException();
      e.value = v;
      long _nextRefreshTime = heapCache.maxLinger == 0 ? 0 : Long.MAX_VALUE;
      long _expiryTimeFromStorage = se.getValueExpiryTime();
      boolean _expired = _expiryTimeFromStorage != 0 && _expiryTimeFromStorage <= now;
      if (!_expired) {
        _nextRefreshTime = heapCache.calcNextRefreshTime((K) se.getKey(), v, se.getCreatedOrUpdated(), null);
        expiry = _nextRefreshTime;
        if (_nextRefreshTime == -1 || _nextRefreshTime == Long.MAX_VALUE) {
          e.nextRefreshTime = Entry.FETCHED_STATE;
        } else if (_nextRefreshTime == 0) {
          e.nextRefreshTime = Entry.READ_NON_VALID;
        } else {
          if (_nextRefreshTime < 0) {
            _nextRefreshTime = -_nextRefreshTime;
          }
          if (_nextRefreshTime <= now) {
            e.nextRefreshTime = Entry.READ_NON_VALID;
          } else {
            e.nextRefreshTime = - _nextRefreshTime;
          }
        }
      } else {
        e.nextRefreshTime = Entry.READ_NON_VALID;
      }
      e.nextProcessingStep(Entry.ProcessingState.READ_COMPLETE);
      examine();
    }

    public void heapHitButNotFresh(Entry<K, V> e) {
      entry = e;
      examine();
    }

    public void heapMiss() {
      heapMiss = true;
      examine();
    }

    public void heapHitAndFresh(Entry<K, V> e) {
      entry = e;
      examine();
    }

    public void examine() {
      operation.examine(this, entry);
      if (needsFinish) {
        finish();
      }
    }

    @Override
    public void wantMutation() {
      heapHitButNotFresh = false;
      heapMiss = false;
      if (!entryLocked) {
        lockFor(Entry.ProcessingState.MUTATE);
        operation.examine(this, entry);
        if (needsFinish) {
          finish();
        }
        return;
      }
      operation.update(this, entry);
      if (needsFinish) {
        finish();
      }
    }

    @Override
    public void finish() {
      needsFinish = false;
      noMutationRequested();
    }

    @Override
    public void finish(final R result) {
      needsFinish = false;
      this.result = result;
      noMutationRequested();
    }

    @Override
    public void load() {
      if (!entry.isVirgin() && !storageRead) {
        synchronized (heapCache.lock) {
          heapCache.loadButHitCnt++;
        }
      }
      if (source == null) {
        exceptionToPropagate = new CacheUsageExcpetion("source not set");
        synchronized (heapCache.lock) {
          if (entry.isVirgin()) {
            heapCache.loadFailedCnt++;
          }
        }
        return;
      }
      needsFinish = false;
      entry.nextProcessingStep(Entry.ProcessingState.LOAD);
      Entry<K, V> e = entry;
      long t0 = lastModificationTime = loadStartedTime = System.currentTimeMillis();
      V v;
      try {
        if (e.isVirgin() || e.hasException()) {
          v = source.get(e.key, t0, null, e.getLastModification());
        } else {
          v = source.get(e.key, t0, e.getValue(), e.getLastModification());
        }
      } catch (Throwable _ouch) {
        loadException(_ouch);
        return;
      }
      loadComplete(v);
    }

    void lockFor(Entry.ProcessingState ps) {
      if (entryLocked) {
        entry.nextProcessingStep(ps);
        return;
      }
      Entry<K, V> e = entry;
      if (e == NON_FRESH_DUMMY) {
        e = heapCache.lookupOrNewEntrySynchronized(key);
      }
      int _spinCount = BaseCache.TUNABLE.maximumEntryLockSpins;
      for (;;) {
        if (_spinCount-- <= 0) {
          throw new CacheLockSpinsExceededError();
        }
        synchronized (e) {
          e.waitForFetch();
          if (!e.isRemovedState()) {
            e.startFetch(ps);
            entryLocked = true;
            entry = e;
            return;
          }
        }
        e = heapCache.lookupOrNewEntrySynchronized(key);
      }
    }

    @Override
    public void loadComplete(V value) {
      newValueOrException = value;
      loadCompleted();
    }

    @Override
    public void loadException(Throwable t) {
      synchronized (heapCache.lock) {
        heapCache.loadExceptionCnt++;
      }
      newValueOrException = (V) new ExceptionWrapper(t);
      loadCompleted();
    }

    public void loadCompleted() {
      entry.nextProcessingStep(Entry.ProcessingState.LOAD_COMPLETE);
      loadCompletedTime = System.currentTimeMillis();
      mutationCalculateExpiry();
    }

    @Override
    public void result(R r) {
      result = r;
    }

    @Override
    public void put(V value) {
      needsFinish = false;
      newValueOrException = value;
      lastModificationTime = System.currentTimeMillis();
      mutationCalculateExpiry();
    }

    @Override
    public void remove() {
      needsFinish = false;
      remove = true;
      lastModificationTime = System.currentTimeMillis();
      mutationMayCallWriter();
    }

    public void mutationCalculateExpiry() {
      try {
        entry.nextProcessingStep(Entry.ProcessingState.EXPIRY);
        if (heapCache.timer != null) {
          expiry = heapCache.calculateNextRefreshTime(
            entry, newValueOrException,
            lastModificationTime, entry.nextRefreshTime);
        } else {
          expiry = heapCache.maxLinger == 0 ? 0 : Long.MAX_VALUE;
        }
      } catch (Exception ex) {
        expiryCalculationException(ex);
        return;
      }
      expiryCalculated();
    }

    public void expiryCalculated() {
      entry.nextProcessingStep(Entry.ProcessingState.EXPIRY_COMPLETE);
      if (loadCompletedTime > 0) {
        boolean _suppressException = heapCache.needsSuppress(entry, newValueOrException);
        if (_suppressException) {
          newValueOrException = entry.getValue();
          lastModificationTime = entry.getLastModification();
          synchronized (heapCache.lock) {
            heapCache.suppressedExceptionCnt++;
          }
        } else {
          if (newValueOrException instanceof ExceptionWrapper) {
            ExceptionWrapper ew = (ExceptionWrapper) newValueOrException;
            if (expiry < 0) {
              ew.until = -expiry;
            } else if (expiry > Entry.EXPIRY_TIME_MIN && expiry != Long.MAX_VALUE){
              ew.until = expiry;
            }
          }
        }
        operation.loaded(this, entry, newValueOrException);
        if (_suppressException) {
          mutationSkipWriterSuppressedException();
          return;
        }
        mutationUpdateHeap();
        return;
      }
      mutationMayCallWriter();
    }

    public void expiryCalculationException(Throwable t) {
      mutationAbort(t);
    }

    public void mutationSkipWriterSuppressedException() {
      mutationUpdateHeap();
    }

    /**
     * Entry mutation, call writer if needed or skip to {@link #mutationMayStore()}
     */
    public void mutationMayCallWriter() {
      if (writer == null) {
        skipWriting();
        return;
      }
      if (!remove) {
        if (!(newValueOrException instanceof ExceptionWrapper)) {
          entry.nextProcessingStep(Entry.ProcessingState.WRITE);
          try {
            writer.write(key, newValueOrException);
            writeComplete();
          } catch (Throwable t) {
            writeException(t);
          }
        } else {
          mutationMayStore();
        }
      } else {
        try {
          entry.nextProcessingStep(Entry.ProcessingState.WRITE);
          writer.delete(key);
          writeComplete();
        } catch (Throwable t) {
          writeException(t);
        }
      }
    }

    @Override
    public void writeComplete() {
      entry.nextProcessingStep(Entry.ProcessingState.WRITE_COMPLETE);
      mutationUpdateHeap();
    }

    @Override
    public void writeException(Throwable t) {
      mutationAbort(t);
    }

    public void skipWriting() {
      mutationUpdateHeap();
    }

    public void mutationUpdateHeap() {
      entry.setLastModification(lastModificationTime);
      if (remove) {
        entry.nextRefreshTime = Entry.REMOVE_PENDING;
      } else {
        entry.value = newValueOrException;
      }
      mutationMayStore();
    }

    /**
     * Entry mutation, call storage if needed
     */
    public void mutationMayStore() {
      if (storage == null) {
        skipStore();
        return;
      }
      entry.nextProcessingStep(Entry.ProcessingState.STORE);
      try {
        if (remove) {
          storage.remove(key);
        } else {
          storage.put(entry, expiry);
        }
      } catch (Throwable t) {
        storeException(t);
        return;
      }
      storeComplete();
    }

    @Override
    public void storeComplete() {
      entry.nextProcessingStep(Entry.ProcessingState.STORE_COMPLETE);
      mutationReleaseLock();
    }

    @Override
    public void storeException(Throwable t) {
      mutationAbort(t);
    }

    public void skipStore() {
      mutationReleaseLock();
    }

    public void mutationReleaseLock() {
      synchronized (entry) {
        entry.processingDone();
        entry.notifyAll();
        if (remove) {
          synchronized (heapCache.lock) {
            heapCache.removedCnt++;
            heapCache.removeEntry(entry);
          }
        } else {
          long _nextRefreshTime = expiry;
          if (_nextRefreshTime == 0) {
            entry.nextRefreshTime = org.cache2k.impl.Entry.FETCH_NEXT_TIME_STATE;
          } else if (_nextRefreshTime == Long.MAX_VALUE) {
            entry.nextRefreshTime = org.cache2k.impl.Entry.FETCHED_STATE;
          } else {
            entry.nextRefreshTime =
              heapCache.stopStartTimer(expiry, entry, System.currentTimeMillis());
          }
        }
      }
      synchronized (heapCache.lock) {
        updateMutationStatisticsInsideLock();
      }
      mutationDone();
    }

    public void updateOnlyReadStatisticsInsideLock() {
      if (heapHitButNotFresh) {
        heapCache.peekHitNotFreshCnt++;
      }
      if (heapMiss) {
        heapCache.peekMissCnt++;
      }
      if (storageRead && storageMiss) {
        heapCache.readNonFreshCnt++;
        heapCache.peekHitNotFreshCnt++;
      }
      if (storageRead && !storageMiss) {
        heapCache.readHitCnt++;
      }
    }

    public void updateMutationStatisticsInsideLock() {
      if (loadCompletedTime > 0) {
        heapCache.loadCnt++;
        heapCache.fetchMillis += loadCompletedTime - loadStartedTime;
        if (storageRead && storageMiss) {
          heapCache.readMissCnt++;
        }
      } else {
        updateOnlyReadStatisticsInsideLock();
      }
    }

    public void examinationAbort(Throwable t) {
      exceptionToPropagate = t;
      if (entryLocked) {
        synchronized (entry) {
          entry.processingDone();
          entry.notifyAll();
        }
      }
      ready();
    }

    public void mutationAbort(Throwable t) {
      exceptionToPropagate = t;
      synchronized (entry) {
        entry.processingDone();
        entry.notifyAll();
      }
      ready();
    }

    /**
     *
     */
    public void mutationDone() {
      evictEventuallyAfterMutation();
    }

    /**
     * Standard behavior specifies, we always check whether something is to
     * evict after each cache operation that may have created a new entry.
     */
    public void evictEventuallyAfterMutation() {
      heapCache.evictEventually();
      ready();
    }

    public void noMutationRequested() {
      if (entryLocked) {
        synchronized (entry) {
          if (entry.isVirgin()) {
            entry.nextRefreshTime = Entry.FETCH_ABORT;
          }
          entry.processingDone();
          entry.notifyAll();
        }
      }
      synchronized (heapCache.lock) {
        updateOnlyReadStatisticsInsideLock();
      }
      if (storageRead) {
        evictEventuallyAfterExamine();
        return;
      }
      ready();
    }

    public void evictEventuallyAfterExamine() {
      heapCache.evictEventually();
      ready();
    }

    public void ready() {

    }

  }

}
