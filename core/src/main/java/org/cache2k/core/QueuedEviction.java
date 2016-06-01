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

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Jens Wilke
 */
public class QueuedEviction implements Eviction, EvictionThread.Job {

  private final static int MAX_POLLS = 23;

  ConcurrentLinkedQueue<Entry> queue;
  AbstractEviction forward;
  EvictionThread threadRunner;

  @Override
  public long getHitCount() {
    return 0;
  }

  @Override
  public void insert(final Entry e) {
    queue.add(e);
    threadRunner.ensureRunning();
  }

  @Override
  public void remove(final Entry e) {
    queue.add(e);
    threadRunner.ensureRunning();
  }

  /**
   * Drain the queue and execute remove all in the current thread.
   */
  @Override
  public long removeAll() {
    threadRunner.removeJob(this);
    runEvictionJob();
    long _removedCount = forward.removeAll();
    threadRunner.addJob(this);
    return _removedCount;
  }

  @Override
  public long getNewEntryCount() {
    return 0;
  }

  @Override
  public void close() {

  }

  @Override
  public boolean runEvictionJob() {
    Entry e = queue.poll();
    if (e == null) {
      return false;
    }
    int _pollCount = MAX_POLLS;
    do {
      if (e.isRemovedFromReplacementList()) {
        forward.insert(e);
      } else {
        forward.remove(e);
      }
      if (--_pollCount == 0) {
        break;
      }
      e = queue.poll();
    } while (e != null);
    return true;
  }

  @Override
  public long getSize() {
    return forward.getSize();
  }

  @Override
  public void checkIntegrity(final IntegrityState _integrityState) {
    forward.checkIntegrity(_integrityState);
  }

  @Override
  public void stop() {

  }

  @Override
  public void start() {

  }

  @Override
  public String getExtraStatistics() {
    return forward.getExtraStatistics();
  }

  @Override
  public long getRemovedCount() {
    return forward.getRemovedCount();
  }

  @Override
  public long getExpiredRemovedCount() {
    return forward.getExpiredRemovedCount();
  }

  @Override
  public long getVirginRemovedCount() {
    return forward.getVirginRemovedCount();
  }

  @Override
  public long getEvictedCount() {
    return forward.getEvictedCount();
  }
}
