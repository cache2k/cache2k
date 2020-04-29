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

  static final AtomicLongFieldUpdater<StandardCommonMetrics> putHitUpdater =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "putHit");
  private volatile long putHit;
  @Override
  public void putHit() {
    putHitUpdater.incrementAndGet(this);
  }
  @Override
  public long getPutHitCount() {
    return putHitUpdater.get(this);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> heapHitButNoReadUpdater =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "heapHitButNoRead");
  private volatile long heapHitButNoRead;
  @Override
  public void heapHitButNoRead() {
    heapHitButNoReadUpdater.incrementAndGet(this);
  }
  @Override
  public long getHeapHitButNoReadCount() {
    return heapHitButNoReadUpdater.get(this);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> timerEventUpdater =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "timerEvent");
  private volatile long timerEvent;
  @Override
  public void timerEvent() {
    timerEventUpdater.incrementAndGet(this);
  }
  @Override
  public long getTimerEventCount() {
    return timerEventUpdater.get(this);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> loadMillisUpdater =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "loadMillis");
  private volatile long loadMillis;
  @Override
  public long getLoadMillis() {
    return loadMillisUpdater.get(this);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> refreshUpdater =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "refresh");
  private volatile long refresh;
  @Override
  public long getRefreshCount() {
    return refreshUpdater.get(this);
  }
  @Override
  public void refresh(final long _millis) {
    refreshUpdater.incrementAndGet(this);
    loadMillisUpdater.addAndGet(this, _millis);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> readThroughUpdater =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "readThrough");
  private volatile long readThrough;
  @Override
  public long getReadThroughCount() {
    return readThroughUpdater.get(this);
  }
  @Override
  public void readThrough(final long _millis) {
    readThroughUpdater.incrementAndGet(this);
    loadMillisUpdater.addAndGet(this, _millis);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> reloadUpdater =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "reload");
  private volatile long reload;
  @Override
  public long getReloadCount() {
    return reloadUpdater.get(this);
  }
  @Override
  public void explicitLoad(final long _millis) {
    reloadUpdater.incrementAndGet(this);
    loadMillisUpdater.addAndGet(this, _millis);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> loadExceptionUpdater =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "loadException");
  private volatile long loadException;
  @Override
  public long getLoadExceptionCount() {
    return loadExceptionUpdater.get(this);
  }
  @Override
  public void loadException() {
    loadExceptionUpdater.incrementAndGet(this);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> suppressedExceptionUpdater =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "suppressedException");
  private volatile long suppressedException;
  @Override
  public long getSuppressedExceptionCount() {
    return suppressedExceptionUpdater.get(this);
  }
  @Override
  public void suppressedException() {
    suppressedExceptionUpdater.incrementAndGet(this);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> expiredKeptUpdater =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "expiredKept");
  private volatile long expiredKept;
  @Override
  public long getExpiredKeptCount() {
    return expiredKeptUpdater.get(this);
  }
  @Override
  public void expiredKept() {
    expiredKeptUpdater.incrementAndGet(this);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> peekMissUpdater =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "peekMiss");
  private volatile long peekMiss;
  @Override
  public long getPeekMissCount() {
    return peekMissUpdater.get(this);
  }
  @Override
  public void peekMiss() {
    peekMissUpdater.incrementAndGet(this);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> peekHitNotFreshUpdater =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "peekHitNotFresh");
  private volatile long peekHitNotFresh;
  @Override
  public long getPeekHitNotFreshCount() {
    return peekHitNotFreshUpdater.get(this);
  }
  @Override
  public void peekHitNotFresh() {
    peekHitNotFreshUpdater.incrementAndGet(this);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> refreshHitUpdater =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "refreshHit");
  private volatile long refreshHit;
  @Override
  public long getRefreshedHitCount() {
    return refreshHitUpdater.get(this);
  }
  @Override
  public void refreshedHit() {
    refreshHitUpdater.incrementAndGet(this);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> refreshSubmitFailedUpdater =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "refreshSubmitFailed");
  private volatile long refreshSubmitFailed;
  @Override
  public long getRefreshFailedCount() {
    return refreshSubmitFailedUpdater.get(this);
  }
  @Override
  public void refreshFailed() {
    refreshSubmitFailedUpdater.incrementAndGet(this);
  }

  @Override
  public boolean isDisabled() {
    return false;
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> goneSpinUpdater =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "goneSpin");
  private volatile long goneSpin;
  @Override
  public long getGoneSpinCount() {
    return goneSpinUpdater.get(this);
  }
  @Override
  public void goneSpin() {
    goneSpinUpdater.incrementAndGet(this);
  }

}
