package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
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
import org.cache2k.CacheEntryProcessingException;
import org.cache2k.CacheEntryProcessor;
import org.cache2k.MutableCacheEntry;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * ConcurrentMap interface wrapper on top of a cache. The map interface does not cause calls to the cache source.
 * An attached writer is called.
 *
 * @author Jens Wilke
 */
public class ConcurrentMapWrapper<K,V> implements ConcurrentMap<K, V> {

  Cache<K, V> cache;
  Class<?> keyType;
  Class<?> valueType;

  public ConcurrentMapWrapper(Cache<K, V> cache) {
    this.cache = cache;
    BaseCache bc = (BaseCache) cache;
    keyType = bc.getKeyType();
    valueType = bc.getValueType();
  }

  @Override
  public V putIfAbsent(K key, final V value) {
    CacheEntryProcessor<K, V, V> p = new CacheEntryProcessor<K, V, V>() {
      @Override
      public V process(MutableCacheEntry<K, V> entry, Object... arguments) throws Exception {
        if (!entry.exists()) {
          entry.setValue(value);
          return null;
        }
        return entry.getValue();
      }
    };
    return cache.invoke(key, p);
  }

  @Override
  public boolean remove(Object key, Object value) {
    if (keyType.isAssignableFrom(key.getClass()) && valueType.isAssignableFrom(value.getClass())) {
      return cache.remove((K) key, (V) value);
    }
    return false;
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    return cache.replace(key, oldValue, newValue);
  }

  @Override
  public V replace(K key, final V value) {
    CacheEntryProcessor<K, V, V> p = new CacheEntryProcessor<K, V, V>() {
      @Override
      public V process(MutableCacheEntry<K, V> entry, Object... arguments) throws Exception {
        if (entry.exists()) {
          V result = entry.getValue();
          entry.setValue(value);
          return result;
        }
        return null;
      }
    };
    return cache.invoke(key, p);
  }

  @Override
  public int size() {
    return cache.getTotalEntryCount();
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public boolean containsKey(Object key) {
    if (keyType.isAssignableFrom(key.getClass())) {
      return cache.contains((K) key);
    }
    return false;
  }

  @Override
  public boolean containsValue(Object value) {
    if (value == null) {
      for (CacheEntry<K, V> e : cache) {
        if (e.getValue() == null) {
          return true;
        }
      }
    } else {
      for (CacheEntry<K, V> e : cache) {
        if (value.equals(e.getValue())) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public V get(Object key) {
    if (keyType.isAssignableFrom(key.getClass())) {
      return cache.peek((K) key);
    }
    return null;
  }

  @Override
  public V put(final K key, final V value) {
    CacheEntryProcessor<K, V, V> p = new CacheEntryProcessor<K, V, V>() {
      @Override
      public V process(MutableCacheEntry<K, V> entry, Object... arguments) throws Exception {
        V result = entry.getValue();
        entry.setValue(value);
        return result;
      }
    };
    return cache.invoke(key, p);
  }

  @Override
  public V remove(Object key) {
    if (!keyType.isAssignableFrom(key.getClass())) {
      return null;
    }
    CacheEntryProcessor<K, V, V> p = new CacheEntryProcessor<K, V, V>() {
      @Override
      public V process(MutableCacheEntry<K, V> entry, Object... arguments) throws Exception {
        V result = entry.getValue();
        entry.remove();
        return result;
      }
    };
    return cache.invoke((K) key, p);
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    cache.putAll(m);
  }

  @Override
  public void clear() {
    cache.clear();
  }

  @Override
  public Set<K> keySet() {
    return new AbstractSet<K>() {
      @Override
      public Iterator<K> iterator() {
        final Iterator<CacheEntry<K,V>> it = cache.iterator();
        return new Iterator<K>() {

          @Override
          public boolean hasNext() {
            return it.hasNext();
          }

          @Override
          public K next() {
            return it.next().getKey();
          }

          @Override
          public void remove() {
            it.remove();
          }
        };
      }

      @Override
      public boolean contains(Object o) {
        return containsKey(o);
      }

      @Override
      public int size() {
        return size();
      }
    };
  }

  @Override
  public Collection<V> values() {
    return new AbstractSet<V>() {
      @Override
      public Iterator<V> iterator() {
        final Iterator<CacheEntry<K,V>> it = cache.iterator();
        return new Iterator<V>() {

          @Override
          public boolean hasNext() {
            return it.hasNext();
          }

          @Override
          public V next() {
            return it.next().getValue();
          }

          @Override
          public void remove() {
            it.remove();
          }
        };
      }

      @Override
      public int size() {
        return size();
      }
    };
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return new AbstractSet<Entry<K, V>>() {
      @Override
      public Iterator<Entry<K, V>> iterator() {
        final Iterator<CacheEntry<K,V>> it = cache.iterator();
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
              public V setValue(V value) {
                throw new UnsupportedOperationException();
              }
            };
          }

          @Override
          public void remove() {
            it.remove();
          }
        };
      }

      @Override
      public int size() {
        return size();
      }
    };
  }

  /** This is the object identity of the cache */
  @Override
  public boolean equals(Object o) {
    return cache.equals(o);
  }

  @Override
  public int hashCode() {
    return cache.hashCode();
  }

}
