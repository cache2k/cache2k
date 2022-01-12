package org.cache2k.core.util;

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

import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;

/**
 * @author Jens Wilke
 */
public class TimestampFormatterTest {

  /**
   * Replace the actual digits with hashes. The numeric result of the formatting depends on system
   * time. The tests just check for the actual formatting and the handing of the milliseconds.
   */
  static String hashDigits(String s) {
    return s.replaceAll("[0-9]", "#");
  }

  private static Instant toInstant(long millis) {
    return Instant.ofEpochMilli(millis);
  }

  @Test
  public void test() {
    long t0 = 0;
    assertEquals("####-##-##T##:##:##", hashDigits(Util.formatTime(toInstant(t0))));
  }

  @Test
  public void testWithMillis() {
    long t0 = 123;
    assertEquals("####-##-##T##:##:##.###", hashDigits(Util.formatTime(toInstant(t0))));
  }

  @Test
  public void testWithMillis2() {
    long t0 = 120;
    assertEquals("####-##-##T##:##:##.##", hashDigits(Util.formatTime(toInstant(t0))));
  }

}
