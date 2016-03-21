package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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
 * Created by jeans on 1/6/16.
 */
public interface InternalCacheInfo {
  String getName();

  String getImplementation();

  int getSize();

  int getMaxSize();

  long getVirginEvictCnt();

  long getFetchButHitCnt();

  long getStorageHitCnt();

  long getStorageLoadCnt();

  long getStorageMissCnt();

  long getReadUsageCnt();

  long getUsageCnt();

  long getMissCnt();

  long getNewEntryCnt();

  long getFetchCnt();

  int getFetchesInFlightCnt();

  long getBulkGetCnt();

  long getRefreshCnt();

  long getInternalExceptionCnt();

  long getRefreshSubmitFailedCnt();

  long getSuppressedExceptionCnt();

  long getFetchExceptionCnt();

  long getRefreshHitCnt();

  long getExpiredCnt();

  long getEvictedCnt();

  long getRemovedCnt();

  long getPutNewEntryCnt();

  long getPutCnt();

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

  double getMillisPerFetch();

  long getFetchMillis();

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

}
