package org.cache2k;

/*
 * #%L
 * cache2k API only package
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

import org.cache2k.customization.*;
import org.cache2k.event.CacheEntryOperationListener;
import org.cache2k.integration.AdvancedCacheLoader;
import org.cache2k.integration.CacheLoader;
import org.cache2k.integration.CacheWriter;
import org.cache2k.integration.ExceptionPropagator;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * @deprecated Replace with {@link Cache2kBuilder}
 */
public class CacheBuilder<K,V> {

  public static CacheBuilder<?,?> newCache() {
    return new CacheBuilder(Cache2kBuilder.newCache());
  }

  public static <K,V> CacheBuilder<K,V> newCache(Class<K> _keyType, Class<V> _valueType) {
    return new CacheBuilder<K, V>(Cache2kBuilder.newCache(_keyType, _valueType));
  }

  public static <K,V> CacheBuilder<K,V> newCache(CacheTypeDescriptor<K> _keyType, Class<V> _valueType) {
    return new CacheBuilder<K, V>(Cache2kBuilder.newCache(_keyType, _valueType));
  }

  public static <K,V> CacheBuilder<K,V> newCache(Class<K> _keyType, CacheTypeDescriptor<V> _valueType) {
    return new CacheBuilder<K, V>(Cache2kBuilder.newCache(_keyType, _valueType));
  }

  public static <K,V> CacheBuilder<K,V> newCache(CacheTypeDescriptor<K> _keyType, CacheTypeDescriptor<V> _valueType) {
    return new CacheBuilder<K, V>(Cache2kBuilder.newCache(_keyType, _valueType));
  }

  /**
   * Method to be removed. Entry type information will be discarded.
   *
   * @deprecated use {@link #newCache(Class, CacheTypeDescriptor)}
   */
  @SuppressWarnings("unchecked")
  public static <K, C extends Collection<T>, T> CacheBuilder<K, C> newCache(
    Class<K> _keyType, Class<C> _collectionType, Class<T> _entryType) {
    return new CacheBuilder<K, C>(Cache2kBuilder.newCache(_keyType, _collectionType, _entryType));
  }

  public static <K1, T> CacheBuilder<K1, T> fromConfig(final CacheConfig<K1, T> c) {
    return new CacheBuilder<K1, T>(Cache2kBuilder.fromConfig(c));
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

  public CacheBuilder<K, V> expiryDuration(final long v, final TimeUnit u) {
    builder.expiryDuration(v, u);
    return this;
  }

  public CacheBuilder<K, V> entryExpiryCalculator(final EntryExpiryCalculator<K, V> c) {
    builder.entryExpiryCalculator(c);
    return this;
  }

  public CacheBuilder<K, V> name(final Class<?> _class) {
    builder.name(_class);
    return this;
  }

  public CacheBuilder<K, V> name(final Object _containingObject, final String _fieldName) {
    builder.name(_containingObject, _fieldName);
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
    builder.heapEntryCapacity(v);
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

  public CacheBuilder<K, V> expiryCalculator(final ExpiryCalculator<K, V> c) {
    builder.expiryCalculator(c);
    return this;
  }

  public CacheBuilder<K, V> name(final Class<?> _class, final String _fieldName) {
    builder.name(_class, _fieldName);
    return this;
  }

  public CacheBuilder<K, V> exceptionExpiryDuration(final long v, final TimeUnit u) {
    builder.exceptionExpiryDuration(v, u);
    return this;
  }

  public Cache<K, V> build() {
    return builder.build();
  }

  public CacheConfig createConfiguration() {
    return builder.createConfiguration();
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
  public CacheConfig getConfig() {
    return builder.getConfig();
  }

  public CacheBuilder<K, V> expirySecs(final int v) {
    builder.expirySecs(v);
    return this;
  }

  public CacheBuilder<K, V> maxSizeBound(final int v) {
    builder.maxSizeBound(v);
    return this;
  }

  public CacheBuilder<K, V> name(final String v) {
    builder.name(v);
    return this;
  }

  public CacheBuilder<K, V> source(final CacheSourceWithMetaInfo<K, V> g) {
    builder.source(g);
    return this;
  }

  public CacheBuilder<K, V> source(final ExperimentalBulkCacheSource<K, V> g) {
    builder.source(g);
    return this;
  }

  public CacheBuilder<K, V> implementation(final Class<?> c) {
    builder.implementation(c);
    return this;
  }

  public <K2> CacheBuilder<K2, V> keyType(final CacheTypeDescriptor<K2> t) {
    builder.keyType(t);
    return (CacheBuilder<K2, V>) this;
  }

  public CacheBuilder<K, V> expiryMillis(final long v) {
    builder.expiryMillis(v);
    return this;
  }

  public CacheBuilder<K, V> backgroundRefresh(final boolean f) {
    builder.backgroundRefresh(f);
    return this;
  }

  public CacheBuilder<K, V> source(final CacheSource<K, V> g) {
    builder.source(g);
    return this;
  }

  public CacheBuilder<K, V> source(final BulkCacheSource<K, V> g) {
    builder.source(g);
    return this;
  }

  public CacheBuilder<K, V> exceptionExpiryCalculator(final org.cache2k.customization.ExceptionExpiryCalculator<K> c) {
    builder.exceptionExpiryCalculator(c);
    return this;
  }

  public CacheBuilder<K, V> maxSize(final int v) {
    builder.maxSize(v);
    return this;
  }

  public <T2> CacheBuilder<K, T2> valueType(final CacheTypeDescriptor<T2> t) {
    builder.valueType(t);
    return (CacheBuilder<K, T2>) this;
  }

  public CacheBuilder<K, V> keepDataAfterExpired(final boolean v) {
    builder.keepDataAfterExpired(v);
    return this;
  }

  public CacheBuilder<K, V> refreshController(final RefreshController lc) {
    builder.refreshController(lc);
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
