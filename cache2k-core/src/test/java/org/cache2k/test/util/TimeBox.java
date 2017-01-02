package org.cache2k.test.util;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2017 headissue GmbH, Munich
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

/**
 * Execute a piece of work and assertions. The assertions are only
 * respected if everything happens in a given timebox.
 *
 * @author Jens Wilke
 */
public class TimeBox {

  long startTime = System.currentTimeMillis();
  long timeBox;

  public static TimeBox millis(long t) {
    TimeBox b = new TimeBox();
    b.timeBox = t;
    return b;
  }

  public static TimeBox seconds(long t) {
    return millis(t * 1000);
  }

  /**
   * Immediately executes the runnable. This method serves the purpose to make the
   * code look more fluent.
   */
  public TimeBox work(Runnable r) {
    r.run();
    return this;
  }

  /**
   * Execute the runnable. AssertionErrors will be suppressed if the execution
   * is not happening within the given timebox.
   */
  public void check(Runnable r) {
    try {
      r.run();
    } catch (AssertionError ex) {
      if (System.currentTimeMillis() - startTime < timeBox) {
        throw ex;
      }
    }
  }

}
