package org.cache2k.configuration;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2017 headissue GmbH, Munich
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
import org.cache2k.expiry.*;
import org.cache2k.event.CacheEntryOperationListener;
import org.cache2k.integration.AdvancedCacheLoader;
import org.cache2k.integration.CacheWriter;
import org.cache2k.integration.ExceptionPropagator;
import org.cache2k.integration.FunctionalCacheLoader;
import org.cache2k.integration.ResiliencePolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

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
 * @author Jens Wilke
 */
@SuppressWarnings("unused")
public class Cache2kConfiguration<K, V> implements ConfigurationBean, ConfigurationWithSections {

  public static final long EXPIRY_NOT_ETERNAL = Long.MAX_VALUE - 1;

  private boolean storeByReference;
  private String name;
  private CacheType<K> keyType;
  private CacheType<V> valueType;
  private long entryCapacity = 2000;
  private boolean strictEviction = false;
  private boolean refreshAhead = false;
  private long expireAfterWrite = -1;
  private long retryInterval = -1;
  private long maxRetryInterval = -1;
  private long resilienceDuration = -1;
  private boolean keepDataAfterExpired = false;
  private boolean sharpExpiry = false;
  private boolean suppressExceptions = true;
  private int loaderThreadCount;
  private boolean permitNullValues = false;
  private boolean disableStatistics = false;
  private boolean disableLastModificationTime = false;
  private boolean externalConfigurationPresent = false;
  private boolean boostConcurrency = false;

  private CustomizationSupplier<Executor> loaderExecutor;
  private CustomizationSupplier<Executor> prefetchExecutor;
  private CustomizationSupplier<Executor> asyncListenerExecutor;
  private CustomizationSupplier<ExpiryPolicy<K,V>> expiryPolicy;
  private CustomizationSupplier<ResiliencePolicy<K,V>> resiliencePolicy;
  private CustomizationSupplier<? extends FunctionalCacheLoader<K,V>> loader;
  private CustomizationSupplier<CacheWriter<K,V>> writer;
  private CustomizationSupplier<AdvancedCacheLoader<K,V>> advancedLoader;
  private CustomizationSupplier<ExceptionPropagator<K>> exceptionPropagator;

  private CustomizationCollection<CacheEntryOperationListener<K,V>> listeners;
  private CustomizationCollection<CacheEntryOperationListener<K,V>> asyncListeners;

  private ConfigurationSectionContainer sections;

  /**
   * Construct a config instance setting the type parameters and returning a
   * proper generic type.
   *
   * @see Cache2kBuilder#keyType(Class)
   * @see Cache2kBuilder#valueType(Class)
   */
  public static <K,V> Cache2kConfiguration<K, V> of(Class<K> keyType, Class<V> valueType) {
    Cache2kConfiguration c = new Cache2kConfiguration();
    c.setKeyType(keyType);
    c.setValueType(valueType);
    return (Cache2kConfiguration<K, V>) c;
  }

  /**
   * Construct a config instance setting the type parameters and returning a
   * proper generic type.
   *
   * @see Cache2kBuilder#keyType(CacheType)
   * @see Cache2kBuilder#valueType(CacheType)
   */
  public static <K,V> Cache2kConfiguration<K, V> of(Class<K> keyType, CacheType<V> valueType) {
    Cache2kConfiguration c = new Cache2kConfiguration();
    c.setKeyType(keyType);
    c.setValueType(valueType);
    return (Cache2kConfiguration<K, V>) c;
  }

  /**
   * Construct a config instance setting the type parameters and returning a
   * proper generic type.
   *
   * @see Cache2kBuilder#keyType(Class)
   * @see Cache2kBuilder#valueType(Class)
   */
  public static <K,V> Cache2kConfiguration<K, V> of(CacheType<K> keyType, Class<V> valueType) {
    Cache2kConfiguration c = new Cache2kConfiguration();
    c.setKeyType(keyType);
    c.setValueType(valueType);
    return (Cache2kConfiguration<K, V>) c;
  }

  /**
   * Construct a config instance setting the type parameters and returning a
   * proper generic type.
   *
   * @see Cache2kBuilder#keyType(CacheType)
   * @see Cache2kBuilder#valueType(CacheType)
   */
  public static <K,V> Cache2kConfiguration<K, V> of(CacheType<K> keyType, CacheType<V> valueType) {
    Cache2kConfiguration c = new Cache2kConfiguration();
    c.setKeyType(keyType);
    c.setValueType(valueType);
    return (Cache2kConfiguration<K, V>) c;
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

  private void checkNull(Object v) {
    if (v == null) {
      throw new NullPointerException("null value not allowed");
    }
  }

  /**
   * @see Cache2kBuilder#keyType(Class)
   * @see CacheType for a general discussion on types
   */
  public void setKeyType(Class<?> v) {
    checkNull(v);
    setKeyType(CacheTypeCapture.of(v));
  }

  /**
   * @see Cache2kBuilder#keyType(CacheType)
   * @see CacheType for a general discussion on types
   */
  public void setKeyType(CacheType v) {
    checkNull(v);
    if (v.isArray()) {
      throw new IllegalArgumentException("Arrays are not supported for keys");
    }
    keyType = v.getBeanRepresentation();
  }

  public CacheType<V> getValueType() {
    return valueType;
  }

  /**
   * @see Cache2kBuilder#valueType(Class)
   * @see CacheType for a general discussion on types
   */
  public void setValueType(Class<?> v) {
    checkNull(v);
    setValueType(CacheTypeCapture.of(v));
  }

  /**
   * @see Cache2kBuilder#valueType(CacheType)
   * @see CacheType for a general discussion on types
   */
  public void setValueType(CacheType v) {
    checkNull(v);
    if (v.isArray()) {
      throw new IllegalArgumentException("Arrays are not supported for values");
    }
    valueType = v.getBeanRepresentation();
  }

  public boolean isEternal() {
    return expireAfterWrite == -1 || expireAfterWrite == ExpiryTimeValues.ETERNAL;
  }

  /**
   * @see Cache2kBuilder#eternal(boolean)
   */
  public void setEternal(boolean v) {
    if (v) {
      setExpireAfterWrite(ExpiryTimeValues.ETERNAL);
    } else {
      setExpireAfterWrite(EXPIRY_NOT_ETERNAL);
    }
  }

  public long getExpireAfterWrite() {
    return expireAfterWrite;
  }

  /**
   * @see Cache2kBuilder#expireAfterWrite
   */
  public void setExpireAfterWrite(long millis) {
    if (millis == expireAfterWrite) {
      return;
    }
    if (expireAfterWrite != -1) {
      if (millis == Expiry.ETERNAL) {
        throw new IllegalArgumentException("not eternal or expiry was set, refusing to reset back to eternal");
      }
      if (expireAfterWrite == Expiry.ETERNAL) {
        throw new IllegalArgumentException("eternal was set, refusing to reset to expire time span");
      }
    }
    this.expireAfterWrite = millis;
  }

  /**
   * @see Cache2kBuilder#retryInterval
   */
  public long getRetryInterval() {
    return retryInterval;
  }

  /**
   * @see Cache2kBuilder#retryInterval
   */
  public void setRetryInterval(long millis) {
    retryInterval = millis;
  }

  /**
   * @see Cache2kBuilder#maxRetryInterval
   */
  public long getMaxRetryInterval() {
    return maxRetryInterval;
  }

  /**
   * @see Cache2kBuilder#maxRetryInterval
   */
  public void setMaxRetryInterval(long millis) {
    maxRetryInterval = millis;
  }

  /**
   * @see Cache2kBuilder#resilienceDuration
   */
  public long getResilienceDuration() {
    return resilienceDuration;
  }

  /**
   * @see Cache2kBuilder#resilienceDuration
   */
  public void setResilienceDuration(long millis) {
    resilienceDuration = millis;
  }

  public boolean isKeepDataAfterExpired() {
    return keepDataAfterExpired;
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

  public boolean isSuppressExceptions() {
    return suppressExceptions;
  }

  /**
   * @see Cache2kBuilder#suppressExceptions(boolean)
   */
  public void setSuppressExceptions(boolean v) {
    this.suppressExceptions = v;
  }

  public boolean isExternalConfigurationPresent() {
    return externalConfigurationPresent;
  }

  /**
   * True, if an external configuration for the cache was found and is applied.
   */
  public void setExternalConfigurationPresent(final boolean v) {
    externalConfigurationPresent = v;
  }

  /**
   * Mutable collection of additional configuration sections
   */
  public ConfigurationSectionContainer getSections() {
    if (sections == null) {
      sections = new ConfigurationSectionContainer();
    }
    return sections;
  }

  public CustomizationSupplier<? extends FunctionalCacheLoader<K,V>> getLoader() {
    return loader;
  }

  public void setLoader(final CustomizationSupplier<? extends FunctionalCacheLoader<K,V>> v) {
    loader = v;
  }

  public CustomizationSupplier<AdvancedCacheLoader<K, V>> getAdvancedLoader() {
    return advancedLoader;
  }

  public void setAdvancedLoader(final CustomizationSupplier<AdvancedCacheLoader<K, V>> v) {
    advancedLoader = v;
  }

  public int getLoaderThreadCount() {
    return loaderThreadCount;
  }

  /**
   * @see Cache2kBuilder#loaderThreadCount(int)
   */
  public void setLoaderThreadCount(final int v) {
    loaderThreadCount = v;
  }

  public CustomizationSupplier<ExpiryPolicy<K, V>> getExpiryPolicy() {
    return expiryPolicy;
  }

  public void setExpiryPolicy(final CustomizationSupplier<ExpiryPolicy<K, V>> _expiryPolicy) {
    expiryPolicy = _expiryPolicy;
  }

  public CustomizationSupplier<CacheWriter<K, V>> getWriter() {
    return writer;
  }

  /**
   * @see Cache2kBuilder#writer(CacheWriter)
   */
  public void setWriter(final CustomizationSupplier<CacheWriter<K, V>> v) {
    writer = v;
  }

  public boolean isStoreByReference() {
    return storeByReference;
  }

  /**
   * @see Cache2kBuilder#storeByReference(boolean)
   */
  public void setStoreByReference(final boolean v) {
    storeByReference = v;
  }

  public CustomizationSupplier<ExceptionPropagator<K>> getExceptionPropagator() {
    return exceptionPropagator;
  }

  /**
   * @see Cache2kBuilder#exceptionPropagator(ExceptionPropagator)
   */
  public void setExceptionPropagator(final CustomizationSupplier<ExceptionPropagator<K>> v) {
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
  public CustomizationCollection<CacheEntryOperationListener<K,V>> getListeners() {
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
   * A set of listeners. A listener can be added by adding it to the collection.
   * Duplicate (in terms of equal objects) listeners will be ignored.
   *
   * @return Mutable collection of listeners
   */
  public CustomizationCollection<CacheEntryOperationListener<K,V>> getAsyncListeners() {
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

  public CustomizationSupplier<ResiliencePolicy<K, V>> getResiliencePolicy() {
    return resiliencePolicy;
  }

  /**
   * @see Cache2kBuilder#resiliencePolicy
   */
  public void setResiliencePolicy(final CustomizationSupplier<ResiliencePolicy<K, V>> _resiliencePolicy) {
    resiliencePolicy = _resiliencePolicy;
  }

  public boolean isStrictEviction() {
    return strictEviction;
  }

  /**
   * @see Cache2kBuilder#strictEviction(boolean)
   */
  public void setStrictEviction(final boolean v) {
    strictEviction = v;
  }

  public boolean isPermitNullValues() {
    return permitNullValues;
  }

  /**
   * @see Cache2kBuilder#permitNullValues(boolean)
   */
  public void setPermitNullValues(final boolean v) {
    permitNullValues = v;
  }

  public boolean isDisableStatistics() {
    return disableStatistics;
  }

  /**
   * @see Cache2kBuilder#disableStatistics
   */
  public void setDisableStatistics(final boolean v) {
    disableStatistics = v;
  }

  public CustomizationSupplier<Executor> getLoaderExecutor() {
    return loaderExecutor;
  }

  public boolean isDisableLastModificationTime() {
    return disableLastModificationTime;
  }

  /**
   * @see Cache2kBuilder#disableLastModificationTime
   */
  public void setDisableLastModificationTime(final boolean v) {
    disableLastModificationTime = v;
  }

  /**
   * @see Cache2kBuilder#loaderExecutor(Executor)
   */
  public void setLoaderExecutor(final CustomizationSupplier<Executor> v) {
    loaderExecutor = v;
  }

  public CustomizationSupplier<Executor> getPrefetchExecutor() {
    return prefetchExecutor;
  }

  /**
   * @see Cache2kBuilder#prefetchExecutor(Executor)
   */
  public void setPrefetchExecutor(final CustomizationSupplier<Executor> v) {
    prefetchExecutor = v;
  }

  public CustomizationSupplier<Executor> getAsyncListenerExecutor() {
    return asyncListenerExecutor;
  }

  /**
   * @see Cache2kBuilder#asyncListenerExecutor(Executor)
   */
  public void setAsyncListenerExecutor(final CustomizationSupplier<Executor> v) {
    asyncListenerExecutor = v;
  }

  public boolean isBoostConcurrency() {
    return boostConcurrency;
  }

  /**
   * @see Cache2kBuilder#boostConcurrency(boolean)
   */
  public void setBoostConcurrency(final boolean v) {
    boostConcurrency = v;
  }


}
