package org.cache2k.jcache.provider.event;

/*-
 * #%L
 * cache2k JCache provider
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

import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.event.EventType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Calls the listeners with help of the executor. The dispatcher is designed to
 * achieve maximum parallelism but deliver events for one key in the correct order.
 *
 * <p>General principle: Before a listener is called a queue is created for
 * the specific key. In the queue all following events will be enqueued in case the
 * listener isn't finished yet.
 *
 * <p>Rationale: We get get called with a new event synchronously and then do the
 * async dispatching. This means we need to be careful not to introduce contention
 * in the synchronous listener execution of the cache. Alternatively we could just
 * have add the event to a queue and then do the more complex dispatching logic in a
 * separate thread, however, that means that we would need an additional thread per cache.
 *
 * @author Jens Wilke
 */
public class AsyncDispatcher<K, V> {

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
  static Object getLockObject(Object key) {
    return KEY_LOCKS[key.hashCode() & KEY_LOCKS_MASK];
  }

  private final Executor executor;

  /**
   * A hash map updated concurrently for events on different keys. For the queue we
   * use a non thread safe linked list, because only one operation happens per key
   * at once.
   */
  private final Map<K, Queue<EntryEvent<K, V>>> keyQueue =
    new ConcurrentHashMap<K, Queue<EntryEvent<K, V>>>();
  private final Map<EventType, List<Listener<K, V>>> asyncListenerByType;

  {
    asyncListenerByType = new HashMap<EventType, List<Listener<K, V>>>();
    for (EventType t : EventType.values()) {
      asyncListenerByType.put(t, new CopyOnWriteArrayList<Listener<K, V>>());
    }
  }

  public AsyncDispatcher(Executor executor) {
    this.executor = executor;
  }

  void addAsyncListener(Listener<K, V> l) {
    asyncListenerByType.get(l.getEventType()).add(l);
  }

  boolean removeAsyncListener(CacheEntryListenerConfiguration<K, V> cfg) {
    boolean found = false;
    for (EventType t : EventType.values()) {
      found |= EventHandlingImpl.removeCfgMatch(cfg, asyncListenerByType.get(t));
    }
    return found;
  }

  void collectListeners(Collection<Listener<K, V>> l) {
    for (EventType t : EventType.values()) {
      l.addAll(asyncListenerByType.get(t));
    }
  }

  /**
   * If listeners are registered for this event type, run the listeners or
   * queue the event, if already something is happening for this key.
   */
  void deliverAsyncEvent(EntryEvent<K, V> event) {
    if (asyncListenerByType.get(event.getEventType()).isEmpty()) {
      return;
    }
    List<Listener<K, V>> listeners =
      new ArrayList<Listener<K, V>>(asyncListenerByType.get(event.getEventType()));
    if (listeners.isEmpty()) {
      return;
    }
    K key = event.getKey();
    synchronized (getLockObject(key)) {
      Queue<EntryEvent<K, V>> q = keyQueue.get(key);
      if (q != null) {
        q.add(event);
        return;
      }
      q = new LinkedList<EntryEvent<K, V>>();
      keyQueue.put(key, q);
    }
    runAllListenersInParallel(event, listeners);
  }

  /**
   * Pass on runnables to the executor for all listeners. After each event is handled
   * within the listener we check whether the event is processed by all listeners, by
   * decrementing a countdown. In case the event is processed completely, we check whether
   * more is queued up for this key meanwhile.
   */
  void runAllListenersInParallel(EntryEvent<K, V> event, List<Listener<K, V>> listeners) {
    AtomicInteger countDown = new AtomicInteger(listeners.size());
    for (Listener<K, V> l : listeners) {
      Runnable r = new Runnable() {
        @Override
        public void run() {
          try {
            l.fire(event);
          } catch (Throwable t) {
            t.printStackTrace();
          }
          int done = countDown.decrementAndGet();
          if (done == 0) {
            runMoreOnKeyQueueOrStop(event.getKey());
          }
        }
      };
      executor.execute(r);
    }
  }

  /**
   * Check the event queue for this key and process the next event. If no more events are
   * present remove the queue.
   */
  void runMoreOnKeyQueueOrStop(K key) {
    EntryEvent<K, V> event;
    synchronized (getLockObject(key)) {
      Queue<EntryEvent<K, V>> q = keyQueue.get(key);
      if (q.isEmpty()) {
        keyQueue.remove(key);
        return;
      }
      event = q.remove();
    }
    List<Listener<K, V>> listeners =
      new ArrayList<Listener<K, V>>(asyncListenerByType.get(event.getEventType()));
    if (listeners.isEmpty()) {
      runMoreOnKeyQueueOrStop(key);
      return;
    }
    runAllListenersInParallel(event, listeners);
  }

}
