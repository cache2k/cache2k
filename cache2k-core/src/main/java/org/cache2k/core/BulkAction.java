package org.cache2k.core;

/*
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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

import org.cache2k.io.AsyncBulkCacheLoader;
import org.cache2k.io.AsyncCacheLoader;
import org.cache2k.io.CacheLoaderException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Execute a set of entry actions in parallel to leverage bulk I/O. The basic idea is to
 * collect I/O requests by implementing the callback interfaces and then
 * issue a bulk I/O request on the corresponding bulk interface. This way the
 * entry processing code can be used and it does not need to be aware of bulk operations.
 * Theoretically, its also possible to process an inhomogeneous set of actions and try to
 * do as much bulk I/O as possible.
 *
 * <p>Since multiple entries are locked, we need to do precautions to avoid deadlocks.
 * The strategy is to never hold a lock for one entry and wait for locking another entry.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("Convert2Diamond")
public abstract class BulkAction<K, V, R> implements
  AsyncCacheLoader<K, V>,
  AsyncBulkCacheLoader.BulkCallback<K, V>,
  EntryAction.CompletedCallback<K, V, R> {

  /** Used for executors **/
  private final HeapCache<K, V> heapCache;
  /**
   * Map with the individual entry actions, the map will not be modified after the
   * bulk operation start, so its save to read from it from different threads.
   */
  private final Map<K, EntryAction<K, V, R>> key2action;
  private final Set<K> toLoad;
  private final AsyncCacheLoader<K, V> loader;
  private Collection<EntryAction<K, V, R>> toStart;
  private int completedCount = 0;
  /** For debugging to check complete is called once. */
  private boolean completedCalled = false;

  /** Create object and start operation. */
  public BulkAction(HeapCache<K, V> heapCache, AsyncCacheLoader<K, V> loader, Set<K> keys, boolean sync) {
    this.heapCache = heapCache;
    this.loader = loader;
    key2action = new HashMap<>(keys.size());
    toLoad = new HashSet<>(keys.size());
    toStart = new ArrayList<>(keys.size());
    for (K key : keys) {
      EntryAction<K, V, R> action = createEntryAction(key, this);
      toStart.add(action);
      key2action.put(key, action);
    }
    synchronized (this) {
      startRemaining();
      if (sync) {
        loopIfSyncAndComplete();
      }
    }
  }

  /**
   * In sync mode we don't get a callback in a separate thread.
   */
  private void loopIfSyncAndComplete() {
    while (!toStart.isEmpty()) {
      startRemaining();
    }
    bulkOperationCompleted();
  }

  /**
   * Try to start as much entry actions in parallel as possible.
   * A start may fail, if there is currently processing going on for an
   * entry. If nothing can be started we queue in our action in only one entry.
   * Waiting for more than one entry might cause a deadlock.
   */
  private void startRemaining() {
    while (!tryStartAllAndProcessPendingIo()) {
      if (startSingleActionWithDelay()) {
        return;
      }
    }
  }

  /**
   * Try to start all actions. An action may require a processing lock.
   * When in bulk mode, if the lock cannot be acquired straight away, the start is rejected.
   * In this case we keep the action in the toStart list.
   *
   * @return true, if callback is expected and actions are running or if completed
   */
  private boolean tryStartAllAndProcessPendingIo() {
    boolean someStarted = false;
    Iterator<EntryAction<K, V, R>> it = toStart.iterator();
    List<EntryAction<K, V, R>> rejected = new ArrayList<>(toStart.size());
    int alreadyCompleted = completedCount;
    while (it.hasNext()) {
      EntryAction<K, V, R> action = it.next();
      action.setBulkMode(true);
      try {
        action.start();
        someStarted = true;
      } catch (EntryAction.AbortWhenProcessingException e) {
        rejected.add(action);
      }
    }
    if (someStarted) {
      processPendingIo();
    }
    boolean allCompletedInSameThread =
      (completedCount - alreadyCompleted) == (toStart.size() - rejected.size());
    toStart = rejected;
    if (completedCount == key2action.size()) {
      triggerComplete();
      return true;
    }
    boolean callbackPending = someStarted && !allCompletedInSameThread;
    return callbackPending;
  }

  /**
   * All actions were tried but no action could be started without delay.
   * Start a single action. The processing will proceed as soon as the
   * ongoing operation finishes.
   *
   * @return true if callback is pending or completed
   */
  private boolean startSingleActionWithDelay() {
    int alreadyCompleted = completedCount;
    for (EntryAction<K, V, R> action : toStart) {
      action.setBulkMode(false);
      toStart.remove(action);
      action.start();
      break;
    }
    if (completedCount == key2action.size()) {
      triggerComplete();
      return true;
    }
    boolean callbackPending =
      alreadyCompleted == completedCount;
    return callbackPending;
  }

  /**
   * After we started at least one action, check for pending IO requests
   * we collected. This currently only looks for collected load requests, but
   * can/will be extended to other IO operation like tiered read/write and
   * bulk writer.
   */
  private void processPendingIo() {
    if (!toLoad.isEmpty()) {
      startLoading();
    }
  }

  /**
   * Load request for single value coming for entry action. If we execute in bulk
   * we just collect. If not in bulk mode we execute straight away.
   */
  @SuppressWarnings({"rawtypes", "ConstantConditions"})
  @Override
  public void load(K key, Context<K, V> context, Callback<V> callback) throws Exception {
    if (((EntryAction) context).isBulkMode()) {
      toLoad.add(key);
    } else {
      loader.load(key, context, callback);
    }
  }

  private void checkPresent(Object action) {
    if (action == null) {
      wrongCallback();
    }
  }

  private void wrongCallback() {
    throw new IllegalArgumentException("Callback key not part of request or already processed");
  }

  /**
   * Start loading via the available async loader. User bulk if available.
   */
  private void startLoading() {
    if (loader instanceof AsyncBulkCacheLoader && toLoad.size() > 1) {
      startLoadingBulk();
    } else {
      startLoadingSingle();
    }
  }

  /**
   * Call async loader for all load requests we collected.
   */
  private void startLoadingSingle() {
    Iterator<K> it = toLoad.iterator();
    while (it.hasNext()) {
      K key = it.next();
      it.remove();
      EntryAction<K, V, R> action = key2action.get(key);
      try {
        loader.load(key, action, action);
      } catch (Throwable ouch) {
        action.onLoadFailure(ouch);
      }
    }
  }

  /**
   * Start loading via calling the async bulk loader. The keys are removed from
   * toLoad in the callback to ourselves since we use toLoad additionally to keep track
   * of partial completions. We need to copy the keys, since we modify the toLoad set
   * if we get partial completions.
   */
  private void startLoadingBulk() {
    AsyncBulkCacheLoader<K, V> bulkLoader = (AsyncBulkCacheLoader<K, V>) loader;
    Set<K> keysCopy = Collections.unmodifiableSet(new HashSet<K>(toLoad));
    try {
      bulkLoader.loadAll(keysCopy, new MyBulkLoadContext(keysCopy), this);
    } catch (Throwable ouch) {
      onLoadFailure(ouch);
    }
  }

  @Override
  public void onLoadSuccess(Map<? extends K, ? extends V> data) {
    for (Map.Entry<? extends K, ? extends V> entry : data.entrySet()) {
      onLoadSuccessInternal(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public void onLoadSuccess(K key, V value) {
    onLoadSuccessInternal(key, value);
  }

  private void onLoadSuccessInternal(K key, V value) {
    synchronized (this) {
      boolean present = toLoad.remove(key);
      if (!present) {
        wrongCallback();
      }
    }
    EntryAction<K, V, R> action = key2action.get(key);
    checkPresent(action);
    action.onLoadSuccess(value);
  }

  /**
   * Fail all pending load requests
   */
  @Override
  public synchronized void onLoadFailure(Throwable exception) {
    for (K key : toLoad) {
      EntryAction<K, V, R> action = key2action.get(key);
      action.onLoadFailure(exception);
    }
    toLoad.clear();
  }

  /**
   * Callback upon completion of an entry action. Either start more actions
   * or complete processing.
   */
  @Override
  public void entryActionCompleted(EntryAction<K, V, R> ea) {
    boolean sameThread = Thread.holdsLock(this);
    synchronized (this) {
      completedCount++;
      if (sameThread) { return; }
      int startedCount = key2action.size() - toStart.size();
      boolean allCompletedThatWasStarted = completedCount == startedCount;
      if (allCompletedThatWasStarted) {
        if (!toStart.isEmpty()) {
          startRemaining();
        } else {
          triggerComplete();
        }
      }
    }
  }

  private void triggerComplete() {
    completedCalled = true;
    bulkOperationCompleted();
  }

  public Throwable getExceptionToPropagate() {
    Throwable exceptionToPropagate = null;
    int exceptionCount = 0;
    for (EntryAction<K, V, R> ea : key2action.values()) {
      Throwable exception = ea.getExceptionToPropagate();
      if (exception != null) {
        exceptionToPropagate = exception;
        exceptionCount++;
      }
    }
    if (exceptionCount > 1) {
      return new BulkOperationException(exceptionCount, key2action.size(), exceptionToPropagate);
    }
    return exceptionToPropagate;
  }

  public CacheLoaderException getLoaderException() {
    Throwable exceptionToPropagate = null;
    int exceptionCount = 0;
    for (EntryAction<K, V, R> ea : key2action.values()) {
      Throwable exception = ea.getLoaderException();
      if (exception != null) {
        exceptionToPropagate = exception;
        exceptionCount++;
      }
    }
    if (exceptionCount == 0) {
      return null;
    }
    return BulkResultCollector.createBulkLoaderException(
      exceptionCount, key2action.size(), exceptionToPropagate);
  }

  public Collection<EntryAction<K, V, R>> getActions() {
    return key2action.values();
  }

  protected void bulkOperationCompleted() { }

  protected abstract EntryAction<K, V, R> createEntryAction(K key, BulkAction<K, V, R> self);

  private class MyBulkLoadContext implements AsyncBulkCacheLoader.BulkLoadContext<K, V> {

    private final Set<K> keys;
    private Set<Context<K, V>> contextSet;

    MyBulkLoadContext(Set<K> keys) {
      this.keys = keys;
    }

    /** Lazily create set with entry contexts */
    @Override
    public Set<Context<K, V>> getContextSet() {
      if (contextSet == null) {
        contextSet = new HashSet<>(toLoad.size());
        for (K key : keys) {
          contextSet.add(key2action.get(key));
        }
      }
      return contextSet;
    }

    @Override
    public long getStartTime() {
      K firstKey = keys.iterator().next();
      return key2action.get(firstKey).getStartTime();
    }

    @Override
    public Set<K> getKeys() {
      return keys;
    }

    @Override
    public Executor getExecutor() {
      return heapCache.getExecutor();
    }

    @Override
    public Executor getLoaderExecutor() {
      return heapCache.getLoaderExecutor();
    }

  }

}
