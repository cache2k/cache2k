package org.cache2k;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.expiry.Expiry;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
public class ExpiryTest {

  @Test(expected = IllegalArgumentException.class)
  public void toSharpException() {
    Expiry.toSharpTime(-123);
  }

  @Test
  public void toSharp() {
    assertEquals(-123, Expiry.toSharpTime(123));
  }

  @Test
  public void testEarliestTime() {
    assertEquals(123, Expiry.earliestTime(100, 123, 150));
    assertEquals(123, Expiry.earliestTime(100, 150, 123));
    assertEquals(123, Expiry.earliestTime(100, 123, 88));
    assertEquals(123, Expiry.earliestTime(100, 88, 123));

    assertEquals(123, Expiry.earliestTime(100, 123, 150));
    assertEquals(123, Expiry.earliestTime(100, 150, 123));
    assertEquals(123, Expiry.earliestTime(100, 123, 0));
    assertEquals(123, Expiry.earliestTime(100, 0, 123));

    assertEquals(123, Expiry.earliestTime(123, 123, 150));
    assertEquals(123, Expiry.earliestTime(123, 150, 123));
    assertEquals(123, Expiry.earliestTime(123, 123, 88));
    assertEquals(123, Expiry.earliestTime(123, 88, 123));

    assertEquals(Long.MAX_VALUE, Expiry.earliestTime(100, 0, 0));
    assertEquals(Long.MAX_VALUE, Expiry.earliestTime(100, 88, 88));
  }

}
