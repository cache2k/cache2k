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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Execute a set of entry actions in parallel to leverage bulk I/O. The basic idea is to
 * collect I/O requests by implementing the interfaces {@code EntryAction} calls and then
 * issue a bulk I/O request on the corresponding bulk interface. This way the actual
 * entry processing code can be used and it does not need to be aware of bulk operations.
 * Its also possible to process an inhomogeneous set of actions and try to do as much bulk I/O
 * as possible.
 *
 * <p>Since multiple entries are locked, we need to do precautions to avoid deadlocks.
 * The strategy is to never hold a lock for one entry and wait for locking another entry.
 *
 * @author Jens Wilke
 */
public abstract class AsyncBulkAction<K, V, R> implements
  AsyncCacheLoader<K, V>,
  AsyncBulkCacheLoader.BulkCallback<K, V>,
  EntryAction.CompletedCallback<K, V, R> {

  private final Map<K, EntryAction<K, V, R>> key2action;
  private final Collection<EntryAction<K, V, R>> toStart;
  private final Set<K> toLoad;
  private final Set<K> completed;
  private final AsyncCacheLoader<K, V> loader;
  private Throwable exceptionToPropagate;
  private int exceptionCount = 0;

  /** Create object and start operation. */
  public AsyncBulkAction(AsyncCacheLoader<K, V> loader, Set<K> keys, boolean sync) {
    key2action = new HashMap<>(keys.size());
    toLoad = new HashSet<>(keys.size());
    completed = new HashSet<>();
    Collection<EntryAction<K, V, R>> actions = toStart = new ArrayList<>(keys.size());
    for (K k : keys) {
      actions.add(createEntryAction(k, this));
    }
    this.loader = loader;
    for (EntryAction<K, V, R> action : actions) {
      K key = action.getKey();
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
    extractException();
    bulkOperationCompleted();
  }

  /**
   * Try to start as much as possible. If nothing can be started at all we
   */
  private void startRemaining() {
    if (!tryStartAll()) {
      startOneWithStalling();
    }
  }

  /**
   * Try to start all actions. An action, e.g. when mutating may require
   * an processing lock. If the lock cannot be acquired straight away,
   * we will reject the start and keep the action in the toStart list.
   *
   * @return true, if some were started
   */
  private boolean tryStartAll() {
    boolean someStarted = false;
    Collection<EntryAction<K, V, R>> rejected = new ArrayList<>();
    Iterator<EntryAction<K, V, R>> it = toStart.iterator();
    while (it.hasNext()) {
      EntryAction<K, V, R> action = it.next();
      action.setBulkMode(true);
      try {
        it.remove();
        action.start();
        someStarted = true;
      } catch (EntryAction.AbortWhenProcessingException ex) {
        rejected.add(action);
      }
    }
    toStart.addAll(rejected);
    if (someStarted) {
      processPendingIo();
    }
    return someStarted;
  }

  /**
   * At least start one action. The operation will wait until the entry can be locked.
   */
  private void startOneWithStalling() {
    for (EntryAction<K, V, R> action : toStart) {
      action.setBulkMode(false);
      action.start();
      toStart.remove(action);
      break;
    }
  }

  /**
   * After we started some or at least one action, check for pending IO requests
   * we collected.
   */
  private void processPendingIo() {
    if (!toLoad.isEmpty()) {
      startBulkLoad();
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

  private void startBulkLoad() {
    AsyncCacheLoader<K, V> loader = this.loader;
    if (loader instanceof AsyncBulkCacheLoader && toLoad.size() > 1) {
      AsyncBulkCacheLoader<K, V> bulkLoader = (AsyncBulkCacheLoader<K, V>) loader;
      Set<Context<K, V>> contextSet = new HashSet<>(toLoad.size());
      for (K key : toLoad) {
        contextSet.add(key2action.get(key));
      }
      try {
        bulkLoader.loadAll(toLoad, contextSet, this);
      } catch (Throwable ouch) {
        onLoadFailure(ouch);
      }
    } else {
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
  }

  @Override
  public synchronized void onLoadSuccess(Map<? extends K, ? extends V> data) {
    for (Map.Entry<? extends K, ? extends V> entry : data.entrySet()) {
      onLoadSuccessInternal(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public synchronized void onLoadSuccess(K key, V value) {
    onLoadSuccessInternal(key, value);
  }

  private void onLoadSuccessInternal(K key, V value) {
    boolean present = toLoad.remove(key);
    if (!present) {
      wrongCallback();
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

  @Override
  public synchronized void entryActionCompleted(EntryAction<K, V, R> ea) {
    completed.add(ea.getKey());
    int startedCount = key2action.size() - toStart.size();
    boolean allCompletedThatWasStarted = completed.size() == startedCount;
    if (allCompletedThatWasStarted) {
      if (!toStart.isEmpty()) {
        startRemaining();
      } else {
        extractException();
        bulkOperationCompleted();
      }
    }
  }

  private void extractException() {
    for (EntryAction<K, V, R> ea : key2action.values()) {
      propagateFirstException(ea);
    }
  }

  private void propagateFirstException(EntryAction<K, V, R> ea) {
    Throwable exception = ea.getException();
    if (exception != null) {
      exceptionCount++;
    }
    if (exceptionToPropagate == null && exception != null) {
      exceptionToPropagate = exception;
    }
  }

  public Throwable getExceptionToPropagate() {
    if (exceptionToPropagate instanceof CacheLoaderException) {
      String txt = "finished with " + exceptionCount + " exceptions " +
        "out of " + key2action.size() + " operations" +
        ", one propagated as cause";
      return new CacheLoaderException(txt, exceptionToPropagate.getCause());
    }
    return exceptionToPropagate;
  }

  public Collection<EntryAction<K, V, R>> getActions() {
    return key2action.values();
  }

  public void fillResultMap(Map<K, R> map) {
    for (Map.Entry<K, EntryAction<K, V, R>> e : key2action.entrySet()) {
      map.put(e.getKey(), e.getValue().result);
    }
  }

  protected void bulkOperationCompleted() { }

  protected abstract EntryAction<K, V, R> createEntryAction(K key, AsyncBulkAction<K, V, R> self);

}
