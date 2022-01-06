package org.cache2k.core.event;

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

import org.cache2k.core.api.InternalCache;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Dispatch events via the executor. Executes in parallel or serializes them, if
 * for the identical key.
 *
 * @author Jens Wilke
 */
public class AsyncDispatcher<K> {

  private static final int KEY_LOCKS_MASK =
    2 << (31 - Integer.numberOfLeadingZeros(Runtime.getRuntime().availableProcessors())) - 1;
  private static final Object[] KEY_LOCKS;

  static {
    KEY_LOCKS = new Object[KEY_LOCKS_MASK + 1];
    for (int i = 0; i < KEY_LOCKS.length; i++) {
      KEY_LOCKS[i] = new Object();
    }
  }

  /**
   * Simulate locking by key, use the hash code to spread and avoid lock contention.
   * The additional locking we introduce here is currently run synchronously inside the
   * entry mutation operation.
   */
  private static Object getLockObject(Object key) {
    int hc = key.hashCode();
    return KEY_LOCKS[hc & KEY_LOCKS_MASK];
  }

  private final Map<K, Queue<AsyncEvent<K>>> keyQueue =
    new ConcurrentHashMap<K, Queue<AsyncEvent<K>>>();
  private Executor executor;
  private InternalCache cache;

  public AsyncDispatcher(InternalCache cache, final Executor executor) {
    this.cache = cache;
    this.executor = executor;
  }

  /**
   * Immediately executes an event with the provided executor. If an event
   * is already executing for the identical key, queue the event and execute
   * the event with FIFO scheme, preserving the order of the arrival.
   */
  public void queue(final AsyncEvent<K> event) {
    K key = event.getKey();
    synchronized (getLockObject(key)) {
      Queue<AsyncEvent<K>> q = keyQueue.get(key);
      if (q != null) {
        q.add(event);
        return;
      }
      q = new LinkedList<AsyncEvent<K>>();
      keyQueue.put(key, q);
    }
    Runnable r = new Runnable() {
      @Override
      public void run() {
        runMoreOrStop(event);
      }
    };
    executor.execute(r);
  }

  /**
   * Run as long there is still an event for the key.
   */
  public void runMoreOrStop(AsyncEvent<K> event) {
    for (;;) {
      try {
        event.execute();
      } catch (Throwable t) {
        cache.getLog().warn("Async event exception", t);
      }
      K key = event.getKey();
      synchronized (getLockObject(key)) {
        Queue<AsyncEvent<K>> q = keyQueue.get(key);
        if (q.isEmpty()) {
          keyQueue.remove(key);
          return;
        }
        event = q.remove();
      }
    }
  }

}
