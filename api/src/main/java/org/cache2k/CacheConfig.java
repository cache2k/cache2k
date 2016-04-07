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

import org.cache2k.customization.*;
import org.cache2k.customization.ExceptionExpiryCalculator;
import org.cache2k.integration.AdvancedCacheLoader;
import org.cache2k.integration.CacheLoader;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cache configuration. Adheres to bean standard.
 *
 * @author Jens Wilke; created: 2013-06-25
 */
public class CacheConfig<K, V> implements Serializable {

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
  private long expiryMillis  = 10 * 60 * 1000;
  private long exceptionExpiryMillis = -1;
  private boolean keepDataAfterExpired = true;
  private boolean sharpExpiry = false;
  private List<Object> moduleConfiguration;
  private boolean suppressExceptions = true;
  private CacheLoader<K,V> loader;
  private AdvancedCacheLoader<K,V> advancedLoader;
  private int loaderThreadCount;
  private ExpiryCalculator<K,V> expiryCalculator;
  private ExceptionExpiryCalculator<K> exceptionExpiryCalculator;

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
   * @see CacheBuilder#name(String)
   */
  public String getName() {
    return name;
  }

  /**
   *
   * @see CacheBuilder#name(String)
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   *
   * @see CacheBuilder#entryCapacity(int)
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
   * @see CacheBuilder#refreshAhead(boolean)
   */
  public boolean isRefreshAhead() {
    return refreshAhead;
  }

  /**
   *
   * @see CacheBuilder#refreshAhead(boolean)
   */
  public void setRefreshAhead(boolean v) {
    this.refreshAhead = v;
  }

  /**
   * @deprecated use {@link #isRefreshAhead()}
   *
   * @see CacheBuilder#refreshAhead(boolean)
   */
  public boolean isBackgroundRefresh() {
    return refreshAhead;
  }

  /**
   * @deprecated use {@link #setRefreshAhead(boolean)}
   *
   * @see CacheBuilder#refreshAhead(boolean)
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
    setKeyType(CacheType.fromClass(v));
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
    return expiryMillis == -1 || expiryMillis == Long.MAX_VALUE;
  }

  /**
   * Set cache entry don't expiry by time.
   */
  public void setEternal(boolean v) {
    if (v) {
      this.expiryMillis = -1;
    }
  }

  /**
   * @depcrecated use {@link #setExpiryMillis}
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
   * @see org.cache2k.CacheBuilder#exceptionExpiryDuration
   */
  public void setExceptionExpiryMillis(long v) {
    exceptionExpiryMillis = v;
  }

  public boolean isKeepDataAfterExpired() {
    return keepDataAfterExpired;
  }

  /**
   * Expired data is kept in the cache until the entry is evicted by the replacement
   * algorithm. This consumes memory, but if the data is accessed again the previous
   * data can be used by the cache source for optimizing, e.g. for a get if-modified-since.
   *
   * @see org.cache2k.CacheSourceWithMetaInfo
   */
  public void setKeepDataAfterExpired(boolean v) {
    this.keepDataAfterExpired = v;
  }

  public boolean isSharpExpiry() {
    return sharpExpiry;
  }

  /**
   * @see CacheBuilder#sharpExpiry(boolean)
   */
  public void setSharpExpiry(boolean sharpExpiry) {
    this.sharpExpiry = sharpExpiry;
  }

  public boolean isSuppressExceptions() {
    return suppressExceptions;
  }

  /**
   * @see CacheBuilder#suppressExceptions(boolean)
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

  public Class<?> getImplementation() {
    return implementation;
  }

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
   * @see CacheBuilder#loaderThreadCount(int)
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

  public boolean isStoreByReference() {
    return storeByReference;
  }

  /**
   * @see CacheBuilder#storeByReference(boolean)
   */
  public void setStoreByReference(final boolean _storeByReference) {
    storeByReference = _storeByReference;
  }
}
