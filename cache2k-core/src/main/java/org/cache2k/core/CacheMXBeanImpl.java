package org.cache2k.core;

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

import org.cache2k.jmx.CacheMXBean;

import java.util.Date;
import java.util.Iterator;

/**
 * @author Jens Wilke
 */
public class CacheMXBeanImpl implements CacheMXBean {

  private final InternalCache cache;

  @Override
  public String getKeyType() {
    return cache.getKeyType().getTypeName();
  }

  @Override
  public String getValueType() {
    return cache.getValueType().getTypeName();
  }

  public CacheMXBeanImpl(InternalCache cache) {
    this.cache = cache;
  }

  private InternalCacheInfo getInfo() { return cache.getInfo(); }

  @Override
  public long getSize() {
    return getInfo().getSize();
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
  public long getMissCount() {
    return getInfo().getMissCount();
  }

  @Override
  public long getInsertCount() {
    return getInfo().getNewEntryCount();
  }

  @Override
  public long getLoadCount() {
    return getInfo().getLoadCount();
  }

  @Override
  public long getRefreshCount() {
    return getInfo().getRefreshCount();
  }

  @Override
  public long getRefreshFailedCount() {
    return getInfo().getRefreshRejectedCount();
  }

  @Override
  public long getRefreshedHitCount() {
    return getInfo().getRefreshedHitCount();
  }

  @Override
  public long getExpiredCount() {
    return getInfo().getExpiredCount();
  }

  @Override
  public long getEvictedCount() {
    return getInfo().getEvictedCount();
  }

  @Override
  public long getEvictedWeight() {
    return getInfo().getEvictedWeight();
  }

  @Override
  public long getKeyMutationCount() {
    return getInfo().getKeyMutationCount();
  }

  @Override
  public long getLoadExceptionCount() {
    return getInfo().getLoadExceptionCount();
  }

  @Override
  public long getSuppressedLoadExceptionCount() {
    return getInfo().getSuppressedExceptionCount();
  }

  @Override
  public long getGetCount() {
    return getInfo().getGetCount();
  }

  @Override
  public long getPutCount() {
    return getInfo().getPutCount();
  }

  @Override
  public long getClearCount() {
    return getInfo().getClearCount();
  }

  @Override
  public long getRemoveCount() {
    return getInfo().getRemoveCount();
  }

  @Override
  public long getClearedEntriesCount() {
    return getInfo().getClearedEntriesCount();
  }

  @Override
  public double getHitRate() {
    return getInfo().getHitRate();
  }

  @Override
  public int getHashQuality() {
    return -1;
  }

  @Override
  public double getMillisPerLoad() {
    return getInfo().getMillisPerLoad();
  }

  @Override
  public long getTotalLoadMillis() {
    return getInfo().getLoadMillis();
  }

  @Override
  public String getIntegrityDescriptor() {
    return getInfo().getIntegrityDescriptor();
  }

  @Override
  public Date getCreatedTime() {
    return new Date(getInfo().getStartedTime());
  }

  @Override
  public Date getClearedTime() {
    return optionalDate(getInfo().getClearedTime());
  }

  @Override
  public Date getInfoCreatedTime() {
    return new Date(getInfo().getInfoCreatedTime());
  }

  Date optionalDate(long millis) {
    return millis == 0 ? null : new Date(millis);
  }

  @Override
  public int getInfoCreatedDeltaMillis() {
    return getInfo().getInfoCreationDeltaMs();
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
  public int getAlert() {
    Iterator<HealthInfoElement> it = getInfo().getHealth().iterator();
    if (!it.hasNext()) {
      return 0;
    }
    HealthInfoElement hi = it.next();
    if (HealthInfoElement.FAILURE.equals(hi.getLevel())) {
      return 2;
    }
    return 1;
  }

  @Override
  public String getEvictionStatistics() {
    return getInfo().getExtraStatistics();
  }

  @Override
  public boolean isLoaderPresent() {
    return cache.isLoaderPresent();
  }

  @Override
  public boolean isWeigherPresent() {
    return cache.isWeigherPresent();
  }

}
