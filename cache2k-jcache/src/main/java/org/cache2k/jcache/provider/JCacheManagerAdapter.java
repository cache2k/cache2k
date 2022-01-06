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

import org.cache2k.core.spi.CacheLifeCycleListener;
import org.cache2k.core.CacheManagerImpl;
import org.cache2k.jcache.provider.generic.storeByValueSimulation.CopyCacheProxy;

import javax.cache.Cache;
import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;
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
 * @author Jens Wilke
 */
public class JCacheManagerAdapter implements CacheManager {

  private static final JCacheJmxSupport JMX_SUPPORT = findJCacheJmxSupportInstance();

  /**
   * The JMX support is already created via the serviceloader
   */
  private static JCacheJmxSupport findJCacheJmxSupportInstance() {
    return JCacheJmxSupport.SINGLETON;
  }

  private final org.cache2k.CacheManager manager;
  private final JCacheProvider provider;

  /**
   * All caches. Currently closed caches stay in the map.
   * Guarded by getLockObject().
   */
  private final Map<String, Cache> name2adapter = new HashMap<String, Cache>();

  /**
   * Needed for event delivery to find the corresponding JCache for a cache2k cache.
   * Guarded by getLockObject().
   */
  private volatile Map<org.cache2k.Cache, Cache> c2k2jCache = Collections.emptyMap();

  private final Set<String> configuredCacheNames;

  public JCacheManagerAdapter(JCacheProvider p, org.cache2k.CacheManager cm) {
    manager = cm;
    provider = p;
    Set<String> names = new HashSet<String>();
    for (String s : manager.getConfiguredCacheNames()) {
      names.add(s);
    }
    configuredCacheNames = Collections.unmodifiableSet(names);
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

  public <K, V, C extends Configuration<K, V>> Cache<K, V> createCache(String cacheName, C cfg)
    throws IllegalArgumentException {
    checkClosed();
    checkNonNullCacheName(cacheName);
    synchronized (getLockObject()) {
      Cache jsr107cache = name2adapter.get(cacheName);
      if (jsr107cache != null && !jsr107cache.isClosed()) {
        throw new CacheException("cache already existing with name: " + cacheName);
      }
      org.cache2k.Cache existingCache = manager.getCache(cacheName);
      if (existingCache != null && !existingCache.isClosed()) {
        throw new CacheException("A cache2k instance is already existing with name: " + cacheName);
      }
      JCacheBuilder<K, V> builder = new JCacheBuilder<K, V>(cacheName, this);
      builder.setConfiguration(cfg);
      Cache<K, V> cache = builder.build();
      org.cache2k.Cache cache2k = cache.unwrap(org.cache2k.Cache.class);
      Map<org.cache2k.Cache, Cache> cloneC2k2jCache =
        new WeakHashMap<org.cache2k.Cache, Cache>(c2k2jCache);
      cloneC2k2jCache.put(cache2k, cache);
      c2k2jCache = cloneC2k2jCache;
      name2adapter.put(cache.getName(), cache);
      if (builder.isStatisticsEnabled()) {
        enableStatistics(cacheName, true);
      }
      if (builder.isManagementEnabled()) {
        enableManagement(cacheName, true);
      }
      return cache;
    }
  }

  private Object getLockObject() {
    return ((CacheManagerImpl) manager).getLockObject();
  }

  @Override @SuppressWarnings("unchecked")
  public <K, V> Cache<K, V> getCache(String cacheName, Class<K> keyType, Class<V> valueType) {
    if (keyType == null || valueType == null) {
      throw new NullPointerException();
    }
    Cache<K, V> c = getCache(cacheName);
    if (c == null) {
      return null;
    }
    Configuration cfg = c.getConfiguration(Configuration.class);
    if (!cfg.getKeyType().equals(keyType)) {
      throw new ClassCastException(
        "key type mismatch, expected: " + cfg.getKeyType().getName());
    }
    if (!cfg.getValueType().equals(valueType)) {
      throw new ClassCastException(
        "value type mismatch, expected: " + cfg.getValueType().getName());
    }
    return c;
  }

  private JCacheAdapter getAdapter(String name) {
    Cache ca = name2adapter.get(name);
    if (ca instanceof CopyCacheProxy) {
      ca = ((CopyCacheProxy) ca).getWrappedCache();
    }
    if (ca instanceof TouchyJCacheAdapter) {
      return ((TouchyJCacheAdapter) ca).cache;
    }
    return (JCacheAdapter) ca;
  }

  private void checkNonNullCacheName(String cacheName) {
    if (cacheName == null) {
      throw new NullPointerException("cache name is null");
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public <K, V> Cache<K, V> getCache(String cacheName) {
    checkClosed();
    checkNonNullCacheName(cacheName);
    synchronized (getLockObject()) {
      Cache<K, V> c = name2adapter.get(cacheName);
      if (c != null && manager.getCache(cacheName) == c.unwrap(org.cache2k.Cache.class) &&
        !c.isClosed()) {
        return c;
      }
      if (configuredCacheNames.contains(cacheName)) {
        return createCache(cacheName, new MutableConfiguration<K, V>());
      }
    }
    return null;
  }

  @Override
  public Iterable<String> getCacheNames() {
    checkClosed();
    Set<String> names = new HashSet<String>();
    for (org.cache2k.Cache c : manager.getActiveCaches()) {
      names.add(c.getName());
    }
    names.addAll(configuredCacheNames);
    return Collections.unmodifiableSet(names);
  }

  @Override
  public void destroyCache(String cacheName) {
    checkClosed();
    checkNonNullCacheName(cacheName);
    org.cache2k.Cache c = manager.getCache(cacheName);
    if (c != null) {
      c.close();
    }
  }

  @Override
  public void enableManagement(String cacheName, boolean enabled) {
    checkClosed();
    checkNonNullCacheName(cacheName);
    synchronized (getLockObject()) {
      JCacheAdapter ca = getAdapter(cacheName);
      if (ca == null) {
        return;
      }
      Cache c = name2adapter.get(cacheName);
      if (enabled) {
        if (!ca.jmxEnabled) {
          JMX_SUPPORT.enableJmx(ca.cache, c);
          ca.jmxEnabled = true;
        }
      } else {
        if (ca.jmxEnabled) {
          JMX_SUPPORT.disableJmx(ca.cache);
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
  public void enableStatistics(String cacheName, boolean enabled) {
    checkClosed();
    checkNonNullCacheName(cacheName);
    synchronized (getLockObject()) {
      if (enabled) {
        JCacheAdapter ca = getAdapter(cacheName);
        if (ca != null) {
          synchronized (ca.cache) {
            if (!ca.jmxStatisticsEnabled) {
              JMX_SUPPORT.enableStatistics(ca);
              ca.jmxStatisticsEnabled = true;
            }
          }
        }
      } else {
        JCacheAdapter ca = getAdapter(cacheName);
        if (ca != null) {
          synchronized (ca.cache) {
            if (ca.jmxStatisticsEnabled) {
              JMX_SUPPORT.disableStatistics(ca.cache);
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

  @SuppressWarnings("unchecked")
  @Override
  public <T> T unwrap(Class<T> type) {
    if (org.cache2k.CacheManager.class.isAssignableFrom(type)) {
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
  public Cache resolveCacheWrapper(org.cache2k.Cache c2kCache) {
    synchronized (getLockObject()) {
      return c2k2jCache.get(c2kCache);
    }
  }

}
