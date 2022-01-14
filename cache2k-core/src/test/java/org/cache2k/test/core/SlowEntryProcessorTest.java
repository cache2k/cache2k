package org.cache2k.test.core;

/*-
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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
import org.cache2k.processor.EntryProcessor;
import org.cache2k.processor.MutableCacheEntry;
import org.cache2k.test.util.IntCacheRule;
import org.cache2k.testing.category.SlowTests;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;

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

  static final Integer KEY = 3;

  /** Provide unique standard cache per method */
  @Rule public IntCacheRule target = new IntCacheRule();

  /**
   * Modification timestamp should be set to the time just before the processor
   * was invoked. Needs some reorganization in the time handling of entry action.
   */
  @Test @Ignore("TODO: SlowEntryProcessorTest.modificationTime_reference_before_invoke")
  public void modificationTime_reference_before_invoke() {
    Cache<Integer, Integer> c = target.cache();
    long t0 = currentTimeMillis();
    c.invoke(KEY, e -> {
      e.setValue((int) (currentTimeMillis() - t0));
      sleep(1);
      return null;
    });
    assertThat((Long) c.invoke(KEY, MutableCacheEntry::getModificationTime))
      .isLessThanOrEqualTo((t0 + c.peek(KEY)));
  }

}
