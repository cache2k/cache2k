package org.cache2k.core;

/*
 * #%L
 * cache2k core
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

/**
 * Created by jeans on 1/6/16.
 */
public interface InternalCacheInfo {

  String getName();

  String getImplementation();

  /**
   * Current number of entries in the cache. This may include entries with expired
   * values.
   */
  long getSize();

  /**
   * Configured limit of the total cache entry capacity.
   */
  long getHeapCapacity();

  long getStorageHitCnt();

  long getStorageMissCnt();

  /**
   * Number of cache operations, only access.
   */
  long getGetCount();

  long getMissCount();

  long getNewEntryCount();

  /**
   * Loader calls including reloads and refresh.
   */
  long getLoadCount();

  /**
   *
   */
  long getReloadCount();

  long getRefreshCount();

  long getInternalExceptionCount();

  long getRefreshSubmitFailedCount();

  long getSuppressedExceptionCount();

  long getLoadExceptionCount();

  long getRefreshHitCount();

  long getExpiredCount();

  long getEvictedCount();

  long getEvictionRunningCount();

  long getRemovedCount();

  long getPutCount();

  long getClearRemovedCount();

  long getClearCount();

  long getKeyMutationCount();

  long getTimerEventCount();

  double getDataHitRate();

  String getDataHitString();

  int getCollisionPercentage();

  int getSlotsPercentage();

  int getHq0();

  int getHq1();

  int getHq2();

  int getHashQualityInteger();

  double getMillisPerLoad();

  long getLoadMillis();

  int getCollisionCount();

  int getCollisionSlotCount();

  int getLongestSlot();

  String getIntegrityDescriptor();

  long getGoneSpinCount();

  long getStartedTime();

  long getClearedTime();

  long getInfoCreatedTime();

  int getInfoCreationDeltaMs();

  int getHealth();

  String getExtraStatistics();

  long getAsyncLoadsStarted();

  long getAsyncLoadsInFlight();

  int getLoaderThreadsLimit();

  int getLoaderThreadsMaxActive();

}
