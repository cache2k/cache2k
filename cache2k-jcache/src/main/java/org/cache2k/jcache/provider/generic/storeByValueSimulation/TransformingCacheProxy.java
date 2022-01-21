package org.cache2k.jcache.provider.generic.storeByValueSimulation;

/*-
 * #%L
 * cache2k JCache provider
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;
import javax.cache.processor.MutableEntry;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
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
public abstract class TransformingCacheProxy<K, V, K0, V0> implements Cache<K, V> {

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
   * @param passingKeyTransformer Special transformer for keys that go in and are not stored by
   *                              the cache (e.g. for #conatainsKey)
   * @param passingValueTransformer Special transformer for keys that go in and are not stored by
   *                                the cache (e.g. for the oldValue in replace)
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
    Map<K0, V0> m = cache.getAll(compactKeySet(keys));
    return expandMap(m);
  }

  static <E, I> Set<I> compactSet(Set<? extends E> keys,
                                  ObjectTransformer<E, I> tr) {
    if (keys == null) {
      return null;
    }
    int size = keys.size();
    return new AbstractSet<I>() {
      @Override
      public Iterator<I> iterator() {
        Iterator<? extends E> it = keys.iterator();
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
        return size;
      }
    };
  }

  Set<K0> compactKeySet(Set<? extends K> keys) {
    return compactSet(keys, keyTransformer);
  }

  Map<K0, V0> compactMap(Map<? extends K, ? extends V> map) {
    if (map == null) {
      return null;
    }
    Map<K0, V0> m2 = new HashMap<>();
    for (Map.Entry<? extends K, ? extends V> e : map.entrySet()) {
      m2.put(keyTransformer.compact(e.getKey()), valueTransformer.compact(e.getValue()));
    }
    return m2;
  }

  Map<K, V> expandMap(Map<K0, V0> map) {
    Map<K, V> m2 = new HashMap<>();
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
  public void loadAll(Set<? extends K> keys, boolean replaceExistingValues,
                      CompletionListener completionListener) {
    cache.loadAll(compactKeySet(keys), replaceExistingValues, completionListener);
  }

  @Override
  public void put(K key, V value) {
    cache.put(keyTransformer.compact(key), valueTransformer.compact(value));
  }

  @Override
  public V getAndPut(K key, V value) {
    return valueTransformer.expand(cache.getAndPut(keyTransformer.compact(key),
      valueTransformer.compact(value)));
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
    cache.removeAll(compactKeySet(keys));
  }

  @Override
  public void removeAll() {
    cache.removeAll();
  }

  @Override
  public void clear() {
    cache.clear();
  }

  @Override
  public <T> T invoke(
      K key, EntryProcessor<K, V, T> entryProcessor, Object... arguments)
    throws EntryProcessorException {
    EntryProcessor<K0, V0, T> processor = wrapEntryProcessor(entryProcessor);
    return cache.invoke(keyTransformer.compact(key), processor, arguments);
  }

  private <T> EntryProcessor<K0, V0, T> wrapEntryProcessor(
    EntryProcessor<K, V, T> entryProcessor) {
    Objects.requireNonNull(entryProcessor);
    return (entry, arguments) -> {
      MutableEntry<K, V>  e = wrapMutableEntry(entry);
      return entryProcessor.process(e, arguments);
    };
  }

  private MutableEntry<K, V> wrapMutableEntry(MutableEntry<K0, V0> entry) {
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
    Map<K0, EntryProcessorResult<T>> map =
      cache.invokeAll(compactKeySet(keys), processor, arguments);
    Map<K, EntryProcessorResult<T>> m2 = new HashMap<>();
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

  @Override
  public Iterator<Entry<K, V>> iterator() {
    Iterator<Entry<K0, V0>> it = cache.iterator();

    return new Iterator<Entry<K, V>>() {
      @Override
      public boolean hasNext() {
        return it.hasNext();
      }

      @Override
      public Entry<K, V> next() {
        Entry<K0, V0> e = it.next();
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
