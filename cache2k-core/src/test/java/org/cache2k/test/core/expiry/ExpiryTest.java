package org.cache2k.test.core.expiry;

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

import org.cache2k.test.util.TestingBase;
import org.cache2k.test.util.IntCountingCacheSource;
import org.cache2k.core.ResiliencePolicyException;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.io.CacheLoader;
import org.cache2k.io.CacheLoaderException;
import org.cache2k.io.LoadExceptionInfo;
import org.cache2k.io.ResiliencePolicy;
import org.cache2k.processor.MutableCacheEntry;
import org.cache2k.test.core.TestingParameters;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.core.api.InternalCache;
import org.cache2k.testing.category.FastTests;
import org.junit.experimental.categories.Category;
import org.junit.Test;

import java.io.Closeable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static java.lang.Long.MAX_VALUE;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.cache2k.expiry.ExpiryTimeValues.*;
import static org.cache2k.operation.CacheControl.of;
import static org.cache2k.test.core.BasicCacheTest.AlwaysExceptionSource;
import static org.cache2k.test.core.BasicCacheTest.OccasionalExceptionSource;
import static org.cache2k.test.core.Constants.resilienceCacheAndSuppressExceptions;
import static org.cache2k.test.core.Constants.resilienceCacheExceptions;
import static org.cache2k.test.core.TestingParameters.MINIMAL_TICK_MILLIS;

/**
 * @author Jens Wilke
 */
@SuppressWarnings({"unchecked", "rawtypes"})
@Category(FastTests.class)
public class ExpiryTest extends TestingBase {

  public static final long LONG_DELTA = TestingParameters.MAX_FINISH_WAIT_MILLIS;

  { enableFastClock(); }

  @Test
  public void notEternal_policy() {
    builder().eternal(false).expiryPolicy((key, value, startTime, currentEntry) -> 0).build();
  }

  @Test
  public void expireAfterWrite_eternal() {
    builder().expireAfterWrite(123, TimeUnit.SECONDS).eternal(true).build();
  }

  @Test
  public void testFetchAlways() {
    IntCountingCacheSource g = new IntCountingCacheSource();
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(g)
      .expireAfterWrite(0, TimeUnit.SECONDS).build();
    checkAlwaysLoaded(g, c);
  }

  private void checkAlwaysLoaded(IntCountingCacheSource g, Cache<Integer, Integer> c) {
    assertThat(g.getLoaderCalledCount())
      .as("no miss yet")
      .isEqualTo(0);
    c.get(1802);
    assertThat(g.getLoaderCalledCount())
      .as("one miss yet")
      .isEqualTo(1);
    c.get(1802);
    assertThat(g.getLoaderCalledCount())
      .as("additional miss")
      .isEqualTo(2);
    c.get(1802);
    assertThat(g.getLoaderCalledCount())
      .as("additional miss")
      .isEqualTo(3);
    c.get(1802);
    assertThat(g.getLoaderCalledCount())
      .as("additional miss")
      .isEqualTo(4);
  }

  @Test
  public void testFetchAlwaysWithVariableExpiry0() {
    IntCountingCacheSource g = new IntCountingCacheSource();
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(g)
      .expiryPolicy((key, value, startTime, currentEntry) -> 0)
      .expireAfterWrite(0, TimeUnit.SECONDS).build();
    checkAlwaysLoaded(g, c);
  }

  @Test
  public void testFetchAlwaysWithVariableExpiryInPast() {
    IntCountingCacheSource g = new IntCountingCacheSource();
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(g)
      .expiryPolicy((key, value, startTime, currentEntry) -> 1234567)
      .expireAfterWrite(0, TimeUnit.SECONDS).build();
    checkAlwaysLoaded(g, c);
  }

  @Test
  public void testFetchAlwaysWithVariableExpiryInPastAfterLoad() {
    IntCountingCacheSource g = new IntCountingCacheSource();
    Cache<Integer, Integer> c = cache = Cache2kBuilder.of(Integer.class, Integer.class)
      .loader(g)
      .expiryPolicy((key, value, startTime, currentEntry) -> {
        sleep(3);
        return startTime + 1;
      })
      .expireAfterWrite(0, TimeUnit.SECONDS).build();
    checkAlwaysLoaded(g, c);
  }

  @Test
  public void testEternalExceptionsEternal() {
    IntCountingCacheSource g = new IntCountingCacheSource() {
      @Override
      public Integer load(Integer o) {
        incrementLoadCalledCount();
        if (o == 99) {
          throw new RuntimeException("ouch");
        }
        return o;
      }
    };
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(g)
      .eternal(true)
      .resiliencePolicy(new EnableExceptionCaching())
      .build();
    assertThat(g.getLoaderCalledCount())
      .as("no miss")
      .isEqualTo(0);
    c.get(1802);
    assertThat(g.getLoaderCalledCount())
      .as("one miss")
      .isEqualTo(1);
    c.get(1802);
    assertThat(g.getLoaderCalledCount())
      .as("one miss")
      .isEqualTo(1);
    assertThat(((InternalCache) c).getEntryState(1802).contains("nextRefreshTime=ETERNAL")).isTrue();
    CacheEntry<Integer, Integer> e = c.getEntry(99);
    entryHasException(e);
    assertThat(e.getException().getClass()).isEqualTo(RuntimeException.class);
    assertThat(g.getLoaderCalledCount())
      .as("two miss")
      .isEqualTo(2);
    assertThat(((InternalCache) c).getEntryState(99).contains("nextRefreshTime=ETERNAL")).isTrue();
    e = c.getEntry(99);
    entryHasException(e);
    assertThat(e.getException().getClass()).isEqualTo(RuntimeException.class);
    assertThat(g.getLoaderCalledCount())
      .as("two miss")
      .isEqualTo(2);
    try {
      c.get(99);
      fail("expect exception");
    } catch (Exception ex) {
      assertThat(ex.toString()).contains("expiry=ETERNAL");
    }
  }

  static final String EXPIRY_MARKER = "expiry=";

  /**
   * Switching to eternal means exceptions expire immediately.
   */
  @Test
  public void testEternal_keepData() {
    final int exceptionKey = 99;
    IntCountingCacheSource g = new IntCountingCacheSource() {
      @Override
      public Integer load(Integer o) {
        incrementLoadCalledCount();
        if (o == exceptionKey) {
          throw new RuntimeException("ouch");
        }
        return o;
      }
    };
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(g)
      .eternal(true)
      .keepDataAfterExpired(true)
      .build();
    assertThat(g.getLoaderCalledCount()).as("no miss").isEqualTo(0);
    c.get(1802);
    assertThat(g.getLoaderCalledCount()).as("one miss").isEqualTo(1);
    c.get(1802);
    assertThat(g.getLoaderCalledCount())
      .as("one miss")
      .isEqualTo(1);
    assertThat(((InternalCache) c).getEntryState(1802).contains("nextRefreshTime=ETERNAL")).isTrue();
    assertThatCode(() ->
      c.get(exceptionKey))
      .isInstanceOf(CacheLoaderException.class)
      .hasMessageNotContainingAny(EXPIRY_MARKER)
      .getCause().isInstanceOf(RuntimeException.class);
    assertThat(g.getLoaderCalledCount()).as("miss").isEqualTo(2);
    assertThat(((InternalCache) c).getEntryState(exceptionKey).contains("state=4")).isTrue();
    CacheEntry<Integer, Integer> e = c.getEntry(exceptionKey);
    entryHasException(e);
    assertThat(e.getException().getClass()).isEqualTo(RuntimeException.class);
    assertThat(g.getLoaderCalledCount())
      .as("miss")
      .isEqualTo(3);
  }

  @Test
  public void loadExceptionEntryNullInExpiryPolicy() throws ExecutionException, InterruptedException {
    AtomicBoolean success = new AtomicBoolean();
    IntCountingCacheSource g = new IntCountingCacheSource() {
      @Override
      public Integer load(Integer o) {
        incrementLoadCalledCount();
        if (getLoaderCalledCount() == 1) {
          throw new RuntimeException("ouch");
        }
        return getLoaderCalledCount();
      }
    };
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(g)
      .resiliencePolicy(new EnableExceptionCaching(MAX_VALUE))
      .expiryPolicy((key, value, startTime, currentEntry) -> {
        if (g.getLoaderCalledCount() == 2) {
          assertThat(currentEntry).isNull();
          success.set(true);
        }
        return 0;
      })
      .build();
    try {
      c.get(1);
      fail("exception expected");
    } catch (CacheLoaderException expected) {
    }
    c.reloadAll(asList(1)).get();
    assertThat(success.get()).isTrue();
  }

  @Test
  public void dontCallAdvancedLoaderWithExceptionEntry_enableExceptionCaching() throws ExecutionException, InterruptedException {
    dontCallAdvancedLoaderWithExceptionEntry(b -> b.
        resiliencePolicy(new EnableExceptionCaching(Long.MAX_VALUE))
      );
  }

  @Test
  public void dontCallAdvancedLoaderWithExceptionEntry_keepData() throws ExecutionException, InterruptedException {
    dontCallAdvancedLoaderWithExceptionEntry(b -> b.keepDataAfterExpired(true));
  }

  public void dontCallAdvancedLoaderWithExceptionEntry(
    Consumer<Cache2kBuilder<Integer, Integer>> builderAction) throws ExecutionException, InterruptedException {
    AtomicBoolean success = new AtomicBoolean();
    AtomicInteger loadCount = new AtomicInteger();
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader((key, startTime, currentEntry) -> {
        if (loadCount.incrementAndGet() == 1) {
          assertThat(currentEntry).isNull();
          throw new RuntimeException("ouch");
        }
        assertThat(currentEntry)
          .as("entry is null, if exception happened previously")
          .isNull();
        success.set(true);
        return key;
      })
      .setup(builderAction)
      .build();
    try {
      c.get(1);
      fail("exception expected");
    } catch (CacheLoaderException expected) {
    }
    c.reloadAll(asList(1)).get();
    assertThat(success.get()).isTrue();
  }

  @Test
  public void dontCallAsyncLoaderWithExceptionEntry_keepData() throws ExecutionException, InterruptedException {
    dontCallAsyncLoaderWithExceptionEntry(b -> b.keepDataAfterExpired(true));
  }

  @Test
  public void dontCallAsyncLoaderWithExceptionEntry_cacheExceptions() throws ExecutionException, InterruptedException {
    dontCallAsyncLoaderWithExceptionEntry(b -> b.
      resiliencePolicy(new EnableExceptionCaching(Long.MAX_VALUE))
    );
  }

  public void dontCallAsyncLoaderWithExceptionEntry(
    Consumer<Cache2kBuilder<Integer, Integer>> builderAction) throws ExecutionException, InterruptedException {
    AtomicBoolean success = new AtomicBoolean();
    AtomicInteger loadCount = new AtomicInteger();
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader((key, context, callback) -> {
        if (loadCount.incrementAndGet() == 1) {
          assertThat(context.getCurrentEntry()).isNull();
          throw new RuntimeException("ouch");
        }
        assertThat(context.getCurrentEntry())
          .as("entry is null, if exception happened previously")
          .isNull();
        success.set(true);
        callback.onLoadSuccess(key);
      })
      .setup(builderAction)
      .build();
    try {
      c.get(1);
      fail("exception expected");
    } catch (CacheLoaderException expected) {
    }
    c.reloadAll(asList(1)).get();
    assertThat(success.get()).isTrue();
  }

  /**
   * Switching to eternal means exceptions expire immediately.
   */
  @Test
  public void testEternal_noKeep() {
    final int exceptionKey = 99;
    IntCountingCacheSource g = new IntCountingCacheSource() {
      @Override
      public Integer load(Integer o) {
        incrementLoadCalledCount();
        if (o == exceptionKey) {
          throw new RuntimeException("ouch");
        }
        return o;
      }
    };
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(g)
      .eternal(true)
      .build();
    assertThat(g.getLoaderCalledCount())
      .as("no miss")
      .isEqualTo(0);
    c.get(1802);
    assertThat(g.getLoaderCalledCount())
      .as("one miss")
      .isEqualTo(1);
    c.get(1802);
    assertThat(g.getLoaderCalledCount())
      .as("one miss")
      .isEqualTo(1);
    assertThat(((InternalCache) c).getEntryState(1802).contains("nextRefreshTime=ETERNAL")).isTrue();
    assertThatCode(() ->
      c.get(exceptionKey))
      .isInstanceOf(CacheLoaderException.class)
      .hasMessageNotContainingAny(EXPIRY_MARKER)
      .getCause().isInstanceOf(RuntimeException.class);
    assertThat(g.getLoaderCalledCount())
      .as("miss")
      .isEqualTo(2);
    CacheEntry<Integer, Integer> e = c.getEntry(exceptionKey);
    entryHasException(e);
    assertThat(e.getException().getClass()).isEqualTo(RuntimeException.class);
    assertThat(g.getLoaderCalledCount())
      .as("miss")
      .isEqualTo(3);
  }

  @Test
  public void testEternalExceptionsExpire() {
    IntCountingCacheSource g = new IntCountingCacheSource() {
      @Override
      public Integer load(Integer o) {
        incrementLoadCalledCount();
        if (o == 99) {
          throw new RuntimeException("ouch");
        }
        return o;
      }
    };
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(g)
      .eternal(true)
      .resiliencePolicy(new EnableExceptionCaching(TestingParameters.MINIMAL_TICK_MILLIS))
      .build();
    try {
      c.get(99);
      fail("exception expected");
    } catch (Exception ex) {
      assertThat(ex instanceof CacheLoaderException).isTrue();
      assertThat(ex.toString().contains(EXPIRY_MARKER))
        .as("expiry on exception")
        .isTrue();
      assertThat(ex.getCause() instanceof RuntimeException).isTrue();
    }
    await(() -> {
      if (getInfo().getExpiredCount() > 0) {
        c.getEntry(99);
        assertThat(g.getLoaderCalledCount() > 1).isTrue();
        return true;
      }
      return false;
    });
  }

  public static class EnableExceptionCaching implements ResiliencePolicy<Integer, Integer> {

    private final long retryMillis;

    public EnableExceptionCaching(long retryMillis) {
      this.retryMillis = retryMillis;
    }

    public EnableExceptionCaching() { this(Long.MAX_VALUE); }

    @Override
    public long suppressExceptionUntil(Integer key, LoadExceptionInfo<Integer, Integer> loadExceptionInfo,
                                       CacheEntry<Integer, Integer> cachedEntry) {
      return 0;
    }

    @Override
    public long retryLoadAfter(Integer key, LoadExceptionInfo<Integer, Integer> loadExceptionInfo) {
      if (retryMillis == Long.MAX_VALUE) {
        return retryMillis;
      }
      return loadExceptionInfo.getLoadTime() + retryMillis;
    }
  }

  /**
   * Don't suppress exceptions eternally if resilience policy is enabled by specifying
   * a retry interval.
   */
  @Test
  public void testEternalExceptionsExpireNoSuppress() {
    IntCountingCacheSource g = new IntCountingCacheSource() {
      @Override
      public Integer load(Integer o) {
        incrementLoadCalledCount();
        if (o == 99) {
          throw new RuntimeException("ouch");
        }
        return o;
      }
    };
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(g)
      .eternal(true)
      .resiliencePolicy(new EnableExceptionCaching(MINIMAL_TICK_MILLIS))
      .build();
    c.put(99, 1);
    int v = c.peek(99);
    assertThat(v).isEqualTo(1);
    within(MINIMAL_TICK_MILLIS)
      .perform(() -> assertThatCode(() -> c.reloadAll(asList(99)).get())
        .isInstanceOf(ExecutionException.class)
        .getCause()
        .isInstanceOf(CacheLoaderException.class))
      .expectMaybe(() -> {
        try {
          c.get(99);
          fail("exception expected");
        } catch (Exception ex) {
          assertThat(ex instanceof CacheLoaderException).isTrue();
          assertThat(ex.toString().contains(EXPIRY_MARKER))
            .as("expiry on exception")
            .isTrue();
          assertThat(ex.getCause() instanceof RuntimeException).isTrue();
        }
      });
    assertThat(g.getLoaderCalledCount() > 0).isTrue();
  }


  @Test
  public void testValueExpireExceptionsEternal() {
    IntCountingCacheSource g = new IntCountingCacheSource() {
      @Override
      public Integer load(Integer o) {
        incrementLoadCalledCount();
        if (o == 99) {
          throw new RuntimeException("ouch");
        }
        return o;
      }
    };
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(g)
      .resiliencePolicy(resilienceCacheExceptions())
      .build();
    assertThat(g.getLoaderCalledCount())
      .as("no miss")
      .isEqualTo(0);
    c.get(1802);
    assertThat(g.getLoaderCalledCount())
      .as("one miss")
      .isEqualTo(1);
    c.get(1802);
    assertThat(g.getLoaderCalledCount())
      .as("one miss")
      .isEqualTo(1);
    CacheEntry<Integer, Integer> e = c.getEntry(99);
    entryHasException(e);
    assertThat(e.getException().getClass()).isEqualTo(RuntimeException.class);
    assertThat(g.getLoaderCalledCount())
      .as("two miss")
      .isEqualTo(2);
    assertThat(((InternalCache) c).getEntryState(99).contains("nextRefreshTime=ETERNAL")).isTrue();
  }

  /**
   * Exceptions are cached no longer than the specified expireAfterWrite duration, even
   * if the resilience policy returns a later time.
   */
  @Test
  public void cacheExceptionTimeCapped() {
    IntCountingCacheSource g = new IntCountingCacheSource() {
      @Override
      public Integer load(Integer o) {
        incrementLoadCalledCount();
        if (o == 99) {
          throw new RuntimeException("ouch");
        }
        return o;
      }
    };
    long expiryMillis = MAX_VALUE / 10000;
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(g)
      .expireAfterWrite(expiryMillis, MILLISECONDS)
      .resiliencePolicy(resilienceCacheExceptions())
      .build();
    CacheEntry<Integer, Integer> e = c.getEntry(99);
    LoadExceptionInfo<Integer, Integer> info = e.getExceptionInfo();
    assertThat(info.getUntil()).isEqualTo(info.getLoadTime() + expiryMillis);
  }

  /**
   * Exceptions are suppressed no longer than the specified expireAfterWrite duration, even
   * if the resilience policy returns a later time.
   */
  @Test
  public void suppressExceptionTimeCapped() {
    IntCountingCacheSource g = new IntCountingCacheSource() {
      @Override
      public Integer load(Integer o) {
        incrementLoadCalledCount();
        if (getLoaderCalledCount() == 2) {
          throw new RuntimeException("ouch");
        }
        return o;
      }
    };
    long expiryMillis = MAX_VALUE / 10000;
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(g)
      .expireAfterWrite(expiryMillis, MILLISECONDS)
      .resiliencePolicy(resilienceCacheAndSuppressExceptions())
      .build();
    c.get(1);
    long loadTime = c.invoke(1, entry -> {
      entry.load();
      return entry.getStartTime();
    });
    long expiryTime = c.invoke(1, MutableCacheEntry::getExpiryTime);
    assertThat(g.getLoaderCalledCount())
      .as("two loads")
      .isEqualTo(2);
    assertThat(expiryTime).isEqualTo(loadTime + expiryMillis);
  }

  private static void entryHasException(CacheEntry<Integer, Integer> e) {
    try {
      e.getValue();
      fail("exception expected");
    } catch (CacheLoaderException ex) {
    }
    assertThat(e.getException()).isNotNull();
  }

  @Test
  public void testImmediateExpire() {
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new IntCountingCacheSource())
      .expireAfterWrite(0, MILLISECONDS)
      .keepDataAfterExpired(false)
      .sharpExpiry(true)
      .build();
    c.get(1);
    assertThat(getInfo().getSize()).isEqualTo(0);
    c.put(3, 3);
    assertThat(getInfo().getSize()).isEqualTo(0);
    assertThat(getInfo().getPutCount()).isEqualTo(0);
  }

  @Test
  public void testImmediateExpireWhenCacheDisabled() {
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new IntCountingCacheSource())
      .keepDataAfterExpired(false)
      .build();
    c.get(1);
    assertThat(getInfo().getSize()).isEqualTo(1);
    of(c).changeCapacity(0);
    assertThat(getInfo().getSize()).isEqualTo(0);
    c.put(3, 3);
    assertThat(getInfo().getSize()).isEqualTo(0);
    c.get(2);
    assertThat(getInfo().getSize()).isEqualTo(0);
    assertThat(getInfo().getGetCount()).isEqualTo(2);
    of(c).changeCapacity(123);
    c.get(4);
    assertThat(getInfo().getSize()).isEqualTo(1);
  }

  @Test
  public void testImmediateExpireWithExpiryCalculator() {
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new IntCountingCacheSource())
      .expiryPolicy((key, value, startTime, currentEntry) -> 0)
      .keepDataAfterExpired(false)
      .sharpExpiry(true)
      .build();
    c.get(1);
    assertThat(getInfo().getSize()).isEqualTo(0);
    c.put(3, 3);
    assertThat(getInfo().getSize()).isEqualTo(0);
  }

  @Test
  public void testImmediateExpireAfterUpdate() {
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new IntCountingCacheSource())
      .expiryPolicy((key, value, startTime, currentEntry) -> {
        if (currentEntry == null) {
          return ETERNAL;
        }
        return NOW;
      })
      .keepDataAfterExpired(false)
      .sharpExpiry(true)
      .build();
    c.get(1);
    assertThat(getInfo().getSize()).isEqualTo(1);
    c.put(1, 3);
    assertThat(getInfo().getSize()).isEqualTo(0);
    assertThat(getInfo().getPutCount()).isEqualTo(0);
  }

  @Test
  public void testImmediateExpireAfterPut() {
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .expiryPolicy((key, value, startTime, currentEntry) -> {
        if (currentEntry == null) {
          return ETERNAL;
        }
        return NOW;
      })
      .keepDataAfterExpired(false)
      .sharpExpiry(true)
      .build();
    c.put(1, 1);
    assertThat(getInfo().getPutCount()).isEqualTo(1);
    assertThat(getInfo().getSize()).isEqualTo(1);
    c.put(1, 3);
    assertThat(getInfo().getSize()).isEqualTo(0);
    assertThat(getInfo().getPutCount()).isEqualTo(1);
  }

  @Test
  public void testResiliencePolicyException() {
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .eternal(true)
      .expiryPolicy((key, value, startTime, currentEntry) -> 0)
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(Integer key,
                                           LoadExceptionInfo<Integer, Integer> exceptionInformation,
                                           CacheEntry<Integer, Integer> cachedEntry) {
          fail("not reached");
          return 0;
        }

        @Override
        public long retryLoadAfter(Integer key, LoadExceptionInfo<Integer, Integer> exceptionInformation) {
          throw new NullPointerException("test");
        }
      })
      .loader(new OccasionalExceptionSource())
      .build();
    CacheEntry e = c.getEntry(1);
    assertThat(e.getException().getClass()).isEqualTo(ResiliencePolicyException.class);
    assertThat(e.getException().getCause().getClass()).isEqualTo(NullPointerException.class);
  }

  @Test
  public void testResiliencePolicyLoadExceptionInformationContent_keep() {
    long t0 = ticks();
    final int initial = -4711;
    AtomicInteger cacheRetryCount = new AtomicInteger(initial);
    AtomicInteger suppressRetryCount = new AtomicInteger(initial);
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .eternal(true)
      .keepDataAfterExpired(true)
      .expiryPolicy((key, value, startTime, currentEntry) -> 0)
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(Integer key, LoadExceptionInfo<Integer, Integer> inf,
                                           CacheEntry<Integer, Integer> cachedEntry) {
          assertThat(inf.getException() instanceof IllegalStateException).isTrue();
          assertThat(cachedEntry.getValue()).isEqualTo(key);
          assertThat(cachedEntry.getKey()).isEqualTo(key);
          suppressRetryCount.set(inf.getRetryCount());
          return 0;
        }

        @Override
        public long retryLoadAfter(Integer key, LoadExceptionInfo inf) {
          cacheRetryCount.set(inf.getRetryCount());
          if (inf.getRetryCount() == 0) {
            assertThat(inf.getLoadTime()).isEqualTo(inf.getSinceTime());
          } else {
            assertThat(inf.getLoadTime() > inf.getSinceTime())
              .as("2 ms pause, time different")
              .isTrue();
          }
          assertThat(inf.getLoadTime() >= t0).isTrue();
          assertThat(inf.getUntil()).isEqualTo(0);
          return 0;
        }
      })
      .loader(new Every1ExceptionLoader())
      .build();
    CacheEntry e = c.getEntry(0xff);
    assertThat(e.getException()).isNotNull();
    assertThat(suppressRetryCount.get()).isEqualTo(initial);
    assertThat(cacheRetryCount.get()).isEqualTo(0);
    sleep(2);
    c.getEntry(0xff);
    assertThat(cacheRetryCount.get()).isEqualTo(1);
    e = c.getEntry(0x06);
    assertThat(e.getException()).isNull();
    e = c.getEntry(0x06);
    assertThat(e.getException()).isNotNull();
    assertThat(suppressRetryCount.get()).isEqualTo(0);
    assertThat(cacheRetryCount.get()).isEqualTo(0);
    sleep(2);
    e = c.getEntry(0x06);
    assertThat(e.getException()).isNotNull();
    assertThat(suppressRetryCount.get()).isEqualTo(0);
    assertThat(cacheRetryCount.get()).isEqualTo(1);
  }

  @Test
  public void testResiliencePolicyLoadExceptionInformationContent_noKeep() {
    long t0 = ticks();
    final int initial = -4711;
    AtomicInteger cacheRetryCount = new AtomicInteger(initial);
    AtomicInteger suppressRetryCount = new AtomicInteger(initial);
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .keepDataAfterExpired(false)
      .eternal(true)
      .expiryPolicy((key, value, startTime, currentEntry) -> 0)
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(Integer key, LoadExceptionInfo<Integer, Integer> inf,
                                           CacheEntry<Integer, Integer> cachedEntry) {
          assertThat(inf.getException() instanceof IllegalStateException).isTrue();
          assertThat(cachedEntry.getValue()).isEqualTo(key);
          assertThat(cachedEntry.getKey()).isEqualTo(key);
          suppressRetryCount.set(inf.getRetryCount());
          return 0;
        }

        @Override
        public long retryLoadAfter(Integer key, LoadExceptionInfo inf) {
          cacheRetryCount.set(inf.getRetryCount());
          if (inf.getRetryCount() == 0) {
            assertThat(inf.getLoadTime()).isEqualTo(inf.getSinceTime());
          } else {
            assertThat(inf.getLoadTime() > inf.getSinceTime())
              .as("2 ms pause, time different")
              .isTrue();
          }
          assertThat(inf.getLoadTime() >= t0).isTrue();
          assertThat(inf.getUntil()).isEqualTo(0);
          return 0;
        }
      })
      .loader(new Every1ExceptionLoader())
      .build();
    CacheEntry e = c.getEntry(0xff);
    assertThat(e.getException()).isNotNull();
    assertThat(suppressRetryCount.get()).isEqualTo(initial);
    assertThat(cacheRetryCount.get()).isEqualTo(0);
    sleep(2);
    c.getEntry(0xff);
    assertThat(cacheRetryCount.get()).isEqualTo(0);
    e = c.getEntry(0x06);
    assertThat(e.getException()).isNull();
    e = c.getEntry(0x06);
    assertThat(e.getException()).isNotNull();
    assertThat(suppressRetryCount.get())
      .as("valid entry expired immediately")
      .isEqualTo(initial);
    assertThat(cacheRetryCount.get()).isEqualTo(0);
    sleep(2);
    e = c.getEntry(0x06);
    assertThat(e.getException()).isNotNull();
    assertThat(suppressRetryCount.get())
      .as("valid entry expired immediately")
      .isEqualTo(initial);
    assertThat(cacheRetryCount.get()).isEqualTo(0);
  }

  @Test
  public void testResiliencePolicyLoadExceptionCountWhenSuppressed() throws ExecutionException, InterruptedException {
    final int initial = -4711;
    AtomicInteger cacheRetryCount = new AtomicInteger(initial);
    AtomicInteger suppressRetryCount = new AtomicInteger(initial);
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .eternal(true)
      .keepDataAfterExpired(true)
      .expiryPolicy((key, value, startTime, currentEntry) -> 0)
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(Integer key, LoadExceptionInfo<Integer, Integer> inf,
                                           CacheEntry<Integer, Integer> cachedEntry) {
          suppressRetryCount.set(inf.getRetryCount());
          return inf.getLoadTime() + 1;
        }

        @Override
        public long retryLoadAfter(Integer key, LoadExceptionInfo<Integer, Integer> inf) {
          cacheRetryCount.set(inf.getRetryCount());
          return 0;
        }
      })
      .loader(new AlwaysExceptionSource())
      .build();
    c.put(1, 1);
    assertThat(suppressRetryCount.get()).isEqualTo(initial);
    assertThat(cacheRetryCount.get()).isEqualTo(initial);
    c.getEntry(1);
    assertThat(suppressRetryCount.get()).isEqualTo(0);
    assertThat(cacheRetryCount.get()).isEqualTo(initial);
    assertThat(getInfo().getSuppressedExceptionCount()).isEqualTo(1);
    c.reloadAll(asList(1)).get();
    assertThat(suppressRetryCount.get()).isEqualTo(1);
    assertThat(cacheRetryCount.get()).isEqualTo(initial);
    c.reloadAll(asList(1)).get();
    assertThat(suppressRetryCount.get()).isEqualTo(2);
    assertThat(cacheRetryCount.get()).isEqualTo(initial);
  }

  @Test
  public void testPolicyNotCalledIfExpire0() {
    AtomicLong policyCalled = new AtomicLong();
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new AlwaysExceptionSource())
      .expireAfterWrite(0, SECONDS)
      .keepDataAfterExpired(true)
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(Integer key,
                                           LoadExceptionInfo<Integer, Integer> exceptionInformation,
                                           CacheEntry<Integer, Integer> cachedEntry) {
          policyCalled.incrementAndGet();
          return 1000;
        }

        @Override
        public long retryLoadAfter(Integer key, LoadExceptionInfo<Integer, Integer> exceptionInformation) {
          policyCalled.incrementAndGet();
          return 0;
        }
      })
      .build();
    c.put(1, 1);
    c.getEntry(1);
    assertThat(policyCalled.get()).isEqualTo(0);
    assertThat(getInfo().getSuppressedExceptionCount()).isEqualTo(0);
  }

  @Test
  public void testImmediateExpireButKeepDataDoesSuppress() {
    AtomicLong policyCalled = new AtomicLong();
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new AlwaysExceptionSource())
      .expiryPolicy((key, value, startTime, currentEntry) -> 0)
      .keepDataAfterExpired(true)
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(Integer key,
                                           LoadExceptionInfo<Integer, Integer> exceptionInformation,
                                           CacheEntry<Integer, Integer> cachedEntry) {
          policyCalled.incrementAndGet();
          return exceptionInformation.getLoadTime() + 1;
        }

        @Override
        public long retryLoadAfter(Integer key, LoadExceptionInfo<Integer, Integer> exceptionInformation) {
          policyCalled.incrementAndGet();
          return 0;
        }
      })
      .build();
    c.put(1, 1);
    c.getEntry(1);
    assertThat(policyCalled.get()).isEqualTo(1);
    assertThat(getInfo().getSuppressedExceptionCount()).isEqualTo(1);
  }

  @Test
  public void testResiliencePolicyLoadExceptionInformationCounterReset_keep() {
    final int initial = -4711;
    AtomicInteger cacheRetryCount = new AtomicInteger(initial);
    AtomicInteger suppressRetryCount = new AtomicInteger(initial);
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .keepDataAfterExpired(true)
      .expiryPolicy((key, value, startTime, currentEntry) -> NOW)
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(Integer key, LoadExceptionInfo<Integer, Integer> inf,
                                           CacheEntry<Integer, Integer> cachedEntry) {
          suppressRetryCount.set(inf.getRetryCount());
          return ETERNAL;
        }

        @Override
        public long retryLoadAfter(Integer key, LoadExceptionInfo<Integer, Integer> inf) {
          cacheRetryCount.set(inf.getRetryCount());
          return NOW;
        }
      })
      .loader(new Every1ExceptionLoader())
      .build();
    int key = 2 + 8;
    CacheEntry<Integer, Integer> e = c.getEntry(key);
    assertThat(e.getException()).isNull();
    e = c.getEntry(key);
    assertThat(e.getException()).as("Exception was suppressed").isNull();
    assertThat(suppressRetryCount.get()).isEqualTo(0);
    assertThat(cacheRetryCount.get()).isEqualTo(-4711);
    e = c.getEntry(key);
    assertThat(e.getException()).as("still suppressed").isNull();
    assertThat(suppressRetryCount.get())
      .as("no additional loader call")
      .isEqualTo(0);
    assertThat(cacheRetryCount.get()).isEqualTo(-4711);
    c.put(key, 123);
    e = c.getEntry(key);
    assertThat(e.getException()).isNull();
    assertThat(suppressRetryCount.get()).isEqualTo(0);
    e = c.getEntry(key);
    assertThat(e.getException()).isNull();
    assertThat(suppressRetryCount.get()).isEqualTo(0);
  }

  /**
   * Refresh ahead is on but the expiry policy returns 0.
   * That is a contradiction. Expiry policy overrules refresh ahead, the
   * entry is expired and not visible.
   */
  @Test
  public void testRefreshButNoRefreshIfAlreadyExpiredZeroTimeCheckCounters() {
    final int count = 3;
    IntCountingCacheSource countingLoader = new IntCountingCacheSource();
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .refreshAhead(true)
      .eternal(true)
      .expiryPolicy((key, value, startTime, currentEntry) -> NOW)
      .loader(countingLoader)
      .build();
    c.get(1);
    c.get(2);
    c.get(3);
    await("All expired", () -> getInfo().getExpiredCount() >= count);
    assertThat(getInfo().getRefreshCount() + getInfo().getRefreshRejectedCount()).isEqualTo(0);
    assertThat(countingLoader.getLoaderCalledCount()).isEqualTo(count);
    assertThat(getInfo().getExpiredCount()).isEqualTo(count);
  }

  /**
   * Refresh ahead is on but the expiry policy returns 0.
   * That is a contradiction. Expiry policy overrules refresh ahead, the
   * entry is expired and not visible.
   */
  @Test // enabled again 23.8.2016;jw @Ignore("no keep and refresh ahead is prevented")
  public void testRefreshNoKeepButNoRefreshIfAlreadyExpiredZeroTimeCheckCounters() {
    final int count = 3;
    IntCountingCacheSource countingLoader = new IntCountingCacheSource();
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .refreshAhead(true)
      .eternal(true)
      .expiryPolicy((key, value, startTime, currentEntry) -> NOW)
      .loader(countingLoader)
      .keepDataAfterExpired(false)
      .build();
    c.get(1);
    c.get(2);
    c.get(3);
    await("All expired", () -> getInfo().getExpiredCount() >= count);
    assertThat(getInfo().getRefreshCount() + getInfo().getRefreshRejectedCount()).isEqualTo(0);
    assertThat(countingLoader.getLoaderCalledCount()).isEqualTo(count);
    assertThat(getInfo().getExpiredCount()).isEqualTo(count);
  }

  /**
   * Refresh ahead is on but the expiry policy returns 0.
   * That is a contradiction. Expiry policy overrules refresh ahead, the
   * entry is expired and not visible.
   */
  @Test
  public void testRefreshButNoRefreshIfAlreadyExpiredZeroTime() {
    IntCountingCacheSource countingLoader = new IntCountingCacheSource();
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .refreshAhead(true)
      .eternal(true)
      .expiryPolicy((key, value, startTime, currentEntry) -> NOW)
      .loader(countingLoader)
      .build();
    c.get(1);
    assertThat(c.containsKey(1)).isFalse();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRefreshWithKeepData() {
    builder(Integer.class, Integer.class)
      .refreshAhead(true)
      .keepDataAfterExpired(false)
      .eternal(true)
      .loader(new IdentIntSource())
      .build();
  }

  @Test
  public void testRefreshIfAlreadyExpiredLoadTime() {
    final int count = 3;
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .refreshAhead(true)
      .eternal(true)
      .expiryPolicy((key, value, startTime, currentEntry) -> startTime)
      .loader(new IdentIntSource())
      .build();
    c.get(1);
    c.get(2);
    c.get(3);
    await("All refreshed", () -> getInfo().getRefreshCount() + getInfo().getRefreshRejectedCount() >= count);
    await("All expired", () -> getInfo().getExpiredCount() >= count);
  }

  static final long FUTURE_TIME =
    LocalDateTime.parse("2058-02-18T23:42:15")
      .atZone(ZoneId.of("UTC")).toInstant().toEpochMilli();

  @Test(expected = IllegalArgumentException.class)
  public void manualExpire_exception() {
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .eternal(true)
      .build();
    c.put(1, 2);
    c.expireAt(1, FUTURE_TIME);
  }

  @Test
  public void manualExpire_now() {
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .eternal(true)
      .build();
    c.put(1, 2);
    c.expireAt(1, 0);
    assertThat(c.containsKey(1)).isFalse();
  }

  @Test
  public void manualExpire_aboutNow() {
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .expireAfterWrite(LONG_DELTA, MILLISECONDS)
      .build();
    c.put(1, 2);
    assertThat(c.containsKey(1)).isTrue();
    c.expireAt(1, ticks());
    assertThat(c.containsKey(1)).isFalse();
    statistics()
      .putCount.expect(1)
      .expiredCount.expect(1)
      .expectAllZero();
  }

  abstract class ManualExpireFixture {

    final Semaphore sem = new Semaphore(1);
    final AtomicInteger count = new AtomicInteger();
    final Cache<Integer, Integer> cache;
    final long startTime;

    {
      Cache2kBuilder<Integer, Integer> b = builder(Integer.class, Integer.class)
        .expireAfterWrite(LONG_DELTA, TimeUnit.MILLISECONDS)
        .refreshAhead(true)
        .keepDataAfterExpired(false);
      addLoader(b);
      cache = b.build();
      startTime = getClock().ticks();
    }

    protected void addLoader(Cache2kBuilder<Integer, Integer> b) {
      b.loader(key -> waitForSemaphoreAndLoad());
    }

    protected Integer waitForSemaphoreAndLoad() throws Exception {
      sem.acquire(); sem.release();
      count.incrementAndGet();
      return 4711;
    }

    abstract void test() throws Exception;

    void likeRefreshImmediately() {
      assertThat(cache.containsKey(1)).isTrue();
      assertThat(count.get()).isEqualTo(0);
      sem.release();
      await("loader called", () -> count.get() == 1);
      await("load complete, since 2.8", () -> cache.peek(1) == 4711);
      try {
        assertThat(getInfo().getSize()).isEqualTo(1);
      } catch (AssertionError e) {
        if (ticks() < (startTime + LONG_DELTA)) {
          throw e;
        }
      }
    }
  }

  /**
   * Refreshing cache, expire with no cache => item not visible any more, loader not called
   */
  @Test
  public void manualExpire_refresh_NOW_gone() {
    new ManualExpireFixture() {
      @Override
      void test() {
        cache.put(1, 2);
        cache.expireAt(1, NOW);
        assertThat(cache.containsKey(1)).isFalse();
        assertThat(count.get()).isEqualTo(0);
        assertThat(getInfo().getSize()).isEqualTo(0);
      }
    }.test();
  }

  /**
   * Check that we don't get into trouble if value collides with internal state number
   * range.
   */
  @Test
  public void  manualExpire_refresh_Non0_gone() throws Exception {
    final long timeValuePotentiallyCollidingWithInternalStates = 7;
    new ManualExpireFixture() {
      @Override
      void test() throws Exception {
        cache.put(1, 2);
        sem.acquire();
        cache.expireAt(1, timeValuePotentiallyCollidingWithInternalStates);
        likeRefreshImmediately();
      }
    }.test();
  }

  /**
   * Triggers refresh. Item stays visible in the cache during loading.
   * After load is complete it is invisible but stays in the cache.
   */
  @Test
  public void manualExpire_NOWish_refreshImmediately() throws Exception {
    new ManualExpireFixture() {
      @Override
      void test() throws Exception {
        cache.put(1, 2);
        sem.acquire();
        cache.mutate(1, entry -> entry.setExpiryTime(1234));
        likeRefreshImmediately();
      }
    }.test();
  }

  /**
   * Refresh by manual expiry trigger. Use async loader and check that no more than one thread is
   * needed to execute in parallel.
   */
  @Test
  public void manualExpire_REFRESH_refreshImmediately_async() throws Exception {
    new ManualExpireFixture() {
      @Override
      protected void addLoader(Cache2kBuilder<Integer, Integer> b) {
        b.loader((key, context, callback) -> {
          Executor executor = context.getLoaderExecutor();
          executor.execute(() -> {
            try {
              callback.onLoadSuccess(waitForSemaphoreAndLoad());
            } catch (Exception ex) {
              callback.onLoadFailure(ex);
            }
          });
        });
      }

      @Override
      void test() throws Exception {
        cache.put(1, 2);
        sem.acquire();
        cache.expireAt(1, ExpiryTimeValues.REFRESH);
        likeRefreshImmediately();
      }
    }.test();
  }

  @Test
  public void manualExpire_refresh_pastTime() throws Exception {
    new ManualExpireFixture() {
      @Override
      void test() throws Exception {
        cache.put(1, 2);
        sem.acquire();
        cache.expireAt(1, 12345);
        likeRefreshImmediately();
      }
    }.test();
  }

  /**
   * Leads to a special case using {@link org.cache2k.core.Entry#EXPIRED_REFRESH_PENDING}
   */
  @Test
  public void manualExpire_refreshAhead_sharp() {
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(LONG_DELTA, MILLISECONDS)
      .refreshAhead(true)
      .keepDataAfterExpired(false)
      .loader(key -> 4711)
      .build();
    c.put(1, 2);
    c.expireAt(1, -ticks());
    assertThat(c.containsKey(1)).isFalse();
    await(() -> getInfo().getRefreshCount() > 0);
  }

  @Test
  public void manualExpire_sharp() {
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(LONG_DELTA, MILLISECONDS)
      .loader(key -> 4711)
      .build();
    c.put(1, 2);
    c.expireAt(1, -ticks());
    assertThat(c.containsKey(1)).isFalse();
  }

  @Test
  public void manualExpire_refreshAhead_expireAt_NOW_gone() {
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(LONG_DELTA, MILLISECONDS)
      .refreshAhead(true)
      .keepDataAfterExpired(false)
      .loader(key -> 4711)
      .build();
    within(LONG_DELTA).perform(() -> {
      c.put(1, 2);
      c.expireAt(1, -ticks());
    }).expectMaybe(() -> {
      assertThat(c.containsKey(1)).isFalse();
      await(() -> getInfo().getRefreshCount() > 0);
      assertThat(getInfo().getSize())
        .as("in cache if within delta time")
        .isEqualTo(1);
    });
    c.expireAt(1, NOW);
    assertThat(getInfo().getSize())
      .as("empty after expired immediately")
      .isEqualTo(0);
  }

  @Test
  public void manualExpire_refreshAhead_sharp_refresh() {
    AtomicInteger loadCounter = new AtomicInteger();
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(LONG_DELTA, TimeUnit.MILLISECONDS)
      .refreshAhead(true)
      .keepDataAfterExpired(false)
      .loader(key -> 4711 + loadCounter.getAndIncrement())
      .build();
    c.put(1, 2);
    within(LONG_DELTA)
      .perform(() -> {
        c.expireAt(1, -ticks());
        assertThat(c.containsKey(1))
          .as("entry not visible after expireAt, during refresh and after refresh")
          .isFalse();
        await(() -> getInfo().getRefreshCount() == 1);
      }).expectMaybe(() -> {
        assertThat(getInfo().getSize()).isEqualTo(1);
        assertThat(c.containsKey(1))
          .as("visible, since 2.8")
          .isTrue();
        c.expireAt(1, ETERNAL);
        assertThat(c.containsKey(1))
          .as("Visible, when expiry extended")
          .isTrue();
        assertThat((int) c.peek(1)).isEqualTo(4711);
        assertThat(getInfo().getSize()).isEqualTo(1);
        assertThat(getInfo().getRefreshCount()).isEqualTo(1);
        c.expireAt(1, REFRESH);
        assertThat(getInfo().getSize()).isEqualTo(1);
        await(() -> getInfo().getRefreshCount() == 2);
        assertThat(c.containsKey(1))
          .as("visible, since 2.8")
          .isTrue();
        assertThat((int) c.get(1)).isEqualTo(4712);
      });
  }

  @Test
  public void manualExpiryPut_sharp() {
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(LONG_DELTA, MILLISECONDS)
      .refreshAhead(true)
      .loader(key -> 4711)
      .build();
    c.invoke(1, e -> {
      e.setExpiryTime(ticks() + LONG_DELTA);
      e.setValue(7);
      return null;
    });
    assertThat(c.containsKey(1)).isTrue();
    assertThat(c.peek(1)).isEqualTo((Integer) 7);
  }

  @Test
  public void manualExpireWithEntryProcessor_sharp() {
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(LONG_DELTA, MILLISECONDS)
      .refreshAhead(true)
      .loader(key -> 4711)
      .build();
    c.put(1, 2);
    c.invoke(1, e -> {
      e.setExpiryTime(-ticks());
      return null;
    });
    assertThat(c.containsKey(1)).isFalse();
  }

  /**
   * If null values are not permitted and the expiry policy says immediate
   * expiry for a null value, the load does not store a null value but removes
   * the entry. In this case the get operation variants need to behave like no
   * entry is present.
   */
  @Test
  public void nullFromLoaderRemoves_get_getEntry_getAll() {
    Cache<Integer, Integer> c = cacheNoNullNoLoaderException();
    assertThat(c.get(1)).isNull();
    assertThat(c.getEntry(2)).isNull();
    Map<Integer, Integer> map = c.getAll(asList(3, 4, 5));
    assertThat(map.size()).isEqualTo(0);
  }

  @Test
  public void expiryEventLags() {
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(TestingParameters.MINIMAL_TICK_MILLIS, TimeUnit.MILLISECONDS)
      .timerLag(TestingParameters.MINIMAL_TICK_MILLIS * 2, TimeUnit.MILLISECONDS)
      .build();
    within(TestingParameters.MINIMAL_TICK_MILLIS * 2 - 1)
      .perform(() -> {
        for (int i = 0; i < 3; i++) {
          c.put(i, i);
        }
      }).expectMaybe(() -> {
        assertThat(getCache().asMap().size()).isEqualTo(3);
        sleep(MINIMAL_TICK_MILLIS);
        assertThat(getCache().asMap().size()).isEqualTo(3);
      });
  }

  /** Checks that nothing breaks here. */
  @Test
  public void high_expiry() {
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(MAX_VALUE - 47, MILLISECONDS)
      .build();
    c.put(1, 1);
    assertThat(c.containsKey(1)).isTrue();
  }

  private Cache<Integer, Integer> cacheNoNullNoLoaderException() {
    return builder(Integer.class, Integer.class)
        .expireAfterWrite(LONG_DELTA, TimeUnit.MILLISECONDS)
        .keepDataAfterExpired(false)
        .permitNullValues(false)
        .loader(key -> null)
        .expiryPolicy((key, value, startTime, currentEntry) -> value == null ? ExpiryTimeValues.NOW : ExpiryTimeValues.ETERNAL)
        .build();
  }

  public static class Every1ExceptionLoader implements CacheLoader<Integer, Integer> {

    public final Map<Integer, AtomicInteger> key2count = new HashMap<>();

    protected void maybeThrowException(Integer key, int count) {
      if ((key & (1 << count)) != 0) {
        throw new IllegalStateException("counter=" + count);
      }
    }

    @Override
    public Integer load(Integer key) throws Exception {
      AtomicInteger count;
      synchronized (key2count) {
        count = key2count.get(key);
        if (count == null) {
          count = new AtomicInteger();
          key2count.put(key, count);
        } else {
          count.getAndIncrement();
        }
      }
      maybeThrowException(key, count.get());
      return key;
    }

  }

  private static class ClosingExpiryPolicy<K, V> implements ExpiryPolicy<K, V>, Closeable {

    AtomicBoolean closeCalled = new AtomicBoolean(false);

    @Override
    public void close() {
      closeCalled.set(true);
    }

    @Override
    public long calculateExpiryTime(K key, V value, long startTime, CacheEntry<K, V> currentEntry) {
      return 0;
    }
  }

  @Test
  public void close_called() {
    ClosingExpiryPolicy<Integer, Integer> ep = new ClosingExpiryPolicy();
    builder().expiryPolicy(ep).build().close();
    assertThat(ep.closeCalled.get()).isTrue();
  }

}
