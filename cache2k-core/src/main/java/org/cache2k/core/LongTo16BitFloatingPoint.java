package org.cache2k.core;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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
 * Converts an unsigned long value to a 16 bit floating point number and back.
 * Used to store a compact representation of the calculated weight in the
 * cache entry.
 *
 * @author Jens Wilke
 */
public class LongTo16BitFloatingPoint {

  private static final int FRACTION_BITS = 10;

  public static int fromLong(long v) {
    if (v <= 0) {
      if (v == 0) {
        return 0;
      }
      throw new IllegalArgumentException("weight must be positive");
    }
    int exp = (64 - FRACTION_BITS) - Long.numberOfLeadingZeros(v);
    if (exp < 0) {
      int r = (int) v;
      return (int) v;
    }
    return (exp << FRACTION_BITS) | ((int) (v >> exp));
  }

  public static long toLong(int v) {
    int exp = (v >> FRACTION_BITS);
    return (v & ((1 << FRACTION_BITS) - 1)) << exp;
  }

}
