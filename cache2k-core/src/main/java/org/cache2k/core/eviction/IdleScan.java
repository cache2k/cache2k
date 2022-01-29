package org.cache2k.core.eviction;

/*-
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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

import org.cache2k.CacheClosedException;
import org.cache2k.core.api.InternalCacheCloseContext;
import org.cache2k.core.api.NeedsClose;
import org.cache2k.operation.Scheduler;
import org.cache2k.operation.TimeReference;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Scans for idle entries. This uses a scheduler to wakeup in regular intervals and adds
 * additional scans in the eviction and eventually evicts the entry if it had no access since
 * the last scan round. The duration of the scan round is controlled via
 * {@link org.cache2k.Cache2kBuilder#idleScanTime(long, TimeUnit)} If there is normal eviction
 * activity in the cache, e.g. when a capacity limit is configured, only the difference of
 * scans is executed to achieve the configured scan time to cover all cached entries.
 * If eviction scans are more than needed for a the scan round time, the processing stops
 * and waits for a full round duration.
 *
 * @author Jens Wilke
 */
public class IdleScan implements NeedsClose {

  private static final long IDLE = -1;

  private final long roundTicks;
  private final Scheduler scheduler;
  private final TimeReference clock;
  private final Eviction eviction;
  /** Only for toString output */
  private long lastWakeupTicks;
  private long roundStartTicks = IDLE;
  /** Scan count at round start */
  private long roundStartScans;
  /** Size at round start. */
  private long scansPerRound;
  private long wakeupIntervalMillis;
  /** Entries evicted via idle eviction. */
  private long evictedCount = 0;
  private long lastScanCount = 0;
  private long roundStartCount = 0;
  private long roundCompleteCount = 0;
  private long roundAbortCount = 0;

  public IdleScan(TimeReference clock, Scheduler scheduler,
                  Eviction eviction, long roundTicks) {
    this.scheduler = scheduler;
    this.clock = clock;
    this.eviction = eviction;
    this.roundTicks = roundTicks;
    synchronized (this) {
      scheduleIdleWakeup(eviction.getMetrics());
    }
  }

  /**
   * Wakeup every specified scan time. If there is enough eviction activity or
   * the cache is empty, we need to do nothing.
   */
  private void idleWakeup() {
    synchronized (this) {
      long now = clock.ticks();
      EvictionMetrics metrics = eviction.getMetrics();
      long size = metrics.getSize();
      long scansSinceLastWakeup = metrics.getScanCount() - lastScanCount;
      boolean enoughScanActivity = scansSinceLastWakeup >= size;
      boolean empty = size == 0;
      if (empty || enoughScanActivity) {
        scheduleIdleWakeup(metrics);
        return;
      }
      startNewScanRound(now, metrics);
    }
  }

  private void scanWakeup() {
    int extraScan;
    synchronized (this) {
      long now = clock.ticks();
      lastWakeupTicks = now;
      EvictionMetrics metrics = eviction.getMetrics();
      long expectedScans =
        scansPerRound * (now - roundStartTicks) / roundTicks +
          roundStartScans - metrics.getIdleNonEvictDrainCount();
      long remainingScans = roundStartScans + scansPerRound - expectedScans;
      extraScan = (int) (expectedScans - metrics.getScanCount());
      if (extraScan < -remainingScans || metrics.getSize() == 0) {
        scheduleIdleWakeup(metrics); return;
      }
      if (extraScan <= 0) { scheduleNextWakeup(wakeupIntervalMillis); return; }
      if (now >= roundStartTicks + roundTicks) {
        startNewScanRound(now, metrics);
      } else {
        scheduleNextWakeup(wakeupIntervalMillis);
      }
    }
    try {
      long count = eviction.evictIdleEntries(extraScan);
      synchronized (this) {
        evictedCount += count;
      }
    } catch (CacheClosedException ignore) { }
  }

  /** With smaller or equal size do one wakeup per cache entry */
  static final int PRECISION_SIZE_THRESHOLD = 100;
  /** Usually do 100 wakeups per scan cycle */
  static final int REGULAR_WAKEUPS_PER_ROUND = 100;
  /** If caches are bigger, limit the amount of work in each wakeup and wakup more often */
  static final int MAX_SCAN_PER_WAKEUP = 50;

  /**
   * Interval depending on size and configuration. We wakeup 100 times per
   * scan round or more often either if size is small or very big.
   *
   * @return may return 0
   */
  static long calculateWakeupIntervalTicks(long roundTicks, long scansPerRound) {
    if (scansPerRound <= PRECISION_SIZE_THRESHOLD) {
      return roundTicks / scansPerRound;
    }
    long ticksThroughput = roundTicks / REGULAR_WAKEUPS_PER_ROUND;
    long ticksLimited = roundTicks / (scansPerRound / MAX_SCAN_PER_WAKEUP);
    return Math.min(ticksThroughput, ticksLimited);
  }

  private void startNewScanRound(long now, EvictionMetrics metrics) {
    if (roundStartTicks != IDLE) { roundCompleteCount++; }
    long size = metrics.getSize();
    scansPerRound = size;
    roundStartCount++;
    lastWakeupTicks = roundStartTicks = now;
    roundStartScans = eviction.startNewIdleScanRound();
    long rawWakeupIntervalMillis =
      clock.ticksToMillisCeiling(calculateWakeupIntervalTicks(roundTicks, scansPerRound));
    wakeupIntervalMillis = Math.max(1, rawWakeupIntervalMillis);
    scheduleNextWakeup(wakeupIntervalMillis);
  }

  private void scheduleIdleWakeup(EvictionMetrics metrics) {
    if (roundStartTicks != IDLE) { roundAbortCount++; }
    roundStartTicks = IDLE;
    lastScanCount = metrics.getScanCount();
    scheduleIgnoreException(scheduler, this::idleWakeup, clock.ticksToMillisCeiling(roundTicks));
  }

  private void scheduleNextWakeup(long delayMillis) {
    scheduleIgnoreException(scheduler, this::scanWakeup, delayMillis);
  }

  /**
   * Ignore when scheduler is closed. That happens when the cache is closed concurrently.
   * No more wakeups are needed anyway.
   */
  private static void scheduleIgnoreException(Scheduler scheduler,
                                              Runnable runnable,
                                              long delayMillis) {
    try {
      scheduler.schedule(runnable, delayMillis);
    } catch (RejectedExecutionException ignore) { }
  }

  @Override
  public synchronized void close(InternalCacheCloseContext closeContext) {
    closeContext.closeCustomization(scheduler, "scheduler for idle processing");
  }

  private int getIdleScanPercent() {
    return (int) ((lastWakeupTicks - roundStartTicks) * 100 / roundTicks);
  }

  @Override
  public synchronized String toString() {
    return "idleScanRoundStarted=" + roundStartCount +
      ", idleScanRoundCompleted=" + roundCompleteCount +
      ", idleScanRoundAbort=" + roundAbortCount +
      ", idleEvicted=" + evictedCount +
      ", idleScanPercent=" + (roundStartTicks == IDLE ? "IDLE" : getIdleScanPercent());
  }

}
