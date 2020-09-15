package org.cache2k.core.timing;

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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.core.HeapCache;
import org.cache2k.core.util.ClockDefaultImpl;
import org.cache2k.core.util.InternalClock;
import org.cache2k.core.util.TunableFactory;
import org.cache2k.integration.CacheLoader;
import org.cache2k.testing.category.SlowTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@Category(SlowTests.class)
public class TimingHandlerTestSlow {

  private static final InternalClock CLOCK = ClockDefaultImpl.INSTANCE;

  @Test
  public void purgeCalled() {
    int _SIZE = 1000;
    int _PURGE_INTERVAL = TunableFactory.get(Timing.Tunable.class).purgeInterval;
    Cache<Integer, Integer> c = Cache2kBuilder.of(Integer.class, Integer.class)
      .entryCapacity(_SIZE)
      .expireAfterWrite(5, TimeUnit.MINUTES)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          return key + 123;
        }
      })
      .build();
    for (int i = 0; i < _PURGE_INTERVAL + _SIZE + 125; i++) {
      c.get(i);
    }
    assertEquals(10000, _PURGE_INTERVAL);
    int _timerCacnelCount =
      ((StaticTiming) c.requestInterface(HeapCache.class).getTiming()).timerCancelCount;
    assertTrue("purge called", _timerCacnelCount < _PURGE_INTERVAL);
    c.close();
  }

}
