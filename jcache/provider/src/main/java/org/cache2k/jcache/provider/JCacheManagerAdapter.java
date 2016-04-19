package org.cache2k.jcache.provider;

/*
 * #%L
 * cache2k JCache JSR107 implementation
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.integration.CacheWriter;
import org.cache2k.customization.ExpiryCalculator;
import org.cache2k.integration.ExceptionPropagator;
import org.cache2k.impl.CacheLifeCycleListener;
import org.cache2k.impl.CacheManagerImpl;
import org.cache2k.impl.InternalCache;
import org.cache2k.jcache.provider.generic.storeByValueSimulation.CopyCacheProxy;
import org.cache2k.jcache.provider.generic.storeByValueSimulation.ObjectCopyFactory;
import org.cache2k.jcache.provider.generic.storeByValueSimulation.ObjectTransformer;
import org.cache2k.jcache.provider.generic.storeByValueSimulation.RuntimeCopyTransformer;
import org.cache2k.jcache.provider.generic.storeByValueSimulation.SimpleObjectCopyFactory;

import javax.cache.Cache;
import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.Duration;
import javax.cache.expiry.EternalExpiryPolicy;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.expiry.ModifiedExpiryPolicy;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheLoaderException;
import javax.cache.spi.CachingProvider;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;

/**
 * @author Jens Wilke; created: 2015-03-27
 */
public class JCacheManagerAdapter implements CacheManager {

  static JCacheJmxSupport jmxSupport;

  static {
    for (CacheLifeCycleListener l : ServiceLoader.load(CacheLifeCycleListener.class)) {
      if (l instanceof JCacheJmxSupport) {
        jmxSupport = (JCacheJmxSupport) l;
      }
    }
  }

  org.cache2k.CacheManager manager;
  Cache2kCachingProvider provider;
  Map<String, Cache> name2adapter = new HashMap<String, Cache>();

  /**
   * Needed for event delivery to find the corresponding JCache for a cache2k cache.
   */
  volatile Map<org.cache2k.Cache, Cache> c2k2jCache = Collections.emptyMap();

  public JCacheManagerAdapter(Cache2kCachingProvider p, org.cache2k.CacheManager cm) {
    manager = cm;
    provider = p;
  }

  @Override
  public CachingProvider getCachingProvider() {
    return provider;
  }

  @Override
  public URI getURI() {
    return provider.name2Uri(manager.getName());
  }

  @Override
  public ClassLoader getClassLoader() {
    return manager.getClassLoader();
  }

  @Override
  public Properties getProperties() {
    return manager.getProperties();
  }

  @Override
  public <K, V, C extends Configuration<K, V>> Cache<K, V> createCache(String _cacheName, C cfg)
    throws IllegalArgumentException {
    checkClosed();
    checkNonNullCacheName(_cacheName);
    if (cfg instanceof CompleteConfiguration) {
      CompleteConfiguration<K, V> cc = (CompleteConfiguration<K, V>) cfg;
      if (hasSpecialExpiryPolicy(cc) || hasAlwaysSpecialExpiry()) {
        return createCacheWithSpecialExpiry(_cacheName, cc);
      }
    }
    if (hasAlwaysSpecialExpiry()) {
      MutableConfiguration<K, V> cc = new MutableConfiguration<K, V>();
      cc.setTypes(cfg.getKeyType(), cfg.getValueType());
      cc.setStoreByValue(cfg.isStoreByValue());
      return createCacheWithSpecialExpiry(_cacheName, cc);
    }
    Cache2kBuilder b = Cache2kBuilder.of(cfg.getKeyType(), cfg.getValueType());
    b.name(_cacheName);
    b.eternal(true);
    b.keepValueAfterExpired(false);
    MutableConfiguration<K, V> _cfgCopy = null;
    if (cfg instanceof CompleteConfiguration) {
      CompleteConfiguration<K, V> cc = (CompleteConfiguration<K, V>) cfg;
      _cfgCopy = new MutableConfiguration<K, V>();
      _cfgCopy.setTypes(cc.getKeyType(), cc.getValueType());
      _cfgCopy.setStoreByValue(cc.isStoreByValue());

      if (cc.isReadThrough()) {
        throw new UnsupportedOperationException("no support for jsr107 read through operation");
      }
      if (cc.isWriteThrough()) {
        throw new UnsupportedOperationException("no support for jsr107 write through operation");
      }
      if (cc.getCacheEntryListenerConfigurations().iterator().hasNext()) {
        throw new UnsupportedOperationException("no support for jsr107 entry listener");
      }
      ExpiryPolicy _policy = cc.getExpiryPolicyFactory().create();
      if (_policy instanceof EternalExpiryPolicy) {
        b.eternal(true);
      } else if (_policy instanceof ModifiedExpiryPolicy) {
        Duration d = ((ModifiedExpiryPolicy) _policy).getExpiryForUpdate();
        b.expireAfterWrite(d.getDurationAmount(), d.getTimeUnit());
      } else {

        b.expiryCalculator(new ExpiryCalculatorAdapter(_policy));
        throw new RuntimeException("internal error, cannot happen");
      }
    }
    b.manager(manager);
    synchronized (((CacheManagerImpl) manager).getLockObject()) {
      Cache _jsr107cache = name2adapter.get(_cacheName);
      if (_jsr107cache != null && !_jsr107cache.isClosed()) {
        throw new CacheException("cache already existing with name: " + _cacheName);
      }
      org.cache2k.Cache _existingCache = manager.getCache(_cacheName);
      if (_existingCache != null && !_existingCache.isClosed()) {
        throw new CacheException("A cache2k instance is already existing with name: " + _cacheName);
      }
      JCacheAdapter<K, V> c = new JCacheAdapter<K, V>(this, b.build(), _cfgCopy);
      name2adapter.put(c.getName(), c);
      return c;
    }
  }

  boolean hasAlwaysSpecialExpiry() {
    return true;
  }

  <K, V> boolean hasSpecialExpiryPolicy(CompleteConfiguration<K, V> cc) {
    ExpiryPolicy _policy = cc.getExpiryPolicyFactory().create();
    if (_policy instanceof EternalExpiryPolicy) {
      return false;
    } else if (_policy instanceof ModifiedExpiryPolicy) {
      return false;
    }
    return true;
  }

  @SuppressWarnings("unchecked")
  <K, V, C extends Configuration<K, V>> Cache<K, V> createCacheWithSpecialExpiry(String _cacheName, CompleteConfiguration<K, V> cc)
    throws IllegalArgumentException {
    Cache2kBuilder b = Cache2kBuilder.of(cc.getKeyType(), TouchyJCacheAdapter.TimeVal.class);
    b.name(_cacheName);
    b.sharpExpiry(true);
    b.keepValueAfterExpired(false);
    b.exceptionPropagator(new ExceptionPropagator() {
      @Override
      public void propagateException(String _additionalMessage, Throwable _originalException) {
        throw new CacheLoaderException(_additionalMessage, _originalException);
      }
    });
    MutableConfiguration<K, V> _cfgCopy = new MutableConfiguration<K, V>();
    _cfgCopy.setTypes(cc.getKeyType(), cc.getValueType());
    _cfgCopy.setStoreByValue(cc.isStoreByValue());
    _cfgCopy.setReadThrough(cc.isReadThrough());
    _cfgCopy.setWriteThrough(cc.isWriteThrough());
    if (cc.getCacheLoaderFactory() != null) {
      final CacheLoader<K, V> clf = cc.getCacheLoaderFactory().create();
      b.loader(new org.cache2k.integration.CacheLoader<K,TouchyJCacheAdapter.TimeVal>() {
        @Override
        public TouchyJCacheAdapter.TimeVal load(K k) {
          V v = clf.load(k);
          if (v == null) {
            return null;
          }
          return new TouchyJCacheAdapter.TimeVal<V>(v);
        }
      });
    }
    if (cc.isWriteThrough()) {
      final javax.cache.integration.CacheWriter<? super K, ? super V> cw = cc.getCacheWriterFactory().create();
      b.writer(new CacheWriterAdapterForTouchRecording(cw));
    }
    ExpiryPolicy _policy = EternalExpiryPolicy.factoryOf().create();
    if (cc.getExpiryPolicyFactory() != null) {
      _policy = cc.getExpiryPolicyFactory().create();
    }
    b.expiryCalculator(new TouchyJCacheAdapter.ExpiryCalculatorAdapter(_policy));
    b.eternal(true);
    b.manager(manager);
    synchronized (((CacheManagerImpl) manager).getLockObject()) {
      Cache _jsr107cache = name2adapter.get(_cacheName);
      if (_jsr107cache != null && !_jsr107cache.isClosed()) {
        throw new CacheException("cache already existing with name: " + _cacheName);
      }
      org.cache2k.Cache _existingCache = manager.getCache(_cacheName);
      if (_existingCache != null && !_existingCache.isClosed()) {
        throw new CacheException("A cache2k instance is already existing with name: " + _cacheName);
      }

      TouchyJCacheAdapter.EventHandling<K,V> _eventHandling = new TouchyJCacheAdapter.EventHandling<K, V>();
      _eventHandling.registerCache2kListeners(b);
      for (CacheEntryListenerConfiguration<K,V> cfg : cc.getCacheEntryListenerConfigurations()) {
        _eventHandling.registerListener(cfg);
      }
      _eventHandling.init(this, Executors.newCachedThreadPool());

      JCacheAdapter<K, TouchyJCacheAdapter.TimeVal<V>> ca =
        new JCacheAdapter<K, TouchyJCacheAdapter.TimeVal<V>>(this, b.build(), null);
      if (cc.getCacheLoaderFactory() != null) {
        ca.loaderConfigured = true;
      }
      ca.readThrough = cc.isReadThrough();
      TouchyJCacheAdapter<K, V> c = new TouchyJCacheAdapter<K, V>();
      c.cache = ca;
      c.valueType = cc.getValueType();
      c.keyType = cc.getKeyType();
      c.expiryPolicy = _policy;
      c.c2kCache = (InternalCache<K, TouchyJCacheAdapter.TimeVal<V>>) ca.cache;
      _jsr107cache = c;
      c.eventHandling = _eventHandling;

      if (cc.isStoreByValue()) {
        ca.storeByValue = true;
        final ObjectTransformer<K, K> _keyTransformer = createCopyTransformer(cc.getKeyType());
        final ObjectTransformer<V, V> _valueTransformer = createCopyTransformer(cc.getValueType());
        _jsr107cache =
          new CopyCacheProxy(
            _jsr107cache,
            _keyTransformer,
            _valueTransformer);
      }

      Map<org.cache2k.Cache, Cache> _cloneC2k2jCache = new WeakHashMap<org.cache2k.Cache, Cache>();
      _cloneC2k2jCache.putAll(c2k2jCache);
      _cloneC2k2jCache.put(c.c2kCache, _jsr107cache);
      c2k2jCache = _cloneC2k2jCache;
      name2adapter.put(c.getName(), _jsr107cache);
      if (cc.isStatisticsEnabled()) {
        enableStatistics(c.getName(), true);
      }
      if (cc.isManagementEnabled()) {
        enableManagement(c.getName(), true);
      }
      return _jsr107cache;
    }
  }

  @SuppressWarnings("unchecked")
  private <K, V> ObjectTransformer<K, K> createCopyTransformer(final Class<K> _type) {
    ObjectCopyFactory f = new SimpleObjectCopyFactory();
    ObjectTransformer<K, K> _keyTransformer;
    _keyTransformer = f.createCopyTransformer(_type);
    if (_keyTransformer == null) {
      _keyTransformer = (ObjectTransformer<K, K>) new RuntimeCopyTransformer();
    }
    return _keyTransformer;
  }

  @Override @SuppressWarnings("unchecked")
  public <K, V> Cache<K, V> getCache(String _cacheName, final Class<K> _keyType, final Class<V> _valueType) {
    checkClosed();
    synchronized (((CacheManagerImpl) manager).getLockObject()) {
      Cache<K, V> c = name2adapter.get(_cacheName);
      if (c != null && manager.getCache(_cacheName) == c.unwrap(org.cache2k.Cache.class) && !c.isClosed()) {
        Configuration cfg = c.getConfiguration(Configuration.class);
        if (!cfg.getKeyType().equals(_keyType)) {
          if (_keyType.equals(Object.class)) {
            throw new IllegalArgumentException("Available cache by requested name has runtime type parameters.");
          }
          throw new ClassCastException("key type mismatch, expected: " + cfg.getKeyType().getName());
        }
        if (!cfg.getValueType().equals(_valueType)) {
          if (_valueType.equals(Object.class)) {
            throw new IllegalArgumentException("Available cache by requested name has runtime type parameters.");
          }
          throw new ClassCastException("value type mismatch, expected: " + cfg.getValueType().getName());
        }
        return c;
      }

      return null;
    }
  }

  private JCacheAdapter getAdapter(String _name) {
    Cache ca = name2adapter.get(_name);
    if (ca instanceof CopyCacheProxy) {
      ca = ((CopyCacheProxy) ca).getWrappedCache();
    }
    if (ca instanceof TouchyJCacheAdapter) {
      return (JCacheAdapter) ((TouchyJCacheAdapter) ca).cache;
    }
    return (JCacheAdapter) ca;
  }

  private void checkNonNullCacheName(String _cacheName) {
    if (_cacheName == null) {
      throw new NullPointerException("cache name is null");
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public <K, V> Cache<K, V> getCache(String _cacheName) {
    return (Cache<K, V>) getCache(_cacheName, Object.class, Object.class);
  }

  @Override
  public Iterable<String> getCacheNames() {
    Set<String> _names = new HashSet<String>();
    for (org.cache2k.Cache c : manager) {
      _names.add(c.getName());
    }
    return Collections.unmodifiableSet(_names);
  }

  @Override
  public void destroyCache(String _cacheName) {
    checkClosed();
    checkNonNullCacheName(_cacheName);
    org.cache2k.Cache c = manager.getCache(_cacheName);
    if (c != null) {
      c.close();
    }
  }

  @Override
  public void enableManagement(String _cacheName, boolean enabled) {
    checkClosed();
    checkNonNullCacheName(_cacheName);
    JCacheAdapter ca = getAdapter(_cacheName);
    if (ca == null) {
      return;
    }
    Cache c = name2adapter.get(_cacheName);
    if (enabled) {
      if (!ca.configurationEnabled) {
        jmxSupport.enableConfiguration(ca.cache, c);
        ca.configurationEnabled = true;
      }
    } else {
      if (ca.configurationEnabled) {
        jmxSupport.disableConfiguration(ca.cache);
        ca.configurationEnabled = false;
      }
    }
  }

  private void checkClosed() {
    if (isClosed()) {
      throw new IllegalStateException("cache manager is closed");
    }
  }

  @Override
  public void enableStatistics(String _cacheName, boolean enabled) {
    checkClosed();
    checkNonNullCacheName(_cacheName);
    if (enabled) {
      JCacheAdapter ca = getAdapter(_cacheName);
      if (ca != null) {
        synchronized (ca.cache) {
          if (!ca.statisticsEnabled) {
            jmxSupport.enableStatistics(ca);
            ca.statisticsEnabled = true;
          }
        }
      }
    } else {
      JCacheAdapter ca = getAdapter(_cacheName);
      if (ca != null) {
        synchronized (ca.cache) {
          if (ca.statisticsEnabled) {
            jmxSupport.disableStatistics(ca.cache);
            ca.statisticsEnabled = false;
          }
        }
      }
    }
  }

  @Override
  public void close() {
    manager.close();
  }

  @Override
  public boolean isClosed() {
    return manager.isClosed();
  }

  @Override
  public <T> T unwrap(Class<T> _class) {
    if (org.cache2k.CacheManager.class.isAssignableFrom(_class)) {
      return (T) manager;
    }
    throw new IllegalArgumentException("requested unwrap class not available");
  }

  /**
   * Return the JCache wrapper for a c2k cache.
   */
  public Cache resolveCacheWrapper(org.cache2k.Cache _c2kCache) {
    return c2k2jCache.get(_c2kCache);
  }

  static class ExpiryCalculatorAdapter<K, V> implements ExpiryCalculator<K, V> {

    ExpiryPolicy policy;

    public ExpiryCalculatorAdapter(ExpiryPolicy policy) {
      this.policy = policy;
    }

    @Override
    public long calculateExpiryTime(K _key, V _value, long _loadTime, CacheEntry<K, V> _oldEntry) {
      Duration d;
      if (_oldEntry == null) {
        d = policy.getExpiryForCreation();
      } else {
        d = policy.getExpiryForUpdate();
      }
      if (d == null) {
        throw new NullPointerException("JSR107 spec says null means, no change in duration, not supported");
      }
      if (d.equals(Duration.ETERNAL)) {
        return Long.MAX_VALUE;
      }
      if (d.equals(Duration.ZERO)) {
        return 0;
      }
      return _loadTime + d.getTimeUnit().toMillis(d.getDurationAmount());
    }
  }

  static class CacheWriterAdapterForTouchRecording<K, V> extends CacheWriter<K, TouchyJCacheAdapter.TimeVal<V>> {

    javax.cache.integration.CacheWriter<K, V> cacheWriter;

    public CacheWriterAdapterForTouchRecording(javax.cache.integration.CacheWriter<K, V> cacheWriter) {
      this.cacheWriter = cacheWriter;
    }

    @Override
    public void write(final K key, final TouchyJCacheAdapter.TimeVal<V> value) throws Exception {
      Cache.Entry<K, V> ce = new Cache.Entry<K, V>() {
        @Override
        public K getKey() {
          return key;
        }

        @Override
        public V getValue() {
          return value.value;
        }

        @Override
        public <T> T unwrap(Class<T> clazz) {
          throw new UnsupportedOperationException("unwrap entry not supported");
        }
      };
      cacheWriter.write(ce);
    }

    @Override
    public void delete(Object key) throws Exception {
      cacheWriter.delete(key);
    }

  }

}
