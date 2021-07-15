package org.cache2k.addon;

/*
 * #%L
 * cache2k addon
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

import org.cache2k.Cache;
import org.cache2k.DataAware;
import org.cache2k.io.AsyncBulkCacheLoader;
import org.cache2k.operation.TimeReference;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Jens Wilke
 */
public class CoalescingBulkLoader<K, V> implements AsyncBulkCacheLoader<K, V>, AutoCloseable {

  private final long maxDelayMillis;
  private final int maxLoadSize;
  private final AsyncBulkCacheLoader<K, V> forwardingLoader;
  private final TimeReference timeReference;
  private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
  private final AtomicLong queueSize = new AtomicLong();
  private final Queue<Request<K, V>> pending = new ConcurrentLinkedQueue<>();
  private ScheduledFuture<?> schedule = null;

  public CoalescingBulkLoader(
    AsyncBulkCacheLoader<K, V> forwardingLoader, TimeReference timeReference,
    long maxDelayMillis, int maxLoadSize) {
    this.maxDelayMillis = maxDelayMillis;
    this.maxLoadSize = maxLoadSize;
    this.forwardingLoader = forwardingLoader;
    this.timeReference = timeReference;
  }

  @Override
  public void loadAll(Set<K> keys, BulkLoadContext<K, V> context, BulkCallback<K, V> callback) {
    for (K key : keys) {
      Request rq = new Request();
      rq.key = key;
      rq.context = context;
      pending.add(rq);
    }
    int sizeToAdd = keys.size();
    long totalSize = queueSize.addAndGet(sizeToAdd);
    if (totalSize >= maxLoadSize) {
      doLoad();
    } else if (totalSize == sizeToAdd) {
      startDelay();
    }
  }

  private class Request<K, V> implements DataAware<K, V> {
    K key;
    BulkLoadContext<K, V> context;
  }

  private void startLoad(ConcurrentMap<K, BulkLoadContext<K, V>> requestMap) {
    BulkLoadContext<K, V> context = createMergedContext(requestMap);
    try {
      forwardingLoader.loadAll(context.getKeys(), context, context.getCallback());
    } catch (Exception e) {
      context.getCallback().onLoadFailure(e);
    }
  }

  private BulkLoadContext<K, V> createMergedContext(ConcurrentMap<K, BulkLoadContext<K, V>> requestMap) {
    long startTime = Long.MAX_VALUE;
    Set<K> keys = new HashSet<>();
    Map<K, Context<K, V>> contextMap = new HashMap<>();
    BulkLoadContext firstContext = null;
    for (Map.Entry<K, BulkLoadContext<K, V>> e : requestMap.entrySet()) {
      if (firstContext == null) {
        firstContext = e.getValue();
      }
      startTime = Math.min(startTime, e.getValue().getStartTime());
      keys.add(e.getKey());
      contextMap.put(e.getKey(), e.getValue().getContextMap().get(e.getKey()));
    }
    final long finalStartTime = startTime;
    final BulkLoadContext finalFirstContext = firstContext;
    BulkCallback<K, V> callback = new BulkCallback<K, V>() {
      @Override
      public void onLoadSuccess(Map<? extends K, ? extends V> data) {
        for (Map.Entry<? extends K, ? extends V> e : data.entrySet()) {
          onLoadSuccess(e.getKey(), e.getValue());
        }
      }
      @Override
      public void onLoadSuccess(K key, V value) {
        BulkLoadContext<K, V> ctx = requestMap.get(key);
        if (ctx == null) {
          throw new IllegalStateException("unexpected callback for this key");
        }
        requestMap.remove(key);
        ctx.getCallback().onLoadSuccess(key, value);
      }
      @Override
      public void onLoadFailure(Throwable exception) {
        for (BulkLoadContext<K, V> ctx : requestMap.values()) {
          ctx.getCallback().onLoadFailure(exception);
        }
        requestMap.clear();
      }
    };
    BulkLoadContext<K, V> context = new BulkLoadContext<K, V>() {
      @Override public Cache<K, V> getCache() { return finalFirstContext.getCache(); }
      @Override public Map<K, Context<K, V>> getContextMap() { return contextMap; }
      @Override public long getStartTime() { return finalStartTime; }
      @Override public Set<K> getKeys() { return keys; }
      @Override public Executor getExecutor() { return finalFirstContext.getExecutor(); }
      @Override public Executor getLoaderExecutor() { return finalFirstContext.getLoaderExecutor(); }
      @Override public BulkCallback<K, V> getCallback() {
        return callback;
      }
    };
    return context;
  }

  private synchronized void startDelay() {
    if (schedule == null && queueSize.get() > 0) {
      schedule = timer.schedule(this::doLoad, maxDelayMillis, TimeUnit.MILLISECONDS);
    }
  }

  public synchronized void doLoad() {
    if (schedule != null) {
      schedule.cancel(false);
      schedule = null;
    }
    long sizeRemaining;
    do {
      ConcurrentMap<K, BulkLoadContext<K, V>> requestMap = new ConcurrentHashMap<>();
      for (int i = 0; i < maxLoadSize; i++) {
        Request<K, V> rq = pending.poll();
        if (rq == null) {
          break;
        }
        requestMap.put(rq.key, rq.context);
      }
      sizeRemaining = queueSize.addAndGet(-requestMap.size());
      startLoad(requestMap);
    } while(sizeRemaining >= maxLoadSize);
    Request rq = pending.peek();
    if (rq != null) {
      long startTime = timeReference.toMillis(rq.context.getStartTime());
      long now = timeReference.toMillis(timeReference.millis());
      schedule = timer.schedule(this::doLoad, startTime + maxDelayMillis - now, TimeUnit.MILLISECONDS);
    }
  }

  public long getQueueSize() {
    return queueSize.get();
  }

  @Override
  public synchronized void close() throws Exception {
    queueSize.set(Long.MIN_VALUE);
    timer.shutdown();
    pending.clear();
  }

}
