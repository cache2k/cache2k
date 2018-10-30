package org.cache2k.core;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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

import org.cache2k.CacheEntry;
import org.cache2k.core.operation.LoadedEntry;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.event.CacheEntryExpiredListener;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.integration.AdvancedCacheLoader;
import org.cache2k.event.CacheEntryCreatedListener;
import org.cache2k.event.CacheEntryRemovedListener;
import org.cache2k.event.CacheEntryUpdatedListener;
import org.cache2k.integration.CacheWriter;
import org.cache2k.integration.CacheWriterException;
import org.cache2k.CustomizationException;
import org.cache2k.integration.AsyncCacheLoader;
import org.cache2k.core.experimentalApi.AsyncCacheWriter;
import org.cache2k.core.operation.ExaminationEntry;
import org.cache2k.core.operation.Progress;
import org.cache2k.core.operation.Semantic;
import org.cache2k.core.storageApi.StorageCallback;
import org.cache2k.core.storageApi.StorageAdapter;
import org.cache2k.core.storageApi.StorageEntry;
import org.cache2k.integration.ExceptionInformation;
import org.cache2k.integration.RefreshedTimeWrapper;

/**
 * This is a method object to perform an operation on an entry.
 *
 * @author Jens Wilke
 */
@SuppressWarnings({"SynchronizeOnNonFinalField", "unchecked"})
public abstract class EntryAction<K, V, R> implements
  AsyncCacheLoader.Context<K, V>,
  AsyncCacheLoader.Callback<K, V>, AsyncCacheWriter.Callback, Progress<K, V, R> {

  static final Entry NON_FRESH_DUMMY = new Entry();

  InternalCache<K, V> userCache;
  HeapCache<K, V> heapCache;
  K key;
  Semantic<K, V, R> operation;
  Entry<K, V> entry;
  V newValueOrException;
  V oldValueOrException;
  R result;
  long currentTime;
  long lastRefreshTime;
  long loadStartedTime;
  RuntimeException exceptionToPropagate;
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

  /** Stats for load should be counted as refresh */
  boolean refresh = false;

  /**
   * Fresh load in first round with {@link #loadAndMutate}.
   * Triggers that we always say it is present.
   */
  boolean successfulLoad = false;

  boolean suppressException = false;

  /**
   * Use async when available.
   */
  boolean preferAsync = true;

  Thread syncThread;

  ActionCompletedCallback completedCallback;

  @SuppressWarnings("unchecked")
  public EntryAction(HeapCache<K,V> _heapCache, InternalCache<K,V> _userCache,
                     Semantic<K, V, R> op, K k, Entry<K, V> e, ActionCompletedCallback cb) {
    heapCache = _heapCache;
    userCache = _userCache;
    operation = op;
    key = k;
    if (e != null) {
      entry = e;
    } else {
      entry = (Entry<K,V>) NON_FRESH_DUMMY;
    }
    if (cb == null) {
      syncThread = Thread.currentThread();
    } else {
      completedCallback = cb;
    }
  }

  @SuppressWarnings("unchecked")
  public EntryAction(HeapCache<K,V> _heapCache, InternalCache<K,V> _userCache,
                     Semantic<K, V, R> op, K k, Entry<K, V> e) {
    this(_heapCache, _userCache, op, k, e, null);
  }

  /**
   * Provide the cache loader, if present.
   */
  protected AdvancedCacheLoader<K, V> loader() {
    return heapCache.loader;
  }

  protected AsyncCacheLoader<K, V> asyncLoader() {
    return null;
  }

  /**
   * Provide the standard metrics for updating.
   */
  protected CommonMetrics.Updater metrics() {
    return heapCache.metrics;
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

  @SuppressWarnings("unchecked")
  protected abstract TimingHandler<K,V> timing(); //  { return RefreshHandler.ETERNAL; }

  @Override
  public K getKey() {
    return entry.getKey();
  }

  @Override
  public long getCurrentTime() {
    if (currentTime > 0) {
      return currentTime;
    }
    return currentTime = millis();
  }

  @Override
  public V getCachedValue() {
    V v = oldValueOrException;
    if (v instanceof ExceptionWrapper) {
      return null;
    }
    return v;
  }

  @Override
  public Throwable getCachedException() {
    V v = oldValueOrException;
    if (v instanceof ExceptionWrapper) { return ((ExceptionWrapper) v).getException(); }
    return null;
  }

  @Override
  public boolean isPresent() {
    doNotCountAccess = true;
    return successfulLoad || entry.hasFreshData(heapCache.getClock());
  }

  @Override
  public boolean isPresentOrInRefreshProbation() {
    doNotCountAccess = true;
    return
      successfulLoad ||
      entry.getNextRefreshTime() == Entry.EXPIRED_REFRESHED ||
      entry.hasFreshData(heapCache.getClock());
  }

  @Override
  public boolean isPresentOrMiss() {
    if (successfulLoad || entry.hasFreshData(heapCache.getClock())) {
      return true;
    }
    countMiss = true;
    return false;
  }

  @Override
  public void wantData() {
    wantData = true;
    retrieveDataFromHeap();
  }

  public void retrieveDataFromHeap() {
    Entry<K, V> e = entry;
    if (e == NON_FRESH_DUMMY) {
      e = heapCache.lookupEntry(key);
      if (e == null) {
        heapMiss();
        return;
      }
    }
    heapHit(e);
  }

  private long millis() {
    return heapCache.getClock().millis();
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
  public void refresh() {
    refresh = true;
    load();
  }

  @SuppressWarnings("unchecked")
  @Override
  public void load() {
    lockFor(Entry.ProcessingState.LOAD);
    needsFinish = false;
    load = true;
    Entry<K, V> e = entry;
    long t0 = heapCache.isUpdateTimeNeeded() ? lastRefreshTime = loadStartedTime = getCurrentTime() : 0;
    if (e.getNextRefreshTime() == Entry.EXPIRED_REFRESHED) {
      long nrt = e.getRefreshProbationNextRefreshTime();
      if (nrt > t0) {
        reviveRefreshedEntry(nrt);
        return;
      }
    }
    AsyncCacheLoader<K, V> _asyncLoader;
    if (preferAsync && (_asyncLoader = asyncLoader()) != null) {
      lockFor(Entry.ProcessingState.LOAD_ASYNC);
      if (e.isVirgin() || e.getException() != null) {
        _asyncLoader.load(key, this, this);
      } else {
        _asyncLoader.load(key, this, this);
      }
      asyncOperationStarted();
      return;
    }
    AdvancedCacheLoader<K, V> _loader = loader();
    if (_loader == null) {
      mutationAbort(null);
      return;
    }
    V v;
    try {
      if (e.isVirgin()) {
        v = _loader.load(key, t0, null);
      } else {
        v = _loader.load(key, t0, e);
      }
    } catch (Throwable _ouch) {
      onLoadFailureIntern(_ouch);
      return;
    }
    onLoadSuccessIntern(v);
  }

  public void reviveRefreshedEntry(long nrt) {
    metrics().refreshedHit();
    Entry<K, V> e = entry;
    newValueOrException = e.getValueOrException();
    lastRefreshTime = e.getRefreshTime();
    expiry = nrt;
    expiryCalculated();
  }

  void lockFor(int ps) {
    if (entryLocked) {
      entry.nextProcessingStep(ps);
      return;
    }
    Entry<K, V> e = entry;
    if (e == NON_FRESH_DUMMY) {
      e = heapCache.lookupOrNewEntry(key);
    }
    for (; ; ) {
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
      e = heapCache.lookupOrNewEntry(key);
    }
  }

  @SuppressWarnings("SameParameterValue")
  void lockForNoHit(int ps) {
    if (entryLocked) {
      entry.nextProcessingStep(ps);
      return;
    }
    Entry<K, V> e = entry;
    if (e == NON_FRESH_DUMMY) {
      e = heapCache.lookupOrNewEntryNoHitRecord(key);
    }
    for (; ; ) {
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
      e = heapCache.lookupOrNewEntryNoHitRecord(key);
    }
  }

  @Override
  public void onLoadSuccess(V v) {
    checkEntryStateOnLoadCallback();
    onLoadSuccessIntern(v);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void onLoadFailure(Throwable t) {
    checkEntryStateOnLoadCallback();
    onLoadFailureIntern(t);
  }

  private void checkEntryStateOnLoadCallback() {
    if (entry.getProcessingState() != Entry.ProcessingState.LOAD_ASYNC) {
      throw new IllegalStateException("async callback on wrong entry state. duplicate callback?");
    }
  }

  private void onLoadSuccessIntern(V v) {
    if (v instanceof RefreshedTimeWrapper) {
      RefreshedTimeWrapper wr = (RefreshedTimeWrapper<V>)v;
      lastRefreshTime = wr.getRefreshTime();
      v = (V) wr.getValue();
    }

    newValueOrException = v;
    loadCompleted();
  }

  private void onLoadFailureIntern(Throwable t) {
    newValueOrException = (V) new ExceptionWrapper(key, t, loadStartedTime, entry);
    loadCompleted();
  }

  public void loadCompleted() {
    entry.nextProcessingStep(Entry.ProcessingState.LOAD_COMPLETE);
    entryLocked = true;
    if (!metrics().isDisabled() && heapCache.isUpdateTimeNeeded()) {
      long _loadCompletedTime = millis();
      long _delta = _loadCompletedTime - loadStartedTime;
      if (refresh) {
        metrics().refresh(_delta);
      } else if (entry.isVirgin() || !storageRead) {
        metrics().load(_delta);
      } else {
        metrics().reload(_delta);
      }
    }
    mutationCalculateExpiry();
  }

  @Override
  public void result(R r) {
    result = r;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void entryResult(final ExaminationEntry e) {
    result = (R) heapCache.returnEntry(e);
  }

  @Override
  public RuntimeException propagateException(final K key, final ExceptionInformation inf) {
    return heapCache.exceptionPropagator.propagateException(key, inf);
  }

  @Override
  public void put(V value) {
    lockFor(Entry.ProcessingState.MUTATE);
    needsFinish = false;
    newValueOrException = value;
    if (!heapCache.isUpdateTimeNeeded()) {
      lastRefreshTime = 0;
    } else {
      lastRefreshTime = getCurrentTime();
    }
    mutationCalculateExpiry();
  }

  @Override
  public void remove() {
    lockForNoHit(Entry.ProcessingState.MUTATE);
    needsFinish = false;
    remove = true;
    mutationMayCallWriter();
  }

  @Override
  public void expire(long expiryTime) {
    lockForNoHit(Entry.ProcessingState.MUTATE);
    needsFinish = false;
    newValueOrException = entry.getValueOrException();
    if (heapCache.isUpdateTimeNeeded()) {
      lastRefreshTime = entry.getRefreshTime();
    }
    expiry = expiryTime;
    if (newValueOrException instanceof ExceptionWrapper) {
      setUntil(ExceptionWrapper.class.cast(newValueOrException));
    }
    checkKeepOrRemove();
  }

  @Override
  public void putAndSetExpiry(final V value, final long expiryTime, final long refreshTime) {
    lockFor(Entry.ProcessingState.MUTATE);
    needsFinish = false;
    newValueOrException = value;
    if (refreshTime >= 0) {
      lastRefreshTime = refreshTime;
    } else {
      if (heapCache.isUpdateTimeNeeded()) {
        lastRefreshTime = getCurrentTime();
      }
    }
    if (newValueOrException instanceof ExceptionWrapper) {
      setUntil(ExceptionWrapper.class.cast(newValueOrException));
    }
    if (expiryTime != ExpiryTimeValues.NEUTRAL) {
      expiry = expiryTime;
      expiryCalculated();
    } else {
      mutationCalculateExpiry();
    }
  }

  public void mutationCalculateExpiry() {
    entry.nextProcessingStep(Entry.ProcessingState.EXPIRY);
    if (newValueOrException instanceof ExceptionWrapper) {
      try {
        expiry = 0;
        ExceptionWrapper ew = (ExceptionWrapper) newValueOrException;
        if ((entry.isDataValid() || entry.isExpired()) && entry.getException() == null) {
          expiry = timing().suppressExceptionUntil(entry, ew);
        }
        if (expiry > loadStartedTime) {
          suppressException = true;
          newValueOrException = entry.getValueOrException();
          lastRefreshTime = entry.getRefreshTime();
          metrics().suppressedException();
          entry.setSuppressedLoadExceptionInformation(ew);
        } else {
          if (load) {
            metrics().loadException();
          }
          expiry = timing().cacheExceptionUntil(entry, ew);
        }
        setUntil(ew);
      } catch (Throwable ex) {
        if (load) {
          resiliencePolicyException(new ResiliencePolicyException(ex));
          return;
        }
        expiryCalculationException(ex);
        return;
      }
    } else {
      try {
        expiry = timing().calculateNextRefreshTime(
          entry, newValueOrException,
          lastRefreshTime);
        if (newValueOrException == null && heapCache.isRejectNullValues() && expiry != ExpiryTimeValues.NO_CACHE) {
          RuntimeException _ouch = heapCache.returnNullValueDetectedException();
          if (load) {
            decideForLoaderExceptionAfterExpiryCalculation(new ResiliencePolicyException(_ouch));
            return;
          } else {
            mutationAbort(_ouch);
            return;
          }
        }
        entry.resetSuppressedLoadExceptionInformation();
      } catch (Throwable ex) {
        if (load) {
          decideForLoaderExceptionAfterExpiryCalculation(new ExpiryPolicyException(ex));
          return;
        }
        expiryCalculationException(ex);
        return;
      }
    }
    expiryCalculated();
  }

  /**
   * An exception happened during or after expiry calculation. We handle this identical to
   * the loader exception and let the resilience policy decide what to do with it.
   * Rationale: An exception cause by the expiry policy may have its root cause
   * in the loaded value and may be temporary.
   */
  @SuppressWarnings("unchecked")
  private void decideForLoaderExceptionAfterExpiryCalculation(RuntimeException _ouch) {
    newValueOrException = (V) new ExceptionWrapper<K>(key, _ouch, loadStartedTime, entry);
    expiry = 0;
    mutationCalculateExpiry();
  }

  /**
   * We have two exception in this case: One from the loader or the expiry policy, one from
   * the resilience policy. Propagate exception from the resilience policy and suppress
   * the other, since this is a general configuration problem.
   */
  @SuppressWarnings("unchecked")
  private void resiliencePolicyException(RuntimeException _ouch) {
    newValueOrException = (V)
      new ExceptionWrapper<K>(key, _ouch, loadStartedTime, entry);
    expiry = 0;
    expiryCalculated();
  }

  private void setUntil(final ExceptionWrapper _ew) {
    if (expiry < 0) {
      _ew.setUntil(-expiry);
    } else if (expiry >= Entry.EXPIRY_TIME_MIN) {
      _ew.setUntil(expiry);
    }
  }

  public void expiryCalculationException(Throwable t) {
    mutationAbort(new ExpiryPolicyException(t));
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
      if (expiry > 0 || expiry == -1 || (expiry < 0 && -expiry > loadStartedTime)) {
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
    ExaminationEntry<K,V> ee = new LoadedEntry<K,V>() {
      @Override
      public K getKey() {
        return entry.getKey();
      }

      @Override
      public V getValueOrException() {
        return newValueOrException;
      }

      @Override
      public long getRefreshTime() {
        return lastRefreshTime;
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
    boolean _hasKeepAfterExpired = heapCache.isKeepAfterExpired();
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
      if (heapCache.isRecordRefreshTime()) {
        entry.setRefreshTime(lastRefreshTime);
      }
      if (remove) {
        if (expiredImmediately) {
          entry.setNextRefreshTime(Entry.EXPIRED);
          entry.setValueOrException(newValueOrException);
        } else {
          if (!entry.isVirgin()) {
            entry.setNextRefreshTime(Entry.REMOVE_PENDING);
          }
        }
      } else {
        oldValueOrException = entry.getValueOrException();
        entry.setValueOrException(newValueOrException);
      }
    }
    heapCache.eviction.updateWeight(entry);
    mutationMayStore();
  }

  /**
   * Entry mutation, call storage if needed
   */
  public void mutationMayStore() {
    skipStore();
  }

  public void skipStore() {
    callListeners();
  }

  public void callListeners() {
    if (!mightHaveListeners()) {
      mutationReleaseLockAndStartTimer();
      return;
    }
    CacheEntry<K,V> _currentEntry = heapCache.returnCacheEntry(entry);
    if (expiredImmediately) {
      if (storageDataValid || heapDataValid) {
        if (entryExpiredListeners() != null) {
          for (CacheEntryExpiredListener<K,V> l : entryExpiredListeners()) {
            try {
              l.onEntryExpired(userCache, _currentEntry);
            } catch (Throwable t) {
              exceptionToPropagate = new ListenerException(t);
            }
          }
        }
      }
    } else if (remove) {
      if (storageDataValid || heapDataValid) {
        if (entryRemovedListeners() != null) {
          for (CacheEntryRemovedListener<K,V> l : entryRemovedListeners()) {
            try {
              l.onEntryRemoved(userCache, _currentEntry);
            } catch (Throwable t) {
              exceptionToPropagate = new ListenerException(t);
            }
          }
        }
      }
    } else {
      if (storageDataValid || heapDataValid) {
        if (entryUpdatedListeners() != null) {
          CacheEntry<K,V> _previousEntry =
            heapCache.returnCacheEntry(entry.getKey(), oldValueOrException);
          for (CacheEntryUpdatedListener<K,V> l : entryUpdatedListeners()) {
            try {
              l.onEntryUpdated(userCache, _previousEntry, _currentEntry);
            } catch (Throwable t) {
              exceptionToPropagate = new ListenerException(t);
            }
          }
        }
      } else {
        if (entryCreatedListeners() != null) {
          for (CacheEntryCreatedListener<K,V> l : entryCreatedListeners()) {
            try {
              l.onEntryCreated(userCache, _currentEntry);
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
    if (load) {
      if (!remove ||
        !(entry.getValueOrException() == null && heapCache.isRejectNullValues())) {
        operation.loaded(this, entry);
      }
    }
    synchronized (entry) {
      entry.processingDone();
      entryLocked = false;
      if (refresh) {
        heapCache.startRefreshProbationTimer(entry, expiry);
        updateMutationStatistics();
        mutationDone();
        return;
      }

      if (remove) {
        heapCache.removeEntry(entry);
      } else {
        entry.setNextRefreshTime(timing().stopStartTimer(expiry, entry));
        if (entry.isExpired()) {
          entry.setNextRefreshTime(Entry.EXPIRY_TIME_MIN);
          userCache.expireOrScheduleFinalExpireEvent(entry);
        }
      }
    }
    updateMutationStatistics();
    mutationDone();
  }

  public void updateMutationStatistics() {
    if (loadStartedTime > 0) {
      return;
    }
    updateOnlyReadStatistics();
  }

  public void updateOnlyReadStatistics() {
    if (countMiss) {
      if (heapHit) {
        metrics().peekHitNotFresh();
      }
      if (heapMiss) {
        metrics().peekMiss();
      }
    } else if (doNotCountAccess && heapHit) {
      metrics().heapHitButNoRead();
    }
  }

  /**
   * Failure call on Progress from Semantic.
   */
  @Override
  public void failure(RuntimeException t) {
    updateOnlyReadStatistics();
    mutationAbort(t);
  }

  public void examinationAbort(CustomizationException t) {
    exceptionToPropagate = t;
    if (entryLocked) {
      synchronized (entry) {
        entry.processingDone();
        entryLocked = false;
      }
    }
    ready();
  }

  public void mutationAbort(RuntimeException t) {
    exceptionToPropagate = t;
    synchronized (entry) {
      entry.processingDone();
      entryLocked = false;
      needsFinish = false;
    }
    ready();
  }

  /**
   *
   */
  public void mutationDone() {
    ready();
  }

  public void noMutationRequested() {
    if (entryLocked) {
      synchronized (entry) {
        entry.processingDone();
        if (entry.isVirgin()) {
          heapCache.removeEntry(entry);
        }
      }
      entryLocked = false;
    }
    synchronized (heapCache.lock) {
      updateOnlyReadStatistics();
    }
    ready();
  }

  public void ready() {
    if (completedCallback != null) {
      completedCallback.entryActionCompleted(this);
    }
  }

  /**
   * If thread is a synchronous call, wait until operation is complete.
   * There is a little chance that the call back completes before we get
   * here as well as some other operation changing the entry again.
   */
  public void asyncOperationStarted() {
    if (syncThread == Thread.currentThread()) {
      synchronized (entry) {
        while (entry.isProcessing()) {
          try {
            entry.wait();
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
        }
      }
    } else {
      entryLocked = false;
    }
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

  public interface ActionCompletedCallback {
    void entryActionCompleted(EntryAction ea);
  }

}
