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

  private ConcurrentLinkedQueue<Entry> queue = new ConcurrentLinkedQueue<Entry>();
  private final static EvictionThread threadRunner = new EvictionThread();
  private AbstractEviction forward;

  public QueuedEviction(final AbstractEviction _forward) {
    forward = _forward;
    threadRunner.addJob(this);
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

  @Override
  public void insertWithoutEviction(final Entry e) {
    queue.add(e);
    threadRunner.ensureRunning();
  }

  @Override
  public void evictEventually() {
    forward.evictEventually();
  }

  /**
   * Drain the queue and execute remove all in the current thread.
   */
  @Override
  public long removeAll() {
    long _removedCount = forward.removeAll();
    return _removedCount;
  }

  @Override
  public void close() {
    stop();
  }

  @Override
  public boolean runEvictionJob() {
    Entry e = queue.poll();
    if (e == null) {
      return false;
    }
    int _pollCount = MAX_POLLS;
    do {
      if (e.isNotYetInsertedInReplacementList()) {
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
  public boolean drain() {
    Entry e = queue.poll();
    boolean f = false;
    while (e != null) {
      f = true;
      if (e.isNotYetInsertedInReplacementList()) {
        forward.insertWithoutEviction(e);
      } else {
        forward.remove(e);
      }
      e = queue.poll();
    }
    return f;
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
    threadRunner.removeJob(this);
  }

  @Override
  public void start() {
    threadRunner.addJob(this);
  }

  @Override
  public long getNewEntryCount() {
    return forward.getNewEntryCount();
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

  @Override
  public long getHitCount() {
    return forward.getHitCount();
  }

}
