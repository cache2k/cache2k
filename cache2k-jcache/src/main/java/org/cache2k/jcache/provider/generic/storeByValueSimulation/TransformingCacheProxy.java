package org.cache2k.jcache.provider.generic.storeByValueSimulation;

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

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;
import javax.cache.processor.MutableEntry;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * This is a proxy that filters all keys and values that go in and out.
 * The basic use case is to mimic store by value semantics on a
 * heap based cache. This is done by cloning or otherwise copying all instances that go
 * in and out from a cache.
 *
 * <p>This generic approach also may convert the types. Right now we just need the
 * identical types, so this might be a little over engineered.</p>
 *
 * <p>Complete except listeners and configuration.</p>
 *
 * @author Jens Wilke
 */
public class TransformingCacheProxy<K, V, K0, V0> implements Cache<K, V> {

  protected ObjectTransformer<K, K0> keyTransformer;
  protected ObjectTransformer<V, V0> valueTransformer;
  protected ObjectTransformer<K, K0> passingKeyTransformer;
  protected ObjectTransformer<V, V0> passingValueTransformer;
  protected Cache<K0, V0> cache;

  /**
   *
   * @param cache the wrapped cache
   * @param keyTransformer Keys that go in and out will be sent through
   * @param valueTransformer Values that go in and out will be sent through
   * @param passingKeyTransformer Special transformer for keys that go in and are not stored by the cache (e.g. for #conatainsKey)
   * @param passingValueTransformer Special transformer for keys that go in and are not stored by the cache (e.g. for the oldValue in replace)
   */
  public TransformingCacheProxy(
      Cache<K0, V0> cache,
      ObjectTransformer<K, K0> keyTransformer,
      ObjectTransformer<V, V0> valueTransformer,
      ObjectTransformer<K, K0> passingKeyTransformer,
      ObjectTransformer<V, V0> passingValueTransformer) {
    this.cache = cache;
    this.keyTransformer = keyTransformer;
    this.passingKeyTransformer = passingKeyTransformer;
    this.passingValueTransformer = passingValueTransformer;
    this.valueTransformer = valueTransformer;
  }

  @Override
  public V get(K key) {
    return valueTransformer.expand(cache.get(keyTransformer.compact(key)));
  }

  @Override
  public Map<K, V> getAll(Set<? extends K> keys) {
    Map<K0, V0> m = cache.getAll(compactBoundedKeys(keys));
    return expandMap(m);
  }

  static <E, I> Set<I> compactSet(final Set<E> keys, final ObjectTransformer<E, I> tr) {
    if (keys == null) {
      return null;
    }
    final int _size = keys.size();
    return new AbstractSet<I>() {
      @Override
      public Iterator<I> iterator() {
        final Iterator<E> it = keys.iterator();
        return new Iterator<I>() {
          @Override
          public boolean hasNext() {
            return it.hasNext();
          }

          @Override
          public I next() {
            return tr.compact(it.next());
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }

      @Override
      public int size() {
        return _size;
      }
    };
  }

  static <E, I> Set<I> compactBoundedSet(final Set<? extends E> keys, final ObjectTransformer<E, I> tr) {
    if (keys == null) {
      return null;
    }
    final int _size = keys.size();
    return new AbstractSet<I>() {
      @Override
      public Iterator<I> iterator() {
        final Iterator<? extends E> it = keys.iterator();
        return new Iterator<I>() {
          @Override
          public boolean hasNext() {
            return it.hasNext();
          }

          @Override
          public I next() {
            return tr.compact(it.next());
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }

      @Override
      public int size() {
        return _size;
      }
    };
  }

  static <E, I> Set<E> expandSet(final Set<I> keys, final ObjectTransformer<E, I> tr) {
    final int _size = keys.size();
    return new AbstractSet<E>() {
      @Override
      public Iterator<E> iterator() {
        final Iterator<I> it = keys.iterator();
        return new Iterator<E>() {
          @Override
          public boolean hasNext() {
            return it.hasNext();
          }

          @Override
          public E next() {
            return tr.expand(it.next());
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }

      @Override
      public int size() {
        return _size;
      }
    };
  }

  Set<K> expandKeys(Set<K0> keys) {
    return expandSet(keys, keyTransformer);
  }

  Set<K0> compactKeys(Set<K> keys) {
    return compactSet(keys, keyTransformer);
  }

  Set<K0> compactBoundedKeys(Set<? extends K> keys) {
    return compactBoundedSet(keys, keyTransformer);
  }

  Set<V> expandValues(Set<V0> values) {
    return expandSet(values, valueTransformer);
  }

  Set<V0> compactValues(Set<V> values) {
    return compactSet(values, valueTransformer);
  }

  Map<K0, V0> compactMap(final Map<? extends K, ? extends V> map) {
    if (map == null) {
      return null;
    }
    Map<K0, V0> m2 = new HashMap<K0, V0>();
    for (Map.Entry<? extends K, ? extends V> e : map.entrySet()) {
      m2.put(keyTransformer.compact(e.getKey()), valueTransformer.compact(e.getValue()));
    }
    return m2;
  }

  Map<K, V> expandMap(final Map<K0, V0> map) {
    Map<K, V> m2 = new HashMap<K, V>();
    for (Map.Entry<K0, V0> e : map.entrySet()) {
      m2.put(keyTransformer.expand(e.getKey()), valueTransformer.expand(e.getValue()));
    }
    return m2;
  }

  @Override
  public boolean containsKey(K key) {
    return cache.containsKey(keyTransformer.compact(key));
  }

  @Override
  public void loadAll(Set<? extends K> keys, boolean replaceExistingValues, CompletionListener completionListener) {
    cache.loadAll(compactBoundedKeys(keys), replaceExistingValues, completionListener);
  }

  @Override
  public void put(K key, V value) {
    cache.put(keyTransformer.compact(key), valueTransformer.compact(value));
  }

  @Override
  public V getAndPut(K key, V value) {
    return valueTransformer.expand(cache.getAndPut(keyTransformer.compact(key), valueTransformer.compact(value)));
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> map) {
    cache.putAll(compactMap(map));
  }

  @Override
  public boolean putIfAbsent(K key, V value) {
    return cache.putIfAbsent(keyTransformer.compact(key), valueTransformer.compact(value));
  }

  @Override
  public boolean remove(K key) {
    return cache.remove(passingKeyTransformer.compact(key));
  }

  @Override
  public boolean remove(K key, V oldValue) {
    return cache.remove(passingKeyTransformer.compact(key), valueTransformer.compact(oldValue));
  }

  @Override
  public V getAndRemove(K key) {
    return valueTransformer.expand(cache.getAndRemove(passingKeyTransformer.compact(key)));
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    return cache.replace(
        passingKeyTransformer.compact(key),
        passingValueTransformer.compact(oldValue),
        valueTransformer.compact(newValue));
  }

  @Override
  public boolean replace(K key, V value) {
    return cache.replace(passingKeyTransformer.compact(key), valueTransformer.compact(value));
  }

  @Override
  public V getAndReplace(K key, V value) {
    return passingValueTransformer.expand(
        cache.getAndReplace(passingKeyTransformer.compact(key), valueTransformer.compact(value)));
  }

  @Override
  public void removeAll(Set<? extends K> keys) {
    cache.removeAll(compactBoundedKeys(keys));
  }

  @Override
  public void removeAll() {
    cache.removeAll();
  }

  @Override
  public void clear() {
    cache.clear();
  }

  /**
   * Not supported (yet?)
   *
   * @throws UnsupportedOperationException always throws exception
   */
  @Override
  public <C extends Configuration<K, V>> C getConfiguration(Class<C> clazz) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T invoke(
      K key, final EntryProcessor<K, V, T> entryProcessor, Object... arguments) throws EntryProcessorException {
    EntryProcessor<K0, V0, T> processor = wrapEntryProcessor(entryProcessor);
    return cache.invoke(keyTransformer.compact(key), processor, arguments);
  }

  private <T> EntryProcessor<K0, V0, T> wrapEntryProcessor(final EntryProcessor<K, V, T> entryProcessor) {
    if (entryProcessor == null) {
      throw new NullPointerException("null processor");
    }
    return new EntryProcessor<K0, V0, T>() {
      @Override
      public T process(final MutableEntry<K0, V0> entry, Object... arguments) throws EntryProcessorException {
        MutableEntry<K, V>  e = wrapMutableEntry(entry);
        return entryProcessor.process(e, arguments);
      }
    };
  }

  private MutableEntry<K, V> wrapMutableEntry(final MutableEntry<K0, V0> entry) {
    return new MutableEntry<K, V>() {
      @Override
      public boolean exists() {
        return entry.exists();
      }

      @Override
      public void remove() {
        entry.remove();
      }

      @Override
      public void setValue(V value) {
        entry.setValue(valueTransformer.compact(value));
      }

      @Override
      public K getKey() {
        return keyTransformer.expand(entry.getKey());
      }

      @Override
      public V getValue() {
        return valueTransformer.expand(entry.getValue());
      }

      @Override
      public <T> T unwrap(Class<T> clazz) {
        return entry.unwrap(clazz);
      }
    };
  }

  @Override
  public <T> Map<K, EntryProcessorResult<T>> invokeAll(
      Set<? extends K> keys, EntryProcessor<K, V, T> entryProcessor, Object... arguments) {
    EntryProcessor<K0, V0, T> processor = wrapEntryProcessor(entryProcessor);
    Map<K0, EntryProcessorResult<T>> map = cache.invokeAll(compactBoundedKeys(keys), processor, arguments);
    Map<K, EntryProcessorResult<T>> m2 = new HashMap<K, EntryProcessorResult<T>>();
    for (Map.Entry<K0, EntryProcessorResult<T>> e : map.entrySet()) {
      m2.put(keyTransformer.expand(e.getKey()), e.getValue());
    }
    return m2;
  }

  @Override
  public String getName() {
    return cache.getName();
  }

  @Override
  public CacheManager getCacheManager() {
    return cache.getCacheManager();
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
    return cache.unwrap(clazz);
  }

  /**
   * Not supported (yet?)
   *
   * @throws UnsupportedOperationException always throws exception
   */
  @Override
  public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
    throw new UnsupportedOperationException();
  }

  /**
   * Not supported (yet?)
   *
   * @throws UnsupportedOperationException always throws exception
   */
  @Override
  public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Iterator<Entry<K, V>> iterator() {
    final Iterator<Entry<K0, V0>> it = cache.iterator();

    return new Iterator<Entry<K, V>>() {
      @Override
      public boolean hasNext() {
        return it.hasNext();
      }

      @Override
      public Entry<K, V> next() {
        final Entry<K0, V0> e = it.next();
        return new Entry<K, V>() {
          @Override
          public K getKey() {
            return keyTransformer.expand(e.getKey());
          }

          @Override
          public V getValue() {
            return valueTransformer.expand(e.getValue());
          }

          @Override
          public <T> T unwrap(Class<T> clazz) {
            return e.unwrap(clazz);
          }
        };
      }

      @Override
      public void remove() {
        it.remove();
      }
    };
  }

  public Cache<K0, V0> getWrappedCache() {
    return cache;
  }

  public String toString() {
    return getClass().getSimpleName() + "@" + cache;
  }

}
