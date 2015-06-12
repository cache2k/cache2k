package org.cache2k.jcache;

/*
 * #%L
 * cache2k JCache JSR107 implementation
 * %%
 * Copyright (C) 2000 - 2015 headissue GmbH, Munich
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

import org.cache2k.CacheBuilder;
import org.cache2k.impl.CacheManagerImpl;

import javax.cache.Cache;
import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.Duration;
import javax.cache.expiry.EternalExpiryPolicy;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.expiry.ModifiedExpiryPolicy;
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
public class Cache2kManagerAdapter implements CacheManager {

  org.cache2k.CacheManager manager;
  Cache2kCachingProvider provider;
  Map<String, Cache2kCacheAdapter> name2adapter = new HashMap<String, Cache2kCacheAdapter>();

  public Cache2kManagerAdapter(Cache2kCachingProvider p, org.cache2k.CacheManager cm) {
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
    CacheBuilder b = CacheBuilder.newCache(cfg.getKeyType(), cfg.getValueType());
    b.name(_cacheName);
    b.eternal(true);
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
      if (_policy.equals(EternalExpiryPolicy.factoryOf().create())) {
        b.eternal(true);
      } else if (_policy instanceof ModifiedExpiryPolicy) {
        Duration d = ((ModifiedExpiryPolicy) _policy).getExpiryForUpdate();
        b.expiryDuration(d.getDurationAmount(), d.getTimeUnit());
      } else {
        throw new UnsupportedOperationException("no support for exipry policy: " + _policy.getClass());
      }
    }
    b.manager(manager);
    synchronized (((CacheManagerImpl)manager).getLockObject()) {
      Cache _jsr107cache = name2adapter.get(_cacheName);
      if (_jsr107cache != null && !_jsr107cache.isClosed()) {
        throw new CacheException("cache already existing with name: " + _cacheName);
      }
      org.cache2k.Cache _existingCache = manager.getCache(_cacheName);
      if (_existingCache != null && !_existingCache.isClosed()) {
        throw new CacheException("A cache2k instance is already existing with name: " + _cacheName);
      }
      Cache2kCacheAdapter<K, V> c = new Cache2kCacheAdapter<K, V>(this, b.build(), cfg.isStoreByValue(), _cfgCopy);
      name2adapter.put(c.getName(), c);
      return c;
    }
  }

  @Override
  public <K, V> Cache<K, V> getCache(String _cacheName, final Class<K> _keyType, final Class<V> _valueType) {
    checkClosed();
    synchronized (((CacheManagerImpl)manager).getLockObject()) {
      Cache2kCacheAdapter<K, V> c = name2adapter.get(_cacheName);
      if (c != null && manager.getCache(_cacheName) == c.cache && !c.isClosed()) {
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

}
