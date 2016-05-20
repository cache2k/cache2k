package org.cache2k.configuration;

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

import org.cache2k.Cache2kBuilder;
import org.cache2k.expiry.*;
import org.cache2k.event.CacheEntryOperationListener;
import org.cache2k.integration.AdvancedCacheLoader;
import org.cache2k.integration.CacheLoader;
import org.cache2k.integration.CacheWriter;
import org.cache2k.integration.ExceptionPropagator;
import org.cache2k.integration.ResiliencePolicy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Cache configuration. Adheres to bean standard.
 *
 * @author Jens Wilke; created: 2013-06-25
 */
public class Cache2kConfiguration<K, V> implements Serializable {

  public final long EXPIRY_MILLIS_ETERNAL = Long.MAX_VALUE;

  private boolean storeByReference;
  private String name;
  private CacheType<K> keyType;
  private CacheType<V> valueType;
  private Class<?> implementation;

  private long entryCapacity = 2000;
  private boolean strictEviction = false;
  private int heapEntryCapacity = -1;
  private boolean refreshAhead = false;
  private long expireAfterWriteMillis = -1;
  private long retryIntervalMillis = -1;
  private long maxRetryIntervalMillis = -1;
  private long resilienceDurationMillis = -1;
  private boolean keepDataAfterExpired = true;
  private boolean sharpExpiry = false;
  private boolean suppressExceptions = true;
  private int loaderThreadCount;

  private ExpiryPolicy<K,V> expiryPolicy;
  private ResiliencePolicy<K,V> resiliencePolicy;
  private CacheLoader<K,V> loader;
  private CacheWriter<K,V> writer;
  private AdvancedCacheLoader<K,V> advancedLoader;
  private ExceptionPropagator exceptionPropagator;
  private Collection<CacheEntryOperationListener<K,V>> listeners;
  private Collection<CacheEntryOperationListener<K,V>> asyncListeners;

  private List<Object> sections;

  /**
   * Construct a config instance setting the type parameters and returning a
   * proper generic type. See the respective setters for more information on
   * the key/value types.
   *
   * @see #setKeyType(Class)
   * @see #setValueType(Class)
   */
  public static <K,V> Cache2kConfiguration<K, V> of(Class<K> keyType, Class<V> valueType) {
    Cache2kConfiguration c = new Cache2kConfiguration();
    c.setKeyType(keyType);
    c.setValueType(valueType);
    return (Cache2kConfiguration<K, V>) c;
  }

  /**
   * Construct a config instance setting the type parameters and returning a
   * proper generic type. See the respective setters for more information on
   * the key/value types.
   *
   * @see #setKeyType(Class)
   * @see #setValueType(Class)
   */
  public static <K,V> Cache2kConfiguration<K, V> of(Class<K> keyType, CacheType<V> valueType) {
    Cache2kConfiguration c = new Cache2kConfiguration();
    c.setKeyType(keyType);
    c.setValueType(valueType);
    return (Cache2kConfiguration<K, V>) c;
  }

  /**
   * Construct a config instance setting the type parameters and returning a
   * proper generic type. See the respective setters for more information on
   * the key/value types.
   *
   * @see #setKeyType(Class)
   * @see #setValueType(Class)
   */
  public static <K,V> Cache2kConfiguration<K, V> of(CacheType<K> keyType, Class<V> valueType) {
    Cache2kConfiguration c = new Cache2kConfiguration();
    c.setKeyType(keyType);
    c.setValueType(valueType);
    return (Cache2kConfiguration<K, V>) c;
  }

  /**
   * Construct a config instance setting the type parameters and returning a
   * proper generic type. See the respective setters for more information on
   * the key/value types.
   *
   * @see #setKeyType(Class)
   * @see #setValueType(Class)
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
   *
   * @see Cache2kBuilder#refreshAhead(boolean)
   */
  public boolean isRefreshAhead() {
    return refreshAhead;
  }

  /**
   *
   * @see Cache2kBuilder#refreshAhead(boolean)
   */
  public void setRefreshAhead(boolean v) {
    this.refreshAhead = v;
  }

  /**
   * @deprecated use {@link #isRefreshAhead()}
   *
   * @see Cache2kBuilder#refreshAhead(boolean)
   */
  public boolean isBackgroundRefresh() {
    return refreshAhead;
  }

  /**
   * @deprecated use {@link #setRefreshAhead(boolean)}
   *
   * @see Cache2kBuilder#refreshAhead(boolean)
   */
  public void setBackgroundRefresh(boolean v) {
    refreshAhead = v;
  }

  public CacheType<K> getKeyType() {
    return keyType;
  }

  void checkNull(Object v) {
    if (v == null) {
      throw new NullPointerException("null value not allowed");
    }
  }

  /**
   * The used type of the cache key. A suitable cache key must provide a useful equals() and hashCode() method.
   * Arrays are not valid for cache keys.
   *
   * <p><b>About types:</b><br/>
   *
   * The type may be set only once and cannot be changed during the lifetime of a cache. If no type information
   * is provided it defaults to the Object class. The provided type information might be used inside the cache
   * for optimizations and as well as to select appropriate default transformation schemes for copying
   * objects or marshalling. The correct types are not strictly enforced at all levels by the cache
   * for performance reasons. The cache application guarantees that only the specified types will be used.
   * The cache will check the type compatibility at critical points, e.g. when reconnecting to an external storage.
   * Generic types: An application may provide more detailed type information to the cache, which
   * contains also generic type parameters by providing a {@link CacheTypeCapture} where the cache can extract
   * the type information.
   * </p>
   *
   * @see CacheTypeCapture
   * @see #setKeyType(CacheType)
   */
  public void setKeyType(Class<?> v) {
    checkNull(v);
    setKeyType(CacheTypeCapture.of(v));
  }

  /**
   * Set more detailed type information of the cache key, containing possible generic type arguments.
   *
   * @see #setKeyType(Class) for a general discussion on types
   */
  public void setKeyType(CacheType v) {
    checkNull(v);
    if (keyType != null && !v.equals(keyType)) {
      throw new IllegalArgumentException("Key type may only set once.");
    }
    if (v.isArray()) {
      throw new IllegalArgumentException("Arrays are not supported for keys");
    }
    keyType = v.getBeanRepresentation();
  }

  public CacheType<V> getValueType() {
    return valueType;
  }

  /**
   * The used type of the cache value. A suitable cache key must provide a useful equals() and hashCode() method.
   * Arrays are not valid for cache keys.
   *
   * @see #setKeyType(Class) for a general discussion on types
   */
  public void setValueType(Class<?> v) {
    checkNull(v);
    setValueType(CacheTypeCapture.of(v));
  }

  public void setValueType(CacheType v) {
    checkNull(v);
    if (valueType != null && !v.equals(valueType)) {
      throw new IllegalArgumentException("Value type may only set once.");
    }
    if (v.isArray()) {
      throw new IllegalArgumentException("Arrays are not supported for values");
    }
    valueType = v.getBeanRepresentation();
  }

  public boolean isEternal() {
    return expireAfterWriteMillis == -1 || expireAfterWriteMillis == EXPIRY_MILLIS_ETERNAL;
  }

  /**
   * Set cache entry don't expiry by time.
   */
  public void setEternal(boolean v) {
    if (v) {
      this.expireAfterWriteMillis = EXPIRY_MILLIS_ETERNAL;
    }
  }

  /**
   * @deprecated use {@link #setExpireAfterWriteMillis}
   */
  public void setExpirySeconds(int v) {
    if (v == -1 || v == Integer.MAX_VALUE) {
      expireAfterWriteMillis = -1;
    }
    expireAfterWriteMillis = v * 1000L;
  }

  /**
   * @deprecated use {@link #getExpireAfterWriteMillis}
   */
  public int getExpirySeconds() {
    if (isEternal()) {
      return -1;
    }
    return (int) (expireAfterWriteMillis / 1000);
  }

  public long getExpireAfterWriteMillis() {
    return expireAfterWriteMillis;
  }

  /**
   * The expiry value of all entries. If an entry specific expiry calculation is
   * determined this is the maximum expiry time. A value of -1 switches expiry off, that
   * means entries are kept for an eternal time, a value of 0 switches caching off.
   */
  public void setExpireAfterWriteMillis(long v) {
    this.expireAfterWriteMillis = v;
  }

  /**
   * @see Cache2kBuilder#retryInterval
   */
  public long getRetryIntervalMillis() {
    return retryIntervalMillis;
  }

  /**
   * @see Cache2kBuilder#retryInterval
   */
  public void setRetryIntervalMillis(long v) {
    retryIntervalMillis = v;
  }

  /**
   * @see Cache2kBuilder#maxRetryInterval
   */
  public long getMaxRetryIntervalMillis() {
    return maxRetryIntervalMillis;
  }

  /**
   * @see Cache2kBuilder#maxRetryInterval
   */
  public void setMaxRetryIntervalMillis(final long _maxRetryIntervalMillis) {
    maxRetryIntervalMillis = _maxRetryIntervalMillis;
  }

  /**
   * @see Cache2kBuilder#resilienceDuration
   */
  public long getResilienceDurationMillis() {
    return resilienceDurationMillis;
  }

  /**
   * @see Cache2kBuilder#resilienceDuration
   */
  public void setResilienceDurationMillis(final long _resilienceDurationMillis) {
    resilienceDurationMillis = _resilienceDurationMillis;
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
  public void setSharpExpiry(boolean sharpExpiry) {
    this.sharpExpiry = sharpExpiry;
  }

  public boolean isSuppressExceptions() {
    return suppressExceptions;
  }

  /**
   * @see Cache2kBuilder#suppressExceptions(boolean)
   */
  public void setSuppressExceptions(boolean suppressExceptions) {
    this.suppressExceptions = suppressExceptions;
  }

  /**
   * @deprecated since 0.24, only needed for storage
   */
  public int getHeapEntryCapacity() {
    return heapEntryCapacity;
  }

  /**
   * @deprecated since 0.24, only needed for storage
   */
  public void setHeapEntryCapacity(int v) {
    this.heapEntryCapacity = v;
  }

  /**
   * Mutable collection of additional configuration sections
   */
  public Collection<Object> getSections() {
    if (sections == null) {
      sections = new ArrayList<Object>();
    }
    return sections;
  }


  public CacheLoader<K,V> getLoader() {
    return loader;
  }

  public void setLoader(final CacheLoader<K,V> v) {
    loader = v;
  }

  public AdvancedCacheLoader<K, V> getAdvancedLoader() {
    return advancedLoader;
  }

  public void setAdvancedLoader(final AdvancedCacheLoader<K, V> v) {
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

  public ExpiryPolicy<K, V> getExpiryPolicy() {
    return expiryPolicy;
  }

  public void setExpiryPolicy(final ExpiryPolicy<K, V> _expiryPolicy) {
    expiryPolicy = _expiryPolicy;
  }

  public CacheWriter<K, V> getWriter() {
    return writer;
  }

  /**
   * @see Cache2kBuilder#writer(CacheWriter)
   */
  public void setWriter(final CacheWriter<K, V> v) {
    writer = v;
  }

  public boolean isStoreByReference() {
    return storeByReference;
  }

  /**
   * @see Cache2kBuilder#storeByReference(boolean)
   */
  public void setStoreByReference(final boolean _storeByReference) {
    storeByReference = _storeByReference;
  }

  public ExceptionPropagator getExceptionPropagator() {
    return exceptionPropagator;
  }

  /**
   * @see Cache2kBuilder#exceptionPropagator(ExceptionPropagator)
   * @param _exceptionPropagator
   */
  public void setExceptionPropagator(final ExceptionPropagator _exceptionPropagator) {
    exceptionPropagator = _exceptionPropagator;
  }

  /**
   * A set of listeners. Listeners added in this collection will be
   * executed in a synchronous mode, meaning, further processing for
   * an entry will stall until a registered listener is executed.
   * The expiry will be always executed asynchronously.
   *
   * <p>A listener can be added by adding it to the collection.
   * Duplicate (in terms of equal objects) identical listeners will be ignored.
   *
   * @return Mutable collection of listeners
   */
  public Collection<CacheEntryOperationListener<K,V>> getListeners() {
    if (listeners == null) {
      listeners = new HashSet<CacheEntryOperationListener<K,V>>();
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
   * Duplicate (in terms of equal objects) identical listeners will be ignored.
   *
   * @return Mutable collection of listeners
   */
  public Collection<CacheEntryOperationListener<K,V>> getAsyncListeners() {
    if (asyncListeners == null) {
      asyncListeners = new HashSet<CacheEntryOperationListener<K,V>>();
    }
    return asyncListeners;
  }

  /**
   * @return True if listeners are added to this configuration.
   */
  public boolean hasAsyncListeners() {
    return asyncListeners != null && !asyncListeners.isEmpty();
  }

  public ResiliencePolicy<K, V> getResiliencePolicy() {
    return resiliencePolicy;
  }

  public void setResiliencePolicy(final ResiliencePolicy<K, V> _resiliencePolicy) {
    resiliencePolicy = _resiliencePolicy;
  }

  public boolean hasStrictEviction() {
    return strictEviction;
  }

  public void setStrictEviction(final boolean _strictEviction) {
    strictEviction = _strictEviction;
  }
}
