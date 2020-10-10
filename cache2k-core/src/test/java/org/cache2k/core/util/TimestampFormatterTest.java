package org.cache2k.core.util;

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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Jens Wilke
 */
public class TimestampFormatterTest {

  @Test
  public void test() {
    long t0 = 0;
    assertEquals("1970-01-01T10:00:00", Util.formatMillis(t0));
  }

  @Test
  public void testWithMillis() {
    long t0 = 123;
    assertEquals("1970-01-01T10:00:00.123", Util.formatMillis(t0));
  }

  @Test
  public void testWithMillis2() {
    long t0 = 120;
    assertEquals("1970-01-01T10:00:00.12", Util.formatMillis(t0));
  }

}
