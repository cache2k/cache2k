package org.cache2k.addon;

/*-
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
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Wraps a {@link AsyncBulkCacheLoader} and combines (single) load requests into bulk
 * requests. Usually a bulk load takes place, when a bulk operation, e.g.
 * {@link Cache#getAll} is issued. The cache core is producing no bulk requests take
 * place for refresh, because every expiry is handled individually. This class
 * coalesces requests into larger chunks.
 *
 * <p>Parameters: You may specify how long requests are being delayed and a maximum
 * of loads coalesced into one batch.
 *
 * <p>Coalescing by default only happens for refresh ahead requests. This is controlled
 * via the parameter {@code refreshOnly}. Requests that are not refresh ahead are
 * client issued and executed immediately, together with any pending refresh ahead requests.
 *
 * <p>Usage: Either use the constructor
 * {@link CoalescingBulkLoader#CoalescingBulkLoader(AsyncBulkCacheLoader, long, int, boolean)}
 * and wrap a loader explicitly, or use the declarative configuration with
 * {@link CoalescingBulkLoaderSupport}. If in doubt check the test cases.
 *
 * @author Jens Wilke
 */
public class CoalescingBulkLoader<K, V> implements AsyncBulkCacheLoader<K, V>, AutoCloseable {

  private final long maxDelayMillis;
  private final int maxBatchSize;
  private final boolean refreshOnly;
  private final AsyncBulkCacheLoader<K, V> forwardingLoader;
  private final TimeReference timeReference;
  private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
  private final AtomicLong queueSize = new AtomicLong();
  private final Queue<Request<K, V>> pending = new ConcurrentLinkedQueue<>();

  /**
   * Constructor using the default time reference {@link TimeReference#DEFAULT}
   * @param forwardingLoader requests are forwarded to this loader
   * @param maxDelayMillis see {@link CoalescingBulkLoaderConfig.Builder#maxDelay(long, TimeUnit)}                        
   * @param maxBatchSize see {@link CoalescingBulkLoaderConfig.Builder#maxBatchSize(int)}
   * @param refreshOnly see {@link CoalescingBulkLoaderConfig.Builder#refreshOnly}
   */
  public CoalescingBulkLoader(AsyncBulkCacheLoader<K, V> forwardingLoader, long maxDelayMillis,
                              int maxBatchSize, boolean refreshOnly) {
    this(forwardingLoader, TimeReference.DEFAULT, maxDelayMillis, maxBatchSize, refreshOnly);
  }

  /**
   * Constructor using the specified time reference instance.
   * @param timeReference if the cache is using a different time reference, the instance is
   *                      used to translate to milli seconds via {@link TimeReference#toMillis(long)}
   */
  public CoalescingBulkLoader(AsyncBulkCacheLoader<K, V> forwardingLoader,
                              TimeReference timeReference, long maxDelayMillis, int maxBatchSize,
                              boolean refreshOnly) {
    Objects.requireNonNull(forwardingLoader, "forwardingLoader");
    this.maxDelayMillis = maxDelayMillis;
    this.maxBatchSize = maxBatchSize;
    this.forwardingLoader = forwardingLoader;
    this.timeReference = timeReference;
    this.refreshOnly = refreshOnly;
  }

  @Override
  public void loadAll(Set<K> keys, BulkLoadContext<K, V> context, BulkCallback<K, V> callback) {
    boolean flush = false;
    for (K key : keys) {
      Request<K, V> rq = new Request<>();
      rq.key = key;
      rq.context = context;
      pending.add(rq);
      flush |= !context.isRefreshAhead();
    }
    int sizeToAdd = keys.size();
    long totalSize = queueSize.addAndGet(sizeToAdd);
    if (refreshOnly && flush) {
      flush();
    } else if (totalSize >= maxBatchSize) {
      instantLoadAndScheduleTimer();
    } else if (totalSize == sizeToAdd) {
      startDelay();
    }
  }

  private static class Request<K, V> implements DataAware<K, V> {
    K key;
    BulkLoadContext<K, V> context;
  }

  /**
   * Send requests to the loader
   *
   * @param requestMap concurrent map used to keep track during callbacks
   */
  private void startLoad(ConcurrentMap<K, BulkLoadContext<K, V>> requestMap) {
    BulkLoadContext<K, V> context = createMergedContext(requestMap);
    try {
      forwardingLoader.loadAll(context.getKeys(), context, context.getCallback());
    } catch (Throwable e) {
      context.getCallback().onLoadFailure(e);
    }
  }

  private BulkLoadContext<K, V> createMergedContext(ConcurrentMap<K, BulkLoadContext<K, V>> requestMap) {
    long startTime = Long.MAX_VALUE;
    Set<K> keys = new HashSet<>();
    Map<K, Context<K, V>> contextMap = new HashMap<>();
    BulkLoadContext<K, V> firstContext = null;
    for (Map.Entry<K, BulkLoadContext<K, V>> e : requestMap.entrySet()) {
      if (firstContext == null) {
        firstContext = e.getValue();
      }
      startTime = Math.min(startTime, e.getValue().getStartTime());
      keys.add(e.getKey());
      contextMap.put(e.getKey(), e.getValue().getContextMap().get(e.getKey()));
    }
    long finalStartTime = startTime;
    BulkLoadContext<K, V> finalFirstContext = firstContext;
    BulkCallback<K, V> callback = new BulkCallback<K, V>() {
      @Override
      public void onLoadSuccess(Map<? extends K, ? extends V> data) {
        for (Map.Entry<? extends K, ? extends V> e : data.entrySet()) {
          onLoadSuccess(e.getKey(), e.getValue());
        }
      }
      @Override
      public void onLoadSuccess(K key, V value) {
        BulkLoadContext<K, V> ctx = requestMap.remove(key);
        if (ctx == null) {
          throw new IllegalStateException("unexpected callback for this key");
        }
        ctx.getCallback().onLoadSuccess(key, value);
      }
      @Override
      public void onLoadFailure(Throwable exception) {
        for (Map.Entry<K, BulkLoadContext<K, V>> entry : requestMap.entrySet()) {
          entry.getValue().getCallback().onLoadFailure(entry.getKey(), exception);
        }
        requestMap.clear();
      }
      @Override
      public void onLoadFailure(K key, Throwable exception) {
        BulkLoadContext<K, V> ctx = requestMap.remove(key);
        if (ctx != null) {
          ctx.getCallback().onLoadFailure(key, exception);
        }
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
      /** Always false, since we might have a mixture. */
      @Override public boolean isRefreshAhead() { return false; }
    };
    return context;
  }

  private void startDelay() {
    scheduleTimer(maxDelayMillis);
  }

  private void scheduleTimer(long millis) {
    timer.schedule(this::timerEvent, millis, TimeUnit.MILLISECONDS);
  }

  /**
   * Only execute if still scheduled
   */
  private void timerEvent() {
    try {
      timerEventCheckAndForwardRequests();
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  /**
   * Max batch size reached, forward requests immediately to loader
   * within calling thread. If more requests are pending, schedule the timer.
   *
   * <p>There is no protection against scheduling multiple timer tasks and no
   * timer task is ever cancelled. Instead, with in the timer we check whether
   * there is actually work to do. Scheduling and running the timers has no
   * considerable overhead, since only happening once per maximum bulk request.
   */
  private void instantLoadAndScheduleTimer() {
    do {
      forwardRequests(false, true);
    } while (queueSize.get() >= maxBatchSize);
    Request<K, V> next = pending.peek();
    if (next == null) {
      return;
    }
    long startTime = timeReference.toMillis(next.context.getStartTime());
    long now = timeReference.toMillis(timeReference.millis());
    scheduleTimer(startTime + maxDelayMillis - now);
  }

  /**
   * Send all pending requests to the loader. This is used for testing.
   */
  public void flush() {
    do {
      forwardRequests(false, false);
    } while (queueSize.get() > 0);
  }

  /**
   * Gather requests up to max batch size and forward to the loader
   *
   * @param timerEvent true for timer event, don't do anything if not due
   * @param onlyWhenFull true for queue spill, double check within lock
   * @return true if there might be more to process
   */
  public boolean forwardRequests(boolean timerEvent, boolean onlyWhenFull) {
    long sizeRemaining;
    do {
      ConcurrentMap<K, BulkLoadContext<K, V>> requestMap;
      synchronized (pending) {
        if (onlyWhenFull && queueSize.get() < maxBatchSize) {
          return false;
        }
        if (timerEvent) {
          Request<K, V> next = pending.peek();
          if (next == null) {
            return false;
          }
          if (queueSize.get() < maxBatchSize) {
            long startTime = timeReference.toMillis(next.context.getStartTime());
            long now = timeReference.toMillis(timeReference.millis());
            if (now - startTime < maxDelayMillis) {
              scheduleTimer(startTime + maxDelayMillis - now);
              return false;
            }
          }
        }
        requestMap = new ConcurrentHashMap<>();
        for (int i = 0; i < maxBatchSize; i++) {
          Request<K, V> rq = pending.poll();
          if (rq == null) {
            break;
          }
          requestMap.put(rq.key, rq.context);
        }
        sizeRemaining = queueSize.addAndGet(-requestMap.size());
      }
      if (!requestMap.isEmpty()) {
        startLoad(requestMap);
      }
    } while (sizeRemaining >= maxBatchSize);
    return true;
  }

  /**
   * Checks whether pending requests are due to send, then sends as many requests
   * as possible until maxBatchSize is reached
   */
  private void timerEventCheckAndForwardRequests() {
    while (forwardRequests(true, false)) { }
  }

  public long getQueueSize() {
    return queueSize.get();
  }

  @Override
  public void close() throws Exception {
    queueSize.set(Long.MIN_VALUE);
    timer.shutdown();
    pending.clear();
  }

}
