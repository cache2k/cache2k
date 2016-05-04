package org.cache2k.test.core;

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

import org.cache2k.Cache;
import org.cache2k.core.EntryAction;
import org.cache2k.junit.FastTests;
import org.cache2k.processor.CacheEntryProcessor;
import org.cache2k.processor.MutableCacheEntry;
import org.cache2k.test.util.IntCacheRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class EntryProcessorTest {

  final static Integer KEY = 3;

  /** Provide unique standard cache per method */
  @Rule public IntCacheRule target = new IntCacheRule();
  Cache<Integer, Integer> cache;
  @Before public void setup() { cache = target.cache(); }

  /**
   * Test that exceptions get propagated, otherwise we cannot use assert inside the processor.
   */
  @Test(expected = EntryAction.ProcessingFailureException.class)
  public void exception() {
    cache.invoke(KEY, new CacheEntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(final MutableCacheEntry<Integer, Integer> entry, final Object... arguments) throws Exception {
        throw new IllegalStateException("test");
      }
    });
  }

  @Test
  public void exists_Empty() {
    cache.invoke(KEY, new CacheEntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(final MutableCacheEntry<Integer, Integer> entry, final Object... arguments) throws Exception {
        assertFalse(entry.exists());
        return null;
      }
    });
    assertEquals(0, target.info().getSize());
  }

}
