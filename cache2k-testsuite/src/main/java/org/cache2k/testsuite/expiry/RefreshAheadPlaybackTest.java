package org.cache2k.testsuite.expiry;

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

import org.cache2k.testing.category.TimingTests;
import org.cache2k.testsuite.eviction.TimeTracePlaybackTest;
import org.cache2k.testsuite.eviction.Trace;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Jens Wilke
 */
@Category(TimingTests.class)
public class RefreshAheadPlaybackTest {

  static class Data {
    int loadTime;
    int expiryTime;
    int refreshTime;
    int accessCount;
  }

  static class ExpiryRefreshSimulation implements TimeTracePlaybackTest.CacheSimulation {

    Map<Integer, Data> cache = new HashMap<>();
    final int ttl;
    final int refreshTime;
    int lastAccessTime;
    long refreshCount;
    int firstAccess = 1;

    public ExpiryRefreshSimulation(int ttl, int refreshTime) {
      this.ttl = ttl;
      this.refreshTime = refreshTime;
    }

    @Override
    public boolean access(int now, int key) {
      lastAccessTime = now;
      Data d = cache.get(key);
      if (d == null) {
        d = new Data();
        cache.put(key, d);
        initialLoad(d, now);
        return false;
      }
      if (refreshBeforeAccessed(now, d)) {
        refreshCount++;
      }
      if (now >= d.expiryTime) {
        initialLoad(d, now);
        return false;
      }
      if (refreshWhenAccessed(now, d)) {
        refreshCount++;
      }
      d.accessCount++;
      return true;
    }

    private void initialLoad(Data d, int now) {
      load(d, now);
      d.accessCount = firstAccess;
    }

    protected boolean refreshBeforeAccessed(int now, Data d) {
      return false;
    }

    protected boolean refreshWhenAccessed(int now, Data d) {
      return false;
    }

    protected void load(Data d, int loadTime) {
      d.loadTime = loadTime;
      d.accessCount = 0;
      if (ttl == Integer.MAX_VALUE) {
        d.expiryTime = Integer.MAX_VALUE;
      } else {
        d.expiryTime = loadTime + ttl;
      }
      d.refreshTime = loadTime + refreshTime;
    }

    @Override
    public int getSize() {
      for (Data d : cache.values()) {
        if (refreshBeforeAccessed(lastAccessTime, d)) {
          refreshCount++;
        }
      }
      Collection<Map.Entry<Integer, Data>> copy = new ArrayList<>();
      copy.addAll(cache.entrySet());
      for (Map.Entry<Integer, Data> e : copy) {
        if (lastAccessTime >= e.getValue().expiryTime) {
          cache.remove(e.getKey());
        }
      }
      return cache.size();
    }

    @Override
    public long getRefreshCount() {
      return refreshCount;
    }
  }

  /**
   * Caffeine approach: refresh is triggered after a time period when the entry
   * is accessed. The refresh happens after the time period and the triggering access.
   */
  static class AccessTriggeredRefreshSimulation extends ExpiryRefreshSimulation {

    public AccessTriggeredRefreshSimulation(int ttl, int refreshPercentage) {
      super(ttl, refreshPercentage);
    }

    @Override
    protected boolean refreshWhenAccessed(int now, Data d) {
      if (now >= d.refreshTime) {
        load(d, now);
        return true;
      }
      return false;
    }

  }

  /**
   * Cache2k approach: refresh triggered after time period when there
   * were enough requests in between. The refresh happens after the time period.
   */
  static class TimerTriggeredRefreshSimulation extends ExpiryRefreshSimulation {

    int minAccess;

    public TimerTriggeredRefreshSimulation(int ttl, int refreshTime, int firstAccess, int minAccess) {
      super(ttl, refreshTime);
      this.firstAccess = firstAccess;
      this.minAccess = minAccess;
    }

    /**
     * In the simulation this is called when the entry is accessed, however, the
     * refresh would happen at refreshTime in reality.
     */
    @Override
    protected boolean refreshBeforeAccessed(int now, Data d) {
      if (now >= d.refreshTime && d.accessCount >= minAccess) {
        load(d, d.refreshTime);
        return true;
      }
      return false;
    }

  }

  public void expiryOnly(int ttl) {
    int[] trace = Trace.WEBLOG424_NOROBOTS.get();
    TimeTracePlaybackTest.PlaybackResult res =
      TimeTracePlaybackTest.playback(new ExpiryRefreshSimulation(ttl, -1), trace);
    System.err.println(res.toString());
  }

  @Test
  public void caffeine() {
    int i = 50;
    final int ttl = 3 * 60;
    int[] trace = Trace.WEBLOG424_NOROBOTS.get();
    ExpiryRefreshSimulation sim = new AccessTriggeredRefreshSimulation(ttl, i);
    TimeTracePlaybackTest.PlaybackResult res =
      TimeTracePlaybackTest.playback(sim, trace);
    res.setRefreshCount(sim.getRefreshCount());
    System.err.println("Caffeine " + i + " " + res.toString());
  }

  @Test
  public void test() {
    int[] trace = Trace.WEBLOG424_NOROBOTS.get();
    final int ttl = 3 * 60;
    expiryOnly(ttl);
    expiryOnly(Integer.MAX_VALUE);
    for (int i = 50; i <= 95; i+=5) {
      ExpiryRefreshSimulation sim = new TimerTriggeredRefreshSimulation(ttl, ttl * i / 100, 1, 1);
      TimeTracePlaybackTest.PlaybackResult res =
        TimeTracePlaybackTest.playback(sim, trace);
      res.setRefreshCount(sim.getRefreshCount());
      System.err.println("Cache2k 0/1 " + i + " " + res.toString());
    }
    for (int i = 50; i <= 95; i+=5) {
      ExpiryRefreshSimulation sim = new TimerTriggeredRefreshSimulation(ttl, ttl * i / 100, 0, 1);
      TimeTracePlaybackTest.PlaybackResult res =
        TimeTracePlaybackTest.playback(sim, trace);
      res.setRefreshCount(sim.getRefreshCount());
      System.err.println("Cache2k 2/1 " + i + " " + res.toString());
    }
    for (int i = 96; i <= 99; i++) {
      ExpiryRefreshSimulation sim = new TimerTriggeredRefreshSimulation(ttl, ttl * i / 100, 0, 1);
      TimeTracePlaybackTest.PlaybackResult res =
        TimeTracePlaybackTest.playback(sim, trace);
      res.setRefreshCount(sim.getRefreshCount());
      System.err.println("Cache2k 2/1 " + i + " " + res.toString());
    }
    for (int i = 50; i <= 95; i+=5) {
      ExpiryRefreshSimulation sim = new TimerTriggeredRefreshSimulation(ttl, ttl * i / 100, 1, 2);
      TimeTracePlaybackTest.PlaybackResult res =
        TimeTracePlaybackTest.playback(sim, trace);
      res.setRefreshCount(sim.getRefreshCount());
      System.err.println("Cache2k 2 " + i + " " + res.toString());
    }
    for (int i = 96; i <= 99; i++) {
      ExpiryRefreshSimulation sim = new TimerTriggeredRefreshSimulation(ttl, ttl * i / 100, 1, 2);
      TimeTracePlaybackTest.PlaybackResult res =
        TimeTracePlaybackTest.playback(sim, trace);
      res.setRefreshCount(sim.getRefreshCount());
      System.err.println("Cache2k 2 " + i + " " + res.toString());
    }
    for (int i = 50; i <= 95; i+=5) {
      ExpiryRefreshSimulation sim = new TimerTriggeredRefreshSimulation(ttl, ttl * i / 100, 1, 3);
      TimeTracePlaybackTest.PlaybackResult res =
        TimeTracePlaybackTest.playback(sim, trace);
      res.setRefreshCount(sim.getRefreshCount());
      System.err.println("Cache2k 3 " + i + " " + res.toString());
    }
    for (int i = 96; i <= 99; i++) {
      ExpiryRefreshSimulation sim = new TimerTriggeredRefreshSimulation(ttl, ttl * i / 100, 1, 3);
      TimeTracePlaybackTest.PlaybackResult res =
        TimeTracePlaybackTest.playback(sim, trace);
      res.setRefreshCount(sim.getRefreshCount());
      System.err.println("Cache2k 3 " + i + " " + res.toString());
    }
    for (int i = 20; i <= 95; i+=5) {
      ExpiryRefreshSimulation sim = new AccessTriggeredRefreshSimulation(ttl, ttl * i / 100);
      TimeTracePlaybackTest.PlaybackResult res =
        TimeTracePlaybackTest.playback(sim, trace);
      res.setRefreshCount(sim.getRefreshCount());
      System.err.println("Caffeine " + i + " " + res.toString());
    }
    {
      ExpiryRefreshSimulation sim = new AccessTriggeredRefreshSimulation(Integer.MAX_VALUE, ttl);
      TimeTracePlaybackTest.PlaybackResult res =
        TimeTracePlaybackTest.playback(sim, trace);
      res.setRefreshCount(sim.getRefreshCount());
      System.err.println("Caffeine ttl off, refresh = tll, " + res.toString());
    }
    {
      ExpiryRefreshSimulation sim = new AccessTriggeredRefreshSimulation(ttl * 110 / 100, ttl);
      TimeTracePlaybackTest.PlaybackResult res =
        TimeTracePlaybackTest.playback(sim, trace);
      res.setRefreshCount(sim.getRefreshCount());
      System.err.println("Caffeine ttl 110%, refresh = tll, " + res.toString());
    }
    {
      ExpiryRefreshSimulation sim = new AccessTriggeredRefreshSimulation(ttl * 2, ttl);
      TimeTracePlaybackTest.PlaybackResult res =
        TimeTracePlaybackTest.playback(sim, trace);
      res.setRefreshCount(sim.getRefreshCount());
      System.err.println("Caffeine ttl 200%, refresh = tll, " + res.toString());
    }
    {
      ExpiryRefreshSimulation sim = new TimerTriggeredRefreshSimulation(ttl * 2, ttl * 199 / 100, 0,1);
      TimeTracePlaybackTest.PlaybackResult res =
        TimeTracePlaybackTest.playback(sim, trace);
      res.setRefreshCount(sim.getRefreshCount());
      System.err.println("Cache2k ttl 200%, refresh = 199%, min=2/1, " + res.toString());
    }
    {
      ExpiryRefreshSimulation sim = new TimerTriggeredRefreshSimulation(ttl * 2, ttl * 199 / 100, 1,2);
      TimeTracePlaybackTest.PlaybackResult res =
        TimeTracePlaybackTest.playback(sim, trace);
      res.setRefreshCount(sim.getRefreshCount());
      System.err.println("Cache2k ttl 200%, refresh = 199%, min=2, " + res.toString());
    }
    {
      ExpiryRefreshSimulation sim = new TimerTriggeredRefreshSimulation(ttl * 2, ttl * 199 / 100, 1,3);
      TimeTracePlaybackTest.PlaybackResult res =
        TimeTracePlaybackTest.playback(sim, trace);
      res.setRefreshCount(sim.getRefreshCount());
      System.err.println("Cache2k ttl 200%, refresh = 199%, min=3, " + res.toString());
    }
  }

}
