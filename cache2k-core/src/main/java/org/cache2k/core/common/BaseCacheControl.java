package org.cache2k.core.common;

/*
 * #%L
 * cache2k core implementation
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

import org.cache2k.core.api.CommonMetrics;
import org.cache2k.core.api.InternalCache;
import org.cache2k.core.api.InternalCacheInfo;
import org.cache2k.management.CacheControl;
import org.cache2k.management.CacheStatistics;

import java.util.Date;

/**
 * Provide cache control on top of internal cache.
 * This gets reused by the JMX extension.
 *
 * @author Jens Wilke
 */
public class BaseCacheControl implements CacheControl {

  private final InternalCache cache;

  public BaseCacheControl(InternalCache cache) {
    this.cache = cache;
  }

  @Override
  public String getKeyType() {
    return cache.getKeyType().getTypeName();
  }

  @Override
  public String getValueType() {
    return cache.getValueType().getTypeName();
  }

  @Override
  public String getName() {
    return cache.getName();
  }

  @Override
  public String getManagerName() {
    return cache.getCacheManager().getName();
  }

  private InternalCacheInfo getInfo() { return cache.getInfo(); }

  @Override
  public long getSize() {
    return cache.getTotalEntryCount();
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
  public Date getCreatedTime() {
    return new Date(getInfo().getStartedTime());
  }

  @Override
  public Date getClearedTime() {
    return optionalDate(getInfo().getClearedTime());
  }


  Date optionalDate(long millis) {
    return millis == 0 ? null : new Date(millis);
  }

  @Override
  public String getImplementation() {
    return getInfo().getImplementation();
  }

  public void clear() {
    cache.clear();
  }

  @Override
  public void changeCapacity(long entryCountOrWeight) {
    cache.getEviction().changeCapacity(entryCountOrWeight);
  }

  @Override
  public boolean isLoaderPresent() {
    return cache.isLoaderPresent();
  }

  @Override
  public boolean isWeigherPresent() {
    return cache.isWeigherPresent();
  }

  @Override
  public boolean isStatisticsEnabled() {
    return !(cache.getCommonMetrics() instanceof CommonMetrics.BlackHole);
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
  public void close() {
    cache.close();
  }

  @Override
  public void destroy() {
    cache.close();
  }

}
