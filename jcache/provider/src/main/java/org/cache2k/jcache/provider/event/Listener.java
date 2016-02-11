package org.cache2k.jcache.provider.event;

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
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;
import javax.cache.event.EventType;

/**
 * Holds the configuration, the filter and the JSR107 listener itself.
 *
 * @author Jens Wilke
 */
abstract class Listener<K, V> {

  CacheEntryListenerConfiguration<K, V> config;
  CacheEntryEventFilter<K, V> filter;

  public Listener(final CacheEntryListenerConfiguration<K, V> _config, final CacheEntryEventFilter<K, V> _filter) {
    config = _config;
    filter = _filter;
  }

  public abstract EventType getEventType();

  public abstract void fire(EntryEvent<K, V> e);

  static class Created<K, V> extends Listener<K, V> {

    CacheEntryCreatedListener<K, V> listener;

    public Created(final CacheEntryListenerConfiguration<K, V> _config, final CacheEntryEventFilter<K, V> _filter, final CacheEntryCreatedListener<K, V> _listener) {
      super(_config, _filter);
      listener = _listener;
    }

    @Override
    public EventType getEventType() {
      return EventType.CREATED;
    }

    public void fire(EntryEvent<K,V> e) {
      if (filter != null && !filter.evaluate(e)) {
        return;
      }
      listener.onCreated(e);
    }

  }

  static class Updated<K, V> extends Listener<K, V> {

    CacheEntryUpdatedListener<K, V> listener;

    public Updated(final CacheEntryListenerConfiguration<K, V> _config, final CacheEntryEventFilter<K, V> _filter, final CacheEntryUpdatedListener<K, V> _listener) {
      super(_config, _filter);
      listener = _listener;
    }

    @Override
    public EventType getEventType() {
      return EventType.UPDATED;
    }

    public void fire(EntryEvent<K,V> e) {
      if (filter != null && !filter.evaluate(e)) {
        return;
      }
      listener.onUpdated(e);
    }

  }

  static class Removed<K, V> extends Listener<K, V> {

    CacheEntryRemovedListener<K, V> listener;

    public Removed(final CacheEntryListenerConfiguration<K, V> _config, final CacheEntryEventFilter<K, V> _filter, final CacheEntryRemovedListener<K, V> _listener) {
      super(_config, _filter);
      listener = _listener;
    }

    @Override
    public EventType getEventType() {
      return EventType.REMOVED;
    }

    public void fire(EntryEvent<K,V> e) {
      if (filter != null && !filter.evaluate(e)) {
        return;
      }
      listener.onRemoved(e);
    }

  }

  static class Expired<K, V> extends Listener<K, V> {

    CacheEntryExpiredListener<K, V> listener;

    public Expired(final CacheEntryListenerConfiguration<K, V> _config, final CacheEntryEventFilter<K, V> _filter, final CacheEntryExpiredListener<K, V> _listener) {
      super(_config, _filter);
      listener = _listener;
    }

    @Override
    public EventType getEventType() {
      return EventType.EXPIRED;
    }

    public void fire(EntryEvent<K,V> e) {
      if (filter != null && !filter.evaluate(e)) {
        return;
      }
      listener.onExpired(e);
    }

  }

}
