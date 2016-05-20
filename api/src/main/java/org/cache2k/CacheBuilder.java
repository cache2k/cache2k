package org.cache2k;

/*
 * #%L
 * cache2k API
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

import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.configuration.CacheType;
import org.cache2k.expiry.*;
import org.cache2k.event.CacheEntryOperationListener;
import org.cache2k.integration.AdvancedCacheLoader;
import org.cache2k.integration.CacheLoader;
import org.cache2k.integration.CacheWriter;
import org.cache2k.integration.ExceptionPropagator;
import org.cache2k.integration.ExceptionInformation;
import org.cache2k.integration.ResiliencePolicy;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @deprecated Replaced with {@link Cache2kBuilder}
 */
public class CacheBuilder<K,V> {

  public static CacheBuilder<?,?> newCache() {
    return new CacheBuilder(Cache2kBuilder.forUnknownTypes());
  }

  public static <K,V> CacheBuilder<K,V> newCache(Class<K> _keyType, Class<V> _valueType) {
    return fromConfig(Cache2kConfiguration.of(_keyType, _valueType));
  }

  @SuppressWarnings("unchecked")
  public static <K, C extends Collection<T>, T> CacheBuilder<K, C> newCache(
    Class<K> _keyType, Class<C> _collectionType, Class<T> _entryType) {
    return newCache(_keyType, _collectionType);
  }

  public static <K1, T> CacheBuilder<K1, T> fromConfig(final Cache2kConfiguration<K1, T> c) {
    return new CacheBuilder<K1, T>(Cache2kBuilder.of(c));
  }

  public CacheBuilder(final Cache2kBuilder<K, V> _builder) {
    builder = _builder;
  }

  Cache2kBuilder<K,V> builder;

  public CacheBuilder<K, V> addListener(final CacheEntryOperationListener<K, V> listener) {
    builder.addListener(listener);
    return this;
  }

  public CacheBuilder<K, V> entryCapacity(final int v) {
    builder.entryCapacity(v);
    return this;
  }

  public CacheBuilder<K, V> loaderThreadCount(final int v) {
    builder.loaderThreadCount(v);
    return this;
  }

  /**
   * @deprecated Replaced by {@link Cache2kBuilder#expireAfterWrite}
   */
  public CacheBuilder<K, V> expiryDuration(final long v, final TimeUnit u) {
    builder.expireAfterWrite(v, u);
    return this;
  }

  public CacheBuilder<K, V> entryExpiryCalculator(final EntryExpiryCalculator<K, V> c) {
    builder.expiryPolicy(c);
    return this;
  }

  public CacheBuilder<K, V> name(final Class<?> _class) {
    name(_class.getSimpleName());
    return this;
  }

  public CacheBuilder<K, V> name(final Object _containingObject, final String _fieldName) {
    name(_containingObject.getClass(), _fieldName);
    return this;
  }

  public CacheBuilder<K, V> loader(final CacheLoader<K, V> l) {
    builder.loader(l);
    return this;
  }

  public CacheBuilder<K, V> sharpExpiry(final boolean f) {
    builder.sharpExpiry(f);
    return this;
  }

  public CacheBuilder<K, V> eternal(final boolean v) {
    builder.eternal(v);
    return this;
  }

  public CacheBuilder<K, V> suppressExceptions(final boolean v) {
    builder.suppressExceptions(v);
    return this;
  }

  public CacheBuilder<K, V> writer(final CacheWriter<K, V> w) {
    builder.writer(w);
    return this;
  }

  public CacheBuilder<K, V> heapEntryCapacity(final int v) {
    builder.toConfiguration().setHeapEntryCapacity(v);
    return this;
  }

  public <T2> CacheBuilder<K, T2> valueType(final Class<T2> t) {
    builder.valueType(t);
    return (CacheBuilder<K, T2>) this;
  }

  public <K2> CacheBuilder<K2, V> keyType(final Class<K2> t) {
    builder.keyType(t);
    return (CacheBuilder<K2, V>) this;
  }

  public CacheBuilder<K, V> expiryCalculator(final ExpiryPolicy<K, V> c) {
    builder.expiryPolicy(c);
    return this;
  }

  public CacheBuilder<K, V> name(final Class<?> _class, final String _fieldName) {
    builder.name(_class.getSimpleName() + "." +  _fieldName);
    return this;
  }

  public CacheBuilder<K, V> exceptionExpiryDuration(final long v, final TimeUnit u) {
    builder.retryInterval(v, u);
    return this;
  }

  public Cache<K, V> build() {
    return builder.build();
  }

  public Cache2kConfiguration createConfiguration() {
    return builder.toConfiguration();
  }

  public CacheBuilder<K, V> manager(final CacheManager m) {
    builder.manager(m);
    return this;
  }

  public CacheBuilder<K, V> storeByReference(final boolean v) {
    builder.storeByReference(v);
    return this;
  }

  @Deprecated
  public Cache2kConfiguration getConfig() {
    return builder.toConfiguration();
  }

  public CacheBuilder<K, V> expirySecs(final int v) {
    builder.expireAfterWrite(v, TimeUnit.SECONDS);
    return this;
  }

  public CacheBuilder<K, V> maxSizeBound(final int v) {
    return this;
  }

  public CacheBuilder<K, V> name(final String v) {
    builder.name(v);
    return this;
  }

  public CacheBuilder<K, V> source(final CacheSourceWithMetaInfo<K, V> eg) {
    builder.loader(new AdvancedCacheLoader<K, V>() {
      @Override
      public V load(final K key, final long currentTime, final CacheEntry<K, V> previousEntry) throws Exception {
        try {
          if (previousEntry != null && previousEntry.getException() == null) {
            return eg.get(key, currentTime, previousEntry.getValue(), previousEntry.getLastModification());
          } else {
            return eg.get(key, currentTime, null, 0);
          }
        } catch (Exception e) {
          throw e;
        } catch (Throwable t) {
          throw new RuntimeException("rethrow throwable", t);
        }
      }
    });
    return this;
  }

  /**
   * Removed without replacement.
   */
  public CacheBuilder<K, V> implementation(final Class<?> c) {
    return this;
  }

  public <K2> CacheBuilder<K2, V> keyType(final CacheType<K2> t) {
    builder.keyType(t);
    return (CacheBuilder<K2, V>) this;
  }

  public CacheBuilder<K, V> expiryMillis(final long v) {
    builder.expireAfterWrite(v, TimeUnit.MILLISECONDS);
    return this;
  }

  public CacheBuilder<K, V> backgroundRefresh(final boolean f) {
    builder.refreshAhead(f);
    return this;
  }

  public CacheBuilder<K, V> source(final CacheSource<K, V> g) {
    builder.loader(new AdvancedCacheLoader<K, V>() {
      @Override
      public V load(final K key, final long currentTime, final CacheEntry<K, V> previousEntry) throws Exception {
        try {
          return g.get(key);
        } catch (Exception e) {
          throw e;
        } catch (Throwable t) {
          throw new RuntimeException("rethrow throwable", t);
        }
      }
    });
    return this;
  }

  public CacheBuilder<K, V> source(final BulkCacheSource<K, V> s) {
    builder.loader(new AdvancedCacheLoader<K, V>() {
      @Override
      public V load(final K key, final long currentTime, final CacheEntry<K, V> previousEntry) throws Exception {
        CacheEntry<K, V> entry = previousEntry;
        if (previousEntry == null)
          entry = new CacheEntry<K, V>() {
            @Override
            public K getKey() {
              return key;
            }

            @Override
            public V getValue() {
              return null;
            }

            @Override
            public Throwable getException() {
              return null;
            }

            @Override
            public long getLastModification() {
              return 0;
            }
          };
        try {
          return s.getValues(Collections.singletonList(entry), currentTime).get(0);
        } catch (Exception e) {
          throw e;
        } catch (Throwable t) {
          throw new RuntimeException("rethrow throwable", t);
        }
      }
    });
    return this;
  }

  public CacheBuilder<K, V> exceptionExpiryCalculator(final org.cache2k.ExceptionExpiryCalculator<K> c) {
    builder.resiliencePolicy(new ResiliencePolicy<K, V>() {
      @Override
      public long suppressExceptionUntil(final K key, final ExceptionInformation exceptionInformation, final CacheEntry<K, V> cachedContent) {
        return c.calculateExpiryTime(key, exceptionInformation.getException(), exceptionInformation.getLoadTime());
      }

      @Override
      public long retryLoadAfter(final K key, final ExceptionInformation exceptionInformation) {
        return c.calculateExpiryTime(key, exceptionInformation.getException(), exceptionInformation.getLoadTime());
      }
    });
    return this;
  }

  public CacheBuilder<K, V> maxSize(final int v) {
    builder.entryCapacity(v);
    return this;
  }

  public <T2> CacheBuilder<K, T2> valueType(final CacheType<T2> t) {
    builder.valueType(t);
    return (CacheBuilder<K, T2>) this;
  }

  public CacheBuilder<K, V> keepDataAfterExpired(final boolean v) {
    builder.keepDataAfterExpired(v);
    return this;
  }

  public CacheBuilder<K, V> refreshController(final RefreshController lc) {
    expiryCalculator(new ExpiryPolicy<K, V>() {
      @Override
      public long calculateExpiryTime(K _key, V _value, long _loadTime, CacheEntry<K, V> _oldEntry) {
        if (_oldEntry != null) {
          return lc.calculateNextRefreshTime(_oldEntry.getValue(), _value, _oldEntry.getLastModification(), _loadTime);
        } else {
          return lc.calculateNextRefreshTime(null, _value, 0L, _loadTime);
        }
      }
    });
    return this;
  }

  public CacheBuilder<K, V> refreshAhead(final boolean f) {
    builder.refreshAhead(f);
    return this;
  }

  public CacheBuilder<K, V> exceptionPropagator(final ExceptionPropagator ep) {
    builder.exceptionPropagator(ep);
    return this;
  }

  public CacheBuilder<K, V> loader(final AdvancedCacheLoader<K, V> l) {
    builder.loader(l);
    return this;
  }

}
