package org.cache2k.core.common;

/*
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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

import org.cache2k.config.CacheType;
import org.cache2k.core.api.CommonMetrics;
import org.cache2k.core.api.InternalCache;
import org.cache2k.core.api.InternalCacheInfo;
import org.cache2k.operation.CacheControl;
import org.cache2k.operation.CacheStatistics;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Provide cache control on top of internal cache.
 * This gets reused by the JMX extension.
 *
 * @author Jens Wilke
 */
public class BaseCacheControl implements CacheControl {

  InternalCache<?, ?> internalCache;
  String qualifiedCacheName;

  public BaseCacheControl(InternalCache<?, ?> cache) {
    internalCache = cache;
    qualifiedCacheName = cache.getQualifiedName();
  }

  @Override
  public CacheType<?> getKeyType() {
    return getCache().getKeyType();
  }

  @Override
  public CacheType<?> getValueType() {
    return getCache().getValueType();
  }

  @Override
  public String getName() {
    return getCache().getName();
  }

  @Override
  public String getManagerName() {
    return getCache().getCacheManager().getName();
  }

  private InternalCache<?, ?> getCache() { return internalCache; }

  private InternalCacheInfo getInfo() { return getCache().getInfo(); }

  @Override
  public long getSize() {
    return getCache().getTotalEntryCount();
  }

  @Override
  public long getEntryCapacity() {
    return getInfo().getHeapCapacity();
  }

  @Override
  public long getCapacityLimit() {
    return isWeigherPresent() ? getTotalWeight() : getEntryCapacity();
  }

  @Override
  public long getMaximumWeight() {
    return getInfo().getMaximumWeight();
  }

  @Override
  public long getTotalWeight() {
    return getInfo().getTotalWeight();
  }

  @Override
  public Instant getCreatedTime() {
    return Instant.ofEpochMilli(getInfo().getStartedTime());
  }

  @Override
  public Instant getClearedTime() {
    return instantOrNull(getInfo().getClearedTime());
  }


  Instant instantOrNull(long millis) {
    return millis == 0 ? null : Instant.ofEpochMilli(millis);
  }

  @Override
  public String getImplementation() {
    return getInfo().getImplementation();
  }

  public CompletableFuture<Void> clear() {
    getCache().clear();
    return CompletableFuture.completedFuture(null);
  }

  public CompletableFuture<Void> removeAll() {
    getCache().removeAll();
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> changeCapacity(long entryCountOrWeight) {
    getCache().getEviction().changeCapacity(entryCountOrWeight);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public boolean isLoaderPresent() {
    return getCache().isLoaderPresent();
  }

  @Override
  public boolean isWeigherPresent() {
    return getCache().isWeigherPresent();
  }

  @Override
  public boolean isStatisticsEnabled() {
    return !(getCache().getCommonMetrics() instanceof CommonMetrics.BlackHole);
  }

  @Override
  public CacheStatistics sampleStatistics() {
    if (!isStatisticsEnabled()) {
      return null;
    }
    InternalCacheInfo info = getInfo();
    return new AbstractCacheStatistics() {
      @Override
      protected InternalCacheInfo info() {
        return info;
      }
    };
  }

  @Override
  public CompletableFuture<Void> close() {
    getCache().close();
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> destroy() {
    getCache().close();
    return CompletableFuture.completedFuture(null);
  }

}
