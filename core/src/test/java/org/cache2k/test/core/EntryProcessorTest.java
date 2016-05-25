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
import org.cache2k.integration.CacheLoaderException;
import org.cache2k.integration.CacheWriter;
import org.cache2k.integration.ExceptionInformation;
import org.cache2k.integration.ResiliencePolicy;
import org.cache2k.junit.FastTests;
import org.cache2k.processor.CacheEntryProcessingException;
import org.cache2k.processor.CacheEntryProcessor;
import org.cache2k.processor.EntryProcessingResult;
import org.cache2k.processor.MutableCacheEntry;
import org.cache2k.test.util.CacheRule;
import org.cache2k.test.util.IntCacheRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;
import static org.cache2k.test.core.StaticUtil.*;

/**
 * @author Jens Wilke
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

  /**
   * Test that exceptions get propagated, otherwise we cannot use assert inside the processor.
   */
  @Test(expected = CacheEntryProcessingException.class)
  public void exception() {
    Cache<Integer, Integer> c = target.cache();
    c.invoke(KEY, new CacheEntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(final MutableCacheEntry<Integer, Integer> entry, final Object... arguments) throws Exception {
        throw new IllegalStateException("test");
      }
    });
  }

  @Test
  public void invokeAll_exception() {
    Cache<Integer, Integer> c = target.cache();
    Map<Integer, EntryProcessingResult<Object>> _resultMap = c.invokeAll(asSet(KEY), new CacheEntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(final MutableCacheEntry<Integer, Integer> entry, final Object... arguments) throws Exception {
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
    } catch (CacheEntryProcessingException ex ) {
      assertEquals(IllegalStateException.class, ex.getCause().getClass());
    }
  }

  @Test
  public void exists_Empty() {
    Cache<Integer, Integer> c = target.cache();
    c.invoke(KEY, new CacheEntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(final MutableCacheEntry<Integer, Integer> entry, final Object... arguments) throws Exception {
        assertFalse(entry.exists());
        return null;
      }
    });
    assertEquals(0, target.info().getSize());
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
    ww.cache.invoke(KEY, new CacheEntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(final MutableCacheEntry<Integer, Integer> entry, final Object... arguments) throws Exception {
        entry.remove();
        return null;
      }
    });
    assertEquals(1, ww.writer.deleteCalled.get());
  }

  @Test
  public void setValue_Empty_WriterWrite() {
    CacheWithWriter ww = cacheWithWriter();
    ww.cache.invoke(KEY, new CacheEntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(final MutableCacheEntry<Integer, Integer> entry, final Object... arguments) throws Exception {
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
    c.invoke(KEY, new CacheEntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(final MutableCacheEntry<Integer, Integer> entry, final Object... arguments) throws Exception {
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
    c.invoke(KEY, new CacheEntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(final MutableCacheEntry<Integer, Integer> entry, final Object... arguments) throws Exception {
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
