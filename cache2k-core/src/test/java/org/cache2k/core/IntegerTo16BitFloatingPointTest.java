package org.cache2k.core;

/*-
 * #%L
 * cache2k core implementation
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

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Jens Wilke
 */
public class IntegerTo16BitFloatingPointTest {

  @Test
  public void testRandom() {
    Random rnd = new Random(1802);
    long l1 = 0;
    int c1 = 0;
    for (int i = 0; i < 1000; i++) {
      int l2 = rnd.nextInt() & Integer.MAX_VALUE;
      int c2 = IntegerTo16BitFloatingPoint.compress(l2);
      check(l1, c1, l2, c2);
      l1 = l2;
      c1 = c2;
    }
  }

  void check(long l1, int c1, long l2, int c2) {
    assertTrue(!(l1 > l2) || (c1 >= c2));
    assertTrue(!(l1 < l2) || (c1 <= c2));
  }

  @Test
  public void testRandom2() {
    Random rnd = new Random(1802);
    int l1 = rnd.nextInt() & 0x7fffffff;
    int c1 = IntegerTo16BitFloatingPoint.compress(l1);
    for (int i = 0; i < 1000; i++) {
      int l2 = rnd.nextInt() & 0x7fffffff;
      int c2 = IntegerTo16BitFloatingPoint.compress(l2);
      check(l1, c1, l2, c2);
      l1 = l2;
      c1 = c2;
    }
  }

  void checkExp0(int v) {
    assertEquals(v, IntegerTo16BitFloatingPoint.compress(v));
    assertEquals(v, IntegerTo16BitFloatingPoint.expand(v));
  }

  @Test
  public void testExp0() {
    int[] ia = new int[]{0, 1, 2, 0xff, (1 << 10) - 1};
    for (int i : ia) {
      checkExp0(i);
    }
  }

  void checkPair(long v1, long v2) {
    assertEquals(v2,
      IntegerTo16BitFloatingPoint.expand(IntegerTo16BitFloatingPoint.compress((int) v1)));
  }

  @Test
  public void testPairs() {
    int[] ia = new int[]{
      0, 0,
      2, 2,
      (1 << 10) - 1, (1 << 10) - 1,
      1 << 10, 1 << 10,
      (1 << 10) + 1, 1 << 10,

      (1 << 11) - 1, (1 << 11) - 1 & (~0x01),
      1 << 11, 1 << 11,
      (1 << 11) + 1, 1 << 11,

      (1 << 12) - 1, (1 << 12) - 1 & (~0x03),
      1 << 12, 1 << 12,
      (1 << 12) + 1, 1 << 12,

    };
    for (int i = 0; i < ia.length; i += 2) {
      checkPair(ia[i], ia[i + 1]);
    }
  }

  @Test
  public void test1() {
    assertEquals(1, IntegerTo16BitFloatingPoint.compress(1));
    assertEquals(2, IntegerTo16BitFloatingPoint.compress(2));
    assertEquals(3584, IntegerTo16BitFloatingPoint.compress(4096));
  }

}
