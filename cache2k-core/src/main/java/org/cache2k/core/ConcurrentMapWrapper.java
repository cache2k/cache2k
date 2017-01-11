package org.cache2k.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2017 headissue GmbH, Munich
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
import org.cache2k.processor.EntryProcessor;
import org.cache2k.processor.MutableCacheEntry;

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

  private boolean permitNull;
  private Cache<K, V> cache;
  private Class<?> keyType;
  private Class<?> valueType;

  public ConcurrentMapWrapper(InternalCache<K, V> cache) {
    this.cache = cache;
    permitNull = cache.isNullValuePermitted();
    keyType = cache.getKeyType().getType();
    valueType = cache.getValueType().getType();
  }

  @Override
  public V putIfAbsent(K key, final V value) {
    EntryProcessor<K, V, V> p = new EntryProcessor<K, V, V>() {
      @Override
      public V process(MutableCacheEntry<K, V> entry) throws Exception {
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
      return cache.removeIfEquals((K) key, (V) value);
    }
    return false;
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    return cache.replaceIfEquals(key, oldValue, newValue);
  }

  @Override
  public V replace(K key, final V value) {
    EntryProcessor<K, V, V> p = new EntryProcessor<K, V, V>() {
      @Override
      public V process(MutableCacheEntry<K, V> entry) throws Exception {
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
    return ((InternalCache) cache).getTotalEntryCount();
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public boolean containsKey(Object key) {
    if (keyType.isAssignableFrom(key.getClass())) {
      return cache.containsKey((K) key);
    }
    return false;
  }

  @Override
  public boolean containsValue(Object value) {
    if (value == null) {
      if (!permitNull) {
        throw new NullPointerException();
      }
      for (CacheEntry<K, V> e : cache.entries()) {
        if (e.getValue() == null) {
          return true;
        }
      }
    } else {
      for (CacheEntry<K, V> e : cache.entries()) {
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
    EntryProcessor<K, V, V> p = new EntryProcessor<K, V, V>() {
      @Override
      public V process(MutableCacheEntry<K, V> entry) throws Exception {
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
    EntryProcessor<K, V, V> p = new EntryProcessor<K, V, V>() {
      @Override
      public V process(MutableCacheEntry<K, V> entry) throws Exception {
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
        final Iterator<CacheEntry<K,V>> it = cache.entries().iterator();
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
        return ConcurrentMapWrapper.this.size();
      }
    };
  }

  @Override
  public Collection<V> values() {
    return new AbstractSet<V>() {
      @Override
      public Iterator<V> iterator() {
        final Iterator<CacheEntry<K,V>> it = cache.entries().iterator();
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
        return ConcurrentMapWrapper.this.size();
      }
    };
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return new AbstractSet<Entry<K, V>>() {
      @Override
      public Iterator<Entry<K, V>> iterator() {
        final Iterator<CacheEntry<K,V>> it = cache.entries().iterator();
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
        return ConcurrentMapWrapper.this.size();
      }
    };
  }

  /** This is the object identity of the cache */
  @Override
  public boolean equals(Object o) {
    if (o instanceof ConcurrentMapWrapper) {
      return cache.equals(((ConcurrentMapWrapper) o).cache);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return cache.hashCode();
  }

}
