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

}
