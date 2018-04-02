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
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.integration.CacheLoader;
import org.cache2k.integration.CacheLoaderException;
import org.cache2k.testing.category.FastTests;
import org.cache2k.test.util.CacheRule;
import org.cache2k.test.util.IntCacheRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

import static org.cache2k.test.core.StaticUtil.*;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class RejectNullValueTest {

  private final static Integer KEY = 1;
  private final static Integer VALUE = 1;

  @ClassRule
  public final static CacheRule<Integer, Integer> staticTarget = new IntCacheRule()
    .config(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        configureRejectNull(b);
      }
    });

  static void configureRejectNull(final Cache2kBuilder<Integer, Integer> b) {
    b.permitNullValues(false);
    b.loader(new CacheLoader<Integer, Integer>() {
      @Override
      public Integer load(final Integer key) throws Exception {
        if (key % 2 == 0) {
          return null;
        }
        return key;
      }
    });
    b.expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
      @Override
      public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
        if (key >= 8) {
        throw new RuntimeException("exception in the expiry policy");
      }
      if (key % 4 == 0 && value == null) {
        return NO_CACHE;
      }
      return ETERNAL;
      }
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
    assertEquals((Integer) 1, v);
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
  public void get_no_cache() {
    Cache<Integer, Integer> c = target.cache();
    c.put(4, 1);
    assertTrue(c.containsKey(4));
    reload(c, 4);
    assertFalse(c.containsKey(4));
  }

  @Test(expected = CacheLoaderException.class)
  public void get_expiryPolicy_exception() {
    Cache<Integer, Integer> c = target.cache();
    c.get(8);
  }

}
