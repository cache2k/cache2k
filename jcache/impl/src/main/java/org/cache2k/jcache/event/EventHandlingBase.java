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

import org.cache2k.Cache;
import org.cache2k.CacheBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.jcache.JCacheManagerAdapter;

import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Factory;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListener;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;
import javax.cache.event.EventType;
import javax.xml.bind.Marshaller;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * cache2k does not support changing the listener configuration at runtime. Registers one
 * listener for each event type to cache2k and delivers them to the JCache listeners.
 * Synchronous events are delivered sequentially. Asynchronous events are delivered by an executor
 * and get maximum parallelism.
 *
 * @see AsyncDispatcher
 * @param <K> key type
 * @param <V> value type of user cache
 * @param <W> value type of internal cache wrapper
 *
 * @author Jens Wilke
 */
public abstract class EventHandlingBase<K,V,W> {

  JCacheManagerAdapter manager;
  List<Listener.Created<K,V>> createdListener = new CopyOnWriteArrayList<Listener.Created<K, V>>();
  List<Listener.Updated<K,V>> updatedListener = new CopyOnWriteArrayList<Listener.Updated<K, V>>();
  List<Listener.Removed<K,V>> removedListener = new CopyOnWriteArrayList<Listener.Removed<K, V>>();
  List<Listener.Expired<K,V>> expiredListener = new CopyOnWriteArrayList<Listener.Expired<K, V>>();
  AsyncDispatcher<K,V> asyncDispatcher = new AsyncDispatcher<K, V>();

  public void init(JCacheManagerAdapter m, Executor ex) {
    manager = m;
    asyncDispatcher.executor = ex;
  }

  void addAsyncListener(Listener<K,V> l) {
    asyncDispatcher.addAsyncListener(l);
  }

  static <T extends Listener<K,V>, K, V> boolean removeCfgMatch(
    final CacheEntryListenerConfiguration<K,V> cfg,
    final List<T> _listenerList) {
    Iterator<T> it = _listenerList.iterator();
    while (it.hasNext()) {
      Listener<K,V> l = it.next();
      if (l.config.equals(cfg)) {
        _listenerList.remove(l);
        removeCfgMatch(cfg, _listenerList);
        return true;
      }
    }
    return false;
  }

  public boolean unregisterListener(CacheEntryListenerConfiguration<K,V> cfg) {
    boolean _found = false;
    _found |= removeCfgMatch(cfg, createdListener);
    _found |= removeCfgMatch(cfg, updatedListener);
    _found |= removeCfgMatch(cfg, removedListener);
    _found |= removeCfgMatch(cfg, expiredListener);
    _found |= asyncDispatcher.removeAsyncListener(cfg);
    return _found;
  }

  public Collection<CacheEntryListenerConfiguration<K,V>> getAllListenerConfigurations() {
    Collection<Listener<K,V>> l = new ArrayList<Listener<K,V>>();
    l.addAll(createdListener);
    l.addAll(updatedListener);
    l.addAll(removedListener);
    l.addAll(expiredListener);
    asyncDispatcher.collectListeners(l);
    Set<CacheEntryListenerConfiguration<K,V>> _cfgs = new HashSet<CacheEntryListenerConfiguration<K, V>>();
    for (Listener<K,V> li : l) {
      _cfgs.add(li.config);
    }
    return _cfgs;
  }

  @SuppressWarnings("unchecked")
  public void registerListener(CacheEntryListenerConfiguration<K,V> cfg) {
    synchronized (asyncDispatcher) {
      if (getAllListenerConfigurations().contains(cfg)) {
        throw new IllegalArgumentException("configuration already registered");
      }
    }
    Factory<CacheEntryEventFilter<? super K,? super V>> _filterFactory = cfg.getCacheEntryEventFilterFactory();
    Factory<CacheEntryListener<? super K,? super V>> _listenerFactory = cfg.getCacheEntryListenerFactory();
    if (_listenerFactory == null) {
      throw new IllegalArgumentException("listener factory missing");
    }
    CacheEntryEventFilter<K, V> _filter = null;
    if (_filterFactory != null) {
      _filter = (CacheEntryEventFilter<K, V>) _filterFactory.create();
    }
    Object _listener = _listenerFactory.create();
    boolean _synchronous = cfg.isSynchronous();
    if (_listener instanceof CacheEntryCreatedListener) {
      Listener.Created<K,V> l = new Listener.Created<K, V>(cfg, _filter, (CacheEntryCreatedListener<K,V>) _listener);
      if (_synchronous) {
        createdListener.add(l);
      } else {
        addAsyncListener(l);
      }
    }
    if (_listener instanceof CacheEntryUpdatedListener) {
      Listener.Updated<K,V> l = new Listener.Updated<K, V>(cfg, _filter, (CacheEntryUpdatedListener<K,V>) _listener);
      if (_synchronous) {
        updatedListener.add(l);
      } else {
        addAsyncListener(l);
      }
    }
    if (_listener instanceof CacheEntryRemovedListener) {
      Listener.Removed<K,V> l = new Listener.Removed<K, V>(cfg, _filter, (CacheEntryRemovedListener<K,V>) _listener);
      if (_synchronous) {
        removedListener.add(l);
      } else {
        addAsyncListener(l);
      }
    }
    if (_listener instanceof CacheEntryExpiredListener) {
      Listener.Expired<K,V> l = new Listener.Expired<K, V>(cfg, _filter, (CacheEntryExpiredListener<K,V>) _listener);
      if (_synchronous) {
        expiredListener.add(l);
      } else {
        addAsyncListener(l);
      }
    }
  }

  public void registerCache2kListeners(CacheBuilder<K, W> _builder) {
    _builder.addSynchronousListener(new CreatedListenerAdapter());
    _builder.addSynchronousListener(new UpdatedListenerAdapter());
    _builder.addSynchronousListener(new RemovedListenerAdapter());
    _builder.addSynchronousListener(new ExpiredListenerAdapter());
  }

  protected abstract V extractValue(W _value);

  @SuppressWarnings("unchecked")
  class CreatedListenerAdapter implements org.cache2k.CacheEntryCreatedListener<K, W> {

    @Override
    public void onEntryCreated(
        final org.cache2k.Cache<K, W> c,
        final CacheEntry<K, W> e) {
      if (e.getException() != null) {
        return;
      }
      javax.cache.Cache<K,V> _jCache = manager.resolveCacheWrapper(c);
      EntryEvent<K, V> cee =
        new EntryEvent<K, V>(_jCache, EventType.CREATED, e.getKey(), extractValue(e.getValue()));
      asyncDispatcher.deliverAsyncEvent(cee);
      for (Listener<K,V> t : createdListener) {
        t.fire(cee);
      }
    }

  }

  @SuppressWarnings("unchecked")
  class UpdatedListenerAdapter implements org.cache2k.CacheEntryUpdatedListener<K, W> {

    @Override
    public void onEntryUpdated(final Cache<K, W> c, final W _previousValue, final CacheEntry<K, W> e) {
      if (e.getException() != null) {
        return;
      }
      javax.cache.Cache<K,V> _jCache = manager.resolveCacheWrapper(c);
      EntryEvent<K, V> cee =
        new EntryEventWithOldValue<K, V>(_jCache, EventType.UPDATED, e.getKey(), extractValue(e.getValue()), extractValue(_previousValue));
      asyncDispatcher.deliverAsyncEvent(cee);
      for (Listener<K,V> t : createdListener) {
        t.fire(cee);
      }
    }

  }

  @SuppressWarnings("unchecked")
  class RemovedListenerAdapter implements org.cache2k.CacheEntryRemovedListener<K, W> {

    @Override
    public void onEntryRemoved(
      final org.cache2k.Cache<K, W> c,
      final CacheEntry<K, W> e) {
      javax.cache.Cache<K,V> _jCache = manager.resolveCacheWrapper(c);
      EntryEvent<K, V> cee =
        new EntryEvent<K, V>(_jCache, EventType.REMOVED, e.getKey(), extractValue(e.getValue()));
      asyncDispatcher.deliverAsyncEvent(cee);
      for (Listener<K,V> t : createdListener) {
        t.fire(cee);
      }
    }

  }

  @SuppressWarnings("unchecked")
  class ExpiredListenerAdapter implements org.cache2k.CacheEntryExpiredListener<K, W> {

    @Override
    public void onEntryExpired(
      final org.cache2k.Cache<K, W> c,
      final CacheEntry<K, W> e) {
      javax.cache.Cache<K,V> _jCache = manager.resolveCacheWrapper(c);
      EntryEvent<K, V> cee =
        new EntryEvent<K, V>(_jCache, EventType.EXPIRED, e.getKey(), extractValue(e.getValue()));
      asyncDispatcher.deliverAsyncEvent(cee);
      for (Listener<K,V> t : createdListener) {
        t.fire(cee);
      }
    }

  }

}
