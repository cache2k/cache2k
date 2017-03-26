package org.cache2k.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2017 headissue GmbH, Munich
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
@SuppressWarnings("unused")
public class StandardStorageMetrics implements StorageMetrics.Updater {

  static final AtomicLongFieldUpdater<StandardStorageMetrics> READ_HIT_UPDATER =
    AtomicLongFieldUpdater.newUpdater(StandardStorageMetrics.class, "readHit");
  private volatile long readHit;
  @Override
  public void readHit() {
    READ_HIT_UPDATER.incrementAndGet(this);
  }
  @Override
  public long getReadHitCount() {
    return READ_HIT_UPDATER.get(this);
  }
  @Override
  public void readHit(final long cnt) {
    READ_HIT_UPDATER.addAndGet(this, cnt);
  }

  static final AtomicLongFieldUpdater<StandardStorageMetrics> READ_MISS_UPDATER =
    AtomicLongFieldUpdater.newUpdater(StandardStorageMetrics.class, "readMiss");
  private volatile long readMiss;
  @Override
  public void readMiss() {
    READ_MISS_UPDATER.incrementAndGet(this);
  }
  @Override
  public long getReadMissCount() {
    return READ_MISS_UPDATER.get(this);
  }
  @Override
  public void readMiss(final long cnt) {
    READ_MISS_UPDATER.addAndGet(this, cnt);
  }

  static final AtomicLongFieldUpdater<StandardStorageMetrics> READ_EXPIRED_UPDATER =
    AtomicLongFieldUpdater.newUpdater(StandardStorageMetrics.class, "readNonFresh");
  private volatile long readNonFresh;
  @Override
  public void readNonFresh() {
    READ_EXPIRED_UPDATER.incrementAndGet(this);
  }
  @Override
  public long getReadNonFreshCount() {
    return READ_EXPIRED_UPDATER.get(this);
  }
  @Override
  public void readNonFresh(final long cnt) {
    READ_EXPIRED_UPDATER.addAndGet(this, cnt);
  }

}
