package org.cache2k.test.core;

/*
 * #%L
 * cache2k core implementation
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
import org.cache2k.CacheEntry;
import org.cache2k.core.api.CoreConfig;
import org.cache2k.core.util.SimulatedClock;
import org.cache2k.event.CacheEntryExpiredListener;
import org.cache2k.expiry.Expiry;
import org.cache2k.io.AdvancedCacheLoader;
import org.cache2k.io.CacheLoader;
import org.cache2k.io.CacheLoaderException;
import org.cache2k.io.CacheWriter;
import org.cache2k.io.LoadExceptionInfo;
import org.cache2k.integration.LoadDetail;
import org.cache2k.integration.Loaders;
import org.cache2k.io.ResiliencePolicy;
import org.cache2k.management.CacheControl;
import org.cache2k.test.core.expiry.ExpiryTest;
import org.cache2k.testing.category.FastTests;
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;
import static org.cache2k.test.core.StaticUtil.*;

/**
 * Tests for the entry processor.
 *
 * @author Jens Wilke
 * @see EntryProcessor
 * @see Cache#invoke(Object, EntryProcessor)
 * @see Cache#invokeAll(Iterable, EntryProcessor)
 */
@SuppressWarnings({"rawtypes", "unchecked", "deprecation"})
@Category(FastTests.class)
public class EntryProcessorTest {

  static final Integer KEY = 3;
  static final Integer VALUE = 7;

  /** Provide unique standard cache per method */
  @Rule public IntCacheRule target = new IntCacheRule();
  /*
  Cache<Integer, Integer> cache;
  @Before public void setup() { cache = target.cache(); }
  */

  public long millis() {
    return System.currentTimeMillis();
  }

  @Test
  public void initial_noop() {
    Cache<Integer, Integer> c = target.cache();
    EntryProcessor p = new EntryProcessor() {
      @Override
      public Object process(MutableCacheEntry e) {
        return null;
      }
    };
    Object result = c.invoke(123, p);
    assertNull(result);
    EntryProcessor<Integer, Integer, String> p2 = new EntryProcessor<Integer, Integer, String>() {
      @Override
      public String process(MutableCacheEntry<Integer, Integer> e) {
        return "hello";
      }
    };
    String result2 = c.invoke(123, p2);
    assertEquals("hello", result2);
  }

  @Test
  public void initial_otherResult() {
    Cache<Integer, Integer> c = target.cache();
    EntryProcessor p = new EntryProcessor() {
      @Override
      public Object process(MutableCacheEntry e) {
        return null;
      }
    };
    Object result = c.invoke(123, p);
    assertNull(result);
  }

  @Test(expected = NullPointerException.class)
  public void initial_NullKey() {
    Cache<Integer, Integer> c = target.cache();
    EntryProcessor p = new EntryProcessor() {
      @Override
      public Object process(MutableCacheEntry e) {
        return null;
      }
    };
    c.invoke(null, p);
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
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        throw new IllegalStateException("test");
      }
    });
  }

  @Test
  public void initial_Not_Existing() {
    Cache<Integer, Integer> c = target.cache();
    final AtomicBoolean reached = new AtomicBoolean(false);
    final int key = 123;
    EntryProcessor p = new EntryProcessor() {
      @Override
      public Object process(MutableCacheEntry e) {
        assertFalse(e.exists());
        assertEquals(0, e.getModificationTime());
        assertEquals(key, e.getKey());
        reached.set(true);
        return null;
      }
    };
    Object result = c.invoke(key, p);
    assertNull(result);
    assertTrue(reached.get());
  }

  @Test
  public void initial_GetYieldsNull() {
    Cache<Integer, Integer> c = target.cache();
    final AtomicBoolean reached = new AtomicBoolean(false);
    EntryProcessor p = new EntryProcessor() {
      @Override
      public Object process(MutableCacheEntry e) {
        assertNull(e.getValue());
        reached.set(true);
        return null;
      }
    };
    final int key = 123;
    Object result = c.invoke(key, p);
    assertNull(result);
    assertTrue("no exception during process", reached.get());
    assertFalse(c.containsKey(key));
  }

  @Test
  public void initial_Return() {
    Cache<Integer, Integer> c = target.cache();
    EntryProcessor p = new EntryProcessor() {
      @Override
      public Object process(MutableCacheEntry e) {
        return "abc";
      }
    };
    Object result = c.invoke(123, p);
    assertEquals("abc", result);
  }

  @Test
  public void initial_exists_Empty() {
    Cache<Integer, Integer> c = target.cache();
    c.invoke(KEY, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        assertFalse(e.exists());
        return null;
      }
    });
    assertEquals(0, target.info().getSize());
  }

  @Test
  public void initial_Set() {
    Cache<Integer, Integer> c = target.cache();
    EntryProcessor p = new EntryProcessor() {
      @Override
      public Object process(MutableCacheEntry e) {
        e.setValue("dummy");
        return "abc";
      }
    };
    Object result = c.invoke(123, p);
    assertEquals("abc", result);
  }

  @Test
  public void test_Initial_GetSet() {
    target.statistics();
    Cache<Integer, Integer> c = target.cache();
    EntryProcessor p = new EntryProcessor() {
      @Override
      public Object process(MutableCacheEntry e) {
        Object o = e.getValue();
        assertNull(o);
        e.setValue("dummy");
        return "abc";
      }
    };
    Object result = c.invoke(123, p);
    assertEquals("abc", result);
    target.statistics()
      .missCount.expect(1)
      .getCount.expect(1)
      .putCount.expect(1)
      .expectAllZero();
  }

  @Test
  public void invokeAll_exception() {
    Cache<Integer, Integer> c = target.cache();
    Map<Integer, EntryProcessingResult<Object>> resultMap =
      c.invokeAll(toIterable(KEY), new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        throw new IllegalStateException("test");
      }
    });
    assertEquals(1, resultMap.size());
    EntryProcessingResult<Object>  result = resultMap.get(KEY);
    assertNotNull(result);
    assertNotNull(result.getException());
    assertEquals(IllegalStateException.class, result.getException().getClass());
    try {
      result.getResult();
      fail();
    } catch (EntryProcessingException ex) {
      assertEquals(IllegalStateException.class, ex.getCause().getClass());
    }
  }

  @Test
  public void nomap_getRefreshTime() {
    Cache<Integer, Integer> c = target.cache();
    final long t0 = millis();
    c.invoke(1, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        assertThat(e.getStartTime(), greaterThanOrEqualTo(t0));
        assertEquals(0, e.getModificationTime());
        return null;
      }
    });
  }

  @Test
  public void getCurrentTime_getRefreshTime_setRefreshTime_setValue() {
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.recordModificationTime(true);
      }
    });
    final long t0 = millis();
    final long early = t0 - 10;
    c.put(1, 1);
    c.invoke(1, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        assertThat(e.getStartTime(), greaterThanOrEqualTo(t0));
        assertThat("refresh time updated by put()", e.getModificationTime(),
          greaterThanOrEqualTo(t0));
        e.setModificationTime(early);
        return null;
      }
    });
    c.invoke(1, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        assertThat("refresh time not updated", e.getModificationTime(), greaterThanOrEqualTo(t0));
        e.setModificationTime(early);
        e.setValue(3);
        return null;
      }
    });
    c.invoke(1, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        assertEquals("was update on setValue", early, e.getModificationTime());
        return null;
      }
    });
  }

  @Test
  public void load_unconditional() {
    CacheWithLoader cwl = cacheWithLoader();
    Cache<Integer, Integer> c = cwl.cache;
    c.invoke(1, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        e.load();
        assertEquals(1, (int) e.getValue());
        return null;
      }
    });
    c.get(2);
    assertEquals(2, cwl.loader.getCount());
    c.invoke(2, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        assertTrue(e.exists());
        e.load();
        assertEquals(2, (int) e.getValue());
        return null;
      }
    });
    assertEquals(3, cwl.loader.getCount());
  }

  @Test
  public void load_unsupported() {
    Cache<Integer, Integer> c = target.cache();
    try {
      c.invoke(1, new EntryProcessor<Integer, Integer, Object>() {
        @Override
        public Object process(MutableCacheEntry<Integer, Integer> e) {
          e.load();
          return null;
        }
      });
      fail("exception expected");
    } catch (EntryProcessingException ex) {
      assertTrue(ex.getCause() instanceof UnsupportedOperationException);
    }
  }

  @Test
  public void load_getRefreshTime() {
    CacheWithLoader cwl = cacheWithLoader();
    Cache<Integer, Integer> c = cwl.cache;
    final long t0 = millis();
    c.get(1);
    c.invoke(1, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        assertThat(e.getStartTime(), greaterThanOrEqualTo(t0));
        assertThat("refresh time updated by put()",
          e.getModificationTime(), greaterThanOrEqualTo(t0));
        return null;
      }
    });
  }

  @Test
  public void getValue_load() {
    CacheWithLoader cwl = cacheWithLoader();
    Cache<Integer, Integer> c = cwl.cache;
    c.put(1, 1);
    assertThatCode(() -> {
      c.invoke(1, e -> {
        e.getValue();
        e.load();
        return null;
      });
    }).isInstanceOf(EntryProcessingException.class)
      .getCause()
      .isInstanceOf(IllegalStateException.class);
  }

  /**
   * Corner case. The load is triggered since remove resets the state, which is an optimization.
   * After the load, remove() will avoid the mutation again, so nothing is inserted and
   * the final value is null.
   */
  @Test(expected = EntryProcessingException.class)
  public void exists_set_remove_get_yields_exception() {
    CacheWithLoader cwl = cacheWithLoader();
    Cache<Integer, Integer> c = cwl.cache;
    assertEquals(0, cwl.loader.getCount());
    Integer result =
      c.invoke(1, e -> {
        e.exists();
        e.setValue(4711);
        e.remove();
        return e.getValue() + 1801;
      });
    fail("exception expected");
  }

  @Test
  public void load_changeRefreshTimeInLoader() {
    final long probeTime = 4711;
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.recordModificationTime(true)
         .wrappingLoader(new AdvancedCacheLoader<Integer, LoadDetail<Integer>>() {
          @Override
          public LoadDetail<Integer> load(Integer key, long startTime,
                                          CacheEntry<Integer,
                                            LoadDetail<Integer>> currentEntry) {
            return Loaders.wrapRefreshedTime(key, probeTime);
          }
        });
      }
    });
    c.get(1);
    c.invoke(1, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        assertEquals(probeTime, e.getModificationTime());
        return null;
      }
    });
  }

  @Test
  public void load_changeRefreshTimeInLoader_triggeredViaEntryProcessor() {
    final long probeTime = 4711;
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.recordModificationTime(true)
         .wrappingLoader(new AdvancedCacheLoader<Integer, LoadDetail<Integer>>() {
          @Override
          public LoadDetail<Integer> load(Integer key, long startTime,
                                          CacheEntry<Integer,
                                            LoadDetail<Integer>> currentEntry) {
            return Loaders.wrapRefreshedTime(key, probeTime);
          }
        });
      }
    });
    c.invoke(1, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        e.getValue();
        assertEquals(probeTime, e.getModificationTime());
        return null;
      }
    });
  }

  @Test
  public void load_changeRefreshTimeInLoaderNoRecord() {
    final long probeTime = 4711;
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.wrappingLoader(new AdvancedCacheLoader<Integer, LoadDetail<Integer>>() {
            @Override
            public LoadDetail<Integer> load(Integer key, long startTime,
                                            CacheEntry<Integer,
                                              LoadDetail<Integer>> currentEntry) {
              return Loaders.wrapRefreshedTime(key, probeTime);
            }
          });
      }
    });
    c.get(1);
    c.invoke(1, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        assertEquals(0, e.getModificationTime());
        return null;
      }
    });
  }

  @Test
  public void initial_getRefreshTime() {
    Cache<Integer, Integer> c = target.cache();
    c.invoke(1, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        assertEquals(0L, e.getModificationTime());
        return null;
      }
    });
  }

  public static class IdentCountingLoader implements CacheLoader<Integer, Integer> {

    public static final int KEY_YIELDING_PERMANENT_EXCEPTION = 0xcafebabe;
    AtomicInteger counter = new AtomicInteger();

    public long getCount() {
      return counter.get();
    }

    @Override
    public Integer load(Integer key) throws Exception {
      if (key == KEY_YIELDING_PERMANENT_EXCEPTION) {
        throw new Exception("load exception on " + KEY_YIELDING_PERMANENT_EXCEPTION);
      }
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
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader(c.loader);
        b.recordModificationTime(true);
      }
    });
    return c;
  }

  /**
   * Set expiry which keeps exceptions
   */
  CacheWithLoader cacheWithLoaderKeepExceptions() {
    final CacheWithLoader c = new CacheWithLoader();
    c.cache = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.loader(c.loader);
        b.resiliencePolicy(new ExpiryTest.EnableExceptionCaching());
        b.expireAfterWrite(999, TimeUnit.DAYS);
      }
    });
    return c;
  }

  @Test
  public void getValue_triggerLoad() {
    CacheWithLoader wl = cacheWithLoader();
    wl.cache.invoke(KEY, new EntryProcessor<Integer, Integer, Void>() {
      @Override
      public Void process(MutableCacheEntry<Integer, Integer> e) {
        Integer v = e.getValue();
        assertEquals(KEY, v);
        assertFalse(e.exists());
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
      public Void process(MutableCacheEntry<Integer, Integer> e) {
        Throwable t = e.getException();
        assertNull(t);
        assertFalse(e.exists());
        Integer v = e.getValue();
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
      public Void process(MutableCacheEntry<Integer, Integer> e) {
        Integer v = e.getValue();
        assertEquals(KEY, v);
        assertFalse(e.exists());
        e.remove();
        assertFalse(e.exists());
        return null;
      }
    });
    assertEquals(1, wl.loader.getCount());
    assertFalse(wl.cache.containsKey(KEY));
  }

  @Test
  public void put_setValue_remove() {
    CacheWithLoader wl = cacheWithLoader();
    wl.cache.put(KEY, KEY);
    wl.cache.invoke(KEY, new EntryProcessor<Integer, Integer, Void>() {
      @Override
      public Void process(MutableCacheEntry<Integer, Integer> e) {
        e.setValue(VALUE);
        e.remove();
        return null;
      }
    });
    assertFalse("removed", wl.cache.containsKey(KEY));
  }

  @Test
  public void setValue_remove() {
    CacheWithLoader wl = cacheWithLoader();
    wl.cache.invoke(KEY, new EntryProcessor<Integer, Integer, Void>() {
      @Override
      public Void process(MutableCacheEntry<Integer, Integer> e) {
        e.setValue(VALUE);
        e.remove();
        return null;
      }
    });
    assertFalse("removed", wl.cache.containsKey(KEY));
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
      public Void process(MutableCacheEntry<Integer, Integer> e) {
        Integer v = e.getValue();
        assertEquals(123, (int) v);
        e.remove();
        return null;
      }
    });
    target.statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .loadCount.expect(1)
      .expectAllZero();
    assertFalse(wl.cache.containsKey(123));
    Integer v = wl.cache.peek(123);
    assertNull(v);
    target.statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .expectAllZero();
  }

  /**
   * Test that load count only counts successful loads.
   */
  @Test
  public void getValue_triggerLoad_exception_count_successful_load() {
    CacheWithLoader wl = cacheWithLoader();
    target.statistics();
    wl.cache.invoke(123, new EntryProcessor<Integer, Integer, Void>() {
      @Override
      public Void process(MutableCacheEntry<Integer, Integer> e) {
        Integer v = e.getValue();
        assertEquals(123, (int) v);
        e.remove();
        return null;
      }
    });
    target.statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .loadCount.expect(1)
      .expectAllZero();
    boolean exceptionThrown = false;
    try {
      wl.cache.invoke(IdentCountingLoader.KEY_YIELDING_PERMANENT_EXCEPTION,
        new EntryProcessor<Integer, Integer, Void>() {
        @Override
        public Void process(MutableCacheEntry<Integer, Integer> e) {
          e.getValue();
          return null;
        }
      });
    } catch (EntryProcessingException ex) {
      exceptionThrown = true;
    }
    assertTrue(exceptionThrown);
    target.statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .loadCount.expect(0)
      .expectAllZero();
  }

  @Test
  public void expires_before_mutation() {
    final long expireAfterWriteMillis = 100;
    AtomicInteger listenerCallCount = new AtomicInteger();
    Cache<Integer, Integer> c =
      target.cache(new CacheRule.Specialization<Integer, Integer>() {
        @Override
        public void extend(Cache2kBuilder<Integer, Integer> b) {
          b .section(CoreConfig.class, b2 -> b2
              .timerReference(new SimulatedClock())
            )
            .sharpExpiry(true)
            .expiryPolicy((key, value, loadTime, oldEntry) -> loadTime + expireAfterWriteMillis)
            .addListener((CacheEntryExpiredListener<Integer, Integer>) (cache, entry) -> {
              listenerCallCount.incrementAndGet();
            });
        }
      });
    c.put(123, 4711);
    AtomicInteger callCount4 = new AtomicInteger();
    AtomicInteger callCount3 = new AtomicInteger();
    AtomicInteger callCount1 = new AtomicInteger();
    c.invoke(123, new EntryProcessor<Integer, Integer, Void>() {
      @Override
      public Void process(MutableCacheEntry<Integer, Integer> e) {
        int count = callCount4.incrementAndGet();
        if (count == 1) {
          sleep(expireAfterWriteMillis * 3);
        }
        assertFalse("entry is expired, not existing", e.exists());
        callCount3.incrementAndGet();
        e.setValue(123);
        callCount1.incrementAndGet();
        assertEquals("listener called before mutation lock",
          1, listenerCallCount.get());
        return null;
      }
    });
    assertEquals(4, callCount4.get());
    assertEquals(3, callCount3.get());
    assertEquals(1, callCount1.get());
  }

  private void sleep(long millis) {
    try {
      target.getClock().sleep(millis);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Is entry lock given up after an exception?
   */
  @Test
  public void exception_after_mutation() {
    Cache<Integer, Integer> c = target.cache();
    target.statistics();
    try {
      c.invoke(123, (EntryProcessor<Integer, Integer, Void>) e -> {
        e.setValue(e.getValue());
        throw new RuntimeException("exception in entry processor");
        });
      fail("expect exception");
    } catch (EntryProcessingException expected) { }
    target.statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .loadCount.expect(0)
      .expectAllZero();
    c.put(123, 123);
  }

  @Test
  public void remove_after_mutation() {
    Cache<Integer, Integer> c = target.cache();
    target.statistics();
    c.invoke(123, (EntryProcessor<Integer, Integer, Void>) e -> {
      e.setValue(e.getValue());
      e.remove();
      return null;
    });
    target.statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .loadCount.expect(0)
      .expectAllZero();
    assertNull(c.get(123));
    c.put(123, 123);
  }

  @Test
  public void getValue_triggerLoad_setValue() {
    CacheWithLoader wl = cacheWithLoader();
    wl.cache.invoke(KEY, new EntryProcessor<Integer, Integer, Void>() {
      @Override
      public Void process(MutableCacheEntry<Integer, Integer> e) {
        Integer v = e.getValue();
        assertEquals(KEY, v);
        assertFalse(e.exists());
        e.setValue(4711);
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
      public Void process(MutableCacheEntry<Integer, Integer> e) {
        Integer v = e.getValue();
        assertEquals(KEY, v);
        assertFalse(e.exists());
        e.setException(new NoSuchElementException());
        return null;
      }
    });
    assertEquals(1, wl.loader.getCount());
    assertFalse("exception expires immediately", wl.cache.containsKey(KEY));
  }

  @Test(expected = EntryProcessingException.class)
  public void setException_getException_getValue_exception() {
    CacheWithLoader wl = cacheWithLoader();
    wl.cache.invoke(KEY, new EntryProcessor<Integer, Integer, Void>() {
      @Override
      public Void process(MutableCacheEntry<Integer, Integer> e) {
        e.setException(new NoSuchElementException());
        assertNull(e.getException());
        assertNull(e.getValue());
        return null;
      }
    });
    fail("excepton expected");
  }

  /**
   * An exception within the entry processor aborts the processing and the
   * cache content is not altered.
   */
  @Test
  public void setValue_throwException() {
    CacheWithLoader wl = cacheWithLoader();
    try {
      wl.cache.invoke(KEY, new EntryProcessor<Integer, Integer, Void>() {
        @Override
        public Void process(MutableCacheEntry<Integer, Integer> e) {
          e.setValue(VALUE);
          throw new RuntimeException("terminate with exception");
        }
      });
      fail("exception expected");
    } catch (EntryProcessingException _expected) {
    }
    assertFalse(wl.cache.containsKey(KEY));
    wl.cache.put(KEY, VALUE);
  }

  @Test
  public void setException_keep_exception() {
    CacheWithLoader wl = cacheWithLoaderKeepExceptions();
    wl.cache.invoke(KEY, new EntryProcessor<Integer, Integer, Void>() {
      @Override
      public Void process(MutableCacheEntry<Integer, Integer> e) {
        e.setException(new NoSuchElementException());
        return null;
      }
    });
    try {
      wl.cache.get(KEY);
      fail("exception expected");
    } catch (CacheLoaderException ex) {
    }
  }

  @Test
  public void getValue_triggerLoad_setExpiry() {
    CacheWithLoader wl = cacheWithLoader();
    wl.cache.invoke(KEY, new EntryProcessor<Integer, Integer, Void>() {
      @Override
      public Void process(MutableCacheEntry<Integer, Integer> e) {
        Integer v = e.getValue();
        assertEquals(KEY, v);
        assertFalse(e.exists());
        e.setExpiryTime(Expiry.NOW);
        return null;
      }
    });
    assertEquals(1, wl.loader.getCount());
    assertFalse("expires immediately", wl.cache.containsKey(KEY));
  }

  static class CountingWriter implements CacheWriter<Integer, Integer> {

    AtomicLong writeCalled = new AtomicLong();
    AtomicLong deleteCalled = new AtomicLong();

    @Override
    public void delete(Integer key) {
      deleteCalled.incrementAndGet();
    }

    @Override
    public void write(Integer key, Integer value) {
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
      public void extend(Cache2kBuilder<Integer, Integer> b) {
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
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        e.remove();
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
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        e.setValue(123);
        return null;
      }
    });
    assertEquals(0, ww.writer.deleteCalled.get());
    assertEquals(1, ww.writer.writeCalled.get());
  }

  @Test
  public void setException_propagation() {
    final String text = "set inside process";
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
          @Override
          public long suppressExceptionUntil(Integer key,
                                             LoadExceptionInfo<Integer> loadExceptionInfo,
                                             CacheEntry<Integer, Integer> cachedContent) {
            return Long.MAX_VALUE;
          }

          @Override
          public long retryLoadAfter(Integer key, LoadExceptionInfo<Integer> loadExceptionInfo) {
            return Long.MAX_VALUE;
          }
        });
      }
    });
    c.invoke(KEY, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        e.setException(new IllegalStateException(text));
        return null;
      }
    });
    try {
      c.get(KEY);
      fail();
    } catch (CacheLoaderException ex) {
      assertTrue(ex.getCause().toString().contains(text));
    }
  }

  @Test
  public void setException_policy_called() {
    final String text = "set inside process";
    final AtomicLong retryLoadAfter = new AtomicLong();
    final ResiliencePolicy<Integer, Integer> policy = new ResiliencePolicy<Integer, Integer>() {
      @Override
      public long suppressExceptionUntil(Integer key,
                                         LoadExceptionInfo<Integer> exceptionInformation,
                                         CacheEntry<Integer, Integer> cachedContent) {
        return 0;
      }

      @Override
      public long retryLoadAfter(Integer key, LoadExceptionInfo<Integer> exceptionInformation) {
        retryLoadAfter.incrementAndGet();
        return ETERNAL;
      }
    };
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.resiliencePolicy(policy);
      }
    });
    c.invoke(KEY, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        e.setException(new IllegalStateException(text));
        return null;
      }
    });
    try {
      c.get(KEY);
      fail();
    } catch (CacheLoaderException ex) {
      assertTrue(ex.getCause().toString().contains(text));
    }
    assertEquals(1, retryLoadAfter.get());
  }

  /**
   * When the entry is read and modified, we need three restarts.
   */
  @Test
  public void read_write_ep_executed_once_after_mutation_lock() {
    Cache<Integer, Integer> c = target.cache();
    c.put(1, 0);
    final AtomicInteger count0 = new AtomicInteger();
    final AtomicInteger count1 = new AtomicInteger();
    c.invoke(1, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        count0.incrementAndGet();
        int val = e.getValue();
        e.setValue(val + 1);
        count1.incrementAndGet();
        return null;
      }
    });
    assertEquals(1, (int) c.get(1));
    assertEquals(
      "passed 4 times: initial/start, after installation read/examine, " +
               "after mutation lock/examine again, mutation",
      4, count0.get());
    assertEquals(
      "passed 1 times: after installation read, after mutation lock",
      1, count1.get());
  }

  /**
   * Only a write occurs, the entry state is not read. We expect no restart to happen.
   */
  @Test
  public void write_ep_executed_once() {
    Cache<Integer, Integer> c = target.cache();
    final AtomicInteger count0 = new AtomicInteger();
    final AtomicInteger count1 = new AtomicInteger();
    c.invoke(1, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        count0.incrementAndGet();
        e.setValue(123);
        count1.incrementAndGet();
        return null;
      }
    });
    assertEquals(123, (int) c.peek(1));
    assertEquals(
      "passed 1 times: initial/start",
      1, count0.get());
    assertEquals(
      "passed 1 times: after installation read, after mutation lock",
      1, count1.get());
  }

  @Test
  public void lock() {
    Cache<Integer, Integer> c = target.cache();
    int key = 123;
    c.invoke(key, entry -> entry.lock());
    assertFalse(c.containsKey(key));
    assertEquals(0, CacheControl.of(c).getSize());
    c.invoke(key, entry -> {
      entry.exists();
      entry.lock();
      return null;
    });
    assertFalse(c.containsKey(key));
    assertEquals(0, CacheControl.of(c).getSize());
    c.invoke(key, entry -> {
      entry.setValue(4711);
      entry.exists();
      entry.lock();
      return null;
    });
    assertTrue(c.containsKey(key));
    assertEquals(1, CacheControl.of(c).getSize());
    c.invoke(key, entry -> {
      entry.lock();
      entry.getValue();
      entry.setValue(4711);
      return null;
    });
  }

  @Test
  public void getExceptionInfo() {
    CacheWithLoader cl = cacheWithLoader();
    LoadExceptionInfo<Integer> info =
    cl.cache.invoke(IdentCountingLoader.KEY_YIELDING_PERMANENT_EXCEPTION,
      entry -> {
        entry.load();
        return entry.getExceptionInfo();
      });
    assertNotNull(info);
    assertEquals("exception not cached", 0, info.getUntil());
  }

}
