package org.cache2k.core;

/*
 * #%L
 * cache2k core
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

import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.event.CacheEntryExpiredListener;
import org.cache2k.integration.AdvancedCacheLoader;
import org.cache2k.event.CacheEntryCreatedListener;
import org.cache2k.event.CacheEntryRemovedListener;
import org.cache2k.event.CacheEntryUpdatedListener;
import org.cache2k.integration.CacheWriter;
import org.cache2k.integration.CacheWriterException;
import org.cache2k.CustomizationException;
import org.cache2k.core.experimentalApi.AsyncCacheLoader;
import org.cache2k.core.experimentalApi.AsyncCacheWriter;
import org.cache2k.core.operation.ExaminationEntry;
import org.cache2k.core.operation.Progress;
import org.cache2k.core.operation.ReadOnlyCacheEntry;
import org.cache2k.core.operation.Semantic;
import org.cache2k.core.storageApi.StorageCallback;
import org.cache2k.core.storageApi.StorageAdapter;
import org.cache2k.core.storageApi.StorageEntry;

/**
 * This is a method object to perform an operation on an entry.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("SynchronizeOnNonFinalField")
public abstract class EntryAction<K, V, R> implements
  StorageCallback, AsyncCacheLoader.Callback<V>, AsyncCacheWriter.Callback, Progress<V, R> {

  static final Entry NON_FRESH_DUMMY = new Entry();

  InternalCache<K, V> userCache;
  HeapCache<K, V> heapCache;
  K key;
  Semantic<K, V, R> operation;
  Entry<K, V> entry;
  V newValueOrException;
  V oldValueOrException;
  long previousModificationTime;
  R result;
  long lastModificationTime;
  long loadStartedTime;
  long loadCompletedTime;
  CustomizationException exceptionToPropagate;
  boolean remove;
  /** Special case of remove, expiry is in the past */
  boolean expiredImmediately;
  long expiry = 0;
  /**
   * We locked the entry, don't lock it again.
   */
  boolean entryLocked = false;
  /**
   * True if entry had some data after locking. Expiry is not checked.
   */
  boolean heapDataValid = false;
  boolean storageDataValid = false;
  boolean needsFinish = true;

  boolean storageRead = false;
  boolean storageMiss = false;

  boolean heapMiss = false;

  boolean wantData = false;
  boolean countMiss = false;
  boolean heapHit = false;
  boolean doNotCountAccess = false;

  boolean loadAndMutate = false;
  boolean load = false;

  /**
   * Fresh load in first round with {@link #loadAndMutate}.
   * Triggers that we always say it is present.
   */
  boolean successfulLoad = false;

  boolean suppressException = false;

  public EntryAction(HeapCache<K,V> _heapCache, InternalCache<K,V> _userCache, Semantic<K, V, R> op, K k, Entry<K, V> e) {
    heapCache = _heapCache;
    userCache = _userCache;
    operation = op;
    key = k;
    if (e != null) {
      entry = e;
    } else {
      entry = NON_FRESH_DUMMY;
    }
  }

  /**
   * Provide the cache loader, if present.
   */
  protected AdvancedCacheLoader<K, V> loader() {
    return heapCache.loader;
  }

  /**
   * Provide the standard metrics for updating.
   */
  protected CommonMetrics.Updater metrics() {
    return heapCache.metrics;
  }

  /**
   * Provide the storage, default null.
   */
  protected StorageAdapter storage() {
    return null;
  }

  /**
   * Provide the writer, default null.
   */
  protected CacheWriter<K, V> writer() {
    return null;
  }

  /**
   * True if there is any listener defined. Default false.
   */
  protected boolean mightHaveListeners() {
    return false;
  }

  /**
   * Provide the registered listeners for entry creation.
   */
  protected CacheEntryCreatedListener<K, V>[] entryCreatedListeners() {
    return null;
  }

  /**
   * Provide the registered listeners for entry update.
   */
  protected CacheEntryUpdatedListener<K, V>[] entryUpdatedListeners() {
    return null;
  }

  /**
   * Provide the registered listeners for entry removal.
   */
  protected CacheEntryRemovedListener<K, V>[] entryRemovedListeners() {
    return null;
  }

  protected CacheEntryExpiredListener<K, V>[] entryExpiredListeners() {
    return null;
  }

  protected StorageMetrics.Updater storageMetrics() { return null; }

  @SuppressWarnings("unchecked")
  protected abstract TimingHandler<K,V> timing(); //  { return RefreshHandler.ETERNAL; }

  @Override
  public boolean isPresent() {
    doNotCountAccess = true;
    return successfulLoad || entry.hasFreshData();
  }

  @Override
  public boolean isPresentOrMiss() {
    if (successfulLoad || entry.hasFreshData()) {
      return true;
    }
    countMiss = true;
    return false;
  }

  @Override
  public void wantData() {
    wantData = true;
    if (storage() == null) {
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
    int _spinCount = HeapCache.TUNABLE.maximumEntryLockSpins;
    Entry<K, V> e = entry;
    boolean _needStorageRead = false;
    if (e == NON_FRESH_DUMMY) {
      e = heapCache.lookupOrNewEntrySynchronized(key);
    }
    for (; ; ) {
      if (_spinCount-- <= 0) {
        throw new CacheLockSpinsExceededError();
      }
      synchronized (e) {
        e.waitForProcessing();
        if (!e.isGone()) {
          if (e.isVirgin()) {
            storageRead = _needStorageRead = true;
            e.startProcessing(Entry.ProcessingState.READ);
            entryLocked = true;
            heapDataValid = e.isDataValid();
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
      se = storage().get(entry.key);
    } catch (Throwable ex) {
      onReadFailure(ex);
      return;
    }
    onReadSuccess(se);
  }

  @Override
  public void onReadFailure(Throwable t) {
    examinationAbort(new StorageReadException(t));
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
    synchronized (e) {
      e.setNextRefreshTime(Entry.READ_NON_VALID);
      storageMiss = true;
    }
    examine();
  }

  public void storageReadHit(StorageEntry se) {
    Entry<K, V> e = entry;
    synchronized (e) {
      e.setLastModificationFromStorage(se.getCreatedOrUpdated());
      long now = System.currentTimeMillis();
      V v = (V) se.getValueOrException();
      e.setValueOrException(v);
      long _nextRefreshTime;
      long _expiryTimeFromStorage = se.getValueExpiryTime();
      boolean _expired = _expiryTimeFromStorage != 0 && _expiryTimeFromStorage <= now;
      if (!_expired) {
        _nextRefreshTime = timing().calculateNextRefreshTime(e, v, now);
        expiry = _nextRefreshTime;
        if (_nextRefreshTime == ExpiryPolicy.ETERNAL) {
          e.setNextRefreshTime(Entry.DATA_VALID);
          storageDataValid = true;
        } else if (_nextRefreshTime == 0) {
          e.setNextRefreshTime(Entry.READ_NON_VALID);
        } else {
          if (_nextRefreshTime < 0) {
            e.setNextRefreshTime(_nextRefreshTime);
            storageDataValid = true;
          }
          if (_nextRefreshTime <= now) {
            e.setNextRefreshTime(Entry.READ_NON_VALID);
          } else {
            e.setNextRefreshTime(-_nextRefreshTime);
            storageDataValid = true;
          }
        }
      } else {
        e.setNextRefreshTime(Entry.READ_NON_VALID);
      }
      e.nextProcessingStep(Entry.ProcessingState.READ_COMPLETE);
    }
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
    if (!entryLocked && wantData) {
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
  public void loadAndMutation() {
    loadAndMutate = true;
    load();
  }

  @Override
  public void load() {
    if (!entry.isVirgin() && !storageRead) {
      synchronized (heapCache.lock) {
        heapCache.loadButHitCnt++;
      }
    }
    AdvancedCacheLoader<K, V> _loader = loader();
    if (_loader == null) {
      exceptionToPropagate = new CustomizationException(new CacheUsageExcpetion("source not set"));
      return;
    }
    if (!entryLocked) {
      lockFor(Entry.ProcessingState.LOAD);
    } else {
      entry.nextProcessingStep(Entry.ProcessingState.LOAD);
    }
    needsFinish = false;
    load = true;
    Entry<K, V> e = entry;
    long t0 = lastModificationTime = loadStartedTime = System.currentTimeMillis();
    V v;
    try {
      if (e.isVirgin()) {
        v = _loader.load(e.key, t0, null);
      } else {
        v = _loader.load(e.key, t0, e);
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
    int _spinCount = HeapCache.TUNABLE.maximumEntryLockSpins;
    for (; ; ) {
      if (_spinCount-- <= 0) {
        throw new CacheLockSpinsExceededError();
      }
      synchronized (e) {
        e.waitForProcessing();
        if (!e.isGone()) {
          e.startProcessing(ps);
          entryLocked = true;
          heapDataValid = e.isDataValid();
          heapHit = !e.isVirgin();
          entry = e;
          return;
        }
      }
      e = heapCache.lookupOrNewEntrySynchronized(key);
    }
  }

  void lockForNoHit(Entry.ProcessingState ps) {
    if (entryLocked) {
      entry.nextProcessingStep(ps);
      return;
    }
    Entry<K, V> e = entry;
    if (e == NON_FRESH_DUMMY) {
      e = heapCache.lookupOrNewEntrySynchronizedNoHitRecord(key);
    }
    int _spinCount = HeapCache.TUNABLE.maximumEntryLockSpins;
    for (; ; ) {
      if (_spinCount-- <= 0) {
        throw new CacheLockSpinsExceededError();
      }
      synchronized (e) {
        e.waitForProcessing();
        if (!e.isGone()) {
          e.startProcessing(ps);
          entryLocked = true;
          heapDataValid = e.isDataValid();
          heapHit = !e.isVirgin();
          entry = e;
          return;
        }
      }
      e = heapCache.lookupOrNewEntrySynchronizedNoHitRecord(key);
    }
  }

  @Override
  public void onLoadSuccess(V value) {
    newValueOrException = value;
    loadCompleted();
  }

  @SuppressWarnings("unchecked")
  @Override
  public void onLoadFailure(Throwable t) {
    synchronized (heapCache.lock) {
      heapCache.loadExceptionCnt++;
    }
    newValueOrException = (V) new ExceptionWrapper(key, t, loadStartedTime, entry);
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
    lockFor(Entry.ProcessingState.MUTATE);
    needsFinish = false;
    newValueOrException = value;
    lastModificationTime = System.currentTimeMillis();
    mutationCalculateExpiry();
  }

  @Override
  public void remove() {
    lockForNoHit(Entry.ProcessingState.MUTATE);
    needsFinish = false;
    remove = true;
    lastModificationTime = System.currentTimeMillis();
    mutationMayCallWriter();
  }

  @Override
  public void expire(long t) {
    newValueOrException = entry.getValue();
    lastModificationTime = entry.getLastModification();
    expiry = t;
    if (newValueOrException instanceof ExceptionWrapper) {
      setUntil(ExceptionWrapper.class.cast(newValueOrException));
    }
    expiryCalculated();
  }

  public void mutationCalculateExpiry() {
    entry.nextProcessingStep(Entry.ProcessingState.EXPIRY);
    try {
      if (newValueOrException instanceof ExceptionWrapper) {
        ExceptionWrapper ew = (ExceptionWrapper) newValueOrException;
        if ((entry.isDataValid() || entry.isExpired()) && entry.getException() == null) {
          expiry = timing().suppressExceptionUntil(entry, ew);
        }
        if (expiry > loadStartedTime) {
          suppressException = true;
          newValueOrException = entry.getValue();
          lastModificationTime = entry.getLastModification();
          synchronized (heapCache.lock) {
            heapCache.suppressedExceptionCnt++;
          }
          entry.setSuppressedLoadExceptionInformation(ew);
        } else {
          expiry = timing().cacheExceptionUntil(entry, ew);
        }
        setUntil(ew);
      } else {
        expiry = timing().calculateNextRefreshTime(
          entry, newValueOrException,
          lastModificationTime);
        entry.resetSuppressedLoadExceptionInformation();
      }
    } catch (Exception ex) {
      expiryCalculationException(ex);
      return;
    }
    expiryCalculated();
  }

  private void setUntil(final ExceptionWrapper _ew) {
    if (expiry < 0) {
      _ew.until = -expiry;
    } else if (expiry >= Entry.EXPIRY_TIME_MIN) {
      _ew.until = expiry;
    }
  }

  public void expiryCalculationException(Throwable t) {
    mutationAbort(new ExpiryCalculationException(t));
  }

  public void expiryCalculated() {
    entry.nextProcessingStep(Entry.ProcessingState.EXPIRY_COMPLETE);
    if (load) {
      if (loadAndMutate) {
        loadAndExpiryCalculatedMutateAgain();
        return;
      }
      checkKeepOrRemove();
      return;
    } else {
      if (expiry > 0 || expiry == -1) {
        if (entry.isVirgin()) {
          metrics().putNewEntry();
        } else {
          if (!wantData) {
            metrics().putNoReadHit();
          } else {
            metrics().putHit();
          }
        }
      }
    }
    mutationMayCallWriter();
  }

  public void loadAndExpiryCalculatedMutateAgain() {
    load = loadAndMutate = false;
    needsFinish = successfulLoad = true;
    ExaminationEntry ee = new ExaminationEntry() {
      @Override
      public Object getKey() {
        return entry.getKey();
      }

      @Override
      public Object getValueOrException() {
        return newValueOrException;
      }

      @Override
      public long getLastModification() {
        return lastModificationTime;
      }
    };
    operation.update(this, ee);
    if (needsFinish) {
      updateDidNotTriggerDifferentMutationStoreLoadedValue();
    }
  }

  public void updateDidNotTriggerDifferentMutationStoreLoadedValue() {
    checkKeepOrRemove();
  }

  /**
   * Entry mutation, call writer if needed or skip to {@link #mutationMayStore()}
   */
  public void mutationMayCallWriter() {
    CacheWriter<K, V> _writer = writer();
    if (_writer == null) {
      skipWritingNoWriter();
      return;
    }
    if (remove) {
      try {
        entry.nextProcessingStep(Entry.ProcessingState.WRITE);
        _writer.delete(key);
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
      _writer.write(key, newValueOrException);
    } catch (Throwable t) {
      onWriteFailure(t);
      return;
    }
    onWriteSuccess();
  }

  @Override
  public void onWriteSuccess() {
    entry.nextProcessingStep(Entry.ProcessingState.WRITE_COMPLETE);
    checkKeepOrRemove();
  }

  @Override
  public void onWriteFailure(Throwable t) {
    mutationAbort(new CacheWriterException(t));
  }

  public void skipWritingForException() {
    checkKeepOrRemove();
  }

  public void skipWritingNoWriter() {
    checkKeepOrRemove();
  }

  /**
   * In case we have a expiry of 0, this means that the entry should
   * not be cached. If there is a valid entry, we remove it if we do not
   * keep the data.
   */
  public void checkKeepOrRemove() {
    boolean _hasKeepAfterExpired = heapCache.hasKeepAfterExpired();
    if (expiry != 0 || remove || _hasKeepAfterExpired) {
      mutationUpdateHeap();
      return;
    }
    if (_hasKeepAfterExpired) {
      expiredImmediatelyKeepData();
      return;
    }
    expiredImmediatelyAndRemove();
  }



  public void expiredImmediatelyKeepData() {
    expiredImmediately = true;
    mutationUpdateHeap();
  }

  public void expiredImmediatelyAndRemove() {
    remove = true;
    expiredImmediately = true;
    mutationUpdateHeap();
  }

  public void mutationUpdateHeap() {
    synchronized (entry) {
      entry.setLastModification(lastModificationTime);
      if (remove) {
        entry.setNextRefreshTime(Entry.REMOVE_PENDING);
      } else {
        oldValueOrException = entry.getValueOrException();
        previousModificationTime = entry.getLastModification();
        entry.setValueOrException(newValueOrException);
      }
    }
    mutationMayStore();
  }

  /**
   * Entry mutation, call storage if needed
   */
  public void mutationMayStore() {
    if (storage() == null) {
      skipStore();
      return;
    }
    mutationStore();
  }

  public void mutationStore() {
    entry.nextProcessingStep(Entry.ProcessingState.STORE);
    boolean _entryRemoved = false;
    try {
      if (remove) {
        _entryRemoved = storage().remove(key);
      } else {
        storage().put(entry, expiry);
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
    callListeners();
  }

  @Override
  public void onStoreFailure(Throwable t) {
    mutationAbort(new StorageWriteException(t));
  }

  public void skipStore() {
    callListeners();
  }

  public void callListeners() {
    if (!mightHaveListeners()) {
      mutationReleaseLockAndStartTimer();
      return;
    }
    if (expiredImmediately) {
      if (storageDataValid || heapDataValid) {
        if (entryExpiredListeners() != null) {
          for (CacheEntryExpiredListener l : entryExpiredListeners()) {
            try {
              l.onEntryExpired(userCache, entry);
            } catch (Throwable t) {
              exceptionToPropagate = new ListenerException(t);
            }
          }
        }
      }
    } else if (remove) {
      if (storageDataValid || heapDataValid) {
        if (entryRemovedListeners() != null) {
          for (CacheEntryRemovedListener l : entryRemovedListeners()) {
            try {
              l.onEntryRemoved(userCache, entry);
            } catch (Throwable t) {
              exceptionToPropagate = new ListenerException(t);
            }
          }
        }
      }
    } else {
      if (storageDataValid || heapDataValid) {
        if (entryUpdatedListeners() != null) {
          for (CacheEntryUpdatedListener l : entryUpdatedListeners()) {
            try {
              l.onEntryUpdated(userCache,
                new ReadOnlyCacheEntry<K, V>(entry.getKey(), oldValueOrException, previousModificationTime),
                entry);
            } catch (Throwable t) {
              exceptionToPropagate = new ListenerException(t);
            }
          }
        }
      } else {
        if (entryCreatedListeners() != null) {
          for (CacheEntryCreatedListener l : entryCreatedListeners()) {
            try {
              l.onEntryCreated(userCache, entry);
            } catch (Throwable t) {
              exceptionToPropagate = new ListenerException(t);
            }
          }
        }
      }
    }
    mutationReleaseLockAndStartTimer();
  }

  public void mutationReleaseLockAndStartTimer() {
    if (load && !remove) {
      operation.loaded(this, entry);
    }
    try {
      synchronized (entry) {
        entry.processingDone();
        entryLocked = false;
        entry.notifyAll();
        if (remove) {
          synchronized (heapCache.lock) {
            if (heapCache.removeEntry(entry)) {
              if (expiredImmediately) {
                heapCache.expiredRemoveCnt++;
              } else {
                heapCache.removedCnt++;
              }
            }
          }
        } else {
          entry.setNextRefreshTime(timing().stopStartTimer(expiry, entry));
          if (entry.isExpired()) {
            entry.setNextRefreshTime(Entry.EXPIRY_TIME_MIN);
            userCache.expireOrScheduleFinalExpireEvent(entry);
          }
        }
      }
    } finally {
      synchronized (heapCache.lock) {
        updateMutationStatisticsInsideLock();
      }
      mutationDone();
    }
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
        storageMetrics().readNonFresh();
        heapCache.peekHitNotFreshCnt++;
      }
    } else if (doNotCountAccess && heapHit) {
      metrics().containsButHit();
    }
    if (storageRead && !storageMiss) {
      storageMetrics().readHit();
    }
  }

  public void updateMutationStatisticsInsideLock() {
    if (loadCompletedTime > 0) {
      heapCache.loadWoRefreshCnt++;
      heapCache.fetchMillis += loadCompletedTime - loadStartedTime;
      if (storageRead && storageMiss) {
        storageMetrics().readMiss();
      }
    } else {
      updateOnlyReadStatisticsInsideLock();
      if (wantData) {
        metrics().casOperation();
      }
      if (remove) {
        if (heapDataValid) {
          metrics().remove();
        } else if (heapHit) {
        }
      }
    }
  }

  /**
   * Failure call on Progress from Semantic.
   */
  public void failure(Throwable t) {
    mutationAbort(new ProcessingFailureException(t));
  }

  public void examinationAbort(CustomizationException t) {
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

  public void mutationAbort(CustomizationException t) {
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
        entry.processingDone();
        entry.notifyAll();
        if (entry.isVirgin()) {
          synchronized (heapCache.lock) {
            heapCache.removeEntry(entry);
            heapCache.virginRemovedCnt++;
          }
        }
      }
      entryLocked = false;
    }
    synchronized (heapCache.lock) {
      updateOnlyReadStatisticsInsideLock();
    }
    if (!heapHit) {
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

  public static class StorageReadException extends CustomizationException {
    public StorageReadException(final Throwable cause) {
      super(cause);
    }
  }

  public static class StorageWriteException extends CustomizationException {
    public StorageWriteException(final Throwable cause) {
      super(cause);
    }
  }

  public static class ProcessingFailureException extends CustomizationException {
    public ProcessingFailureException(final Throwable cause) {
      super(cause);
    }
  }

  public static class ListenerException extends CustomizationException {
    public ListenerException(final Throwable cause) {
      super(cause);
    }
  }

}
