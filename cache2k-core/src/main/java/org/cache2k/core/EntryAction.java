package org.cache2k.core;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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
import org.cache2k.integration.CacheLoaderException;
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
import static org.cache2k.core.Entry.ProcessingState.*;

/**
 * This is a method object to perform an operation on an entry.
 *
 * @author Jens Wilke
 */
@SuppressWarnings({"SynchronizeOnNonFinalField", "unchecked"})
public abstract class EntryAction<K, V, R> extends Entry.PiggyBack implements
  Runnable,
  AsyncCacheLoader.Context<K, V>,
  AsyncCacheLoader.Callback<K, V>, AsyncCacheWriter.Callback, Progress<K, V, R> {

  static final Entry NON_FRESH_DUMMY = new Entry();

  InternalCache<K, V> userCache;
  HeapCache<K, V> heapCache;
  K key;
  Semantic<K, V, R> operation;

  /**
   * Reference to the entry we do processing for or the dummy entry {@link #NON_FRESH_DUMMY}
   * The field is volatile, to be safe when we get a callback from a different thread.
   */
  volatile Entry<K, V> entry;
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
   * True if entry had some data after locking. Clock for expiry is not checked.
   * Also true if entry contains data in refresh probation.
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

  Thread syncThread;

  /**
   * Callback on on completion, set if client request is async.
   */
  CompletedCallback completedCallback;

  private EntryAction nextAction = null;

  /**
   * Action is completed
   */
  private boolean completed;

  public void enqueueToExecute(EntryAction v) {
    EntryAction next;
    EntryAction target = this;
    while ((next = target.nextAction) != null) {
      target = next;
    }
    target.nextAction = v;
  }

  @SuppressWarnings("unchecked")
  public EntryAction(HeapCache<K,V> _heapCache, InternalCache<K,V> _userCache,
                     Semantic<K, V, R> op, K k, Entry<K, V> e, CompletedCallback cb) {
    super(null);
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
  protected abstract TimingHandler<K,V> timing();

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
    V v = entry.getValueOrException();
    if (v instanceof ExceptionWrapper || entry.isVirgin()) {
      return null;
    }
    return v;
  }

  @Override
  public Throwable getCachedException() {
    V v = entry.getValueOrException();
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

  /**
   * Entry point to execute this action.
   */
  public void run() {
    needsFinish = true;
    operation.start(this);
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
      lockFor(MUTATE);
      if (!entryLocked) { return; }
      countMiss = false;
      operation.examine(this, entry);
      if (needsFinish) {
        finish();
      }
      return;
    }
    checkExpiryBeforeMutation();
  }

  /**
   * Check whether we are executed on an expired entry before the
   * timer event for expiry was received. In case expiry listeners
   * are present, we want to make sure that an expiry is sent before
   * a mutation (especially load) happens.
   *
   * update counter, stop timer?
   */
  public void checkExpiryBeforeMutation() {
    if (entryExpiredListeners() != null && entry == NON_FRESH_DUMMY) {
      needsFinish = true;
      wantData();
      return;
    }
    if (entryExpiredListeners() != null && entry.getNextRefreshTime() < 0) {
      boolean justExpired = false;
      synchronized (entry) {
        if (entry.getNextRefreshTime() < 0) {
          justExpired = true;
          entry.setNextRefreshTime(Entry.EXPIRED);
          timing().stopStartTimer(0, entry);
        }
      }
      if (justExpired) {
        CacheEntry<K, V> entryCopy = heapCache.returnCacheEntry(entry);
        sendExpiryEvents(entryCopy);
        metrics().expiredKept();
      }
    }
    continueWithMutation();
  }

  /**
   * Separate entry point used for expiry event to execute the expiry on the action context.
   * In this case the entry is already locked.
   */
  public void executeMutationForStartedProcessing() {
    needsFinish = true;
    entryLocked = true;
    heapDataValid = entry.isDataValidOrProbation();
    continueWithMutation();
  }

  public void continueWithMutation() {
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
    lockFor(LOAD);
    checkLocked();
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
    if ((_asyncLoader = asyncLoader()) != null) {
      lockFor(LOAD_ASYNC);
      try {
        _asyncLoader.load(key, this, this);
      } catch (Exception ex) {
        onLoadFailure(ex);
        exceptionToPropagate = new CacheLoaderException(ex);
        return;
      }
      waitIfSynchronous();
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
    while(lockOrWait(ps, e)) {
      e = heapCache.lookupOrNewEntry(key);
    }
  }

  void lockForNoHit(int ps) {
    if (entryLocked) {
      entry.nextProcessingStep(ps);
      return;
    }
    Entry<K, V> e = entry;
    if (e == NON_FRESH_DUMMY) {
      e = heapCache.lookupOrNewEntryNoHitRecord(key);
    }
    while (lockOrWait(ps, e)) {
      e = heapCache.lookupOrNewEntryNoHitRecord(key);
    }
  }

  /**
   * In case another entry action is currently running and this is an async operation,
   * don't block, just chain in the start of this action into the running action.
   */
  boolean lockOrWait(int ps, Entry e) {
    synchronized (e) {
      e.waitForProcessing();
      if (!e.isGone()) {
        e.startProcessing(ps, this);
        entryLocked = true;
        heapDataValid = e.isDataValidOrProbation();
        heapHit = !e.isVirgin();
        entry = e;
        return false;
      }
    }
    return true;
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

  /**
   * Make sure only one callback succeeds. The entry reference is volatile,
   * so we are sure its there.
   */
  private void checkEntryStateOnLoadCallback() {
    synchronized (entry) {
      if (!entry.checkAndSwitchProcessingState(LOAD_ASYNC, LOAD_COMPLETE) || completed) {
        throw new IllegalStateException("async callback on wrong entry state. duplicate callback?");
      }
    }
    checkLocked();
  }

  private void checkLocked() {
    if (!entryLocked) {
      throw new AssertionError();
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
    entry.nextProcessingStep(LOAD_COMPLETE);
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
    lockFor(MUTATE);
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
    lockForNoHit(MUTATE);
    needsFinish = false;
    remove = true;
    mutationMayCallWriter();
  }

  @Override
  public void expire(long expiryTime) {
    lockForNoHit(EXPIRE);
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
    lockFor(MUTATE);
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
    entry.nextProcessingStep(EXPIRY);
    if (newValueOrException instanceof ExceptionWrapper) {
      try {
        expiry = 0;
        ExceptionWrapper ew = (ExceptionWrapper) newValueOrException;
        if ((entry.isDataValid() || entry.isExpiredState()) && entry.getException() == null) {
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
    entry.nextProcessingStep(EXPIRY_COMPLETE);
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
      needsFinish = false;
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
        entry.nextProcessingStep(WRITE);
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
    entry.nextProcessingStep(WRITE);
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
    entry.nextProcessingStep(WRITE_COMPLETE);
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
   * In case we have an expiry of 0, this means that the entry should
   * not be cached. If there is a valid entry, we remove it if we do not
   * keep the data.
   */
  public void checkKeepOrRemove() {
    boolean _hasKeepAfterExpired = heapCache.isKeepAfterExpired();
    if (expiry != 0 || remove) {
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

  /**
   * The final write in the entry is at {@link #mutationReleaseLockAndStartTimer()}
   */
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
    CacheEntry<K,V> entryCopy = heapCache.returnCacheEntry(entry);
    if (expiredImmediately) {
      if (storageDataValid || heapDataValid) {
        if (entryExpiredListeners() != null) {
          sendExpiryEvents(entryCopy);
        }
      }
    } else if (remove) {
      if (storageDataValid || heapDataValid) {
        if (entryRemovedListeners() != null) {
          for (CacheEntryRemovedListener<K,V> l : entryRemovedListeners()) {
            try {
              l.onEntryRemoved(userCache, entryCopy);
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
              l.onEntryUpdated(userCache, _previousEntry, entryCopy);
            } catch (Throwable t) {
              exceptionToPropagate = new ListenerException(t);
            }
          }
        }
      } else {
        if (entryCreatedListeners() != null) {
          for (CacheEntryCreatedListener<K,V> l : entryCreatedListeners()) {
            try {
              l.onEntryCreated(userCache, entryCopy);
            } catch (Throwable t) {
              exceptionToPropagate = new ListenerException(t);
            }
          }
        }
      }
    }
    mutationReleaseLockAndStartTimer();
  }

  private void sendExpiryEvents(final CacheEntry<K, V> _entryCopy) {
    for (CacheEntryExpiredListener<K,V> l : entryExpiredListeners()) {
      try {
        l.onEntryExpired(userCache, _entryCopy);
      } catch (Throwable t) {
        exceptionToPropagate = new ListenerException(t);
      }
    }
  }

  /**
   * Mutate the entry and start timer for expiry.
   * Entry mutation and start of expiry has to be done atomically to avoid races.
   */
  public void mutationReleaseLockAndStartTimer() {
    checkLocked();
    checkLocked();
    if (load) {
      if (!remove ||
        !(entry.getValueOrException() == null && heapCache.isRejectNullValues())) {
        operation.loaded(this, entry);
      }
    }
    boolean justExpired = false;
    synchronized (entry) {
      if (refresh) {
        heapCache.startRefreshProbationTimer(entry, expiry);
      } else if (remove) {
        heapCache.removeEntry(entry);
      } else {
        entry.setNextRefreshTime(timing().stopStartTimer(expiry, entry));
        if (!expiredImmediately && entry.isExpiredState()) {
          justExpired = true;
          lockFor(EXPIRE);
        }
      }
      if (!justExpired) {
        entry.processingDone(this);
        entryLocked = false;
      }
    }
    if (justExpired) {
      expiry = 0;
      checkKeepOrRemove();
      return;
    }
    updateMutationStatistics();
    mutationDone();
  }

  public void updateMutationStatistics() {
    if (loadStartedTime > 0) {
      return;
    }
    if (expiredImmediately && !remove) {
      metrics().expiredKept();
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
        entry.processingDone(this);
        entryLocked = false;
      }
    }
    ready();
  }

  public void mutationAbort(RuntimeException t) {
    exceptionToPropagate = t;
    synchronized (entry) {
      entry.processingDone(this);
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
        entry.processingDone(this);
        if (entry.isVirgin()) {
          heapCache.removeEntry(entry);
        }
      }
      entryLocked = false;
    }
    updateOnlyReadStatistics();
    ready();
  }

  /**
   * Execute any callback or other actions waiting for this one to complete.
   * It is safe to access {@link #nextAction} here, also we don't hold the entry lock
   * since the entry does not point on this action any more.
   */
  public void ready() {
    completed = true;
    if (completedCallback != null) {
      completedCallback.entryActionCompleted(this);
    }
    if (nextAction != null) {
      nextAction.run();
    }
  }

  /**
   * If thread is a synchronous call, wait until operation is complete.
   * There is a little chance that the callback completes before we get
   * here as well as some other operation changing the entry again.
   */
  public void waitIfSynchronous() {
    if (syncThread == Thread.currentThread()) {
      synchronized (entry) {
        entry.waitForProcessing();
      }
    } else {
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

  public interface CompletedCallback<K,V,R> {
    void entryActionCompleted(EntryAction<K,V,R> ea);
  }

}
