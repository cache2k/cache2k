package org.cache2k;

/*
 * #%L
 * cache2k API only package
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
  private int heapEntryCapacity = -1;
  private boolean backgroundRefresh = true;
  private int expirySeconds = 10 * 60;
  private boolean keepDataAfterExpired = true;
  private List<Object> moduleConfiguration;

  public String getName() {
    return name;
  }

  /**
   * Sets the name of a cache. If a name is not specified the caching framework
   * provides a name for the cache.
   *
   * <p>If a name is specified it must be ensured it is unique within the cache
   * manager. A unique name may consist of a namespace and a counter, e.g.
   * "com.company.application.AssetCache-1".
   *
   * <p>Allowed characters for a cache name, are URL non-reserved characters,
   * these are: [A-Z], [a-z], [0-9] and [~-_.-], see RFC3986. The reason for
   * restricting the characters in names, is that the names may be used to derive
   * other resource names from it, e.g. for file based storage.
   *
   * <p>For brevity within log messages and other displays the cache name may be
   * shortened if the manager name is included as prefix.
   */
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

  public boolean isEternal() {
    return expirySeconds == -1 || expirySeconds == Integer.MAX_VALUE;
  }

  /**
   * Set cache entry don't expiry by time.
   */
  public void setEternal(boolean v) {
    if (v) {
      this.expirySeconds = -1;
    }
  }

  /**
   * Time to pass until an entry is expired. Expiry means that an entry is not returned by the
   * cache any more. 0 means expiry is immediately (no caching). -1 or {@link Integer#MAX_VALUE}
   * means no expiry, this is identical to setEternal(true).
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

  public int getHeapEntryCapacity() {
    return heapEntryCapacity;
  }

  /**
   * Maximum number of entries that the cache keeps in the heap.
   * Only relevant if a storage modules is defined.
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

  public void addModuleConfiguration(Object o) {
    if (moduleConfiguration == null) {
      moduleConfiguration = new ArrayList<>();
    }
    moduleConfiguration.add(o);
  }

  public List<StorageConfiguration> getStorageModules() {
    if (moduleConfiguration == null) {
      return Collections.emptyList();
    }
    ArrayList<StorageConfiguration> l = new ArrayList<>();
    for (Object o : moduleConfiguration) {
      if (o instanceof StorageConfiguration) {
        l.add((StorageConfiguration) o);
      }
    }
    return l;
  }

}
