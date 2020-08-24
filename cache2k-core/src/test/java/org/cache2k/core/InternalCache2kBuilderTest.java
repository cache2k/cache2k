package org.cache2k.core;

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

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
public class InternalCache2kBuilderTest {

  @Test
  public void determineSegmentCount_strictEviction() throws Exception {
    int _segs = InternalCache2kBuilder.determineSegmentCount(true, 12, true, 1000000, 0, 32);
    assertEquals(1, _segs);
  }

  @Test
  public void determineSegmentCount_regularConcurrency() throws Exception {
    int _segs = InternalCache2kBuilder.determineSegmentCount(false, 12, false, 1000000, 0, 0);
    assertEquals(2, _segs);
  }

  @Test
  public void determineSegmentCount_boostConcurrency() throws Exception {
    int _segs = InternalCache2kBuilder.determineSegmentCount(false, 12, true, 1000000, 0, 0);
    assertEquals(16, _segs);
  }

  @Test
  public void determineSegmentCount_noSegmentationBelow1000Entries() throws Exception {
    int _segs = InternalCache2kBuilder.determineSegmentCount(false, 12, true, 999, 0, 0);
    assertEquals(1, _segs);
  }

  @Test
  public void determineSegmentCount_override12() throws Exception {
    int _segs = InternalCache2kBuilder.determineSegmentCount(false, 12, true, 1000000, 0, 12);
    assertEquals(16, _segs);
  }

  @Test
  public void determineSegmentCount_override1() throws Exception {
    int _segs = InternalCache2kBuilder.determineSegmentCount(false, 12, true, 1000000, 0, 1);
    assertEquals(1, _segs);
  }

  @Test
  public void determineSegmentCount_override16() throws Exception {
    int _segs = InternalCache2kBuilder.determineSegmentCount(false, 12, true, 1000000, 0, 16);
    assertEquals(16, _segs);
  }

  @Test
  public void determineSegmentCount_override17() throws Exception {
    int _segs = InternalCache2kBuilder.determineSegmentCount(false, 12, true, 1000000, 0, 17);
    assertEquals(32, _segs);
  }

  @Test
  public void determineSegmentCount_override32() throws Exception {
    int _segs = InternalCache2kBuilder.determineSegmentCount(false, 12, true, 1000000, 0, 32);
    assertEquals(32, _segs);
  }

}
