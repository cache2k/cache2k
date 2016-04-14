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
import org.cache2k.customization.ExceptionExpiryCalculator;
import org.cache2k.event.CacheEntryOperationListener;
import org.cache2k.integration.AdvancedCacheLoader;
import org.cache2k.integration.CacheLoader;
import org.cache2k.integration.CacheWriter;
import org.cache2k.integration.ExceptionPropagator;

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
public class CacheConfig<K, V> implements Serializable {

  public final long EXPIRY_MILLIS_ETERNAL = Long.MAX_VALUE;

  private boolean storeByReference;
  private String name;
  private CacheTypeDescriptor keyType;
  private CacheTypeDescriptor valueType;
  private Class<?> implementation;
  private int maxSize = 2000;
  private int entryCapacity = 2000;
  private int maxSizeHighBound = Integer.MAX_VALUE;
  private int maxSizeLowBound = 0;
  private int heapEntryCapacity = -1;
  private boolean refreshAhead = false;
  private long expiryMillis  = -1;
  private long exceptionExpiryMillis = -1;
  private boolean keepValueAfterExpired = true;
  private boolean sharpExpiry = false;
  private List<Object> moduleConfiguration;
  private boolean suppressExceptions = true;
  private int loaderThreadCount;
  private ExpiryCalculator<K,V> expiryCalculator;
  private ExceptionExpiryCalculator<K> exceptionExpiryCalculator;
  private CacheLoader<K,V> loader;
  private CacheWriter<K,V> writer;
  private AdvancedCacheLoader<K,V> advancedLoader;
  private ExceptionPropagator exceptionPropagator;
  private Collection<CacheEntryOperationListener<K,V>> listeners;
  private Collection<CacheEntryOperationListener<K,V>> asyncListeners;

  /**
   * Construct a config instance setting the type parameters and returning a
   * proper generic type. See the respective setters for more information on
   * the key/value types.
   *
   * @see #setKeyType(Class)
   * @see #setValueType(Class)
   */
  public static <K,V> CacheConfig<K, V> of(Class<K> keyType, Class<V> valueType) {
    CacheConfig c = new CacheConfig();
    c.setKeyType(keyType);
    c.setValueType(valueType);
    return (CacheConfig<K, V>) c;
  }

  /**
   * Construct a config instance setting the type parameters and returning a
   * proper generic type. See the respective setters for more information on
   * the key/value types.
   *
   * @see #setKeyType(Class)
   * @see #setValueType(Class)
   */
  public static <K,V> CacheConfig<K, V> of(Class<K> keyType, CacheTypeDescriptor<V> valueType) {
    CacheConfig c = new CacheConfig();
    c.setKeyType(keyType);
    c.setValueType(valueType);
    return (CacheConfig<K, V>) c;
  }

  /**
   * Construct a config instance setting the type parameters and returning a
   * proper generic type. See the respective setters for more information on
   * the key/value types.
   *
   * @see #setKeyType(Class)
   * @see #setValueType(Class)
   */
  public static <K,V> CacheConfig<K, V> of(CacheTypeDescriptor<K> keyType, Class<V> valueType) {
    CacheConfig c = new CacheConfig();
    c.setKeyType(keyType);
    c.setValueType(valueType);
    return (CacheConfig<K, V>) c;
  }

  /**
   * Construct a config instance setting the type parameters and returning a
   * proper generic type. See the respective setters for more information on
   * the key/value types.
   *
   * @see #setKeyType(Class)
   * @see #setValueType(Class)
   */
  public static <K,V> CacheConfig<K, V> of(CacheTypeDescriptor<K> keyType, CacheTypeDescriptor<V> valueType) {
    CacheConfig c = new CacheConfig();
    c.setKeyType(keyType);
    c.setValueType(valueType);
    return (CacheConfig<K, V>) c;
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
   * @see Cache2kBuilder#entryCapacity(int)
   */
  public int getEntryCapacity() {
    return entryCapacity;
  }

  public void setEntryCapacity(int v) {
    this.entryCapacity = v;
  }

  /**
   * @deprecated Use {@link #getEntryCapacity()}
   */
  public int getMaxSize() {
    return entryCapacity;
  }

  /**
   * @deprecated Use {@link #setEntryCapacity(int)}
   */
  public void setMaxSize(int v) {
    this.entryCapacity = v;
  }

  /**
   * @deprecated not used.
   */
  public int getMaxSizeHighBound() {
    return maxSizeHighBound;
  }

  /**
   * @deprecated not used.
   */
  public void setMaxSizeHighBound(int maxSizeHighBound) {
    if (maxSize > maxSizeHighBound) {
      maxSize = maxSizeHighBound;
    }
    this.maxSizeHighBound = maxSizeHighBound;
  }

  /**
   * @deprecated not used.
   */
  public int getMaxSizeLowBound() {
    return maxSizeLowBound;
  }

  /**
   * @deprecated not used.
   */
  public void setMaxSizeLowBound(int maxSizeLowBound) {
    if (maxSize < maxSizeLowBound) {
      maxSize = maxSizeLowBound;
    }
    this.maxSizeLowBound = maxSizeLowBound;
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

  public CacheTypeDescriptor getKeyType() {
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
   * contains also generic type parameters by providing a {@link CacheType} where the cache can extract
   * the type information.
   * </p>
   *
   * @see CacheType
   * @see #setKeyType(CacheTypeDescriptor)
   */
  public void setKeyType(Class<?> v) {
    checkNull(v);
    setKeyType(new CacheTypeDescriptor.OfClass(v));
  }

  /**
   * Set more detailed type information of the cache key, containing possible generic type arguments.
   *
   * @see #setKeyType(Class) for a general discussion on types
   */
  public void setKeyType(CacheTypeDescriptor v) {
    checkNull(v);
    if (keyType != null && !v.equals(keyType)) {
      throw new IllegalArgumentException("Key type may only set once.");
    }
    if (v.isArray()) {
      throw new IllegalArgumentException("Arrays are not supported for keys");
    }
    keyType = v.getBeanRepresentation();
  }

  public CacheTypeDescriptor<V> getValueType() {
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
    setValueType(new CacheTypeDescriptor.OfClass(v));
  }

  public void setValueType(CacheTypeDescriptor v) {
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
    return expiryMillis == -1 || expiryMillis == EXPIRY_MILLIS_ETERNAL;
  }

  /**
   * Set cache entry don't expiry by time.
   */
  public void setEternal(boolean v) {
    if (v) {
      this.expiryMillis = EXPIRY_MILLIS_ETERNAL;
    }
  }

  /**
   * @deprecated use {@link #setExpiryMillis}
   */
  public void setExpirySeconds(int v) {
    if (v == -1 || v == Integer.MAX_VALUE) {
      expiryMillis = -1;
    }
    expiryMillis = v * 1000L;
  }

  public int getExpirySeconds() {
    if (isEternal()) {
      return -1;
    }
    return (int) (expiryMillis / 1000);
  }

  public long getExpiryMillis() {
    return expiryMillis;
  }

  /**
   * The expiry value of all entries. If an entry specific expiry calculation is
   * determined this is the maximum expiry time. A value of -1 switches expiry off, that
   * means entries are kept for an eternal time, a value of 0 switches caching off.
   */
  public void setExpiryMillis(long expiryMillis) {
    this.expiryMillis = expiryMillis;
  }

  public long getExceptionExpiryMillis() {
    return exceptionExpiryMillis;
  }

  /**
   * @see Cache2kBuilder#exceptionExpiryDuration
   */
  public void setExceptionExpiryMillis(long v) {
    exceptionExpiryMillis = v;
  }

  public boolean isKeepValueAfterExpired() {
    return keepValueAfterExpired;
  }

  /**
   * @see Cache2kBuilder#keepValueAfterExpired(boolean)
   */
  public void setKeepValueAfterExpired(boolean v) {
    this.keepValueAfterExpired = v;
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

  public List<Object> getModuleConfiguration() {
    return moduleConfiguration;
  }

  public void setModuleConfiguration(List<Object> moduleConfiguration) {
    this.moduleConfiguration = moduleConfiguration;
  }

  /**
   * @deprecated since 0.25
   */
  public Class<?> getImplementation() {
    return implementation;
  }

  /**
   * @deprecated since 0.25
   */
  public void setImplementation(Class<?> cacheImplementation) {
    this.implementation = cacheImplementation;
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

  public ExceptionExpiryCalculator<K> getExceptionExpiryCalculator() {
    return exceptionExpiryCalculator;
  }

  public void setExceptionExpiryCalculator(final ExceptionExpiryCalculator<K> _exceptionExpiryCalculator) {
    exceptionExpiryCalculator = _exceptionExpiryCalculator;
  }

  public ExpiryCalculator<K, V> getExpiryCalculator() {
    return expiryCalculator;
  }

  public void setExpiryCalculator(final ExpiryCalculator<K, V> _expiryCalculator) {
    expiryCalculator = _expiryCalculator;
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

}
