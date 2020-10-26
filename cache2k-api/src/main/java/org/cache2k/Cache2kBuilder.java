package org.cache2k;

/*
 * #%L
 * cache2k API
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

import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.configuration.CacheTypeCapture;
import org.cache2k.configuration.CacheType;
import org.cache2k.configuration.ConfigurationSection;
import org.cache2k.configuration.ConfigurationSectionBuilder;
import org.cache2k.configuration.CustomizationReferenceSupplier;
import org.cache2k.configuration.CustomizationSupplier;
import org.cache2k.event.CacheClosedListener;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.event.CacheEntryOperationListener;
import org.cache2k.io.AdvancedCacheLoader;
import org.cache2k.io.AsyncCacheLoader;
import org.cache2k.io.CacheLoader;
import org.cache2k.io.CacheWriter;
import org.cache2k.io.LoadExceptionInfo;
import org.cache2k.io.ExceptionPropagator;
import org.cache2k.integration.LoadDetail;
import org.cache2k.io.ResiliencePolicy;
import org.cache2k.io.CacheLoaderException;
import org.cache2k.processor.MutableCacheEntry;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
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
 * <p>Caches belong to a cache manager. If no cache manager is set explicitly via
 * {@link #manager} the default cache manager will be used, as defined by
 * {@link CacheManager#getInstance()}.
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
 * <p>To create a cache without type parameters or {@code Cache<Object,Object>}, use
 * {@link Cache2kBuilder#forUnknownTypes()}.
 *
 * @author Jens Wilke
 * @since 1.0
 */
public class Cache2kBuilder<K, V> {

  private static final String MSG_NO_TYPES =
    "Use Cache2kBuilder.forUnknownTypes(), to construct a builder with no key and value types";

  /**
   * Create a new cache builder for a cache that has no type restrictions
   * or to set the type information later via the builder methods {@link #keyType} or
   * {@link #valueType}.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Cache2kBuilder forUnknownTypes() {
    return new Cache2kBuilder(null, null);
  }

  /**
   * Create a new cache builder for key and value types of classes with no generic parameters.
   *
   * @see #keyType(Class)
   * @see #valueType(Class)
   */
  public static <K, V> Cache2kBuilder<K, V> of(Class<K> keyType, Class<V> valueType) {
    return new Cache2kBuilder<K, V>(CacheTypeCapture.of(keyType), CacheTypeCapture.of(valueType));
  }

  /**
   * Create a builder from the configuration.
   */
  public static <K, V> Cache2kBuilder<K, V> of(Cache2kConfiguration<K, V> c) {
    Cache2kBuilder<K, V> cb = new Cache2kBuilder<K, V>(c);
    return cb;
  }

  private CacheType<K> keyType;
  private CacheType<V> valueType;
  private Cache2kConfiguration<K, V> config = null;
  private CacheManager manager = null;

  private Cache2kBuilder(Cache2kConfiguration<K, V> cfg) {
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
    Type[] types = ((ParameterizedType) t).getActualTypeArguments();
    keyType = (CacheType<K>) CacheTypeCapture.of(types[0]).getBeanRepresentation();
    valueType = (CacheType<V>) CacheTypeCapture.of(types[1]).getBeanRepresentation();
    if (Object.class.equals(keyType.getType()) &&
      Object.class.equals(valueType.getType())) {
      throw new IllegalArgumentException(MSG_NO_TYPES);
    }
  }

  private Cache2kBuilder(CacheType<K> keyType, CacheType<V> valueType) {
    this.keyType = keyType;
    this.valueType = valueType;
  }

  private void withConfig(Cache2kConfiguration<K, V> cfg) {
    config = cfg;
  }

  @SuppressWarnings("unchecked")
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
   * @throws IllegalStateException if the manager is not provided immediately after the builder
   *         is created.
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
  @SuppressWarnings("unchecked")
  public final <K2> Cache2kBuilder<K2, V> keyType(Class<K2> t) {
    Cache2kBuilder<K2, V> me = (Cache2kBuilder<K2, V>) this;
    me.config().setKeyType(t);
    return me;
  }

  /**
   * Sets the value type to use. Arrays are not supported.
   *
   * @throws IllegalArgumentException in case the type is illegal
   * @see CacheType for a general discussion on types
   */
  @SuppressWarnings("unchecked")
  public final <V2> Cache2kBuilder<K, V2> valueType(Class<V2> t) {
    Cache2kBuilder<K, V2> me = (Cache2kBuilder<K, V2>) this;
    me.config().setValueType(t);
    return me;
  }

  /**
   * The used type of the cache key. A suitable cache key must provide a useful
   * {@code equals} and {@code hashCode} method. Arrays are not valid for cache keys.
   *
   * @throws IllegalArgumentException in case the type is illegal
   * @see CacheType for a general discussion on types
   */
  @SuppressWarnings("unchecked")
  public final <K2> Cache2kBuilder<K2, V> keyType(CacheType<K2> t) {
    Cache2kBuilder<K2, V> me = (Cache2kBuilder<K2, V>) this;
    me.config().setKeyType(t);
    return me;
  }

  /**
   * Sets the value type to use. Arrays are not supported.
   *
   * @throws IllegalArgumentException in case the type is illegal
   * @see CacheType for a general discussion on types
   */
  @SuppressWarnings("unchecked")
  public final <V2> Cache2kBuilder<K, V2> valueType(CacheType<V2> t) {
    Cache2kBuilder<K, V2> me = (Cache2kBuilder<K, V2>) this;
    me.config().setValueType(t);
    return me;
  }

  /**
   * Constructs a cache name out of the class name, a field name and a unique name identifying the
   * component in the application. Result example:
   * {@code webImagePool~com.example.ImagePool.id2Image}
   *
   * <p>See {@link #name(String)} for a general discussion about cache names.
   *
   * @param uniqueName unique name differentiating multiple components of the same type.
   *                    May be {@code null}.
   * @see #name(String)
   */
  public final Cache2kBuilder<K, V> name(String uniqueName, Class<?> clazz, String fieldName) {
    if (fieldName == null) {
      throw new NullPointerException();
    }
    if (uniqueName == null) {
      return name(clazz, fieldName);
    }
    config().setName(uniqueName + '~' + clazz.getName() + "." + fieldName);
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
  public final Cache2kBuilder<K, V> name(Class<?> clazz, String fieldName) {
    if (fieldName == null) {
      throw new NullPointerException();
    }
    config().setName(clazz.getName() + "." + fieldName);
    return this;
  }

  /**
   * Sets a cache name from the fully qualified class name.
   *
   * <p>See {@link #name(String)} for a general discussion about cache names.
   *
   * @see #name(String)
   */
  public final Cache2kBuilder<K, V> name(Class<?> clazz) {
    config().setName(clazz.getName());
    return this;
  }

  /**
   * Sets the name of a cache. If a name is specified it must be ensured it is unique within
   * the cache manager. Cache names are used at several places to have a unique ID of a cache.
   * For example, for referencing additional configuration or to register JMX beans.
   *
   * <p>If a name is not specified the cache generates a name automatically. The name is
   * inferred from the call stack trace and contains the simple class name, the method and
   * the line number of the of the caller to {@code build()}. The name also contains
   * a random number. Automatically generated names don't allow reliable management, logging and
   * additional configuration of caches. If no name is set, {@link #build()} will always create
   * a new cache with a new unique name within the cache manager. Automatically generated
   * cache names start with the character {@code '_'} as prefix to separate the names from the
   * usual class name space.
   *
   * <p>For maximum compatibility cache names should only be composed with the characters
   * {@code [-_.a-zA-Z0-9]}. The characters {@code {}|\^&=";:<>*?/} are not allowed in a cache name.
   * The reason for restricting the characters in names, is that the names may be used to derive
   * other resource names from it, e.g. for file based storage. The characters {@code *} and
   * {@code ?} are used for wildcards in JMX and cannot be used in an object name.
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
   * for optimizing (e.g. if-modified-since for a HTTP request). Default value: {@code false}
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
   * <p>The default value is 2000. The default value is conservative, so the application
   * will usually run stable without tuning or setting a reasonable size.
   */
  public final Cache2kBuilder<K, V> entryCapacity(long v) {
    config().setEntryCapacity(v);
    return this;
  }

  /**
   * When set to {@code true}, cached values do not expire by time. Entries will need to be removed
   * from the cache explicitly or will be evicted if capacity constraints are reached.
   *
   * <p>Setting eternal to {@code false} signals that the data should expire, but there is no
   * predefined expiry value at programmatic level. This value needs to be set by other
   * means, e.g. within a configuration file.
   *
   * <p>The default behavior of the cache is identical to the setting of eternal. Entries will
   * not expire. When eternal was set explicitly it cannot be reset to another value afterwards.
   * This should protect against misconfiguration.
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
   * Suppress an exception from the cache loader, if there is previous data.
   * When a load was not successful, the operation is retried at shorter interval then
   * the normal expiry, see {@link #retryInterval(long, TimeUnit)}.
   *
   * <p>Exception suppression is only active when entries expire (eternal is not true) or the
   * explicit configuration of the timing parameters for resilience, e.g.
   * {@link #resilienceDuration(long, TimeUnit)}. Check the user guide chapter for details.
   *
   * <p>Exception suppression is enabled by default. Setting this to {@code false}, will disable
   * exceptions suppression (aka resilience).
   *
   * @see <a href="https://cache2k.org/docs/latest/user-guide.html#resilience-and-exceptions">
   *   cache2k user guide - Exceptions and Resilience</a>
   */
  public final Cache2kBuilder<K, V> suppressExceptions(boolean v) {
    config().setSuppressExceptions(v);
    return this;
  }


  /**
   * Time duration after insert or updated an cache entry expires.
   * To switch off time based expiry use {@link #eternal(boolean)}. The expiry
   * happens via a timer event and may lag approximately one second by default, see
   * {@link #timerLag(long, TimeUnit)}. For exact expiry specify an
   * {@link ExpiryPolicy} and enable {@link #sharpExpiry(boolean)}.
   *
   * <p>If an {@link ExpiryPolicy} is specified in combination to this value,
   * the maximum expiry duration is capped to the value specified here.
   *
   * <p>A value of {@code 0} means every entry should expire immediately. This can be useful
   * to disable caching via configuration.
   *
   * @throws IllegalArgumentException if {@link #eternal(boolean)} was set to true
   * @see <a href="https://cache2k.org/docs/latest/user-guide.html#expiry-and-refresh">
   *   cache2k user guide - Expiry and Refresh</a>
   */
  public final Cache2kBuilder<K, V> expireAfterWrite(long v, TimeUnit u) {
    config().setExpireAfterWrite(toDuration(v, u));
    return this;
  }

  /**
   * Change the maximum lag time for timer events. Timer events are used for
   * expiry and refresh operations. The default is approximately one second.
   */
  public final Cache2kBuilder<K, V> timerLag(long v, TimeUnit u) {
    config().setTimerLag(toDuration(v, u));
    return this;
  }

  private static Duration toDuration(long v, TimeUnit u) {
    return Duration.ofMillis(u.toMillis(v));
  }

  /**
   * Sets customization for propagating loader exceptions. By default loader exceptions
   * are wrapped into a {@link CacheLoaderException}.
   */
  public final Cache2kBuilder<K, V> exceptionPropagator(
    final org.cache2k.integration.ExceptionPropagator<K> ep) {
    ExceptionPropagator<K> newPropagator = new ExceptionPropagator<K>() {
      @Override
      public RuntimeException propagateEntryLoadException(LoadExceptionInfo<K> newInfo) {
        org.cache2k.integration.ExceptionInformation oldInfo =
          new org.cache2k.integration.ExceptionInformation() {
          @Override
          public org.cache2k.integration.ExceptionPropagator getExceptionPropagator() {
            return ep;
          }

          @Override
          public Throwable getException() {
            return newInfo.getException();
          }

          @Override
          public int getRetryCount() {
            return newInfo.getRetryCount();
          }

          @Override
          public long getSinceTime() {
            return newInfo.getSinceTime();
          }

          @Override
          public long getLoadTime() {
            return newInfo.getLoadTime();
          }

          @Override
          public long getUntil() {
            return newInfo.getUntil();
          }
        };
        return ep.propagateException(newInfo.getKey(), oldInfo);
      }
    };
    exceptionPropagator(newPropagator);
    return this;
  }

  /**
   * Sets customization for propagating loader exceptions. By default loader exceptions
   * are wrapped into a {@link CacheLoaderException}.
   */
  public final Cache2kBuilder<K, V> exceptionPropagator(ExceptionPropagator<K> ep) {
    config().setExceptionPropagator(wrapCustomizationInstance(ep));
    return this;
  }

  /** Wraps to factory but passes on nulls. */
  private static <T> CustomizationReferenceSupplier<T> wrapCustomizationInstance(T obj) {
    if (obj == null) { return null; }
    return new CustomizationReferenceSupplier<T>(obj);
  }

  @Deprecated
  public final Cache2kBuilder<K, V> loader(
    final org.cache2k.integration.AsyncCacheLoader<K, V> oldLoader) {
    AsyncCacheLoader<K, V> newLoader = new AsyncCacheLoader<K, V>() {
      @Override
      public void load(K key, Context<K, V> context, Callback<V> callback) throws Exception {
        org.cache2k.integration.AsyncCacheLoader.Context<K, V> oldContext =
          new org.cache2k.integration.AsyncCacheLoader.Context<K, V>() {
            @Override
            public long getLoadStartTime() {
              return context.getLoadStartTime();
            }

            @Override
            public K getKey() {
              return context.getKey();
            }

            @Override
            public Executor getExecutor() {
              return context.getExecutor();
            }

            @Override
            public Executor getLoaderExecutor() {
              return context.getLoaderExecutor();
            }

            @Override
            public CacheEntry<K, V> getCurrentEntry() {
              return context.getCurrentEntry();
            }
          };
        org.cache2k.integration.AsyncCacheLoader.Callback<V> oldCallback =
          new org.cache2k.integration.AsyncCacheLoader.Callback<V>() {
          @Override
          public void onLoadSuccess(V value) {
            callback.onLoadSuccess(value);
          }

          @Override
          public void onLoadFailure(Throwable t) {
            callback.onLoadFailure(t);
          }
        };
        oldLoader.load(key, oldContext, oldCallback);
      }
    };
    this.loader(newLoader);
    return this;
  }

  /**
   * Enables read through operation and sets a cache loader. Different loader types
   * are available: {@link CacheLoader}, {@link AdvancedCacheLoader}.
   *
   * @see CacheLoader for general discussion on cache loaders
   */
  public final Cache2kBuilder<K, V> loader(CacheLoader<K, V> l) {
    config().setLoader(wrapCustomizationInstance(l));
    return this;
  }

  /**
   * Enables read through operation and sets a cache loader. Different loader types
   * are available: {@link CacheLoader}, {@link AdvancedCacheLoader}.
   *
   * @see CacheLoader for general discussion on cache loaders
   */
  public final Cache2kBuilder<K, V> loader(AdvancedCacheLoader<K, V> l) {
    config().setAdvancedLoader(wrapCustomizationInstance(l));
    return this;
  }

  /**
   * Enables read through operation and sets a cache loader. Different loader types
   * are available: {@link CacheLoader}, {@link AdvancedCacheLoader},
   * {@link AsyncCacheLoader}
   *
   * @see CacheLoader for general discussion on cache loaders
   */
  public final Cache2kBuilder<K, V> loader(AsyncCacheLoader<K, V> l) {
    config().setAsyncLoader(wrapCustomizationInstance(l));
    return this;
  }

  /**
   * Enables read through operation and sets a cache loader
   *
   * @see CacheLoader for general discussion on cache loaders
   */
  @SuppressWarnings("unchecked")
  public final Cache2kBuilder<K, V> wrappingLoader(AdvancedCacheLoader<K, LoadDetail<V>> l) {
    config().setAdvancedLoader((
      CustomizationSupplier<AdvancedCacheLoader<K, V>>) (Object) wrapCustomizationInstance(l));
    return this;
  }

  /**
   * Enables read through operation and sets a cache loader
   *
   * @see CacheLoader for general discussion on cache loaders
   */
  @SuppressWarnings("unchecked")
  public final Cache2kBuilder<K, V> wrappingLoader(CacheLoader<K, LoadDetail<V>> l) {
    config().setAdvancedLoader((
      CustomizationSupplier<AdvancedCacheLoader<K, V>>) (Object) wrapCustomizationInstance(l));
    return this;
  }

  /**
   * Enables write through operation and sets a writer customization that gets
   * called synchronously upon cache mutations. By default write through is not enabled.
   */
  public final Cache2kBuilder<K, V> writer(CacheWriter<K, V> w) {
    config().setWriter(wrapCustomizationInstance(w));
    return this;
  }

  /**
   * Listener that is called after a cache is closed. This is mainly used for the JCache
   * integration.
   */
  public final Cache2kBuilder<K, V> addCacheClosedListener(CacheClosedListener listener) {
    config().getCacheClosedListeners().add(wrapCustomizationInstance(listener));
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
  public final Cache2kBuilder<K, V> addListener(CacheEntryOperationListener<K, V> listener) {
    config().getListeners().add(wrapCustomizationInstance(listener));
    return this;
  }

  /**
   * A set of listeners. Listeners added in this collection will be
   * executed in a asynchronous mode.
   *
   * @throws IllegalArgumentException if an identical listener is already added.
   * @param listener The listener to add
   */
  public final Cache2kBuilder<K, V> addAsyncListener(CacheEntryOperationListener<K, V> listener) {
    config().getAsyncListeners().add(wrapCustomizationInstance(listener));
    return this;
  }

  /**
   * Set expiry policy to use.
   *
   * <p>If this is specified the maximum expiry time is still limited to the value in
   * {@link #expireAfterWrite}. If {@link #expireAfterWrite(long, java.util.concurrent.TimeUnit)}
   * is set to 0 then expiry calculation is not used, all entries expire immediately.
   *
   * <p>If no maximum expiry is specified via {@link #expireAfterWrite} at least the
   * {@link #resilienceDuration} needs to be specified, if resilience should be enabled.
   */
  public final Cache2kBuilder<K, V> expiryPolicy(ExpiryPolicy<K, V> c) {
    config().setExpiryPolicy(wrapCustomizationInstance(c));
    return this;
  }

  /**
   * When {@code true}, enable background refresh / refresh ahead. After the expiry time of a
   * value is reached, the loader is invoked to fetch a fresh value. The old value will be
   * returned by the cache, although it is expired, and will be replaced by the new value,
   * once the loader is finished. In the case there are not enough loader threads available,
   * the value will expire immediately and the next {@code get()} request will trigger the load.
   *
   * <p>Once refreshed, the entry is in a probation period. If it is not accessed until the next
   * expiry, no refresh will be done and the entry expires regularly. This means that the
   * time an entry stays within the probation period is determined by the configured expiry time
   * or the {@code ExpiryPolicy}. In case an entry is not accessed any more it needs to
   * reach the expiry time twice before being removed from the cache.
   *
   * <p>The number of threads used to do the refresh are configured via
   * {@link #loaderThreadCount(int)}
   *
   * <p>By default, refresh ahead is not enabled.
   *
   * @see CacheLoader
   * @see #loaderThreadCount(int)
   */
  public final Cache2kBuilder<K, V> refreshAhead(boolean f) {
    config().setRefreshAhead(f);
    return this;
  }

  /**
   * By default the expiry time is not exact, which means, a value might be visible for up to
   * a second longer after the requested time of expiry. The time lag depends on the system load
   * and the parameter {@link #timerLag(long, TimeUnit)}
   * Switching to {@code true}, means that values will not be visible when the time is reached that
   * {@link ExpiryPolicy} returned. This has no effect on {@link #expireAfterWrite(long, TimeUnit)}.
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
   * @see #refreshExecutor(Executor)
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
    config().setRetryInterval(toDuration(v, u));
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
    config().setMaxRetryInterval(toDuration(v, u));
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
    config().setResilienceDuration(toDuration(v, u));
    return this;
  }

  /**
   * Sets a custom resilience policy to control the cache behavior in the presence
   * of exceptions from the loader. A specified policy will be ignored if
   * {@link #expireAfterWrite} is set to 0.
   */
  public final Cache2kBuilder<K, V> resiliencePolicy(ResiliencePolicy<K, V> v) {
    config().setResiliencePolicy(wrapCustomizationInstance(v));
    return this;
  }

  /**
   * Add a new configuration sub section.
   *
   * @see org.cache2k.configuration.ConfigurationWithSections
   */
  public final Cache2kBuilder<K, V> with(
    ConfigurationSectionBuilder<? extends ConfigurationSection>... sectionBuilders) {
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
  public final Cache2kBuilder<K, V> strictEviction(boolean flag) {
    config().setStrictEviction(flag);
    return this;
  }

  /**
   * When {@code true}, {@code null} values are allowed in the cache. In the default configuration
   * {@code null} values are prohibited.
   *
   * <p>See the chapter in the user guide for details on {@code null} values.
   *
   * <p>When used within Spring, {@code null} are allowed by default.
   *
   * @see CacheLoader#load(Object)
   * @see ExpiryPolicy#calculateExpiryTime(Object, Object, long, CacheEntry)
   */
  public final Cache2kBuilder<K, V> permitNullValues(boolean flag) {
    config().setPermitNullValues(flag);
    return this;
  }

  /**
   * By default statistic gathering is enabled. Switching this to {@code true} will disable all
   * statistics that have significant overhead. Whether the values become accessible with JMX is
   * controlled by {@link #enableJmx(boolean)}.
   */
  public final Cache2kBuilder<K, V> disableStatistics(boolean flag) {
    config().setDisableStatistics(flag);
    return this;
  }

  /**
   * Deprecated since version 1.2. Method has no effect and will be removed in future releases.
   * Time recording is disabled by default and needs to be enabled via
   * {@link #recordRefreshedTime(boolean)}.
   */
  @Deprecated
  public final Cache2kBuilder<K, V> disableLastModificationTime(boolean flag) {
    return this;
  }

  /**
   * Enables that time of an refresh (means update or freshness check of a value) is
   * available at {@link MutableCacheEntry#getModificationTime()}.
   *
   * @see MutableCacheEntry#getModificationTime()
   */
  public final Cache2kBuilder<K, V> recordRefreshedTime(boolean flag) {
    config().setRecordRefreshedTime(flag);
    return this;
  }

  /**
   * When {@code true}, optimize for high core counts and applications that do lots of mutations
   * in the cache. When switched on, the cache will occupy slightly more memory and eviction
   * efficiency may drop slightly. This overhead is negligible for big cache sizes (100K and more).
   *
   * <p>Typical interactive do not need to enable this. May improve concurrency for applications
   * that utilize all cores and cache operations account for most CPU cycles.
   */
  public final Cache2kBuilder<K, V> boostConcurrency(boolean f) {
    config().setBoostConcurrency(f);
    return this;
  }

  /**
   * When {@code true} expose statistics via JMX. Disabled by default. It is possible to enable
   * JMX even there is no cache name specified with {@link #name(String)}, since a name will
   * be generated internally, thus enabling JMX by default for all caches is feasible.
   * As of version 2, JMX support moved to an optional module {@code cache2k-jmx}.
   * If this is not included in the classpath/modulepath, this option has no effect.
   */
  public final Cache2kBuilder<K, V> enableJmx(boolean f) {
    config().setEnableJmx(f);
    return this;
  }

  /**
   * Disables reporting of cache metrics to monitoring systems or management.
   * This should be enabled, e.g. if a cache is created dynamically and
   * intended to be short lived. All extensions for monitoring or management
   * respect this parameter.
   */
  public final Cache2kBuilder<K, V> disableMonitoring(boolean f) {
    config().setDisableMonitoring(f);
    return this;
  }

  /**
   * Thread pool / executor service to use for triggered load operations. If no executor is
   * specified the cache will create a thread pool, if needed.
   *
   * @see #loaderThreadCount(int)
   */
  public final Cache2kBuilder<K, V> loaderExecutor(Executor v) {
    config().setLoaderExecutor(new CustomizationReferenceSupplier<Executor>(v));
    return this;
  }

  /**
   * Thread pool / executor service to use for refresh ahead operations. If not
   * specified the same refresh ahead operation will use the thread pool defined by
   * {@link #loaderExecutor(Executor)} or a cache local pool is created.
   *
   * <p>The executor for refresh operations may reject execution when not enough resources
   * are available. If a refresh is rejected, the cache entry expires normally.
   *
   * @see #loaderThreadCount(int)
   * @see #loaderExecutor(Executor)
   */
  public final Cache2kBuilder<K, V> refreshExecutor(Executor v) {
    config().setRefreshExecutor(new CustomizationReferenceSupplier<Executor>(v));
    return this;
  }

  /**
   * Executor for asynchronous operations. On Java 8 and above the
   * {@link ForkJoinPool#commonPool()} is used by default or an internal
   * executor is used that has unbounded thread capacity otherwise.
   */
  public final Cache2kBuilder<K, V> executor(Executor v) {
    config().setExecutor(new CustomizationReferenceSupplier<Executor>(v));
    return this;
  }

  /**
   * Executor for asynchronous listeners. If not configured the common
   * asynchronous executor is used as defined by {@link #executor(Executor)}
   *
   * @see #addAsyncListener(CacheEntryOperationListener)
   */
  public final Cache2kBuilder<K, V> asyncListenerExecutor(Executor v) {
    config().setAsyncListenerExecutor(new CustomizationReferenceSupplier<Executor>(v));
    return this;
  }

  /**
   * Clock to be used by the cache as time reference.
   */
  public final Cache2kBuilder<K, V> timeReference(TimeReference v) {
    config().setTimeReference(new CustomizationReferenceSupplier<TimeReference>(v));
    return this;
  }

  /**
   * Set the weigher to be used to calculate the entry weight. The parameter
   * {@link #maximumWeight(long)} needs to be specified as well. Using a weigher has a slightly
   * performance impact on the update of existing entries.
   */
  public final Cache2kBuilder<K, V> weigher(Weigher<K, V> v) {
    config().setWeigher(new CustomizationReferenceSupplier<Weigher>(v));
    return this;
  }

  /**
   * Specifies the maximum weight of entries the cache may contain. To obtain the entry weight a
   * {@link Weigher} must be specified via {@link #weigher}.
   * <p>
   * The weight of an entry does not influence which entry is evicted next, but is only used to
   * constrain the capacity of the cache. The cache implementation tries the best to keep the cache
   * within its maximum weight limit, but eviction may kick in before or after reaching the limit.
   * <p>
   * The maximum weight setting cannot be used together with {@link #entryCapacity(long)}.
   */
  public final Cache2kBuilder<K, V> maximumWeight(long v) {
    config().setMaximumWeight(v);
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
  public final Cache2kConfiguration<K, V> toConfiguration() {
    return config();
  }

  /**
   * Get the associated cache manager.
   */
  public final CacheManager getManager() {
    return manager;
  }

  /**
   * Builds a cache with the specified configuration parameters.
   *
   * <p>If XML configuration is present, the section for the cache name is looked up and
   * the configuration in it applied, before the cache is build.
   *
   * @throws IllegalArgumentException if a cache of the same name is already active in the
   *         cache manager
   * @throws IllegalArgumentException if a configuration entry for the named cache is required but
   *         not present
   */
  public final Cache<K, V> build() {
    return CacheManager.PROVIDER.createCache(manager, config());
  }

}
