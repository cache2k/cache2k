package org.cache2k.core.timing;

/*
 * #%L
 * cache2k core implementation
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

import org.assertj.core.api.Condition;
import org.cache2k.Cache2kBuilder;
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.core.api.InternalClock;
import org.cache2k.core.api.Scheduler;
import org.cache2k.core.util.SimulatedClock;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;

/**
 * Only test special cases not covered by normal testing.
 *
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class TimerTest {

  final List<MyTimerTask> executed = new CopyOnWriteArrayList<MyTimerTask>();
  Timer timer;
  MyClock clock;

  void init(long startTime, long lagMillis, int steps) {
    clock = new MyClock(startTime);
    timer = new DefaultTimer(clock, lagMillis, steps);
  }

  void init(long startTime, long lagMillis) {
    clock = new MyClock(startTime);
    timer = new DefaultTimer(clock, lagMillis);
  }

  List<MyTimerTask> schedule(long... times) {
    List<MyTimerTask> result = new ArrayList<MyTimerTask>();
    for (long millis : times) {
      MyTimerTask t = new MyTimerTask();
      t.scheduleTime = millis;
      timer.schedule(t, millis);
      result.add(t);
    }
    return result;
  }

  /**
   * Move time forward and return tasks that
   * are executed.
   */
  void run(long time) {
    clock.moveTo(time);
    if (clock.scheduledTime <= time) {
      Runnable action = clock.scheduled;
      clock.reset();
      action.run();
    }
  }

  Collection<MyTimerTask> extractUpTo(Collection<MyTimerTask> l, long time) {
    List<MyTimerTask> result = new ArrayList<MyTimerTask>();
    Iterator<MyTimerTask> it = l.iterator();
    while (it.hasNext()) {
      MyTimerTask t = it.next();
      if (t.scheduleTime <= time) {
        result.add(t);
        it.remove();
      }
    }
    return result;
  }

  @Test
  public void misc() {
    long startTime = 10;
    Timer st =
      new DefaultTimer(new SimulatedClock(startTime), 1);
    TimerTask t = new MyTimerTask();
    try {
      st.schedule(t, -5);
    } catch (IllegalArgumentException ex) {
    }
    st.schedule(t, 10);
    try {
      st.schedule(t, 15);
    } catch (IllegalStateException ex) {
    }
    t = new MyTimerTask();
    st.cancelAll();
    try {
      st.schedule(t, 15);
    } catch (IllegalStateException ex) {
    }
  }

  @Test
  public void misc2() {
    long startTime = 10;
    long lagTime = 100;
    MyClock clock = new MyClock(startTime);
    Timer st = new DefaultTimer(clock, lagTime);
    MyTimerTask tt1 = new MyTimerTask();
    long t1 = 123456789;
    st.schedule(tt1, t1);
    assertNotNull("something scheduled", clock.scheduled);
    MyTimerTask tt2 = new MyTimerTask();
    long t2 = 543;
    st.schedule(tt2, t2);
    clock.moveTo(t2 + lagTime);
    clock.scheduled.run();
    assertTrue(tt2.executed);
    assertFalse(tt1.executed);
    clock.scheduled.run(); // stray
    assertFalse(tt1.executed);
    clock.moveTo(t1 - 1);
    clock.scheduled.run(); // stray
    assertFalse(tt1.executed);
    clock.moveTo(t1 + lagTime);
    clock.scheduled.run();
    assertTrue(tt1.executed);
  }

  @Test
  public void scheduleInPast() {
    init(100, 10, 10);
    MyTimerTask t = schedule(50).get(0);
    assertTrue(t.executed);
  }

  /**
   * Time is already reached, so task executes immediately.
   */
  @Test
  public void scheduleStartTime() {
    long startTime = 100;
    init(startTime, 10, 10);
    MyTimerTask t = schedule(startTime).get(0);
    assertTrue(t.executed);
  }

  @Test
  public void scheduleReachedTime1() {
    long startTime = 100;
    init(startTime, 10, 10);
    run(145);
    MyTimerTask t = schedule(140).get(0);
    assertFalse("inserted in timer, since timer structure was not advanced", t.executed);
    run(140 + 10);
    assertTrue(t.executed);
  }

  @Test
  public void scheduleDistantFuture() {
    long startTime = 100;
    init(startTime, 10, 10);
    run(145);
    MyTimerTask t = schedule(987654).get(0);
    assertFalse(t.executed);
    run(987654 + 27);
    assertTrue(t.executed);
  }

  @Test
  public void scheduleReachedTime2() {
    long startTime = 100;
    long endTime = startTime + 100;
    long lagMillis = 3;
    init(startTime, lagMillis, 4);
    List<MyTimerTask> scheduled = new ArrayList<MyTimerTask>();
    for (long i = startTime; i < endTime; i++) {
      scheduled.addAll(schedule(i, i - 1, i - 3, i - 27));
      run(i);
      scheduled.addAll(schedule(i, i - 1, i - 3, i - 27));
    }
    run(endTime + lagMillis);
    assertThat(scheduled).are(new Condition<MyTimerTask>() {
      @Override
      public boolean matches(MyTimerTask value) {
        return value.executed;
      }
    });
  }

  @Test
  public void scheduleNow() {
    init(100, 10, 10);
    MyTimerTask t = schedule(0).get(0);
    assertTrue(t.executed);
  }

  @Test
  public void startTime0() {
    init(0, 10, 10);
    MyTimerTask t = schedule(0).get(0);
    assertTrue(t.executed);
  }

  @Test
  public void insertSequence() {
    long lagMillis = 2;
    long startTime = 2;
    long endTime = 31;
    List<MyTimerTask> scheduled = new ArrayList<MyTimerTask>();
    init(startTime, lagMillis, 2);
    for (long i = startTime + 1; i < endTime; i++) {
      scheduled.addAll(schedule(i));
    }
    for (long i = startTime + 1; i <= endTime + lagMillis; i++) {
      run(i);
      Collection<MyTimerTask> expected = extractUpTo(scheduled, i - lagMillis);
      assertThat(executed).containsAll(expected);
    }
  }

  @Test
  public void insertSequence3() {
    long lagMillis = 1;
    long startTime = 2;
    long endTime = 31;
    List<MyTimerTask> scheduled = new ArrayList<MyTimerTask>();
    init(startTime, lagMillis, 2);
    for (long i = startTime + 1; i < endTime; i++) {
      scheduled.addAll(schedule(i));
      scheduled.addAll(schedule(i));
      scheduled.addAll(schedule(i));
    }
    for (long i = startTime + 1; i <= endTime + lagMillis; i++) {
      run(i);
      Collection<MyTimerTask> expected = extractUpTo(scheduled, i - lagMillis);
      assertThat(executed).containsAll(expected);
    }
  }

  public void test2_2_x(long offset) {
    long lagMillis = 2;
    long startTime = 2;
    init(startTime, lagMillis, 123);
    MyTimerTask t = schedule(startTime + offset).get(0);
    run(startTime + offset + lagMillis);
    assertTrue(t.executed);
  }

  @Test
  public void executedAfterLag_2_1() {
    test2_2_x(1);
  }
  @Test
  public void executedAfterLag_2_2() {
    test2_2_x(2);
  }

  @Test(expected = IllegalArgumentException.class)
  public void scheduleMax() {
    init(100, 10, 2);
    schedule(Long.MAX_VALUE);
  }

  @Test
  public void scheduleMaxMinus1() {
    init(2, 1, 2);
    schedule(Long.MAX_VALUE - 1);
  }

  @Test
  public void schedule100Years() {
    init(System.currentTimeMillis(), 2);
    schedule(clock.millis() + 100 * 1000 * 60 * 60 * 24 * 365);
  }

  @Test
  public void config() {
    long lag = hashCode();
    assertEquals("builder and config bean working", lag,
      Cache2kBuilder.forUnknownTypes()
      .timerLag(lag, TimeUnit.MILLISECONDS).toConfiguration().getTimerLag().toMillis());
    assertEquals("eternal / overflow", Long.MAX_VALUE,
      Cache2kBuilder.forUnknownTypes()
        .timerLag(Long.MAX_VALUE, TimeUnit.SECONDS).toConfiguration().getTimerLag().toMillis());
    assertSame("eternal / overflow", Cache2kConfiguration.ETERNAL_DURATION,
      Cache2kBuilder.forUnknownTypes()
        .timerLag(Long.MAX_VALUE, TimeUnit.SECONDS).toConfiguration().getTimerLag());
  }

  static long taskIdCounter = 0;

  class MyTimerTask extends TimerTask implements Comparable<MyTimerTask> {
    long id = taskIdCounter++;
    long scheduleTime;
    volatile boolean executed = false;

    @Override
    protected void action() {
      executed = true;
      TimerTest.this.executed.add(this);
    }

    @Override
    public String toString() {
      return "MyTimerTask{" +
        "id=" + id +
        ", scheduleTime=" + scheduleTime +
        ", executed=" + executed +
        ", super=" + super.toString() +
        '}';
    }

    @Override
    public int compareTo(MyTimerTask o) {
      return (int) (o.scheduleTime - scheduleTime);
    }
  }

  static class MyClock implements InternalClock, Scheduler {

    long time;
    Runnable scheduled;
    long scheduledTime = Long.MAX_VALUE;

    MyClock(long startTime) {
      this.time = startTime;
    }

    void moveTo(long newTime) {
      time = newTime;
    }

    void reset() {
      scheduledTime = Long.MAX_VALUE;
      scheduled = null;
    }

    @Override
    public long millis() {
      return time;
    }

    @Override
    public void sleep(long millis) { assert false; }

    @Override
    public void schedule(final Runnable runnable, long millis) {
      assertThat(millis).isLessThanOrEqualTo(scheduledTime);
      scheduledTime = millis;
      scheduled = new Runnable() {
        @Override
        public void run() {
          scheduledTime = Long.MAX_VALUE;
          scheduled = null;
          runnable.run();
        }
      };
    }

    @Override
    public void execute(Runnable command) {
      command.run();
    }

  }

}
