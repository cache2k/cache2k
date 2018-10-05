package org.cache2k.core;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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
public class SegmentedEviction implements Eviction, EvictionMetrics {

  private Eviction[] segments;

  public SegmentedEviction(final Eviction[] _segments) {
    segments = _segments;
  }


  @Override
  public void updateWeight(final Entry e) {
    int hc = e.hashCode;
    Eviction[] sgs = segments;
    int _mask = sgs.length - 1;
    int idx = hc & _mask;
    sgs[idx].updateWeight(e);
  }

  @Override
  public boolean submitWithoutEviction(final Entry e) {
    int hc = e.hashCode;
    Eviction[] sgs = segments;
    int _mask = sgs.length - 1;
    int idx = hc & _mask;
    return sgs[idx].submitWithoutEviction(e);
  }

  @Override
  public void evictEventually(int _hashCodeHint) {
    Eviction[] sgs = segments;
    int _mask = sgs.length - 1;
    int idx = _hashCodeHint & _mask;
    sgs[idx].evictEventually(_hashCodeHint);
  }

  @Override
  public void evictEventually() {
    for (Eviction ev : segments) {
      ev.evictEventually();
    }
  }

  @Override
  public long removeAll() {
    long _count = 0;
    for (Eviction ev : segments) {
      _count += ev.removeAll();
    }
    return _count;
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
  public <T> T runLocked(final Job<T> j) {
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
  public void checkIntegrity(final IntegrityState _integrityState) {
    for (int i = 0; i < segments.length; i++) {
      _integrityState.group("eviction" + i);
      segments[i].checkIntegrity(_integrityState);
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
  public long getCurrentWeight() {
    long sum = 0;
    for (Eviction ev : segments) {
      long l = ev.getMetrics().getCurrentWeight();
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

}
