package org.cache2k.core;

/*
 * #%L
 * cache2k core package
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

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * @author Jens Wilke
 */
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
  @Override
  public void putNewEntry(final long cnt) {
    PUT_NEW_ENTRY_UPDATER.addAndGet(this, cnt);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> CAS_OPERATION_UPDATER =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "casOperation");
  private volatile long casOperation;
  @Override
  public void casOperation() {
    CAS_OPERATION_UPDATER.incrementAndGet(this);
  }
  @Override
  public long getCasOperationCount() {
    return CAS_OPERATION_UPDATER.get(this);
  }
  @Override
  public void casOperation(final long cnt) {
    CAS_OPERATION_UPDATER.addAndGet(this, cnt);
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
  @Override
  public void putHit(final long cnt) {
    putHitUpdater.addAndGet(this, cnt);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> putNoReadHitUpdater =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "putNoReadHit");
  private volatile long putNoReadHit;
  @Override
  public void putNoReadHit() {
    putNoReadHitUpdater.incrementAndGet(this);
  }
  @Override
  public long getPutNoReadHitCount() {
    return putNoReadHitUpdater.get(this);
  }
  @Override
  public void putNoReadHit(final long cnt) {
    putNoReadHitUpdater.addAndGet(this, cnt);
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
  @Override
  public void heapHitButNoRead(final long cnt) {
    heapHitButNoReadUpdater.addAndGet(this, cnt);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> removeUpdater =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "remove");
  private volatile long remove;
  @Override
  public void remove() {
    removeUpdater.incrementAndGet(this);
  }
  @Override
  public long getRemoveCount() {
    return removeUpdater.get(this);
  }
  @Override
  public void remove(final long cnt) {
    removeUpdater.addAndGet(this, cnt);
  }

  static final AtomicLongFieldUpdater<StandardCommonMetrics> containsButHitUpdater =
    AtomicLongFieldUpdater.newUpdater(StandardCommonMetrics.class, "containsButHit");
  private volatile long containsButHit;
  @Override
  public void containsButHit() {
    containsButHitUpdater.incrementAndGet(this);
  }
  @Override
  public long getContainsButHitCount() {
    return containsButHitUpdater.get(this);
  }
  @Override
  public void containsButHit(final long cnt) {
    containsButHitUpdater.addAndGet(this, cnt);
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
  @Override
  public void timerEvent(final long cnt) {
    timerEventUpdater.addAndGet(this, cnt);
  }
}
