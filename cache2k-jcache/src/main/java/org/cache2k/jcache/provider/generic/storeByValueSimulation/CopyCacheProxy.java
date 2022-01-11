package org.cache2k.jcache.provider.generic.storeByValueSimulation;

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
public class CopyCacheProxy<K, V> extends TransformingCacheProxy<K, V, K, V> {

  @SuppressWarnings("unchecked")
  public CopyCacheProxy(Cache<K, V> cache, ObjectTransformer<K, K> keyTransformer,
                        ObjectTransformer<V, V> valueTransformer) {
    super(cache, keyTransformer, valueTransformer, ObjectTransformer.IDENT_TRANSFORM,
      ObjectTransformer.IDENT_TRANSFORM);
  }

  /**
   * Delegates to the wrapped cache. Wrap configuration and return true on store by value
   */
  @SuppressWarnings("unchecked")
  @Override
  public <C extends Configuration<K, V>> C getConfiguration(Class<C> clazz) {
    C c = cache.getConfiguration(clazz);
    if (c instanceof CompleteConfiguration) {
      CompleteConfiguration<K, V> cc = (CompleteConfiguration<K, V>) c;
      return (C) new CompleteConfiguration<K, V>() {
        @Override
        public Iterable<CacheEntryListenerConfiguration<K, V>>
        getCacheEntryListenerConfigurations() {
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
        public Factory<CacheLoader<K, V>> getCacheLoaderFactory() {
          return cc.getCacheLoaderFactory();
        }

        @Override
        public Factory<CacheWriter<? super K, ? super V>> getCacheWriterFactory() {
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
        public Class<V> getValueType() {
          return cc.getValueType();
        }

        @Override
        public boolean isStoreByValue() {
          return true;
        }
      };
    } else if (c instanceof Configuration) {
      return (C) new Configuration<K, V>() {
        @Override
        public Class<K> getKeyType() {
          return c.getKeyType();
        }

        @Override
        public Class<V> getValueType() {
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
  public void registerCacheEntryListener(
    CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
    cache.registerCacheEntryListener(cacheEntryListenerConfiguration);
  }

  /**
   * Delegates to wrapped cache.
   */
  @Override
  public void deregisterCacheEntryListener(
    CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
    cache.deregisterCacheEntryListener(cacheEntryListenerConfiguration);
  }

}
