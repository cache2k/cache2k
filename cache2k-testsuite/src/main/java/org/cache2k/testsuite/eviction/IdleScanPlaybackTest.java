package org.cache2k.testsuite.eviction;

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

import org.cache2k.testing.category.SlowTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;

/**
 * Playback test for idle scanner. Check that results are stable.
 *
 * @author Jens Wilke
 */
@Category(SlowTests.class)
public class IdleScanPlaybackTest {

  @Test
  public void testUnbounded46() {
    int[] trace = Trace.WEBLOG424_NOROBOTS.get();
    TimeTracePlaybackTest.PlaybackResult res =
      TimeTracePlaybackTest.runWithCache2k(Long.MAX_VALUE, 46 * 60, trace);
    assertEquals(47108, res.hitCount);
    assertEquals(3483, res.maxSize);
    assertEquals(1373, res.getAverageSize());
  }

  @Test
  public void testCap1000() {
    int[] trace = Trace.WEBLOG424_NOROBOTS.get();
    TimeTracePlaybackTest.PlaybackResult res =
      TimeTracePlaybackTest.runWithCache2k(1000, 46 * 60, trace);
    assertEquals(46899, res.hitCount);
    assertEquals(1000, res.maxSize);
    assertEquals(954, res.getAverageSize());
  }

}
