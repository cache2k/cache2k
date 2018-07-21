package org.cache2k.test.core;

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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.expiry.Expiry;
import org.cache2k.integration.CacheLoader;
import org.cache2k.integration.CacheLoaderException;
import org.cache2k.integration.CacheWriter;
import org.cache2k.integration.ExceptionInformation;
import org.cache2k.integration.ResiliencePolicy;
import org.cache2k.processor.EntryProcessingException;
import org.cache2k.processor.EntryProcessingResult;
import org.cache2k.processor.EntryProcessor;
import org.cache2k.processor.MutableCacheEntry;
import org.cache2k.test.util.CacheRule;
import org.cache2k.test.util.IntCacheRule;
import org.cache2k.testing.category.FastTests;
import org.cache2k.testing.category.SlowTests;
import org.hamcrest.Matchers;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.cache2k.test.core.StaticUtil.toIterable;
import static org.junit.Assert.*;

/**
 * Tests for the entry processor, timing related.
 *
 * @author Jens Wilke
 * @see EntryProcessor
 * @see Cache#invoke(Object, EntryProcessor)
 * @see Cache#invokeAll(Iterable, EntryProcessor)
 */
@Category(SlowTests.class)
public class SlowEntryProcessorTest {

  final static Integer KEY = 3;

  /** Provide unique standard cache per method */
  @Rule public IntCacheRule target = new IntCacheRule();

  /**
   * Modification timestamp should be set to the time just before the processor
   * was invoked. Needs some reorganization in the time handling of entry action.
   */
  @Test @Ignore("TODO: SlowEntryProcessorTest.modificationTime_reference_before_invoke")
  public void modificationTime_reference_before_invoke() {
    Cache<Integer, Integer> c = target.cache();
    final long t0 = System.currentTimeMillis();
    c.invoke(KEY, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(final MutableCacheEntry<Integer, Integer> e) throws Exception {
        e.setValue((int) (System.currentTimeMillis() - t0));
        Thread.sleep(1);
        return null;
      }
    });
    assertThat(c.peekEntry(KEY).getLastModification(), Matchers.lessThanOrEqualTo((t0 + c.peek(KEY))));
  }

}
