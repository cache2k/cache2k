package org.cache2k.core.util;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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

import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;

/**
 * Only test special cases not covered by normal testing.
 *
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class SimpleTimerTest {

  @Test
  public void purge() {
    long _START_TIME = 100;
    long _OFFSET = 1;
    int _SIZE = 123;
    SimpleTimer t =
      new SimpleTimer(
        new SimulatedClock(_START_TIME, true),
        SimpleTimerTest.class.getName(), true);
    MyTimerTask[] arr = new MyTimerTask[_SIZE];
    for (int i = 0; i < _SIZE; i++) {
      arr[i] = new MyTimerTask();
      t.schedule(arr[i], _START_TIME + i + _OFFSET);
      if (i%3 == 0) {
        arr[i].cancel();
      }
    }
    t.purge();
    t.timeReachedEvent(_START_TIME + _SIZE + _OFFSET);
    int count = 0;
    for (int i = 0; i < _SIZE; i++) {
      if (arr[i].executed) {
        count++;
      }
    }
    assertEquals(82, count);
  }

  @Test
  public void misc() {
    long _START_TIME = 100;
    SimpleTimer st =
      new SimpleTimer(
        new SimulatedClock(_START_TIME, true),
        SimpleTimerTest.class.getName(), true);
    SimpleTimerTask t = new MyTimerTask();
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
    st.cancel();
    try {
      st.schedule(t, 15);
    } catch (IllegalStateException ex) {
    }
  }

  static class MyTimerTask extends SimpleTimerTask {
    volatile boolean executed = false;
    @Override
    public void run() {
      executed = true;
    }
  }

}
