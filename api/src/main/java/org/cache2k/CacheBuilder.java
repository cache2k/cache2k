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

import org.cache2k.customization.ExceptionExpiryCalculator;
import org.cache2k.customization.ExpiryCalculator;
import org.cache2k.event.CacheEntryOperationListener;
import org.cache2k.integration.AdvancedCacheLoader;
import org.cache2k.integration.CacheLoader;
import org.cache2k.integration.CacheWriter;
import org.cache2k.integration.ExceptionPropagator;
import org.cache2k.spi.Cache2kCoreProvider;
import org.cache2k.spi.SingleProviderResolver;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * @author Jens Wilke; created: 2013-06-25
 */
public abstract class CacheBuilder<K, V>
  extends RootAnyBuilder<K, V> implements Cloneable {

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

  /**
   *
   * @deprecated will removed with no replacement? Construct the cache with type information.
   */
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
  protected CacheSource cacheSource;
  protected CacheSourceWithMetaInfo cacheSourceWithMetaInfo;
  protected ExperimentalBulkCacheSource experimentalBulkCacheSource;
  protected BulkCacheSource bulkCacheSource;
  protected CacheWriter cacheWriter;
  protected ExceptionPropagator exceptionPropagator;

  public <K2> CacheBuilder<K2, V>  keyType(Class<K2> t) {
    config.setKeyType(t);
    return (CacheBuilder<K2, V>) this;
  }

  public <T2> CacheBuilder<K, T2>  valueType(Class<T2> t) {
    config.setValueType(t);
    return (CacheBuilder<K, T2>) this;
  }

  public <K2> CacheBuilder<K2, V>  keyType(CacheTypeDescriptor<K2> t) {
    config.setKeyType(t);
    return (CacheBuilder<K2, V>) this;
  }

  public <T2> CacheBuilder<K, T2>  valueType(CacheTypeDescriptor<T2> t) {
    config.setValueType(t);
    return (CacheBuilder<K, T2>) this;
  }

  /**
   * Constructs a cache name out of the simple class name and fieldname.
   *
   * <p>See {@link #name(String)} for a general discussion about cache names.
   *
   * @see #name(String)
   */
  public CacheBuilder<K, V> name(Class<?> _class, String _fieldName) {
    config.setName(_class.getSimpleName() + "." + _fieldName);
    return this;
  }

  /**
   * Sets a cache name from the simple class name.
   *
   * <p>See {@link #name(String)} for a general discussion about cache names.
   *
   * @see #name(String)
   */
  public CacheBuilder<K, V> name(Class<?> _class) {
    config.setName(_class.getSimpleName());
    return this;
  }

  /**
   * Constructs a cache name out of the simple class name and fieldname.
   *
   * @see #name(String)
   * @deprecated users should change to, e.g. <code>name(this.getClass(), "cache")</code>
   */
  public CacheBuilder<K, V> name(Object _containingObject, String _fieldName) {
    return name(_containingObject.getClass(), _fieldName);
  }

  /**
   * The manager this cache should belong to.
   */
  public CacheBuilder<K, V> manager(CacheManager m) {
    manager = m;
    return this;
  }

  /**
   * Sets the name of a cache. If a name is specified it must be ensured it is unique within
   * the cache manager. Cache names are used at several places to have a unique ID of a cache.
   * For example, to register JMX beans. Another usage is derive a filename for a persistence
   * cache.
   *
   * <p>If a name is not specified the cache generates a name automatically. The name is
   * inferred from the call stack trace and contains the simple class name, the method and
   * the line number of the of the caller to <code>build()</code>. Instead of relying to the
   * automatic name generation, a name should be chosen carefully.
   *
   * <p>In case of a name collision the cache is generating a unique name by adding a counter value.
   * This behavior may change.
   *
   * <p>TODO: Remove autogeneration and name uniquifier?
   *
   * <p>Allowed characters for a cache name, are URL non-reserved characters,
   * these are: [A-Z], [a-z], [0-9] and [~-_.-], see RFC3986. The reason for
   * restricting the characters in names, is that the names may be used to derive
   * other resource names from it, e.g. for file based storage.
   *
   * <p>For brevity within log messages and other displays the cache name may be
   * shortened if the manager name is included as prefix.
   *
   * @see Cache#getName()
   * @see org.cache2k.StorageConfiguration.Builder#storageName(String)
   */
  public CacheBuilder<K, V> name(String v) {
    config.setName(v);
    return this;
  }

  public CacheBuilder<K, V> keepDataAfterExpired(boolean v) {
    config.setKeepDataAfterExpired(v);
    return this;
  }

  /**
   * The maximum number of entries hold by the cache. When the maximum size is reached, by
   * inserting new entries, the cache eviction algorithm will remove one or more entries
   * to keep the size within the configured limit.
   *
   * <p>In case of an attached storage this setting means the total cache capacity. A different
   * in heap capacity can be set by {@link #heapEntryCapacity}.
   *
   */
  public CacheBuilder<K, V> entryCapacity(int v) {
    config.setEntryCapacity(v);
    return this;
  }

  /**
   * @deprecated Use {@link #entryCapacity(int)}
   */
  public CacheBuilder<K, V> maxSize(int v) {
    config.setEntryCapacity(v);
    return this;
  }

  /**
   * @deprecated not used.
   */
  public CacheBuilder<K, V> maxSizeBound(int v) {
    return this;
  }

  /**
   * Keep entries forever. Default is false. By default the cache uses an expiry time
   * of 10 minutes. If there is no explicit expiry configured for exceptions
   * with {@link #exceptionExpiryDuration(long, TimeUnit)}, exceptions will
   * not be cached and expire immediately.
   */
  public CacheBuilder<K, V> eternal(boolean v) {
    config.setEternal(v);
    return this;
  }

  /**
   * If an exceptions gets thrown by the cache source, suppress it if there is
   * a previous value. When this is active, and an exception was suppressed
   * the expiry is determined by the exception expiry settings. Default: true
   */
  public CacheBuilder<K, V> suppressExceptions(boolean v) {
    config.setSuppressExceptions(v);
    return this;
  }

  /**
   * Maximum count of entries stored in heap.
   */
  public CacheBuilder<K, V> heapEntryCapacity(int v) {
    config.setHeapEntryCapacity(v);
    return this;
  }

  /**
   * Set the time duration after that an inserted or updated cache entry expires.
   * To switch off time based expiry use {@link #eternal(boolean)}.
   *
   * <p>If an {@link ExpiryCalculator} is set, this setting
   * controls the maximum possible expiry duration.
   *
   * <p>A value of 0 means every entry should expire immediately. In case of
   * a cache source present, a cache in this setting can be used to block out
   * concurrent requests for the same key.
   *
   * <p>For an expiry duration of 0 or within low millis, in a special corner case,
   * especially when a source call executes below an OS time slice, more values from
   * the cache source could be produced, then actually returned by the cache. This
   * is within the consistency guarantees of a cache, however, it may be important
   * to interpret concurrency testing results.
   */
  public CacheBuilder<K, V> expiryDuration(long v, TimeUnit u) {
    config.setExpiryMillis(u.toMillis(v));
    return this;
  }

  /**
   * Separate timeout in the case an exception was thrown in the cache source.
   * By default 10% of the normal expiry is used.
   */
  public CacheBuilder<K, V> exceptionExpiryDuration(long v, TimeUnit u) {
    config.setExceptionExpiryMillis(u.toMillis(v));
    return this;
  }

  /**
   * @deprecated since 0.20, please use {@link #expiryDuration}
   */
  public CacheBuilder<K, V> expirySecs(int v) {
    config.setExpiryMillis(v * 1000);
    return this;
  }

  /**
   * @deprecated since 0.20, please use {@link #expiryDuration}
   */
  public CacheBuilder<K, V> expiryMillis(long v) {
    config.setExpiryMillis(v);
    return this;
  }

  public CacheBuilder<K, V> exceptionPropagator(ExceptionPropagator ep) {
    exceptionPropagator = ep;
    return this;
  }

  /**
   * @deprecated use the loader, will be removed
   */
  public CacheBuilder<K, V> source(CacheSource<K, V> g) {
    cacheSource = g;
    return this;
  }

  /**
   * @deprecated use the loader, will be removed
   */
  public CacheBuilder<K, V> source(CacheSourceWithMetaInfo<K, V> g) {
    cacheSourceWithMetaInfo = g;
    return this;
  }

  /**
   * @deprecated use the loader, will be removed
   */
  public CacheBuilder<K, V> source(ExperimentalBulkCacheSource<K, V> g) {
    experimentalBulkCacheSource = g;
    return this;
  }

  /**
   * @deprecated use the loader, will be removed
   */
  public CacheBuilder<K, V> source(BulkCacheSource<K, V> g) {
    bulkCacheSource = g;
    return this;
  }

  public CacheBuilder<K, V> loader(CacheLoader<K, V> l) {
    config.setLoader(l);
    return this;
  }

  public CacheBuilder<K, V> loader(AdvancedCacheLoader<K, V> l) {
    config.setAdvancedLoader(l);
    return this;
  }

  public CacheBuilder<K, V> writer(CacheWriter<K, V> w) {
    cacheWriter = w;
    return this;
  }

  public abstract CacheBuilder<K, V> addListener(CacheEntryOperationListener<K,V> listener);

  /**
   * @deprecated replaced by {@link #expiryCalculator}
   */
  public CacheBuilder<K, V> entryExpiryCalculator(EntryExpiryCalculator<K, V> c) {
    expiryCalculator(c);
    return this;
  }

  /**
   * Set expiry calculator to use. If {@link #expiryDuration(long, java.util.concurrent.TimeUnit)}
   * is set to 0 then expiry calculation is not used, all entries expire immediately.
   */
  public CacheBuilder<K, V> expiryCalculator(ExpiryCalculator<K, V> c) {
    config.setExpiryCalculator(c);
    return this;
  }

  /**
   * Set expiry calculator to use in case of an exception happened in the {@link CacheSource}.
   */
  public CacheBuilder<K, V> exceptionExpiryCalculator(ExceptionExpiryCalculator<K> c) {
    config.setExceptionExpiryCalculator(c);
    return this;
  }

  /**
   * @deprecated since 0.20, please use {@link #expiryCalculator}
   */
  public CacheBuilder<K, V> refreshController(final RefreshController lc) {
    expiryCalculator(new ExpiryCalculator<K, V>() {
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

  /**
   * Sets the internal cache implementation to be used. This is used currently
   * for internal purposes. It will be removed from the general API, since the implementation
   * type is not defined within the api module.
   *
   * @deprecated since 0.23
   */
  public CacheBuilder<K, V> implementation(Class<?> c) {
    config.setImplementation(c);
    return this;
  }

  /**
   * When true, enable background refresh / refresh ahead. After an entry is expired, the cache
   * loader is invoked to fetch a fresh value. The old value will be returned by the cache, although
   * it is expired, and will be replaced by the new value, once the loader is finished. In the case
   * there are not enough threads available to start the loading, the entry will expire immediately and
   * the next {@code get()} request will trigger the load.
   *
   * <p>Once refreshed, the entry is in a trail period. If it is not accessed until the next
   * expiry, no refresh will be done and the entry expires regularly.
   */
  public CacheBuilder<K, V> backgroundRefresh(boolean f) {
    config.setBackgroundRefresh(f);
    return this;
  }

  /**
   * By default the expiry time is not exact, which means, a value might be visible a few
   * milliseconds after the time of expiry. The timing depends on the system load.
   * Switching to true, means an entry is not visible exactly at and after the time of
   * expiry.
   */
  public CacheBuilder<K, V> sharpExpiry(boolean f) {
    config.setSharpExpiry(f);
    return this;
  }

  /**
   * Maximum number of threads this cache should use for calls to the {@link CacheLoader}
   */
  public CacheBuilder<K, V> loaderThreadCount(int v) {
    config.setLoaderThreadCount(v);
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
  public abstract Cache<K, V> build();

}
