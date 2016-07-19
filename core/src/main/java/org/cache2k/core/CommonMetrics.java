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
 * @author Jens Wilke
 */
public interface CommonMetrics {

  long getPutNewEntryCount();
  long getPutHitCount();

  long getPutNoReadHitCount();

  /**
   * Correction counter for read usage
   */
  long getContainsButHitCount();

  /**
   * A valid entry was removed from the cache.
   */
  long getRemoveCount();

  long getHeapHitButNoReadCount();

  /**
   * Count of timer events delivered to this cache.
   */
  long getTimerEventCount();

  /**
   * The cache produced an exception by itself that should have been prevented.
   */
  long getInternalExceptionCount();

  /**
   * Entry was loaded, triggered by a get()
   */
  long getLoadCount();

  /**
   * Entry was loaded again, triggered by a get()
   */
  long getReloadCount();

  /**
   * Entry was loaded again, triggered by timer
   */
  long getRefreshCount();

  /**
   * Accumulated milliseconds spend in load operations.
   */
  long getLoadMillis();

  /**
   * Counter of exceptions thrown from the loader.
   */
  long getLoadExceptionCount();

  /**
   * Counter of suppressed exception from the loader
   */
  long getSuppressedExceptionCount();

  /**
   * Number of entries expired, but kept in the cache because of
   * ongoing processing on the entry (pinned) or because keepData is enabled.
   */
  long getExpiredKeptCount();

  /**
   * Peek but nothing available in the heap.
   */
  long getPeekMissCount();

  /**
   * Peek, but entry available was not fresh.
   */
  long getPeekHitNotFreshCount();

  /**
   * Entry on probation for refresh got hit.
   */
  long getRefreshHitCount();

  /**
   * Refresh submit failed. Happens if the loader executor has not
   * enough available resources and rejects the refresh task.
   */
  long getRefreshSubmitFailedCount();

  /**
   * Entry was removed while waiting to get the mutation lock.
   * Use this counter only if usage is counted (hit or new).
   */
  long getGoneSpinCount();

  boolean isDisabled();

  interface Updater extends CommonMetrics {

    void putNewEntry();
    void putNewEntry(long cnt);

    void putHit();
    void putHit(long cnt);

    void putNoReadHit();
    void putNoReadHit(long cnt);

    void containsButHit();
    void containsButHit(long cnt);

    void remove();
    void remove(long cnt);

    void heapHitButNoRead();
    void heapHitButNoRead(long cnt);

    void timerEvent();
    void timerEvent(long cnt);

    void internalException();

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

    void refreshHit();
    void refreshHit(long cnt);

    void refreshSubmitFailed();
    void refreshSubmitFailed(long cnt);

    void goneSpin();
    void goneSpin(long cnt);

  }

  class BlackHole implements Updater {

    @Override
    public void containsButHit() {

    }

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
    public void putNoReadHit() {

    }

    @Override
    public void putNoReadHit(final long cnt) {

    }

    @Override
    public void containsButHit(final long cnt) {

    }

    @Override
    public void remove() {

    }

    @Override
    public void remove(final long cnt) {

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
    public void internalException() {

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
    public void refreshHit() {

    }

    @Override
    public void refreshHit(final long cnt) {

    }

    @Override
    public void refreshSubmitFailed() {

    }

    @Override
    public void refreshSubmitFailed(final long cnt) {

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
    public long getPutNoReadHitCount() {
      return 0;
    }

    @Override
    public long getContainsButHitCount() {
      return 0;
    }

    @Override
    public long getRemoveCount() {
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
    public long getInternalExceptionCount() {
      return 0;
    }

    @Override
    public long getLoadCount() {
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
    public long getRefreshHitCount() {
      return 0;
    }

    @Override
    public long getRefreshSubmitFailedCount() {
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
