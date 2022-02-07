package org.cache2k.core.timing;

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

import static java.lang.Long.MAX_VALUE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import org.assertj.core.api.Condition;
import org.cache2k.operation.TimeReference;
import org.cache2k.operation.Scheduler;
import org.cache2k.testing.SimulatedClock;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.fail;
import static org.cache2k.Cache2kBuilder.forUnknownTypes;
import static org.cache2k.config.Cache2kConfig.EXPIRY_ETERNAL;
import static org.cache2k.core.timing.TimerTask.Sentinel;

/**
 * Only test special cases not covered by normal testing.
 *
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class TimerTest {

  final List<MyTimerTask> executed = new CopyOnWriteArrayList<>();
  Timer timer;
  MyClock clock;

  void init(long startTime, long lagMillis, int steps) {
    clock = new MyClock(startTime);
    timer = new DefaultTimer(clock, clock, lagMillis, steps);
  }

  void init(long startTime, long lagMillis) {
    clock = new MyClock(startTime);
    timer = new DefaultTimer(clock, clock, lagMillis);
  }

  List<MyTimerTask> schedule(long... times) {
    List<MyTimerTask> result = new ArrayList<>();
    for (long millis : times) {
      MyTimerTask t = new MyTimerTask();
      t.scheduleTime = millis;
      timer.schedule(t, millis);
      result.add(t);
    }
    return result;
  }

  Collection<MyTimerTask> extractUpTo(Collection<MyTimerTask> l, long time) {
    List<MyTimerTask> result = new ArrayList<>();
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
    SimulatedClock simulatedClock = new SimulatedClock(startTime);
    Timer st =
      new DefaultTimer(simulatedClock, simulatedClock, 1);
    TimerTask t = new MyTimerTask();
    assertThat(t.getState()).isEqualTo("unscheduled");
    try {
      st.schedule(t, -5);
      fail("exception expected");
    } catch (IllegalArgumentException ex) {
    }
    st.schedule(t, 10);
    try {
      st.schedule(t, 15);
      fail("exception expected, already scheduled");
    } catch (IllegalStateException ex) {
    }
    t = new Sentinel();
    t.execute();
    t.next = null;
    t.run();
  }

  /**
   *
   */
  @Test
  public void immediateExecution() {
    MyTimerTask t = new MyTimerTask();
    t.markForImmediateExecution();
    assertThat(t.isScheduled()).isTrue();
    t.run();
    assertThat(t.isExecuted()).isTrue();
    assertThat(t.executed).isTrue();
    t = new MyTimerTask();
    t.markForImmediateExecution();
    assertThat(t.isExecuted()).isTrue();
    assertThat(t.executed).isFalse();
    t.run();
    assertThat(t.isExecuted()).isTrue();
    assertThat(t.executed).isTrue();
  }

  @Test
  public void misc2() {
    long startTime = 10;
    long lagTime = 100;
    MyClock clock = new MyClock(startTime);
    Timer st = new DefaultTimer(clock, clock, lagTime);
    MyTimerTask tt1 = new MyTimerTask();
    long t1 = 123456789;
    st.schedule(tt1, t1);
    assertThat(clock.scheduledTime).isNotNull();
    MyTimerTask tt2 = new MyTimerTask();
    long t2 = 543;
    st.schedule(tt2, t2);
    clock.moveTo(t2 + lagTime);
    clock.scheduled.run();
    assertThat(tt2.executed).isTrue();
    assertThat(tt1.executed).isFalse();
    clock.moveTo(t1 - 1);
    clock.scheduled.run(); // stray
    assertThat(tt1.executed).isFalse();
    clock.moveTo(t1 + lagTime);
    clock.scheduled.run();
    assertThat(tt1.executed).isTrue();
  }

  @Test
  public void scheduleInPast() {
    init(100, 10, 10);
    MyTimerTask t = schedule(50).get(0);
    assertThat(t.executed).isTrue();
    assertThat(t.getState()).isEqualTo("executed");
  }

  /**
   * Time is already reached, so task executes immediately.
   */
  @Test
  public void scheduleStartTime() {
    long startTime = 100;
    init(startTime, 10, 10);
    MyTimerTask t = schedule(startTime).get(0);
    assertThat(t.executed).isTrue();
  }

  @Test
  public void scheduleReachedTime0() {
    long startTime = 100;
    init(startTime, 10, 10);
    MyTimerTask t = schedule(140).get(0);
    assertThat(t.isScheduled()).isTrue();
    clock.run(150);
    assertThat(t.executed).isTrue();
  }

  @Test
  public void scheduleAndCancel() {
    long startTime = 100;
    init(startTime, 10, 10);
    MyTimerTask t = schedule(140).get(0);
    assertThat(t.isScheduled()).isTrue();
    assertThat(t.cancel()).isTrue();
    assertThat(t.getState()).isEqualTo("cancelled");
    assertThat(t.cancel()).isFalse();
    clock.run(150);
    assertThat(t.executed).isFalse();
  }

  @Test
  public void scheduleAndCancelAll() {
    long startTime = 100;
    init(startTime, 10, 10);
    MyTimerTask t = schedule(140).get(0);
    assertThat(t.isScheduled()).isTrue();
    timer.cancelAll();
    clock.run(150);
    assertThat(t.executed).isFalse();
  }

  @Test
  public void scheduleReachedTime1() {
    long startTime = 100;
    init(startTime, 10, 10);
    clock.run(145);
    MyTimerTask t = schedule(140).get(0);
    assertThat(t.executed)
      .as("inserted in timer, since timer structure was not advanced")
      .isFalse();
    clock.run(140 + 10);
    assertThat(t.executed).isTrue();
  }

  @Test
  public void scheduleDistantFuture() {
    long startTime = 100;
    init(startTime, 10, 10);
    clock.run(145);
    MyTimerTask t = schedule(987654).get(0);
    assertThat(t.executed).isFalse();
    assertThat(t.getState()).isEqualTo("scheduled");
    assertThat(t.isScheduled()).isTrue();
    clock.run(987654 + 27);
    assertThat(t.executed).isTrue();
  }

  @Test
  public void scheduleReachedTime2() {
    long startTime = 100;
    long endTime = startTime + 100;
    long lagMillis = 3;
    init(startTime, lagMillis, 4);
    List<MyTimerTask> scheduled = new ArrayList<>();
    for (long i = startTime; i < endTime; i++) {
      scheduled.addAll(schedule(i, i - 1, i - 3, i - 27));
      clock.run(i);
      scheduled.addAll(schedule(i, i - 1, i - 3, i - 27));
    }
    clock.run(endTime + lagMillis);
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
    assertThat(t.executed).isTrue();
  }

  @Test
  public void startTime0() {
    init(0, 10, 10);
    MyTimerTask t = schedule(0).get(0);
    assertThat(t.executed).isTrue();
  }

  @Test
  public void insertSequence() {
    long lagMillis = 2;
    long startTime = 2;
    long endTime = 31;
    List<MyTimerTask> scheduled = new ArrayList<>();
    init(startTime, lagMillis, 2);
    for (long i = startTime + 1; i < endTime; i++) {
      scheduled.addAll(schedule(i));
    }
    for (long i = startTime + 1; i <= endTime + lagMillis; i++) {
      clock.run(i);
      Collection<MyTimerTask> expected = extractUpTo(scheduled, i - lagMillis);
      assertThat(executed).containsAll(expected);
    }
  }

  @Test
  public void insertSequence3() {
    long lagMillis = 1;
    long startTime = 2;
    long endTime = 31;
    List<MyTimerTask> scheduled = new ArrayList<>();
    init(startTime, lagMillis, 2);
    for (long i = startTime + 1; i < endTime; i++) {
      scheduled.addAll(schedule(i));
      scheduled.addAll(schedule(i));
      scheduled.addAll(schedule(i));
    }
    for (long i = startTime + 1; i <= endTime + lagMillis; i++) {
      clock.run(i);
      Collection<MyTimerTask> expected = extractUpTo(scheduled, i - lagMillis);
      assertThat(executed).containsAll(expected);
    }
  }

  public void test2_2_x(long offset) {
    long lagMillis = 2;
    long startTime = 2;
    init(startTime, lagMillis, 123);
    MyTimerTask t = schedule(startTime + offset).get(0);
    clock.run(startTime + offset + lagMillis);
    assertThat(t.executed).isTrue();
  }

  @Test
  public void executedAfterLag_2_1() {
    test2_2_x(1);
  }
  @Test
  public void executedAfterLag_2_2() {
    test2_2_x(2);
  }

  @Test
  public void scheduleMaxMinus1() {
    init(2, 1, 2);
    schedule(Long.MAX_VALUE - 1);
  }

  @Test
  public void schedule100Years() {
    init(System.currentTimeMillis(), 2);
    schedule(clock.ticks() + 100L * 1000 * 60 * 60 * 24 * 365);
  }

  @Test
  public void config() {
    long lag = hashCode();
    assertThat(forUnknownTypes()
      .timerLag(lag, MILLISECONDS).config().getTimerLag().toMillis())
      .as("builder and config bean working")
      .isEqualTo(lag);
    assertThat(forUnknownTypes()
      .timerLag(MAX_VALUE, SECONDS).config().getTimerLag().toMillis())
      .as("eternal / overflow")
      .isEqualTo(MAX_VALUE);
    assertThat(forUnknownTypes()
      .timerLag(MAX_VALUE, SECONDS).config().getTimerLag())
      .as("eternal / overflow")
      .isSameAs(EXPIRY_ETERNAL);
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

  static class MyClock extends TimeReference.Milliseconds implements  Scheduler {

    private long time;
    private Runnable scheduled;
    private long scheduledTime = Long.MAX_VALUE;

    MyClock(long startTime) {
      this.time = startTime;
    }

    /**
     * Move time forward and return tasks that are executed.
     */
    void run(long time) {
      moveTo(time);
      if (scheduledTime <= time) {
        Runnable action = scheduled;
        reset();
        action.run();
      }
    }

    void moveTo(long newTime) {
      time = newTime;
    }

    void reset() {
      scheduledTime = Long.MAX_VALUE;
      scheduled = null;
    }

    @Override
    public long ticks() {
      return time;
    }

    @Override
    public void sleep(long ticks) { assert false; }

    @Override
    public void schedule(Runnable runnable, long delayMillis) {
      long ticks = time + toTicks(Duration.ofMillis(delayMillis));
      assertThat(ticks).isLessThanOrEqualTo(scheduledTime);
      scheduledTime = ticks;
      scheduled = () -> {
        scheduledTime = Long.MAX_VALUE;
        scheduled = null;
        runnable.run();
      };
    }

    @Override
    public void execute(Runnable command) {
      command.run();
    }

  }

}
