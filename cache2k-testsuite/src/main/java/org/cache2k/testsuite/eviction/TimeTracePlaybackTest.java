package org.cache2k.testsuite.eviction;

/*-
 * #%L
 * cache2k testsuite on public API
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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.event.CacheEntryEvictedListener;
import org.cache2k.operation.TimeReference;
import org.cache2k.testing.SimulatedClock;
import org.cache2k.testing.category.TimingTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Compare idle scanning to the established Time to Idle semantics
 *
 * @author Jens Wilke
 * @see <a href="https://github.com/cache2k/cache2k/issues/39">Github issue #39 - Time to Idle</a>
 */
@Category(TimingTests.class)
public class TimeTracePlaybackTest {

  static final boolean STAT_OUTPUT = true;
  static final boolean DEBUG_OUTPUT = false;
  static final int TRACE_KEY = 10095;

  /** Run with unbounded cache to get trace statistics */
  @Test
  public void outputTraceStatistics() {
    PlaybackResult res;
    res = playback(new LruCache(), Trace.WEBLOG424_NOROBOTS.get());
    if (STAT_OUTPUT) { System.out.println(res); }
  }

  @Test
  public void lruTimeToIdleTab() {
    System.out.println("_Established Time To Idle semantics_");
    System.out.println("| TTI/Minutes | Maximum cache size | Average cache size | Hitrate |");
    System.out.println("|---:|---:|---:|---:|");
    int[] trace = Trace.WEBLOG424_NOROBOTS.get();
    for (int i = 55; i <= 65; i++) {
      PlaybackResult res = playback(new LruCache(i * 60), trace);
      markdownRow(i, res);
    }
  }

  public void markdownRow(int i, PlaybackResult res) {
    System.out.println("| " + i + " | " + res.maxSize + " | " + res.getAverageSize() + " | " + res.getHitrate());
  }

  @Test
  public void cache2kIdleScanTab() {
    System.out.println("_Time To Idle emulation via scanning in cache2k 2.6_");
    System.out.println("| Scan round time/Minutes | Maximum cache size | Average cache size | Hitrate |");
    System.out.println("|---:|---:|---:|---:|");
    int[] trace = Trace.WEBLOG424_NOROBOTS.get();
    for (int i = 40; i <= 50; i++) {
      PlaybackResult res = runWithCache2k(Long.MAX_VALUE, i * 60, trace);
      markdownRow(i, res);
    }
  }

  @Test
  public void cache2kIdleScanTab45() {
    System.out.println("_Time To Idle emulation via scanning in cache2k 2.6_");
    System.out.println("| Scan round time/Minutes | Maximum cache size | Average cache size | Hitrate |");
    System.out.println("|---:|---:|---:|---:|");
    int[] trace = Trace.WEBLOG424_NOROBOTS.get();
    int i = 45;
    PlaybackResult res = runWithCache2k(true, Long.MAX_VALUE, i * 60, trace);
    markdownRow(i, res);
    res.histogram.print();
  }

  @Test
  public void cache2kIdleScanTab2000Cap() {
    System.out.println("_Time To Idle emulation via scanning in cache2k 2.6 with capacity limit of 2000 entries_");
    System.out.println("| Scan round time/Minutes | Maximum cache size | Average cache size | Hitrate |");
    int[] trace = Trace.WEBLOG424_NOROBOTS.get();
    for (int i = 40; i <= 50; i++) {
      PlaybackResult res = runWithCache2k(2000, i * 60, trace);
      markdownRow(i, res);
    }
  }

  @Test
  public void cache2kIdleScanTab1000Cap() {
    int[] trace = Trace.WEBLOG424_NOROBOTS.get();
    for (int i = 40; i <= 50; i++) {
      PlaybackResult res = runWithCache2k(1000, i * 60, trace);
      markdownRow(i, res);
    }
  }

  public static PlaybackResult playback(CacheSimulation cache, int[] trace) {
    PlaybackResult result = new PlaybackResult();
    for (int i = 0; i < trace.length; i += 2) {
      int time = trace[i];
      int key = trace[i+1];
      boolean hit = cache.access(time, key);
      result.record(hit, cache.getSize());
      if (DEBUG_OUTPUT) {
        if (key == TRACE_KEY) {
          System.err.println(hit ? "HIT" : "INSERT");
        }
        if (i % (trace.length / 1000) == 0) {
          System.err.println(cache);
        }
      }
    }
    return result;
  }

  static String extractColdSizeHotSize(Cache c) {
    String s = c.toString();
    int idx = s.indexOf("coldSize=");
    int idx2 = s.indexOf(", hotMax", idx);
    return s.substring(idx, idx2);
  }

  static long extractScanCount(Cache c) {
    String s = c.toString();
    final String str = "evictionScanCount=";
    int idx = s.indexOf(str);
    int idx2 = s.indexOf(", ", idx);
    return Long.parseLong(s.substring(idx + str.length(), idx2));
  }

  public static PlaybackResult runWithCache2k(long entryCapacity, long scanTimeSeconds, int[] trace) {
    return runWithCache2k(false, entryCapacity, scanTimeSeconds, trace);
  }

  public static PlaybackResult runWithCache2k(boolean histogram, long entryCapacity, long scanTimeSeconds, int[] trace) {
    SimulatedClock clock = new SimulatedClock(true, START_OFFSET_MILLIS);
    DurationHistogram histo = histogram ? new DurationHistogram(clock) : null;
    Cache2kBuilder<Integer, Data> builder = Cache2kBuilder.of(Integer.class, Data.class)
      .timeReference(clock)
      .scheduler(clock)
      .executor(clock.wrapExecutor(Runnable::run))
      .idleScanTime(scanTimeSeconds, TimeUnit.SECONDS)
      .entryCapacity(entryCapacity)
      .strictEviction(true);
    if (histo != null) {
      builder.addListener((CacheEntryEvictedListener<Integer, Data>) (x, entry)
        -> histo.recordEviction(entry.getValue()));
    }
    Cache<Integer, Data> cache = builder.build();
    PlaybackResult res = playback(new Cache2kCache(clock, cache), trace);
    res.histogram = histo;
    if (DEBUG_OUTPUT) {
      long scans = extractScanCount(cache);
      System.out.println("Scan count: " + scans);
      System.out.println("Scan per hour: " + (scans / 24));
      System.out.println(res);
      System.out.println(cache);
    }
    cache.close();
    return res;
  }

  static class DurationHistogram {
    static final long UNIT = 1000 * 60;
    static final long RESOLUTION = 5;
    final TreeMap<Long, List<Data>> map = new TreeMap<>();
    final TimeReference clock;
    public DurationHistogram(TimeReference clock) {
      this.clock = clock;
    }
    public void recordEviction(Data d) {
      if (DEBUG_OUTPUT && d.key == TRACE_KEY) {
        System.err.println("EVICT");
      }
      long delta = (clock.ticks() - seconds2millis(d.lastAccess)) / UNIT;
      delta -= delta % RESOLUTION;
      List<Data> l = map.computeIfAbsent(delta, k -> new ArrayList<>());
      l.add(d);
    }
    public void print() {
      int maxSize = 0;
      for (List<Data> l : map.values()) {
        maxSize = Math.max(maxSize, l.size());
      }
      for (Map.Entry<Long, List<Data>> e : map.entrySet()) {
        if (RESOLUTION == 1) {
          System.out.println("| " + e.getKey() + " | " + e.getValue().size() + " | " + e.getValue().get(0));
        } else {
          System.out.println("| " + e.getKey() + " | " + e.getValue().size() + " | "
            + bar(maxSize, e.getValue().size()) + " | ");
        }
      }
    }
  }

  static String bar(int max, int size) {
    final String bar =
      "::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::";
    return bar.substring(0, size * (bar.length()) / max);
  }

  public static class PlaybackResult {
    long hitCount;
    long accumulatedSize;
    long requestCount;
    long refreshCount;
    int maxSize = 0;
    DurationHistogram histogram;

    public void record(boolean hit, int size) {
      requestCount++;
      if (hit) { hitCount++; }
      accumulatedSize += size;
      maxSize = Math.max(maxSize, size);
    }

    public String getHitrate() {
      return String.format("%.2f", hitCount * 100D / requestCount);
    }

    public long getAverageSize() {
      return accumulatedSize / requestCount;
    }

    public long getRefreshCount() {
      return refreshCount;
    }

    public void setRefreshCount(long refreshCount) {
      this.refreshCount = refreshCount;
    }

    @Override
    public String toString() {
      return "PlaybackResult{" +
        "hitCount=" + hitCount +
        ", averageSize=" + (accumulatedSize / requestCount) +
        ", maxSize=" + maxSize +
        ", requestCount=" + requestCount +
        ", hitrate=" + getHitrate() +
        ", refreshCount=" + refreshCount +
        ", loadCount=" + (refreshCount + requestCount - hitCount) +
        '}';
    }
  }

  public interface CacheSimulation {
    /**
     * @param now time in seconds
     */
    boolean access(int now, int key);
    int getSize();
    default long getRefreshCount() { return 0; }
  }

  static class Data {
    int key;
    Data prev, next = this;
    int lastAccess;
    private void remove() {
      next.prev = prev;
      prev.next = next;
      next = prev = null;
    }
    private void insert(Data head) {
      prev = head;
      next = head.next;
      next.prev = this;
      head.next = this;
    }
    @Override
    public String toString() {
      return lastAccess + "," + key;
    }
  }

  static final long START_OFFSET_MILLIS = 1000;

  static class Cache2kCache implements CacheSimulation {

    private final SimulatedClock clock;
    private final Cache<Integer, Data> cache;
    private final int lastNow = -1;

    public Cache2kCache(SimulatedClock clock, Cache<Integer, Data> cache) {
      this.clock = clock;
      this.cache = cache;
    }

    @Override
    public boolean access(int time, int key) {
      long now = seconds2millis(time);
      advanceClock(now);
      Data d = cache.get(key);
      boolean hit = true;
      if (d == null) {
        d = new Data();
        d.key = key;
        cache.put(key, d);
        hit = false;
      }
      d.lastAccess = time;
      return hit;
    }

    private void advanceClock(long now) {
      if (now != lastNow) {
        long moveClock = now - clock.ticks();
        if (moveClock > 0) {
          try {
            clock.sleep(moveClock);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted");
          }
        }
      }
    }

    @Override
    public int getSize() {
      return cache.asMap().size();
    }

    @Override
    public String toString() {
      return extractColdSizeHotSize(cache);
    }

  }

  private static long seconds2millis(int time) {
    return time * 1000 + START_OFFSET_MILLIS;
  }

  /**
   * Use LRU list for time to idle.
   */
  static class LruCache implements CacheSimulation {

    private final int timeToIdle;
    private final Data lru = new Data();
    private final Map<Integer, Data> map = new HashMap<>();

    /** Default constructor, do not apply time to idle */
    public LruCache() {
      this(Integer.MAX_VALUE / 2);
    }

    public LruCache(int timeToIdle) {
      this.timeToIdle = timeToIdle;
    }

    @Override
    public boolean access(int now, int key) {
      Data d = map.get(key);
      boolean hit;
      if (hit = d != null) {
        d.remove();
      } else {
        d = new Data();
        d.key = key;
        map.put(key, d);
      }
      d.lastAccess = now;
      d.insert(lru);
      expireIdle(now);
      return hit;
    }

    private void expireIdle(int now) {
      while (lru.prev != lru && lru.prev.lastAccess <= (now - timeToIdle)) {
        map.remove(lru.prev.key);
        lru.prev.remove();
      }
    }

    @Override
    public int getSize() {
      return map.size();
    }

  }

}
