/**
 *  Copyright 2011-2013 Terracotta, Inc.
 *  Copyright 2011-2013 Oracle, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jsr107.tck.event;

import org.jsr107.tck.support.OperationHandler;
import org.jsr107.tck.support.Server;

import javax.cache.Cache;
import javax.cache.Caching;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListener;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;
import javax.cache.event.EventType;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * A {@link org.jsr107.tck.support.Server} that handles {@link javax.cache.event.CacheEntryListener} requests from a
 * {@link org.jsr107.tck.event.CacheEntryListenerClient} and delegates them to an underlying
 * {@link javax.cache.event.CacheEntryListener}.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 * @author Brian Oliver
 * @author Joe Fialli
 */
public class CacheEntryListenerServer<K, V> extends Server {
  /**
   * The underlying {@link javax.cache.event.CacheEntryListener} that will be used to
   * listen cache entry events delivered by the {@link org.jsr107.tck.event.CacheEntryListenerClient}s.
   */
  private Set<CacheEntryListener<K, V>> listeners;

  /**
   * Constructs an CacheLoaderServer.
   *
   * @param port        the port on which to accept {@link org.jsr107.tck.integration.CacheLoaderClient} request.
   * @param keyClass    the class for entry key
   * @param valueClass  the class for entry value
   */
  public CacheEntryListenerServer(int port, Class keyClass, Class valueClass) {
    super(port);
    this.listeners = new HashSet<CacheEntryListener<K, V>>();

    // establish the client-server operation handlers
    for (EventType eventType : EventType.values()) {
      addOperationHandler(new CacheEntryEventOperationHandler(eventType, keyClass, valueClass));
    }
  }

  /**
   * Set the {@link javax.cache.event.CacheEntryListener} the {@link CacheEntryListenerServer} should use
   * from now on.
   *
   * @param cacheEventListener the {@link javax.cache.event.CacheEntryListener}
   */
  public void addCacheEventListener(CacheEntryListener<K, V> cacheEventListener) {
    if (cacheEventListener == null) {
      throw new NullPointerException();
    }
    this.listeners.add(cacheEventListener);
  }

  public void removeCacheEventListener(CacheEntryListener<K, V> cacheEventListener) {
    if (cacheEventListener != null) {
      listeners.remove(cacheEventListener);
    }
  }

  /**
   * The {@link org.jsr107.tck.support.OperationHandler} for a {@link javax.cache.event.CacheEntryListener} handlers.
   */
  public class CacheEntryEventOperationHandler implements OperationHandler {

    private EventType eventType;
    private Class keyClass;
    private Class valueClass;

    public CacheEntryEventOperationHandler(EventType type, Class keyClass, Class valueClass) {
      this.eventType = type;
      this.keyClass = keyClass;
      this.valueClass = valueClass;
    }

    @Override
    public String getType() {
      return eventType.name();
    }

    @Override
    public void onProcess(ObjectInputStream ois,
                          ObjectOutputStream oos) throws IOException, ClassNotFoundException {

        // load a CacheEntryEvent
        String sourceCacheName = ois.readUTF();
        URI sourceCacheManagerURI = (URI) ois.readObject();
        Cache source = null;
          try {
            source =
              Caching.getCachingProvider().getCacheManager(sourceCacheManagerURI, null).
                getCache(sourceCacheName, keyClass, valueClass);
          } catch (Throwable t) {
            t.printStackTrace();
          }
        try {
          TestCacheEntryEvent event = new TestCacheEntryEvent(source, eventType);
          event.readObject(ois);

          runHandlers(eventType, event);

          // let client know completed synchronous communication
          oos.writeObject(null);
        } catch (Throwable t) {
          oos.writeObject(t);
        }
      }

  }

  private void runHandlers(EventType eventType, TestCacheEntryEvent event) {
    ArrayList events = new ArrayList(1);
    events.add(event);

    for (CacheEntryListener listener : listeners) {
      switch (eventType) {
        case CREATED :
          if (listener instanceof CacheEntryCreatedListener) {
            ((CacheEntryCreatedListener) listener).onCreated(events);
          }
          break;

        case UPDATED:
          if (listener instanceof CacheEntryUpdatedListener) {
            ((CacheEntryUpdatedListener) listener).onUpdated(events);
          }
          break;

        case REMOVED:
          if (listener instanceof CacheEntryRemovedListener) {
            ((CacheEntryRemovedListener) listener).onRemoved(events);
          }
          break;

        case EXPIRED:
          if (listener instanceof CacheEntryExpiredListener) {
            ((CacheEntryExpiredListener) listener).onExpired(events);
          }
          break;

        default:
          break;
      }
    }
  }
}
