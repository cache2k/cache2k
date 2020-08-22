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

import org.cache2k.core.concurrency.Job;

/**
 * Forwards eviction operations to segments based on the hash code.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("rawtypes")
public class SegmentedEviction implements Eviction, EvictionMetrics {

  private final Eviction[] segments;

  public SegmentedEviction(Eviction[] segments) {
    this.segments = segments;
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

  @Override
  public long removeAll() {
    long count = 0;
    for (Eviction ev : segments) {
      count += ev.removeAll();
    }
    return count;
  }

  @Override
  public void start() {
    for (Eviction ev : segments) {
      ev.start();
    }
  }

  @Override
  public void stop() {
    for (Eviction ev : segments) {
      ev.stop();
    }
  }

  @Override
  public void close() {
    for (Eviction ev : segments) {
      ev.close();
    }
  }

  @Override
  public boolean drain() {
    boolean f = false;
    for (Eviction ev : segments) {
      f |= ev.drain();
    }
    return f;
  }

  @Override
  public <T> T runLocked(Job<T> j) {
    return runLocked(0, j);
  }

  private <T> T runLocked(final int idx, final Job<T> j) {
    if (idx == segments.length) {
      return j.call();
    }
    return segments[idx].runLocked(new Job<T>() {
      @Override
      public T call() {
        return runLocked(idx + 1, j);
      }
    });
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
    return this;
  }

  @Override
  public String getExtraStatistics() {
    StringBuilder sb  = new StringBuilder();
    for (int i = 0; i < segments.length; i++) {
      if (i > 0) { sb.append(", "); }
      sb.append("eviction").append(i).append('(');
      sb.append(segments[i].getMetrics().getExtraStatistics());
      sb.append(')');
    }
    return sb.toString();
  }

  @Override
  public long getHitCount() {
    long sum = 0;
    for (Eviction ev : segments) {
      sum += ev.getMetrics().getHitCount();
    }
    return sum;
  }

  @Override
  public long getNewEntryCount() {
    long sum = 0;
    for (Eviction ev : segments) {
      sum += ev.getMetrics().getNewEntryCount();
    }
    return sum;
  }

  @Override
  public long getRemovedCount() {
    long sum = 0;
    for (Eviction ev : segments) {
      sum += ev.getMetrics().getRemovedCount();
    }
    return sum;
  }

  @Override
  public long getExpiredRemovedCount() {
    long sum = 0;
    for (Eviction ev : segments) {
      sum += ev.getMetrics().getExpiredRemovedCount();
    }
    return sum;
  }

  @Override
  public long getVirginRemovedCount() {
    long sum = 0;
    for (Eviction ev : segments) {
      sum += ev.getMetrics().getVirginRemovedCount();
    }
    return sum;
  }

  @Override
  public long getEvictedCount() {
    long sum = 0;
    for (Eviction ev : segments) {
      sum += ev.getMetrics().getEvictedCount();
    }
    return sum;
  }

  @Override
  public long getSize() {
    long sum = 0;
    for (Eviction ev : segments) {
      sum += ev.getMetrics().getSize();
    }
    return sum;
  }

  @Override
  public long getMaxSize() {
    long sum = 0;
    for (Eviction ev : segments) {
      long l = ev.getMetrics().getMaxSize();
      if (l == Long.MAX_VALUE) {
        return Long.MAX_VALUE;
      }
      sum += l;
    }
    if (sum < 0) {
      return -1;
    }
    return sum;
  }

  @Override
  public long getMaxWeight() {
    long sum = 0;
    for (Eviction ev : segments) {
      long l = ev.getMetrics().getMaxWeight();
      if (l == Long.MAX_VALUE) {
        return Long.MAX_VALUE;
      }
      sum += l;
    }
    if (sum < 0) {
      return -1;
    }
    return sum;
  }

  @Override
  public long getTotalWeight() {
    long sum = 0;
    for (Eviction ev : segments) {
      long l = ev.getMetrics().getTotalWeight();
      sum += l;
    }
    return sum;
  }

  @Override
  public int getEvictionRunningCount() {
    int sum = 0;
    for (Eviction ev : segments) {
      sum += ev.getMetrics().getEvictionRunningCount();
    }
    return sum;
  }

  @Override
  public long getEvictedWeight() {
    int sum = 0;
    for (Eviction ev : segments) {
      sum += ev.getMetrics().getEvictedWeight();
    }
    return sum;
  }

  @Override
  public boolean isWeigherPresent() {
    return segments[0].isWeigherPresent();
  }

  @Override
  public void changeCapacity(long entryCountOrWeight) {
    long limitPerSegment = isWeigherPresent() ?
      InternalCache2kBuilder.determineMaxWeight(entryCountOrWeight, segments.length) :
      InternalCache2kBuilder.determineMaxSize(entryCountOrWeight, segments.length);
    for (Eviction ev : segments) {
      ev.changeCapacity(limitPerSegment);
    }
  }

}
