package org.cache2k.config;

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

import org.cache2k.Cache2kBuilder;
import org.cache2k.Weigher;
import org.cache2k.event.CacheEntryOperationListener;
import org.cache2k.event.CacheLifecycleListener;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.io.AdvancedCacheLoader;
import org.cache2k.io.AsyncCacheLoader;
import org.cache2k.io.CacheLoader;
import org.cache2k.io.CacheWriter;
import org.cache2k.io.ExceptionPropagator;
import org.cache2k.io.ResiliencePolicy;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for a cache2k cache.
 *
 * <p>To create a cache, the {@link Cache2kBuilder} is used. All configuration properties
 * are present on the builder and are documented in this place. Consequently all properties
 * refer to the corresponding builder method.
 *
 * <p>The configuration bean is designed to be serializable. This is used for example to copy
 * default configurations. The builder allows object references to customizations to be set.
 * If this happens the configuration is not serializable. Such configuration is only used for
 * immediate creation of one cache via the builder.
 *
 * <p>The configuration may contain additional beans, called configuration sections, that are
 * used to configure extensions or sub modules.
 *
 * <p>Within the XML configuration of a cache manager different default configuration
 * values may be specified. To get a configuration bean with the effective defaults of
 * a specific manager do {@code Cache2kBuilder.forUnknownTypes().manager(...).toConfiguration()}
 *
 * @author Jens Wilke
 */
@SuppressWarnings("unused")
public class Cache2kConfig<K, V>
  implements ConfigBean<Cache2kConfig<K, V>, Cache2kBuilder<K, V>>, ConfigWithSections {

  /**
   * A marker duration used in {@link #setExpireAfterWrite(Duration)}, to request
   * eternal expiry. The maximum duration after the duration is considered as eternal
   * for our purposes.
   */
  public static final Duration EXPIRY_ETERNAL = Duration.ofMillis(ExpiryTimeValues.ETERNAL);
  /**
   * Marker duration that {@code setEternal(false)} was set.
   */
  public static final Duration EXPIRY_NOT_ETERNAL = Duration.ofMillis(ExpiryTimeValues.ETERNAL - 1);
  public static final long UNSET_LONG = -1;

  private boolean storeByReference;
  private String name;
  private boolean nameWasGenerated;
  private CacheType<K> keyType;
  private CacheType<V> valueType;
  private long entryCapacity = UNSET_LONG;
  private Duration expireAfterWrite = null;
  private Duration timerLag = null;
  private long maximumWeight = UNSET_LONG;
  private int loaderThreadCount;

  private boolean eternal = false;
  private boolean keepDataAfterExpired = false;
  private boolean sharpExpiry = false;
  private boolean strictEviction = false;
  private boolean refreshAhead = false;
  private boolean permitNullValues = false;
  private boolean recordModificationTime = false;
  private boolean boostConcurrency = false;

  private boolean disableStatistics = false;
  private boolean disableMonitoring = false;

  private boolean externalConfigurationPresent = false;

  private CustomizationSupplier<? extends Executor> loaderExecutor;
  private CustomizationSupplier<? extends Executor> refreshExecutor;
  private CustomizationSupplier<? extends Executor> asyncListenerExecutor;
  private CustomizationSupplier<? extends Executor> executor;
  private CustomizationSupplier<? extends ExpiryPolicy<K, V>> expiryPolicy;
  private CustomizationSupplier<? extends ResiliencePolicy<K, V>> resiliencePolicy;
  private CustomizationSupplier<? extends CacheLoader<K, V>> loader;
  private CustomizationSupplier<? extends CacheWriter<K, V>> writer;
  private CustomizationSupplier<? extends AdvancedCacheLoader<K, V>> advancedLoader;
  private CustomizationSupplier<? extends AsyncCacheLoader<K, V>> asyncLoader;
  private CustomizationSupplier<? extends ExceptionPropagator<K>> exceptionPropagator;
  private CustomizationSupplier<? extends Weigher<K, V>> weigher;

  private CustomizationCollection<CacheEntryOperationListener<K, V>> listeners;
  private CustomizationCollection<CacheEntryOperationListener<K, V>> asyncListeners;
  private Collection<CustomizationSupplier<CacheLifecycleListener>> lifecycleListeners;
  private Set<Feature> features;
  private SectionContainer sections;

  /**
   * Construct a config instance setting the type parameters and returning a
   * proper generic type.
   *
   * @see Cache2kBuilder#keyType(Class)
   * @see Cache2kBuilder#valueType(Class)
   */
  public static <K, V> Cache2kConfig<K, V> of(Class<K> keyType, Class<V> valueType) {
    Cache2kConfig<K, V> c = new Cache2kConfig<K, V>();
    c.setKeyType(CacheType.of(keyType));
    c.setValueType(CacheType.of(valueType));
    return c;
  }

  /**
   * Construct a config instance setting the type parameters and returning a
   * proper generic type.
   *
   * @see Cache2kBuilder#keyType(CacheType)
   * @see Cache2kBuilder#valueType(CacheType)
   */
  public static <K, V> Cache2kConfig<K, V> of(CacheType<K> keyType, CacheType<V> valueType) {
    Cache2kConfig<K, V> c = new Cache2kConfig<K, V>();
    c.setKeyType(keyType);
    c.setValueType(valueType);
    return c;
  }

  /**
   * @see Cache2kBuilder#name(String)
   */
  public String getName() {
    return name;
  }

  /**
   *
   * @see Cache2kBuilder#name(String)
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * True if name is generated and not set by the cache client.
   */
  public boolean isNameWasGenerated() {
    return nameWasGenerated;
  }

  public void setNameWasGenerated(boolean v) {
    this.nameWasGenerated = v;
  }

  /**
   *
   * @see Cache2kBuilder#entryCapacity
   */
  public long getEntryCapacity() {
    return entryCapacity;
  }

  public void setEntryCapacity(long v) {
    this.entryCapacity = v;
  }

  /**
   * @see Cache2kBuilder#refreshAhead(boolean)
   */
  public boolean isRefreshAhead() {
    return refreshAhead;
  }

  /**
   * @see Cache2kBuilder#refreshAhead(boolean)
   */
  public void setRefreshAhead(boolean v) {
    this.refreshAhead = v;
  }

  public CacheType<K> getKeyType() {
    return keyType;
  }

  /**
   * @throws NullPointerException if parameter is null
   */
  public static void checkNull(Object obj) {
    if (obj == null) {
      throw new NullPointerException("Null value not allowed");
    }
  }

  /**
   * @see Cache2kBuilder#keyType(CacheType)
   * @see CacheType for a general discussion on types
   */
  public void setKeyType(CacheType<K> v) {
    if (v == null) {
      valueType = null;
      return;
    }
    if (v.isArray()) {
      throw new IllegalArgumentException("Arrays are not supported for keys");
    }
    keyType = v;
  }

  public CacheType<V> getValueType() {
    return valueType;
  }

  /**
   * @see Cache2kBuilder#valueType(CacheType)
   * @see CacheType for a general discussion on types
   */
  public void setValueType(CacheType<V> v) {
    if (v == null) {
      valueType = null;
      return;
    }
    if (v.isArray()) {
      throw new IllegalArgumentException("Arrays are not supported for values");
    }
    valueType = v;
  }

  public Duration getExpireAfterWrite() {
    return expireAfterWrite;
  }

  /**
   * Sets expire after write. The value is capped at {@link #EXPIRY_ETERNAL}, meaning
   * an equal or higher duration is treated as eternal expiry.
   *
   * @see Cache2kBuilder#expireAfterWrite
   */
  public void setExpireAfterWrite(Duration v) {
    if (v == null) {
      v = null;
      return;
    }
    v = durationCeiling(v);
    if (v.isNegative()) {
      throw new IllegalArgumentException("Duration must be positive");
    }
    this.expireAfterWrite = v;
  }

  public boolean isEternal() {
    return eternal;
  }

  public void setEternal(boolean v) {
    this.eternal = v;
  }

  public Duration getTimerLag() {
    return timerLag;
  }

  /**
   * @see Cache2kBuilder#timerLag(long, TimeUnit)
   */
  public void setTimerLag(Duration v) {
    this.timerLag = durationCeiling(v);
  }

  public boolean isKeepDataAfterExpired() {
    return keepDataAfterExpired;
  }

  public long getMaximumWeight() {
    return maximumWeight;
  }

  /**
   * @see Cache2kBuilder#maximumWeight
   */
  public void setMaximumWeight(long v) {
    if (entryCapacity >= 0) {
      throw new IllegalArgumentException(
        "entryCapacity already set, setting maximumWeight is illegal");
    }
    maximumWeight = v;
  }

  /**
   * @see Cache2kBuilder#keepDataAfterExpired(boolean)
   */
  public void setKeepDataAfterExpired(boolean v) {
    this.keepDataAfterExpired = v;
  }

  public boolean isSharpExpiry() {
    return sharpExpiry;
  }

  /**
   * @see Cache2kBuilder#sharpExpiry(boolean)
   */
  public void setSharpExpiry(boolean v) {
    this.sharpExpiry = v;
  }

  /**
   * An external configuration for the cache was found and is applied.
   * This is {@code true} if default values are set via the XML configuration or
   * if there is a specific section for the cache name.
   */
  public boolean isExternalConfigurationPresent() {
    return externalConfigurationPresent;
  }

  public void setExternalConfigurationPresent(boolean v) {
    externalConfigurationPresent = v;
  }

  /**
   * Mutable collection of additional configuration sections
   */
  public SectionContainer getSections() {
    if (sections == null) {
      sections = new SectionContainer();
    }
    return sections;
  }

  /**
   * Adds the collection of sections to the existing list. This method is intended to
   * improve integration with bean configuration mechanisms that use the set method and
   * construct a set or list, like Springs' bean XML configuration.
   */
  public void setSections(Collection<ConfigSection> c) {
    getSections().addAll(c);
  }

  public CustomizationSupplier<? extends CacheLoader<K, V>> getLoader() {
    return loader;
  }

  public void setLoader(CustomizationSupplier<? extends CacheLoader<K, V>> v) {
    loader = v;
  }

  public CustomizationSupplier<? extends AdvancedCacheLoader<K, V>> getAdvancedLoader() {
    return advancedLoader;
  }

  /**
   * @see Cache2kBuilder#loader(AdvancedCacheLoader)
   */
  public void setAdvancedLoader(CustomizationSupplier<? extends AdvancedCacheLoader<K, V>> v) {
    advancedLoader = v;
  }

  public CustomizationSupplier<? extends AsyncCacheLoader<K, V>> getAsyncLoader() {
    return asyncLoader;
  }

  public void setAsyncLoader(CustomizationSupplier<? extends AsyncCacheLoader<K, V>> v) {
    asyncLoader = v;
  }

  public int getLoaderThreadCount() {
    return loaderThreadCount;
  }

  /**
   * @see Cache2kBuilder#loaderThreadCount(int)
   */
  public void setLoaderThreadCount(int v) {
    loaderThreadCount = v;
  }

  public CustomizationSupplier<? extends ExpiryPolicy<K, V>> getExpiryPolicy() {
    return expiryPolicy;
  }

  public void setExpiryPolicy(CustomizationSupplier<? extends ExpiryPolicy<K, V>> v) {
    expiryPolicy = v;
  }

  public CustomizationSupplier<? extends CacheWriter<K, V>> getWriter() {
    return writer;
  }

  /**
   * @see Cache2kBuilder#writer(CacheWriter)
   */
  public void setWriter(CustomizationSupplier<? extends CacheWriter<K, V>> v) {
    writer = v;
  }

  public boolean isStoreByReference() {
    return storeByReference;
  }

  /**
   * @see Cache2kBuilder#storeByReference(boolean)
   */
  public void setStoreByReference(boolean v) {
    storeByReference = v;
  }

  public CustomizationSupplier<? extends ExceptionPropagator<K>> getExceptionPropagator() {
    return exceptionPropagator;
  }

  /**
   * @see Cache2kBuilder#exceptionPropagator(ExceptionPropagator)
   */
  public void setExceptionPropagator(CustomizationSupplier<? extends ExceptionPropagator<K>> v) {
    exceptionPropagator = v;
  }

  /**
   * A set of listeners. Listeners added in this collection will be
   * executed in a synchronous mode, meaning, further processing for
   * an entry will stall until a registered listener is executed.
   * The expiry will be always executed asynchronously.
   *
   * <p>A listener can be added by adding it to the collection.
   * Duplicate (in terms of equal objects) listeners will be ignored.
   *
   * @return Mutable collection of listeners
   */
  public CustomizationCollection<CacheEntryOperationListener<K, V>> getListeners() {
    if (listeners == null) {
      listeners = new DefaultCustomizationCollection<CacheEntryOperationListener<K, V>>();
    }
    return listeners;
  }

  /**
   * @return True if listeners are added to this configuration.
   */
  public boolean hasListeners() {
    return listeners != null && !listeners.isEmpty();
  }

  /**
   * Adds the collection of customizations to the existing list. This method is intended to
   * improve integration with bean configuration mechanisms that use the set method and
   * construct a set or list, like Springs' bean XML configuration.
   */
  public void setListeners(
    Collection<CustomizationSupplier<CacheEntryOperationListener<K, V>>> c) {
    getListeners().addAll(c);
  }

  /**
   * A set of listeners. A listener can be added by adding it to the collection.
   * Duplicate (in terms of equal objects) listeners will be ignored.
   *
   * @return Mutable collection of listeners
   */
  public CustomizationCollection<CacheEntryOperationListener<K, V>> getAsyncListeners() {
    if (asyncListeners == null) {
      asyncListeners = new DefaultCustomizationCollection<CacheEntryOperationListener<K, V>>();
    }
    return asyncListeners;
  }

  /**
   * @return True if listeners are added to this configuration.
   */
  public boolean hasAsyncListeners() {
    return asyncListeners != null && !asyncListeners.isEmpty();
  }

  /**
   * Adds the collection of customizations to the existing list. This method is intended to
   * improve integration with bean configuration mechanisms that use the set method and
   * construct a set or list, like Springs' bean XML configuration.
   */
  public void setAsyncListeners(
    Collection<CustomizationSupplier<CacheEntryOperationListener<K, V>>> c) {
    getAsyncListeners().addAll(c);
  }

  /**
   * A set of listeners. A listener can be added by adding it to the collection.
   * Duplicate (in terms of equal objects) listeners will be ignored.
   *
   * @return Mutable collection of listeners
   */
  public Collection<CustomizationSupplier<? extends CacheLifecycleListener>> getLifecycleListeners() {
    if (lifecycleListeners == null) {
      lifecycleListeners = new DefaultCustomizationCollection<CacheLifecycleListener>();
    }
    return (Collection<CustomizationSupplier<? extends CacheLifecycleListener>>) (Object)
      lifecycleListeners;
  }

  /**
   * @return True if listeners are added to this configuration.
   */
  public boolean hasLifecycleListeners() {
    return lifecycleListeners != null && !lifecycleListeners.isEmpty();
  }

  /**
   * Adds the collection of customizations to the existing list. This method is intended to
   * improve integration with bean configuration mechanisms that use the set method and
   * construct a set or list, like Springs' bean XML configuration.
   */
  public void setLifecycleListeners(
    Collection<CustomizationSupplier<? extends CacheLifecycleListener>> c) {
    getLifecycleListeners().addAll(c);
  }

  public Set<Feature> getFeatures() {
    if (features == null) {
      features = new HashSet<>();
    }
    return features;
  }

  public boolean hasFeatures() {
    return features != null && !features.isEmpty();
  }

  public void setFeatures(Set<? extends Feature> v) {
    getFeatures().addAll(v);
  }

  public CustomizationSupplier<? extends ResiliencePolicy<K, V>> getResiliencePolicy() {
    return resiliencePolicy;
  }

  /**
   * @see Cache2kBuilder#resiliencePolicy
   */
  public void setResiliencePolicy(CustomizationSupplier<? extends ResiliencePolicy<K, V>> v) {
    resiliencePolicy = v;
  }

  public boolean isStrictEviction() {
    return strictEviction;
  }

  /**
   * @see Cache2kBuilder#strictEviction(boolean)
   */
  public void setStrictEviction(boolean v) {
    strictEviction = v;
  }

  public boolean isPermitNullValues() {
    return permitNullValues;
  }

  /**
   * @see Cache2kBuilder#permitNullValues(boolean)
   */
  public void setPermitNullValues(boolean v) {
    permitNullValues = v;
  }

  public boolean isDisableStatistics() {
    return disableStatistics;
  }

  /**
   * @see Cache2kBuilder#disableStatistics
   */
  public void setDisableStatistics(boolean v) {
    disableStatistics = v;
  }

  public CustomizationSupplier<? extends Executor> getLoaderExecutor() {
    return loaderExecutor;
  }

  /**
   * @see Cache2kBuilder#loaderExecutor(Executor)
   */
  public void setLoaderExecutor(CustomizationSupplier<? extends Executor> v) {
    loaderExecutor = v;
  }

  public boolean isRecordModificationTime() {
    return recordModificationTime;
  }

  /**
   * @see Cache2kBuilder#recordModificationTime
   */
  public void setRecordModificationTime(boolean v) {
    recordModificationTime = v;
  }

  public CustomizationSupplier<? extends Executor> getRefreshExecutor() {
    return refreshExecutor;
  }

  /**
   * @see Cache2kBuilder#refreshExecutor(Executor)
   */
  public void setRefreshExecutor(CustomizationSupplier<? extends Executor> v) {
    refreshExecutor = v;
  }

  public CustomizationSupplier<? extends Executor> getExecutor() {
    return executor;
  }

  /**
   * @see Cache2kBuilder#executor(Executor)
   */
  public void setExecutor(CustomizationSupplier<? extends Executor> v) {
    executor = v;
  }

  public CustomizationSupplier<? extends Executor> getAsyncListenerExecutor() {
    return asyncListenerExecutor;
  }

  /**
   * @see Cache2kBuilder#asyncListenerExecutor(Executor)
   */
  public void setAsyncListenerExecutor(CustomizationSupplier<? extends Executor> v) {
    asyncListenerExecutor = v;
  }

  public CustomizationSupplier<? extends Weigher<K, V>> getWeigher() {
    return weigher;
  }

  /**
   * @see Cache2kBuilder#weigher(Weigher)
   */
  public void setWeigher(CustomizationSupplier<? extends Weigher<K, V>> v) {
    /*
    if (entryCapacity >= 0) {
      throw new IllegalArgumentException(
        "entryCapacity already set, specifying a weigher is illegal");
    }
    */
    weigher = v;
  }

  public boolean isBoostConcurrency() {
    return boostConcurrency;
  }

  /**
   * @see Cache2kBuilder#boostConcurrency(boolean)
   */
  public void setBoostConcurrency(boolean v) {
    boostConcurrency = v;
  }

  public boolean isDisableMonitoring() {
    return disableMonitoring;
  }

  /**
   * @see Cache2kBuilder#disableMonitoring(boolean)
   */
  public void setDisableMonitoring(boolean disableMonitoring) {
    this.disableMonitoring = disableMonitoring;
  }

  private Duration durationCeiling(Duration v) {
    if (v != null && EXPIRY_ETERNAL.compareTo(v) <= 0) {
      v = EXPIRY_ETERNAL;
    }
    return v;
  }

  /**
   * Creates a cache builder from the configuration.
   */
  public Cache2kBuilder<K, V> builder() {
    return Cache2kBuilder.of(this);
  }

}
