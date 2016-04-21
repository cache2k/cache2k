package org.cache2k.jcache.provider.generic.storeByValueSimulation;

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

import javax.cache.Cache;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.Factory;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheWriter;

/**
 * Cache proxy that expects the transformers keep the type but copy the objects. Copying could be
 * done e.g. cloning and serialization or can be skipped in case of immutable types.
 *
 * @author Jens Wilke
 */
public class CopyCacheProxy<K, T> extends TransformingCacheProxy<K, T, K, T> {

  @SuppressWarnings("unchecked")
  public CopyCacheProxy(Cache<K, T> cache, ObjectTransformer<K, K> keyTransformer, ObjectTransformer<T, T> valueTransformer) {
    super(cache, keyTransformer, valueTransformer, ObjectTransformer.IDENT_TRANSFORM, ObjectTransformer.IDENT_TRANSFORM);
  }

  /**
   * Delegates to the wrapped cache. Wrap configuration and return true on store by value
   */
  @SuppressWarnings("unchecked")
  @Override
  public <C extends Configuration<K, T>> C getConfiguration(Class<C> clazz) {
    final C c = cache.getConfiguration(clazz);
    if (c instanceof CompleteConfiguration) {
      final CompleteConfiguration<K, T> cc = (CompleteConfiguration<K,T>) c;
      return (C) new CompleteConfiguration<K, T>() {
        @Override
        public Iterable<CacheEntryListenerConfiguration<K, T>> getCacheEntryListenerConfigurations() {
          return cc.getCacheEntryListenerConfigurations();
        }

        @Override
        public boolean isReadThrough() {
          return cc.isReadThrough();
        }

        @Override
        public boolean isWriteThrough() {
          return cc.isWriteThrough();
        }

        @Override
        public boolean isStatisticsEnabled() {
          return cc.isStatisticsEnabled();
        }

        @Override
        public boolean isManagementEnabled() {
          return cc.isManagementEnabled();
        }

        @Override
        public Factory<CacheLoader<K, T>> getCacheLoaderFactory() {
          return cc.getCacheLoaderFactory();
        }

        @Override
        public Factory<CacheWriter<? super K, ? super T>> getCacheWriterFactory() {
          return cc.getCacheWriterFactory();
        }

        @Override
        public Factory<ExpiryPolicy> getExpiryPolicyFactory() {
          return cc.getExpiryPolicyFactory();
        }

        @Override
        public Class<K> getKeyType() {
          return cc.getKeyType();
        }

        @Override
        public Class<T> getValueType() {
          return cc.getValueType();
        }

        @Override
        public boolean isStoreByValue() {
          return true;
        }
      };
    } else if (c instanceof Configuration) {
      return (C) new Configuration<K, T>() {
        @Override
        public Class<K> getKeyType() {
          return c.getKeyType();
        }

        @Override
        public Class<T> getValueType() {
          return c.getValueType();
        }

        @Override
        public boolean isStoreByValue() {
          return true;
        }
      };
    }
    return c;
  }

  /**
   * Delegates to wrapped cache.
   */
  @Override
  public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, T> cacheEntryListenerConfiguration) {
    cache.registerCacheEntryListener(cacheEntryListenerConfiguration);
  }

  /**
   * Delegates to wrapped cache.
   */
  @Override
  public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, T> cacheEntryListenerConfiguration) {
    cache.deregisterCacheEntryListener(cacheEntryListenerConfiguration);
  }

}
