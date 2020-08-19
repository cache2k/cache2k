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

import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListener;
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
  CacheEntryListener<K, V> entryListener;

  Listener(CacheEntryListenerConfiguration<K, V> config,
           CacheEntryEventFilter<K, V> filter, CacheEntryListener<K, V> entryListener) {
    this.config = config;
    this.filter = filter;
    this.entryListener = entryListener;
  }

  public abstract EventType getEventType();

  public abstract void fire(EntryEvent<K, V> e);

  static class Created<K, V> extends Listener<K, V> {

    CacheEntryCreatedListener<K, V> listener;

    Created(CacheEntryListenerConfiguration<K, V> config, CacheEntryEventFilter<K, V> filter,
            CacheEntryCreatedListener<K, V> listener) {
      super(config, filter, listener);
      this.listener = listener;
    }

    @Override
    public EventType getEventType() {
      return EventType.CREATED;
    }

    public void fire(EntryEvent<K, V> e) {
      if (filter != null && !filter.evaluate(e)) {
        return;
      }
      listener.onCreated(e);
    }

  }

  static class Updated<K, V> extends Listener<K, V> {

    CacheEntryUpdatedListener<K, V> listener;

    Updated(CacheEntryListenerConfiguration<K, V> config, CacheEntryEventFilter<K, V> filter,
            CacheEntryUpdatedListener<K, V> listener) {
      super(config, filter, listener);
      this.listener = listener;
    }

    @Override
    public EventType getEventType() {
      return EventType.UPDATED;
    }

    public void fire(EntryEvent<K, V> e) {
      if (filter != null && !filter.evaluate(e)) {
        return;
      }
      listener.onUpdated(e);
    }

  }

  static class Removed<K, V> extends Listener<K, V> {

    CacheEntryRemovedListener<K, V> listener;

    Removed(CacheEntryListenerConfiguration<K, V> config, CacheEntryEventFilter<K, V> filter,
            CacheEntryRemovedListener<K, V> listener) {
      super(config, filter, listener);
      this.listener = listener;
    }

    @Override
    public EventType getEventType() {
      return EventType.REMOVED;
    }

    public void fire(EntryEvent<K, V> e) {
      if (filter != null && !filter.evaluate(e)) {
        return;
      }
      listener.onRemoved(e);
    }

  }

  static class Expired<K, V> extends Listener<K, V> {

    CacheEntryExpiredListener<K, V> listener;

    Expired(CacheEntryListenerConfiguration<K, V> config, CacheEntryEventFilter<K, V> filter,
            CacheEntryExpiredListener<K, V> listener) {
      super(config, filter, listener);
      this.listener = listener;
    }

    @Override
    public EventType getEventType() {
      return EventType.EXPIRED;
    }

    public void fire(EntryEvent<K, V> e) {
      if (filter != null && !filter.evaluate(e)) {
        return;
      }
      listener.onExpired(e);
    }

  }

}
