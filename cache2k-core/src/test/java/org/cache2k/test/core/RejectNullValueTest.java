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
import org.cache2k.Cache2kBuilder;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.io.CacheLoaderException;
import org.cache2k.testing.category.FastTests;
import org.cache2k.test.util.CacheRule;
import org.cache2k.test.util.IntCacheRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.ExecutionException;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class RejectNullValueTest {

  private static final Integer KEY = 1;
  private static final Integer VALUE = 1;

  @ClassRule
  public static final CacheRule<Integer, Integer> staticTarget = new IntCacheRule()
    .config(RejectNullValueTest::configureRejectNull);

  static void configureRejectNull(Cache2kBuilder<Integer, Integer> b) {
    b.permitNullValues(false);
    b.loader(key -> {
      if (key % 2 == 0) {
        return null;
      }
      return key;
    });
    b.expiryPolicy((key, value, startTime, currentEntry) -> {
      if (key >= 8) {
        throw new RuntimeException("exception in the expiry policy");
      }
      if (key % 4 == 0 && value == null) {
        return ExpiryTimeValues.NOW;
      }
      return ExpiryTimeValues.ETERNAL;
    });
  }

  CacheRule<Integer, Integer> target;

  @Before
  public void setup() {
    target = staticTarget;
  }

  @Test(expected = NullPointerException.class)
  public void put() {
    Cache<Integer, Integer> c = target.cache();
    c.put(KEY, null);
  }

  @Test(expected = NullPointerException.class)
  public void replace() {
    Cache<Integer, Integer> c = target.cache();
    c.put(KEY, VALUE);
    c.replace(KEY, null);
  }

  @Test(expected = NullPointerException.class)
  public void replaceIfEquals() {
    Cache<Integer, Integer> c = target.cache();
    c.put(KEY, VALUE);
    c.replaceIfEquals(KEY, VALUE, null);
  }

  @Test
  public void get_nonNull() {
    Cache<Integer, Integer> c = target.cache();
    Integer v = c.get(1);
    assertThat(v).isEqualTo((Integer) 1);
  }

  /**
   * NullPointerException is propagated, since expiry policy says it would store it.
   */
  @Test(expected = CacheLoaderException.class)
  public void get_null_exception() {
    Cache<Integer, Integer> c = target.cache();
    c.get(2);
  }

  /**
   * Special rule if expiry policy says NO_CACHE, value is removed.
   */
  @Test
  public void get_no_cache() throws ExecutionException, InterruptedException {
    Cache<Integer, Integer> c = target.cache();
    c.put(4, 1);
    assertThat(c.containsKey(4)).isTrue();
    c.reloadAll(asList(4)).get();
    assertThat(c.containsKey(4)).isFalse();
  }

  @Test(expected = CacheLoaderException.class)
  public void get_expiryPolicy_exception() {
    Cache<Integer, Integer> c = target.cache();
    c.get(8);
  }

}
