package org.cache2k.core;

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

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * @author Jens Wilke
 */
@SuppressWarnings({"unused"})
public class StandardCommonMetrics implements CommonMetrics.Updater {

  static final AtomicLongFieldUpdater<StandardCommonMetrics> PUT_NEW_ENTRY_UPDATER =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "putNewEntry");
  private volatile long putNewEntry;
  @Override
  public void putNewEntry() {
    PUT_NEW_ENTRY_UPDATER.incrementAndGet(this);
  }
  @Override
  public long getPutNewEntryCount() {
    return PUT_NEW_ENTRY_UPDATER.get(this);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> PUT_HIT_UPDATER =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "putHit");
  private volatile long putHit;
  @Override
  public void putHit() {
    PUT_HIT_UPDATER.incrementAndGet(this);
  }
  @Override
  public long getPutHitCount() {
    return PUT_HIT_UPDATER.get(this);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> HEAP_HIT_BUT_NO_READ_UPDATER =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "heapHitButNoRead");
  private volatile long heapHitButNoRead;
  @Override
  public void heapHitButNoRead() {
    HEAP_HIT_BUT_NO_READ_UPDATER.incrementAndGet(this);
  }
  @Override
  public long getHeapHitButNoReadCount() {
    return HEAP_HIT_BUT_NO_READ_UPDATER.get(this);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> TIMER_EVENT_UPDATER =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "timerEvent");
  private volatile long timerEvent;
  @Override
  public void timerEvent() {
    TIMER_EVENT_UPDATER.incrementAndGet(this);
  }
  @Override
  public long getTimerEventCount() {
    return TIMER_EVENT_UPDATER.get(this);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> LOAD_MILLIS_UPDATER =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "loadMillis");
  private volatile long loadMillis;
  @Override
  public long getLoadMillis() {
    return LOAD_MILLIS_UPDATER.get(this);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> REFRESH_UPDATER =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "refresh");
  private volatile long refresh;
  @Override
  public long getRefreshCount() {
    return REFRESH_UPDATER.get(this);
  }
  @Override
  public void refresh(final long millis) {
    REFRESH_UPDATER.incrementAndGet(this);
    LOAD_MILLIS_UPDATER.addAndGet(this, millis);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> READ_THROUGH_UPDATER =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "readThrough");
  private volatile long readThrough;
  @Override
  public long getReadThroughCount() {
    return READ_THROUGH_UPDATER.get(this);
  }
  @Override
  public void readThrough(final long millis) {
    READ_THROUGH_UPDATER.incrementAndGet(this);
    LOAD_MILLIS_UPDATER.addAndGet(this, millis);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> RELOAD_UPDATER =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "reload");
  private volatile long reload;
  @Override
  public long getExplicitLoadCount() {
    return RELOAD_UPDATER.get(this);
  }
  @Override
  public void explicitLoad(final long millis) {
    RELOAD_UPDATER.incrementAndGet(this);
    LOAD_MILLIS_UPDATER.addAndGet(this, millis);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> LOAD_EXCEPTION_UPDATER =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "loadException");
  private volatile long loadException;
  @Override
  public long getLoadExceptionCount() {
    return LOAD_EXCEPTION_UPDATER.get(this);
  }
  @Override
  public void loadException() {
    LOAD_EXCEPTION_UPDATER.incrementAndGet(this);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> SUPPRESSED_EXCEPTION_UPDATER =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "suppressedException");
  private volatile long suppressedException;
  @Override
  public long getSuppressedExceptionCount() {
    return SUPPRESSED_EXCEPTION_UPDATER.get(this);
  }
  @Override
  public void suppressedException() {
    SUPPRESSED_EXCEPTION_UPDATER.incrementAndGet(this);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> EXPIRED_KEPT_UPDATER =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "expiredKept");
  private volatile long expiredKept;
  @Override
  public long getExpiredKeptCount() {
    return EXPIRED_KEPT_UPDATER.get(this);
  }
  @Override
  public void expiredKept() {
    EXPIRED_KEPT_UPDATER.incrementAndGet(this);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> PEEK_MISS_UPDATER =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "peekMiss");
  private volatile long peekMiss;
  @Override
  public long getPeekMissCount() {
    return PEEK_MISS_UPDATER.get(this);
  }
  @Override
  public void peekMiss() {
    PEEK_MISS_UPDATER.incrementAndGet(this);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> PEEK_HIT_NOT_FRESH_UPDATER =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "peekHitNotFresh");
  private volatile long peekHitNotFresh;
  @Override
  public long getPeekHitNotFreshCount() {
    return PEEK_HIT_NOT_FRESH_UPDATER.get(this);
  }
  @Override
  public void peekHitNotFresh() {
    PEEK_HIT_NOT_FRESH_UPDATER.incrementAndGet(this);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> REFRESH_HIT_UPDATER =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "refreshHit");
  private volatile long refreshHit;
  @Override
  public long getRefreshedHitCount() {
    return REFRESH_HIT_UPDATER.get(this);
  }
  @Override
  public void refreshedHit() {
    REFRESH_HIT_UPDATER.incrementAndGet(this);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> REFRESH_REJECTED_UPDATER =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "refreshRejected");
  private volatile long refreshRejected;
  @Override
  public long getRefreshRejectedCount() {
    return REFRESH_REJECTED_UPDATER.get(this);
  }
  @Override
  public void refreshRejected() {
    REFRESH_REJECTED_UPDATER.incrementAndGet(this);
  }

  @Override
  public boolean isDisabled() {
    return false;
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> GONE_SPIN_UPDATER =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "goneSpin");
  private volatile long goneSpin;
  @Override
  public long getGoneSpinCount() {
    return GONE_SPIN_UPDATER.get(this);
  }
  @Override
  public void goneSpin() {
    GONE_SPIN_UPDATER.incrementAndGet(this);
  }

}
