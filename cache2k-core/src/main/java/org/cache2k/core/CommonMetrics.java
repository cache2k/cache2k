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
   * Operation was accessing a heap entry and counted a hit it is existing, but
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
   * Entry was loaded, triggered by a get()
   *
   * @see InternalCacheInfo#getLoadCount()
   */
  long getReadThroughCount();

  /**
   * Entry was loaded again, e.g. when expired, triggered by a get() or reload()
   *
   * @see InternalCacheInfo#getReloadCount()
   */
  long getReloadCount();

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
   * Peek operation (or get() if no loader is present), has no hit.
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
   *
   * @see CommonMetrics#getRefreshFailedCount()
   */
  long getRefreshFailedCount();

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
    void putNewEntry(long cnt);

    void putHit();
    void putHit(long cnt);

    void heapHitButNoRead();
    void heapHitButNoRead(long cnt);

    void timerEvent();
    void timerEvent(long cnt);

    void load(long _millis);
    void load(long cnt, long _millis);

    void reload(long _millis);
    void reload(long cnt, long _millis);

    void refresh(long _millis);
    void refresh(long cnt, long _millis);

    void loadException();
    void loadException(long cnt);

    void suppressedException();
    void suppressedException(long cnt);

    void expiredKept();
    void expiredKept(long cnt);

    void peekMiss();
    void peekMiss(long cnt);

    void peekHitNotFresh();
    void peekHitNotFresh(long cnt);

    void refreshedHit();
    void refreshedHit(long cnt);

    void refreshFailed();
    void refreshFailed(long cnt);

    void goneSpin();
    void goneSpin(long cnt);

  }

  class BlackHole implements Updater {

    @Override
    public void putNewEntry() {

    }

    @Override
    public void putNewEntry(final long cnt) {

    }

    @Override
    public void putHit() {

    }

    @Override
    public void putHit(final long cnt) {

    }

    @Override
    public void heapHitButNoRead() {

    }

    @Override
    public void heapHitButNoRead(final long cnt) {

    }

    @Override
    public void timerEvent() {

    }

    @Override
    public void timerEvent(final long cnt) {

    }

    @Override
    public void load(final long _millis) {

    }

    @Override
    public void load(final long cnt, final long _millis) {

    }

    @Override
    public void reload(final long _millis) {

    }

    @Override
    public void reload(final long cnt, final long _millis) {

    }

    @Override
    public void refresh(final long _millis) {

    }

    @Override
    public void refresh(final long cnt, final long _millis) {

    }

    @Override
    public void loadException() {

    }

    @Override
    public void loadException(final long cnt) {

    }

    @Override
    public void suppressedException() {

    }

    @Override
    public void suppressedException(final long cnt) {

    }

    @Override
    public void expiredKept() {

    }

    @Override
    public void expiredKept(final long cnt) {

    }

    @Override
    public void peekMiss() {

    }

    @Override
    public void peekMiss(final long cnt) {

    }

    @Override
    public void peekHitNotFresh() {

    }

    @Override
    public void peekHitNotFresh(final long cnt) {

    }

    @Override
    public void refreshedHit() {

    }

    @Override
    public void refreshedHit(final long cnt) {

    }

    @Override
    public void refreshFailed() {

    }

    @Override
    public void refreshFailed(final long cnt) {

    }

    @Override
    public void goneSpin() {

    }

    @Override
    public void goneSpin(final long cnt) {

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
    public long getReloadCount() {
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
    public long getRefreshFailedCount() {
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
