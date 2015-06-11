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

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.expiry.Duration;
import javax.cache.expiry.EternalExpiryPolicy;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.expiry.ModifiedExpiryPolicy;
import javax.cache.spi.CachingProvider;
import java.net.URI;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * @author Jens Wilke; created: 2015-03-27
 */
public class Cache2kManagerAdapter implements CacheManager {

  org.cache2k.CacheManager manager;
  Cache2kCachingProvider provider;

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
    CacheBuilder b = CacheBuilder.newCache(cfg.getKeyType(), cfg.getValueType());
    b.eternal(true);
    if (cfg instanceof CompleteConfiguration) {
      CompleteConfiguration<K, V> cc = (CompleteConfiguration<K, V>) cfg;
      if (cc.isManagementEnabled()) {
        throw new UnsupportedOperationException("no support for jsr107 management");
      }
      if (cc.isStatisticsEnabled()) {
        throw new UnsupportedOperationException("no support for jsr107 statistics");
      }
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
    return new Cache2kCacheAdapter<K, V>(this, b.build());
  }

  @Override
  public <K, V> Cache<K, V> getCache(String cacheName, Class<K> keyType, Class<V> valueType) {
    return null;
  }

  @Override
  public <K, V> Cache<K, V> getCache(String cacheName) {
    return null;
  }

  @Override
  public Iterable<String> getCacheNames() {
    Set<String> _names = new HashSet<String>();
    for (org.cache2k.Cache c : manager) {
      if (!c.isClosed()) {
        _names.add(c.getName());
      }
    }
    return _names;
  }

  @Override
  public void destroyCache(String cacheName) {
    manager.getCache(cacheName).close();
  }

  @Override
  public void enableManagement(String cacheName, boolean enabled) {

  }

  @Override
  public void enableStatistics(String cacheName, boolean enabled) {

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
  public <T> T unwrap(Class<T> clazz) {
    return null;
  }

}
