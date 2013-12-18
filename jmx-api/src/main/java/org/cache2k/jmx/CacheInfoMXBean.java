package org.cache2k.jmx;

/*
 * #%L
 * cache2k jmx api definitions
 * %%
 * Copyright (C) 2000 - 2013 headissue GmbH, Munich
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

import java.util.Date;

/**
 * @author Jens Wilke; created: 2013-07-16
 */
@SuppressWarnings("unused")
public interface CacheInfoMXBean {

  int getSize();

  /**
   * How often data was requested from the cache. In multi threading scenarios this
   * counter may be not totally accurate. For performance reason the cache implementation
   * may choose to only present a best effort value. It is guaranteed that the
   * usage count is always greater than the miss count.
   */
  long getUsageCnt();

  /**
   * Counter of the event that: a client requested a data which was not
   * present in the cache or had expired.
   */
  long getMissCnt();

  /**
   * Counter for the event that we crated a new cache entry. Either upon
   * a client get request or a put operation.
   */
  long getNewEntryCnt();

  /**
   * Counter for the event that data was fetched from the underlying storage,
   * either on client request or upon a background refresh.
   */
  long getFetchCnt();

  /**
   * Counter for the event that the data of a cache entry was refreshed.
   */
  long getRefreshCnt();


  long getRefreshSubmitFailedCnt();

  long getRefreshHitCnt();

  /**
   * Counter for the event that data in the cache has expired.
   *
   * <p>This can mean that the cache entry is removed or just marked as expired
   * in case that the keep data option is enabled.
   *
   * @see org.cache2k.CacheConfig#setKeepDataAfterExpired(boolean)
   */
  long getExpiredCnt();

  long getEvictedCnt();

  long getPrefetchCnt();

  long getPutCnt();

  long getKeyMismatchCnt();

  double getDataHitRate();

  double getCacheHitRate();

  double getHashQuality();

  int getCollisionCnt();

  int getCollisionsSlotCnt();

  int getLongestCollisionSize();

  int getMsecsPerFetch();

  long getFetchMillis();

  int getMemoryUsage();

  String getImplementation();

  String getIntegrityDescriptor();

  Date getCreated();

  Date getCleared();

  Date getInfoCreated();

  int getInfoCreatedDetlaMs();

}
