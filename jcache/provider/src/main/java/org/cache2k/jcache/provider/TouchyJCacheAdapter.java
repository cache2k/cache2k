package org.cache2k.jcache.provider;

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
import org.cache2k.EntryExpiryCalculator;
import org.cache2k.jcache.provider.event.EventHandlingBase;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;
import javax.cache.processor.MutableEntry;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Adapter to add required semantics for JSR107 with expiry policy.
 *
 * <p>There are multiple requirements which makes cache operations with expiry policy very inefficient. These are:
 * <ul>
 *   <li>The TCK checks that the access policy is called and adjusted on each cache request</li>
 *   <li>The TCK has some tests that use a zero duration on expiry, so an entry is expired after the first access</li>
 *   <li>The TCK does not allow that the expiry policy methods are called in the configuration phase</li>
 *   <li>In case the expiry policy methods return null, this means, that the expiry is not changed</li>
 * </ul>
 *
 * </p>
 * JSR107 has rules which make cache operations with an expiry policy quite ineffective.
 *
 *
 * @author Jens Wilke; created: 2015-04-01
 */
public class TouchyJCacheAdapter<K, V> implements Cache<K, V> {

  org.cache2k.impl.InternalCache<K, TimeVal<V>> c2kCache;
  JCacheAdapter<K, TimeVal<V>> cache;
  Class<K> keyType;
  Class<V> valueType;
  ExpiryPolicy expiryPolicy;
  EventHandling<K,V> eventHandling;

  @Override
  public V get(K key) {
    return returnValue(key, cache.get(key));
  }

  @Override
  public Map<K, V> getAll(Set<? extends K> keys) {
    final Map<K, TimeVal<V>> m0 = cache.getAll(keys);
    final Map<K, TimeVal<V>> map = new HashMap<K, TimeVal<V>>();
    for (Map.Entry<K, TimeVal<V>> e : m0.entrySet()) {
      if (e.getValue() != null) {
        map.put(e.getKey(), e.getValue());
      }
    }
    return new Map<K, V>() {
      @Override
      public int size() {
        return map.size();
      }

      @Override
      public boolean isEmpty() {
        return map.isEmpty();
      }

      @Override
      public boolean containsKey(Object key) {
        return map.containsKey(key);
      }

      @Override
      public boolean containsValue(Object value) {
        return map.containsValue(value);
      }

      @Override
      public V get(Object key) {
        return returnValue((K) key, map.get(key));
      }

      @Override
      public V put(K key, V value) {
        throw new UnsupportedOperationException("read only");
      }

      @Override
      public V remove(Object key) {
        return returnLastValue(map.remove(key));
      }

      @Override
      public void putAll(Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException("read only");
      }

      @Override
      public void clear() {
        throw new UnsupportedOperationException("read only");
      }

      @Override
      public Set<K> keySet() {
        return map.keySet();
      }

      @Override
      public Collection<V> values() {
        return new AbstractCollection<V>() {
          @Override
          public int size() {
            return map.size();
          }

          @Override
          public boolean isEmpty() {
            return map.isEmpty();
          }

          @Override
          public Iterator<V> iterator() {
            final Iterator<Entry<K, TimeVal<V>>> it = map.entrySet().iterator();
            return new Iterator<V>() {
              @Override
              public boolean hasNext() {
                return it.hasNext();
              }

              @Override
              public V next() {
                Entry<K, TimeVal<V>> e = it.next();
                return returnValue(e.getKey(), e.getValue());
              }

              @Override
              public void remove() {
                throw new UnsupportedOperationException();
              }
            };
          }

        };
      }

      @Override
      public Set<Entry<K, V>> entrySet() {
        final Iterator<Entry<K, TimeVal<V>>> it = map.entrySet().iterator();
        return new AbstractSet<Entry<K, V>>() {
          @Override
          public Iterator<Entry<K, V>> iterator() {
            return new Iterator<Entry<K, V>>() {
              @Override
              public boolean hasNext() {
                return it.hasNext();
              }

              @Override
              public Entry<K, V> next() {
                final Entry<K, TimeVal<V>> e = it.next();
                return new Entry<K, V>() {
                  @Override
                  public K getKey() {
                    return e.getKey();
                  }

                  @Override
                  public V getValue() {
                    return returnValue(e.getKey(), e.getValue());
                  }

                  @Override
                  public V setValue(V value) {
                    throw new UnsupportedOperationException();
                  }
                };
              }

              @Override
              public void remove() {

              }
            };
          }

          @Override
          public int size() {
            return map.size();
          }
        };
      }
    };
  }

  @Override
  public boolean containsKey(K key) {
    return cache.containsKey(key);
  }

  @Override
  public void loadAll(Set<? extends K> keys, boolean replaceExistingValues, CompletionListener completionListener) {
    cache.loadAll(keys, replaceExistingValues, completionListener);
  }

  @Override
  public void put(K key, V value) {
    checkClosed();
    cache.put(key, new TimeVal<V>(value));
  }

  @Override
  public V getAndPut(K key, V value) {
    checkClosed();
    TimeVal<V> e = cache.getAndPut(key, new TimeVal(value));
    if (e != null) {
      return e.value;
    }
    return null;
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> map) {
    checkClosed();
    if (map.containsKey(null)) {
      throw new NullPointerException("null key not allowed");
    }
    Map<K, TimeVal<V>> m2 = new HashMap<K, TimeVal<V>>();
    for (Map.Entry<? extends K, ? extends V> e : map.entrySet()) {
      m2.put(e.getKey(), new TimeVal<V>(e.getValue()));
    }
    cache.putAll(m2);
  }

  @Override
  public boolean putIfAbsent(K key, V value) {
    checkClosed();
    return cache.putIfAbsent(key, new TimeVal<V>(value));
  }

  @Override
  public boolean remove(K key) {
    return cache.remove(key);
  }

  @Override
  public boolean remove(final K key, final V oldValue) {
    checkClosed();
    checkNullValue(oldValue);
    if (key == null) {
      throw new NullPointerException();
    }
    EntryProcessor<K, TimeVal<V>, Boolean> ep = new EntryProcessor<K, TimeVal<V>, Boolean>() {
      @Override
      public Boolean process(final MutableEntry<K, TimeVal<V>> entry, final Object... arguments) throws EntryProcessorException {
        if (!entry.exists()) {
          return false;
        }
        TimeVal<V> _existingValue = entry.getValue();
        if (_existingValue.value.equals(oldValue)) {
          entry.remove();
          return true;
        }
        Duration d = expiryPolicy.getExpiryForAccess();
        if (d != null) {
          TimeVal<V> _newEntry = newValue(_existingValue, d);
          entry.setValue(_newEntry);
        }
        return false;
      }
    };
    return cache.invoke(key, ep);
  }

  @Override
  public V getAndRemove(K key) {
    return returnLastValue(cache.getAndRemove(key));
  }

  final CacheEntry<K, TimeVal<V>> DUMMY_ENTRY = new CacheEntry<K, TimeVal<V>>() {
    @Override
    public K getKey() {
      return null;
    }

    @Override
    public TimeVal<V> getValue() {
      return null;
    }

    @Override
    public Throwable getException() {
      return null;
    }

    @Override
    public long getLastModification() {
      return 0;
    }
  };

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    checkClosed();
    CacheEntry<K, TimeVal<V>> e =
        c2kCache.replaceOrGet(
            key,
            new TimeVal<V>(oldValue),
            new TimeVal<V>(newValue),
            DUMMY_ENTRY);
    if (e != null && e != DUMMY_ENTRY) {
      touchEntry(key, e.getValue());
    }
    return e == null;
  }

  @Override
  public boolean replace(K key, V value) {
    checkClosed();
    return c2kCache.replace(key, new TimeVal<V>(value));
  }

  @Override
  public V getAndReplace(K key, V value) {
    checkClosed();
    return returnLastValue(cache.getAndReplace(key, new TimeVal<V>(value)));
  }

  @Override
  public void removeAll(Set<? extends K> keys) {
    cache.removeAll(keys);
  }

  @Override
  public void removeAll() {
    cache.removeAll();
  }

  @Override
  public void clear() {
    cache.clear();
  }

  @SuppressWarnings("unchecked")
  @Override
  public <C extends Configuration<K, V>> C getConfiguration(Class<C> clazz) {
    if (CompleteConfiguration.class.isAssignableFrom(clazz)) {
      MutableConfiguration<K, V> cfg = new MutableConfiguration<K, V>();
      cfg.setTypes(keyType, valueType);
      cfg.setStatisticsEnabled(cache.statisticsEnabled);
      cfg.setManagementEnabled(cache.configurationEnabled);
      Collection<CacheEntryListenerConfiguration<K,V>> _listenerConfigurations = eventHandling.getAllListenerConfigurations();
      for (CacheEntryListenerConfiguration<K,V> _listenerConfig : _listenerConfigurations) {
        cfg.addCacheEntryListenerConfiguration(_listenerConfig);
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
        return false;
      }
    };
  }

  <T> EntryProcessor<K, TimeVal<V>, T> wrapEntryProcessor(final EntryProcessor<K, V, T> ep) {
    if (ep == null) {
      throw new NullPointerException("processor is null");
    }
    return new EntryProcessor<K, TimeVal<V>, T>() {
      boolean freshOrJustLoaded = false;
      @Override
      public T process(final MutableEntry<K, TimeVal<V>> e0, Object... _args) throws EntryProcessorException {
        MutableEntry<K, V> me = new MutableEntry<K, V>() {

          @Override
          public boolean exists() {
            return e0.exists();
          }

          @Override
          public void remove() {
            e0.remove();
          }

          @Override
          public void setValue(V value) {
            freshOrJustLoaded = true;
            e0.setValue(new TimeVal<V>(value));
          }

          @Override
          public K getKey() {
            return e0.getKey();
          }

          @Override
          public V getValue() {
            boolean _doNotCountCacheAccessIfEntryGetsLoaded = !exists();
            boolean _doNotCountCacheAccessIfEntryIsFresh = freshOrJustLoaded;
            if (_doNotCountCacheAccessIfEntryIsFresh || _doNotCountCacheAccessIfEntryGetsLoaded) {
              if (!cache.readThrough && !exists()) {
                return null;
              }
              freshOrJustLoaded = true;
              TimeVal<V> v = e0.getValue();
              return  v != null ? v.value : null;
            }
            return returnValue(e0.getKey(), e0.getValue());
          }

          @Override
          public <T> T unwrap(Class<T> clazz) {
            return null;
          }
        };
        return ep.process(me, _args);
      }
    };
  }

  @Override
  public <T> T invoke(K key, EntryProcessor<K, V, T> entryProcessor, Object... arguments) throws EntryProcessorException {
    return cache.invoke(key, wrapEntryProcessor(entryProcessor), arguments);
  }

  @Override
  public <T> Map<K, EntryProcessorResult<T>> invokeAll(Set<? extends K> keys, EntryProcessor<K, V, T> entryProcessor, Object... arguments) {
    return cache.invokeAll(keys, wrapEntryProcessor(entryProcessor), arguments);
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
    if (org.cache2k.Cache.class.equals(clazz)) {
      return (T) ((JCacheAdapter) cache).cache;
    }
    throw new IllegalArgumentException("unwrap wrong type");
  }

  @Override
  public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, V> cfg) {
    eventHandling.registerListener(cfg);
  }

  @Override
  public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, V> cfg) {
    eventHandling.unregisterListener(Util.requireNonNull(cfg, "listener configuration"));
  }

  @Override
  public Iterator<Cache.Entry<K, V>> iterator() {
    final Iterator<Cache.Entry<K, TimeVal<V>>> it = cache.iterator();
    return new Iterator<Entry<K, V>>() {
      @Override
      public boolean hasNext() {
        return it.hasNext();
      }

      @Override
      public Entry<K, V> next() {
        final Entry<K, TimeVal<V>> e = it.next();
        return returnEntry(e);
      }

      @Override
      public void remove() {
        it.remove();
      }
    };
  }

  Entry<K, V> returnEntry(final Entry<K, TimeVal<V>> e) {
    final K key = e.getKey();
    final TimeVal<V> v = e.getValue();
    final V _value = returnValue(key, v);
    return new Entry<K, V>() {
      @Override
      public K getKey() {
        return key;
      }

      @Override
      public V getValue() {
        return _value;
      }

      @Override
      public <T> T unwrap(Class<T> clazz) {
        if (clazz.equals(CacheEntry.class)) {
          final CacheEntry<K, TimeVal<V>> ce = e.unwrap(CacheEntry.class);
          return (T) new CacheEntry<K, V>() {
            @Override
            public Throwable getException() {
              return ce.getException();
            }

            @Override
            public K getKey() {
              return key;
            }

            @Override
            public long getLastModification() {
              return ce.getLastModification();
            }

            @Override
            public V getValue() {
              return _value;
            }
          };
        }
        return null;
      }
    };
  }

  void checkClosed() {
    if (cache.isClosed()) {
      throw new IllegalStateException("cache is closed");
    }
  }

  private TimeVal<V> newValue(final TimeVal<V> e, final Duration d) {
    TimeVal<V> _newEntry = new TimeVal<V>(e.value);
    if (Duration.ZERO.equals(d)) {
      _newEntry.expiryTime = 1;
    } else if (Duration.ETERNAL.equals(d)) {
      _newEntry.expiryTime = Long.MAX_VALUE;
    } else {
      _newEntry.expiryTime = System.currentTimeMillis() + d.getTimeUnit().toMillis(d.getDurationAmount());
    }
    return _newEntry;
  }

  V returnLastValue(TimeVal<V> e) {
    if (e != null) {
      return e.value;
    }
    return null;
  }

  V returnValue(K key, TimeVal<V> e) {
    if (e != null) {
      touchEntry(key, e);
      return e.value;
    }
    return null;
  }

  private void touchEntry(K key, TimeVal<V> e) {
    Duration d = expiryPolicy.getExpiryForAccess();
    if (d != null) {
      TimeVal<V> _newEntry = newValue(e, d);
      c2kCache.replaceIfEquals(key, e, _newEntry);
    }
  }

  void checkNullValue(V _value) {
    if (_value == null) {
      throw new NullPointerException("value is null");
    }
  }

  public static class TimeVal<V> {

    V value;

    /**
     * If the expiry policy rule returns null, the expiry time is not changed. We need to remember the expiry time.
     */
    long expiryTime;

    public TimeVal(V _value) {
      if (_value == null) {
        throw new NullPointerException("value is null");
      }
      value = _value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      TimeVal entry = (TimeVal) o;
      if (!value.equals(entry.value)) {
        return false;
      }
      return true;
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }

  }

  static class ExpiryCalculatorAdapter<K, V> implements EntryExpiryCalculator<K, TimeVal<V>> {

    ExpiryPolicy policy;

    public ExpiryCalculatorAdapter(ExpiryPolicy policy) {
      this.policy = policy;
    }

    @Override
    public long calculateExpiryTime(K _key, TimeVal<V> _value, long _fetchTime, CacheEntry<K, TimeVal<V>> _oldEntry) {
      if (_value == null) {
        return 0;
      }
      Duration d;
      if (_value.expiryTime >= 1) {
        return _value.expiryTime == 1 ? 0 : _value.expiryTime;
      }
      if (_oldEntry == null) {
        d = policy.getExpiryForCreation();
      } else {
        d = policy.getExpiryForUpdate();
      }
      if (d == null) {
        if (_oldEntry == null) {
          throw new NullPointerException("no previous expiry value: null expiry duration not valid");
        }
        if (_oldEntry.getException() != null) {
          throw new RuntimeException("exception on this entry, missing duration...", _oldEntry.getException());
        }
        return _value.expiryTime = _oldEntry.getValue().expiryTime;

      }
      if (d.equals(Duration.ETERNAL)) {
        return _value.expiryTime = Long.MAX_VALUE;
      }
      if (d.equals(Duration.ZERO)) {
        return _value.expiryTime = 0;
      }
      return _value.expiryTime = _fetchTime + d.getTimeUnit().toMillis(d.getDurationAmount());
    }
  }

  public String toString() {
    return c2kCache.toString();
  }

  static class EventHandling<K,V> extends EventHandlingBase<K,V,TimeVal<V>> {

    @Override
    protected V extractValue(final TimeVal<V> _value) {
      return _value.value;
    }
  }

}
