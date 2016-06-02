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

import org.cache2k.core.threading.Job;
import org.jctools.queues.MpscArrayQueue;
import org.jctools.queues.MpscChunkedArrayQueue;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Jens Wilke
 */
public class QueuedEviction implements Eviction, EvictionThread.Job {

  private final static int MAX_POLLS = 23;
  private final static AtomicInteger runnerIndex = new AtomicInteger();
  private final static EvictionThread[] threadRunners =
    new EvictionThread[Runtime.getRuntime().availableProcessors()];

  static {
    for (int i = 0; i < threadRunners.length; i++) {
      threadRunners[i] = new EvictionThread();
    }
  }

  private Queue<Entry> queue = new MpscChunkedArrayQueue<Entry>(4, 128, false);

  private AbstractEviction forward;

  private EvictionThread threadRunner =
    threadRunners[runnerIndex.getAndIncrement() % threadRunners.length];

  public QueuedEviction(final AbstractEviction _forward) {
    forward = _forward;
    threadRunner.addJob(this);
  }

  @Override
  public void execute(final Entry e) {
    throw new UnsupportedOperationException();
  }

  /**
   * We are not allowed to evict and remove from the hash in the calling thread.
   * If we queue successfully, it's okay.
   */
  @Override
  public boolean executeWithoutEviction(final Entry e) {
    if (queue.offer(e)) {
      threadRunner.ensureRunning();
      return false;
    } else {
      return forward.executeWithoutEviction(e);
    }
  }

  @Override
  public void evictEventually(int hc) {
    forward.evictEventually(hc);
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
    runnerIndex.decrementAndGet();
    stop();
  }

  @Override
  public boolean runEvictionJob() {
    Entry e = queue.poll();
    if (e == null) { return false; }
    int _pollCount = MAX_POLLS;
    do {
      forward.execute(e);
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
    if (e == null) { return false; }
    do {
      forward.executeWithoutEviction(e);
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
    threadRunner.removeJob(this);
  }

  @Override
  public void start() {
    threadRunner.addJob(this);
  }

  @Override
  public <T> T runLocked(final Job<T> j) {
    return forward.runLocked(j);
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

  @Override
  public long getMaxSize() { return forward.getMaxSize(); }

}
