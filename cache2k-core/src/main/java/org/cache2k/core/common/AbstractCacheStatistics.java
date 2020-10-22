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

import org.cache2k.core.api.InternalCacheInfo;
import org.cache2k.management.CacheStatistics;

/**
 * Extract statistics from {@link InternalCacheInfo}
 *
 * @author Jens Wilke
 */
public abstract class AbstractCacheStatistics implements CacheStatistics {

  protected abstract InternalCacheInfo info();

  @Override
  public long getMissCount() {
    return info().getMissCount();
  }

  @Override
  public long getInsertCount() {
    return info().getNewEntryCount();
  }

  @Override
  public long getLoadCount() {
    return info().getLoadCount();
  }

  @Override
  public long getRefreshCount() {
    return info().getRefreshCount();
  }

  @Override
  public long getRefreshFailedCount() {
    return info().getRefreshRejectedCount();
  }

  @Override
  public long getRefreshedHitCount() {
    return info().getRefreshedHitCount();
  }

  @Override
  public long getExpiredCount() {
    return info().getExpiredCount();
  }

  @Override
  public long getEvictedCount() {
    return info().getEvictedCount();
  }

  @Override
  public long getEvictedOrRemovedWeight() {
    return info().getEvictedWeight();
  }

  @Override
  public long getKeyMutationCount() {
    return info().getKeyMutationCount();
  }

  @Override  public long getLoadExceptionCount() {
    return info().getLoadExceptionCount();
  }

  @Override
  public long getSuppressedLoadExceptionCount() {
    return info().getSuppressedExceptionCount();
  }

  @Override
  public long getGetCount() {
    return info().getGetCount();
  }

  @Override
  public long getPutCount() {
    return info().getPutCount();
  }

  @Override
  public long getClearCallsCount() {
    return info().getClearCount();
  }

  @Override
  public long getRemoveCount() {
    return info().getRemoveCount();
  }

  @Override
  public long getClearedCount() {
    return info().getClearedEntriesCount();
  }

  @Override
  public double getHitRate() {
    return info().getHitRate();
  }

  @Override
  public double getMillisPerLoad() {
    return info().getMillisPerLoad();
  }

  @Override
  public long getTotalLoadMillis() {
    return info().getLoadMillis();
  }

}
