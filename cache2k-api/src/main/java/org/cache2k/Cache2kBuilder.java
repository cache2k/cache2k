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

import org.cache2k.config.Cache2kConfig;
import org.cache2k.config.CacheType;
import org.cache2k.config.ConfigBean;
import org.cache2k.config.ConfigBuilder;
import org.cache2k.config.WithSection;
import org.cache2k.config.SectionBuilder;
import org.cache2k.config.CustomizationReferenceSupplier;
import org.cache2k.config.CustomizationSupplier;
import org.cache2k.config.ConfigSection;
import org.cache2k.config.ToggleFeature;
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
import java.util.function.Consumer;
import java.util.function.Function;

import static org.cache2k.config.Cache2kConfig.checkNull;

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
 * <p>This builder can also be used to alter and build a configuration object that
 * can be retrieved with {@link #config()}
 *
 * @author Jens Wilke
 * @since 1.0
 */
public class Cache2kBuilder<K, V>
  implements ConfigBuilder<Cache2kBuilder<K, V>, Cache2kConfig<K, V>> {

  private static final String MSG_NO_TYPES =
    "Use Cache2kBuilder.forUnknownTypes(), to construct a builder with no key and value types";

  /**
   * Create a new cache builder for a cache that has no type restrictions
   * or to set the type information later via the builder methods {@link #keyType} or
   * {@link #valueType}.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Cache2kBuilder<Object, Object> forUnknownTypes() {
    return new Cache2kBuilder(null, null);
  }

  /**
   * Create a new cache builder for key and value types of classes with no generic parameters.
   *
   * @see #keyType(Class)
   * @see #valueType(Class)
   */
  public static <K, V> Cache2kBuilder<K, V> of(Class<K> keyType, Class<V> valueType) {
    return new Cache2kBuilder<K, V>(CacheType.of(keyType), CacheType.of(valueType));
  }

  /**
   * Create a builder from the configuration bean. The builder is not assigned
   * to a cache manager and any default configurations, if present, will not be
   * applied.
   */
  public static <K, V> Cache2kBuilder<K, V> of(Cache2kConfig<K, V> c) {
    Cache2kBuilder<K, V> cb = new Cache2kBuilder<K, V>(c);
    return cb;
  }

  private CacheType<K> keyType;
  private CacheType<V> valueType;
  private Cache2kConfig<K, V> config = null;
  private CacheManager manager = null;

  private Cache2kBuilder(Cache2kConfig<K, V> cfg) {
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
    keyType = (CacheType<K>) CacheType.of(types[0]);
    valueType = (CacheType<V>) CacheType.of(types[1]);
    if (Object.class.equals(keyType.getType()) &&
      Object.class.equals(valueType.getType())) {
      throw new IllegalArgumentException(MSG_NO_TYPES);
    }
  }

  private Cache2kBuilder(CacheType<K> keyType, CacheType<V> valueType) {
    this.keyType = keyType;
    this.valueType = valueType;
  }

  private void withConfig(Cache2kConfig<K, V> cfg) {
    config = cfg;
  }

  /**
   * Bind to default manager if not set before. Read in default configuration.
   */
  @SuppressWarnings("unchecked")
  private Cache2kConfig<K, V> cfg() {
    if (config == null) {
      if (manager == null) {
        manager = CacheManager.getInstance();
      }
      config = CacheManager.PROVIDER.getDefaultConfig(manager);
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
    me.cfg().setKeyType(CacheType.of(t));
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
    me.cfg().setValueType(CacheType.of(t));
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
    me.cfg().setKeyType(t);
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
    me.cfg().setValueType(t);
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
    cfg().setName(uniqueName + '~' + clazz.getName() + "." + fieldName);
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
    cfg().setName(clazz.getName() + "." + fieldName);
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
    cfg().setName(clazz.getName());
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
    cfg().setName(v);
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
    cfg().setKeepDataAfterExpired(v);
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
    cfg().setEntryCapacity(v);
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
   * @throws IllegalArgumentException in case a previous setting is reset
   */
  public final Cache2kBuilder<K, V> eternal(boolean v) {
    cfg().setEternal(v);
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
   * <p>A value of {@code 0} or {@link org.cache2k.expiry.ExpiryTimeValues#NOW} means
   * every entry should expire immediately. This can be useful to disable caching via
   * configuration.
   *
   * <p>A value of {@code Long.MAX_VALUE} milliseconds or more is treated as
   * eternal expiry.
   *
   * @throws IllegalArgumentException if {@link #eternal(boolean)} was set to true
   * @see <a href="https://cache2k.org/docs/latest/user-guide.html#expiry-and-refresh">
   *   cache2k user guide - Expiry and Refresh</a>
   */
  public final Cache2kBuilder<K, V> expireAfterWrite(long v, TimeUnit u) {
    cfg().setExpireAfterWrite(toDuration(v, u));
    return this;
  }

  /**
   * Change the maximum lag time for timer events. Timer events are used for
   * expiry and refresh operations. The default is approximately one second.
   */
  public final Cache2kBuilder<K, V> timerLag(long v, TimeUnit u) {
    cfg().setTimerLag(toDuration(v, u));
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
    checkNull(ep);
    ExceptionPropagator<K> newPropagator = new ExceptionPropagator<K>() {
      @Override
      public RuntimeException propagateException(LoadExceptionInfo<K> newInfo) {
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
    cfg().setExceptionPropagator(wrapCustomizationInstance(ep));
    return this;
  }

  /** Wraps to factory but passes on nulls. */
  private static <T> CustomizationReferenceSupplier<T> wrapCustomizationInstance(T obj) {
    return new CustomizationReferenceSupplier<T>(obj);
  }

  @Deprecated
  public final Cache2kBuilder<K, V> loader(
    final org.cache2k.integration.AsyncCacheLoader<K, V> oldLoader) {
    checkNull(oldLoader);
    AsyncCacheLoader<K, V> newLoader = new AsyncCacheLoader<K, V>() {
      @Override
      public void load(K key, Context<K, V> context, Callback<V> callback) throws Exception {
        org.cache2k.integration.AsyncCacheLoader.Context<K, V> oldContext =
          new org.cache2k.integration.AsyncCacheLoader.Context<K, V>() {
            @Override
            public long getLoadStartTime() {
              return context.getStartTime();
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
    cfg().setLoader(wrapCustomizationInstance(l));
    return this;
  }

  /**
   * Enables read through operation and sets a cache loader. Different loader types
   * are available: {@link CacheLoader}, {@link AdvancedCacheLoader}.
   *
   * @see CacheLoader for general discussion on cache loaders
   */
  public final Cache2kBuilder<K, V> loader(AdvancedCacheLoader<K, V> l) {
    cfg().setAdvancedLoader(wrapCustomizationInstance(l));
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
    cfg().setAsyncLoader(wrapCustomizationInstance(l));
    return this;
  }

  /**
   * Enables read through operation and sets a cache loader
   *
   * @see CacheLoader for general discussion on cache loaders
   */
  @SuppressWarnings("unchecked")
  public final Cache2kBuilder<K, V> wrappingLoader(AdvancedCacheLoader<K, LoadDetail<V>> l) {
    cfg().setAdvancedLoader((
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
    cfg().setAdvancedLoader((
      CustomizationSupplier<AdvancedCacheLoader<K, V>>) (Object) wrapCustomizationInstance(l));
    return this;
  }

  /**
   * Enables write through operation and sets a writer customization that gets
   * called synchronously upon cache mutations. By default write through is not enabled.
   */
  public final Cache2kBuilder<K, V> writer(CacheWriter<K, V> w) {
    cfg().setWriter(wrapCustomizationInstance(w));
    return this;
  }

  /**
   * Listener that is called after a cache is closed. This is mainly used for the JCache
   * integration.
   */
  public final Cache2kBuilder<K, V> addCacheClosedListener(CacheClosedListener listener) {
    cfg().getLifecycleListeners().add(wrapCustomizationInstance(listener));
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
    cfg().getListeners().add(wrapCustomizationInstance(listener));
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
    cfg().getAsyncListeners().add(wrapCustomizationInstance(listener));
    return this;
  }

  /**
   * Set expiry policy to use.
   *
   * <p>If this is specified the maximum expiry time is still limited to the value in
   * {@link #expireAfterWrite}. If {@link #expireAfterWrite(long, java.util.concurrent.TimeUnit)}
   * is set to 0 then expiry calculation is not used, all entries expire immediately.
   */
  public final Cache2kBuilder<K, V> expiryPolicy(ExpiryPolicy<K, V> c) {
    cfg().setExpiryPolicy(wrapCustomizationInstance(c));
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
    cfg().setRefreshAhead(f);
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
    cfg().setSharpExpiry(f);
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
    cfg().setLoaderThreadCount(v);
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
    cfg().setStoreByReference(v);
    return this;
  }

  /**
   * Sets a custom resilience policy to control the cache behavior in the presence
   * of exceptions from the loader. A specified policy will be ignored if
   * {@link #expireAfterWrite} is set to 0.
   */
  public final Cache2kBuilder<K, V> resiliencePolicy(ResiliencePolicy<K, V> v) {
    cfg().setResiliencePolicy(wrapCustomizationInstance(v));
    return this;
  }

  public final Cache2kBuilder<K, V> resiliencePolicy(
    CustomizationSupplier<? extends ResiliencePolicy<K, V>> v) {
    cfg().setResiliencePolicy(v);
    return this;
  }

  /**
   * To increase performance cache2k optimizes the eviction and does eviction in
   * greater chunks. With strict eviction, the eviction is done for one entry
   * as soon as the capacity constraint is met. This is primarily used for
   * testing and evaluation purposes.
   */
  public final Cache2kBuilder<K, V> strictEviction(boolean flag) {
    cfg().setStrictEviction(flag);
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
    cfg().setPermitNullValues(flag);
    return this;
  }

  /**
   * By default statistic gathering is enabled. Switching this to {@code true} will disable all
   * statistics that have significant overhead. Whether the values become visible in monitoring
   * can be controlled via {@link #disableMonitoring(boolean)}
   */
  public final Cache2kBuilder<K, V> disableStatistics(boolean flag) {
    cfg().setDisableStatistics(flag);
    return this;
  }

  /**
   * Enables that time of an refresh (means update or freshness check of a value) is
   * available at {@link MutableCacheEntry#getModificationTime()}.
   *
   * @see MutableCacheEntry#getModificationTime()
   */
  public final Cache2kBuilder<K, V> recordModificationTime(boolean flag) {
    cfg().setRecordModificationTime(flag);
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
    cfg().setBoostConcurrency(f);
    return this;
  }

  /**
   * Disables reporting of cache metrics to monitoring systems or management.
   * This should be set, e.g. if a cache is created dynamically and
   * intended to be short lived. All extensions for monitoring or management
   * respect this parameter.
   */
  public final Cache2kBuilder<K, V> disableMonitoring(boolean f) {
    cfg().setDisableMonitoring(f);
    return this;
  }

  /**
   * Thread pool / executor service to use for triggered load operations. If no executor is
   * specified the cache will create a thread pool, if needed.
   *
   * @see #loaderThreadCount(int)
   */
  public final Cache2kBuilder<K, V> loaderExecutor(Executor v) {
    cfg().setLoaderExecutor(new CustomizationReferenceSupplier<Executor>(v));
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
    cfg().setRefreshExecutor(new CustomizationReferenceSupplier<Executor>(v));
    return this;
  }

  /**
   * Executor for asynchronous operations. On Java 8 and above the
   * {@link ForkJoinPool#commonPool()} is used by default or an internal
   * executor is used that has unbounded thread capacity otherwise.
   */
  public final Cache2kBuilder<K, V> executor(Executor v) {
    cfg().setExecutor(new CustomizationReferenceSupplier<Executor>(v));
    return this;
  }

  /**
   * Executor for asynchronous listeners. If not configured the common
   * asynchronous executor is used as defined by {@link #executor(Executor)}
   *
   * @see #addAsyncListener(CacheEntryOperationListener)
   */
  public final Cache2kBuilder<K, V> asyncListenerExecutor(Executor v) {
    cfg().setAsyncListenerExecutor(new CustomizationReferenceSupplier<Executor>(v));
    return this;
  }

  /**
   * Set the weigher to be used to calculate the entry weight. The parameter
   * {@link #maximumWeight(long)} needs to be specified as well. Using a weigher has a slightly
   * performance impact on the update of existing entries.
   */
  public final Cache2kBuilder<K, V> weigher(Weigher<K, V> v) {
    cfg().setWeigher(new CustomizationReferenceSupplier<Weigher<K, V>>(v));
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
    cfg().setMaximumWeight(v);
    return this;
  }

  /**
   * Call the consumer with this builder. This can be used to apply configuration
   * fragments within the fluent configuration scheme.
   */
  public final Cache2kBuilder<K, V> setup(Consumer<Cache2kBuilder<K, V>> consumer) {
    consumer.accept(this);
    return this;
  }

  /** Enable a feature */
  public final Cache2kBuilder<K, V> enable(Class<? extends ToggleFeature> feature) {
    ToggleFeature.enable(this, feature);
    return this;
  }

  public final Cache2kBuilder<K, V> disable(Class<? extends ToggleFeature> feature) {
    ToggleFeature.disable(this, feature);
    return this;
  }

  /**
   * Configure a config section. If the section is not existing it a new
   * section is created. If the section is existing the existing instance
   * is modified.
   *
   * @param configSectionClass type of the config section that is created or altered
   * @param builderAction lambda that alters the config section via its builder
   * @param <B> type of the builder for the config section
   * @param <CFG> the type of the config section
   */
  public final <B extends SectionBuilder<B, CFG>, CFG extends ConfigSection<CFG, B>>
    Cache2kBuilder<K, V> with(Class<CFG> configSectionClass, Consumer<B> builderAction) {
    CFG section =
      cfg().getSections().getSection(configSectionClass);
    if (section == null) {
      try {
        section = configSectionClass.getConstructor().newInstance();
      } catch (Exception e) {
        throw new Error("config bean needs working default constructor", e);
      }
      cfg().getSections().add(section);
    }
    builderAction.accept(section.builder());
    return this;
  }

  /**
   * Execute on the underlying configuration object. This can be used to
   * set customization suppliers, like {@link Cache2kConfig#setExecutor(CustomizationSupplier)}
   * instead of instances.
   */
  public Cache2kBuilder<K, V> set(Consumer<Cache2kConfig<K, V>> configAction) {
    configAction.accept(config());
    return this;
  }

  /**
   * Execute setup code for a feature or customization and configure
   * its associated configuration section via its builder.
   *
   * @param setupAction function modifying the configuration
   * @param builderAction function for configuring the customization
   * @param <B> the builder for the customizations' configuration section
   * @param <CFG> the configuration section
   * @param <SUP> the supplier for the customization
   */
  public <B extends SectionBuilder<B, CFG>,
    CFG extends ConfigSection<CFG, B>,
    SUP extends WithSection<CFG, B> & CustomizationSupplier<?>>  Cache2kBuilder<K, V> setupWith(
    Function<Cache2kBuilder<K, V>, SUP> setupAction,
    Consumer<B> builderAction) {
    with(setupAction.apply(this).getConfigClass(), builderAction);
    return this;
  }

  /**
   * Executes setup code for a feature or customization which has additional parameters
   * and configures it via its builder.
   *
   * @param enabler setup function returning am associated configuration
   * @param builderAction function to configure
   */
  public <B extends ConfigBuilder<B, CFG>,
          CFG extends ConfigBean<CFG, B>>
  Cache2kBuilder<K, V> setup(Function<Cache2kBuilder<K, V>, CFG> enabler, Consumer<B> builderAction) {
    builderAction.accept(enabler.apply(this).builder());
    return this;
  }

  /**
   * Enables a toggle feature which has additional parameters and configures it via its builder.
   *
   * @param featureType Type of feature which has additional bean properties
   * @param builderAction function to configure the feature
   */
  public <B extends ConfigBuilder<B, T>, T extends ToggleFeature & ConfigBean<T, B>>
    Cache2kBuilder<K, V> enable(Class<T> featureType, Consumer<B> builderAction) {
    T bean = ToggleFeature.enable(this, featureType);
    builderAction.accept(bean.builder());
    return this;
  }

  /**
   * Enables a feature and configures its associated configuration section via its builder.
   * Be aware that a section might be existing and preconfigured already, the semantics
   * are identical to {@link #with(Class, Consumer)}
   */
  public <B extends SectionBuilder<B, CFG>, T extends ToggleFeature & WithSection<CFG, B>,
    CFG extends ConfigSection<CFG, B>>  Cache2kBuilder<K, V> enableWith(
    Class<T> featureType, Consumer<B> builderAction) {
    T bean = ToggleFeature.enable(this, featureType);
    with(bean.getConfigClass(), builderAction);
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
  @Override
  public final Cache2kConfig<K, V> config() {
    return cfg();
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
    return CacheManager.PROVIDER.createCache(manager, cfg());
  }

}
