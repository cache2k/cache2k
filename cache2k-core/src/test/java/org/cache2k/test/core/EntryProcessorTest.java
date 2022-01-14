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
import org.cache2k.CacheEntry;
import org.cache2k.testing.SimulatedClock;
import org.cache2k.event.CacheEntryExpiredListener;
import org.cache2k.io.CacheLoader;
import org.cache2k.io.CacheLoaderException;
import org.cache2k.io.CacheWriter;
import org.cache2k.io.LoadExceptionInfo;
import org.cache2k.io.ResiliencePolicy;
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

import static java.lang.Long.MAX_VALUE;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.cache2k.expiry.ExpiryTimeValues.NOW;
import static org.cache2k.operation.CacheControl.of;
import static org.cache2k.test.core.EntryProcessorTest.IdentCountingLoader.KEY_YIELDING_PERMANENT_EXCEPTION;

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
    EntryProcessor p = e -> null;
    Object result = c.invoke(123, p);
    assertThat(result).isNull();
    EntryProcessor<Integer, Integer, String> p2 = e -> "hello";
    String result2 = c.invoke(123, p2);
    assertThat(result2).isEqualTo("hello");
  }

  @Test
  public void initial_otherResult() {
    Cache<Integer, Integer> c = target.cache();
    EntryProcessor p = e -> null;
    Object result = c.invoke(123, p);
    assertThat(result).isNull();
  }

  @Test(expected = NullPointerException.class)
  public void initial_NullKey() {
    Cache<Integer, Integer> c = target.cache();
    EntryProcessor p = e -> null;
    c.invoke(null, p);
    fail("never reached");
  }

  /**
   * Test that exceptions get propagated, otherwise we cannot use assert inside the processor.
   */
  @Test(expected = EntryProcessingException.class)
  public void exceptionPropagation() {
    Cache<Integer, Integer> c = target.cache();
    c.invoke(KEY, e -> {
      throw new IllegalStateException("test");
    });
  }

  @Test
  public void initial_Not_Existing() {
    Cache<Integer, Integer> c = target.cache();
    AtomicBoolean reached = new AtomicBoolean(false);
    final int key = 123;
    EntryProcessor p = e -> {
      assertThat(e.exists()).isFalse();
      assertThat(e.getModificationTime()).isEqualTo(0);
      assertThat(e.getKey()).isEqualTo(key);
      reached.set(true);
      return null;
    };
    Object result = c.invoke(key, p);
    assertThat(result).isNull();
    assertThat(reached.get()).isTrue();
  }

  @Test
  public void initial_GetYieldsNull() {
    Cache<Integer, Integer> c = target.cache();
    AtomicBoolean reached = new AtomicBoolean(false);
    EntryProcessor p = e -> {
      assertThat(e.getValue()).isNull();
      reached.set(true);
      return null;
    };
    final int key = 123;
    Object result = c.invoke(key, p);
    assertThat(result).isNull();
    assertThat(reached.get())
      .as("no exception during process")
      .isTrue();
    assertThat(c.containsKey(key)).isFalse();
  }

  @Test
  public void initial_Return() {
    Cache<Integer, Integer> c = target.cache();
    EntryProcessor p = e -> "abc";
    Object result = c.invoke(123, p);
    assertThat(result).isEqualTo("abc");
  }

  @Test
  public void initial_exists_Empty() {
    Cache<Integer, Integer> c = target.cache();
    c.invoke(KEY, e -> {
      assertThat(e.exists()).isFalse();
      return null;
    });
    assertThat(target.info().getSize()).isEqualTo(0);
  }

  @Test
  public void initial_Set() {
    Cache<Integer, Integer> c = target.cache();
    EntryProcessor p = e -> {
      e.setValue("dummy");
      return "abc";
    };
    Object result = c.invoke(123, p);
    assertThat(result).isEqualTo("abc");
  }

  @Test
  public void test_Initial_GetSet() {
    target.statistics();
    Cache<Integer, Integer> c = target.cache();
    EntryProcessor p = e -> {
      Object o = e.getValue();
      assertThat(o).isNull();
      e.setValue("dummy");
      return "abc";
    };
    Object result = c.invoke(123, p);
    assertThat(result).isEqualTo("abc");
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
      c.invokeAll(asList(KEY), e -> {
        throw new IllegalStateException("test");
      });
    assertThat(resultMap.size()).isEqualTo(1);
    EntryProcessingResult<Object>  result = resultMap.get(KEY);
    assertThat(result).isNotNull();
    assertThat(result.getException()).isNotNull();
    assertThat(result.getException().getClass()).isEqualTo(IllegalStateException.class);
    try {
      result.getResult();
      fail("exception expected");
    } catch (EntryProcessingException ex) {
      assertThat(ex.getCause().getClass()).isEqualTo(IllegalStateException.class);
    }
  }

  @Test
  public void nomap_getRefreshTime() {
    Cache<Integer, Integer> c = target.cache();
    long t0 = millis();
    c.invoke(1, e -> {
      assertThat(e.getStartTime()).isGreaterThanOrEqualTo(t0);
      assertThat(e.getModificationTime()).isEqualTo(0);
      return null;
    });
  }

  @Test
  public void getCurrentTime_getRefreshTime_setRefreshTime_setValue() {
    Cache<Integer, Integer> c = target.cache(b -> b.recordModificationTime(true));
    long t0 = millis();
    long early = t0 - 10;
    c.put(1, 1);
    c.invoke(1, e -> {
      assertThat(e.getStartTime()).isGreaterThanOrEqualTo(t0);
      assertThat(e.getModificationTime())
        .as("refresh time updated by put()")
        .isGreaterThanOrEqualTo(t0);
      e.setModificationTime(early);
      return null;
    });
    c.invoke(1, e -> {
      assertThat(e.getModificationTime())
        .as("refresh time not updated")
        .isGreaterThanOrEqualTo(t0);
      e.setModificationTime(early);
      e.setValue(3);
      return null;
    });
    c.invoke(1, e -> {
      assertThat(e.getModificationTime())
        .as("was update on setValue")
        .isEqualTo(early);
      return null;
    });
  }

  @Test
  public void load_unconditional() {
    CacheWithLoader cwl = cacheWithLoader();
    Cache<Integer, Integer> c = cwl.cache;
    c.invoke(1, e -> {
      e.load();
      assertThat((int) e.getValue()).isEqualTo(1);
      return null;
    });
    c.get(2);
    assertThat(cwl.loader.getCount()).isEqualTo(2);
    c.invoke(2, e -> {
      assertThat(e.exists()).isTrue();
      e.load();
      assertThat((int) e.getValue()).isEqualTo(2);
      return null;
    });
    assertThat(cwl.loader.getCount()).isEqualTo(3);
  }

  @Test
  public void load_unsupported() {
    Cache<Integer, Integer> c = target.cache();
    try {
      c.invoke(1, e -> {
        e.load();
        return null;
      });
      fail("exception expected");
    } catch (EntryProcessingException ex) {
      assertThat(ex.getCause() instanceof UnsupportedOperationException).isTrue();
    }
  }

  @Test
  public void load_getRefreshTime() {
    CacheWithLoader cwl = cacheWithLoader();
    Cache<Integer, Integer> c = cwl.cache;
    long t0 = millis();
    c.get(1);
    c.invoke(1, e -> {
      assertThat(e.getStartTime()).isGreaterThanOrEqualTo(t0);
      assertThat(e.getModificationTime())
        .as("refresh time updated by put()")
        .isGreaterThanOrEqualTo(t0);
      return null;
    });
  }

  @Test
  public void getValue_load() {
    CacheWithLoader cwl = cacheWithLoader();
    Cache<Integer, Integer> c = cwl.cache;
    c.put(1, 1);
    assertThatCode(() -> c.invoke(1, e -> {
      e.getValue();
      e.load();
      return null;
    })).isInstanceOf(EntryProcessingException.class)
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
    assertThat(cwl.loader.getCount()).isEqualTo(0);
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
  public void initial_getRefreshTime() {
    Cache<Integer, Integer> c = target.cache();
    c.invoke(1, e -> {
      assertThat(e.getModificationTime()).isEqualTo(0L);
      return null;
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
    CacheWithLoader c = new CacheWithLoader();
    c.cache = target.cache(b -> {
      b.loader(c.loader);
      b.recordModificationTime(true);
    });
    return c;
  }

  /**
   * Set expiry which keeps exceptions
   */
  CacheWithLoader cacheWithLoaderKeepExceptions() {
    CacheWithLoader c = new CacheWithLoader();
    c.cache = target.cache(b -> {
      b.loader(c.loader);
      b.resiliencePolicy(new ExpiryTest.EnableExceptionCaching());
      b.expireAfterWrite(999, TimeUnit.DAYS);
    });
    return c;
  }

  @Test
  public void getValue_triggerLoad() {
    CacheWithLoader wl = cacheWithLoader();
    wl.cache.invoke(KEY, (EntryProcessor<Integer, Integer, Void>) e -> {
      Integer v = e.getValue();
      assertThat(v).isEqualTo(KEY);
      assertThat(e.exists()).isFalse();
      return null;
    });
    assertThat(wl.loader.getCount()).isEqualTo(1);
    assertThat(wl.cache.containsKey(KEY)).isTrue();
    assertThat(wl.cache.peek(KEY)).isEqualTo(KEY);
  }

  @Test
  public void getException_triggerLoad() {
    CacheWithLoader wl = cacheWithLoader();
    wl.cache.invoke(KEY, (EntryProcessor<Integer, Integer, Void>) e -> {
      Throwable t = e.getException();
      assertThat(t).isNull();
      assertThat(e.exists()).isFalse();
      Integer v = e.getValue();
      assertThat(v).isEqualTo(KEY);
      return null;
    });
    assertThat(wl.loader.getCount()).isEqualTo(1);
  }

  @Test
  public void getValue_triggerLoad_remove() {
    CacheWithLoader wl = cacheWithLoader();
    wl.cache.invoke(KEY, (EntryProcessor<Integer, Integer, Void>) e -> {
      Integer v = e.getValue();
      assertThat(v).isEqualTo(KEY);
      assertThat(e.exists()).isFalse();
      e.remove();
      assertThat(e.exists()).isFalse();
      return null;
    });
    assertThat(wl.loader.getCount()).isEqualTo(1);
    assertThat(wl.cache.containsKey(KEY)).isFalse();
  }

  @Test
  public void put_setValue_remove() {
    CacheWithLoader wl = cacheWithLoader();
    wl.cache.put(KEY, KEY);
    wl.cache.invoke(KEY, (EntryProcessor<Integer, Integer, Void>) e -> {
      e.setValue(VALUE);
      e.remove();
      return null;
    });
    assertThat(wl.cache.containsKey(KEY))
      .as("removed")
      .isFalse();
  }

  @Test
  public void setValue_remove() {
    CacheWithLoader wl = cacheWithLoader();
    wl.cache.invoke(KEY, (EntryProcessor<Integer, Integer, Void>) e -> {
      e.setValue(VALUE);
      e.remove();
      return null;
    });
    assertThat(wl.cache.containsKey(KEY))
      .as("removed")
      .isFalse();
  }

  /**
   * No real remove happens / not counted, since the entry was not there before.
   */
  @Test
  public void getValue_triggerLoad_remove_statistics() {
    CacheWithLoader wl = cacheWithLoader();
    target.statistics();
    wl.cache.invoke(123, (EntryProcessor<Integer, Integer, Void>) e -> {
      Integer v = e.getValue();
      assertThat((int) v).isEqualTo(123);
      e.remove();
      return null;
    });
    target.statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .loadCount.expect(1)
      .expectAllZero();
    assertThat(wl.cache.containsKey(123)).isFalse();
    Integer v = wl.cache.peek(123);
    assertThat(v).isNull();
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
    wl.cache.invoke(123, (EntryProcessor<Integer, Integer, Void>) e -> {
      Integer v = e.getValue();
      assertThat((int) v).isEqualTo(123);
      e.remove();
      return null;
    });
    target.statistics()
      .getCount.expect(1)
      .missCount.expect(1)
      .loadCount.expect(1)
      .expectAllZero();
    boolean exceptionThrown = false;
    try {
      wl.cache.invoke(KEY_YIELDING_PERMANENT_EXCEPTION,
        (EntryProcessor<Integer, Integer, Void>) e -> {
          e.getValue();
          return null;
        });
    } catch (EntryProcessingException ex) {
      exceptionThrown = true;
    }
    assertThat(exceptionThrown).isTrue();
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
      target.cache(b -> b.timeReference(new SimulatedClock())
        .sharpExpiry(true)
        .expiryPolicy((key, value, loadTime, oldEntry) -> loadTime + expireAfterWriteMillis)
        .addListener((CacheEntryExpiredListener<Integer, Integer>) (cache, entry) -> listenerCallCount.incrementAndGet()));
    c.put(123, 4711);
    AtomicInteger callCount4 = new AtomicInteger();
    AtomicInteger callCount3 = new AtomicInteger();
    AtomicInteger callCount1 = new AtomicInteger();
    c.invoke(123, (EntryProcessor<Integer, Integer, Void>) e -> {
      int count = callCount4.incrementAndGet();
      if (count == 1) {
        sleep(expireAfterWriteMillis * 3);
      }
      assertThat(e.exists())
        .as("entry is expired, not existing")
        .isFalse();
      callCount3.incrementAndGet();
      e.setValue(123);
      callCount1.incrementAndGet();
      assertThat(listenerCallCount.get())
        .as("listener called before mutation lock")
        .isEqualTo(1);
      return null;
    });
    assertThat(callCount4.get()).isEqualTo(4);
    assertThat(callCount3.get()).isEqualTo(3);
    assertThat(callCount1.get()).isEqualTo(1);
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
    assertThat(c.get(123)).isNull();
    c.put(123, 123);
  }

  @Test
  public void getValue_triggerLoad_setValue() {
    CacheWithLoader wl = cacheWithLoader();
    wl.cache.invoke(KEY, (EntryProcessor<Integer, Integer, Void>) e -> {
      Integer v = e.getValue();
      assertThat(v).isEqualTo(KEY);
      assertThat(e.exists()).isFalse();
      e.setValue(4711);
      return null;
    });
    assertThat(wl.loader.getCount()).isEqualTo(1);
    assertThat(wl.cache.containsKey(KEY)).isTrue();
    assertThat((int) wl.cache.peek(KEY)).isEqualTo(4711);
  }

  @Test
  public void getValue_triggerLoad_setException() {
    CacheWithLoader wl = cacheWithLoader();
    wl.cache.invoke(KEY, (EntryProcessor<Integer, Integer, Void>) e -> {
      Integer v = e.getValue();
      assertThat(v).isEqualTo(KEY);
      assertThat(e.exists()).isFalse();
      e.setException(new NoSuchElementException());
      return null;
    });
    assertThat(wl.loader.getCount()).isEqualTo(1);
    assertThat(wl.cache.containsKey(KEY))
      .as("exception expires immediately")
      .isFalse();
  }

  @Test(expected = EntryProcessingException.class)
  public void setException_getException_getValue_exception() {
    CacheWithLoader wl = cacheWithLoader();
    wl.cache.invoke(KEY, (EntryProcessor<Integer, Integer, Void>) e -> {
      e.setException(new NoSuchElementException());
      assertThat(e.getException()).isNull();
      assertThat(e.getValue()).isNull();
      return null;
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
      wl.cache.invoke(KEY, (EntryProcessor<Integer, Integer, Void>) e -> {
        e.setValue(VALUE);
        throw new RuntimeException("terminate with exception");
      });
      fail("exception expected");
    } catch (EntryProcessingException _expected) {
    }
    assertThat(wl.cache.containsKey(KEY)).isFalse();
    wl.cache.put(KEY, VALUE);
  }

  @Test
  public void setException_keep_exception() {
    CacheWithLoader wl = cacheWithLoaderKeepExceptions();
    wl.cache.invoke(KEY, (EntryProcessor<Integer, Integer, Void>) e -> {
      e.setException(new NoSuchElementException());
      return null;
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
    wl.cache.invoke(KEY, (EntryProcessor<Integer, Integer, Void>) e -> {
      Integer v = e.getValue();
      assertThat(v).isEqualTo(KEY);
      assertThat(e.exists()).isFalse();
      e.setExpiryTime(NOW);
      return null;
    });
    assertThat(wl.loader.getCount()).isEqualTo(1);
    assertThat(wl.cache.containsKey(KEY))
      .as("expires immediately")
      .isFalse();
  }

  /**
   * If the mapping is not existing setExpiry does not have an effect.
   * We also test combinations with get, since this is the usual use case
   * for TTI.
   */
  @Test
  public void setExpiry_notExistent_entry() {
    Cache<Integer, Integer> c = target.cache(new CacheRule.Context<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.expireAfterWrite(MAX_VALUE - 1, MILLISECONDS);
      }
    });
    c.invoke(123, entry -> {
      entry.setExpiryTime(entry.getStartTime() + DAYS.toMillis(5));
      return null;
    });
    assertThat(c.containsKey(123)).isFalse();
    assertThat(c.asMap().size()).isEqualTo(0);
    Integer v = c.invoke(124, entry -> {
      entry.setExpiryTime(entry.getStartTime() + DAYS.toMillis(5));
      return entry.getValue();
    });
    assertThat(c.containsKey(124)).isFalse();
    assertThat(c.asMap().size()).isEqualTo(0);
    assertThat(v).isNull();
    v = c.invoke(125, entry -> {
      Integer x = entry.getValue();
      entry.setExpiryTime(entry.getStartTime() + DAYS.toMillis(5));
      return x;
    });
    assertThat(c.containsKey(124)).isFalse();
    assertThat(c.asMap().size()).isEqualTo(0);
    assertThat(v).isNull();
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
    CacheWithWriter c = new CacheWithWriter();
    c.cache = target.cache(b -> b.writer(c.writer));
    return c;
  }

  @Test
  public void remove_Empty_WriterDelete() {
    CacheWithWriter ww = cacheWithWriter();
    ww.cache.invoke(KEY, e -> {
      e.remove();
      return null;
    });
    assertThat(ww.writer.deleteCalled.get()).isEqualTo(1);
  }

  @Test
  public void setValue_Empty_WriterWrite() {
    CacheWithWriter ww = cacheWithWriter();
    ww.cache.invoke(KEY, e -> {
      e.setValue(123);
      return null;
    });
    assertThat(ww.writer.deleteCalled.get()).isEqualTo(0);
    assertThat(ww.writer.writeCalled.get()).isEqualTo(1);
  }

  @Test
  public void setException_propagation() {
    final String text = "set inside process";
    Cache<Integer, Integer> c = target.cache(b -> {
      b.resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(Integer key,
                                           LoadExceptionInfo<Integer, Integer> loadExceptionInfo,
                                           CacheEntry<Integer, Integer> cachedEntry) {
          return Long.MAX_VALUE;
        }

        @Override
        public long retryLoadAfter(Integer key, LoadExceptionInfo<Integer, Integer> loadExceptionInfo) {
          return Long.MAX_VALUE;
        }
      });
    });
    c.invoke(KEY, e -> {
      e.setException(new IllegalStateException(text));
      return null;
    });
    try {
      c.get(KEY);
      fail("exception expected");
    } catch (CacheLoaderException ex) {
      assertThat(ex.getCause().toString().contains(text)).isTrue();
    }
  }

  @Test
  public void setException_policy_called() {
    final String text = "set inside process";
    AtomicLong retryLoadAfter = new AtomicLong();
    ResiliencePolicy<Integer, Integer> policy = new ResiliencePolicy<Integer, Integer>() {
      @Override
      public long suppressExceptionUntil(Integer key,
                                         LoadExceptionInfo<Integer, Integer> exceptionInformation,
                                         CacheEntry<Integer, Integer> cachedEntry) {
        return 0;
      }

      @Override
      public long retryLoadAfter(Integer key, LoadExceptionInfo<Integer, Integer> exceptionInformation) {
        retryLoadAfter.incrementAndGet();
        return ETERNAL;
      }
    };
    Cache<Integer, Integer> c = target.cache(b -> b.resiliencePolicy(policy));
    c.invoke(KEY, e -> {
      e.setException(new IllegalStateException(text));
      return null;
    });
    try {
      c.get(KEY);
      fail("exception expected");
    } catch (CacheLoaderException ex) {
      assertThat(ex.getCause().toString().contains(text)).isTrue();
    }
    assertThat(retryLoadAfter.get()).isEqualTo(1);
  }

  /**
   * When the entry is read and modified, we need three restarts.
   */
  @Test
  public void read_write_ep_executed_once_after_mutation_lock() {
    Cache<Integer, Integer> c = target.cache();
    c.put(1, 0);
    AtomicInteger count0 = new AtomicInteger();
    AtomicInteger count1 = new AtomicInteger();
    c.invoke(1, e -> {
      count0.incrementAndGet();
      int val = e.getValue();
      e.setValue(val + 1);
      count1.incrementAndGet();
      return null;
    });
    assertThat((int) c.get(1)).isEqualTo(1);
    assertThat(count0.get())
      .as("passed 4 times: initial/start, after installation read/examine, " +
        "after mutation lock/examine again, mutation")
      .isEqualTo(4);
    assertThat(count1.get())
      .as("passed 1 times: after installation read, after mutation lock")
      .isEqualTo(1);
  }

  /**
   * Only a write occurs, the entry state is not read. We expect no restart to happen.
   */
  @Test
  public void write_ep_executed_once() {
    Cache<Integer, Integer> c = target.cache();
    AtomicInteger count0 = new AtomicInteger();
    AtomicInteger count1 = new AtomicInteger();
    c.invoke(1, e -> {
      count0.incrementAndGet();
      e.setValue(123);
      count1.incrementAndGet();
      return null;
    });
    assertThat((int) c.peek(1)).isEqualTo(123);
    assertThat(count0.get())
      .as("passed 1 times: initial/start")
      .isEqualTo(1);
    assertThat(count1.get())
      .as("passed 1 times: after installation read, after mutation lock")
      .isEqualTo(1);
  }

  @Test
  public void lock() {
    Cache<Integer, Integer> c = target.cache();
    int key = 123;
    c.invoke(key, MutableCacheEntry::lock);
    assertThat(c.containsKey(key)).isFalse();
    assertThat(of(c).getSize()).isEqualTo(0);
    c.invoke(key, entry -> {
      entry.exists();
      entry.lock();
      return null;
    });
    assertThat(c.containsKey(key)).isFalse();
    assertThat(of(c).getSize()).isEqualTo(0);
    c.invoke(key, entry -> {
      entry.setValue(4711);
      entry.exists();
      entry.lock();
      return null;
    });
    assertThat(c.containsKey(key)).isTrue();
    assertThat(of(c).getSize()).isEqualTo(1);
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
    LoadExceptionInfo<Integer, Integer> info =
      cl.cache.invoke(KEY_YIELDING_PERMANENT_EXCEPTION,
        entry -> {
          entry.load();
          return entry.getExceptionInfo();
        });
    assertThat(info).isNotNull();
    assertThat(info.getUntil())
      .as("exception not cached")
      .isEqualTo(0);
  }

  public void speedTest() {
    CacheWithLoader cl = cacheWithLoader();
    for (int i = 0; i < 100; i++) {
      for (int j = 0; i < 1000000; i++) {
        cl.cache.invoke(i, entry -> {
          if (entry.exists()) {
            entry.setValue(entry.getValue() + 1);
          } else {
            entry.setValue(entry.getKey());
          }
          return null;
        });
      }
    }
  }

}
