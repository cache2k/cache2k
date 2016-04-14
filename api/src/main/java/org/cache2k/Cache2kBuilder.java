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

import org.cache2k.customization.ExceptionExpiryCalculator;
import org.cache2k.customization.ExpiryCalculator;
import org.cache2k.event.CacheEntryOperationListener;
import org.cache2k.integration.AdvancedCacheLoader;
import org.cache2k.integration.CacheLoader;
import org.cache2k.integration.CacheWriter;
import org.cache2k.integration.ExceptionPropagator;
import org.cache2k.spi.Cache2kCoreProvider;
import org.cache2k.spi.SingleProviderResolver;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

/**
 * Builder to create a new cache2k cache. The usage is:
 *
 * <pre>{@code
 *    Cache<Long, List<String>> c =
 *      new Cache2kBuilder<Long, List<String>>() {}
 *        .name("myCache")
 *        .eternal(true)
 *        .build();
 * }</pre>
 *
 * <p>Caches belong to a cache manager. If no cache manager is set explicitly via {@link #manager} the
 * default cache manager will be used, as defined by {@link CacheManager#getInstance()}.
 *
 * <p>To create a cache from a known configuration in a specified cache manager, use:
 *
 * <pre>{@code
 *   CacheManager manager = ...
 *   CacheConfiguration<Long, List<String>> config = ...
 *
 *   Cache<Long, List<String>> c =
 *     Cache2kBuilder.of(config)
 *       .manager(manager)
 *       .build();
 * }</pre>
 *
 * @author Jens Wilke
 * @since 0.25
 */
public class Cache2kBuilder<K, V>
  extends RootAnyBuilder<K, V> implements Cloneable {

  private static final Cache2kCoreProvider CORE_PROVIDER;

  static {
    CORE_PROVIDER = SingleProviderResolver.getInstance().resolve(Cache2kCoreProvider.class);
  }

  /**
   * Create a new cache builder for a cache that has no type restrictions
   * or to set the type information later via the builder methods {@link #keyType} or
   * {@link #valueType}.
   */
  public static Cache2kBuilder<?,?> forUnknownTypes() {
    return of(new CacheConfig());
  }

  /**
   * Create a new cache builder if key and value types are classes with no generic parameters.
   */
  public static <K,T> Cache2kBuilder<K,T> of(Class<K> _keyType, Class<T> _valueType) {
    return of(CacheConfig.of(_keyType, _valueType));
  }

  /**
   * Create a builder from the configuration.
   */
  public static <K,T> Cache2kBuilder<K, T> of(CacheConfig<K, T> c) {
    Cache2kBuilder<K,T> cb = new Cache2kBuilder<K, T>(c);
    return cb;
  }

  private CacheManager manager;
  private Cache2kBuilder(CacheConfig<K,V> cfg) {
    config = cfg;
  }

  /**
   * Constructor to override for the usage pattern:
   *
   * <pre>{@code
   *    Cache<Long, List<String>> c =
   *      new Cache2kBuilder<Long, List<String>>() {}
   *        .name("myCache")
   *        .eternal(true)
   *        .build();
   * }</pre>
   *
   * The builder extracts the generic type parameters from the anonymous subclass.
   */
  @SuppressWarnings("unchecked")
  protected Cache2kBuilder() {
    Type[] _types =
      ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments();
    config = CacheConfig.of(
      (CacheTypeDescriptor<K>) CacheType.of(_types[0]).getBeanRepresentation(),
      (CacheTypeDescriptor<V>) CacheType.of(_types[1]).getBeanRepresentation());
  }

  /**
   * Sets the key type to use.
   *
   * @throws IllegalArgumentException if key type is already set
   */
  public final <K2> Cache2kBuilder<K2, V> keyType(Class<K2> t) {
    config.setKeyType(t);
    return (Cache2kBuilder<K2, V>) this;
  }

  /**
   * Sets the value type to use.
   *
   * @throws IllegalArgumentException if value type is already set
   */
  public final <T2> Cache2kBuilder<K, T2> valueType(Class<T2> t) {
    config.setValueType(t);
    return (Cache2kBuilder<K, T2>) this;
  }

  /**
   * Sets the key type to use.
   *
   * @throws IllegalArgumentException if key type is already set
   */
  public final <K2> Cache2kBuilder<K2, V> keyType(CacheTypeDescriptor<K2> t) {
    config.setKeyType(t);
    return (Cache2kBuilder<K2, V>) this;
  }

  /**
   * Sets the value type to use.
   *
   * @throws IllegalArgumentException if value type is already set
   */
  public final <T2> Cache2kBuilder<K, T2> valueType(CacheTypeDescriptor<T2> t) {
    config.setValueType(t);
    return (Cache2kBuilder<K, T2>) this;
  }

  /**
   * Constructs a cache name out of the simple class name and fieldname.
   *
   * <p>See {@link #name(String)} for a general discussion about cache names.
   *
   * @see #name(String)
   */
  public final Cache2kBuilder<K, V> name(Class<?> _class, String _fieldName) {
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
  public final Cache2kBuilder<K, V> name(Class<?> _class) {
    config.setName(_class.getSimpleName());
    return this;
  }

  /**
   * Constructs a cache name out of the simple class name and fieldname.
   *
   * @see #name(String)
   * @deprecated users should change to, e.g. <code>name(this.getClass(), "cache")</code>
   */
  public final Cache2kBuilder<K, V> name(Object _containingObject, String _fieldName) {
    return name(_containingObject.getClass(), _fieldName);
  }

  /**
   * The manager, the created cache will belong to.
   */
  public final Cache2kBuilder<K, V> manager(CacheManager m) {
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
   */
  public final Cache2kBuilder<K, V> name(String v) {
    config.setName(v);
    return this;
  }

  public final Cache2kBuilder<K, V> keepDataAfterExpired(boolean v) {
    config.setKeepDataAfterExpired(v);
    return this;
  }

  /**
   * The maximum number of entries hold by the cache. When the maximum size is reached, by
   * inserting new entries, the cache eviction algorithm will remove one or more entries
   * to keep the size within the configured limit.
   */
  public final Cache2kBuilder<K, V> entryCapacity(int v) {
    config.setEntryCapacity(v);
    return this;
  }

  /**
   * Keep entries forever. Default is false. By default the cache uses an expiry time
   * of 10 minutes. If there is no explicit expiry configured for exceptions
   * with {@link #exceptionExpiryDuration(long, TimeUnit)}, exceptions will
   * not be cached and expire immediately.
   */
  public final Cache2kBuilder<K, V> eternal(boolean v) {
    config.setEternal(v);
    return this;
  }

  /**
   * If an exceptions gets thrown by the cache source, suppress it if there is
   * a previous value. When this is active, and an exception was suppressed
   * the expiry is determined by the exception expiry settings. Default: true
   */
  public final Cache2kBuilder<K, V> suppressExceptions(boolean v) {
    config.setSuppressExceptions(v);
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
  public final Cache2kBuilder<K, V> expiryDuration(long v, TimeUnit u) {
    config.setExpiryMillis(u.toMillis(v));
    return this;
  }

  /**
   * Separate timeout in the case an exception was thrown in the cache source.
   * By default 10% of the normal expiry is used.
   */
  public final Cache2kBuilder<K, V> exceptionExpiryDuration(long v, TimeUnit u) {
    config.setExceptionExpiryMillis(u.toMillis(v));
    return this;
  }

  public final Cache2kBuilder<K, V> exceptionPropagator(ExceptionPropagator ep) {
    config.setExceptionPropagator(ep);
    return this;
  }

  public final Cache2kBuilder<K, V> loader(CacheLoader<K, V> l) {
    config.setLoader(l);
    return this;
  }

  public final Cache2kBuilder<K, V> loader(AdvancedCacheLoader<K, V> l) {
    config.setAdvancedLoader(l);
    return this;
  }

  public final Cache2kBuilder<K, V> writer(CacheWriter<K, V> w) {
    config.setWriter(w);
    return this;
  }

  /**
   * Add a listener to the cache.
   *
   * @throws IllegalArgumentException if an identical listener is already added.
   * @param listener The listener to add
   */
  public final Cache2kBuilder<K, V> addListener(CacheEntryOperationListener<K,V> listener) {
    boolean _inserted = config.getListeners().add(listener);
    if (!_inserted) {
      throw new IllegalArgumentException("Listener already added");
    }
    return this;
  }

  /**
   * Set expiry calculator to use. If {@link #expiryDuration(long, java.util.concurrent.TimeUnit)}
   * is set to 0 then expiry calculation is not used, all entries expire immediately.
   */
  public final Cache2kBuilder<K, V> expiryCalculator(ExpiryCalculator<K, V> c) {
    config.setExpiryCalculator(c);
    return this;
  }

  /**
   * Set expiry calculator to use in case of an exception happened in the {@link CacheSource}.
   */
  public final Cache2kBuilder<K, V> exceptionExpiryCalculator(ExceptionExpiryCalculator<K> c) {
    config.setExceptionExpiryCalculator(c);
    return this;
  }

  /**
   * Sets the internal cache implementation to be used. This is used currently
   * for internal purposes. It will be removed from the general API, since the implementation
   * type is not defined within the api module.
   *
   * @deprecated since 0.23
   */
  public final Cache2kBuilder<K, V> implementation(Class<?> c) {
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
  public final Cache2kBuilder<K, V> refreshAhead(boolean f) {
    config.setRefreshAhead(f);
    return this;
  }

  /**
   * By default the expiry time is not exact, which means, a value might be visible a few
   * milliseconds after the time of expiry. The timing depends on the system load.
   * Switching to true, means an entry is not visible exactly at and after the time of
   * expiry.
   */
  public final Cache2kBuilder<K, V> sharpExpiry(boolean f) {
    config.setSharpExpiry(f);
    return this;
  }

  /**
   * Maximum number of threads this cache should use for calls to the {@link CacheLoader}
   */
  public final Cache2kBuilder<K, V> loaderThreadCount(int v) {
    config.setLoaderThreadCount(v);
    return this;
  }

  /**
   * Ensure that the cache value is stored via direct object reference and that
   * no serialization takes place. Cache clients leveraging the fact that an in heap
   * cache stores object references directly should set this value.
   *
   * <p>If this value is not set to true this means: The key and value objects need to have a
   * defined serialization mechanism and cache may choose to use a tiered storage and transfer
   * data to off heap or disk. For the 1.0 version this value has no effect. It should be
   * used by application developers to future proof the applications with upcoming versions.
   */
  public final Cache2kBuilder<K, V> storeByReference(boolean v) {
    config.setStoreByReference(v);
    return this;
  }

  @Deprecated
  public final CacheConfig getConfig() {
    return null;
  }

  /**
   * Builds a cache with the specified configuration parameters.
   * The builder reused to build caches with similar or identical
   * configuration. The builder is not thread safe.
   */
  public final Cache<K, V> build() {
    return CORE_PROVIDER.createCache(manager, config);
  }

}
