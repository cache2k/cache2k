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
import org.cache2k.configuration.CacheTypeCapture;
import org.cache2k.configuration.CacheType;
import org.cache2k.configuration.ConfigurationSection;
import org.cache2k.configuration.ConfigurationSectionBuilder;
import org.cache2k.configuration.CustomizationReferenceSupplier;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.event.CacheEntryOperationListener;
import org.cache2k.integration.AdvancedCacheLoader;
import org.cache2k.integration.CacheLoader;
import org.cache2k.integration.CacheWriter;
import org.cache2k.integration.ExceptionPropagator;
import org.cache2k.integration.ResiliencePolicy;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Builder to create a {@link Cache} instance. The usage is:
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
 * <p>To create a cache without type parameters or {@code Cache<Object,Object>}, use {@link Cache2kBuilder#forUnknownTypes()}.
 *
 * @author Jens Wilke
 * @since 1.0
 */
public class Cache2kBuilder<K, V> {

  private static final String MSG_NO_TYPES = "Use Cache2kBuilder.forUnknownTypes(), to construct a builder with no key and value types";

  /**
   * Create a new cache builder for a cache that has no type restrictions
   * or to set the type information later via the builder methods {@link #keyType} or
   * {@link #valueType}.
   */
  public static Cache2kBuilder forUnknownTypes() {
    return new Cache2kBuilder(null, null);
  }

  /**
   * Create a new cache builder for key and value types are classes with no generic parameters.
   *
   * @see #keyType(Class)
   * @see #valueType(Class)
   */
  public static <K,T> Cache2kBuilder<K,T> of(Class<K> _keyType, Class<T> _valueType) {
    return new Cache2kBuilder<K, T>(CacheTypeCapture.of(_keyType), CacheTypeCapture.of(_valueType));
  }

  /**
   * Create a builder from the configuration.
   */
  public static <K,T> Cache2kBuilder<K, T> of(Cache2kConfiguration<K, T> c) {
    Cache2kBuilder<K,T> cb = new Cache2kBuilder<K, T>(c);
    return cb;
  }

  private CacheType<K> keyType;
  private CacheType<V> valueType;
  private Cache2kConfiguration<K,V> config = null;
  private CacheManager manager = null;

  private Cache2kBuilder(Cache2kConfiguration<K,V> cfg) {
    withConfig(cfg);
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
    Type t = this.getClass().getGenericSuperclass();
    if (!(t instanceof ParameterizedType)) {
      throw new IllegalArgumentException(MSG_NO_TYPES);
    }
    Type[] _types = ((ParameterizedType) t).getActualTypeArguments();
    keyType = (CacheType<K>) CacheTypeCapture.of(_types[0]).getBeanRepresentation();
    valueType = (CacheType<V>) CacheTypeCapture.of(_types[1]).getBeanRepresentation();
    if (Object.class.equals(keyType.getType()) &&
      Object.class.equals(valueType.getType())) {
      throw new IllegalArgumentException(MSG_NO_TYPES);
    }
  }

  private Cache2kBuilder(CacheType<K> _keyType, CacheType<V> _valueType) {
    keyType = _keyType;
    valueType = _valueType;
  }

  private void withConfig(Cache2kConfiguration<K,V> cfg) {
    config = cfg;
  }

  private Cache2kConfiguration<K, V> config() {
    if (config == null) {
      if (manager == null) {
        manager = CacheManager.getInstance();
      }
      config = CacheManager.PROVIDER.getDefaultConfiguration(manager);
      if (keyType != null) {
        config.setKeyType(keyType);
      }
      if (valueType != null) {
        config.setValueType(valueType);
      }
    }
    return config;
  }

  /**
   * The manager, the created cache will belong to. If this is set, it must be the
   * first method called.
   *
   * @param manager The manager the created cache should belong to,
   *                or {@code null} if the default cache manager should be used
   * @throws IllegalStateException if the manager is not provided immediately after the builder is created.
   */
  public final Cache2kBuilder<K, V> manager(CacheManager manager) {
    if (this.manager != null) {
      throw new IllegalStateException("manager() must be first operation on builder.");
    }
    this.manager = manager;
    return this;
  }

  /**
   * The used type of the cache key. A suitable cache key must provide a useful
   * {@code equals} and {@code hashCode} method. Arrays are not valid for cache keys.
   *
   * @throws IllegalArgumentException in case the type is illegal
   * @see CacheType for a general discussion on types
   */
  public final <K2> Cache2kBuilder<K2, V> keyType(Class<K2> t) {
    config().setKeyType(t);
    return (Cache2kBuilder<K2, V>) this;
  }

  /**
   * Sets the value type to use. Arrays are not supported.
   *
   * @throws IllegalArgumentException in case the type is illegal
   * @see CacheType for a general discussion on types
   */
  public final <T2> Cache2kBuilder<K, T2> valueType(Class<T2> t) {
    config().setValueType(t);
    return (Cache2kBuilder<K, T2>) this;
  }

  /**
   * The used type of the cache key. A suitable cache key must provide a useful
   * {@code equals} and {@code hashCode} method. Arrays are not valid for cache keys.
   *
   * @throws IllegalArgumentException in case the type is illegal
   * @see CacheType for a general discussion on types
   */
  public final <K2> Cache2kBuilder<K2, V> keyType(CacheType<K2> t) {
    config().setKeyType(t);
    return (Cache2kBuilder<K2, V>) this;
  }

  /**
   * Sets the value type to use. Arrays are not supported.
   *
   * @throws IllegalArgumentException in case the type is illegal
   * @see CacheType for a general discussion on types
   */
  public final <T2> Cache2kBuilder<K, T2> valueType(CacheType<T2> t) {
    config().setValueType(t);
    return (Cache2kBuilder<K, T2>) this;
  }

  /**
   * Constructs a cache name out of the class name, a field name and a unique name identifying the
   * component in the application. Result example: {@code webImagePool~com.example.ImagePool.id2Image}
   *
   * <p>See {@link #name(String)} for a general discussion about cache names.
   *
   * @param _uniqueName unique name differentiating multiple components of the same type.
   *                    May be {@code null}.
   * @see #name(String)
   */
  public final Cache2kBuilder<K, V> name(String _uniqueName, Class<?> _class, String _fieldName) {
    if (_fieldName == null) {
      throw new NullPointerException();
    }
    if (_uniqueName == null) {
      return name(_class, _fieldName);
    }
    config().setName(_uniqueName + '~' + _class.getName() + "." + _fieldName);
    return this;
  }

  /**
   * Constructs a cache name out of the class name and field name. Result example:
   * {@code com.example.ImagePool.id2Image}
   *
   * <p>See {@link #name(String)} for a general discussion about cache names.
   *
   * @see #name(String)
   */
  public final Cache2kBuilder<K, V> name(Class<?> _class, String _fieldName) {
    if (_fieldName == null) {
      throw new NullPointerException();
    }
    config().setName(_class.getName() + "." + _fieldName);
    return this;
  }

  /**
   * Sets a cache name from the fully qualified class name.
   *
   * <p>See {@link #name(String)} for a general discussion about cache names.
   *
   * @see #name(String)
   */
  public final Cache2kBuilder<K, V> name(Class<?> _class) {
    config().setName(_class.getName());
    return this;
  }

  /**
   * Sets the name of a cache. If a name is specified it must be ensured it is unique within
   * the cache manager. Cache names are used at several places to have a unique ID of a cache.
   * For example, for referencing additional configuration or to register JMX beans.
   *
   * <p>If a name is not specified the cache generates a name automatically. The name is
   * inferred from the call stack trace and contains the simple class name, the method and
   * the line number of the of the caller to <code>build()</code>. The name also contains
   * a random number. Automatically generated names don't allow reliable management, logging and
   * additional configuration of caches. If no name is set, {@link #build()} will always create
   * a new cache with a new unique name within the cache manager. Automatically generated
   * cache names start with the character <code>'_'</code> as prefix to separate the names from the
   * usual class name space.
   *
   * <p>In case of a name collision the cache is generating a unique name by adding a counter value.
   * This behavior may change.
   *
   * <p>Allowed characters for a cache name, are URL non-reserved characters,
   * these are: <code>[A-Z]</code>, <code>[a-z]</code>, <code>[0-9]</code> and <code>[~-_.]</code>,
   * see RFC3986 as well as the characters: <code>[()]</code>. The characters <code>[@, ]</code> are supported
   * as well, but should be avoided.
   *
   * <p>The reason for restricting the characters in names, is that the names may be used to derive
   * other resource names from it, e.g. for file based storage. The cache might not enforce the allowed
   * character set for efficiency reasons.
   *
   * <p>The method is overloaded with variants to provide a naming convention of names.
   *
   * <p>For brevity within log messages and other displays the cache name may be
   * shortened if the manager name is included as prefix.
   *
   * @see Cache#getName()
   */
  public final Cache2kBuilder<K, V> name(String v) {
    config().setName(v);
    return this;
  }

  /**
   * Expired data is kept in the cache until the entry is evicted. This consumes memory,
   * but if the data is accessed again the previous data can be used by the cache loader
   * for optimizing (e.g. if-modified-since for a HTTP request). Default value: false
   *
   * @see AdvancedCacheLoader
   */
  public final Cache2kBuilder<K, V> keepDataAfterExpired(boolean v) {
    config().setKeepDataAfterExpired(v);
    return this;
  }

  /**
   * The maximum number of entries hold by the cache. When the maximum size is reached, by
   * inserting new entries, the cache eviction algorithm will remove one or more entries
   * to keep the size within the configured limit.
   *
   * <p>The value {@code Long.MAX_VALUE} means the capacity is not limited.
   *
   * <p>The default value is: 2000. The default value is conservative, so the application
   * will usually run stable without tuning or setting a reasonable size.
   */
  public final Cache2kBuilder<K, V> entryCapacity(long v) {
    config().setEntryCapacity(v);
    return this;
  }

  /**
   * When set to true, cached values do not expire by time. Entries will need to be removed
   * from the cache explicitly or will be evicted if capacity constraints are reached.

   * <p>Setting eternal to false signals that the data should expire, but there is no
   * predefined expiry value at programmatic level. This value needs to be set by other
   * means, e.g. within a configuration file.
   *
   * <p>The default behavior of the cache is identical to the setting eternal, meaning no
   * expiry.
   *
   * <p>Exceptions: If set to eternal with default setting and if there is no
   * explicit expiry configured for exceptions with {@link #retryInterval(long, TimeUnit)},
   * exceptions will not be cached and expire immediately.
   *
   * @throws IllegalArgumentException in case a previous setting is reset
   */
  public final Cache2kBuilder<K, V> eternal(boolean v) {
    config().setEternal(v);
    return this;
  }

  /**
   * If an exceptions gets thrown by the cache loader, suppress it if there is
   * a previous value. When this is active, and an exception was suppressed
   * the expiry is determined by {@link #retryInterval(long, TimeUnit)}.
   *
   * <p>Setting this to false, will disable suppression or caching (aka resilience).
   * Default value: true
   *
   * <p>Additional information can be found under <b>resilience</b> in
   * the documentation.
   */
  public final Cache2kBuilder<K, V> suppressExceptions(boolean v) {
    config().setSuppressExceptions(v);
    return this;
  }


  /**
   * Time duration after insert or updated an cache entry expires.
   * To switch off time based expiry use {@link #eternal(boolean)}.
   *
   * <p>If an {@link ExpiryPolicy} is specified, the maximum expiry duration
   * will not exceed the value that is specified here.
   *
   * <p>A value of {@code 0} means every entry should expire immediately. Low values or
   * {@code 0} together with read through operation mode with a {@link CacheLoader} should be
   * avoided in production environments.
   *
   * @throws IllegalArgumentException if {@link #eternal(boolean)} was set to true
   */
  public final Cache2kBuilder<K, V> expireAfterWrite(long v, TimeUnit u) {
    config().setExpireAfterWrite(u.toMillis(v));
    return this;
  }

  public final Cache2kBuilder<K, V> exceptionPropagator(ExceptionPropagator<K> ep) {
    config().setExceptionPropagator(wrapCustomizationInstance(ep));
    return this;
  }

  /** Wraps to factory but passes on nulls. */
  private static <T> CustomizationReferenceSupplier<T> wrapCustomizationInstance(T obj) {
    if (obj == null) { return null; }
    return new CustomizationReferenceSupplier<T>(obj);
  }

  public final Cache2kBuilder<K, V> loader(CacheLoader<K, V> l) {
    config().setLoader(wrapCustomizationInstance(l));
    return this;
  }

  public final Cache2kBuilder<K, V> loader(AdvancedCacheLoader<K, V> l) {
    config().setAdvancedLoader(wrapCustomizationInstance(l));
    return this;
  }

  public final Cache2kBuilder<K, V> writer(CacheWriter<K, V> w) {
    config().setWriter(wrapCustomizationInstance(w));
    return this;
  }

  /**
   * Add a listener. The listeners  will be executed in a synchronous mode, meaning,
   * further processing for an entry will stall until a registered listener is executed.
   * The expiry will be always executed asynchronously.
   *
   * @throws IllegalArgumentException if an identical listener is already added.
   * @param listener The listener to add
   */
  public final Cache2kBuilder<K, V> addListener(CacheEntryOperationListener<K,V> listener) {
    boolean _inserted = config().getListeners().add(wrapCustomizationInstance(listener));
    if (!_inserted) {
      throw new IllegalArgumentException("Listener already added");
    }
    return this;
  }

  /**
   * A set of listeners. Listeners added in this collection will be
   * executed in a synchronous mode, meaning, further processing for
   * an entry will stall until a registered listener is executed.
   *
   * @throws IllegalArgumentException if an identical listener is already added.
   * @param listener The listener to add
   */
  public final Cache2kBuilder<K,V> addAsyncListener(CacheEntryOperationListener<K,V> listener) {
    boolean _inserted = config().getAsyncListeners().add(wrapCustomizationInstance(listener));
    if (!_inserted) {
      throw new IllegalArgumentException("Listener already added");
    }
    return this;
  }

  /**
   * Set expiry policy to use.
   *
   * <p>If this is specified the maximum expiry time is still limited to the value in
   * {@link #expireAfterWrite}. If {@link #expireAfterWrite(long, java.util.concurrent.TimeUnit)}
   * is set to 0 then expiry calculation is not used, all entries expire immediately.
   *
   * <p>If no maximum expiry is specified via {@link #expireAfterWrite} at leas the
   * {@link #resilienceDuration} needs to be specified, if resilience should be enabled.
   */
  public final Cache2kBuilder<K, V> expiryPolicy(ExpiryPolicy<K, V> c) {
    config().setExpiryPolicy(wrapCustomizationInstance(c));
    return this;
  }

  /**
   * When true, enable background refresh / refresh ahead. After the expiry time of a value is reached,
   * the loader is invoked to fetch a fresh value. The old value will be returned by the cache, although
   * it is expired, and will be replaced by the new value, once the loader is finished. In the case
   * there are not enough loader threads available, the value will expire immediately and
   * the next {@code get()} request will trigger the load.
   *
   * <p>Once refreshed, the entry is in a trail period. If it is not accessed until the next
   * expiry, no refresh will be done and the entry expires regularly. This means that the
   * time an entry stays within the trail period is determined by the configured expiry time
   * or the the {@code ExpiryPolicy}. In case an entry is not accessed any more it needs to
   * reach the expiry time twice before removed from the cache.
   *
   * <p>The number of threads used to do the refresh are configured via
   * {@link #loaderThreadCount(int)}
   *
   * @see CacheLoader
   * @see #loaderThreadCount(int)
   */
  public final Cache2kBuilder<K, V> refreshAhead(boolean f) {
    config().setRefreshAhead(f);
    return this;
  }

  /**
   * By default the expiry time is not exact, which means, a value might be visible a few
   * milliseconds after the time of expiry. The time lag depends on the system load.
   * Switching to true, means that values will not be visible when the time is reached that
   * {@link ExpiryPolicy} returned.
   */
  public final Cache2kBuilder<K, V> sharpExpiry(boolean f) {
    config().setSharpExpiry(f);
    return this;
  }

  /**
   * If no separate executor is set via {@link #loaderExecutor(Executor)} the cache will
   * create a separate thread pool used exclusively by it. Defines the maximum number of threads
   * this cache should use for calls to the {@link CacheLoader}. The default is one thread
   * per available CPU.
   *
   * <p>If a separate executor is defined the parameter has no effect.
   *
   * @see #loaderExecutor(Executor)
   * @see #prefetchExecutor(Executor)
   */
  public final Cache2kBuilder<K, V> loaderThreadCount(int v) {
    config().setLoaderThreadCount(v);
    return this;
  }

  /**
   * Ensure that the cache value is stored via direct object reference and that
   * no serialization takes place. Cache clients leveraging the fact that an in heap
   * cache stores object references directly should set this value.
   *
   * <p>If this value is not set to true this means: The key and value objects need to have a
   * defined serialization mechanism and the cache may choose to transfer off the heap.
   * For cache2k version 1.0 this value has no effect. It should be
   * used by application developers to future proof the applications with upcoming versions.
   */
  public final Cache2kBuilder<K, V> storeByReference(boolean v) {
    config().setStoreByReference(v);
    return this;
  }

  /**
   * If a loader exception happens, this is the time interval after a
   * retry attempt is made. If not specified, 10% of {@link #maxRetryInterval}.
   */
  public final Cache2kBuilder<K, V> retryInterval(long v, TimeUnit u) {
    config().setRetryInterval(u.toMillis(v));
    return this;
  }

  /**
   * If a loader exception happens, this is the maximum time interval after a
   * retry attempt is made. For retries an exponential backoff algorithm is used.
   * It starts with the retry time and then increases the time to the maximum
   * according to an exponential pattern.
   *
   * <p>By default identical to {@link #resilienceDuration}
   */
  public final Cache2kBuilder<K, V> maxRetryInterval(long v, TimeUnit u) {
    config().setMaxRetryInterval(u.toMillis(v));
    return this;
  }

  /**
   * Time span the cache will suppress loader exceptions if a value is available from
   * a previous load. After the time span is passed the cache will start propagating
   * loader exceptions. If {@link #suppressExceptions} is switched off, this setting
   * has no effect.
   *
   * <p>Defaults to {@link #expireAfterWrite}. If {@link #suppressExceptions}
   * is switched off, this setting has no effect.
   */
  public final Cache2kBuilder<K, V> resilienceDuration(long v, TimeUnit u) {
    config().setResilienceDuration(u.toMillis(v));
    return this;
  }

  /**
   * Sets a custom resilience policy to control the cache behavior in the presence
   * of exceptions from the loader. A specified policy will be ignored if
   * {@link #expireAfterWrite} is set to 0.
   */
  public final Cache2kBuilder<K,V> resiliencePolicy(ResiliencePolicy<K,V> v) {
    config().setResiliencePolicy(wrapCustomizationInstance(v));
    return this;
  }

  /**
   * Add a new configuration sub section.
   *
   * @see org.cache2k.configuration.ConfigurationWithSections
   */
  public final Cache2kBuilder<K, V> with(ConfigurationSectionBuilder<? extends ConfigurationSection>... sectionBuilders) {
    for (ConfigurationSectionBuilder<? extends ConfigurationSection> b : sectionBuilders) {
      config().getSections().add(b.buildConfigurationSection());
    }
    return this;
  }

  /**
   * To increase performance cache2k optimizes the eviction and does eviction in
   * greater chunks. With strict eviction, the eviction is done for one entry
   * as soon as the capacity constraint is met. This is primarily used for
   * testing and evaluation purposes.
   */
  public final Cache2kBuilder<K,V> strictEviction(boolean flag) {
    config().setStrictEviction(flag);
    return this;
  }

  /**
   * When {@code true}, {@code null} values are allowed in the cache. In the default configuration
   * {@code null} values are prohibited.
   *
   * <p>See the chapter in the user guide for details on {@code null} values.
   *
   * @see CacheLoader#load(Object)
   * @see ExpiryPolicy#calculateExpiryTime(Object, Object, long, CacheEntry)
   */
  public final Cache2kBuilder<K,V> permitNullValues(boolean flag) {
    config().setPermitNullValues(flag);
    return this;
  }

  /**
   * By default statistic gathering is enabled. Set true to disable statistics.
   */
  public final Cache2kBuilder<K,V> disableStatistics(boolean flag) {
    config().setDisableStatistics(flag);
    return this;
  }

  /**
   * Number of eviction segments. The default is one or two if the available processor
   * count is more than one. In case the workload has lots of concurrent inserts or eviction
   * the segment count may be increased if the cache eviction becomes the bottle neck.
   * A value higher then the processor count is ineffective. Setting a higher value has
   * a negative impact on the eviction efficiency. There will be hardly any application with
   * a little positive effect when this parameter is set. Usual applications that do mostly
   * concurrent reads, do not need an increase.
   */
  public final Cache2kBuilder<K,V> evictionSegmentCount(int v) {
    config().setEvictionSegmentCount(v);
    return this;
  }

  /**
   * Thread pool / executor service to use for asynchronous load operations. If no executor is specified
   * the cache will create a thread pool, if needed.
   *
   * @see #loaderThreadCount(int)
   * @see #prefetchExecutor(Executor)
   */
  public final Cache2kBuilder<K,V> loaderExecutor(Executor v) {
    config().setLoaderExecutor(new CustomizationReferenceSupplier<Executor>(v));
    return this;
  }

  /**
   * Thread pool / executor service to use for refresh ahead and prefetch operations. If not specified the
   * same refresh ahead operation will use the thread pool defined by {@link #loaderExecutor(Executor)}
   * or a cache local pool is created.
   *
   * @see #loaderThreadCount(int)
   * @see #loaderExecutor(Executor)
   */
  public final Cache2kBuilder<K,V> prefetchExecutor(Executor v) {
    config().setPrefetchExecutor(new CustomizationReferenceSupplier<Executor>(v));
    return this;
  }

  /**
   * Returns the configuration object this builder operates on. Changes to the configuration also
   * will influence the created cache when {@link #build()} is called. The method does not
   * return the effective configuration if additional external/XML configuration is present, since
   * this is applied when {@code build} is called. On the other hand, the method can be used
   * to inspect the effective configuration after {@code build} completed.
   *
   * @return configuration objects with the parameters set in the builder.
   */
  public final Cache2kConfiguration<K,V> toConfiguration() {
    return config();
  }

  /**
   * Builds a cache with the specified configuration parameters.
   * The builder reused to build caches with similar or identical
   * configuration. The builder is not thread safe.
   */
  public final Cache<K, V> build() {
    return CacheManager.PROVIDER.createCache(manager, config());
  }

}
