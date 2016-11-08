package org.cache2k.jcache.provider.event;

/*
 * #%L
 * cache2k JSR107 support
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

  private K key;
  private V value;

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
