/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
import org.cache2k.Cache;
import org.cache2k.CacheBuilder;
import org.cache2k.junit.TimingTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 * Tests that need to run separately to test some assumption on timings of current machines.
 * These tests are not meant to run with the normal tests and should only run on a machine
 * without any other load.
 */
@Category(TimingTests.class)
public class BasicTimingTest {

  /**
   * Test the time to generate the toString() output on a cache that has 1M entries.
   * Needs 3 seconds on 2015 hardware.
   */
  @Test(timeout = 4000)
  public void testBigCacheTiming() {
    final int _CACHE_SIZE = 1000000;
    Cache<Integer,Integer> c =
            CacheBuilder.newCache(Integer.class, Integer.class).entryCapacity(_CACHE_SIZE).build();
    for (int i = 0; i < _CACHE_SIZE; i++) {
      c.put(i, i);
    }
    assertNotNull(c.toString());
  }

}
