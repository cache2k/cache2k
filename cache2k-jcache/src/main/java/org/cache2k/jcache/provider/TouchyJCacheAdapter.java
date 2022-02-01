package org.cache2k.jcache.provider;

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

import org.cache2k.CacheEntry;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.operation.CacheControl;
import org.cache2k.operation.TimeReference;
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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Adapter to add required semantics for JSR107 with a custom expiry policy. The JCacheAdapter is
 * wrapped again and the expiry policy is called when needed.
 *
 * <p>There are multiple requirements which makes cache operations with expiry policy very
 * inefficient. These are:
 * <ul>
 *   <li>The TCK checks that the access policy is called and adjusted on each cache request</li>
 *   <li>The TCK has some tests that use a zero duration on expiry, so an entry is expired after
 *       the first access</li>
 *   <li>The TCK does not allow that the expiry policy methods are called in the
 *       configuration phase</li>
 *   <li>In case the expiry policy methods return null, this means, that the expiry is not
 *       changed</li>
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
  TimeReference clock;

  public TouchyJCacheAdapter(JCacheAdapter<K, V> cache, ExpiryPolicy expiryPolicy) {
    this.expiryPolicy = expiryPolicy;
    this.cache = cache;
    c2kCache = cache.cache;
    clock = CacheControl.of(c2kCache).getTimeReference();
  }

  @Override
  public V get(K key) {
    return returnValue(key, cache.get(key));
  }

  @Override
  public Map<K, V> getAll(Set<? extends K> keys) {
    Map<K, V> map = cache.getAll(keys);
    if (map.isEmpty()) {
      return map;
    }
    Duration d = expiryPolicy.getExpiryForAccess();
    if (d != null) {
      long ticks = durationToTicks(clock, 0, d);
      c2kCache.invokeAll(map.keySet(), entry -> entry.setExpiryTime(ticks));
    }
    return map;
  }

  @Override
  public boolean containsKey(K key) {
    return cache.containsKey(key);
  }

  @Override
  public void loadAll(Set<? extends K> keys, boolean replaceExistingValues,
                      CompletionListener completionListener) {
    cache.loadAll(keys, replaceExistingValues, completionListener);
  }

  @Override
  public void put(K key, V value) {
    cache.put(key, value);
  }

  @Override
  public V getAndPut(K key, V value) {
    checkClosed();
    return cache.getAndPut(key, value);
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
  public boolean remove(K key, V oldValue) {
    checkClosed();
    checkNullValue(oldValue);
    if (key == null) {
      throw new NullPointerException();
    }
    EntryProcessor<K, V, Boolean> ep = new EntryProcessor<K, V, Boolean>() {
      @Override
      public Boolean process(MutableCacheEntry<K, V> e) {
        if (!e.exists()) {
          return false;
        }
        V existingValue = e.getValue();
        if (existingValue.equals(oldValue)) {
          e.remove();
          return true;
        }
        Duration d = expiryPolicy.getExpiryForAccess();
        if (d != null) {
          e.setExpiryTime(durationToTicks(clock, 0, d));
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

  @SuppressWarnings("unchecked")
  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    checkClosed();
    checkNullValue(newValue);
    checkNullValue(oldValue);
    return
      c2kCache.invoke(key, new EntryProcessor<K, V, Boolean>() {
      @Override
      public Boolean process(MutableCacheEntry<K, V> e) {
        if (e.exists()) {
          if (oldValue.equals(e.getValue())) {
            e.setValue(newValue);
            return true;
          } else {
            Duration d = expiryPolicy.getExpiryForAccess();
            if (d != null) {
              e.setExpiryTime(durationToTicks(clock, 0, d));
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
  public <T> T invoke(K key, javax.cache.processor.EntryProcessor<K, V, T> entryProcessor,
                      Object... arguments) throws EntryProcessorException {
    return cache.invoke(key, wrapEntryProcessor(entryProcessor), arguments);
  }

  @Override
  public <T> Map<K, EntryProcessorResult<T>> invokeAll(Set<? extends K> keys,
     javax.cache.processor.EntryProcessor<K, V, T> entryProcessor, Object... arguments) {
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
    Iterator<Cache.Entry<K, V>> it = cache.iterator();
    return new Iterator<Entry<K, V>>() {
      @Override
      public boolean hasNext() {
        return it.hasNext();
      }

      @Override
      public Entry<K, V> next() {
        Entry<K, V> e = it.next();
        return returnEntry(e);
      }

      @Override
      public void remove() {
        it.remove();
      }
    };
  }

  private <T> javax.cache.processor.EntryProcessor<K, V, T> wrapEntryProcessor(
    javax.cache.processor.EntryProcessor<K, V, T> ep) {
    if (ep == null) {
      throw new NullPointerException("processor is null");
    }
    return new javax.cache.processor.EntryProcessor<K, V, T>() {
      @Override
      public T process(MutableEntry<K, V> e0, Object... args)
        throws EntryProcessorException {
        WrappedMutableEntry<K, V> wrappedEntry = new WrappedMutableEntry<K, V>(e0);
        T result = ep.process(wrappedEntry, args);
        if (wrappedEntry.isAccessed()) {
          Duration d = expiryPolicy.getExpiryForAccess();
          if (d != null) {
            long ticks = durationToTicks(clock, 0, d);
            ((JCacheAdapter.MutableEntryAdapter) e0).setExpiryTime(ticks);
          }
        }
        return result;
      }
    };
  }

  /**
   * Determine whether existing cache entry was accessed
   */
  static class WrappedMutableEntry<K, V> implements MutableEntry<K, V> {
    private MutableEntry<K, V> entry;

    private boolean accessed = false;
    private boolean written = false;

    public WrappedMutableEntry(MutableEntry<K, V> entry) {
      this.entry = entry;
    }

    /**
     * Only true if no write before or after the access of an existing entry
     */
    public boolean isAccessed() {
      return accessed & !written;
    }

    @Override
    public boolean exists() {
      return entry.exists();
    }

    @Override
    public void remove() {
      written = true;
      entry.remove();
    }

    @Override
    public V getValue() {
      if (exists()) {
        accessed = !written;
      }
      return entry.getValue();
    }

    @Override
    public void setValue(V value) {
      written = true;
      entry.setValue(value);
    }

    @Override
    public K getKey() {
      return entry.getKey();
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
      return entry.unwrap(clazz);
    }
  }

  /**
   * Entry is accessed update expiry if needed.
   */
  private Entry<K, V> returnEntry(Entry<K, V> e) {
    touchEntry(e.getKey());
    return e;
  }

  /**
   * Entry was accessed update expiry if value is non null.
   */
  private V returnValue(K key, V value) {
    if (value == null) {
      return null;
    }
    touchEntry(key);
    return value;
  }

  private void touchEntry(K key) {
    Duration d = expiryPolicy.getExpiryForAccess();
    if (d != null) {
      c2kCache.expireAt(key, durationToTicks(clock, 0, d));
    }
  }

  private void checkClosed() {
    cache.checkClosed();
  }

  private void checkNullValue(V value) {
    if (value == null) {
      throw new NullPointerException("value is null");
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "!" + cache.toString();
  }

  public static class ExpiryPolicyAdapter<K, V>
    implements org.cache2k.expiry.ExpiryPolicy<K, V>, Closeable {

    private final ExpiryPolicy policy;

    public ExpiryPolicyAdapter(ExpiryPolicy policy) {
      this.policy = policy;
    }

    @Override
    public long calculateExpiryTime(K key, V value, long startTime, CacheEntry<K, V> currentEntry) {
      if (value == null) {
        return NOW;
      }
      Duration d;
      if (currentEntry == null || currentEntry.getException() != null) {
        d = policy.getExpiryForCreation();
      } else {
        d = policy.getExpiryForUpdate();
      }
      if (d == null) {
        return ExpiryTimeValues.NEUTRAL;
      }
      return durationToTicks(TimeReference.DEFAULT, startTime, d);
    }

    @Override
    public void close() throws IOException {
      if (policy instanceof Closeable) {
        ((Closeable) policy).close();
      }
    }

  }

  private static long durationToTicks(TimeReference clock, long nowTicks, Duration d) {
    if (d.equals(Duration.ETERNAL)) {
      return ExpiryTimeValues.ETERNAL;
    }
    if (d.equals(Duration.ZERO)) {
      return ExpiryTimeValues.NOW;
    }
    if (nowTicks == 0) {
      nowTicks = clock.ticks();
    }
    return nowTicks + clock.toTicks(
      java.time.Duration.ofMillis(d.getTimeUnit().toMillis(d.getDurationAmount())));
  }

}
