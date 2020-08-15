package org.cache2k.core;

/*
 * #%L
 * cache2k implementation
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

/**
 * Statistic metrics common to every cache type.
 *
 * @author Jens Wilke
 */
public interface CommonMetrics {

  /**
   * Counted for a put that triggers the insert of a new cache entry.
   *
   * @see InternalCacheInfo#getPutCount()
   */
  long getPutNewEntryCount();

  /**
   * Counted for a put that updates an existing cache entry.
   *
   * @see InternalCacheInfo#getPutCount()
   */
  long getPutHitCount();

  /**
   * Operation was accessing a heap entry and counted a hit if it is existing, but
   * it should not be counted as read/get operation (e.g. {@code contains}). This
   * is a correction counter applied to the get counter.
   *
   * @see CacheBaseInfo#getGetCount()
   */
  long getHeapHitButNoReadCount();

  /**
   * Count of timer events delivered to this cache.
   *
   * @see InternalCacheInfo#getTimerEventCount()
   */
  long getTimerEventCount();

  /**
   * Entry was loaded, triggered by a get(). A read through event means also
   * a get, load and miss event.
   *
   * @see InternalCacheInfo#getLoadCount()
   */
  long getReadThroughCount();

  /**
   * Entry was explicitly loaded, that is every load that is not read through or refresh.
   *
   * @see InternalCacheInfo#getExplicitLoadCount()
   */
  long getExplicitLoadCount();

  /**
   * Entry was loaded again, triggered by timer
   *
   * @see InternalCacheInfo#getRefreshCount()
   */
  long getRefreshCount();

  /**
   * Accumulated milliseconds spend in load operations.
   *
   * @see InternalCacheInfo#getLoadMillis()
   */
  long getLoadMillis();

  /**
   * Counter of exceptions thrown from the loader.
   *
   * @see InternalCacheInfo#getLoadExceptionCount()
   */
  long getLoadExceptionCount();

  /**
   * Counter of suppressed exceptions from the loader
   *
   * @see InternalCacheInfo#getSuppressedExceptionCount()
   */
  long getSuppressedExceptionCount();

  /**
   * Number of entries expired, but kept in the cache because of
   * ongoing processing on the entry (pinned) or because keepData is enabled.
   *
   * @see InternalCacheInfo#getExpiredCount()
   */
  long getExpiredKeptCount();

  /**
   * Incremented if data is requested from the cache but no entry is present
   * (e.g. via peek or get). If a load is started this is not incremented,
   * since the actual miss is recorded via the load/readThrough counter.
   */
  long getPeekMissCount();

  /**
   * Peek, but entry available was not fresh (expired). This is effectively a miss, but we count
   * separately for debugging purposes. Always 0 if not
   * {@link org.cache2k.Cache2kBuilder#keepDataAfterExpired(boolean)}
   */
  long getPeekHitNotFreshCount();

  /**
   * Entry on probation for refresh got hit.
   *
   * @see InternalCacheInfo#getRefreshedHitCount()
   */
  long getRefreshedHitCount();

  /**
   * Refresh submit failed. Happens if the loader executor has not
   * enough available resources and rejects the refresh task.
   */
  long getRefreshRejectedCount();

  /**
   * Entry was removed while waiting to get the mutation lock.
   *
   * @see InternalCacheInfo#getGoneSpinCount()
   */
  long getGoneSpinCount();

  /**
   * True if statistics are disabled.
   */
  boolean isDisabled();

  interface Updater extends CommonMetrics {

    void putNewEntry();

    void putHit();

    void heapHitButNoRead();

    void timerEvent();

    void readThrough(long _millis);

    void explicitLoad(long _millis);

    void refresh(long _millis);

    void loadException();

    void suppressedException();

    void expiredKept();

    void peekMiss();

    void peekHitNotFresh();

    void refreshedHit();

    void refreshFailed();

    void goneSpin();

  }

  class BlackHole implements Updater {

    @Override
    public void putNewEntry() {

    }

    @Override
    public void putHit() {

    }

    @Override
    public void heapHitButNoRead() {

    }

    @Override
    public void timerEvent() {

    }

    @Override
    public void readThrough(final long _millis) {

    }

    @Override
    public void explicitLoad(final long _millis) {

    }

    @Override
    public void refresh(final long _millis) {

    }

    @Override
    public void loadException() {

    }

    @Override
    public void suppressedException() {

    }

    @Override
    public void expiredKept() {

    }

    @Override
    public void peekMiss() {

    }

    @Override
    public void peekHitNotFresh() {

    }

    @Override
    public void refreshedHit() {

    }

    @Override
    public void refreshFailed() {

    }

    @Override
    public void goneSpin() {

    }

    @Override
    public long getPutNewEntryCount() {
      return 0;
    }

    @Override
    public long getPutHitCount() {
      return 0;
    }

    @Override
    public long getHeapHitButNoReadCount() {
      return 0;
    }

    @Override
    public long getTimerEventCount() {
      return 0;
    }

    @Override
    public long getReadThroughCount() {
      return 0;
    }

    @Override
    public long getExplicitLoadCount() {
      return 0;
    }

    @Override
    public long getRefreshCount() {
      return 0;
    }

    @Override
    public long getLoadMillis() {
      return 0;
    }

    @Override
    public long getLoadExceptionCount() {
      return 0;
    }

    @Override
    public long getSuppressedExceptionCount() {
      return 0;
    }

    @Override
    public long getExpiredKeptCount() {
      return 0;
    }

    @Override
    public long getPeekMissCount() {
      return 0;
    }

    @Override
    public long getPeekHitNotFreshCount() {
      return 0;
    }

    @Override
    public long getRefreshedHitCount() {
      return 0;
    }

    @Override
    public long getRefreshRejectedCount() {
      return 0;
    }

    @Override
    public long getGoneSpinCount() {
      return 0;
    }

    @Override
    public boolean isDisabled() {
      return true;
    }
  }

}
