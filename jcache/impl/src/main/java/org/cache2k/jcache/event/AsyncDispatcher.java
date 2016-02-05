package org.cache2k.jcache.event;

/*
 * #%L
 * cache2k JCache JSR107 implementation
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
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
public class AsyncDispatcher<K,V> {

  static final int KEY_LOCKS_LEN = Runtime.getRuntime().availableProcessors() * 3;
  static Object[] KEY_LOCKS;

  static {
    KEY_LOCKS = new Object[KEY_LOCKS_LEN];
    for (int i = 0; i < KEY_LOCKS_LEN; i++) {
      KEY_LOCKS[i] = new Object();
    }
  }

  /**
   * Simulate locking by key, use the hash code to spread and avoid lock contention.
   * The additional locking we introduce here is currently run synchronously inside the
   * entry mutation operation.
   */
  static Object getLockObject(Object key) {
    return KEY_LOCKS[key.hashCode() % KEY_LOCKS_LEN];
  }

  Executor executor;

  /**
   * A hash map updated concurrently for events on different keys. For the queue we
   * use a non thread safe linked list, because only one operation happens per key
   * at once.
   */
  Map<K, Queue<EntryEvent<K, V>>> keyQueue = new ConcurrentHashMap<K, Queue<EntryEvent<K, V>>>();
  Map<EventType, List<Listener<K,V>>> asyncListenerByType;

  {
    asyncListenerByType = new HashMap<EventType, List<Listener<K, V>>>();
    for (EventType t : EventType.values()) {
      asyncListenerByType.put(t, new CopyOnWriteArrayList<Listener<K, V>>());
    }
  }

  void addAsyncListener(Listener<K,V> l) {
    asyncListenerByType.get(l.getEventType()).add(l);
  }

  boolean removeAsyncListener(CacheEntryListenerConfiguration<K,V> cfg) {
    boolean _found = false;
    for (EventType t : EventType.values()) {
      _found |= EventHandlingBase.removeCfgMatch(cfg, asyncListenerByType.get(t));
    }
    return _found;
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
  void deliverAsyncEvent(final EntryEvent<K,V> _event) {
    if (asyncListenerByType.get(_event.getEventType()).size() == 0) {
      return;
    }
    List<Listener<K,V>> _listeners =
      new ArrayList<Listener<K, V>>(asyncListenerByType.get(_event.getEventType()));
    if (_listeners.size() == 0) {
      return;
    }
    K key = _event.getKey();
    synchronized (getLockObject(key)) {
      Queue<EntryEvent<K,V>> q = keyQueue.get(key);
      if (q != null) {
        q.add(_event);
        return;
      }
      q = new LinkedList<EntryEvent<K, V>>();
      keyQueue.put(key, q);
    }
    runAllListenersInParallel(_event, _listeners);
  }

  /**
   * Pass on runnables to the executor for all listeners. After each event is handled
   * within the listener we check whether the event is processed by all listeners, by
   * decrementing a countdown. In case the event is processed completely, we check whether
   * more is queued up for this key meanwhile.
   */
  void runAllListenersInParallel(final EntryEvent<K, V> _event, List<Listener<K, V>> _listeners) {
    final AtomicInteger _countDown = new AtomicInteger(_listeners.size());
    for (final Listener<K,V> l : _listeners) {
      Runnable r = new Runnable() {
        @Override
        public void run() {
          try {
            l.fire(_event);
          } catch (Throwable t) {
            t.printStackTrace();
          }
          int _done = _countDown.decrementAndGet();
          if (_done == 0) {
            runMoreOnKeyQueueOrStop(_event.getKey());
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
    EntryEvent<K,V> _event;
    synchronized (getLockObject(key)) {
      Queue<EntryEvent<K,V>> q = keyQueue.get(key);
      if (q.size() == 0) {
        keyQueue.remove(key);
        return;
      }
      _event = q.remove();
    }
    List<Listener<K,V>> _listeners =
      new ArrayList<Listener<K, V>>(asyncListenerByType.get(_event.getEventType()));
    if (_listeners.size() == 0) {
      runMoreOnKeyQueueOrStop(key);
      return;
    }
    runAllListenersInParallel(_event, _listeners);
  }

}
