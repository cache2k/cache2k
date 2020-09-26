package org.cache2k.core.timing;

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

import org.cache2k.core.util.SimulatedClock;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Only test special cases not covered by normal testing.
 *
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class TimerTest {

  @Test
  public void misc() {
    long startTime = 100;
    Timer st =
      new DefaultTimer(new SimulatedClock(startTime));
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

  static class MyTimerTask extends TimerTask {
    volatile boolean executed = false;
    @Override
    public void run() {
      executed = true;
    }
  }

}
