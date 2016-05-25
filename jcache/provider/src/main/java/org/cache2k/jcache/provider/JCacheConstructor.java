package org.cache2k.jcache.provider;

/*
 * #%L
 * cache2k JSR107 support
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
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.configuration.CacheType;
import org.cache2k.integration.CacheWriter;
import org.cache2k.integration.ExceptionPropagator;
import org.cache2k.integration.ExceptionInformation;
import org.cache2k.jcache.ExtendedConfiguration;
import org.cache2k.jcache.JCacheConfiguration;
import org.cache2k.jcache.provider.event.EventHandling;
import org.cache2k.jcache.provider.generic.storeByValueSimulation.CopyCacheProxy;
import org.cache2k.jcache.provider.generic.storeByValueSimulation.ObjectCopyFactory;
import org.cache2k.jcache.provider.generic.storeByValueSimulation.ObjectTransformer;
import org.cache2k.jcache.provider.generic.storeByValueSimulation.RuntimeCopyTransformer;
import org.cache2k.jcache.provider.generic.storeByValueSimulation.SimpleObjectCopyFactory;

import javax.cache.Cache;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.expiry.EternalExpiryPolicy;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.expiry.ModifiedExpiryPolicy;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheLoaderException;
import java.util.concurrent.Executors;

/**
 * Constructs a requested JCache.
 *
 * @author Jens Wilke
 */
public class JCacheConstructor<K,V> {

  private String name;
  private JCacheManagerAdapter manager;
  private boolean cache2kConfigurationWasProvided = false;
  private CompleteConfiguration<K,V> config;
  private Cache2kConfiguration<K,V> cache2kConfiguration;
  private JCacheConfiguration extraConfiguration = JCACHE_DEFAULTS;
  private CacheType<K> keyType;
  private CacheType<V> valueType;
  private ExpiryPolicy expiryPolicy;
  private Cache<K,V> createdCache;
  private EventHandling<K,V> eventHandling;
  private boolean needsTouchyWrapper;

  public JCacheConstructor(String _name, JCacheManagerAdapter _manager) {
    name = _name;
    manager = _manager;
  }

  public void setConfiguration(Configuration<K,V> cfg) {
    if (cfg instanceof CompleteConfiguration) {
      config = (CompleteConfiguration<K,V>) cfg;
      if (cfg instanceof ExtendedConfiguration) {
        cache2kConfiguration = ((ExtendedConfiguration<K,V>) cfg).getCache2kConfiguration();
        if (cache2kConfiguration != null) {
          cache2kConfigurationWasProvided = true;
          extraConfiguration = CACHE2K_DEFAULTS;
          JCacheConfiguration _extraConfigurationSpecified =
            cache2kConfiguration.getSections().getSection(JCacheConfiguration.class);
          if (_extraConfigurationSpecified != null) {
            extraConfiguration = _extraConfigurationSpecified;
          }
        }
      }
    } else {
      MutableConfiguration<K, V> _cfgCopy = new MutableConfiguration<K, V>();
      _cfgCopy.setTypes(cfg.getKeyType(), cfg.getValueType());
      _cfgCopy.setStoreByValue(cfg.isStoreByValue());
      config = _cfgCopy;
    }
    if (cache2kConfiguration == null) {
      cache2kConfiguration = new Cache2kConfiguration<K, V>();
    }
  }

  public Cache<K,V> build() {
    setupTypes();
    setupName();
    setupDefaults();
    checkConfiguration();
    setupExceptionPropagator();
    setupCacheThrough();
    setupExpiryPolicy();
    setupEventHandling();
    buildAdapterCache();
    wrapForExpiryPolicy();
    wrapIfCopyIsNeeded();
    return createdCache;
  }

  public CompleteConfiguration<K,V> getCompleteConfiguration() {
    return config;
  }

  /**
   * If there is a cache2k configuration, we take the types from there.
   */
  private void setupTypes() {
    if (!cache2kConfigurationWasProvided) {
      cache2kConfiguration.setKeyType(config.getKeyType());
      cache2kConfiguration.setValueType(config.getValueType());
    } else {
      if (cache2kConfiguration.getKeyType() == null) {
        cache2kConfiguration.setKeyType(config.getKeyType());
      }
      if (cache2kConfiguration.getValueType() == null) {
        cache2kConfiguration.setValueType(config.getValueType());
      }
    }
    keyType = cache2kConfiguration.getKeyType();
    valueType = cache2kConfiguration.getValueType();
    if (!config.getKeyType().equals(Object.class) && !config.getKeyType().equals(keyType.getType())) {
      throw new IllegalArgumentException("Key type mismatch between JCache and cache2k configuration");
    }
    if (!config.getValueType().equals(Object.class) && !config.getValueType().equals(valueType.getType())) {
      throw new IllegalArgumentException("Value type mismatch between JCache and cache2k configuration");
    }
  }

  private void setupName() {
    if (cache2kConfiguration.getName() != null && !cache2kConfiguration.getName().equals(name)) {
      throw new IllegalArgumentException("cache name mismatch.");
    }
    cache2kConfiguration.setName(name);
  }

  /**
   * Configure conservative defaults, if no cache2k configuration is available.
   */
  private void setupDefaults() {
    if (!cache2kConfigurationWasProvided) {
      cache2kConfiguration.setSharpExpiry(true);
      cache2kConfiguration.setKeepDataAfterExpired(false);
    }
  }

  private void checkConfiguration() {
    if (!config.isStoreByValue() && extraConfiguration.isCopyAlwaysIfRequested()) {
      throw new IllegalArgumentException(
        "Store by reference requested via JCache configuration but copy requested " +
          "via extra cache2k configuration.");
    }
  }


  /**
   * If an exception propagator is configured, take this one, otherwise go with default that
   * is providing JCache compatible behavior.
   */
  private void setupExceptionPropagator() {
    if (cache2kConfiguration.getExceptionPropagator() != null) {
      return;
    }
    cache2kConfiguration.setExceptionPropagator(new ExceptionPropagator() {
      @Override
      public RuntimeException propagateException(Object key, final ExceptionInformation exceptionInformation) {
        return new CacheLoaderException("propagate previous loader exception", exceptionInformation.getException());
      }
    });
  }

  /**
   * Configure loader and writer.
   */
  private void setupCacheThrough() {
    if (config.getCacheLoaderFactory() != null) {
      final CacheLoader<K, V> clf = config.getCacheLoaderFactory().create();
      cache2kConfiguration.setLoader(new org.cache2k.integration.CacheLoader<K,V>() {
        @Override
        public V load(K k) {
          return clf.load(k);
        }
      });
    }
    if (config.isWriteThrough()) {
      final javax.cache.integration.CacheWriter<? super K, ? super V> cw = config.getCacheWriterFactory().create();
      cache2kConfiguration.setWriter(new CacheWriter<K,V>() {
        @Override
        public void write(final K key, final V value) throws Exception {
          Cache.Entry<K, V> ce = new Cache.Entry<K, V>() {
            @Override
            public K getKey() {
              return key;
            }

            @Override
            public V getValue() {
              return value;
            }

            @Override
            public <T> T unwrap(Class<T> clazz) {
              throw new UnsupportedOperationException("unwrap entry not supported");
            }
          };
          cw.write(ce);
        }

        @Override
        public void delete(final Object key) throws Exception {
          cw.delete(key);
        }
      });
    }
  }

  /**
   * Register a expiry policy to cache2k.
   *
   * <p>JSR107 requires that null values are deleted from the cache. We register an expiry policy
   * to cache2k to provide this behavior.
   */
  private void setupExpiryPolicy() {
    if (cache2kConfigurationWasProvided) {
      final org.cache2k.expiry.ExpiryPolicy<K,V> ep = cache2kConfiguration.getExpiryPolicy();
      if (ep != null) {
        cache2kConfiguration.setExpiryPolicy(new org.cache2k.expiry.ExpiryPolicy<K, V>() {
          @Override
          public long calculateExpiryTime(final K key, final V value, final long loadTime, final CacheEntry<K, V> oldEntry) {
            if (value == null) {
              return NO_CACHE;
            }
            return ep.calculateExpiryTime(key, value, loadTime, oldEntry);
          }
        });
        return;
      }
      if (cache2kConfiguration.getExpireAfterWriteMillis() >= 0) {
        return;
      }
    }
    cache2kConfiguration.setEternal(true);
    if (config.getExpiryPolicyFactory() != null) {
      expiryPolicy = config.getExpiryPolicyFactory().create();
    }
    if (expiryPolicy == null || expiryPolicy instanceof EternalExpiryPolicy) {
      cache2kConfiguration.setExpiryPolicy(new org.cache2k.expiry.ExpiryPolicy<K, V>() {
        @Override
        public long calculateExpiryTime(final K key, final V value, final long loadTime, final CacheEntry<K, V> oldEntry) {
          if (value == null) {
            return NO_CACHE;
          }
          return ETERNAL;
        }
      });
      return;
    }
    if (expiryPolicy instanceof ModifiedExpiryPolicy) {
      Duration d = expiryPolicy.getExpiryForCreation();
      final long _millisDuration = d.getTimeUnit().toMillis(d.getDurationAmount());
      if (_millisDuration == 0) {
        cache2kConfiguration.setExpiryPolicy(new org.cache2k.expiry.ExpiryPolicy<K, V>() {
          @Override
          public long calculateExpiryTime(final K key, final V value, final long loadTime, final CacheEntry<K, V> oldEntry) {
            return NO_CACHE;
          }
        });
        return;
      }
      cache2kConfiguration.setExpiryPolicy(new org.cache2k.expiry.ExpiryPolicy<K, V>() {
        @Override
        public long calculateExpiryTime(final K key, final V value, final long loadTime, final CacheEntry<K, V> oldEntry) {
          if (value == null) {
            return NO_CACHE;
          }
          return loadTime + _millisDuration;
        }
      });
      return;
    }
    if (expiryPolicy instanceof CreatedExpiryPolicy) {
      cache2kConfiguration.setEternal(true);
      Duration d = expiryPolicy.getExpiryForCreation();
      final long _millisDuration = d.getTimeUnit().toMillis(d.getDurationAmount());
      if (_millisDuration == 0) {
        cache2kConfiguration.setExpiryPolicy(new org.cache2k.expiry.ExpiryPolicy<K, V>() {
          @Override
          public long calculateExpiryTime(final K key, final V value, final long loadTime, final CacheEntry<K, V> oldEntry) {
            return NO_CACHE;
          }
        });
        return;
      }
      cache2kConfiguration.setExpiryPolicy(new org.cache2k.expiry.ExpiryPolicy<K, V>() {
        @Override
        public long calculateExpiryTime(final K key, final V value, final long loadTime, final CacheEntry<K, V> oldEntry) {
          if (value == null) {
            return NO_CACHE;
          }
          if (oldEntry == null) {
            return loadTime + _millisDuration;
          } else {
            return NEUTRAL;
          }
        }
      });
      return;
    }
    needsTouchyWrapper = true;
    cache2kConfiguration.setExpiryPolicy(new TouchyJCacheAdapter.ExpiryPolicyAdapter<K,V>(expiryPolicy));
  }

  private void setupEventHandling() {
    eventHandling = new EventHandling<K, V>();
    eventHandling.registerCache2kListeners(cache2kConfiguration);
    for (CacheEntryListenerConfiguration<K,V> cfg : config.getCacheEntryListenerConfigurations()) {
      eventHandling.registerListener(cfg);
    }
    eventHandling.init(manager, Executors.newCachedThreadPool());
  }

  private void buildAdapterCache() {
    JCacheAdapter<K, V> _adapter =
      new JCacheAdapter<K, V>(
        manager,
        Cache2kBuilder.of(cache2kConfiguration).manager(manager.getCache2kManager()).build());
    _adapter.valueType = valueType.getType();
    _adapter.keyType = keyType.getType();
    if (config.getCacheLoaderFactory() != null) {
      _adapter.loaderConfigured = true;
    }
    _adapter.readThrough = config.isReadThrough();
    _adapter.eventHandling = eventHandling;
    if (config.isStoreByValue()) {
      _adapter.storeByValue = true;
    }
    createdCache = _adapter;
  }

  private void wrapForExpiryPolicy() {
    if (needsTouchyWrapper) {
      createdCache = new TouchyJCacheAdapter<K, V>((JCacheAdapter<K, V>) createdCache, expiryPolicy);
    }
  }

  private void wrapIfCopyIsNeeded() {
    if (extraConfiguration.isCopyAlwaysIfRequested() && config.isStoreByValue()) {
      final ObjectTransformer<K, K> _keyTransformer = createCopyTransformer(keyType);
      final ObjectTransformer<V, V> _valueTransformer = createCopyTransformer(valueType);
      createdCache =
        new CopyCacheProxy<K,V>(
          createdCache,
          _keyTransformer,
          _valueTransformer);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> ObjectTransformer<T, T> createCopyTransformer(final CacheType<T> _type) {
    ObjectCopyFactory f = new SimpleObjectCopyFactory();
    ObjectTransformer<T, T> _keyTransformer = f.createCopyTransformer(_type.getType(), manager.getClassLoader());
    if (_keyTransformer == null) {
      _keyTransformer = (ObjectTransformer<T, T>) new RuntimeCopyTransformer(manager.getClassLoader());
    }
    return _keyTransformer;
  }

  /**
   * Defaults to use if no cache2k configuration is provided.
   */
  private static final JCacheConfiguration JCACHE_DEFAULTS =
    new JCacheConfiguration.Builder()
      .alwaysFlushJmxStatistics(true)
      .copyAlwaysIfRequested(true)
      .buildConfigurationSection();

  /**
   * Defaults to use if cache2k configuration is provided but no
   * extra JCacheConfiguration section is added.
   */
  private static final JCacheConfiguration CACHE2K_DEFAULTS =
    new JCacheConfiguration.Builder()
      .alwaysFlushJmxStatistics(false)
      .copyAlwaysIfRequested(false)
      .buildConfigurationSection();

}
