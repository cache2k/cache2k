package org.cache2k;

/*
 * #%L
 * cache2k API only package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.spi.Cache2kCoreProvider;
import org.cache2k.spi.SingleProviderResolver;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * @author Jens Wilke; created: 2013-06-25
 */
public abstract class CacheBuilder<K,T>
  extends RootAnyBuilder<K, T> implements Cloneable {

  private static CacheBuilder PROTOTYPE;

  static {
    try {
      Cache2kCoreProvider _provider =
        SingleProviderResolver.getInstance().resolve(Cache2kCoreProvider.class);
      PROTOTYPE = _provider.getBuilderImplementation().newInstance();
    } catch (Exception ex) {
      throw new Error("cache2k-core module missing, no builder prototype", ex);
    }
  }

  public static CacheBuilder<?,?> newCache() {
    return fromConfig(new CacheConfig());
  }

  public static <K,T> CacheBuilder<K,T> newCache(Class<K> _keyType, Class<T> _valueType) {
    return fromConfig(CacheConfig.of(_keyType, _valueType));
  }

  public static <K,T> CacheBuilder<K,T> newCache(CacheTypeDescriptor<K> _keyType, Class<T> _valueType) {
    return fromConfig(CacheConfig.of(_keyType, _valueType));
  }

  public static <K,T> CacheBuilder<K,T> newCache(Class<K> _keyType, CacheTypeDescriptor<T> _valueType) {
    return fromConfig(CacheConfig.of(_keyType, _valueType));
  }

  public static <K,T> CacheBuilder<K,T> newCache(CacheTypeDescriptor<K> _keyType, CacheTypeDescriptor<T> _valueType) {
    return fromConfig(CacheConfig.of(_keyType, _valueType));
  }

  /**
   * Method to be removed. Entry type information will be discarded.
   *
   * @deprecated use {@link #newCache(Class, CacheTypeDescriptor)}
   */
  @SuppressWarnings("unchecked")
  public static <K, C extends Collection<T>, T> CacheBuilder<K, C> newCache(
    Class<K> _keyType, Class<C> _collectionType, Class<T> _entryType) {
    return fromConfig(CacheConfig.of(_keyType, _collectionType));
  }

  static <K,T> CacheBuilder<K, T> fromConfig(CacheConfig<K, T> c) {
    CacheBuilder<K,T> cb = null;
    try {
      cb = (CacheBuilder<K,T>) PROTOTYPE.clone();
    } catch (CloneNotSupportedException ignored) {  }
    cb.root = cb;
    cb.config = c;
    return cb;
  }

  protected CacheManager manager;
  protected EntryExpiryCalculator entryExpiryCalculator;
  protected CacheSource cacheSource;
  protected CacheSourceWithMetaInfo cacheSourceWithMetaInfo;
  protected RefreshController refreshController;
  protected ExperimentalBulkCacheSource experimentalBulkCacheSource;
  protected BulkCacheSource bulkCacheSource;
  protected ExceptionExpiryCalculator exceptionExpiryCalculator;
  protected CacheWriter cacheWriter;
  protected ExceptionPropagator exceptionPropagator;

  public <K2> CacheBuilder<K2, T>  keyType(Class<K2> t) {
    config.setKeyType(t);
    return (CacheBuilder<K2, T>) this;
  }

  public <T2> CacheBuilder<K, T2>  valueType(Class<T2> t) {
    config.setValueType(t);
    return (CacheBuilder<K, T2>) this;
  }

  /**
   * Constructs a cache name out of the simple class name and fieldname.
   *
   * a cache name should be unique within an application / cache manager!
   */
  public CacheBuilder<K, T> name(Class<?> _class, String _fieldName) {
    config.setName(_class.getSimpleName() + "." + _fieldName);
    return this;
  }

  /**
   * Constructs a cache name from the simple class name.
   */
  public CacheBuilder<K, T> name(Class<?> _class) {
    config.setName(_class.getSimpleName());
    return this;
  }

  /**
   * Constructs a cache name out of the simple class name and fieldname.
   *
   * a cache name should be unique within an application / cache manager!
   */
  public CacheBuilder<K, T> name(Object _containingObject, String _fieldName) {
    return name(_containingObject.getClass(), _fieldName);
  }

  /** */
  public CacheBuilder<K, T> manager(CacheManager m) {
    manager = m;
    return this;
  }

  public CacheBuilder<K, T> name(String v) {
    config.setName(v);
    return this;
  }

  public CacheBuilder<K, T> keepDataAfterExpired(boolean v) {
    config.setKeepDataAfterExpired(v);
    return this;
  }

  public CacheBuilder<K, T> maxSize(int v) {
    config.setMaxSize(v);
    return this;
  }

  public CacheBuilder<K, T> maxSizeBound(int v) {
    config.setMaxSizeHighBound(v);
    return this;
  }

  /**
   * Keep entries forever. Default is false. By default the cache uses an expiry time
   * of 10 minutes.
   */
  public CacheBuilder<K, T> eternal(boolean v) {
    config.setEternal(v);
    return this;
  }

  /**
   * If an exceptions gets thrown by the cache source, suppress it if there is
   * a previous value. When this is active, and an exception was suppressed
   * the expiry is determined by the exception expiry settings. Default: true
   */
  public CacheBuilder<K, T> suppressExceptions(boolean v) {
    config.setSuppressExceptions(v);
    return this;
  }

  public CacheBuilder<K, T> heapEntryCapacity(int v) {
    config.setHeapEntryCapacity(v);
    return this;
  }

  /**
   * Set the time duration after an entry expires. To switch off time
   * based expiry use {@link #eternal(boolean)}. A value of 0 effectively
   * disables the cache.
   *
   * <p/>If an {@link org.cache2k.EntryExpiryCalculator} is set, this setting
   * controls the maximum possible expiry duration.
   */
  public CacheBuilder<K, T> expiryDuration(long v, TimeUnit u) {
    config.setExpiryMillis(u.toMillis(v));
    return this;
  }

  /**
   * Separate timeout in the case an exception was thrown in the cache source.
   * By default 10% of the normal expiry is used.
   */
  public CacheBuilder<K, T> exceptionExpiryDuration(long v, TimeUnit u) {
    config.setExceptionExpiryMillis(u.toMillis(v));
    return this;
  }

  /**
   * @deprecated since 0.20, please use {@link #expiryDuration}
   */
  public CacheBuilder<K, T> expirySecs(int v) {
    config.setExpiryMillis(v * 1000);
    return this;
  }

  /**
   * @deprecated since 0.20, please use {@link #expiryDuration}
   */
  public CacheBuilder<K, T> expiryMillis(long v) {
    config.setExpiryMillis(v);
    return this;
  }

  public CacheBuilder<K, T> exceptionPropagator(ExceptionPropagator ep) {
    exceptionPropagator = ep;
    return this;
  }

  public CacheBuilder<K, T> source(CacheSource<K, T> g) {
    cacheSource = g;
    return this;
  }

  public CacheBuilder<K, T> source(CacheSourceWithMetaInfo<K, T> g) {
    cacheSourceWithMetaInfo = g;
    return this;
  }

  public CacheBuilder<K, T> source(ExperimentalBulkCacheSource<K, T> g) {
    experimentalBulkCacheSource = g;
    return this;
  }

  public CacheBuilder<K, T> source(BulkCacheSource<K, T> g) {
    bulkCacheSource = g;
    return this;
  }

  public CacheBuilder<K, T> writer(CacheWriter<K, T> w) {
    cacheWriter = w;
    return this;
  }

  /**
   * Set expiry calculator to use. If {@link #expiryDuration(long, java.util.concurrent.TimeUnit)}
   * is set to 0 then expiry calculation is not used, all entries expire immediately.
   */
  public CacheBuilder<K, T> entryExpiryCalculator(EntryExpiryCalculator<K, T> c) {
    entryExpiryCalculator = c;
    return this;
  }

  /**
   * Set expiry calculator to use in case of an exception happened in the {@link CacheSource}.
   */
  public CacheBuilder<K, T> exceptionExpiryCalculator(ExceptionExpiryCalculator<K> c) {
    exceptionExpiryCalculator = c;
    return this;
  }

  /**
   * @deprecated since 0.20, please use {@link #entryExpiryCalculator}
   */
  public CacheBuilder<K, T> refreshController(RefreshController c) {
    refreshController = c;
    return this;
  }

  /**
   * Sets the internal cache implementation to be used. This is used currently
   * for internal purposes. It will be removed from the general API, since the implementation
   * type is not defined within the api module.
   *
   * @deprecated since 0.23
   */
  public CacheBuilder<K, T> implementation(Class<?> c) {
    config.setImplementation(c);
    return this;
  }

  @Deprecated
  public CacheConfig getConfig() {
    return null;
  }


  /**
   * Builds a cache with the specified configuration parameters.
   * The builder reused to build caches with similar or identical
   * configuration. The builder is not thread safe.
   * configuration. The builder is not thread safe.
   */
  public abstract Cache<K, T> build();

}
