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
 * @author Jens Wilke
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

  public CacheBuilder(final Cache2kBuilder<K, V> _builder) {
    builder = _builder;
  }

  Cache2kBuilder<K,V> builder;

  public Cache2kBuilder<K, V> addListener(final CacheEntryOperationListener<K, V> listener) {
    return builder.addListener(listener);
  }

  public Cache2kBuilder<K, V> root() {
    return builder.root();
  }

  public Cache2kBuilder<K, V> entryCapacity(final int v) {
    return builder.entryCapacity(v);
  }

  public Cache2kBuilder<K, V> loaderThreadCount(final int v) {
    return builder.loaderThreadCount(v);
  }

  public Cache2kBuilder<K, V> expiryDuration(final long v, final TimeUnit u) {
    return builder.expiryDuration(v, u);
  }

  public Cache2kBuilder<K, V> entryExpiryCalculator(final EntryExpiryCalculator<K, V> c) {
    return builder.entryExpiryCalculator(c);
  }

  public Cache2kBuilder<K, V> name(final Class<?> _class) {
    return builder.name(_class);
  }

  public Cache2kBuilder<K, V> name(final Object _containingObject, final String _fieldName) {
    return builder.name(_containingObject, _fieldName);
  }

  public Cache2kBuilder<K, V> loader(final CacheLoader<K, V> l) {
    return builder.loader(l);
  }

  public Cache2kBuilder<K, V> sharpExpiry(final boolean f) {
    return builder.sharpExpiry(f);
  }

  public Cache2kBuilder<K, V> eternal(final boolean v) {
    return builder.eternal(v);
  }

  public Cache2kBuilder<K, V> suppressExceptions(final boolean v) {
    return builder.suppressExceptions(v);
  }

  public Cache2kBuilder<K, V> writer(final CacheWriter<K, V> w) {
    return builder.writer(w);
  }

  public Cache2kBuilder<K, V> heapEntryCapacity(final int v) {
    return builder.heapEntryCapacity(v);
  }

  public <T2> Cache2kBuilder<K, T2> valueType(final Class<T2> t) {
    return builder.valueType(t);
  }

  public <K2> Cache2kBuilder<K2, V> keyType(final Class<K2> t) {
    return builder.keyType(t);
  }

  public Cache2kBuilder<K, V> expiryCalculator(final ExpiryCalculator<K, V> c) {
    return builder.expiryCalculator(c);
  }

  public Cache2kBuilder<K, V> name(final Class<?> _class, final String _fieldName) {
    return builder.name(_class, _fieldName);
  }

  public Cache2kBuilder<K, V> exceptionExpiryDuration(final long v, final TimeUnit u) {
    return builder.exceptionExpiryDuration(v, u);
  }

  public Cache<K, V> build() {
    return builder.build();
  }

  public CacheConfig createConfiguration() {
    return builder.createConfiguration();
  }

  public static <K1, T> Cache2kBuilder<K1, T> fromConfig(final CacheConfig<K1, T> c) {
    return Cache2kBuilder.fromConfig(c);
  }

  public Cache2kBuilder<K, V> manager(final CacheManager m) {
    return builder.manager(m);
  }

  public Cache2kBuilder<K, V> storeByReference(final boolean v) {
    return builder.storeByReference(v);
  }

  @Deprecated
  public CacheConfig getConfig() {
    return builder.getConfig();
  }

  public Cache2kBuilder<K, V> expirySecs(final int v) {
    return builder.expirySecs(v);
  }

  public Cache2kBuilder<K, V> maxSizeBound(final int v) {
    return builder.maxSizeBound(v);
  }

  public Cache2kBuilder<K, V> name(final String v) {
    return builder.name(v);
  }

  public void setRoot(final Cache2kBuilder<K, V> v) {
    builder.setRoot(v);
  }

  public Cache2kBuilder<K, V> source(final CacheSourceWithMetaInfo<K, V> g) {
    return builder.source(g);
  }

  public Cache2kBuilder<K, V> source(final ExperimentalBulkCacheSource<K, V> g) {
    return builder.source(g);
  }

  public Cache2kBuilder<K, V> implementation(final Class<?> c) {
    return builder.implementation(c);
  }

  public <K2> Cache2kBuilder<K2, V> keyType(final CacheTypeDescriptor<K2> t) {
    return builder.keyType(t);
  }

  public Cache2kBuilder<K, V> expiryMillis(final long v) {
    return builder.expiryMillis(v);
  }

  public Cache2kBuilder<K, V> backgroundRefresh(final boolean f) {
    return builder.backgroundRefresh(f);
  }

  public Cache2kBuilder<K, V> source(final CacheSource<K, V> g) {
    return builder.source(g);
  }

  public Cache2kBuilder<K, V> source(final BulkCacheSource<K, V> g) {
    return builder.source(g);
  }

  public Cache2kBuilder<K, V> exceptionExpiryCalculator(final org.cache2k.customization.ExceptionExpiryCalculator<K> c) {
    return builder.exceptionExpiryCalculator(c);
  }

  public Cache2kBuilder<K, V> maxSize(final int v) {
    return builder.maxSize(v);
  }

  public <T2> Cache2kBuilder<K, T2> valueType(final CacheTypeDescriptor<T2> t) {
    return builder.valueType(t);
  }

  public Cache2kBuilder<K, V> keepDataAfterExpired(final boolean v) {
    return builder.keepDataAfterExpired(v);
  }

  public Cache2kBuilder<K, V> refreshController(final RefreshController lc) {
    return builder.refreshController(lc);
  }

  public Cache2kBuilder<K, V> refreshAhead(final boolean f) {
    return builder.refreshAhead(f);
  }

  public Cache2kBuilder<K, V> exceptionPropagator(final ExceptionPropagator ep) {
    return builder.exceptionPropagator(ep);
  }

  public Cache2kBuilder<K, V> loader(final AdvancedCacheLoader<K, V> l) {
    return builder.loader(l);
  }

}
