package org.cache2k.ee.impl;

/*
 * #%L
 * cache2k for enterprise environments
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

import org.cache2k.Cache;
import org.cache2k.impl.BaseCache;
import org.cache2k.jmx.CacheMXBean;

import java.util.Date;

/**
 * @author Jens Wilke; created: 2014-10-09
 */
public class CacheMXBeanImpl implements CacheMXBean {

  BaseCache cache;

  CacheMXBeanImpl(BaseCache cache) {
    this.cache = cache;
  }

  private BaseCache.Info getInfo() { return cache.getInfo(); }

  @Override
  public int getSize() {
    return getInfo().getSize();
  }

  @Override
  public int getMaximumSize() {
    return getInfo().getMaxSize();
  }

  @Override
  public long getUsageCnt() {
    return getInfo().getUsageCnt();
  }

  @Override
  public long getMissCnt() {
    return getInfo().getMissCnt();
  }

  @Override
  public long getNewEntryCnt() {
    return getInfo().getNewEntryCnt();
  }

  @Override
  public long getFetchCnt() {
    return getInfo().getFetchCnt();
  }

  @Override
  public long getRefreshCnt() {
    return getInfo().getRefreshCnt();
  }

  @Override
  public long getRefreshSubmitFailedCnt() {
    return getInfo().getRefreshSubmitFailedCnt();
  }

  @Override
  public long getRefreshHitCnt() {
    return getInfo().getRefreshHitCnt();
  }

  @Override
  public long getExpiredCnt() {
    return getInfo().getExpiredCnt();
  }

  @Override
  public long getEvictedCnt() {
    return getInfo().getEvictedCnt();
  }

  @Override
  public long getKeyMutationCnt() {
    return getInfo().getKeyMutationCnt();
  }

  @Override
  public long getFetchExceptionCnt() {
    return getInfo().getFetchExceptionCnt();
  }

  @Override
  public long getSuppressedExceptionCnt() {
    return getInfo().getSuppressedExceptionCnt();
  }

  @Override
  public long getPutCnt() {
    return getInfo().getPutCnt();
  }

  @Override
  public double getHitRate() {
    return getInfo().getDataHitRate();
  }

  @Override
  public int getHashQuality() {
    return getInfo().getHashQualityInteger();
  }

  @Override
  public int getHashCollisionCnt() {
    return getInfo().getCollisionCnt();
  }

  @Override
  public int getHashCollisionsSlotCnt() {
    return getInfo().getCollisionSlotCnt();
  }

  @Override
  public int getHashLongestCollisionSize() {
    return getInfo().getLongestCollisionSize();
  }

  @Override
  public double getMillisPerFetch() {
    return getInfo().getMillisPerFetch();
  }

  @Override
  public int getMemoryUsage() {
    return -1;
  }

  @Override
  public long getFetchMillis() {
    return getInfo().getFetchMillis();
  }

  @Override
  public String getIntegrityDescriptor() {
    return getInfo().getIntegrityDescriptor();
  }

  @Override
  public Date getCreatedTime() {
    return new Date(getInfo().getStarted());
  }

  @Override
  public Date getClearedTime() {
    return new Date(getInfo().getCleared());
  }

  @Override
  public Date getLastOperationTime() {
    return new Date(getInfo().getTouched());
  }

  @Override
  public Date getInfoCreatedTime() {
    return new Date(getInfo().getInfoCreated());
  }

  @Override
  public int getInfoCreatedDetlaMillis() {
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
  public void clearTimingStatistics() {
    cache.clearTimingStatistics();
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
