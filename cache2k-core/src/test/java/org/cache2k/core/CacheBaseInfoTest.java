package org.cache2k.core;

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

import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class CacheBaseInfoTest {

  @Test
  public void testMSecsPerLoadFormatting() {
    double v = 2.3456789;
    assertEquals("2.346", CacheBaseInfo.formatMillisPerLoad(v));
    v = 0.04;
    assertEquals("0.04", CacheBaseInfo.formatMillisPerLoad(v));
  }

  @Test
  public void testHashQuality100_1() {
    assertEquals(0, (1 - Math.exp(-0.1 * 0)) * 100, 0.1);

    assertEquals(100, CacheBaseInfo.hashQuality(100, 1));
  }

  @Test
  public void testHashQuality100_5() {
    assertEquals(100, CacheBaseInfo.hashQuality(100, 5));
  }

  @Test
  public void testHashQuality100_7() {
    assertEquals(98, CacheBaseInfo.hashQuality(100, 7));
  }

  @Test
  public void testHashQuality100_10() {
    assertEquals(95, CacheBaseInfo.hashQuality(100, 10));
  }

  @Test
  public void testHashQuality100_20() {
    assertEquals(85, CacheBaseInfo.hashQuality(100, 20));
  }

  @Test
  public void testHashQuality100_40() {
    assertEquals(69, CacheBaseInfo.hashQuality(100, 40));
  }

  @Test
  public void testHashQuality100_100() {
    assertEquals(36, CacheBaseInfo.hashQuality(100, 100));
  }

  @Test
  public void testHashQuality80_7() {
    assertEquals(78, CacheBaseInfo.hashQuality(80, 7));
  }

  @Test
  public void testHashQuality80_20() {
    assertEquals(65, CacheBaseInfo.hashQuality(80, 20));
  }

  @Test
  public void testHashQuality50_50() {
    assertEquals(11, CacheBaseInfo.hashQuality(50, 50));
  }

}
