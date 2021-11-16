package org.cache2k.core;

/*
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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

import org.cache2k.core.api.InternalCacheCloseContext;
import org.cache2k.core.eviction.Eviction;
import org.cache2k.core.eviction.EvictionFactory;
import org.cache2k.core.eviction.EvictionMetrics;

import java.util.function.Supplier;

/**
 * Forwards eviction operations to segments based on the hash code.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("rawtypes")
public class SegmentedEviction implements Eviction {

  private final Eviction[] segments;

  public SegmentedEviction(Eviction[] segments) {
    this.segments = segments;
  }

  @Override
  public long startNewIdleScanRound() {
    long sum = 0;
    for (Eviction ev : segments) {
      sum += ev.startNewIdleScanRound();
    }
    return sum;
  }

  @Override
  public boolean updateWeight(Entry e) {
    int hc = e.hashCode;
    Eviction[] sgs = segments;
    int mask = sgs.length - 1;
    int idx = hc & mask;
    return sgs[idx].updateWeight(e);
  }

  @Override
  public boolean submitWithoutTriggeringEviction(Entry e) {
    int hc = e.hashCode;
    Eviction[] sgs = segments;
    int mask = sgs.length - 1;
    int idx = hc & mask;
    return sgs[idx].submitWithoutTriggeringEviction(e);
  }

  @Override
  public void evictEventuallyBeforeInsertOnSegment(int hashCodeHint) {
    Eviction[] sgs = segments;
    int mask = sgs.length - 1;
    int idx = hashCodeHint & mask;
    sgs[idx].evictEventuallyBeforeInsertOnSegment(hashCodeHint);
  }

  @Override
  public void evictEventuallyBeforeInsert() {
    for (Eviction ev : segments) {
      ev.evictEventuallyBeforeInsert();
    }
  }

  @Override
  public void evictEventually() {
    for (Eviction ev : segments) {
      ev.evictEventually();
    }
  }

  /**
   * Scan all segments for idlers. Maybe a round robin approach is more
   * efficient.
   */
  @Override
  public long evictIdleEntries(int maxScan) {
    int maxScanPerSegment = maxScan / segments.length + 1;
    long evictedCount = 0;
    for (Eviction ev : segments) {
      evictedCount += ev.evictIdleEntries(maxScanPerSegment);
    }
    return evictedCount;
  }

  @Override
  public long removeAll() {
    long count = 0;
    for (Eviction ev : segments) {
      count += ev.removeAll();
    }
    return count;
  }

  @Override
  public void close(InternalCacheCloseContext closeContext) {
    for (Eviction ev : segments) {
      ev.close(closeContext);
    }
  }

  @Override
  public <T> T runLocked(Supplier<T> j) {
    return runLocked(0, j);
  }

  private <T> T runLocked(int idx, Supplier<T> j) {
    if (idx == segments.length) {
      return j.get();
    }
    return segments[idx].runLocked(() -> runLocked(idx + 1, j));
  }

  @Override
  public void checkIntegrity(IntegrityState integrityState) {
    for (int i = 0; i < segments.length; i++) {
      integrityState.group("eviction" + i);
      segments[i].checkIntegrity(integrityState);
    }
  }

  @Override
  public EvictionMetrics getMetrics() {
    EvictionMetrics[] metrics = new EvictionMetrics[segments.length];
    for (int i = 0; i < metrics.length; i++) {
      metrics[i] = segments[i].getMetrics();
    }
    long sum = 0;
    for (EvictionMetrics m : metrics) { sum += m.getSize(); }
    long size = sum;
    sum = 0; for (EvictionMetrics m : metrics) { sum += m.getNewEntryCount(); }
    long newEntryCount = sum;
    sum = 0; for (EvictionMetrics m : metrics) { sum += m.getRemovedCount(); }
    long removedCnt = sum;
    sum = 0; for (EvictionMetrics m : metrics) { sum += m.getVirginRemovedCount(); }
    long virginRemovedCnt = sum;
    sum = 0; for (EvictionMetrics m : metrics) { sum += m.getExpiredRemovedCount(); }
    long expiredRemovedCnt = sum;
    sum = 0; for (EvictionMetrics m : metrics) { sum += m.getEvictedCount(); }
    long evictedCount = sum;
    sum = 0; for (EvictionMetrics m : metrics) {
      long v = m.getMaxSize();
      if (v == Long.MAX_VALUE) { sum = v; break; }
      sum += v;
    }
    long maxSize = sum < -1 ? -1 : sum;
    sum = 0; for (EvictionMetrics m : metrics) {
      long v = m.getMaxWeight();
      if (v == Long.MAX_VALUE) { sum = v; break; }
      sum += v;
    }
    long maxWeight = sum < -1 ? -1 : sum;
    sum = 0; for (EvictionMetrics m : metrics) { sum += m.getTotalWeight(); }
    long totalWeight = sum;
    sum = 0; for (EvictionMetrics m : metrics) { sum += m.getEvictedWeight(); }
    long evictedWeight = sum;
    sum = 0; for (EvictionMetrics m : metrics) { sum += m.getScanCount(); }
    long scanCount = sum;
    sum = 0; for (EvictionMetrics m : metrics) { sum += m.getEvictionRunningCount(); }
    int evictionRunningCount = (int) sum;
    sum = 0; for (EvictionMetrics m : metrics) { sum += m.getIdleNonEvictDrainCount(); }
    long removeAfterScanCount = sum;
    return new EvictionMetrics() {
      @Override public long getSize() { return size; }
      @Override public long getNewEntryCount() { return newEntryCount; }
      @Override public long getRemovedCount() { return removedCnt; }
      @Override public long getVirginRemovedCount() { return virginRemovedCnt; }
      @Override public long getExpiredRemovedCount() { return expiredRemovedCnt; }
      @Override public long getEvictedCount() { return evictedCount; }
      @Override public long getMaxSize() { return maxSize; }
      @Override public long getMaxWeight() { return maxWeight; }
      @Override public long getTotalWeight() { return totalWeight; }
      @Override public long getEvictedWeight() { return evictedWeight; }
      @Override public int getEvictionRunningCount() { return evictionRunningCount; }
      @Override public long getScanCount() { return scanCount; }
      @Override public long getIdleNonEvictDrainCount() { return removeAfterScanCount; }
    };
  }

  @Override
  public boolean isWeigherPresent() {
    return segments[0].isWeigherPresent();
  }

  @Override
  public void changeCapacity(long entryCountOrWeight) {
    long limitPerSegment = isWeigherPresent() ?
      EvictionFactory.determineMaxWeight(entryCountOrWeight, segments.length) :
      EvictionFactory.determineMaxSize(entryCountOrWeight, segments.length);
    for (Eviction ev : segments) {
      ev.changeCapacity(limitPerSegment);
    }
  }

  @Override
  public String toString() {
    StringBuilder sb  = new StringBuilder();
    for (int i = 0; i < segments.length; i++) {
      if (i > 0) { sb.append(", "); }
      sb.append("eviction").append(i).append('(');
      sb.append(segments[i].toString());
      sb.append(')');
    }
    return sb.toString();
  }

}
