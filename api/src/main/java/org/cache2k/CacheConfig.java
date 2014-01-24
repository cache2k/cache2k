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

/**
 * Cache configuration. Adheres to bean standard.
 *
 * @author Jens Wilke; created: 2013-06-25
 */
public class CacheConfig {

  private String name;
  private Class<?> keyType;
  private Class<?> valueType;
  private Class<?> entryType;
  private Class<?> implementation;
  private int maxSize = 2000;
  private int maxSizeHighBound = Integer.MAX_VALUE;
  private int maxSizeLowBound = 0;
  private boolean backgroundRefresh = true;
  private int expirySeconds = 10 * 60;
  private boolean keepDataAfterExpired = true;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getMaxSize() {
    return maxSize;
  }

  public void setMaxSize(int maxSize) {
    this.maxSize = maxSize;
  }

  public int getMaxSizeHighBound() {
    return maxSizeHighBound;
  }

  public void setMaxSizeHighBound(int maxSizeHighBound) {
    if (maxSize > maxSizeHighBound) {
      maxSize = maxSizeHighBound;
    }
    this.maxSizeHighBound = maxSizeHighBound;
  }

  public int getMaxSizeLowBound() {
    return maxSizeLowBound;
  }

  public void setMaxSizeLowBound(int maxSizeLowBound) {
    if (maxSize < maxSizeLowBound) {
      maxSize = maxSizeLowBound;
    }
    this.maxSizeLowBound = maxSizeLowBound;
  }

  public boolean isBackgroundRefresh() {
    return backgroundRefresh;
  }

  public void setBackgroundRefresh(boolean backgroundRefresh) {
    this.backgroundRefresh = backgroundRefresh;
  }

  public Class<?> getKeyType() {
    return keyType;
  }

  public void setKeyType(Class<?> keyType) {
    this.keyType = keyType;
  }

  public Class<?> getValueType() {
    return valueType;
  }

  public Class<?> getEntryType() {
    return entryType;
  }

  public void setEntryType(Class<?> entryType) {
    this.entryType = entryType;
  }

  public void setValueType(Class<?> valueType) {
    this.valueType = valueType;
  }

  public int getExpirySeconds() {
    return expirySeconds;
  }

  /**
   * Time to pass until an entry is expired and will not be returned by the cache any
   * more. 0 means expiry is immediately. -1 or {@link Integer#MAX_VALUE} means
   * no expiry.
   */
  public void setExpirySeconds(int expirySeconds) {
    this.expirySeconds = expirySeconds;
  }

  public boolean isKeepDataAfterExpired() {
    return keepDataAfterExpired;
  }

  /**
   * Expired data is kept in the cache until the entry is evicted by the replacement
   * algorithm. This consumes memory, but if the data needs to be fetched again previous
   * data can be used by the cache source for optimizing, e.g. for a get if-modified-since.
   */
  public void setKeepDataAfterExpired(boolean v) {
    this.keepDataAfterExpired = v;
  }

  public Class<?> getImplementation() {
    return implementation;
  }

  public void setImplementation(Class<?> cacheImplementation) {
    this.implementation = cacheImplementation;
  }

}
