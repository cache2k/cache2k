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
import org.cache2k.CacheManager;
import org.cache2k.CacheSourceWithMetaInfo;
import org.cache2k.CacheWriter;
import org.cache2k.ClosableIterator;
import org.cache2k.EntryProcessingResult;
import org.cache2k.FetchCompletedListener;
import org.cache2k.experimentalApi.AsyncCacheLoader;
import org.cache2k.experimentalApi.AsyncCacheWriter;
import org.cache2k.impl.operation.ExaminationEntry;
import org.cache2k.impl.operation.Progress;
import org.cache2k.impl.operation.Semantic;
import org.cache2k.impl.operation.Specification;
import org.cache2k.impl.util.Log;
import org.cache2k.storage.StorageCallback;
import org.cache2k.storage.StorageEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * @author Jens Wilke
 */
public class WiredCache<K, V> extends AbstractCache<K, V> implements  StorageAdapter.Parent {

  final Specification<K, V> SPEC = new Specification<K,V>();

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
  public String getName() {
    return heapCache.getName();
  }

  @Override
  public StorageAdapter getStorage() {
    return heapCache.getStorage();
  }

  @Override
  public Class<?> getKeyType() {
    return heapCache.getKeyType();
  }

  @Override
  public Class<?> getValueType() {
    return heapCache.getValueType();
  }

  @Override
  public CacheManager getCacheManager() {
    return heapCache.getCacheManager();
  }

  @Override
  public V peekAndPut(K key, V value) {
    return returnValue(execute(key, SPEC.peekAndPut(key, value)));
  }

  @Override
  public V peekAndRemove(K key) {
    return returnValue(execute(key, SPEC.peekAndRemove(key)));
  }

  @Override
  public V peekAndReplace(K key, V value) {
    return returnValue(execute(key, SPEC.peekAndReplace(key, value)));
  }

  @Override
  public CacheEntry<K, V> peekEntry(K key) {
    return execute(key, SPEC.peekEntry(key));
  }

  @Override
  public void prefetch(K key) {
  }

  @Override
  public void prefetch(List<? extends K> keys, int _startIndex, int _afterEndIndex) {
  }

  @Override
  public void prefetch(Set<? extends K> keys) {
  }

  @Override
  public boolean contains(K key) {
    return execute(key, SPEC.contains(key));
  }

  @Override
  public boolean putIfAbsent(K key, V value) {
    return execute(key, SPEC.putIfAbsent(key, value));
  }

  @Override
  public void put(K key, V value) {
    execute(key, SPEC.put(key, value));
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
      put(e.getKey(), e.getValue());
    }
  }

  @Override
  public void remove(K key) {
    execute(key, SPEC.remove(key));
  }

  @Override
  public boolean remove(K key, V value) {
    return execute(key, SPEC.remove(key, value));
  }

  @Override
  public boolean containsAndRemove(K key) {
    return execute(key, SPEC.containsAndRemove(key));
  }

  @Override
  public boolean replace(K key, V _newValue) {
    return execute(key, SPEC.replace(key, _newValue));
  }

  @Override
  public boolean replace(K key, V _oldValue, V _newValue) {
    return execute(key, SPEC.replace(key, _oldValue, _newValue));
  }

  @Override
  public CacheEntry<K, V> replaceOrGet(K key, V _oldValue, V _newValue, CacheEntry<K, V> _dummyEntry) {
    return execute(key, SPEC.replaceOrGet(key, _oldValue, _newValue, _dummyEntry));
  }

  @Override
  public void fetchAll(Set<? extends K> keys, boolean replaceExistingValues, FetchCompletedListener l) {
    heapCache.fetchAll(keys, replaceExistingValues, l);
  }

  V returnValue(V v) {
    return heapCache.returnValue(v);
  }

  V returnValue(Entry<K,V> e) {
    return returnValue(e.getValueOrException());
  }

  Entry<K, V> lookup(K key) {
    final int hc =  heapCache.modifiedHash(key.hashCode());
    return heapCache.lookupEntryUnsynchronized(key, hc);
  }

  Entry<K, V> lookupNoHit(K key) {
    final int hc =  heapCache.modifiedHash(key.hashCode());
    return heapCache.lookupEntryUnsynchronizedNoHitRecord(key, hc);
  }

  @Override
  public V get(K key) {
    Entry<K, V> e = lookup(key);
    if (e != null && e.hasFreshData()) {
      return returnValue(e);
    }
    return returnValue(execute(key, e, SPEC.get(key)));
   }

  /**
   * We need to deal with possible null values and exceptions. This is
   * a simple placeholder implementation that covers it all by working
   * on the entry.
   */
  @Override
  public Map<K, V> getAll(final Set<? extends K> keys) {
    prefetch(keys);
    Map<K, ExaminationEntry<K, V>> map = new HashMap<K, ExaminationEntry<K, V>>();
    for (K k : keys) {
      ExaminationEntry<K, V> e = execute(k, SPEC.getEntry(k));
      if (e != null) {
        map.put(k, e);
      }
    }
    return convertValueMap(map);
  }

  @Override
  public CacheEntry<K, V> getEntry(K key) {
    return execute(key, SPEC.getEntry(key));
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
  public ClosableIterator<CacheEntry<K, V>> iterator() {
    return heapCache.iterator();
  }

  @Override
  public V peek(K key) {
    Entry<K, V> e = lookup(key);
    if (e != null && e.hasFreshData()) {
      return returnValue(e);
    }
    return returnValue(execute(key, SPEC.peek(key)));
  }

  /**
   * We need to deal with possible null values and exceptions. This is
   * a simple placeholder implementation that covers it all by working
   * on the entry.
   */
  @Override
  public Map<K, V> peekAll(final Set<? extends K> keys) {
    Map<K, ExaminationEntry<K, V>> map = new HashMap<K, ExaminationEntry<K, V>>();
    for (K k : keys) {
      ExaminationEntry<K, V> e = execute(k, SPEC.peekEntry(k));
      if (e != null) {
        map.put(k, e);
      }
    }
    return convertValueMap(map);
  }

  private Map<K, V> convertValueMap(final Map<K, ExaminationEntry<K, V>> _map) {
    return new MapValueConverterProxy<K, V, ExaminationEntry<K, V>>(_map) {
      @Override
      protected V convert(final ExaminationEntry<K, V> v) {
        return returnValue(v.getValueOrException());
      }
    };
  }

  @Override
  public InternalCacheInfo getLatestInfo() {
    return heapCache.getLatestInfo();
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
  public boolean isClosed() {
    return heapCache.isClosed();
  }

  @Override
  public void close() {
    heapCache.close();
  }

  @Override
  public String toString() {
    return heapCache.toString();
  }

  <R> R execute(K key, Entry<K, V> e, Semantic<K, V, R> op) {
    EntryAction<R> _action = new EntryAction<R>(op, key, e);
    op.start(_action);
    if (_action.entryLocked) {
      throw new CacheInternalError("entry not unlocked?");
    }
    Throwable t = _action.exceptionToPropagate;
    if (t != null) {
      throw new WrappedOperationException(t);
    }
    return _action.result;
  }

  <R> R execute(K key, Semantic<K, V, R> op) {
    return execute(key, null, op);
  }

  static final Entry NON_FRESH_DUMMY = new Entry();

  @SuppressWarnings("SynchronizeOnNonFinalField")
  class EntryAction<R> implements StorageCallback, AsyncCacheLoader.Callback<V>, AsyncCacheWriter.Callback, Progress<V, R> {

    K key;
    Semantic<K,V,R> operation;
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

    boolean heapMiss = false;

    boolean wantData = false;
    boolean countMiss = false;
    boolean heapHit = false;
    boolean doNotCountAccess = false;

    @Override
    public boolean isPresent() {
      doNotCountAccess = true;
      return entry.hasFreshData();
    }

    @Override
    public boolean isPresentOrMiss() {
      if  (entry.hasFreshData()) {
        return true;
      }
      countMiss = true;
      return false;
    }

    public EntryAction(Semantic<K, V, R> op, K k, Entry<K,V> e) {
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
      wantData = true;
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
      heapHit(e);
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
      heapHit(e);
    }

    public void storageRead() {
      StorageEntry se;
      try {
         se = storage.get(entry.key);
      } catch (Throwable ex) {
        onReadFailure(ex);
        return;
      }
      onReadSuccess(se);
    }

    @Override
    public void onReadFailure(Throwable t) {
      examinationAbort(t);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onReadSuccess(StorageEntry se) {
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

    public void heapMiss() {
      heapMiss = true;
      examine();
    }

    public void heapHit(Entry<K, V> e) {
      heapHit = true;
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
      if (!entryLocked) {
        lockFor(Entry.ProcessingState.MUTATE);
        countMiss = false;
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

    public void finish() {
      needsFinish = false;
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
        onLoadFailure(_ouch);
        return;
      }
      onLoadSuccess(v);
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
    public void onLoadSuccess(V value) {
      newValueOrException = value;
      loadCompleted();
    }

    @Override
    public void onLoadFailure(Throwable t) {
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
        if (_suppressException) {
          mutationSkipWriterSuppressedException();
          return;
        }
        mutationUpdateHeap();
        return;
      } else {
        if (entry.isVirgin()) {
          metrics().putNewEntry();
        } else {
          if (!wantData) {
            metrics().putNoReadHit();
          } else {
            if (expiry > 0) {
              metrics().putHit();
            }
          }
        }
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
        skipWritingNoWriter();
        return;
      }
      if (remove) {
        try {
          entry.nextProcessingStep(Entry.ProcessingState.WRITE);
          writer.delete(key);
        } catch (Throwable t) {
          onWriteFailure(t);
          return;
        }
        onWriteSuccess();
        return;
      }
      if (newValueOrException instanceof ExceptionWrapper) {
        skipWritingForException();
        return;
      }
      entry.nextProcessingStep(Entry.ProcessingState.WRITE);
      try {
        writer.write(key, newValueOrException);
      } catch (Throwable t) {
        onWriteFailure(t);
        return;
      }
      onWriteSuccess();
    }

    @Override
    public void onWriteSuccess() {
      entry.nextProcessingStep(Entry.ProcessingState.WRITE_COMPLETE);
      mutationUpdateHeap();
    }

    @Override
    public void onWriteFailure(Throwable t) {
      mutationAbort(t);
    }

    public void skipWritingForException() {
      mutationUpdateHeap();
    }

    public void skipWritingNoWriter() {
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
      if (expiry == 0) {
        if (heapCache.hasKeepAfterExpired()) {
          mutationStoreRegular();
          return;
        }
        mutationStoreNoKeepExpired();
        return;
      }
      mutationStoreRegular();
    }

    /**
     * The entry is expired immediately. Send a remove to the store, so no old data is kept.
     * TODO-C: Optimize, send remove for expired entries only if present in the storage.
     */
    public void mutationStoreNoKeepExpired() {
      entry.nextProcessingStep(Entry.ProcessingState.STORE);
      boolean _entryRemoved;
      try {
        _entryRemoved = storage.remove(key);
      } catch (Throwable t) {
        onStoreFailure(t);
        return;
      }
      onStoreSuccess(_entryRemoved);
    }

    public void mutationStoreRegular() {
      entry.nextProcessingStep(Entry.ProcessingState.STORE);
      boolean _entryRemoved = false;
      try {
        if (remove) {
          _entryRemoved = storage.remove(key);
        } else {
          storage.put(entry, expiry);
        }
      } catch (Throwable t) {
        onStoreFailure(t);
        return;
      }
      onStoreSuccess(_entryRemoved);
    }

    @Override
    public void onStoreSuccess(boolean _entryRemoved) {
      entry.nextProcessingStep(Entry.ProcessingState.STORE_COMPLETE);
      mutationReleaseLock();
    }

    @Override
    public void onStoreFailure(Throwable t) {
      mutationAbort(t);
    }

    public void skipStore() {
      mutationReleaseLock();
    }

    public void mutationReleaseLock() {
      if (loadCompletedTime > 0) {
        operation.loaded(this, entry);
      }
      synchronized (entry) {
        entry.processingDone();
        entryLocked = false;
        entry.notifyAll();
        if (remove) {
          synchronized (heapCache.lock) {
            if (heapCache.removeEntry(entry)) {
              heapCache.removedCnt++;
            }
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
      if (countMiss) {
        if (heapHit) {
          heapCache.peekHitNotFreshCnt++;
        }
        if (heapMiss) {
          heapCache.peekMissCnt++;
        }
        if (storageRead && storageMiss) {
          heapCache.readNonFreshCnt++;
          heapCache.peekHitNotFreshCnt++;
        }
      } else if (doNotCountAccess && heapHit) {
        metrics().containsButHit();
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
        if (wantData) {
          metrics().casOperation();
        }
      }
    }

    public void examinationAbort(Throwable t) {
      exceptionToPropagate = t;
      if (entryLocked) {
        synchronized (entry) {
          entry.processingDone();
          entry.notifyAll();
          entryLocked = false;
        }
      }
      ready();
    }

    public void mutationAbort(Throwable t) {
      exceptionToPropagate = t;
      synchronized (entry) {
        entry.processingDone();
        entry.notifyAll();
        entryLocked = false;
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
        entryLocked = false;
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

    protected CommonMetrics.Updater metrics() {
      return heapCache.metrics;
    }

  }

}
