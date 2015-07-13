package org.cache2k.jcache;

/*
 * #%L
 * cache2k JCache JSR107 implementation
 * %%
 * Copyright (C) 2000 - 2015 headissue GmbH, Munich
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
import org.cache2k.CacheEntry;
import org.cache2k.impl.BaseCache;

import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author Jens Wilke; created: 2015-03-28
 */
public class Cache2kCacheAdapter<K, V> implements javax.cache.Cache<K, V> {

  Cache2kManagerAdapter manager;
  Cache<K, V> cache;
  BaseCache<?, K, V> cacheImpl;
  boolean storeByValue;
  boolean readThrough = false;
  boolean statisticsEnabled = false;

  /** Null, if no complete configuration is effective */
  CompleteConfiguration<K, V> completeConfiguration;

  public Cache2kCacheAdapter(Cache2kManagerAdapter _manager, Cache<K, V> _cache, boolean _storeByvalue, CompleteConfiguration<K, V> _completeConfiguration) {
    manager = _manager;
    cache = _cache;
    cacheImpl = (BaseCache<?, K, V>) _cache;
    completeConfiguration = _completeConfiguration;
    storeByValue = _storeByvalue;
  }

  @Override
  public V get(K k) {
    checkClosed();
    if (readThrough) {
      return cache.get(k);
    }
    return cache.peek(k);
  }

  @Override
  public Map<K, V> getAll(Set<? extends K> _keys) {
    checkClosed();
    if (readThrough) {
      return cache.getAll(_keys);
    }
    return cache.peekAll(_keys);
  }

  @Override
  public boolean containsKey(K key) {
    checkClosed();
    return cache.contains(key);
  }

  @Override
  public void loadAll(Set<? extends K> keys, boolean replaceExistingValues, CompletionListener completionListener) {
    checkClosed();
    throw new UnsupportedOperationException("jsr107 loadAll() not supported");
  }

  @Override
  public void put(K k, V v) {
    checkClosed();
    checkNullValue(v);
    cache.put(k, v);
  }

  @Override
  public V getAndPut(K key, V _value) {
    checkClosed();
    checkNullValue(_value);
    return cache.peekAndPut(key, _value);
  }

  void checkNullValue(V _value) {
    if (_value == null) {
      throw new NullPointerException("null value not supported");
    }
  }

  void checkNullKey(K key) {
    if (key == null) {
      throw new NullPointerException("null key not supported");
    }
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> map) {
    checkClosed();
    if (map == null) {
      throw new NullPointerException("null map parameter");
    }
    for (Map.Entry<? extends K, ? extends V> e : map.entrySet()) {
      V v = e.getValue();
      checkNullValue(e.getValue());
      checkNullKey(e.getKey());
    }
    for (Map.Entry<? extends K, ? extends V> e : map.entrySet()) {
      V v = e.getValue();
      cache.put(e.getKey(), e.getValue());
    }
  }

  @Override
  public boolean putIfAbsent(K key, V _value) {
    checkClosed();
    checkNullValue(_value);
    return cache.putIfAbsent(key, _value);
  }

  @Override
  public boolean remove(K key) {
    checkClosed();
    return cacheImpl.removeWithFlag(key);
  }

  @Override
  public boolean remove(K key, V _oldValue) {
    checkClosed();
    checkNullValue(_oldValue);
    return cache.remove(key, _oldValue);
  }

  @Override
  public V getAndRemove(K key) {
    checkClosed();
    return cache.peekAndRemove(key);
  }

  @Override
  public boolean replace(K key, V _oldValue, V _newValue) {
    checkClosed();
    checkNullValue(_oldValue);
    checkNullValue(_newValue);
    return cache.replace(key, _oldValue, _newValue);
  }

  @Override
  public boolean replace(K key, V _value) {
    checkClosed();
    checkNullValue(_value);
    return cache.replace(key, _value);
  }

  @Override
  public V getAndReplace(K key, V _value) {
    checkClosed();
    checkNullValue(_value);
    return cache.peekAndReplace(key, _value);
  }

  @Override
  public void removeAll(Set<? extends K> keys) {
    checkClosed();
    for (K k : keys) {
      checkNullKey(k);
      cache.remove(k);
    }
  }

  @Override
  public void removeAll() {
    checkClosed();
    cache.removeAll();
  }

  @Override
  public void clear() {
    cache.clear();
  }

  @SuppressWarnings("unchecked")
  @Override
  public <C extends Configuration<K, V>> C getConfiguration(Class<C> _class) {
    if (_class.isAssignableFrom(CompleteConfiguration.class)) {
      if (completeConfiguration != null) {
        return (C) completeConfiguration;
      }
      MutableConfiguration<K, V> cfg = new MutableConfiguration<K, V>();
      cfg.setTypes((Class<K>) cacheImpl.getKeyType(), (Class<V>) cacheImpl.getValueType());
      cfg.setStoreByValue(storeByValue);
      return (C) cfg;
    }
    return (C) new Configuration<K, V>() {
      @Override
      public Class<K> getKeyType() {
        return (Class<K>) cacheImpl.getKeyType();
      }

      @Override
      public Class<V> getValueType() {
        return (Class<V>) cacheImpl.getValueType();
      }

      @Override
      public boolean isStoreByValue() {
        return storeByValue;
      }
    };
  }

  @Override
  public <T> T invoke(K key, EntryProcessor<K, V, T> entryProcessor, Object... arguments) throws EntryProcessorException {
    throw new UnsupportedOperationException("jsr107 invoke() not supported");
  }

  @Override
  public <T> Map<K, EntryProcessorResult<T>> invokeAll(Set<? extends K> keys, EntryProcessor<K, V, T> entryProcessor, Object... arguments) {
    throw new UnsupportedOperationException("jsr107 invokeAll() not supported");
  }

  @Override
  public String getName() {
    return cache.getName();
  }

  @Override
  public CacheManager getCacheManager() {
    return manager;
  }

  @Override
  public void close() {
    cache.close();
  }

  @Override
  public boolean isClosed() {
    return cache.isClosed();
  }

  @Override
  public <T> T unwrap(Class<T> clazz) {
    if (Cache.class.equals(clazz)) {
      return (T) cache;
    }
    throw new IllegalArgumentException("requested class unknown");
  }

  @Override
  public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
    throw new UnsupportedOperationException("jsr107 registerCacheEntryListener not supported");
  }

  @Override
  public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
    throw new UnsupportedOperationException("jsr107 deregisterCacheEntryListener not supported");
  }

  @Override
  public Iterator<Entry<K, V>> iterator() {
    checkClosed();
    final Iterator<CacheEntry<K, V>> it = cache.iterator();
    return new Iterator<Entry<K, V>>() {

      @Override
      public boolean hasNext() {
        return it.hasNext();
      }

      @Override
      public Entry<K, V> next() {
        final CacheEntry<K, V> e = it.next();
        return new Entry<K, V>() {
          @Override
          public K getKey() {
            return e.getKey();
          }

          @Override
          public V getValue() {
            return e.getValue();
          }

          @Override
          public <T> T unwrap(Class<T> _class) {
            if (CacheEntry.class.equals(_class)) {
              return (T) e;
            }
            return null;
          }
        };
      }

      @Override
      public void remove() {
        it.remove();
      }
    };
  }

  private void checkClosed() {
    if (cache.isClosed()) {
      throw new IllegalStateException("cache is closed");
    }
  }

}
