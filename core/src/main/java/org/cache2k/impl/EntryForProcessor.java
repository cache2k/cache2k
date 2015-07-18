package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2015 headissue GmbH, Munich
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

import org.cache2k.MutableCacheEntry;

/**
 * Mutable entry implementation for entry processor
 *
 * @author Jens Wilke; created: 2015-05-02
 */
public class EntryForProcessor<K, T> implements MutableCacheEntry<K, T> {

  BaseCache.BulkOperation operation;
  int index;
  long lastModification;
  K key;
  T value;
  boolean removed;
  boolean updated = false;
  boolean needsFetch;
  boolean needsLoad;

  @Override
  public void setValue(T v) {
    lastModification = System.currentTimeMillis();
    value = v;
    removed = false;
    needsLoad = false;
    needsFetch = false;
    updated = true;
  }

  @Override
  public void setException(Throwable ex) {
    lastModification = System.currentTimeMillis();
    value = (T) new ExceptionWrapper(ex);
    removed = false;
    needsLoad = false;
    needsFetch = false;
    updated = true;
  }

  @Override
  public void remove() {
    lastModification = System.currentTimeMillis();
    value = null;
    removed = true;
    updated = true;
    needsFetch = needsLoad = false;
  }

  @Override
  public K getKey() {
    return key;
  }

  @Override
  public T getValue() {
    if (needsFetch) {
      operation.loadAndFetch(this);
      needsFetch = false;
    }
    if (value instanceof ExceptionWrapper) {
      return null;
    }
    return value;
  }

  @Override
  public boolean exists() {
    if (needsLoad) {
      throw new UnsupportedOperationException("storage: load only case need implementation");
    }
    return !(needsFetch || removed);
  }

  @Override
  public Throwable getException() {
    if (needsFetch) {
      operation.loadAndFetch(this);
      needsFetch = false;
    }
    if (value instanceof ExceptionWrapper) {
      return ((ExceptionWrapper) value).getException();
    }
    return null;
  }

  public boolean isRemoved() {
    return removed;
  }

  @Override
  public long getLastModification() {
    return lastModification;
  }

}
