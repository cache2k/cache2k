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

import org.cache2k.CacheEntry;

import javax.cache.Cache;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.EventType;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Entry events without original value, for created, expired and removed events.
 *
 * @author Jens Wilke
 */
public class EntryEvent<K, V> extends CacheEntryEvent<K, V> implements Iterable<CacheEntryEvent<? extends K, ? extends V>> {

  K key;
  V value;

  public EntryEvent(final Cache source, final EventType eventType, final CacheEntry<K, V> _c2kEntry) {
    super(source, eventType);
    key = _c2kEntry.getKey();
    value = _c2kEntry.getValue();
  }

  public EntryEvent(final Cache source, final EventType eventType, final K _key, final V _value) {
    super(source, eventType);
    key = _key;
    value = _value;
  }

  @Override
  public V getOldValue() {
    return null;
  }

  @Override
  public boolean isOldValueAvailable() {
    return false;
  }

  @Override
  public K getKey() {
    return key;
  }

  @Override
  public V getValue() {
    return value;
  }

  @Override
  public <T> T unwrap(final Class<T> clazz) {
    return null;
  }

  @Override
  public Iterator<CacheEntryEvent<? extends K, ? extends V>> iterator() {
    return new Iterator<CacheEntryEvent<? extends K, ? extends V>>() {
      boolean hasNext = true;

      @Override
      public boolean hasNext() {
        return hasNext;
      }

      @Override
      public CacheEntryEvent<? extends K, ? extends V> next() {
        if (hasNext) {
          hasNext = false;
          return EntryEvent.this;
        }
        throw new NoSuchElementException();
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

}
