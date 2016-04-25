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

  int getSize();

  int getMaxSize();

  long getVirginEvictCnt();

  long getLoadButHitCnt();

  long getStorageHitCnt();

  long getStorageLoadCnt();

  long getStorageMissCnt();

  long getReadUsageCnt();

  long getUsageCnt();

  long getMissCnt();

  long getNewEntryCnt();

  long getLoadCnt();

  int getLoadsInFlightCnt();

  long getBulkGetCnt();

  long getRefreshCnt();

  long getInternalExceptionCnt();

  long getRefreshSubmitFailedCnt();

  long getSuppressedExceptionCnt();

  long getLoadExceptionCnt();

  long getRefreshHitCnt();

  long getExpiredCnt();

  long getEvictedCnt();

  long getRemovedCnt();

  long getPutNewEntryCnt();

  long getPutCnt();

  long getClearedCnt();

  long getClearCnt();

  long getKeyMutationCnt();

  long getTimerEventCnt();

  double getDataHitRate();

  String getDataHitString();

  double getEntryHitRate();

  String getEntryHitString();

  int getCollisionPercentage();

  int getSlotsPercentage();

  int getHq0();

  int getHq1();

  int getHq2();

  int getHashQualityInteger();

  double getMillisPerLoad();

  long getLoadMillis();

  int getCollisionCnt();

  int getCollisionSlotCnt();

  int getLongestCollisionSize();

  String getIntegrityDescriptor();

  long getStarted();

  long getCleared();

  long getTouched();

  long getInfoCreated();

  int getInfoCreationDeltaMs();

  int getHealth();

  String getExtraStatistics();

  long getAsyncLoadsStarted();

  long getAsyncLoadsInFlight();

  int getLoaderThreadsLimit();

  int getLoaderThreadsMaxActive();

}
