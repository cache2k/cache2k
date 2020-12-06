package org.cache2k.jcache.provider.event;

/*
 * #%L
 * cache2k JCache provider
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

import org.cache2k.Cache;
import org.cache2k.CacheEntry;
import org.cache2k.annotation.Nullable;
import org.cache2k.config.Cache2kConfig;
import org.cache2k.config.CustomizationSupplier;
import org.cache2k.config.CustomizationReferenceSupplier;
import org.cache2k.event.CacheClosedListener;
import org.cache2k.event.CacheEntryOperationListener;
import org.cache2k.jcache.provider.JCacheManagerAdapter;

import javax.cache.CacheException;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Factory;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListener;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;
import javax.cache.event.EventType;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * cache2k does not support changing the listener configuration at runtime. Registers one
 * listener for each event type to cache2k and delivers them to the JCache listeners.
 * Synchronous events are delivered sequentially. Asynchronous events are delivered by an executor
 * and get maximum parallelism.
 *
 * @see AsyncDispatcher
 * @param <K> key type
 * @param <V> value type
 *
 * @author Jens Wilke
 */
public class EventHandlingImpl<K, V> implements EventHandling<K, V>, CacheClosedListener {

  private javax.cache.Cache resolvedJCache;
  private final List<Listener.Created<K, V>> createdListener =
    new CopyOnWriteArrayList<Listener.Created<K, V>>();
  private final List<Listener.Updated<K, V>> updatedListener =
    new CopyOnWriteArrayList<Listener.Updated<K, V>>();
  private final List<Listener.Removed<K, V>> removedListener =
    new CopyOnWriteArrayList<Listener.Removed<K, V>>();
  private final List<Listener.Expired<K, V>> expiredListener =
    new CopyOnWriteArrayList<Listener.Expired<K, V>>();
  private final AsyncDispatcher<K, V> asyncDispatcher;
  private final JCacheManagerAdapter manager;

  public EventHandlingImpl(JCacheManagerAdapter m, Executor ex) {
    asyncDispatcher = new AsyncDispatcher<K, V>(ex);
    manager = m;
  }

  void addAsyncListener(Listener<K, V> l) {
    asyncDispatcher.addAsyncListener(l);
  }

  static <T extends Listener<K, V>, K, V> boolean removeCfgMatch(
    CacheEntryListenerConfiguration<K, V> cfg,
    List<T> listenerList) {
    for (T l : listenerList) {
      if (l.config.equals(cfg)) {
        listenerList.remove(l);
        removeCfgMatch(cfg, listenerList);
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean deregisterListener(CacheEntryListenerConfiguration<K, V> cfg) {
    return
      removeCfgMatch(cfg, createdListener)
      | removeCfgMatch(cfg, updatedListener)
      | removeCfgMatch(cfg, removedListener)
      | removeCfgMatch(cfg, expiredListener)
      | asyncDispatcher.removeAsyncListener(cfg);
  }

  @Override
  public Collection<CacheEntryListenerConfiguration<K, V>> getAllListenerConfigurations() {
    Collection<Listener<K, V>> l = getAllListeners();
    Set<CacheEntryListenerConfiguration<K, V>> cfgs =
      new HashSet<CacheEntryListenerConfiguration<K, V>>();
    for (Listener<K, V> li : l) {
      cfgs.add(li.config);
    }
    return cfgs;
  }

  private Collection<Listener<K, V>> getAllListeners() {
    Collection<Listener<K, V>> l = new ArrayList<Listener<K, V>>();
    l.addAll(createdListener);
    l.addAll(updatedListener);
    l.addAll(removedListener);
    l.addAll(expiredListener);
    asyncDispatcher.collectListeners(l);
    return l;
  }

  /**
   * Copy to a set first, because some listener instances may implement multiple interfaces
   * and show up in the all listeners list more then once
   *
   * @param cache The cache that is closing. No cache operations on entries are allowed.
   * @return
   */
  @Override
  public @Nullable CompletableFuture<Void> onCacheClosed(Cache cache) {
    Set<CacheEntryListener> ls = new HashSet<CacheEntryListener>();
    for (Listener l : getAllListeners()) {
      ls.add(l.entryListener);
    }
    for (CacheEntryListener cl : ls) {
      if (cl instanceof Closeable) {
        try {
          ((Closeable) cl).close();
        } catch (Exception e) {
          throw new CacheException("closing listener", e);
        }
      }
    }
    return null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void registerListener(CacheEntryListenerConfiguration<K, V> cfg) {
    synchronized (asyncDispatcher) {
      if (getAllListenerConfigurations().contains(cfg)) {
        throw new IllegalArgumentException("configuration already registered");
      }
    }
    Factory<CacheEntryEventFilter<? super K, ? super V>> filterFactory =
      cfg.getCacheEntryEventFilterFactory();
    Factory<CacheEntryListener<? super K, ? super V>> listenerFactory =
      cfg.getCacheEntryListenerFactory();
    if (listenerFactory == null) {
      throw new IllegalArgumentException("listener factory missing");
    }
    CacheEntryEventFilter<K, V> filter = null;
    if (filterFactory != null) {
      filter = (CacheEntryEventFilter<K, V>) filterFactory.create();
    }
    Object listener = listenerFactory.create();
    boolean synchronous = cfg.isSynchronous();
    if (listener instanceof CacheEntryCreatedListener) {
      Listener.Created<K, V> l =
        new Listener.Created<K, V>(cfg, filter, (CacheEntryCreatedListener<K, V>) listener);
      if (synchronous) {
        createdListener.add(l);
      } else {
        addAsyncListener(l);
      }
    }
    if (listener instanceof CacheEntryUpdatedListener) {
      Listener.Updated<K, V> l =
        new Listener.Updated<K, V>(cfg, filter, (CacheEntryUpdatedListener<K, V>) listener);
      if (synchronous) {
        updatedListener.add(l);
      } else {
        addAsyncListener(l);
      }
    }
    if (listener instanceof CacheEntryRemovedListener) {
      Listener.Removed<K, V> l =
        new Listener.Removed<K, V>(cfg, filter, (CacheEntryRemovedListener<K, V>) listener);
      if (synchronous) {
        removedListener.add(l);
      } else {
        addAsyncListener(l);
      }
    }
    if (listener instanceof CacheEntryExpiredListener) {
      Listener.Expired<K, V> l =
        new Listener.Expired<K, V>(cfg, filter, (CacheEntryExpiredListener<K, V>) listener);
      if (synchronous) {
        expiredListener.add(l);
      } else {
        addAsyncListener(l);
      }
    }
  }

  @Override
  public void addInternalListenersToCache2kConfiguration(Cache2kConfig<K, V> cfg) {
    Collection<CustomizationSupplier<CacheEntryOperationListener<K, V>>> listeners =
      cfg.getListeners();
    listeners.add(new CustomizationReferenceSupplier<CacheEntryOperationListener<K, V>>(
      new CreatedListenerAdapter()));
    listeners.add(new CustomizationReferenceSupplier<CacheEntryOperationListener<K, V>>(
      new UpdatedListenerAdapter()));
    listeners.add(new CustomizationReferenceSupplier<CacheEntryOperationListener<K, V>>(
      new RemovedListenerAdapter()));
    listeners.add(new CustomizationReferenceSupplier<CacheEntryOperationListener<K, V>>(
      new ExpiredListenerAdapter()));
  }

  private V extractValue(V value) { return value; }

  @SuppressWarnings("unchecked")
  class CreatedListenerAdapter implements org.cache2k.event.CacheEntryCreatedListener<K, V> {

    @Override
    public void onEntryCreated(Cache<K, V> c, CacheEntry<K, V> e) {
      if (e.getException() != null) {
        return;
      }
      javax.cache.Cache<K, V> jCache = getCache(c);
      fireCreated(jCache, e);
    }

  }

  private void fireCreated(javax.cache.Cache<K, V> jCache, CacheEntry<K, V> e) {
    EntryEvent<K, V> cee =
      new EntryEvent<K, V>(jCache, EventType.CREATED, e.getKey(), extractValue(e.getValue()));
    asyncDispatcher.deliverAsyncEvent(cee);
    for (Listener<K, V> t : createdListener) {
      t.fire(cee);
    }
  }

  @SuppressWarnings("unchecked")
  class UpdatedListenerAdapter implements org.cache2k.event.CacheEntryUpdatedListener<K, V> {

    @Override
    public void onEntryUpdated(Cache<K, V> c, CacheEntry<K, V> currentEntry,
                               CacheEntry<K, V> newEntry) {
      javax.cache.Cache<K, V> jCache = getCache(c);
      if (newEntry.getException() != null) {
        if (currentEntry.getException() != null) {
          return;
        }
        EntryEvent<K, V> cee =
          new EntryEvent<K, V>(jCache, EventType.REMOVED, newEntry.getKey(),
            extractValue(currentEntry.getValue()));
        asyncDispatcher.deliverAsyncEvent(cee);
        for (Listener<K, V> t : removedListener) {
          t.fire(cee);
        }
        return;
      }
      if (currentEntry.getException() != null) {
        fireCreated(jCache, newEntry);
        return;
      }
      V v0 = currentEntry.getValue();
      V v1 = newEntry.getValue();
      EntryEvent<K, V> cee =
        new EntryEventWithOldValue<K, V>(jCache, EventType.UPDATED, newEntry.getKey(),
          extractValue(v1), extractValue(v0));
      asyncDispatcher.deliverAsyncEvent(cee);
      for (Listener<K, V> t : updatedListener) {
        t.fire(cee);
      }
    }

  }

  private javax.cache.Cache getCache(Cache<K, V> c) {
    if (resolvedJCache != null) {
      return resolvedJCache;
    }
    return resolvedJCache = manager.resolveCacheWrapper(c);
  }

  @SuppressWarnings("unchecked")
  class RemovedListenerAdapter implements org.cache2k.event.CacheEntryRemovedListener<K, V> {

    @Override
    public void onEntryRemoved(Cache<K, V> c, CacheEntry<K, V> e) {
      if (e.getException() != null) {
        return;
      }
      javax.cache.Cache<K, V> jCache = getCache(c);
      V val = extractValue(e.getValue());
      EntryEvent<K, V> cee =
        new EntryEventWithOldValue<K, V>(jCache, EventType.REMOVED, e.getKey(), val, val);
      asyncDispatcher.deliverAsyncEvent(cee);
      for (Listener<K, V> t : removedListener) {
        t.fire(cee);
      }
    }

  }

  @SuppressWarnings("unchecked")
  class ExpiredListenerAdapter
    implements org.cache2k.event.CacheEntryExpiredListener<K, V> {

    @Override
    public void onEntryExpired(Cache<K, V> c, CacheEntry<K, V> e) {
      if (e.getException() != null) {
        return;
      }
      javax.cache.Cache<K, V> jCache = getCache(c);
      EntryEvent<K, V> cee =
        new EntryEvent<K, V>(jCache, EventType.EXPIRED, e.getKey(), extractValue(e.getValue()));
      asyncDispatcher.deliverAsyncEvent(cee);
      for (Listener<K, V> t : expiredListener) {
        t.fire(cee);
      }
    }

  }

}
