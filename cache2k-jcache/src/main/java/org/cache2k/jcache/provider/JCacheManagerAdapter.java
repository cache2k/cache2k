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

import org.cache2k.core.spi.CacheLifeCycleListener;
import org.cache2k.core.CacheManagerImpl;
import org.cache2k.jcache.provider.generic.storeByValueSimulation.CopyCacheProxy;

import javax.cache.Cache;
import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.configuration.Configuration;
import javax.cache.spi.CachingProvider;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * @author Jens Wilke; created: 2015-03-27
 */
public class JCacheManagerAdapter implements CacheManager {

  private final static JCacheJmxSupport jmxSupport = findJCacheJmxSupportInstance();

  /**
   * The JMX support is already created via the serviceloader
   */
  private static JCacheJmxSupport findJCacheJmxSupportInstance() {
    for (CacheLifeCycleListener l : CacheManagerImpl.getCacheLifeCycleListeners()) {
      if (l instanceof JCacheJmxSupport) {
        return (JCacheJmxSupport) l;
      }
    }
    return null;
  }

  private org.cache2k.CacheManager manager;
  private JCacheProvider provider;

  /**
   * All caches. Currently closed caches stay in the map.
   * Guarded by getLockObject().
   */
  private Map<String, Cache> name2adapter = new HashMap<String, Cache>();

  /**
   * Needed for event delivery to find the corresponding JCache for a cache2k cache.
   * Guarded by getLockObject().
   */
  private volatile Map<org.cache2k.Cache, Cache> c2k2jCache = Collections.emptyMap();

  public JCacheManagerAdapter(JCacheProvider p, org.cache2k.CacheManager cm) {
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

  public <K, V, C extends Configuration<K, V>> Cache<K, V> createCache(String _cacheName, C cfg)
    throws IllegalArgumentException {
    checkClosed();
    checkNonNullCacheName(_cacheName);
    synchronized (getLockObject()) {
      Cache _jsr107cache = name2adapter.get(_cacheName);
      if (_jsr107cache != null && !_jsr107cache.isClosed()) {
        throw new CacheException("cache already existing with name: " + _cacheName);
      }
      org.cache2k.Cache _existingCache = manager.getCache(_cacheName);
      if (_existingCache != null && !_existingCache.isClosed()) {
        throw new CacheException("A cache2k instance is already existing with name: " + _cacheName);
      }
      JCacheBuilder<K,V> _builder = new JCacheBuilder<K, V>(_cacheName, this);
      _builder.setConfiguration(cfg);
      Cache<K,V> _cache = _builder.build();
      org.cache2k.Cache _cache2k = _cache.unwrap(org.cache2k.Cache.class);
      Map<org.cache2k.Cache, Cache> _cloneC2k2jCache = new WeakHashMap<org.cache2k.Cache, Cache>();
      _cloneC2k2jCache.putAll(c2k2jCache);
      _cloneC2k2jCache.put(_cache2k, _cache);
      c2k2jCache = _cloneC2k2jCache;
      name2adapter.put(_cache.getName(), _cache);
      if (_builder.getCompleteConfiguration().isStatisticsEnabled()) {
        enableStatistics(_cacheName, true);
      }
      if (_builder.getCompleteConfiguration().isManagementEnabled()) {
        enableManagement(_cacheName, true);
      }
      return _cache;
    }
  }

  private Object getLockObject() {
    return ((CacheManagerImpl) manager).getLockObject();
  }

  @Override @SuppressWarnings("unchecked")
  public <K, V> Cache<K, V> getCache(String _cacheName, final Class<K> _keyType, final Class<V> _valueType) {
    if (_keyType == null || _valueType == null) {
      throw new NullPointerException();
    }
    Cache<K, V> c = getCache(_cacheName);
    if (c == null) {
      return null;
    }
    Configuration cfg = c.getConfiguration(Configuration.class);
    if (!cfg.getKeyType().equals(_keyType)) {
      throw new ClassCastException("key type mismatch, expected: " + cfg.getKeyType().getName());
    }
    if (!cfg.getValueType().equals(_valueType)) {
      throw new ClassCastException("value type mismatch, expected: " + cfg.getValueType().getName());
    }
    return c;
  }

  private JCacheAdapter getAdapter(String _name) {
    Cache ca = name2adapter.get(_name);
    if (ca instanceof CopyCacheProxy) {
      ca = ((CopyCacheProxy) ca).getWrappedCache();
    }
    if (ca instanceof TouchyJCacheAdapter) {
      return ((TouchyJCacheAdapter) ca).cache;
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
    checkClosed();
    checkNonNullCacheName(_cacheName);
    synchronized (getLockObject()) {
      Cache<K, V> c = name2adapter.get(_cacheName);
      if (c != null && manager.getCache(_cacheName) == c.unwrap(org.cache2k.Cache.class) && !c.isClosed()) {
        return c;
      }
    }
    return null;
  }

  @Override
  public Iterable<String> getCacheNames() {
    checkClosed();
    Set<String> _names = new HashSet<String>();
    for (org.cache2k.Cache c : manager.getActiveCaches()) {
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
    synchronized (getLockObject()) {
      JCacheAdapter ca = getAdapter(_cacheName);
      if (ca == null) {
        return;
      }
      Cache c = name2adapter.get(_cacheName);
      if (enabled) {
        if (!ca.jmxEnabled) {
          jmxSupport.enableJmx(ca.cache, c);
          ca.jmxEnabled = true;
        }
      } else {
        if (ca.jmxEnabled) {
          jmxSupport.disableJmx(ca.cache);
          ca.jmxEnabled = false;
        }
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
    synchronized (getLockObject()) {
      if (enabled) {
        JCacheAdapter ca = getAdapter(_cacheName);
        if (ca != null) {
          synchronized (ca.cache) {
            if (!ca.jmxStatisticsEnabled) {
              jmxSupport.enableStatistics(ca);
              ca.jmxStatisticsEnabled = true;
            }
          }
        }
      } else {
        JCacheAdapter ca = getAdapter(_cacheName);
        if (ca != null) {
          synchronized (ca.cache) {
            if (ca.jmxStatisticsEnabled) {
              jmxSupport.disableStatistics(ca.cache);
              ca.jmxStatisticsEnabled = false;
            }
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

  public org.cache2k.CacheManager getCache2kManager() {
    return manager;
  }

  /**
   * Return the JCache wrapper for a c2k cache.
   */
  public Cache resolveCacheWrapper(org.cache2k.Cache _c2kCache) {
    synchronized (getLockObject()) {
      return c2k2jCache.get(_c2kCache);
    }
  }

}
