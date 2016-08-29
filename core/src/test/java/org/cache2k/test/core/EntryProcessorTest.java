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
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.expiry.Expiry;
import org.cache2k.integration.CacheLoader;
import org.cache2k.integration.CacheLoaderException;
import org.cache2k.integration.CacheWriter;
import org.cache2k.integration.ExceptionInformation;
import org.cache2k.integration.ResiliencePolicy;
import org.cache2k.junit.FastTests;
import org.cache2k.processor.EntryProcessingException;
import org.cache2k.processor.EntryProcessor;
import org.cache2k.processor.EntryProcessingResult;
import org.cache2k.processor.MutableCacheEntry;
import org.cache2k.test.util.CacheRule;
import org.cache2k.test.util.IntCacheRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;
import static org.cache2k.test.core.StaticUtil.*;

/**
 * Tests for the entry processor.
 *
 * @author Jens Wilke
 * @see EntryProcessor
 * @see Cache#invoke(Object, EntryProcessor)
 * @see Cache#invokeAll(Iterable, EntryProcessor)
 */
@Category(FastTests.class)
public class EntryProcessorTest {

  final static Integer KEY = 3;

  /** Provide unique standard cache per method */
  @Rule public IntCacheRule target = new IntCacheRule();
  /*
  Cache<Integer, Integer> cache;
  @Before public void setup() { cache = target.cache(); }
  */

  @Test
  public void intial_noop() {
    Cache<Integer, Integer> c = target.cache();
    EntryProcessor p = new EntryProcessor() {
      @Override
      public Object process(MutableCacheEntry entry) throws Exception {
        return null;
      }
    };
    Object _result = c.invoke(123, p);
    assertNull(_result);
  }

  @Test(expected = NullPointerException.class)
  public void initial_NullKey() {
    Cache<Integer, Integer> c = target.cache();
    EntryProcessor p = new EntryProcessor() {
      @Override
      public Object process(MutableCacheEntry entry) throws Exception {
        return null;
      }
    };
    Object _result = c.invoke(null, p);
    fail("never reached");
  }

  /**
   * Test that exceptions get propagated, otherwise we cannot use assert inside the processor.
   */
  @Test(expected = EntryProcessingException.class)
  public void exceptionPropagation() {
    Cache<Integer, Integer> c = target.cache();
    c.invoke(KEY, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(final MutableCacheEntry<Integer, Integer> entry) throws Exception {
        throw new IllegalStateException("test");
      }
    });
  }

  @Test
  public void initial_Not_Existing() {
    Cache<Integer, Integer> c = target.cache();
    final AtomicBoolean _reached = new AtomicBoolean(false);
    final int _KEY = 123;
    EntryProcessor p = new EntryProcessor() {
      @Override
      public Object process(MutableCacheEntry _entry) throws Exception {
        assertFalse(_entry.exists());
        assertEquals(0, _entry.getLastModification());
        assertEquals(_KEY, _entry.getKey());
        _reached.set(true);
        return null;
      }
    };
    Object _result = c.invoke(_KEY, p);
    assertNull(_result);
  }

  @Test
  public void initial_GetYieldsNull() {
    Cache<Integer, Integer> c = target.cache();
    final AtomicBoolean _reached = new AtomicBoolean(false);
    EntryProcessor p = new EntryProcessor() {
      @Override
      public Object process(MutableCacheEntry _entry) throws Exception {
        assertNull(_entry.getValue());
        _reached.set(true);
        return null;
      }
    };
    final int _KEY = 123;
    Object _result = c.invoke(_KEY, p);
    assertNull(_result);
    assertTrue("no exception during process", _reached.get());
    assertFalse(c.containsKey(_KEY));
  }

  @Test
  public void initial_Return() {
    Cache<Integer, Integer> c = target.cache();
    EntryProcessor p = new EntryProcessor() {
      @Override
      public Object process(MutableCacheEntry _entry) throws Exception {
        return "abc";
      }
    };
    Object _result = c.invoke(123, p);
    assertEquals("abc", _result);
  }

  @Test
  public void initial_exists_Empty() {
    Cache<Integer, Integer> c = target.cache();
    c.invoke(KEY, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(final MutableCacheEntry<Integer, Integer> entry) throws Exception {
        assertFalse(entry.exists());
        return null;
      }
    });
    assertEquals(0, target.info().getSize());
  }

  @Test
  public void test_Initial_Set() {
    Cache<Integer, Integer> c = target.cache();
    EntryProcessor p = new EntryProcessor() {
      @Override
      public Object process(MutableCacheEntry _entry) throws Exception {
        _entry.setValue("dummy");
        return "abc";
      }
    };
    Object _result = c.invoke(123, p);
    assertEquals("abc", _result);
  }

  @Test
  public void test_Initial_GetSet() {
    target.statistics();
    Cache<Integer, Integer> c = target.cache();
    EntryProcessor p = new EntryProcessor() {
      @Override
      public Object process(MutableCacheEntry _entry) throws Exception {
        Object o = _entry.getValue();
        assertNull(o);
        _entry.setValue("dummy");
        return "abc";
      }
    };
    Object _result = c.invoke(123, p);
    assertEquals("abc", _result);
    target.statistics()
      .missCount.expect(1)
      .getCount.expect(1)
      .putCount.expect(1)
      .expectAllZero();
  }

  @Test
  public void invokeAll_exception() {
    Cache<Integer, Integer> c = target.cache();
    Map<Integer, EntryProcessingResult<Object>> _resultMap = c.invokeAll(asSet(KEY), new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(final MutableCacheEntry<Integer, Integer> entry) throws Exception {
        throw new IllegalStateException("test");
      }
    });
    assertEquals(1, _resultMap.size());
    EntryProcessingResult<Object>  _result = _resultMap.get(KEY);
    assertNotNull(_result);
    assertNotNull(_result.getException());
    assertEquals(IllegalStateException.class, _result.getException().getClass());
    try {
      _result.getResult();
      fail();
    } catch (EntryProcessingException ex ) {
      assertEquals(IllegalStateException.class, ex.getCause().getClass());
    }
  }

  public static class IdentCountingLoader extends CacheLoader<Integer, Integer> {
    AtomicInteger counter = new AtomicInteger();

    public long getCount() {
      return counter.get();
    }

    @Override
    public Integer load(final Integer key) throws Exception {
      counter.getAndIncrement();
      return key;
    }
  }

  public static class CacheWithLoader {

    Cache<Integer, Integer> cache;
    IdentCountingLoader loader = new IdentCountingLoader();

  }

  CacheWithLoader cacheWithLoader() {
    final CacheWithLoader c = new CacheWithLoader();
    c.cache = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b.loader(c.loader);
      }
    });
    return c;
  }

  @Test
  public void getValue_triggerLoad() {
    CacheWithLoader wl = cacheWithLoader();
    wl.cache.invoke(KEY, new EntryProcessor<Integer, Integer, Void>() {
      @Override
      public Void process(final MutableCacheEntry<Integer, Integer> entry) throws Exception {
        Integer v = entry.getValue();
        assertEquals(KEY, v);
        assertTrue(entry.exists());
        return null;
      }
    });
    assertEquals(1, wl.loader.getCount());
    assertTrue(wl.cache.containsKey(KEY));
    assertEquals(KEY, wl.cache.peek(KEY));
  }

  @Test
  public void getException_triggerLoad() {
    CacheWithLoader wl = cacheWithLoader();
    wl.cache.invoke(KEY, new EntryProcessor<Integer, Integer, Void>() {
      @Override
      public Void process(final MutableCacheEntry<Integer, Integer> entry) throws Exception {
        Throwable t = entry.getException();
        assertNull(t);
        assertTrue(entry.exists());
        Integer v = entry.getValue();
        assertEquals(KEY, v);
        return null;
      }
    });
    assertEquals(1, wl.loader.getCount());
  }

  @Test
  public void getValue_triggerLoad_remove() {
    CacheWithLoader wl = cacheWithLoader();
    wl.cache.invoke(KEY, new EntryProcessor<Integer, Integer, Void>() {
      @Override
      public Void process(final MutableCacheEntry<Integer, Integer> entry) throws Exception {
        Integer v = entry.getValue();
        assertEquals(KEY, v);
        assertTrue(entry.exists());
        entry.remove();
        assertFalse(entry.exists());
        return null;
      }
    });
    assertEquals(1, wl.loader.getCount());
    assertFalse(wl.cache.containsKey(KEY));
  }

  /**
   * No real remove happens / not counted, since the entry was not there before.
   */
  @Test
  public void getValue_triggerLoad_remove_statistics() {
    CacheWithLoader wl = cacheWithLoader();
    target.statistics();
    wl.cache.invoke(123, new EntryProcessor<Integer, Integer, Void>() {
      @Override
      public Void process(final MutableCacheEntry<Integer, Integer> entry) throws Exception {
        Integer v = entry.getValue();
        assertEquals(123, (int) v);
        entry.remove();
        return null;
      }
    });
    assertFalse(wl.cache.containsKey(123));
    Integer v = wl.cache.peek(123);
    assertNull(v);
    target.statistics()
      .getCount.expect(2)
      .missCount.expect(2)
      .loadCount.expect(1)
      .removeCount.expect(0)
      .expectAllZero();
  }

  @Test
  public void getValue_triggerLoad_setValue() {
    CacheWithLoader wl = cacheWithLoader();
    wl.cache.invoke(KEY, new EntryProcessor<Integer, Integer, Void>() {
      @Override
      public Void process(final MutableCacheEntry<Integer, Integer> entry) throws Exception {
        Integer v = entry.getValue();
        assertEquals(KEY, v);
        assertTrue(entry.exists());
        entry.setValue(4711);
        return null;
      }
    });
    assertEquals(1, wl.loader.getCount());
    assertTrue(wl.cache.containsKey(KEY));
    assertEquals(4711, (int) wl.cache.peek(KEY));
  }

  @Test
  public void getValue_triggerLoad_setException() {
    CacheWithLoader wl = cacheWithLoader();
    wl.cache.invoke(KEY, new EntryProcessor<Integer, Integer, Void>() {
      @Override
      public Void process(final MutableCacheEntry<Integer, Integer> entry) throws Exception {
        Integer v = entry.getValue();
        assertEquals(KEY, v);
        assertTrue(entry.exists());
        entry.setException(new NoSuchElementException());
        return null;
      }
    });
    assertEquals(1, wl.loader.getCount());
    assertFalse("exception expires immediately", wl.cache.containsKey(KEY));
  }

  @Test
  public void getValue_triggerLoad_setExpiry() {
    CacheWithLoader wl = cacheWithLoader();
    wl.cache.invoke(KEY, new EntryProcessor<Integer, Integer, Void>() {
      @Override
      public Void process(final MutableCacheEntry<Integer, Integer> entry) throws Exception {
        Integer v = entry.getValue();
        assertEquals(KEY, v);
        assertTrue(entry.exists());
        entry.setExpiry(Expiry.NO_CACHE);
        return null;
      }
    });
    assertEquals(1, wl.loader.getCount());
    assertFalse("expires immediately", wl.cache.containsKey(KEY));
  }

  static class CountingWriter  extends CacheWriter<Integer, Integer> {

    AtomicLong writeCalled = new AtomicLong();
    AtomicLong deleteCalled = new AtomicLong();

    @Override
    public void delete(final Integer key) throws Exception {
      deleteCalled.incrementAndGet();
    }

    @Override
    public void write(final Integer key, final Integer value) throws Exception {
      writeCalled.incrementAndGet();
    }
  }

  public static class CacheWithWriter {

    Cache<Integer, Integer> cache;
    CountingWriter writer = new CountingWriter();

  }

  CacheWithWriter cacheWithWriter() {
    final CacheWithWriter c = new CacheWithWriter();
    c.cache = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b.writer(c.writer);
      }
    });
    return c;
  }

  @Test
  public void remove_Empty_WriterDelete() {
    CacheWithWriter ww = cacheWithWriter();
    ww.cache.invoke(KEY, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(final MutableCacheEntry<Integer, Integer> entry) throws Exception {
        entry.remove();
        return null;
      }
    });
    assertEquals(1, ww.writer.deleteCalled.get());
  }

  @Test
  public void setValue_Empty_WriterWrite() {
    CacheWithWriter ww = cacheWithWriter();
    ww.cache.invoke(KEY, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(final MutableCacheEntry<Integer, Integer> entry) throws Exception {
        entry.setValue(123);
        return null;
      }
    });
    assertEquals(0, ww.writer.deleteCalled.get());
    assertEquals(1, ww.writer.writeCalled.get());
  }

  @Test
  public void setException_propagation() {
    final String _TEXT = "set inside process";
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b.retryInterval(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
      }
    });
    c.invoke(KEY, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(final MutableCacheEntry<Integer, Integer> entry) throws Exception {
        entry.setException(new IllegalStateException(_TEXT));
        return null;
      }
    });
    try {
      c.get(KEY);
      fail();
    } catch (CacheLoaderException ex) {
      assertTrue(ex.getCause().toString().contains(_TEXT));
    }
  }

  @Test
  public void setException_policy_called() {
    final String _TEXT = "set inside process";
    final AtomicLong _retryLoadAfter = new AtomicLong();
    final ResiliencePolicy<Integer, Integer> _policy = new ResiliencePolicy<Integer, Integer>() {
      @Override
      public long suppressExceptionUntil(final Integer key, final ExceptionInformation exceptionInformation, final CacheEntry<Integer, Integer> cachedContent) {
        return 0;
      }

      @Override
      public long retryLoadAfter(final Integer key, final ExceptionInformation exceptionInformation) {
        _retryLoadAfter.incrementAndGet();
        return ETERNAL;
      }
    };
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b.resiliencePolicy(_policy);
      }
    });
    c.invoke(KEY, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(final MutableCacheEntry<Integer, Integer> entry) throws Exception {
        entry.setException(new IllegalStateException(_TEXT));
        return null;
      }
    });
    try {
      c.get(KEY);
      fail();
    } catch (CacheLoaderException ex) {
      assertTrue(ex.getCause().toString().contains(_TEXT));
    }
    assertEquals(1, _retryLoadAfter.get());
  }

}
