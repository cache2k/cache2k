package org.cache2k;

/*
 * #%L
 * cache2k api only package
 * %%
 * Copyright (C) 2000 - 2014 headissue GmbH, Munich
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

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * @author Jens Wilke; created: 2013-06-25
 */
public abstract class CacheBuilder<K,T> implements Cloneable {

  private static CacheBuilder PROTO;

  static {
    try {
      PROTO = (CacheBuilder) Class.forName("org.cache2k.impl.CacheBuilderImpl").newInstance();
    } catch (Exception ex) {
      throw new LinkageError("cache2k-core implementation missing", ex);
    }
  }

  @SuppressWarnings("unchecked")
  public static <K,T> CacheBuilder<K,T> newCache(Class<K> _keyType, Class<T> _valueType) {
    CacheBuilder<K,T> cb = null;
    try {
      cb = (CacheBuilder<K,T>) PROTO.clone();
    } catch (CloneNotSupportedException ignored) {  }
    cb.ctor(_keyType, _valueType, null);
    return cb;
  }

  @SuppressWarnings("unchecked")
  public static <K, C extends Collection<T>, T> CacheBuilder<K, C> newCache(
    Class<K> _keyType, Class<C> _collectionType, Class<T> _entryType) {
    CacheBuilder<K,C> cb = null;
    try {
      cb = (CacheBuilder<K,C>) PROTO.clone();
    } catch (CloneNotSupportedException ignored) { }
    cb.ctor(_keyType, _collectionType, _entryType);
    return cb;
  }

  protected CacheConfig config;
  protected CacheSource cacheSource;
  protected CacheSourceWithMetaInfo cacheSourceWithMetaInfo;
  protected RefreshController refreshController;
  protected ExperimentalBulkCacheSource experimentalBulkCacheSource;

  /** Builder is constructed from prototype */
  protected void ctor(Class<K> _keyType, Class<T> _valueType, @Nullable Class<?> _entryType) {
    config = new CacheConfig();
    config.setValueType(_valueType);
    config.setKeyType(_keyType);
    config.setEntryType(_entryType);
  }

  /**
   * Constructs a cache name out of the simple class name and fieldname.
   *
   * a cache name should be unique within an application / cache manager!
   */
  public CacheBuilder<K, T> name(Class<?> _class, String _fieldName) {
    config.setName(_class.getSimpleName() + "." + _fieldName);
    return this;
  }

  public CacheBuilder<K, T> name(Class<?> _class) {
    config.setName(_class.getSimpleName());
    return this;
  }

  /**
   * Constructs a cache name out of the simple class name and fieldname.
   *
   * a cache name should be unique within an application / cache manager!
   */
  public CacheBuilder<K, T> name(Object _containingObject, String _fieldName) {
    return name(_containingObject.getClass(), _fieldName);
  }

  public CacheBuilder<K, T> name(String v) {
    config.setName(v);
    return this;
  }

  public CacheBuilder<K, T> maxSize(int v) {
    config.setMaxSize(v);
    return this;
  }

  public CacheBuilder<K, T> maxSizeBound(int v) {
    config.setMaxSizeHighBound(v);
    return this;
  }

  public CacheBuilder<K, T> backgroundRefresh(boolean f) {
    config.setBackgroundRefresh(f);
    return this;
  }

  public CacheBuilder<K, T> expirySecs(int v) {
    config.setExpirySeconds(v);
    return this;
  }

  public CacheBuilder<K, T> source(CacheSource<K, T> g) {
    cacheSource = g;
    return this;
  }

  public CacheBuilder<K, T> source(CacheSourceWithMetaInfo<K, T> g) {
    cacheSourceWithMetaInfo = g;
    return this;
  }

  public CacheBuilder<K, T> source(ExperimentalBulkCacheSource<K, T> g) {
    experimentalBulkCacheSource = g;
    return this;
  }

  public CacheBuilder<K, T> refreshController(RefreshController c) {
    refreshController = c;
    return this;
  }

  public CacheBuilder<K, T> implementation(Class<?> c) {
    config.setImplementation(c);
    return this;
  }

  public CacheConfig getConfig() {
    return config;
  }


  public abstract Cache<K, T> build();

}
