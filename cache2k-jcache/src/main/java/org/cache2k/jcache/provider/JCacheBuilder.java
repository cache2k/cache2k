package org.cache2k.jcache.provider;

/*
 * #%L
 * cache2k JCache provider
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
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
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.configuration.CacheType;
import org.cache2k.configuration.CustomizationReferenceSupplier;
import org.cache2k.core.Cache2kCoreProviderImpl;
import org.cache2k.core.CacheManagerImpl;
import org.cache2k.core.InternalCache2kBuilder;
import org.cache2k.event.CacheClosedListener;
import org.cache2k.integration.AdvancedCacheLoader;
import org.cache2k.integration.CacheWriter;
import org.cache2k.integration.ExceptionPropagator;
import org.cache2k.integration.ExceptionInformation;
import org.cache2k.jcache.ExtendedConfiguration;
import org.cache2k.jcache.ExtendedMutableConfiguration;
import org.cache2k.jcache.JCacheConfiguration;
import org.cache2k.jcache.provider.event.EventHandling;
import org.cache2k.jcache.provider.event.EventHandlingImpl;
import org.cache2k.jcache.provider.generic.storeByValueSimulation.CopyCacheProxy;
import org.cache2k.jcache.provider.generic.storeByValueSimulation.ObjectCopyFactory;
import org.cache2k.jcache.provider.generic.storeByValueSimulation.ObjectTransformer;
import org.cache2k.jcache.provider.generic.storeByValueSimulation.RuntimeCopyTransformer;
import org.cache2k.jcache.provider.generic.storeByValueSimulation.SimpleObjectCopyFactory;

import javax.cache.Cache;
import javax.cache.CacheException;
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
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Executors;

/**
 * Constructs a requested JCache.
 *
 * @author Jens Wilke
 */
public class JCacheBuilder<K, V> {

  private final String name;
  private final JCacheManagerAdapter manager;
  private boolean cache2kConfigurationWasProvided = false;
  private CompleteConfiguration<K, V> config;
  private Cache2kConfiguration<K, V> cache2kConfiguration;
  private JCacheConfiguration extraConfiguration = JCACHE_DEFAULTS;
  private CacheType<K> keyType;
  private CacheType<V> valueType;
  private ExpiryPolicy expiryPolicy;
  private Cache<K, V> createdCache;
  private EventHandling<K, V> eventHandling;
  private boolean needsTouchyWrapper;

  public JCacheBuilder(String name, JCacheManagerAdapter manager) {
    this.name = name;
    this.manager = manager;
  }

  @SuppressWarnings("unchecked")
  public void setConfiguration(Configuration<K, V> cfg) {
    if (cfg instanceof CompleteConfiguration) {
      config = (CompleteConfiguration<K, V>) cfg;
      if (cfg instanceof ExtendedConfiguration) {
        cache2kConfiguration = ((ExtendedConfiguration<K, V>) cfg).getCache2kConfiguration();
        if (cache2kConfiguration != null) {
          if (cache2kConfiguration.getName() != null &&
            !cache2kConfiguration.getName().equals(name)) {
            throw new IllegalArgumentException("cache name mismatch.");
          }
          cache2kConfigurationWasProvided = true;
        }
      }
    } else {
      MutableConfiguration<K, V> cfgCopy = new MutableConfiguration<K, V>();
      cfgCopy.setTypes(cfg.getKeyType(), cfg.getValueType());
      cfgCopy.setStoreByValue(cfg.isStoreByValue());
      config = cfgCopy;
    }
    if (cache2kConfiguration == null) {
      cache2kConfiguration =
        CacheManagerImpl.PROVIDER.getDefaultConfiguration(manager.getCache2kManager());
      if (cfg instanceof ExtendedMutableConfiguration) {
        ((ExtendedMutableConfiguration) cfg).setCache2kConfiguration(cache2kConfiguration);
      }
    }
    cache2kConfiguration.setName(name);
    Cache2kCoreProviderImpl.CACHE_CONFIGURATION_PROVIDER.augmentConfiguration(
      manager.getCache2kManager(), cache2kConfiguration);
    cache2kConfigurationWasProvided |= cache2kConfiguration.isExternalConfigurationPresent();
    if (cache2kConfigurationWasProvided) {
      extraConfiguration = CACHE2K_DEFAULTS;
      JCacheConfiguration extraConfigurationSpecified =
        cache2kConfiguration.getSections().getSection(JCacheConfiguration.class);
      if (extraConfigurationSpecified != null) {
        extraConfiguration = extraConfigurationSpecified;
      }
    }
  }

  public Cache<K, V> build() {
    setupTypes();
    setupDefaults();
    setupExceptionPropagator();
    setupCacheThrough();
    setupExpiryPolicy();
    setupEventHandling();
    buildAdapterCache();
    wrapForExpiryPolicy();
    wrapIfCopyIsNeeded();
    return createdCache;
  }

  public boolean isStatisticsEnabled() {
    return config.isStatisticsEnabled() || extraConfiguration.isEnableStatistics();
  }

  public boolean isManagementEnabled() {
    return config.isManagementEnabled() || extraConfiguration.isEnableManagement();
  }

  JCacheConfiguration getExtraConfiguration() {
    return extraConfiguration;
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
    if (!config.getKeyType().equals(Object.class) &&
      !config.getKeyType().equals(keyType.getType())) {
      throw new IllegalArgumentException(
        "Key type mismatch between JCache and cache2k configuration");
    }
    if (!config.getValueType().equals(Object.class) &&
      !config.getValueType().equals(valueType.getType())) {
      throw new IllegalArgumentException(
        "Value type mismatch between JCache and cache2k configuration");
    }
  }

  /**
   * Configure conservative defaults, if no cache2k configuration is available.
   */
  private void setupDefaults() {
    if (!cache2kConfigurationWasProvided) {
      cache2kConfiguration.setSharpExpiry(true);
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
    cache2kConfiguration.setExceptionPropagator(
      new CustomizationReferenceSupplier<ExceptionPropagator<K>>(new ExceptionPropagator<K>() {
      @Override
      public RuntimeException propagateException(
        Object key, ExceptionInformation exceptionInformation) {
        return new CacheLoaderException(
          "propagate previous loader exception", exceptionInformation.getException());
      }
    }));
  }

  abstract class CloseableLoader extends AdvancedCacheLoader<K, V> implements Closeable { }
  abstract class CloseableWriter extends CacheWriter<K, V> implements Closeable { }

  /**
   * Configure loader and writer.
   */
  private void setupCacheThrough() {
    if (config.getCacheLoaderFactory() != null) {
      final CacheLoader<K, V> clf = config.getCacheLoaderFactory().create();
      cache2kConfiguration.setAdvancedLoader(
        new CustomizationReferenceSupplier<AdvancedCacheLoader<K, V>>(
          new CloseableLoader() {

            @Override
            public void close() throws IOException {
              if (clf instanceof Closeable) {
                ((Closeable) clf).close();
              }
            }

            @Override
            public V load(K key, long startTime, CacheEntry<K, V> currentEntry) {
              return clf.load(key);
            }

          }));
    }
    if (config.getCacheWriterFactory() != null) {
      final javax.cache.integration.CacheWriter<? super K, ? super V> cw =
        config.getCacheWriterFactory().create();
      cache2kConfiguration.setWriter(
        new CustomizationReferenceSupplier<CacheWriter<K, V>>(new CloseableWriter() {
        @Override
        public void write(final K key, final V value) {
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
        public void delete(Object key) {
          cw.delete(key);
        }

        @Override
        public void close() throws IOException {
          if (cw instanceof Closeable) {
            ((Closeable) cw).close();
          }
        }
      }));
    }
  }

  /**
   * Register a expiry policy to cache2k.
   *
   * <p>JSR107 requires that null values are deleted from the cache. We register an expiry policy
   * to cache2k to provide this behavior.
   */
  private void setupExpiryPolicy() {
    if (cache2kConfiguration.getExpiryPolicy() != null) {
      org.cache2k.expiry.ExpiryPolicy<K, V> ep0;
      try {
        ep0 = cache2kConfiguration.getExpiryPolicy().supply(manager.getCache2kManager());
      } catch (Exception ex) {
        throw new CacheException("couldn't initialize expiry policy", ex);
      }
      final org.cache2k.expiry.ExpiryPolicy<K, V> ep = ep0;
      cache2kConfiguration.setExpiryPolicy(
        new CustomizationReferenceSupplier<org.cache2k.expiry.ExpiryPolicy<K, V>>(
        new org.cache2k.expiry.ExpiryPolicy<K, V>() {
        @Override
        public long calculateExpiryTime(
          K key, V value, long loadTime, CacheEntry<K, V> oldEntry) {
          if (value == null) {
            return NO_CACHE;
          }
          return ep.calculateExpiryTime(key, value, loadTime, oldEntry);
        }
      }));
      return;
    }
    if (config.getExpiryPolicyFactory() != null) {
      expiryPolicy = config.getExpiryPolicyFactory().create();
    }
    if (expiryPolicy == null || expiryPolicy instanceof EternalExpiryPolicy) {
      cache2kConfiguration.setExpiryPolicy(
        new CustomizationReferenceSupplier<org.cache2k.expiry.ExpiryPolicy<K, V>>(
          new org.cache2k.expiry.ExpiryPolicy<K, V>() {
        @Override
        public long calculateExpiryTime(K key, V value, long loadTime, CacheEntry<K, V> oldEntry) {
          if (value == null) {
            return NO_CACHE;
          }
          return ETERNAL;
        }
      }));
      return;
    }
    if (expiryPolicy instanceof ModifiedExpiryPolicy) {
      Duration d = expiryPolicy.getExpiryForCreation();
      final long millisDuration = d.getTimeUnit().toMillis(d.getDurationAmount());
      if (millisDuration == 0) {
        cache2kConfiguration.setExpiryPolicy(
          new CustomizationReferenceSupplier<org.cache2k.expiry.ExpiryPolicy<K, V>>(
            new org.cache2k.expiry.ExpiryPolicy<K, V>() {
          @Override
          public long calculateExpiryTime(
            K key, V value, long loadTime, CacheEntry<K, V> oldEntry) {
            return NO_CACHE;
          }
        }));
        return;
      }
      cache2kConfiguration.setExpiryPolicy(
        new CustomizationReferenceSupplier<org.cache2k.expiry.ExpiryPolicy<K, V>>(
          new org.cache2k.expiry.ExpiryPolicy<K, V>() {
        @Override
        public long calculateExpiryTime(K key, V value, long loadTime, CacheEntry<K, V> oldEntry) {
          if (value == null) {
            return NO_CACHE;
          }
          return loadTime + millisDuration;
        }
      }));
      return;
    }
    if (expiryPolicy instanceof CreatedExpiryPolicy) {
      cache2kConfiguration.setEternal(true);
      Duration d = expiryPolicy.getExpiryForCreation();
      final long millisDuration = d.getTimeUnit().toMillis(d.getDurationAmount());
      if (millisDuration == 0) {
        cache2kConfiguration.setExpiryPolicy(
          new CustomizationReferenceSupplier<org.cache2k.expiry.ExpiryPolicy<K, V>>(
            new org.cache2k.expiry.ExpiryPolicy<K, V>() {
          @Override
          public long calculateExpiryTime(
            K key, V value, long loadTime, CacheEntry<K, V> oldEntry) {
            return NO_CACHE;
          }
        }));
        return;
      }
      cache2kConfiguration.setExpiryPolicy(
        new CustomizationReferenceSupplier<org.cache2k.expiry.ExpiryPolicy<K, V>>(
          new org.cache2k.expiry.ExpiryPolicy<K, V>() {
        @Override
        public long calculateExpiryTime(K key, V value, long loadTime, CacheEntry<K, V> oldEntry) {
          if (value == null) {
            return NO_CACHE;
          }
          if (oldEntry == null) {
            return loadTime + millisDuration;
          } else {
            return NEUTRAL;
          }
        }
      }));
      return;
    }
    needsTouchyWrapper = true;
    cache2kConfiguration.setExpiryPolicy(
      new CustomizationReferenceSupplier<org.cache2k.expiry.ExpiryPolicy<K, V>>(
        new TouchyJCacheAdapter.ExpiryPolicyAdapter<K, V>(expiryPolicy)));
  }

  @SuppressWarnings("unchecked")
  private void setupEventHandling() {
    if ((config.getCacheEntryListenerConfigurations() == null ||
      !config.getCacheEntryListenerConfigurations().iterator().hasNext()) &&
      !extraConfiguration.isSupportOnlineListenerAttachment()) {
      eventHandling = (EventHandling<K, V>) EventHandling.DISABLED;
      return;
    }
    EventHandlingImpl<K, V> eventHandling =
      new EventHandlingImpl<K, V>(manager, Executors.newCachedThreadPool());
    eventHandling.addInternalListenersToCache2kConfiguration(cache2kConfiguration);
    for (CacheEntryListenerConfiguration<K, V> cfg : config.getCacheEntryListenerConfigurations()) {
      eventHandling.registerListener(cfg);
    }
    this.eventHandling = eventHandling;
    cache2kConfiguration.getCacheClosedListeners().add(
      new CustomizationReferenceSupplier<CacheClosedListener>(eventHandling));
  }

  private void buildAdapterCache() {
    createdCache =
      new JCacheAdapter<K, V>(
        manager,
        new InternalCache2kBuilder<K, V>(
          cache2kConfiguration, manager.getCache2kManager()).buildAsIs(),
        keyType.getType(), valueType.getType(),
        config.isStoreByValue(),
        config.isReadThrough() || extraConfiguration.isEnableReadThrough(),
        config.getCacheLoaderFactory() != null ||
          cache2kConfiguration.getLoader() != null ||
          cache2kConfiguration.getAdvancedLoader() != null ||
          cache2kConfiguration.getAsyncLoader() != null,
        eventHandling
      );
  }

  private void wrapForExpiryPolicy() {
    if (needsTouchyWrapper) {
      createdCache =
        new TouchyJCacheAdapter<K, V>((JCacheAdapter<K, V>) createdCache, expiryPolicy);
    }
  }

  private void wrapIfCopyIsNeeded() {
    if (extraConfiguration.isCopyAlwaysIfRequested() && config.isStoreByValue()) {
      ObjectTransformer<K, K> keyTransformer = createCopyTransformer(keyType);
      ObjectTransformer<V, V> valueTransformer = createCopyTransformer(valueType);
      createdCache =
        new CopyCacheProxy<K, V>(
          createdCache,
          keyTransformer,
          valueTransformer);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> ObjectTransformer<T, T> createCopyTransformer(CacheType<T> type) {
    ObjectCopyFactory f = new SimpleObjectCopyFactory();
    ObjectTransformer<T, T> keyTransformer =
      f.createCopyTransformer(type.getType(), manager.getClassLoader());
    if (keyTransformer == null) {
      keyTransformer =
        (ObjectTransformer<T, T>) new RuntimeCopyTransformer(manager.getClassLoader()); }
    return keyTransformer;
  }

  /**
   * Defaults to use if no cache2k configuration is provided.
   */
  private static final JCacheConfiguration JCACHE_DEFAULTS =
    new JCacheConfiguration.Builder()
      .copyAlwaysIfRequested(true)
      .supportOnlineListenerAttachment(true)
      .buildConfigurationSection();

  /**
   * Defaults to use if cache2k configuration is provided but no
   * extra JCacheConfiguration section is added.
   */
  private static final JCacheConfiguration CACHE2K_DEFAULTS =
    new JCacheConfiguration.Builder()
      .copyAlwaysIfRequested(false)
      .supportOnlineListenerAttachment(false)
      .buildConfigurationSection();

}
