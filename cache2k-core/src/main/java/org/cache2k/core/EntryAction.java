package org.cache2k.core;

/*-
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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

import org.cache2k.Cache;
import org.cache2k.CacheClosedException;
import org.cache2k.CacheEntry;
import org.cache2k.CacheException;
import org.cache2k.core.api.CommonMetrics;
import org.cache2k.core.api.InternalCache;
import org.cache2k.event.CacheEventListenerException;
import org.cache2k.expiry.RefreshAheadPolicy;
import org.cache2k.operation.TimeReference;
import org.cache2k.core.timing.Timing;
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
import org.cache2k.core.operation.ExaminationEntry;
import org.cache2k.core.operation.Progress;
import org.cache2k.core.operation.Semantic;

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
  AsyncCacheLoader.Callback<V>, Progress<K, V, R>,
  RefreshAheadPolicy.Context {

  @SuppressWarnings("rawtypes")
  public static final Entry NON_FRESH_DUMMY = new Entry();

  @SuppressWarnings("rawtypes")
  static final CompletedCallback NOOP_CALLBACK = ea -> { };

  /**
   * Effective internal cache, either HeapCache or WiredCache.
   * @see InternalCache#getUserCache()
   */
  final InternalCache<K, V> internalCache;
  /**
   * Heap cache holding the in heap objects.
   */
  final HeapCache<K, V> heapCache;
  K key;
  Semantic<K, V, R> operation;

  /**
   * Reference to the entry we do processing for or the dummy entry {@link #NON_FRESH_DUMMY}
   * The field is volatile, to be safe when we get a callback from a different thread.
   */
  volatile Entry<K, V> heapEntry;
  ExaminationEntry<K, V> heapOrLoadedEntry;
  Object newValueOrException;

  /**
   * Only set on request, use getter {@link #getMutationStartTime()}
   */
  long mutationStartTime;

  long modificationTime;
  long loadStartedTime;
  long loadCompletedTime;
  boolean remove;
  /** Special case of remove, expiry is in the past */
  boolean expiredImmediately;
  long expiry = 0;
  long refreshTime = 0;
  /**
   * We locked the entry, don't lock it again.
   */
  boolean entryLocked = false;
  /**
   * True if entry had some data after locking. Clock for expiry is not checked.
   * Also true if entry contains data in refresh probation.
   */
  boolean heapDataValid = false;

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
  boolean valueLoaded = false;

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

  /**
   * Load was yielding an exception. Also true when suppressed.
   */
  boolean loadException = false;

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
  private volatile boolean completed;

  /**
   * True: Abort if the entry is currently processing and we cannot proceed with
   * this operation. Used by the bulk action.
   */
  private boolean bulkMode;

  private volatile RuntimeException exceptionToPropagate;
  /** @see #isResultAvailable() */
  private volatile boolean resultAvailable;
  /** Result or wrapper */
  private volatile Object result;

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
  public EntryAction(HeapCache<K, V> heapCache, InternalCache<K, V> internalCache,
                     Semantic<K, V, R> op, K k, Entry<K, V> e, CompletedCallback<K, V, R> cb) {
    super(null);
    this.heapCache = heapCache;
    this.internalCache = internalCache;
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

  public void setBulkMode(boolean v) {
    bulkMode = v;
  }

  public boolean isBulkMode() {
    return bulkMode;
  }

  @Override
  public K getKey() {
    return key;
  }

  @Override
  public long getStartTime() {
    return getMutationStartTime();
  }

  @Override
  public boolean isRefreshAhead() {
    return refresh;
  }

  @Override
  public long getMutationStartTime() {
    if (mutationStartTime > 0) {
      return mutationStartTime;
    }
    mutationStartTime = ticks();
    return mutationStartTime;
  }

  /**
   * Returns the heap entry directly. This is only consistent while the entry
   * is blocked for processing.
   */
  @Override
  public CacheEntry<K, V> getCurrentEntry() {
    if (completed) {
      throw new IllegalStateException("Entry in context only valid while processing");
    }
    return heapEntry.isValidOrExpiredAndNoException() ? heapEntry : null;
  }

  @Override
  public boolean isLoaderPresent() {
    return internalCache.isLoaderPresent();
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
   * Same as {@link Entry#hasFreshData(TimeReference)} but keep time identically
   * once checked to ensure timing based decisions do not change during the
   * the processing.
   */
  protected boolean hasFreshData() {
    long nrt = heapEntry.getRawExpiry();
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
    long nrt = heapEntry.getRawExpiry();
    if (nrt >= 0 && nrt < Entry.DATA_VALID) {
      return false;
    }
    return Math.abs(nrt) <= getMutationStartTime();
  }

  @Override
  public boolean isDataRefreshing() {
    doNotCountAccess = true;
    long nrt = heapEntry.getRawExpiry();
    return nrt == Entry.EXPIRED_REFRESH_PENDING;
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
    } catch (Throwable t) {
      heapCache.getLog().warn("Exception during entry processing", t);
      heapCache.internalExceptionCnt++;
      throw t;
    }
  }

  /**
   * Entry point to execute this action.
   */
  public void start() {
    int callbackCount = semanticCallback;
    operation.start(key, this);
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

  private long ticks() {
    return heapCache.getClock().ticks();
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
    operation.examine(key, this, heapOrLoadedEntry);
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
   * a mutation happens, which was eventually triggered by the fact that the
   * entry expired. For example, a get() will trigger a load if the entry
   * expired.
   *
   * <p>In case the expiry time is positive, no time checks are carried out when accessing the
   * entry and the expiry would only happen on the expiry event. In this case, we can continue
   * without action, since no expiry happened before.
   */
  public void checkExpiryBeforeMutation() {
    if (entryExpiredListeners() == null) {
      noExpiryListenersPresent();
      return;
    }
    long currentExpiry = heapEntry.getRawExpiry();
    if (currentExpiry >= 0) {
      continueWithMutation();
      return;
    }
    long millis = getMutationStartTime();
    if (millis >= -currentExpiry) {
      boolean justExpired = false;
      synchronized (heapEntry) {
        justExpired = true;
        heapEntry.setRawExpiry(timing().stopStartTimer(heapEntry, ExpiryTimeValues.NOW, 0));
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
    operation.mutate(key, this, heapOrLoadedEntry);
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
    return heapCache.isModificationTimeNeeded() || !metrics().isDisabled();
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
    valueLoaded = true;
    long t0 = needsLoadTimes() ? modificationTime = loadStartedTime = getMutationStartTime() : 0;
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
        if (bulkMode && e.isProcessing()) {
          throw new AbortWhenProcessingException();
        }
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
   * enqueue this operation in a wait list that gets executed when
   * the processing has completed.
   *
   * @return action is enqueued and will be completed in another thread
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

  /**
   * Process async call back. Consistently log internal exceptions caused by the
   * cache. We ignore a CacheClosedException, since the cache might be closed
   * while the load was in flight. A CacheClosedException is still hard
   * to trigger in this path, because this usually would only update the cache
   * entry and touch no global resources.
   *
   * @param v the loaded value
   * @see <a href="https://github.com/cache2k/cache2k/issues/175">Github issue #175</a>
   */
  @Override
  public void onLoadSuccess(V v) {
    checkEntryStateOnLoadCallback();
    try {
      onLoadSuccessIntern(v);
    } catch (CacheClosedException ex) {
    } catch (Throwable internal) {
      heapCache.logAndCountInternalException("onLoadFailure async callback", internal);
      throw internal;
    }
  }

  /**
   * The load failed, resilience and refreshing needs to be triggered
   */
  @Override
  public void onLoadFailure(Throwable t) {
    checkEntryStateOnLoadCallback();
    try {
      onLoadFailureIntern(t);
    } catch (CacheClosedException ex) {
    } catch (Throwable internal) {
      heapCache.logAndCountInternalException("onLoadFailure async callback", internal);
      throw internal;
    }
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

    newValueOrException = v;
    loadCompleted();
  }

  private void onLoadFailureIntern(Throwable t) {
    newValueOrException =
      new ExceptionWrapper<K, V>(key, t, loadStartedTime, heapEntry, getExceptionPropagator());
    loadException = true;
    loadCompleted();
  }

  public void loadCompleted() {
    heapEntry.nextProcessingStep(LOAD_COMPLETE);
    entryLocked = true;
    if (needsLoadTimes()) {
      loadCompletedTime = ticks();
    }
    mutationCalculateExpiry();
  }

  @Override
  public void result(R r) {
    resultAvailable = true;
    result = r;
  }

  @Override
  public void resultOrWrapper(Object r) {
    resultAvailable = true;
    result = r;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void entryResult(ExaminationEntry<K, V> e) {
    resultAvailable = true;
    result = heapCache.returnEntry(e);
  }

  @Override
  public void put(V value) {
    semanticCallback++;
    heapEntry.nextProcessingStep(MUTATE);
    newValueOrException = value;
    if (!heapCache.isModificationTimeNeeded()) {
      modificationTime = 0;
    } else {
      modificationTime = getMutationStartTime();
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

  boolean expirySetOnly = false;

  @Override
  public void expire(long expiryTime) {
    semanticCallback++;
    heapEntry.nextProcessingStep(EXPIRE);
    expirySetOnly = true;
    newValueOrException = heapEntry.getValueOrExceptionNoAccess();
    if (heapCache.isModificationTimeNeeded()) {
      modificationTime = heapEntry.getModificationTime();
    }
    this.expiry = timing().limitExpiryTime(getStartTime(), expiryTime);
    this.refreshTime = timing().calculateRefreshTime(this);
    setUntilInExceptionWrapper();
    checkKeepOrRemove();
  }

  @Override
  public void putAndSetExpiry(V value, long expiryTime, long modificationTime) {
    semanticCallback++;
    heapEntry.nextProcessingStep(MUTATE);
    newValueOrException = value;
    if (modificationTime >= 0) {
      this.modificationTime = modificationTime;
    } else {
      if (heapCache.isModificationTimeNeeded()) {
        this.modificationTime = getMutationStartTime();
      }
    }
    if (expiryTime != ExpiryTimeValues.NEUTRAL) {
      this.expiry = timing().limitExpiryTime(getStartTime(), expiryTime);
      this.refreshTime = timing().calculateRefreshTime(this);
      setUntilInExceptionWrapper();
      expiryCalculated();
    } else {
      mutationCalculateExpiry();
    }
  }

  public void mutationCalculateExpiry() {
    heapEntry.nextProcessingStep(EXPIRY);
    if (heapCache.isDisabled()) {
      cacheDisabledExpireImmediately();
      return;
    }
    if (newValueOrException instanceof ExceptionWrapper) {
      try {
        expiry = 0;
        ExceptionWrapper<K, V> ew = (ExceptionWrapper<K, V>) newValueOrException;
        if (heapEntry.isValidOrExpiredAndNoException()) {
          expiry = timing().suppressExceptionUntil(heapEntry, ew);
        }
        if (expiry > loadStartedTime) {
          suppressException = true;
          newValueOrException = heapEntry.getValueOrException();
          modificationTime = heapEntry.getModificationTime();
          metrics().suppressedException();
          heapEntry.setSuppressedLoadExceptionInformation(ew);
        } else {
          if (valueDefinitelyLoaded) {
            metrics().loadException();
          }
          expiry = timing().cacheExceptionUntil(heapEntry, ew);
        }
        refreshTime = timing().calculateRefreshTime(this);
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
      V newValue = (V) newValueOrException;
      try {
        expiry = timing().calculateExpiry(heapEntry, newValue, modificationTime);
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
        refreshTime = timing().calculateRefreshTime(this);
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
    newValueOrException = new ExceptionWrapper<K, V>(
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
    newValueOrException = new ExceptionWrapper<K, V>(key, ouch, loadStartedTime, heapEntry, getExceptionPropagator());
    expiry = 0;
    expiryCalculated();
  }

  /**
   * In case current value is an exception, set until information, so this can
   * be used by generated exceptions.
   */
  private void setUntilInExceptionWrapper() {
    if (newValueOrException instanceof ExceptionWrapper) {
      ExceptionWrapper<K, V> ew = (ExceptionWrapper<K, V>) newValueOrException;
      newValueOrException = new ExceptionWrapper<>(ew, Math.abs(expiry));
    }
  }

  public void expiryCalculationException(Throwable t) {
    mutationAbort(new ExpiryPolicyException(t));
  }

  public void cacheDisabledExpireImmediately() {
    expiry = ExpiryTimeValues.NOW;
    expiryCalculated();
  }

  public void expiryCalculated() {
    heapEntry.nextProcessingStep(EXPIRY_COMPLETE);
    if (valueLoaded) {
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
    valueDefinitelyLoaded = valueLoaded = loadAndRestart = false;
    successfulLoad = true;
    heapOrLoadedEntry = new ExaminationEntry<K, V>() {
      @Override
      public K getKey() {
        return heapEntry.getKey();
      }

      @Override
      public Object getValueOrException() {
        return newValueOrException;
      }

      @Override
      public Object getValueOrWrapper() {
        return newValueOrException;
      }

      @Override
      public long getModificationTime() {
        return modificationTime;
      }

      @Override
      public long getExpiryTime() {
        return expiry;
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
      writer().write(key, (V) newValueOrException);
    } catch (Throwable t) {
      onWriteFailure(t);
      return;
    }
    onWriteSuccess();
  }

  public void onWriteSuccess() {
    heapEntry.nextProcessingStep(WRITE_COMPLETE);
    checkKeepOrRemove();
  }

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
    boolean expired = expiry == ExpiryTimeValues.NOW;
    if (!expired || remove) {
      mutationMayStore();
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
    mutationMayStore();
  }

  public void expiredImmediatelyAndRemove() {
    remove = true;
    expiredImmediately = true;
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
    Cache<K, V> userCache = getCache();
    Object oldValueOrException = heapEntry.getValueOrExceptionNoAccess();
    if (expiredImmediately || remove) {
      CacheEntry<K, V> entryCopy = heapCache.returnCacheEntry(getKey(), oldValueOrException);
      if (expiredImmediately) {
        sendExpiryEventsWhenExpiredDuringOperation(entryCopy);
      } else if (remove) {
        if (heapDataValid) {
          if (entryRemovedListeners() != null) {
            for (CacheEntryRemovedListener<K, V> l : entryRemovedListeners()) {
              try {
                l.onEntryRemoved(userCache, entryCopy);
              } catch (Throwable t) {
                exceptionToPropagate = new CacheEventListenerException(t);
              }
            }
          }
        }
      }
    } else {
      CacheEntry<K, V> entryCopy = heapCache.returnCacheEntry(getKey(), newValueOrException);
      if (heapDataValid) {
        if (entryUpdatedListeners() != null) {
          CacheEntry<K, V> previousEntry =
            heapCache.returnCacheEntry(heapEntry.getKey(), oldValueOrException);
          for (CacheEntryUpdatedListener<K, V> l : entryUpdatedListeners()) {
            try {
              l.onEntryUpdated(userCache, previousEntry, entryCopy);
            } catch (Throwable t) {
              exceptionToPropagate = new CacheEventListenerException(t);
            }
          }
        }
      } else {
        if (entryCreatedListeners() != null) {
          for (CacheEntryCreatedListener<K, V> l : entryCreatedListeners()) {
            try {
              l.onEntryCreated(userCache, entryCopy);
            } catch (Throwable t) {
              exceptionToPropagate = new CacheEventListenerException(t);
            }
          }
        }
      }
    }
    mutationReleaseLockAndStartTimer();
  }

  /**
   * User facing cache interface instance. Part of async call context.
   * @see AsyncCacheLoader.Context#getCache()
   */
  @Override
  public Cache<K, V> getCache() {
    return internalCache.getUserCache();
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
    if (heapDataValid) {
      if (entryExpiredListeners() != null) {
        sendExpiryEvents(entryCopy);
      }
    }
  }

  private void sendExpiryEvents(CacheEntry<K, V> entryCopy) {
    Cache<K, V> userCache = getCache();
    for (CacheEntryExpiredListener<K, V> l : entryExpiredListeners()) {
      try {
        l.onEntryExpired(userCache, entryCopy);
      } catch (Throwable t) {
        exceptionToPropagate = new CacheEventListenerException(t);
      }
    }
  }

  /**
   * Mutate the entry and start timer for expiry.
   * Entry mutation and start of expiry has to be done atomically to avoid races.
   */
  public void mutationReleaseLockAndStartTimer() {
    boolean justExpired = false;
    boolean evictionHint = false;
    synchronized (heapEntry) {
      if (heapCache.isRecordModificationTime()) {
        heapEntry.setModificationTime(modificationTime);
      }
      if (remove) {
        if (expiredImmediately) {
          heapEntry.setRawExpiry(Entry.EXPIRED);
          heapEntry.setValueOrWrapper(newValueOrException);
        } else {
          if (!heapEntry.isVirgin()) {
            heapEntry.setRawExpiry(Entry.REMOVE_PENDING);
          }
        }
      } else {
        heapEntry.setValueOrWrapper(
          timing().wrapLoadValueForRefresh(this, heapEntry, newValueOrException));
        evictionHint = heapCache.eviction.updateWeight(heapEntry);
      }
      if (remove) {
        heapCache.removeEntry(heapEntry);
      } else {
        try {
          heapEntry.setRawExpiry(timing().stopStartTimer(heapEntry, expiry, refreshTime));
          boolean entryExpired = heapEntry.isExpiredState();
          if (!expiredImmediately && entryExpired) {
            justExpired = true;
          }
        } catch (RuntimeException ex) {
          exceptionToPropagate = ex;
        }
      }
      if (valueLoaded) {
        if (!remove ||
          !(heapEntry.getValueOrException() == null && heapCache.isRejectNullValues())) {
          operation.loaded(key, this, heapEntry);
        }
      }
      if (!justExpired) {
        heapEntry.processingDone(this);
        entryLocked = false;
      }
    } // synchronized
    if (justExpired) {
      expiredAtEndOfOperationStartOver();
      return;
    }
    if (evictionHint) {
      heapCache.eviction.evictEventually();
    }
    updateMutationStatistics();
    mutationDone();
  }

  /**
   * Entry expired during progress of the action, reenter at a previous processing
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
   * Exception that might have happened during processing. A processing
   * exception takes precedence over a loader exception, because it is probably more severe.
   * Loader exceptions will be delayed until the value is accessed. This exception
   * will be propagated instantly.
   */
  public RuntimeException getExceptionToPropagate() {
    return exceptionToPropagate;
  }

  public Throwable getLoaderException() {
    if (result instanceof ExceptionWrapper) {
      return ((ExceptionWrapper<?, ?>) result).getException();
    }
    return null;
  }

  public Throwable getException() {
    if (exceptionToPropagate != null) {
      return exceptionToPropagate;
    }
    return getLoaderException();
  }

  /**
   * If thread is a synchronous call, wait until operation is complete.
   * There is a little chance that the callback completes before we get
   * here as well as some other operation mutating the entry again.
   *
   * <p>For bulk operation we may use the entry in synchronous mode (without callback)
   * but expect the entry not to stoll here, so we can collect all load requests.
   */
  private void asyncExecutionStartedWaitIfSynchronousCall() {
    if (!bulkMode && syncThread == Thread.currentThread()) {
      synchronized (heapEntry) {
        heapEntry.waitForProcessing();
      }
    }
  }

  public Object getResult() {
    return result;
  }

  /**
   * If false, the result is not set. This deals with the fact if null values are not
   * permitted a loaded null means remove or no mapping.
   */
  public boolean isResultAvailable() {
    return resultAvailable;
  }

  @Override
  public boolean isLoadException() {
    return loadException;
  }

  @Override
  public boolean isExceptionSuppressed() {
    return false;
  }

  @Override
  public long getStopTime() {
    return loadCompletedTime;
  }

  @Override
  public long getCurrentTime() {
    return getStopTime();
  }

  @Override
  public long getExpiryTime() {
    return Math.abs(expiry);
  }

  @Override
  public boolean isAccessed() {
    return isRefreshAhead() && AccessWrapper.wasAccessed(heapEntry.getValueOrWrapper());
  }

  @Override
  public Object getUserData() {
    return null;
  }

  @Override
  public void setUserData(Object data) {
  }

  @Override
  public boolean isLoad() {
    return valueDefinitelyLoaded;
  }

  public static class AbortWhenProcessingException extends CacheException { }

  public interface CompletedCallback<K, V, R> {
    void entryActionCompleted(EntryAction<K, V, R> ea);
  }

}
