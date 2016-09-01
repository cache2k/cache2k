package org.cache2k.ee.impl;

/*
 * #%L
 * cache2k ee
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

import org.cache2k.core.InternalCache;
import org.cache2k.core.InternalCacheInfo;
import org.cache2k.jmx.CacheMXBean;

import java.util.Date;

/**
 * @author Jens Wilke; created: 2014-10-09
 */
public class CacheMXBeanImpl implements CacheMXBean {

  InternalCache cache;

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
  public long getMissCnt() {
    return getInfo().getMissCount();
  }

  @Override
  public long getNewEntryCnt() {
    return getInfo().getNewEntryCount();
  }

  @Override
  public long getLoadCnt() {
    return getInfo().getLoadCount();
  }

  @Override
  public long getRefreshCnt() {
    return getInfo().getRefreshCount();
  }

  @Override
  public long getRefreshFailedCnt() {
    return getInfo().getRefreshFailedCount();
  }

  @Override
  public long getRefreshedHitCnt() {
    return getInfo().getRefreshedHitCount();
  }

  @Override
  public long getExpiredCnt() {
    return getInfo().getExpiredCount();
  }

  @Override
  public long getEvictedCnt() {
    return getInfo().getEvictedCount();
  }

  @Override
  public long getKeyMutationCnt() {
    return getInfo().getKeyMutationCount();
  }

  @Override
  public long getLoadExceptionCnt() {
    return getInfo().getLoadExceptionCount();
  }

  @Override
  public long getSuppressedExceptionCnt() {
    return getInfo().getSuppressedExceptionCount();
  }

  @Override
  public long getGetCnt() {
    return getInfo().getGetCount();
  }

  @Override
  public long getPutCnt() {
    return getInfo().getPutCount();
  }

  @Override
  public double getHitRate() {
    return getInfo().getHitRate();
  }

  @Override
  public int getHashQuality() {
    return getInfo().getHashQuality();
  }

  @Override
  public long getHashCollisionCnt() {
    return getInfo().getHashCollisionCount();
  }

  @Override
  public long getHashCollisionsSlotCnt() {
    return getInfo().getHashCollisionSlotCount();
  }

  @Override
  public long getHashLongestCollisionSize() {
    return getInfo().getHashLongestSlotSize();
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
    return new Date(getInfo().getClearedTime());
  }

  @Override
  public Date getInfoCreatedTime() {
    return new Date(getInfo().getInfoCreatedTime());
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
  public int getAlert() {
    return getInfo().getHealth();
  }

  @Override
  public String getExtraStatistics() {
    return getInfo().getExtraStatistics();
  }

}
