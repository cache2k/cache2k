package org.cache2k.test.core.expiry;

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

import org.assertj.core.api.Assertions;
import org.cache2k.io.AdvancedCacheLoader;
import org.cache2k.io.AsyncCacheLoader;
import org.cache2k.test.core.BasicCacheTest;
import org.cache2k.test.util.TestingBase;
import org.cache2k.test.util.IntCountingCacheSource;
import org.cache2k.core.ResiliencePolicyException;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.io.CacheLoader;
import org.cache2k.io.CacheLoaderException;
import org.cache2k.CacheOperationCompletionListener;
import org.cache2k.io.LoadExceptionInfo;
import org.cache2k.io.ResiliencePolicy;
import org.cache2k.processor.EntryProcessor;
import org.cache2k.processor.MutableCacheEntry;
import org.cache2k.test.core.TestingParameters;
import org.cache2k.test.util.Condition;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.core.api.InternalCache;
import org.cache2k.testing.category.FastTests;
import org.hamcrest.Matchers;
import org.junit.Ignore;
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

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import static org.cache2k.test.core.StaticUtil.*;

/**
 * @author Jens Wilke
 */
@SuppressWarnings({"unchecked", "rawtypes"})
@Category(FastTests.class)
public class ExpiryTest extends TestingBase {

  public static final long LONG_DELTA = TestingParameters.MAX_FINISH_WAIT_MILLIS;

  { enableFastClock(); }

  /** Complains because no real expiry value was set. */
  @Test(expected = IllegalArgumentException.class)
  public void notEternal() {
    builder().eternal(false).build();
  }

  @Test
  public void notEternal_policy() {
    builder().eternal(false).expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
      @Override
      public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                      CacheEntry<Integer, Integer> currentEntry) {
        return 0;
      }
    }).build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void eternalTwice() {
    builder().eternal(true).eternal(false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void eternalTwice_reverse() {
    builder().eternal(false).eternal(true);
  }

  @Test(expected = IllegalArgumentException.class)
  public void eternal_expireAfterWrite() {
    builder().eternal(true).expireAfterWrite(123, TimeUnit.SECONDS);
  }

  @Test(expected = IllegalArgumentException.class)
  public void expireAfterWrite_eternal() {
    builder().expireAfterWrite(123, TimeUnit.SECONDS).eternal(true);
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
    assertEquals("no miss yet", 0, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("one miss yet", 1, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("additional miss", 2, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("additional miss", 3, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("additional miss", 4, g.getLoaderCalledCount());
  }

  @Test
  public void testFetchAlwaysWithVariableExpiry0() {
    IntCountingCacheSource g = new IntCountingCacheSource();
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(g)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                        CacheEntry<Integer, Integer> currentEntry) {
          return 0;
        }
      })
      .expireAfterWrite(0, TimeUnit.SECONDS).build();
    checkAlwaysLoaded(g, c);
  }

  @Test
  public void testFetchAlwaysWithVariableExpiryInPast() {
    IntCountingCacheSource g = new IntCountingCacheSource();
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(g)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                        CacheEntry<Integer, Integer> currentEntry) {
          return 1234567;
        }
      })
      .expireAfterWrite(0, TimeUnit.SECONDS).build();
    checkAlwaysLoaded(g, c);
  }

  @Test
  public void testFetchAlwaysWithVariableExpiryInPastAfterLoad() {
    IntCountingCacheSource g = new IntCountingCacheSource();
    Cache<Integer, Integer> c = cache = Cache2kBuilder.of(Integer.class, Integer.class)
      .loader(g)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                        CacheEntry<Integer, Integer> currentEntry) {
          sleep(3);
          return loadTime + 1;
        }
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
    assertEquals("no miss", 0, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("one miss", 1, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("one miss", 1, g.getLoaderCalledCount());
    assertTrue(((InternalCache) c).getEntryState(1802).contains("nextRefreshTime=ETERNAL"));
    CacheEntry<Integer, Integer> e = c.getEntry(99);
    entryHasException(e);
    assertEquals(RuntimeException.class, e.getException().getClass());
    assertEquals("two miss", 2, g.getLoaderCalledCount());
    assertTrue(((InternalCache) c).getEntryState(99).contains("nextRefreshTime=ETERNAL"));
    e = c.getEntry(99);
    entryHasException(e);
    assertEquals(RuntimeException.class, e.getException().getClass());
    assertEquals("two miss", 2, g.getLoaderCalledCount());
    try {
      c.get(99);
      fail("expect exception");
    } catch (Exception ex) {
      assertThat(ex.toString(), containsString("expiry=ETERNAL"));
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
    assertEquals("no miss", 0, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("one miss", 1, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("one miss", 1, g.getLoaderCalledCount());
    assertTrue(((InternalCache) c).getEntryState(1802).contains("nextRefreshTime=ETERNAL"));
    try {
      c.get(exceptionKey);
      fail("exception expected");
    } catch (CacheLoaderException ex) {
      assertThat("no expiry on exception", ex.toString(),
        Matchers.not(Matchers.containsString(EXPIRY_MARKER)));
      assertTrue(ex.getCause() instanceof RuntimeException);
    }
    assertEquals("miss", 2, g.getLoaderCalledCount());
    assertTrue(((InternalCache) c).getEntryState(exceptionKey).contains("state=4"));
    CacheEntry<Integer, Integer> e = c.getEntry(exceptionKey);
    entryHasException(e);
    assertEquals(RuntimeException.class, e.getException().getClass());
    assertEquals("miss", 3, g.getLoaderCalledCount());
  }

  @Test
  public void loadExceptionEntryNullInExpiryPolicy() {
    final AtomicBoolean success = new AtomicBoolean();
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
      .resiliencePolicy(new EnableExceptionCaching(Long.MAX_VALUE))
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                        CacheEntry<Integer, Integer> currentEntry) {
          if (g.getLoaderCalledCount() == 2) {
            assertNull(currentEntry);
            success.set(true);
          }
          return 0;
        }
      })
      .build();
    try {
      c.get(1);
      fail("exception expected");
    } catch (CacheLoaderException expected) {
    }
    reload(1);
    assertTrue(success.get());
  }

  @Test
  public void dontCallAdvancedLoaderWithExceptionEntry_enableExceptioCaching() {
    dontCallAdvancedLoaderWithExceptionEntry(b -> b.
        resiliencePolicy(new EnableExceptionCaching(Long.MAX_VALUE))
      );
  }

  @Test
  public void dontCallAdvancedLoaderWithExceptionEntry_keepData() {
    dontCallAdvancedLoaderWithExceptionEntry(b -> b.keepDataAfterExpired(true));
  }

  public void dontCallAdvancedLoaderWithExceptionEntry(
    Consumer<Cache2kBuilder<Integer, Integer>> builderAction) {
    final AtomicBoolean success = new AtomicBoolean();
    AtomicInteger loadCount = new AtomicInteger();
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new AdvancedCacheLoader<Integer, Integer>() {
        @Override
        public Integer load(Integer key, long startTime,
                            CacheEntry<Integer, Integer> currentEntry) {
          if (loadCount.incrementAndGet() == 1) {
            assertNull(currentEntry);
            throw new RuntimeException("ouch");
          }
          assertNull("entry is null, if exception happened previously", currentEntry);
          success.set(true);
          return key;
        }
      })
      .apply(builderAction)
      .build();
    try {
      c.get(1);
      fail("exception expected");
    } catch (CacheLoaderException expected) {
    }
    reload(1);
    assertTrue(success.get());
  }

  @Test
  public void dontCallAsyncLoaderWithExceptionEntry_keepData() {
    dontCallAsyncLoaderWithExceptionEntry(b -> b.keepDataAfterExpired(true));
  }

  @Test
  public void dontCallAsyncLoaderWithExceptionEntry_cacheExceptions() {
    dontCallAsyncLoaderWithExceptionEntry(b -> b.
      resiliencePolicy(new EnableExceptionCaching(Long.MAX_VALUE))
    );
  }

  public void dontCallAsyncLoaderWithExceptionEntry(
    Consumer<Cache2kBuilder<Integer, Integer>> builderAction) {
    final AtomicBoolean success = new AtomicBoolean();
    AtomicInteger loadCount = new AtomicInteger();
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new AsyncCacheLoader<Integer, Integer>() {
        @Override
        public void load(Integer key, Context<Integer, Integer> context, Callback<Integer> callback) throws Exception {
          if (loadCount.incrementAndGet() == 1) {
            assertNull(context.getCurrentEntry());
            throw new RuntimeException("ouch");
          }
          assertNull("entry is null, if exception happened previously", context.getCurrentEntry());
          success.set(true);
          callback.onLoadSuccess(key);
        }
      })
      .apply(builderAction)
      .build();
    try {
      c.get(1);
      fail("exception expected");
    } catch (CacheLoaderException expected) {
    }
    reload(1);
    assertTrue(success.get());
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
    assertEquals("no miss", 0, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("one miss", 1, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("one miss", 1, g.getLoaderCalledCount());
    assertTrue(((InternalCache) c).getEntryState(1802).contains("nextRefreshTime=ETERNAL"));
    try {
      c.get(exceptionKey);
      fail("exception expected");
    } catch (CacheLoaderException ex) {
      assertThat("no expiry on exception", ex.toString(),
        Matchers.not(Matchers.containsString(EXPIRY_MARKER)));
      assertTrue(ex.getCause() instanceof RuntimeException);
    }
    assertEquals("miss", 2, g.getLoaderCalledCount());
    CacheEntry<Integer, Integer> e = c.getEntry(exceptionKey);
    entryHasException(e);
    assertEquals(RuntimeException.class, e.getException().getClass());
    assertEquals("miss", 3, g.getLoaderCalledCount());
  }

  @Test
  public void testEternalExceptionsExpire() {
    final IntCountingCacheSource g = new IntCountingCacheSource() {
      @Override
      public Integer load(Integer o) {
        incrementLoadCalledCount();
        if (o == 99) {
          throw new RuntimeException("ouch");
        }
        return o;
      }
    };
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(g)
      .eternal(true)
      .resiliencePolicy(new EnableExceptionCaching(TestingParameters.MINIMAL_TICK_MILLIS))
      .build();
    try {
      c.get(99);
      fail("exception expected");
    } catch (Exception ex) {
      assertTrue(ex instanceof CacheLoaderException);
      assertTrue("expiry on exception", ex.toString().contains(EXPIRY_MARKER));
      assertTrue(ex.getCause() instanceof RuntimeException);
    }
    await(new Condition() {
      @Override
      public boolean check() {
        if (getInfo().getExpiredCount() > 0) {
          c.getEntry(99);
          assertTrue(g.getLoaderCalledCount() > 1);
          return true;
        }
        return false;
      }
    });
  }

  public static class EnableExceptionCaching implements ResiliencePolicy<Integer, Integer> {

    private long retryMillis;

    public EnableExceptionCaching(long retryMillis) {
      this.retryMillis = retryMillis;
    }

    public EnableExceptionCaching() { this(Long.MAX_VALUE); }

    @Override
    public long suppressExceptionUntil(Integer key, LoadExceptionInfo<Integer> loadExceptionInfo,
                                       CacheEntry<Integer, Integer> cachedEntry) {
      return 0;
    }

    @Override
    public long retryLoadAfter(Integer key, LoadExceptionInfo<Integer> loadExceptionInfo) {
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
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(g)
      .eternal(true)
      .resiliencePolicy(new EnableExceptionCaching(TestingParameters.MINIMAL_TICK_MILLIS))
      .build();
    c.put(99, 1);
    int v = c.peek(99);
    assertEquals(1, v);
    within(TestingParameters.MINIMAL_TICK_MILLIS)
      .perform(new Runnable() {
        @Override
        public void run() {
          Assertions.assertThatCode(() -> c.reloadAll(asList(99)).get())
            .isInstanceOf(ExecutionException.class)
            .getCause()
            .isInstanceOf(CacheLoaderException.class);
        }
      })
      .expectMaybe(new Runnable() {
        @Override
        public void run() {
          try {
            c.get(99);
            fail("exception expected");
          } catch (Exception ex) {
            assertTrue(ex instanceof CacheLoaderException);
            assertTrue("expiry on exception", ex.toString().contains(EXPIRY_MARKER));
            assertTrue(ex.getCause() instanceof RuntimeException);
          }
        }
      });
    assertTrue(g.getLoaderCalledCount() > 0);
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
      .expireAfterWrite(Long.MAX_VALUE / 10000, TimeUnit.MILLISECONDS)
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(Integer key,
                                           LoadExceptionInfo<Integer> loadExceptionInfo,
                                           CacheEntry<Integer, Integer> cachedEntry) {
          return 0;
        }

        @Override
        public long retryLoadAfter(Integer key, LoadExceptionInfo<Integer> loadExceptionInfo) {
          return Long.MAX_VALUE;
        }
      })
      .build();
    assertEquals("no miss", 0, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("one miss", 1, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("one miss", 1, g.getLoaderCalledCount());
    CacheEntry<Integer, Integer> e = c.getEntry(99);
    entryHasException(e);
    assertEquals(RuntimeException.class, e.getException().getClass());
    assertEquals("two miss", 2, g.getLoaderCalledCount());
    assertTrue(((InternalCache) c).getEntryState(99).contains("nextRefreshTime=ETERNAL"));
  }

  private static void entryHasException(CacheEntry<Integer, Integer> e) {
    try {
      e.getValue();
      fail("exception expected");
    } catch (CacheLoaderException ex) {
    }
    assertNotNull(e.getException());
  }

  @Test
  public void testImmediateExpire() {
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new IntCountingCacheSource())
      .expireAfterWrite(0, TimeUnit.MILLISECONDS)
      .keepDataAfterExpired(false)
      .sharpExpiry(true)
      .build();
    c.get(1);
    assertEquals(0, getInfo().getSize());
    c.put(3, 3);
    assertEquals(0, getInfo().getSize());
    assertEquals(0, getInfo().getPutCount());
  }

  @Test
  public void testImmediateExpireWithExpiryCalculator() {
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new IntCountingCacheSource())
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                        CacheEntry<Integer, Integer> currentEntry) {
          return 0;
        }
      })
      .keepDataAfterExpired(false)
      .sharpExpiry(true)
      .build();
    c.get(1);
    assertEquals(0, getInfo().getSize());
    c.put(3, 3);
    assertEquals(0, getInfo().getSize());
  }

  @Test
  public void testImmediateExpireAfterUpdate() {
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new IntCountingCacheSource())
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                        CacheEntry<Integer, Integer> currentEntry) {
          if (currentEntry == null) {
            return ETERNAL;
          }
          return NOW;
        }
      })
      .keepDataAfterExpired(false)
      .sharpExpiry(true)
      .build();
    c.get(1);
    assertEquals(1, getInfo().getSize());
    c.put(1, 3);
    assertEquals(0, getInfo().getSize());
    assertEquals(0, getInfo().getPutCount());
  }

  @Test
  public void testImmediateExpireAfterPut() {
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                        CacheEntry<Integer, Integer> currentEntry) {
          if (currentEntry == null) {
            return ETERNAL;
          }
          return NOW;
        }
      })
      .keepDataAfterExpired(false)
      .sharpExpiry(true)
      .build();
    c.put(1, 1);
    assertEquals(1, getInfo().getPutCount());
    assertEquals(1, getInfo().getSize());
    c.put(1, 3);
    assertEquals(0, getInfo().getSize());
    assertEquals(1, getInfo().getPutCount());
  }

  @Test
  public void testResiliencePolicyException() {
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .eternal(true)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                        CacheEntry<Integer, Integer> currentEntry) {
          return 0;
        }
      })
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(Integer key,
                                           LoadExceptionInfo<Integer> exceptionInformation,
                                           CacheEntry<Integer, Integer> cachedEntry) {
          fail("not reached");
          return 0;
        }
        @Override
        public long retryLoadAfter(Integer key, LoadExceptionInfo<Integer> exceptionInformation) {
          throw new NullPointerException("test");
        }
      })
      .loader(new BasicCacheTest.OccasionalExceptionSource())
      .build();
    CacheEntry e = c.getEntry(1);
    assertEquals(ResiliencePolicyException.class, e.getException().getClass());
    assertEquals(NullPointerException.class, e.getException().getCause().getClass());
  }

  @Test
  public void testResiliencePolicyLoadExceptionInformationContent_keep() {
    final long t0 = millis();
    final int initial = -4711;
    final AtomicInteger cacheRetryCount = new AtomicInteger(initial);
    final AtomicInteger suppressRetryCount = new AtomicInteger(initial);
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .eternal(true)
      .keepDataAfterExpired(true)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                        CacheEntry<Integer, Integer> currentEntry) {
          return 0;
        }
      })
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(Integer key, LoadExceptionInfo<Integer> inf,
                                           CacheEntry<Integer, Integer> cachedEntry) {
          assertTrue(inf.getException() instanceof IllegalStateException);
          assertEquals(key, cachedEntry.getValue());
          assertEquals(key, cachedEntry.getKey());
          suppressRetryCount.set(inf.getRetryCount());
          return 0;
        }

        @Override
        public long retryLoadAfter(Integer key, LoadExceptionInfo inf) {
          cacheRetryCount.set(inf.getRetryCount());
          if (inf.getRetryCount() == 0) {
            assertEquals(inf.getSinceTime(), inf.getLoadTime());
          } else {
            assertTrue("2 ms pause, time different", inf.getLoadTime() > inf.getSinceTime());
          }
          assertTrue(inf.getLoadTime() >= t0);
          assertEquals(0, inf.getUntil());
          return 0;
        }
      })
      .loader(new Every1ExceptionLoader())
      .build();
    CacheEntry e = c.getEntry(0xff);
    assertNotNull(e.getException());
    assertEquals(initial, suppressRetryCount.get());
    assertEquals(0, cacheRetryCount.get());
    sleep(2);
    c.getEntry(0xff);
    assertEquals(1, cacheRetryCount.get());
    e = c.getEntry(0x06);
    assertNull(e.getException());
    e = c.getEntry(0x06);
    assertNotNull(e.getException());
    assertEquals(0, suppressRetryCount.get());
    assertEquals(0, cacheRetryCount.get());
    sleep(2);
    e = c.getEntry(0x06);
    assertNotNull(e.getException());
    assertEquals(0, suppressRetryCount.get());
    assertEquals(1, cacheRetryCount.get());
  }

  @Test
  public void testResiliencePolicyLoadExceptionInformationContent_noKeep() {
    final long t0 = millis();
    final int initial = -4711;
    final AtomicInteger cacheRetryCount = new AtomicInteger(initial);
    final AtomicInteger suppressRetryCount = new AtomicInteger(initial);
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .keepDataAfterExpired(false)
      .eternal(true)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                        CacheEntry<Integer, Integer> currentEntry) {
          return 0;
        }
      })
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(Integer key, LoadExceptionInfo<Integer> inf,
                                           CacheEntry<Integer, Integer> cachedEntry) {
          assertTrue(inf.getException() instanceof IllegalStateException);
          assertEquals(key, cachedEntry.getValue());
          assertEquals(key, cachedEntry.getKey());
          suppressRetryCount.set(inf.getRetryCount());
          return 0;
        }

        @Override
        public long retryLoadAfter(Integer key, LoadExceptionInfo inf) {
          cacheRetryCount.set(inf.getRetryCount());
          if (inf.getRetryCount() == 0) {
            assertEquals(inf.getSinceTime(), inf.getLoadTime());
          } else {
            assertTrue("2 ms pause, time different", inf.getLoadTime() > inf.getSinceTime());
          }
          assertTrue(inf.getLoadTime() >= t0);
          assertEquals(0, inf.getUntil());
          return 0;
        }
      })
      .loader(new Every1ExceptionLoader())
      .build();
    CacheEntry e = c.getEntry(0xff);
    assertNotNull(e.getException());
    assertEquals(initial, suppressRetryCount.get());
    assertEquals(0, cacheRetryCount.get());
    sleep(2);
    c.getEntry(0xff);
    assertEquals(0, cacheRetryCount.get());
    e = c.getEntry(0x06);
    assertNull(e.getException());
    e = c.getEntry(0x06);
    assertNotNull(e.getException());
    assertEquals("valid entry expired immediately", initial, suppressRetryCount.get());
    assertEquals(0, cacheRetryCount.get());
    sleep(2);
    e = c.getEntry(0x06);
    assertNotNull(e.getException());
    assertEquals("valid entry expired immediately", initial, suppressRetryCount.get());
    assertEquals(0, cacheRetryCount.get());
  }

  @Test
  public void testResiliencePolicyLoadExceptionCountWhenSuppressed() {
    final int initial = -4711;
    final AtomicInteger cacheRetryCount = new AtomicInteger(initial);
    final AtomicInteger suppressRetryCount = new AtomicInteger(initial);
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .eternal(true)
      .keepDataAfterExpired(true)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                        CacheEntry<Integer, Integer> currentEntry) {
          return 0;
        }
      })
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(Integer key, LoadExceptionInfo<Integer> inf,
                                           CacheEntry<Integer, Integer> cachedEntry) {
          suppressRetryCount.set(inf.getRetryCount());
          return inf.getLoadTime() + 1;
        }

        @Override
        public long retryLoadAfter(Integer key, LoadExceptionInfo<Integer> inf) {
          cacheRetryCount.set(inf.getRetryCount());
          return 0;
        }
      })
      .loader(new BasicCacheTest.AlwaysExceptionSource())
      .build();
    c.put(1, 1);
    assertEquals(initial, suppressRetryCount.get());
    assertEquals(initial, cacheRetryCount.get());
    c.getEntry(1);
    assertEquals(0, suppressRetryCount.get());
    assertEquals(initial, cacheRetryCount.get());
    assertEquals(1, getInfo().getSuppressedExceptionCount());
    loadAndWait(new LoaderRunner() {
      @Override
      public void run(CacheOperationCompletionListener l) {
        c.reloadAll(toIterable(1), l);
      }
    });
    assertEquals(1, suppressRetryCount.get());
    assertEquals(initial, cacheRetryCount.get());
    loadAndWait(new LoaderRunner() {
      @Override
      public void run(CacheOperationCompletionListener l) {
        c.reloadAll(toIterable(1), l);
      }
    });
    assertEquals(2, suppressRetryCount.get());
    assertEquals(initial, cacheRetryCount.get());
  }

  @Test
  public void testPolicyNotCalledIfExpire0() {
    final AtomicLong policyCalled = new AtomicLong();
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new BasicCacheTest.AlwaysExceptionSource())
      .expireAfterWrite(0, TimeUnit.SECONDS)
      .keepDataAfterExpired(true)
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(Integer key,
                                           LoadExceptionInfo<Integer> exceptionInformation,
                                           CacheEntry<Integer, Integer> cachedEntry) {
          policyCalled.incrementAndGet();
          return 1000;
        }

        @Override
        public long retryLoadAfter(Integer key, LoadExceptionInfo<Integer> exceptionInformation) {
          policyCalled.incrementAndGet();
          return 0;
        }
      })
      .build();
    c.put(1, 1);
    c.getEntry(1);
    assertEquals(0, policyCalled.get());
    assertEquals(0, getInfo().getSuppressedExceptionCount());
  }

  @Test
  public void testImmediateExpireButKeepDataDoesSuppress() {
    final AtomicLong policyCalled = new AtomicLong();
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new BasicCacheTest.AlwaysExceptionSource())
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                        CacheEntry<Integer, Integer> currentEntry) {
          return 0;
        }
      })
      .keepDataAfterExpired(true)
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(Integer key,
                                           LoadExceptionInfo<Integer> exceptionInformation,
                                           CacheEntry<Integer, Integer> cachedEntry) {
          policyCalled.incrementAndGet();
          return exceptionInformation.getLoadTime() + 1;
        }

        @Override
        public long retryLoadAfter(Integer key, LoadExceptionInfo<Integer> exceptionInformation) {
          policyCalled.incrementAndGet();
          return 0;
        }
      })
      .build();
    c.put(1, 1);
    c.getEntry(1);
    assertEquals(1, policyCalled.get());
    assertEquals(1, getInfo().getSuppressedExceptionCount());
  }

  @Test
  public void testResiliencePolicyLoadExceptionInformationCounterReset_keep() {
    final int initial = -4711;
    final AtomicInteger cacheRetryCount = new AtomicInteger(initial);
    final AtomicInteger suppressRetryCount = new AtomicInteger(initial);
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .eternal(true)
      .keepDataAfterExpired(true)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                        CacheEntry<Integer, Integer> currentEntry) {
          return NOW;
        }
      })
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(Integer key, LoadExceptionInfo<Integer> inf,
                                           CacheEntry<Integer, Integer> cachedEntry) {
          suppressRetryCount.set(inf.getRetryCount());
          return ETERNAL;
        }

        @Override
        public long retryLoadAfter(Integer key, LoadExceptionInfo<Integer> inf) {
          cacheRetryCount.set(inf.getRetryCount());
          return NOW;
        }
      })
      .loader(new Every1ExceptionLoader())
      .build();
    int key = 2 + 8;
    CacheEntry<Integer, Integer> e = c.getEntry(key);
    assertNull(e.getException());
    e = c.getEntry(key);
    assertNull("suppressed", e.getException());
    assertEquals(0, suppressRetryCount.get());
    assertEquals(-4711, cacheRetryCount.get());
    e = c.getEntry(key);
    assertNull("still suppressed", e.getException());
    assertEquals("no additional loader call", 0, suppressRetryCount.get());
    assertEquals(-4711, cacheRetryCount.get());
    c.put(key, 123);
    e = c.getEntry(key);
    assertNull(e.getException());
    assertEquals(0, suppressRetryCount.get());
    e = c.getEntry(key);
    assertNull(e.getException());
    assertEquals(0, suppressRetryCount.get());
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
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                        CacheEntry<Integer, Integer> currentEntry) {
          return 0;
        }
      })
      .loader(countingLoader)
      .build();
    c.get(1);
    c.get(2);
    c.get(3);
    await("All expired", new Condition() {
      @Override
      public boolean check() {
        return getInfo().getExpiredCount() >= count;
      }
    });
    assertEquals(0, getInfo().getRefreshCount() + getInfo().getRefreshRejectedCount());
    assertEquals(count, countingLoader.getLoaderCalledCount());
    assertEquals(count, getInfo().getExpiredCount());
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
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                        CacheEntry<Integer, Integer> currentEntry) {
          return 0;
        }
      })
      .loader(countingLoader)
      .keepDataAfterExpired(false)
      .build();
    c.get(1);
    c.get(2);
    c.get(3);
    await("All expired", new Condition() {
      @Override
      public boolean check() {
        return getInfo().getExpiredCount() >= count;
      }
    });
    assertEquals(0,
      getInfo().getRefreshCount() + getInfo().getRefreshRejectedCount());
    assertEquals(count, countingLoader.getLoaderCalledCount());
    assertEquals(count, getInfo().getExpiredCount());
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
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                        CacheEntry<Integer, Integer> currentEntry) {
          return 0;
        }
      })
      .loader(countingLoader)
      .build();
    c.get(1);
    assertFalse(c.containsKey(1));
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
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                        CacheEntry<Integer, Integer> currentEntry) {
          return loadTime;
        }
      })
      .loader(new IdentIntSource())
      .build();
    c.get(1);
    c.get(2);
    c.get(3);
    await("All refreshed", new Condition() {
      @Override
      public boolean check() {
        return getInfo().getRefreshCount() + getInfo().getRefreshRejectedCount() >= count;
      }
    });
    await("All expired", new Condition() {
      @Override
      public boolean check() {
        return getInfo().getExpiredCount() >= count;
      }
    });
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
    assertFalse(c.containsKey(1));
  }

  @Test
  public void manualExpire_aboutNow() {
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .expireAfterWrite(LONG_DELTA, TimeUnit.MILLISECONDS)
      .build();
    c.put(1, 2);
    assertTrue(c.containsKey(1));
    c.expireAt(1, millis());
    assertFalse(c.containsKey(1));
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
      startTime = getClock().millis();
    }

    protected void addLoader(Cache2kBuilder<Integer, Integer> b) {
      b.loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(Integer key) throws Exception {
          return waitForSemaphoreAndLoad();
        }
      });
    }

    protected Integer waitForSemaphoreAndLoad() throws Exception {
      sem.acquire(); sem.release();
      count.incrementAndGet();
      return 4711;
    }

    abstract void test() throws Exception;

    void likeRefreshImmediately() {
      assertTrue(cache.containsKey(1));
      assertEquals(0, count.get());
      sem.release();
      await("loader called", new Condition() {
        @Override
        public boolean check() {
          return count.get() == 1;
        }
      });
      await("load complete", new Condition() {
        @Override
        public boolean check() {
          return !cache.containsKey(1);
        }
      });
      try {
        assertEquals(1, getInfo().getSize());
      } catch (AssertionError e) {
        if (millis() < (startTime + LONG_DELTA)) {
          throw e;
        }
      }
    }
  }

  /**
   * Refreshing cache, expire with no cache => item not visible any more, loader not called
   */
  @Test
  public void manualExpire_refresh_now_gone() {
    new ManualExpireFixture() {
      @Override
      void test() {
        cache.put(1, 2);
        cache.expireAt(1, ExpiryTimeValues.NOW);
        assertFalse(cache.containsKey(1));
        assertEquals(0, count.get());
        assertEquals(0, getInfo().getSize());
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
  public void manualExpire_refresh_refreshImmediately() throws Exception {
    new ManualExpireFixture() {
      @Override
      void test() throws Exception {
        cache.put(1, 2);
        sem.acquire();
        cache.expireAt(1, ExpiryTimeValues.REFRESH);
        likeRefreshImmediately();
      }
    }.test();
  }

  /**
   * Refresh by manual expiry trigger. Use async loader and check that no more than one thread is
   * needed to execute in parallel.
   */
  @Test
  public void manualExpire_refresh_refreshImmediately_async() throws Exception {
    new ManualExpireFixture() {
      @Override
      protected void addLoader(Cache2kBuilder<Integer, Integer> b) {
        b.loader(new AsyncCacheLoader<Integer, Integer>() {
          @Override
          public void load(Integer key, Context<Integer, Integer> context,
                           final Callback<Integer> callback) {
            Executor executor = context.getLoaderExecutor();
              executor.execute(new Runnable() {
              @Override
              public void run() {
                try {
                  callback.onLoadSuccess(waitForSemaphoreAndLoad());
                } catch (Exception ex) {
                  callback.onLoadFailure(ex);
                }
              }
            });
          }
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
      .expireAfterWrite(LONG_DELTA, TimeUnit.MILLISECONDS)
      .refreshAhead(true)
      .keepDataAfterExpired(false)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(Integer key) {
          return 4711;
        }
      })
      .build();
    c.put(1, 2);
    c.expireAt(1, -millis());
    assertFalse(c.containsKey(1));
    await(new Condition() {
      @Override
      public boolean check() {
        return getInfo().getRefreshCount() > 0;
      }
    });
  }

  @Test
  public void manualExpire_sharp() {
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(LONG_DELTA, TimeUnit.MILLISECONDS)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(Integer key) {
          return 4711;
        }
      })
      .build();
    c.put(1, 2);
    c.expireAt(1, -millis());
    assertFalse(c.containsKey(1));
  }

  @Test
  public void manualExpire_refreshAhead_sharp_expireAt0_gone() {
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(LONG_DELTA, TimeUnit.MILLISECONDS)
      .refreshAhead(true)
      .keepDataAfterExpired(false)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(Integer key) {
          return 4711;
        }
      })
      .build();
    within(LONG_DELTA).perform(new Runnable() {
      @Override
      public void run() {
        c.put(1, 2);
        c.expireAt(1, -millis());
      }
    }).expectMaybe(new Runnable() {
      @Override
      public void run() {
        assertFalse(c.containsKey(1));
        await(new Condition() {
          @Override
          public boolean check() {
            return getInfo().getRefreshCount() > 0;
          }
        });
        assertEquals("in cache if within delta time", 1, getInfo().getSize());
      }
    });
    c.expireAt(1, ExpiryTimeValues.NOW);
    assertEquals("empty after expired immediately", 0, getInfo().getSize());
  }

  @Test
  public void manualExpire_refreshAhead_sharp_refresh() {
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(LONG_DELTA, TimeUnit.MILLISECONDS)
      .refreshAhead(true)
      .keepDataAfterExpired(false)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(Integer key) {
          return 4711;
        }
      })
      .build();
    c.put(1, 2);
    within(LONG_DELTA)
      .perform(new Runnable() {
        @Override
        public void run() {
          c.expireAt(1, -millis());
          assertFalse(
            "entry not visible after expireAt, during refresh and after refresh",
            c.containsKey(1));
          await(new Condition() {
            @Override
            public boolean check() {
              return getInfo().getRefreshCount() == 1;
            }
          });
        }
      }).expectMaybe(new Runnable() {
        @Override
        public void run() {
          assertEquals(1, getInfo().getSize());
          assertFalse("Still invisible", c.containsKey(1));
          c.expireAt(1, ExpiryTimeValues.ETERNAL);
          assertFalse("Keeps invisible, when expiry extended", c.containsKey(1));
          assertEquals(1, getInfo().getSize());
          assertEquals(1, getInfo().getRefreshCount());
          c.expireAt(1, ExpiryTimeValues.REFRESH);
          assertEquals(1, getInfo().getSize());
        }
      }).concludesMaybe(new Runnable() {
      @Override
      public void run() {
        await(new Condition() {
          @Override
          public boolean check() {
            return getInfo().getRefreshCount() == 2;
          }
        });
      }
    });
  }

  @Test
  public void manualExpiryPut_sharp() {
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(LONG_DELTA, TimeUnit.MILLISECONDS)
      .refreshAhead(true)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(Integer key) {
          return 4711;
        }
      })
      .build();
    c.invoke(1, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        e.setExpiryTime(millis() + LONG_DELTA);
        e.setValue(7);
        return null;
      }
    });
    assertTrue(c.containsKey(1));
    assertEquals((Integer) 7, c.peek(1));
  }

  @Test
  public void manualExpireWithEntryProcessor_sharp() {
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(LONG_DELTA, TimeUnit.MILLISECONDS)
      .refreshAhead(true)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(Integer key) {
          return 4711;
        }
      })
      .build();
    c.put(1, 2);
    c.invoke(1, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(MutableCacheEntry<Integer, Integer> e) {
        e.setExpiryTime(-millis());
        return null;
      }
    });
    assertFalse(c.containsKey(1));
  }

  @Test
  public void rejectNull_skipLoaderException_null_getAll_empty() {
    Cache<Integer, Integer> c = cacheNoNullNoLoaderException();
    Map<Integer, Integer> map = c.getAll(toIterable(1, 2, 3));
    assertEquals(0, map.size());
  }

  @Test
  public void rejectNull_skipLoaderException_null_entry() {
    Cache<Integer, Integer> c = cacheNoNullNoLoaderException();
    CacheEntry<?, ?> e = c.getEntry(1);
    assertNull(e);
  }

  @Test
  public void expiryEventLags() {
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(TestingParameters.MINIMAL_TICK_MILLIS, TimeUnit.MILLISECONDS)
      .timerLag(TestingParameters.MINIMAL_TICK_MILLIS * 2, TimeUnit.MILLISECONDS)
      .build();
    within(TestingParameters.MINIMAL_TICK_MILLIS * 2 - 1)
      .perform(new Runnable() {
      @Override
      public void run() {
        for (int i = 0; i < 3; i++) {
          c.put(i, i);
        }
      }
    }).expectMaybe(new Runnable() {
      @Override
      public void run() {
        assertEquals(3, getCache().asMap().size());
        sleep(TestingParameters.MINIMAL_TICK_MILLIS);
        assertEquals(3, getCache().asMap().size());
      }
    });
  }

  /** Checks that nothing breaks here. */
  @Test
  public void high_expiry() {
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(Long.MAX_VALUE - 47, TimeUnit.MILLISECONDS)
      .build();
    c.put(1, 1);
    assertTrue(c.containsKey(1));
  }

  private Cache<Integer, Integer> cacheNoNullNoLoaderException() {
    return builder(Integer.class, Integer.class)
        .expireAfterWrite(LONG_DELTA, TimeUnit.MILLISECONDS)
        .keepDataAfterExpired(false)
        .permitNullValues(false)
        .loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(Integer key) {
            return null;
          }
        })
        .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
          @Override
          public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                          CacheEntry<Integer, Integer> currentEntry) {
            return value == null ? NOW : ETERNAL;
          }
        })
        .build();
  }

  public static class Every1ExceptionLoader implements CacheLoader<Integer, Integer> {

    public final Map<Integer, AtomicInteger> key2count = new HashMap<Integer, AtomicInteger>();

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
    public long calculateExpiryTime(K key, V value, long loadTime, CacheEntry<K, V> currentEntry) {
      return 0;
    }
  }

  @Test
  public void close_called() {
    ClosingExpiryPolicy<Integer, Integer> ep = new ClosingExpiryPolicy();
    builder().expiryPolicy(ep).build().close();
    assertTrue(ep.closeCalled.get());
  }

}
