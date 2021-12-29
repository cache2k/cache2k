package org.cache2k.jcache.provider;

/*-
 * #%L
 * cache2k JCache provider
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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
import org.cache2k.core.CacheClosedException;
import org.cache2k.jcache.provider.event.EventHandling;
import org.cache2k.processor.EntryProcessor;
import org.cache2k.io.CacheWriterException;
import org.cache2k.processor.EntryProcessingResult;
import org.cache2k.processor.MutableCacheEntry;
import org.cache2k.core.EntryAction;
import org.cache2k.core.api.InternalCache;

import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;

import javax.cache.integration.CacheLoaderException;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;
import javax.cache.processor.MutableEntry;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Forward cache operations to cache2k cache implementation.
 *
 * @author Jens Wilke
 */
public class JCacheAdapter<K, V> implements javax.cache.Cache<K, V> {

  private final JCacheManagerAdapter manager;
  protected final InternalCache<K, V> cache;
  private final boolean storeByValue;
  private final boolean loaderConfigured;
  protected final boolean readThrough;
  private final Class<K> keyType;
  private final Class<V> valueType;
  private final EventHandling<K, V> eventHandling;
  protected final AtomicLong iterationHitCorrectionCounter = new AtomicLong();
  protected volatile boolean jmxStatisticsEnabled = false;
  protected volatile boolean jmxEnabled = false;

  public JCacheAdapter(JCacheManagerAdapter manager, Cache<K, V> cache,
                       Class<K> keyType, Class<V> valueType,
                       boolean storeByValue, boolean readThrough, boolean loaderConfigured,
                       EventHandling<K, V> eventHandling) {
    this.manager = manager;
    this.cache = (InternalCache<K, V>) cache;
    this.keyType = keyType;
    this.valueType = valueType;
    this.storeByValue = storeByValue;
    this.readThrough = readThrough;
    this.loaderConfigured = loaderConfigured;
    this.eventHandling = eventHandling;
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
  public Map<K, V> getAll(Set<? extends K> keys) {
    checkClosed();
    if (readThrough) {
      return cache.getAll(keys);
    }
    return cache.peekAll(keys);
  }

  @Override
  public boolean containsKey(K key) {
    checkClosed();
    return cache.containsKey(key);
  }

  @Override
  public void loadAll(Set<? extends K> keys, boolean replaceExistingValues,
                      CompletionListener completionListener) {
    checkClosed();
    if (!loaderConfigured) {
      if (completionListener != null) {
        completionListener.onCompletion();
      }
      return;
    }
    CompletableFuture<Void> future;
    if (replaceExistingValues) {
      future = cache.reloadAll(keys);
    } else {
      future = cache.loadAll(keys);
    }
    future.handle((unused, throwable) -> {
      if (throwable != null) {
        completionListener.onException(new CacheLoaderException(throwable));
      } else {
        completionListener.onCompletion();
      }
      return null;
    });
  }

  @Override
  public void put(K k, V v) {
    checkClosed();
    checkNullValue(v);
    try {
      cache.put(k, v);
    } catch (CacheWriterException ex) {
      throw new javax.cache.integration.CacheWriterException(ex);
    } catch (EntryAction.ListenerException ex) {
      throw new javax.cache.event.CacheEntryListenerException(ex);
    }
  }

  @Override
  public V getAndPut(K key, V value) {
    checkClosed();
    checkNullValue(value);
    try {
      return cache.peekAndPut(key, value);
    } catch (CacheWriterException ex) {
      throw new javax.cache.integration.CacheWriterException(ex);
    } catch (EntryAction.ListenerException ex) {
      throw new javax.cache.event.CacheEntryListenerException(ex);
    }
  }

  private static void checkNullValue(Object value) {
    if (value == null) {
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
    if (map.containsKey(null)) {
      throw new NullPointerException("null key not allowed");
    }
    for (Map.Entry<? extends K, ? extends V> e : map.entrySet()) {
      checkNullValue(e.getValue());
    }
    try {
      cache.putAll(map);
    } catch (EntryAction.ListenerException ex) {
      throw new javax.cache.event.CacheEntryListenerException(ex);
    } catch (CacheWriterException ex) {
      throw new javax.cache.integration.CacheWriterException(ex);
    }
  }

  @Override
  public boolean putIfAbsent(K key, V value) {
    checkClosed();
    checkNullValue(value);
    try {
      return cache.putIfAbsent(key, value);
    } catch (EntryAction.ListenerException ex) {
      throw new javax.cache.event.CacheEntryListenerException(ex);
    } catch (CacheWriterException ex) {
      throw new javax.cache.integration.CacheWriterException(ex);
    }
  }

  @Override
  public boolean remove(K key) {
    checkClosed();
    try {
      return cache.containsAndRemove(key);
    } catch (EntryAction.ListenerException ex) {
      throw new javax.cache.event.CacheEntryListenerException(ex);
    } catch (CacheWriterException ex) {
      throw new javax.cache.integration.CacheWriterException(ex);
    }
  }

  @Override
  public boolean remove(K key, V oldValue) {
    checkClosed();
    checkNullValue(oldValue);
    try {
      return cache.removeIfEquals(key, oldValue);
    } catch (EntryAction.ListenerException ex) {
      throw new javax.cache.event.CacheEntryListenerException(ex);
    } catch (CacheWriterException ex) {
      throw new javax.cache.integration.CacheWriterException(ex);
    }
  }

  @Override
  public V getAndRemove(K key) {
    checkClosed();
    try {
      return cache.peekAndRemove(key);
    } catch (EntryAction.ListenerException ex) {
      throw new javax.cache.event.CacheEntryListenerException(ex);
    } catch (CacheWriterException ex) {
      throw new javax.cache.integration.CacheWriterException(ex);
    }
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    checkClosed();
    checkNullValue(oldValue);
    checkNullValue(newValue);
    try {
      return cache.replaceIfEquals(key, oldValue, newValue);
    } catch (EntryAction.ListenerException ex) {
      throw new javax.cache.event.CacheEntryListenerException(ex);
    } catch (CacheWriterException ex) {
      throw new javax.cache.integration.CacheWriterException(ex);
    }
  }

  @Override
  public boolean replace(K key, V value) {
    checkClosed();
    checkNullValue(value);
    try {
      return cache.replace(key, value);
    } catch (EntryAction.ListenerException ex) {
      throw new javax.cache.event.CacheEntryListenerException(ex);
    } catch (CacheWriterException ex) {
      throw new javax.cache.integration.CacheWriterException(ex);
    }
  }

  @Override
  public V getAndReplace(K key, V value) {
    checkClosed();
    checkNullValue(value);
    try {
      return cache.peekAndReplace(key, value);
    } catch (EntryAction.ListenerException ex) {
      throw new javax.cache.event.CacheEntryListenerException(ex);
    } catch (CacheWriterException ex) {
      throw new javax.cache.integration.CacheWriterException(ex);
    }
  }

  @Override
  public void removeAll(Set<? extends K> keys) {
    checkClosed();
    try {
      cache.removeAll(keys);
    } catch (EntryAction.ListenerException ex) {
      throw new javax.cache.event.CacheEntryListenerException(ex);
    } catch (CacheWriterException ex) {
      throw new javax.cache.integration.CacheWriterException(ex);
    }
  }

  @Override
  public void removeAll() {
    checkClosed();
    try {
      cache.removeAll();
    } catch (EntryAction.ListenerException ex) {
      throw new javax.cache.event.CacheEntryListenerException(ex);
    } catch (CacheWriterException ex) {
      throw new javax.cache.integration.CacheWriterException(ex);
    }
  }

  @Override
  public void clear() {
    cache.clear();
  }

  @SuppressWarnings("unchecked")
  @Override
  public <C extends Configuration<K, V>> C getConfiguration(Class<C> type) {
    if (CompleteConfiguration.class.isAssignableFrom(type)) {
      MutableConfiguration<K, V> cfg = new MutableConfiguration<>();
      cfg.setTypes(keyType, valueType);
      cfg.setStatisticsEnabled(jmxStatisticsEnabled);
      cfg.setManagementEnabled(jmxEnabled);
      cfg.setStoreByValue(storeByValue);
      Collection<CacheEntryListenerConfiguration<K, V>> listenerConfigurations =
        eventHandling.getAllListenerConfigurations();
      for (CacheEntryListenerConfiguration<K, V> listenerConfig : listenerConfigurations) {
        cfg.addCacheEntryListenerConfiguration(listenerConfig);
      }
      return (C) cfg;
    }
    return (C) new Configuration<K, V>() {
      @Override
      public Class<K> getKeyType() {
        return keyType;
      }

      @Override
      public Class<V> getValueType() {
        return valueType;
      }

      @Override
      public boolean isStoreByValue() {
        return storeByValue;
      }
    };
  }

  @Override
  public <T> T invoke(
    K key, javax.cache.processor.EntryProcessor<K, V, T> entryProcessor, Object... arguments)
    throws EntryProcessorException {
    checkClosed();
    checkNullKey(key);
    Map<K, EntryProcessorResult<T>> m =
      invokeAll(Collections.singleton(key), entryProcessor, arguments);
    return !m.isEmpty() ? m.values().iterator().next().get() : null;
  }

  @Override
  public <T> Map<K, EntryProcessorResult<T>> invokeAll(
    Set<? extends K> keys, javax.cache.processor.EntryProcessor<K, V, T> entryProcessor,
    Object... arguments) {
    checkClosed();
    if (entryProcessor == null) {
      throw new NullPointerException("processor is null");
    }
    EntryProcessor<K, V, T> p = e -> {
      MutableEntryAdapter me = new MutableEntryAdapter(e);
      T result = entryProcessor.process(me, arguments);
      return result;
    };
    Map<K, EntryProcessingResult<T>> result = cache.invokeAll(keys, p);
    Map<K, EntryProcessorResult<T>> mappedResult = new HashMap<>();
    for (Map.Entry<K, EntryProcessingResult<T>> e : result.entrySet()) {
      EntryProcessingResult<T> pr = e.getValue();
      EntryProcessorResult<T> epr = () -> {
        Throwable t = pr.getException();
        if (t != null) {
          throw new EntryProcessorException(t);
        }
        return pr.getResult();
      };
      mappedResult.put(e.getKey(), epr);
    }
    return mappedResult;
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

  @SuppressWarnings("unchecked")
  @Override
  public <T> T unwrap(Class<T> clazz) {
    if (Cache.class.equals(clazz)) {
      return (T) cache;
    }
    throw new IllegalArgumentException("requested class unknown");
  }

  @Override
  public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, V> cfg) {
    eventHandling.registerListener(cfg);
  }

  @Override
  public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, V> cfg) {
    if (cfg == null) {
      throw new NullPointerException();
    }
    eventHandling.deregisterListener(cfg);
  }

  /**
   * Iterate with the help of cache2k key iterator.
   */
  @Override
  public Iterator<Entry<K, V>> iterator() {
    checkClosed();
    Iterator<K> keyIterator = cache.keys().iterator();
    return new Iterator<Entry<K, V>>() {

      CacheEntry<K, V> entry;

      @Override
      public boolean hasNext() {
        while (keyIterator.hasNext()) {
          entry = cache.getEntry(keyIterator.next());
          if (entry.getException() == null) {
            return true;
          }
        }
        entry = null;
        return false;
      }

      @Override
      public Entry<K, V> next() {
        if (entry == null && !hasNext()) {
          throw new NoSuchElementException();
        }
        return new Entry<K, V>() {
          @Override
          public K getKey() {
            return entry.getKey();
          }

          @Override
          public V getValue() {
            return entry.getValue();
          }

          @SuppressWarnings("unchecked")
          @Override
          public <T> T unwrap(Class<T> type) {
            if (CacheEntry.class.equals(type)) {
              return (T) entry;
            }
            return null;
          }
        };
      }

      @Override
      public void remove() {
        if (entry == null) {
          throw new IllegalStateException(
            "hasNext() / next() not called or end of iteration reached");
        }
        cache.remove(entry.getKey());
      }
    };
  }

  /**
   * The TCK checks that cache closed exception is triggered before the exceptions about the
   * illegal argument, so first screen whether cache is closed.
   */
  void checkClosed() {
    if (cache.isClosed()) {
      throw new CacheClosedException(cache);
    }
  }

  private class MutableEntryAdapter implements MutableEntry<K, V> {

    private final MutableCacheEntry<K, V> entry;
    private boolean removed;
    private V value;

    MutableEntryAdapter(MutableCacheEntry<K, V> e) {
      entry = e;
    }

    @Override
    public boolean exists() {
      return !removed && (value != null || entry.exists());
    }

    @Override
    public void remove() {
      removed = true;
      value = null;
      entry.remove();
    }

    @Override
    public void setValue(V value) {
      checkNullValue(value);
      this.value = value;
      removed = false;
      entry.setValue(value);
    }

    @Override
    public K getKey() {
      return entry.getKey();
    }

    @Override
    public V getValue() {
      if (value != null) {
        return value;
      }
      if (removed) {
        return null;
      }
      if (!readThrough && !exists()) {
        return null;
      }
      return entry.getValue();
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
      return null;
    }
  }

  public String toString() {
    return getClass().getSimpleName() + "!" + cache;
  }

}
