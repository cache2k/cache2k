package org.cache2k.core;

/*
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
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
import org.cache2k.CacheException;
import org.cache2k.core.api.CommonMetrics;
import org.cache2k.core.api.InternalCache;
import org.cache2k.core.api.InternalClock;
import org.cache2k.core.timing.Timing;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.event.CacheEntryExpiredListener;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.io.AdvancedCacheLoader;
import org.cache2k.event.CacheEntryCreatedListener;
import org.cache2k.event.CacheEntryRemovedListener;
import org.cache2k.event.CacheEntryUpdatedListener;
import org.cache2k.io.CacheLoaderException;
import org.cache2k.io.CacheWriter;
import org.cache2k.io.CacheWriterException;
import org.cache2k.CustomizationException;
import org.cache2k.io.AsyncCacheLoader;
import org.cache2k.core.experimentalApi.AsyncCacheWriter;
import org.cache2k.core.operation.ExaminationEntry;
import org.cache2k.core.operation.Progress;
import org.cache2k.core.operation.Semantic;
import org.cache2k.core.storageApi.StorageCallback;
import org.cache2k.core.storageApi.StorageAdapter;
import org.cache2k.core.storageApi.StorageEntry;
import org.cache2k.integration.RefreshedTimeWrapper;

import java.util.concurrent.Executor;

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
  AsyncCacheLoader.Callback<V>, AsyncCacheWriter.Callback, Progress<K, V, R> {

  @SuppressWarnings("rawtypes")
  public static final Entry NON_FRESH_DUMMY = new Entry();

  @SuppressWarnings("rawtypes")
  static final CompletedCallback NOOP_CALLBACK = new CompletedCallback() {
    @Override
    public void entryActionCompleted(EntryAction ea) {
    }
  };

  InternalCache<K, V> userCache;
  HeapCache<K, V> heapCache;
  K key;
  Semantic<K, V, R> operation;

  /**
   * Reference to the entry we do processing for or the dummy entry {@link #NON_FRESH_DUMMY}
   * The field is volatile, to be safe when we get a callback from a different thread.
   */
  volatile Entry<K, V> heapEntry;
  ExaminationEntry<K, V> heapOrLoadedEntry;
  V newValueOrException;
  V oldValueOrException;
  R result;

  /**
   * Only set on request, use getter {@link #getMutationStartTime()}
   */
  long mutationStartTime;

  long lastRefreshTime;
  long loadStartedTime;
  long loadCompletedTime;
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

  boolean storageRead = false;
  boolean storageMiss = false;

  boolean heapMiss = false;

  boolean wantData = false;
  boolean heapHit = false;

  /**
   * Entry value was requested, cache entry was not available or
   * not valid/expired
   */
  boolean countMiss = false;
  boolean doNotCountAccess = false;

  /**
   * Load the value and restart the processing. Used if a load is triggered
   * within the EntryProcessor.
   */
  boolean loadAndRestart = false;

  /**
   * Loader was called or a refreshed entry was revived
   */
  boolean valueLoadedOrRevived = false;

  /**
   * The effective value was loaded. This may be false although the loader
   * was called, if the loaded value is rejected in the entry processor.
   */
  boolean valueDefinitelyLoaded = false;

  /**
   * Loader was called by this action, tracked for statistics updates.
   * The entry processor may override a loaded value, resetting valueWasLoaded, etc.
   */
  boolean loaderWasCalled = false;

  /** Stats for load should be counted as refresh */
  boolean refresh = false;

  /**
   * Fresh load in first round with {@link #loadAndRestart}.
   * Triggers that we always say it is present.
   */
  boolean successfulLoad = false;

  boolean suppressException = false;

  Thread syncThread;

  /**
   * Callback on on completion, set if client request is async.
   */
  CompletedCallback<K, V, R> completedCallback;

  /**
   * Linked list of actions waiting for execution after this one.
   * Guarded by the entry lock.
   */
  private EntryAction<K, V, ?> nextAction = null;

  private int semanticCallback = 0;

  /**
   * Action is completed
   */
  private boolean completed;

  /**
   * Called on the processing action to enqueue another action
   * to be executed next. Insert at the tail of the double linked
   * list. We are not part of the list.
   */
  @SuppressWarnings("rawtypes")
  public void enqueueToExecute(EntryAction v) {
    EntryAction next;
    EntryAction target = this;
    while ((next = target.nextAction) != null) {
      target = next;
    }
    target.nextAction = v;
  }

  @SuppressWarnings("unchecked")
  public EntryAction(HeapCache<K, V> heapCache, InternalCache<K, V> userCache,
                     Semantic<K, V, R> op, K k, Entry<K, V> e, CompletedCallback<K, V, R> cb) {
    super(null);
    this.heapCache = heapCache;
    this.userCache = userCache;
    operation = op;
    key = k;
    if (e != null) {
      heapEntry = e;
    } else {
      heapEntry = (Entry<K, V>) NON_FRESH_DUMMY;
    }
    if (cb == null) {
      syncThread = Thread.currentThread();
    } else {
      completedCallback = cb;
    }
  }

  public EntryAction(HeapCache<K, V> heapCache, InternalCache<K, V> userCache,
                     Semantic<K, V, R> op, K k, Entry<K, V> e) {
    this(heapCache, userCache, op, k, e, null);
  }

  @Override
  public Executor getExecutor() {
    return executor();
  }

  protected abstract Executor executor();

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

  protected abstract Timing<K, V> timing();

  @Override
  public K getKey() {
    return heapEntry.getKey();
  }

  @Override
  public long getLoadStartTime() {
    return getMutationStartTime();
  }

  @Override
  public long getMutationStartTime() {
    if (mutationStartTime > 0) {
      return mutationStartTime;
    }
    mutationStartTime = millis();
    return mutationStartTime;
  }

  @Override
  public CacheEntry<K, V> getCurrentEntry() {
    if (heapEntry.isVirgin()) {
      return null;
    }
    return heapCache.returnEntry(heapEntry);
  }

  @Override
  public boolean isLoaderPresent() {
    return userCache.isLoaderPresent();
  }

  @Override
  public boolean wasLoaded() {
    return successfulLoad;
  }

  @Override
  public boolean isDataFresh() {
    doNotCountAccess = true;
    return hasFreshData();
  }

  /**
   * Same as {@link Entry#hasFreshData(InternalClock)} but keep time identically
   * once checked to ensure timing based decisions do not change during the
   * the processing.
   */
  protected boolean hasFreshData() {
    long nrt = heapEntry.getNextRefreshTime();
    if (nrt >= Entry.DATA_VALID) {
      return true;
    }
    if (Entry.needsTimeCheck(nrt)) {
      return getMutationStartTime() < -nrt;
    }
    return false;
  }

  @Override
  public boolean isExpiryTimeReachedOrInRefreshProbation() {
    doNotCountAccess = true;
    long nrt = heapEntry.getNextRefreshTime();
    if (nrt == Entry.EXPIRED_REFRESHED) {
      return true;
    }
    if (nrt >= 0 && nrt < Entry.DATA_VALID) {
      return false;
    }
    return Math.abs(nrt) <= getMutationStartTime();
  }

  @Override
  public boolean isDataFreshOrRefreshing() {
    doNotCountAccess = true;
    long nrt = heapEntry.getNextRefreshTime();
    return
      nrt == Entry.EXPIRED_REFRESHED ||
      nrt == Entry.EXPIRED_REFRESH_PENDING || hasFreshData();
  }

  @Override
  public boolean isDataFreshOrMiss() {
    if (hasFreshData()) { return true; }
    countMiss = true;
    return false;
  }

  @Override
  public void run() {
    try {
      start();
    } catch (CacheClosedException ignore) {
    }
  }

  /**
   * Entry point to execute this action.
   */
  public void start() {
    int callbackCount = semanticCallback;
    operation.start(this);
  }

  @Override
  public void wantData() {
    semanticCallback++;
    wantData = true;
    if (heapEntry == NON_FRESH_DUMMY) {
      retrieveDataFromHeap();
    } else {
      if (completedCallback != NOOP_CALLBACK) {
        heapHit = true;
      }
      skipHeapAccessEntryPresent();
    }
  }

  public void retrieveDataFromHeap() {
    Entry<K, V> e = heapEntry;
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
    heapOrLoadedEntry = heapEntry;
    examine();
  }

  public void heapHit(Entry<K, V> e) {
    heapHit = true;
    heapEntry = e;
    heapOrLoadedEntry = heapEntry;
    examine();
  }

  public void skipHeapAccessEntryPresent() {
    heapOrLoadedEntry = heapEntry;
    examine();
  }

  public void examine() {
    int callbackCount = semanticCallback;
    mutationStartTime = 0;
    operation.examine(this, heapOrLoadedEntry);
  }

  @Override
  public void noMutation() {
    semanticCallback++;
    if (successfulLoad) {
      updateDidNotTriggerDifferentMutationStoreLoadedValue();
      return;
    }
    abort();
  }

  @Override
  public void wantMutation() {
    semanticCallback++;
    if (!entryLocked) {
      if (lockForNoHit(MUTATE)) { return; }
      if (wantData) {
        reexamineAfterLock();
        return;
      }
    } else {
      heapEntry.nextProcessingStep(MUTATE);
    }
    checkExpiryBeforeMutation();
  }

  /**
   * Entry state may have changed between the first examination and obtaining
   * the entry lock. Examine again.
   */
  public void reexamineAfterLock() {
    countMiss = false;
    heapOrLoadedEntry = heapEntry;
    mutationStartTime = 0;
    examine();
  }

  /**
   * Check whether we are executed on an expired entry before the
   * timer event for the expiry was received. In case expiry listeners
   * are present, we want to make sure that an expiry is sent before
   * a mutation happens, which was eventually triggered the fact that the
   * entry expired.
   */
  public void checkExpiryBeforeMutation() {
    if (entryExpiredListeners() == null) {
      noExpiryListenersPresent();
      return;
    }
    long nrt = heapEntry.getNextRefreshTime();
    if (nrt >= 0) {
      continueWithMutation();
      return;
    }
    long millis = getMutationStartTime();
    if (millis >= -nrt) {
      boolean justExpired = false;
      synchronized (heapEntry) {
        justExpired = true;
        heapEntry.setNextRefreshTime(Entry.EXPIRED);
        timing().stopStartTimer(0, heapEntry);
        heapDataValid = false;
      }
      if (justExpired) {
        existingEntryExpiredBeforeMutationSendExpiryEvents();
        return;
      }
    }
    continueWithMutation();
  }

  /**
   * The entry logically expired before the mutation. Send expiry event.
   * Example: A get() triggers a load().
   * Also the expiry timer event sends out the events via this method.
   *
   * @see org.cache2k.core.operation.Operations#expireEvent
   */
  public void existingEntryExpiredBeforeMutationSendExpiryEvents() {
    CacheEntry<K, V> entryCopy = heapCache.returnCacheEntry(heapEntry);
    sendExpiryEvents(entryCopy);
    metrics().expiredKept();
    continueWithMutation();
  }

  public void noExpiryListenersPresent() {
    continueWithMutation();
  }

  public void continueWithMutation() {
    int callbackCount = semanticCallback;
    operation.mutate(this, heapOrLoadedEntry);
  }

  @Override
  public void loadAndRestart() {
    loadAndRestart = true;
    load();
  }

  @Override
  public void refresh() {
    refresh = true;
    load();
  }

  private boolean needsLoadTimes() {
    return heapCache.isUpdateTimeNeeded() || !metrics().isDisabled();
  }

  @Override
  public void load() {
    semanticCallback++;
    if (!isLoaderPresent()) {
      mutationAbort(new CacheException("load requested but no loader defined"));
      return;
    }
    heapEntry.nextProcessingStep(LOAD);
    Entry<K, V> e = heapEntry;
    valueLoadedOrRevived = true;
    long t0 = needsLoadTimes() ? lastRefreshTime = loadStartedTime = getMutationStartTime() : 0;
    if (e.getNextRefreshTime() == Entry.EXPIRED_REFRESHED) {
      long nrt = e.getRefreshProbationNextRefreshTime();
      if (nrt > t0) {
        reviveRefreshedEntry(nrt);
        return;
      }
    }
    valueDefinitelyLoaded = true;
    loaderWasCalled = true;
    AsyncCacheLoader<K, V> asyncLoader;
    if ((asyncLoader = asyncLoader()) != null) {
      heapEntry.nextProcessingStep(LOAD_ASYNC);
      try {
        asyncLoader.load(key, this, this);
      } catch (Throwable ouch) {
        onLoadFailure(ouch);
        /*
        Don't propagate exception to the direct caller here via exceptionToPropagate.
        The exception might be temporarily e.g. no execution reject and masked
        by the resilience.
         */
        return;
      }
      asyncExecutionStartedWaitIfSynchronousCall();
      return;
    }
    AdvancedCacheLoader<K, V> loader = loader();
    V v;
    try {
      if (e.isVirgin()) {
        v = loader.load(key, t0, null);
      } else {
        v = loader.load(key, t0, e);
      }
    } catch (Throwable ouch) {
      onLoadFailureIntern(ouch);
      return;
    }
    onLoadSuccessIntern(v);
  }

  public void reviveRefreshedEntry(long nrt) {
    metrics().refreshedHit();
    Entry<K, V> e = heapEntry;
    newValueOrException = e.getValueOrException();
    lastRefreshTime = e.getModificationTime();
    expiry = nrt;
    expiryCalculated();
  }

  /**
   * @return true, in case this is an async call and enqueued the operation
   *         in the running one
   */
  @SuppressWarnings("SameParameterValue")
  private boolean lockForNoHit(int ps) {
    if (entryLocked) {
      heapEntry.nextProcessingStep(ps);
      return false;
    }
    Entry<K, V> e = heapEntry;
    if (e == NON_FRESH_DUMMY) {
      e = heapCache.lookupOrNewEntryNoHitRecord(key);
    }
    for (;;) {
      synchronized (e) {
        if (tryEnqueueOperationInCurrentlyProcessing(e)) {
          return true;
        }
        if (waitForConcurrentProcessingOrStop(ps, e)) {
          return false;
        }
      }
      e = heapCache.lookupOrNewEntryNoHitRecord(key);
    }
  }

  /**
   * If entry is currently processing, and this is an async request, we can
   * enqueue this operation in a waitlist that gets executed when
   * the processing has completed.
   */
  @SuppressWarnings("rawtypes")
  private boolean tryEnqueueOperationInCurrentlyProcessing(Entry e) {
    if (e.isProcessing() && completedCallback != null) {
      EntryAction runningAction = e.getEntryAction();
      if (runningAction != null) {
        runningAction.enqueueToExecute(this);
        return true;
      }
    }
    return false;
  }

  /**
   * Wait for concurrent processing. The entry might be gone as cause of the concurrent operation.
   * In this case we need to insert a new entry.
   *
   * @return true if we got the entry lock, false if we need to spin
   */
  @SuppressWarnings("rawtypes")
  private boolean waitForConcurrentProcessingOrStop(int ps, Entry e) {
    e.waitForProcessing();
    if (!e.isGone()) {
      e.startProcessing(ps, this);
      entryLocked = true;
      heapDataValid = e.isDataAvailableOrProbation();
      heapEntry = e;
      return true;
    }
    return false;
  }

  @Override
  public void onLoadSuccess(V v) {
    checkEntryStateOnLoadCallback();
    onLoadSuccessIntern(v);
  }

  /**
   * The load failed, resilience and refreshing needs to be triggered
   */
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
    synchronized (heapEntry) {
      if (!heapEntry.checkAndSwitchProcessingState(LOAD_ASYNC, LOAD_COMPLETE) || completed) {
        throw new IllegalStateException("async callback on wrong entry state. duplicate callback?");
      }
    }
  }

  private void onLoadSuccessIntern(V v) {
    if (v instanceof RefreshedTimeWrapper) {
      RefreshedTimeWrapper<V> wr = (RefreshedTimeWrapper<V>) v;
      lastRefreshTime = wr.getRefreshTime();
      v = wr.getValue();
    }

    newValueOrException = v;
    loadCompleted();
  }

  private void onLoadFailureIntern(Throwable t) {
    newValueOrException =
      (V) new ExceptionWrapper<K>(key, t, loadStartedTime, heapEntry, getExceptionPropagator());
    loadCompleted();
  }

  public void loadCompleted() {
    heapEntry.nextProcessingStep(LOAD_COMPLETE);
    entryLocked = true;
    if (needsLoadTimes()) {
      loadCompletedTime = millis();
    }
    mutationCalculateExpiry();
  }

  @Override
  public void result(R r) {
    result = r;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void entryResult(ExaminationEntry<K, V> e) {
    result = (R) heapCache.returnEntry(e);
  }

  @Override
  public void put(V value) {
    semanticCallback++;
    heapEntry.nextProcessingStep(MUTATE);
    newValueOrException = value;
    if (!heapCache.isUpdateTimeNeeded()) {
      lastRefreshTime = 0;
    } else {
      lastRefreshTime = getMutationStartTime();
    }
    mutationCalculateExpiry();
  }

  @Override
  public void remove() {
    semanticCallback++;
    heapEntry.nextProcessingStep(MUTATE);
    remove = true;
    mutationMayCallWriter();
  }

  @Override
  public void expire(long expiryTime) {
    semanticCallback++;
    heapEntry.nextProcessingStep(EXPIRE);
    newValueOrException = heapEntry.getValueOrException();
    if (heapCache.isUpdateTimeNeeded()) {
      lastRefreshTime = heapEntry.getModificationTime();
    }
    expiry = expiryTime;
    setUntilInExceptionWrapper();
    checkKeepOrRemove();
  }

  @Override
  public void putAndSetExpiry(V value, long expiryTime, long refreshTime) {
    semanticCallback++;
    heapEntry.nextProcessingStep(MUTATE);
    newValueOrException = value;
    if (refreshTime >= 0) {
      lastRefreshTime = refreshTime;
    } else {
      if (heapCache.isUpdateTimeNeeded()) {
        lastRefreshTime = getMutationStartTime();
      }
    }
    setUntilInExceptionWrapper();
    if (expiryTime != ExpiryTimeValues.NEUTRAL) {
      expiry = expiryTime;
      expiryCalculated();
    } else {
      mutationCalculateExpiry();
    }
  }

  public void mutationCalculateExpiry() {
    heapEntry.nextProcessingStep(EXPIRY);
    if (newValueOrException instanceof ExceptionWrapper) {
      try {
        expiry = 0;
        ExceptionWrapper<K> ew = (ExceptionWrapper<K>) newValueOrException;
        if ((heapEntry.isDataAvailable() || heapEntry.isExpiredState()) &&
          !heapEntry.hasException()) {
          expiry = timing().suppressExceptionUntil(heapEntry, ew);
        }
        if (expiry > loadStartedTime) {
          suppressException = true;
          newValueOrException = heapEntry.getValueOrException();
          lastRefreshTime = heapEntry.getModificationTime();
          metrics().suppressedException();
          heapEntry.setSuppressedLoadExceptionInformation(ew);
        } else {
          if (valueDefinitelyLoaded) {
            metrics().loadException();
          }
          expiry = timing().cacheExceptionUntil(heapEntry, ew);
        }
        setUntilInExceptionWrapper();
      } catch (Throwable ex) {
        if (valueDefinitelyLoaded) {
          resiliencePolicyException(new ResiliencePolicyException(ex));
          return;
        }
        expiryCalculationException(ex);
        return;
      }
    } else {
      try {
        expiry = timing().calculateNextRefreshTime(heapEntry, newValueOrException, lastRefreshTime);
        if (newValueOrException == null && heapCache.isRejectNullValues() &&
          expiry != ExpiryTimeValues.NOW) {
          RuntimeException ouch = heapCache.returnNullValueDetectedException();
          if (valueDefinitelyLoaded) {
            decideForLoaderExceptionAfterExpiryCalculation(new CacheLoaderException(ouch));
          } else {
            mutationAbort(ouch);
          }
          return;
        }
        heapEntry.resetSuppressedLoadExceptionInformation();
      } catch (Throwable ex) {
        if (valueDefinitelyLoaded) {
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
  private void decideForLoaderExceptionAfterExpiryCalculation(RuntimeException ouch) {
    newValueOrException = (V)
      new ExceptionWrapper<K>(
        key, ouch, loadStartedTime,
        heapEntry, getExceptionPropagator());
    expiry = 0;
    mutationCalculateExpiry();
  }

  /**
   * We have two exception in this case: One from the loader or the expiry policy, one from
   * the resilience policy. Propagate exception from the resilience policy and suppress
   * the other, since this is a general configuration problem.
   */
  @SuppressWarnings("unchecked")
  private void resiliencePolicyException(RuntimeException ouch) {
    newValueOrException = (V)
      new ExceptionWrapper<K>(key, ouch, loadStartedTime, heapEntry, getExceptionPropagator());
    expiry = 0;
    expiryCalculated();
  }

  /**
   * In case current value is an exception, set until information, so this can
   * be used by generated exceptions.
   */
  private void setUntilInExceptionWrapper() {
    if (newValueOrException instanceof ExceptionWrapper) {
      ExceptionWrapper<K> ew = (ExceptionWrapper<K>) newValueOrException;
      if (expiry < 0) {
        newValueOrException = (V) new ExceptionWrapper<K>(ew, -expiry);
      } else if (expiry >= Entry.EXPIRY_TIME_MIN) {
        newValueOrException = (V) new ExceptionWrapper<K>(ew, expiry);
      }
    }
  }

  public void expiryCalculationException(Throwable t) {
    mutationAbort(new ExpiryPolicyException(t));
  }

  public void expiryCalculated() {
    heapEntry.nextProcessingStep(EXPIRY_COMPLETE);
    if (valueLoadedOrRevived) {
      if (loadAndRestart) {
        loadAndExpiryCalculatedExamineAgain();
        return;
      }
      checkKeepOrRemove();
      return;
    } else {
      if (expiry > 0 || expiry == -1 || (expiry < 0 && -expiry > loadStartedTime)) {
        if (heapEntry.isVirgin()) {
          metrics().putNewEntry();
        } else {
          metrics().putHit();
        }
      }
    }
    mutationMayCallWriter();
  }

  public void loadAndExpiryCalculatedExamineAgain() {
    valueDefinitelyLoaded = valueLoadedOrRevived = loadAndRestart = false;
    successfulLoad = true;
    heapOrLoadedEntry = new ExaminationEntry<K, V>() {
      @Override
      public K getKey() {
        return heapEntry.getKey();
      }

      @Override
      public V getValueOrException() {
        return newValueOrException;
      }

      @Override
      public long getModificationTime() {
        return lastRefreshTime;
      }

    };
    examine();
  }

  public void updateDidNotTriggerDifferentMutationStoreLoadedValue() {
    checkKeepOrRemove();
  }

  /**
   * Entry mutation, call writer if needed or skip to {@link #mutationMayStore()}
   */
  public void mutationMayCallWriter() {
    if (writer() == null) {
      skipWritingNoWriter();
      return;
    }
    mutationCallWriter();
  }

  public void mutationCallWriter() {
    if (remove) {
      try {
        heapEntry.nextProcessingStep(WRITE);
        writer().delete(key);
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
    heapEntry.nextProcessingStep(WRITE);
    try {
      writer().write(key, newValueOrException);
    } catch (Throwable t) {
      onWriteFailure(t);
      return;
    }
    onWriteSuccess();
  }

  @Override
  public void onWriteSuccess() {
    heapEntry.nextProcessingStep(WRITE_COMPLETE);
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
    boolean hasKeepAfterExpired = heapCache.isKeepAfterExpired();
    if (expiry != 0 || remove) {
      mutationUpdateHeap();
      return;
    }
    if (hasKeepAfterExpired) {
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
    synchronized (heapEntry) {
      if (heapCache.isRecordRefreshTime()) {
        heapEntry.setRefreshTime(lastRefreshTime);
      }
      if (remove) {
        if (expiredImmediately) {
          heapEntry.setNextRefreshTime(Entry.EXPIRED);
          heapEntry.setValueOrException(newValueOrException);
        } else {
          if (!heapEntry.isVirgin()) {
            heapEntry.setNextRefreshTime(Entry.REMOVE_PENDING);
          }
        }
      } else {
        oldValueOrException = heapEntry.getValueOrException();
        heapEntry.setValueOrException(newValueOrException);
      }
    }
    if (!remove) {
      boolean evictionHint = heapCache.eviction.updateWeight(heapEntry);
      if (evictionHint) {
        heapCache.eviction.evictEventually();
      }
    }
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
    CacheEntry<K, V> entryCopy = heapCache.returnCacheEntry(heapEntry);
    if (expiredImmediately) {
      sendExpiryEventsWhenExpiredDuringOperation(entryCopy);
    } else if (remove) {
      if (storageDataValid || heapDataValid) {
        if (entryRemovedListeners() != null) {
          for (CacheEntryRemovedListener<K, V> l : entryRemovedListeners()) {
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
          CacheEntry<K, V> previousEntry =
            heapCache.returnCacheEntry(heapEntry.getKey(), oldValueOrException);
          for (CacheEntryUpdatedListener<K, V> l : entryUpdatedListeners()) {
            try {
              l.onEntryUpdated(userCache, previousEntry, entryCopy);
            } catch (Throwable t) {
              exceptionToPropagate = new ListenerException(t);
            }
          }
        }
      } else {
        if (entryCreatedListeners() != null) {
          for (CacheEntryCreatedListener<K, V> l : entryCreatedListeners()) {
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

  /**
   * Entry was expired during the operation, e.g. by calling setExpiry.
   * In case the entry is freshly created
   * <p>If an entry is created or updated, the expiry listener might be called
   * later in the process, after the timer event was tried to be scheduled.
   * <p>Don't send events in case the entry never was inserted,
   * because in that case we don't send a created event either. Rationale: A created
   * and expiry event only makes sense if something is visible for a short time.
   *
   * @param entryCopy copy of entry for sending to the listener
   */
  private void sendExpiryEventsWhenExpiredDuringOperation(CacheEntry<K, V> entryCopy) {
    if (storageDataValid || heapDataValid) {
      if (entryExpiredListeners() != null) {
        sendExpiryEvents(entryCopy);
      }
    }
  }

  private void sendExpiryEvents(CacheEntry<K, V> entryCopy) {
    for (CacheEntryExpiredListener<K, V> l : entryExpiredListeners()) {
      try {
        l.onEntryExpired(userCache, entryCopy);
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
    if (valueLoadedOrRevived) {
      if (!remove ||
        !(heapEntry.getValueOrException() == null && heapCache.isRejectNullValues())) {
        operation.loaded(this, heapEntry);
      }
    }
    boolean justExpired = false;
    synchronized (heapEntry) {
      if (refresh) {
        heapCache.startRefreshProbationTimer(heapEntry, expiry);
      } else if (remove) {
        heapCache.removeEntry(heapEntry);
      } else {
        heapEntry.setNextRefreshTime(timing().stopStartTimer(expiry, heapEntry));
        boolean entryExpired = heapEntry.isExpiredState();
        if (!expiredImmediately && entryExpired) {
          justExpired = true;
        }
      }
      if (!justExpired) {
        heapEntry.processingDone(this);
        entryLocked = false;
      }
    }
    if (justExpired) {
      expiredAtEndOfOperationStartOver();
      return;
    }
    updateMutationStatistics();
    mutationDone();
  }

  /**
   * Entry expired during progress of the action, reentry at a previous processing
   * state to call listeners and update heap.
   * <p>We could get rid of this path in case the timer would just execute immediately.
   */
  public void expiredAtEndOfOperationStartOver() {
    heapDataValid = true;
    heapEntry.nextProcessingStep(EXPIRE);
    expiry = 0;
    checkKeepOrRemove();
  }

  /**
   * True if data is requested and consumed and this should be counted
   * in the get statistics. False, in case of refresh or explicit load.
   */
  private boolean isGetLike() {
    return !refresh && completedCallback != NOOP_CALLBACK && countMiss;
  }

  public void updateMutationStatistics() {
    if (expiredImmediately && !remove) {
      metrics().expiredKept();
    }
    if (loaderWasCalled) {
      long delta = loadCompletedTime - loadStartedTime;
      if (refresh) {
        metrics().refresh(delta);
      } else if (isGetLike()) {
        metrics().readThrough(delta);
      } else {
        metrics().explicitLoad(delta);
      }
      if (countMiss) {
        if (heapHit) {
          metrics().heapHitButNoRead();
        }
      }
      return;
    }
    updateReadStatisticsNoTailCall();
  }

  private void updateReadStatisticsNoTailCall() {
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
    semanticCallback++;
    mutationAbort(t);
  }

  public void examinationAbort(CustomizationException t) {
    exceptionToPropagate = t;
    abort();
  }

  public void mutationAbort(RuntimeException t) {
    exceptionToPropagate = t;
    abort();
  }

  /**
   *
   */
  public void mutationDone() {
    completeProcessCallbacks();
  }

  /**
   * Abort, but check whether entry was locked before.
   */
  public void abort() {
    if (entryLocked) {
      abortReleaseLock();
      return;
    }
    abortFinalize();
  }

  /**
   * Release the lock. Cleanup entry if it was inserted on behalf of this
   * operation.
   */
  public void abortReleaseLock() {
    synchronized (heapEntry) {
      heapEntry.processingDone(this);
      if (heapEntry.isVirgin()) {
        heapCache.removeEntry(heapEntry);
      }
      entryLocked = false;
    }
    abortFinalize();
  }

  public void abortFinalize() {
    updateReadStatisticsNoTailCall();
    completeProcessCallbacks();
  }

  /**
   * Execute any callback or other actions waiting for this one to complete.
   * It is safe to access {@link #nextAction} here, also we don't hold the entry lock
   * since the entry does not point on this action any more.
   *
   * <p>This is pretty basic and maybe needs more work.
   * TODO: what about an exception in a callback? send callbacks with another executor?
   */
  public void completeProcessCallbacks() {
    completed = true;
    if (nextAction != null) {
      executor().execute(nextAction);
    }
    if (completedCallback != null) {
      completedCallback.entryActionCompleted(this);
    }
    ready();
  }

  public void ready() {
  }

  /**
   * If thread is a synchronous call, wait until operation is complete.
   * There is a little chance that the callback completes before we get
   * here as well as some other operation mutating the entry again.
   */
  private void asyncExecutionStartedWaitIfSynchronousCall() {
    if (syncThread == Thread.currentThread()) {
      synchronized (heapEntry) {
        heapEntry.waitForProcessing();
      }
    }
  }

  public static class StorageReadException extends CustomizationException {
    public StorageReadException(Throwable cause) {
      super(cause);
    }
  }

  public static class StorageWriteException extends CustomizationException {
    public StorageWriteException(Throwable cause) {
      super(cause);
    }
  }

  public static class ProcessingFailureException extends CustomizationException {
    public ProcessingFailureException(Throwable cause) {
      super(cause);
    }
  }

  public static class ListenerException extends CustomizationException {
    public ListenerException(Throwable cause) {
      super(cause);
    }
  }

  public interface CompletedCallback<K, V, R> {
    void entryActionCompleted(EntryAction<K, V, R> ea);
  }

}
