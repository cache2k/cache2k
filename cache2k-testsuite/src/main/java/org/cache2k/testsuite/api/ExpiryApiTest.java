package org.cache2k.testsuite.api;

/*
 * #%L
 * cache2k testsuite on public API
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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

import static org.cache2k.expiry.Expiry.*;
import static org.junit.jupiter.api.Assertions.*;

import org.cache2k.expiry.Expiry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Test functionality in API package.
 *
 * @author Jens Wilke
 */
@Tag("org.cache2k.testing.category.FastTests")
public class ExpiryApiTest {

  @Test
  public void toSharp_PassEternal() {
    assertEquals(ETERNAL, toSharpTime(ETERNAL)) ;
  }

  @Test
  public void toSharp_passNegative() {
    assertEquals(-123, toSharpTime(-123));
  }

  @Test
  public void toSharp_positive() {
    assertEquals(-123, toSharpTime(123));
  }

  @Test
  public void mix_preferRefreshIfPitIsEternal() {
    assertEquals(105, mixTimeSpanAndPointInTime(100, 5, ETERNAL));
  }

  @Test
  public void mix_preferRefreshIfPitFar() {
    assertEquals(105, mixTimeSpanAndPointInTime(100, 5, 200));
  }

  @Test
  public void mix_preferPitIfCloser() {
    assertEquals(104, mixTimeSpanAndPointInTime(100, 5, 104));
  }

  @Test
  public void mix_preferPitEqual() {
    assertEquals(105, mixTimeSpanAndPointInTime(100, 5, 105));
  }

  @Test
  public void mix_preferPitEqual_sharp() {
    assertEquals(-105, mixTimeSpanAndPointInTime(100, 5, -105));
  }

  @Test
  public void mix_preferPitIfCloser_Max() {
    assertEquals(104, mixTimeSpanAndPointInTime(100, Long.MAX_VALUE, 104));
  }

  @Test
  public void mix_shorterTimeSpanIfPitIsNear() {
    assertEquals(101, mixTimeSpanAndPointInTime(100, 5, -106));
  }

  @Test
  public void mix_noSharp_passIfPitIsNear() {
    assertEquals(105, mixTimeSpanAndPointInTime(100, 5, 106));
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
