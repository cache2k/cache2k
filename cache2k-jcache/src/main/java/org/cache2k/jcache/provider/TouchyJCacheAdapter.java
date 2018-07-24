package org.cache2k.jcache.provider;

/*
 * #%L
 * cache2k JCache provider
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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

import org.cache2k.CacheEntry;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.processor.EntryProcessor;
import org.cache2k.processor.MutableCacheEntry;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;
import javax.cache.processor.MutableEntry;
import java.io.Closeable;
import java.io.IOException;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Adapter to add required semantics for JSR107 with a custom expiry policy. The JCacheAdapter is
 * wrapped again and the expiry policy is called when needed.
 *
 * <p>There are multiple requirements which makes cache operations with expiry policy very inefficient. These are:
 * <ul>
 *   <li>The TCK checks that the access policy is called and adjusted on each cache request</li>
 *   <li>The TCK has some tests that use a zero duration on expiry, so an entry is expired after the first access</li>
 *   <li>The TCK does not allow that the expiry policy methods are called in the configuration phase</li>
 *   <li>In case the expiry policy methods return null, this means, that the expiry is not changed</li>
 * </ul>
 *
 * <p>
 *
 * @author Jens Wilke
 */
public class TouchyJCacheAdapter<K, V> implements Cache<K, V> {

  org.cache2k.Cache<K, V> c2kCache;
  JCacheAdapter<K, V> cache;
  ExpiryPolicy expiryPolicy;

  public TouchyJCacheAdapter(JCacheAdapter<K,V> _cache, ExpiryPolicy _expiryPolicy) {
    expiryPolicy = _expiryPolicy;
    cache = _cache;
    c2kCache = _cache.cache;
  }

  @Override
  public V get(K key) {
    return returnValue(key, cache.get(key));
  }

  @Override
  public Map<K, V> getAll(Set<? extends K> keys) {
    final Map<K, V> map = cache.getAll(keys);
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

      @SuppressWarnings("unchecked")
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
        return map.remove(key);
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
            final Iterator<Entry<K, V>> it = map.entrySet().iterator();
            return new Iterator<V>() {
              @Override
              public boolean hasNext() {
                return it.hasNext();
              }

              @Override
              public V next() {
                Entry<K, V> e = it.next();
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
        final Iterator<Entry<K, V>> it = map.entrySet().iterator();
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
                final Entry<K, V> e = it.next();
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
    cache.put(key, value);
  }

  @Override
  public V getAndPut(K key, V value) {
    checkClosed();
    return cache.getAndPut(key,value);
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> map) {
    cache.putAll(map);
  }

  @Override
  public boolean putIfAbsent(K key, V value) {
    return cache.putIfAbsent(key, value);
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
    EntryProcessor<K,V,Boolean> ep = new EntryProcessor<K, V, Boolean>() {
      @Override
      public Boolean process(final MutableCacheEntry<K, V> e) {
        if (!e.exists()) {
          return false;
        }
        V _existingValue = e.getValue();
        if (_existingValue.equals(oldValue)) {
          e.remove();
          return true;
        }
        Duration d = expiryPolicy.getExpiryForAccess();
        if (d != null) {
          e.setExpiry(calculateExpiry(d));
        }
        return false;
      }
    };
    return c2kCache.invoke(key, ep);
  }

  @Override
  public V getAndRemove(K key) {
    return cache.getAndRemove(key);
  }

  private final static CacheEntry DUMMY_ENTRY = new CacheEntry() {
    @Override
    public Object getKey() {
      return null;
    }

    @Override
    public Object getValue() {
      return null;
    }

    @Override
    public Throwable getException() {
      return null;
    }

    @SuppressWarnings("deprecation")
    @Override
    public long getLastModification() {
      return 0;
    }
  };

  @SuppressWarnings("unchecked")
  @Override
  public boolean replace(K key, final V oldValue, final V newValue) {
    checkClosed();
    checkNullValue(newValue);
    checkNullValue(oldValue);
    return
      c2kCache.invoke(key, new EntryProcessor<K, V, Boolean>() {
      @Override
      public Boolean process(final MutableCacheEntry<K, V> e) {
        if (e.exists()) {
          if (oldValue.equals(e.getValue())) {
            e.setValue(newValue);
            return true;
          } else {
            Duration d = expiryPolicy.getExpiryForAccess();
            if (d != null) {
              e.setExpiry(calculateExpiry(d));
            }
          }
        }
        return false;
      }
    });
  }

  @Override
  public boolean replace(K key, V value) {
    checkClosed();
    checkNullValue(value);
    return c2kCache.replace(key, value);
  }

  @Override
  public V getAndReplace(K key, V value) {
    return cache.getAndReplace(key, value);
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
    return cache.getConfiguration(clazz);
  }

  @Override
  public <T> T invoke(K key, javax.cache.processor.EntryProcessor<K,V, T> entryProcessor, Object... arguments) throws EntryProcessorException {
    return cache.invoke(key, wrapEntryProcessor(entryProcessor), arguments);
  }

  @Override
  public <T> Map<K, EntryProcessorResult<T>> invokeAll(Set<? extends K> keys, javax.cache.processor.EntryProcessor<K,V,T> entryProcessor, Object... arguments) {
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
    return c2kCache.requestInterface(clazz);
  }

  @Override
  public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, V> cfg) {
    cache.registerCacheEntryListener(cfg);
  }

  @Override
  public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, V> cfg) {
    cache.deregisterCacheEntryListener(cfg);
  }

  @Override
  public Iterator<Cache.Entry<K, V>> iterator() {
    final Iterator<Cache.Entry<K, V>> it = cache.iterator();
    return new Iterator<Entry<K, V>>() {
      @Override
      public boolean hasNext() {
        return it.hasNext();
      }

      @Override
      public Entry<K, V> next() {
        final Entry<K, V> e = it.next();
        return returnEntry(e);
      }

      @Override
      public void remove() {
        it.remove();
      }
    };
  }

  private <T> javax.cache.processor.EntryProcessor<K,V,T> wrapEntryProcessor(final javax.cache.processor.EntryProcessor<K,V,T> ep) {
    if (ep == null) {
      throw new NullPointerException("processor is null");
    }
    return new javax.cache.processor.EntryProcessor<K,V, T>() {
      boolean freshOrJustLoaded = false;
      @Override
      public T process(final MutableEntry<K, V> e0, Object... _args) throws EntryProcessorException {
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
            checkNullValue(value);
            freshOrJustLoaded = true;
            e0.setValue(value);
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
              return e0.getValue();
            }
            return returnValue(e0.getKey(), e0.getValue());
          }

          @Override
          public <X> X unwrap(Class<X> clazz) {
            return null;
          }
        };
        return ep.process(me, _args);
      }
    };
  }

  /**
   * Entry is accessed update expiry if needed.
   */
  private Entry<K, V> returnEntry(final Entry<K, V> e) {
    touchEntry(e.getKey());
    return e;
  }

  /**
   * Entry was accessed update expiry if value is non null.
   */
  private V returnValue(K key, V _value) {
    if (_value != null) {
      Duration d = expiryPolicy.getExpiryForAccess();
      if (d != null) {
        c2kCache.expireAt(key, calculateExpiry(d));
      }
      return _value;
    }
    return null;
  }

  private static long calculateExpiry(final Duration d) {
    if (Duration.ZERO.equals(d)) {
      return ExpiryTimeValues.NO_CACHE;
    } else if (Duration.ETERNAL.equals(d)) {
      return ExpiryTimeValues.ETERNAL;
    }
    return System.currentTimeMillis() + d.getTimeUnit().toMillis(d.getDurationAmount());
  }

  private void touchEntry(K key) {
    Duration d = expiryPolicy.getExpiryForAccess();
    if (d != null) {
      c2kCache.expireAt(key, calculateExpiry(d));
    }
  }

  private void checkClosed() {
    if (cache.isClosed()) {
      throw new IllegalStateException("cache is closed");
    }
  }

  private void checkNullValue(V _value) {
    if (_value == null) {
      throw new NullPointerException("value is null");
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "@" + c2kCache.toString();
  }

  public static class ExpiryPolicyAdapter<K, V>
    implements org.cache2k.expiry.ExpiryPolicy<K, V>, Closeable {

    private ExpiryPolicy policy;

    public ExpiryPolicyAdapter(ExpiryPolicy policy) {
      this.policy = policy;
    }

    @Override
    public long calculateExpiryTime(K _key, V _value, long _loadTime, CacheEntry<K, V> _oldEntry) {
      if (_value == null) {
        return NO_CACHE;
      }
      Duration d;
      if (_oldEntry == null || _oldEntry.getException() != null) {
        d = policy.getExpiryForCreation();
      } else {
        d = policy.getExpiryForUpdate();
      }
      if (d == null) {
        return ExpiryTimeValues.NEUTRAL;
      }
      if (d.equals(Duration.ETERNAL)) {
        return ExpiryTimeValues.ETERNAL;
      }
      if (d.equals(Duration.ZERO)) {
        return ExpiryTimeValues.NO_CACHE;
      }
      return _loadTime + d.getTimeUnit().toMillis(d.getDurationAmount());
    }

    @Override
    public void close() throws IOException {
      if (policy instanceof Closeable) {
        ((Closeable) policy).close();
      }
    }

  }

}
