package org.cache2k.core;

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
import org.cache2k.Cache2kBuilder;
import org.cache2k.processor.CacheEntryProcessor;
import org.cache2k.integration.CacheLoader;
import org.cache2k.processor.MutableCacheEntry;
import org.cache2k.test.core.Statistics;
import org.cache2k.junit.FastTests;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class InvokeTest {

  Cache<Integer, String> cache;
  final Statistics statistics = new Statistics();

  public Statistics statistics() {
    statistics.sample(cache);
    return statistics;
  }

  public void setUp() {
    cache = Cache2kBuilder.of(Integer.class, String.class)
      .name(this.getClass().getName())
      .eternal(true)
      .build();
  }

  @After
  public void tearDown() {
    assertEquals(0, ((InternalCache) cache).getLatestInfo().getLoadsInFlightCnt());
    cache.close();
    cache = null;
  }

  @Test
  public void test_Initial_NoOp() {
    setUp();
    CacheEntryProcessor p = new CacheEntryProcessor() {
      @Override
      public Object process(MutableCacheEntry entry, Object... arguments) throws Exception {
        return null;
      }
    };
    Object _result = cache.invoke(123, p);
    assertNull(_result);
  }

  @Test(expected = NullPointerException.class)
  public void test_Initial_NullKey() {
    setUp();
    CacheEntryProcessor p = new CacheEntryProcessor() {
      @Override
      public Object process(MutableCacheEntry entry, Object... arguments) throws Exception {
        return null;
      }
    };
    Object _result = cache.invoke(null, p);
    fail("never reached");
  }

  @Test
  public void test_Initial_Not_Existing() {
    setUp();
    final AtomicBoolean _reached = new AtomicBoolean(false);
    final int _KEY = 123;
    CacheEntryProcessor p = new CacheEntryProcessor() {
      @Override
      public Object process(MutableCacheEntry _entry, Object... _args) throws Exception {
        assertFalse(_entry.exists());
        assertEquals(0, _entry.getLastModification());
        assertEquals(_KEY, _entry.getKey());
        _reached.set(true);
        return null;
      }
    };
    Object _result = cache.invoke(_KEY, p);
    assertNull(_result);
  }

  @Test
  public void test_Initial_GetYieldsNull() {
    setUp();
    final AtomicBoolean _reached = new AtomicBoolean(false);
    CacheEntryProcessor p = new CacheEntryProcessor() {
      @Override
      public Object process(MutableCacheEntry _entry, Object... _args) throws Exception {
        assertNull(_entry.getValue());
        _reached.set(true);
        return null;
      }
    };
    final int _KEY = 123;
    Object _result = cache.invoke(_KEY, p);
    assertNull(_result);
    assertTrue("no exception during process", _reached.get());
    assertFalse(cache.contains(_KEY));
  }

  @Test
  public void test_Initial_Return() {
    setUp();
    CacheEntryProcessor p = new CacheEntryProcessor() {
      @Override
      public Object process(MutableCacheEntry _entry, Object... _args) throws Exception {
        return "abc";
      }
    };
    Object _result = cache.invoke(123, p);
    assertEquals("abc", _result);
  }

  @Test
  public void test_Initial_Set() {
    setUp();
    CacheEntryProcessor p = new CacheEntryProcessor() {
      @Override
      public Object process(MutableCacheEntry _entry, Object... _args) throws Exception {
        _entry.setValue("dummy");
        return "abc";
      }
    };
    Object _result = cache.invoke(123, p);
    assertEquals("abc", _result);
  }

  @Test
  public void test_Initial_GetSet() {
    setUp();
    statistics().reset();
    CacheEntryProcessor p = new CacheEntryProcessor() {
      @Override
      public Object process(MutableCacheEntry _entry, Object... _args) throws Exception {
        Object o = _entry.getValue();
        _entry.setValue("dummy");
        return "abc";
      }
    };
    Object _result = cache.invoke(123, p);
    assertEquals("abc", _result);
    statistics()
      .missCount.expect(1)
      .readCount.expect(1)
      .putCount.expect(1)
      .expectAllZero();
  }

  @Test
  public void testLoaderInvokedAndCacheMutation() {
    cacheWithLoader();
    cache.invoke(123, new CacheEntryProcessor<Integer, String, Void>() {
      @Override
      public Void process(final MutableCacheEntry<Integer, String> entry, final Object... arguments) throws Exception {
        String v = entry.getValue();
        assertEquals("123", v);
        return null;
      }
    });
    String v = cache.peek(123);
    assertEquals("123", v);
  }

  @Test
  public void testLoaderInvokedAndSetDifferentValue() {
    cacheWithLoader();
    cache.invoke(123, new CacheEntryProcessor<Integer, String, Void>() {
      @Override
      public Void process(final MutableCacheEntry<Integer, String> entry, final Object... arguments) throws Exception {
        String v = entry.getValue();
        assertEquals("123", v);
        entry.setValue("456");
        return null;
      }
    });
    String v = cache.peek(123);
    assertEquals("456", v);
  }

  /**
   * No real remove happens / not counted, since the entry was not there before.
   */
  @Test
  public void testLoaderInvokedAndRemove() {
    cacheWithLoader();
    statistics().reset();
    cache.invoke(123, new CacheEntryProcessor<Integer, String, Void>() {
      @Override
      public Void process(final MutableCacheEntry<Integer, String> entry, final Object... arguments) throws Exception {
        String v = entry.getValue();
        assertEquals("123", v);
        entry.remove();
        return null;
      }
    });
    assertFalse(cache.contains(123));
    String v = cache.peek(123);
    assertNull(v);
    statistics()
      .readCount.expect(2)
      .missCount.expect(2)
      .loadCount.expect(1)
      .removeCount.expect(0)
      .expectAllZero();
  }

  private void cacheWithLoader() {
    cache = Cache2kBuilder.of(Integer.class, String.class)
      .name(this.getClass().getName())
      .eternal(true)
      .loader(new CacheLoader<Integer, String>() {
        @Override
        public String load(final Integer key) throws Exception {
          return key + "";
        }
      })
      .build();
  }

}
