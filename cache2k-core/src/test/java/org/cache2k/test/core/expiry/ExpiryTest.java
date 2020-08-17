package org.cache2k.test.core.expiry;

/*
 * #%L
 * cache2k implementation
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

import org.cache2k.integration.AsyncCacheLoader;
import org.cache2k.test.core.BasicCacheTest;
import org.cache2k.test.util.TestingBase;
import org.cache2k.test.util.IntCountingCacheSource;
import org.cache2k.core.ResiliencePolicyException;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.integration.CacheLoader;
import org.cache2k.integration.CacheLoaderException;
import org.cache2k.CacheOperationCompletionListener;
import org.cache2k.integration.ExceptionInformation;
import org.cache2k.integration.ResiliencePolicy;
import org.cache2k.processor.EntryProcessor;
import org.cache2k.processor.MutableCacheEntry;
import org.cache2k.test.core.TestingParameters;
import org.cache2k.test.util.Condition;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.core.InternalCache;
import org.cache2k.testing.category.FastTests;
import org.cache2k.test.util.TimeBox;
import org.hamcrest.Matchers;
import org.junit.experimental.categories.Category;
import org.junit.Test;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import static org.cache2k.test.core.StaticUtil.*;

/**
 * @author Jens Wilke
 */
@SuppressWarnings("unchecked")
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
      public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
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
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(g)
      .expireAfterWrite(0, TimeUnit.SECONDS).build();
    checkAlwaysLoaded(g, c);
  }

  private void checkAlwaysLoaded(final IntCountingCacheSource g, final Cache<Integer, Integer> c) {
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
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(g)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
          return 0;
        }
      })
      .expireAfterWrite(0, TimeUnit.SECONDS).build();
    checkAlwaysLoaded(g, c);
  }

  @Test
  public void testFetchAlwaysWithVariableExpiryInPast() {
    IntCountingCacheSource g = new IntCountingCacheSource();
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(g)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
          return 1234567;
        }
      })
      .expireAfterWrite(0, TimeUnit.SECONDS).build();
    checkAlwaysLoaded(g, c);
  }

  @Test
  public void testFetchAlwaysWithVariableExpiryInPastAfterLoad() {
    IntCountingCacheSource g = new IntCountingCacheSource();
    final Cache<Integer, Integer> c = cache = Cache2kBuilder.of(Integer.class, Integer.class)
      .loader(g)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
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
      public Integer load(final Integer o) {
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
      .retryInterval(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
      .build();
    assertEquals("no miss", 0, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("one miss", 1, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("one miss", 1, g.getLoaderCalledCount());
    assertTrue(((InternalCache) c).getEntryState(1802).contains("nextRefreshTime=ETERNAL"));
    CacheEntry<Integer,Integer> e = c.getEntry(99);
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

  final String EXPIRY_MARKER = "expiry=";

  /**
   * Switching to eternal means exceptions expire immediately.
   */
  @Test
  public void testEternal_keepData() throws Exception {
    final int EXCEPTION_KEY = 99;
    IntCountingCacheSource g = new IntCountingCacheSource() {
      @Override
      public Integer load(final Integer o) {
        incrementLoadCalledCount();
        if (o == EXCEPTION_KEY) {
          throw new RuntimeException("ouch");
        }
        return o;
      }
    };
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
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
      Integer obj = c.get(EXCEPTION_KEY);
      fail("exception expected");
    } catch (CacheLoaderException ex) {
      assertThat("no expiry on exception", ex.toString(),
        Matchers.not(Matchers.containsString(EXPIRY_MARKER)));
      assertTrue(ex.getCause() instanceof RuntimeException);
    }
    assertEquals("miss", 2, g.getLoaderCalledCount());
    assertTrue(((InternalCache) c).getEntryState(EXCEPTION_KEY).contains("state=4"));
    CacheEntry<Integer, Integer> e = c.getEntry(EXCEPTION_KEY);
    entryHasException(e);
    assertEquals(RuntimeException.class, e.getException().getClass());
    assertEquals("miss", 3, g.getLoaderCalledCount());
  }

  /**
   * Switching to eternal means exceptions expire immediately.
   */
  @Test
  public void testEternal_noKeep() throws Exception {
    final int EXCEPTION_KEY = 99;
    IntCountingCacheSource g = new IntCountingCacheSource() {
      @Override
      public Integer load(final Integer o) {
        incrementLoadCalledCount();
        if (o == EXCEPTION_KEY) {
          throw new RuntimeException("ouch");
        }
        return o;
      }
    };
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
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
      Integer obj = c.get(EXCEPTION_KEY);
      fail("exception expected");
    } catch (CacheLoaderException ex) {
      assertThat("no expiry on exception", ex.toString(),
        Matchers.not(Matchers.containsString(EXPIRY_MARKER)));
      assertTrue(ex.getCause() instanceof RuntimeException);
    }
    assertEquals("miss", 2, g.getLoaderCalledCount());
    CacheEntry<Integer, Integer> e = c.getEntry(EXCEPTION_KEY);
    entryHasException(e);
    assertEquals(RuntimeException.class, e.getException().getClass());
    assertEquals("miss", 3, g.getLoaderCalledCount());
  }

  @Test
  public void testEternalExceptionsExpire() {
    final IntCountingCacheSource g = new IntCountingCacheSource() {
      @Override
      public Integer load(final Integer o) {
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
      .retryInterval(TestingParameters.MINIMAL_TICK_MILLIS, TimeUnit.MILLISECONDS)
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
      public boolean check() throws Exception {
        if (getInfo().getExpiredCount() > 0) {
          c.getEntry(99);
          assertTrue(g.getLoaderCalledCount() > 1);
          return true;
        }
        return false;
      }
    });
  }

  /**
   * Don't suppress exceptions eternally if resilience policy is enabled by specifying
   * a retry interval.
   */
  @Test
  public void testEternalExceptionsExpireNoSuppress() {
    final IntCountingCacheSource g = new IntCountingCacheSource() {
      @Override
      public Integer load(final Integer o) {
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
      .retryInterval(TestingParameters.MINIMAL_TICK_MILLIS, TimeUnit.MILLISECONDS)
      .build();
    c.put(99, 1);
    int v = c.peek(99);
    assertEquals(1, v);
    TimeBox.millis(TestingParameters.MINIMAL_TICK_MILLIS)
      .work(new Runnable() {
        @Override
        public void run() {
          loadAndWait(new LoaderRunner() {
            @Override
            public void run(final CacheOperationCompletionListener l) {
              c.reloadAll(toIterable(99), l);
            }
          });
        }
      })
      .check(new Runnable() {
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
      public Integer load(final Integer o) {
        incrementLoadCalledCount();
        if (o == 99) {
          throw new RuntimeException("ouch");
        }
        return o;
      }
    };
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(g)
      .expireAfterWrite(Long.MAX_VALUE / 2, TimeUnit.MILLISECONDS)
      .retryInterval(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
      .build();
    assertEquals("no miss", 0, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("one miss", 1, g.getLoaderCalledCount());
    c.get(1802);
    assertEquals("one miss", 1, g.getLoaderCalledCount());
    CacheEntry<Integer,Integer> e = c.getEntry(99);
    entryHasException(e);
    assertEquals(RuntimeException.class, e.getException().getClass());
    assertEquals("two miss", 2, g.getLoaderCalledCount());
    assertTrue(((InternalCache) c).getEntryState(99).contains("nextRefreshTime=ETERNAL"));
  }

  private static void entryHasException(final CacheEntry<Integer, Integer> e) {
    try {
      e.getValue();
      fail("exception expected");
    } catch (CacheLoaderException ex) {
    }
    assertNotNull(e.getException());
  }

  @Test
  public void testImmediateExpire() {
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new IntCountingCacheSource())
      .expireAfterWrite(0, TimeUnit.MILLISECONDS)
      .keepDataAfterExpired(false)
      .sharpExpiry(true)
      .build();
    c.get(1);
    assertEquals(0, getInfo().getSize());
    c.put(3,3);
    assertEquals(0, getInfo().getSize());
    assertEquals(0, getInfo().getPutCount());
  }

  @Test
  public void testImmediateExpireWithExpiryCalculator() {
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new IntCountingCacheSource())
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
          return 0;
        }
      })
      .keepDataAfterExpired(false)
      .sharpExpiry(true)
      .build();
    c.get(1);
    assertEquals(0, getInfo().getSize());
    c.put(3,3);
    assertEquals(0, getInfo().getSize());
  }

  @Test
  public void testImmediateExpireAfterUpdate() {
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new IntCountingCacheSource())
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
          if (oldEntry == null) {
            return ExpiryPolicy.ETERNAL;
          }
          return ExpiryPolicy.NO_CACHE;
        }
      })
      .keepDataAfterExpired(false)
      .sharpExpiry(true)
      .build();
    c.get(1);
    assertEquals(1, getInfo().getSize());
    c.put(1,3);
    assertEquals(0, getInfo().getSize());
    assertEquals(0, getInfo().getPutCount());
  }

  @Test
  public void testImmediateExpireAfterPut() {
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
          if (oldEntry == null) {
            return ExpiryPolicy.ETERNAL;
          }
          return ExpiryPolicy.NO_CACHE;
        }
      })
      .keepDataAfterExpired(false)
      .sharpExpiry(true)
      .build();
    c.put(1, 1);
    assertEquals(1, getInfo().getPutCount());
    assertEquals(1, getInfo().getSize());
    c.put(1,3);
    assertEquals(0, getInfo().getSize());
    assertEquals(1, getInfo().getPutCount());
  }

  @Test
  public void testResiliencePolicyException() {
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .eternal(true)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
          return 0;
        }
      })
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(final Integer key, final ExceptionInformation exceptionInformation, final CacheEntry<Integer, Integer> cachedContent) {
          fail("not reached");
          return 0;
        }
        @Override
        public long retryLoadAfter(final Integer key, final ExceptionInformation exceptionInformation) {
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
  public void testResiliencePolicyLoadExceptionInformationContent_keep() throws Exception {
    final long t0 = millis();
    final int _INITIAL = - 4711;
    final AtomicInteger _cacheRetryCount = new AtomicInteger(_INITIAL);
    final AtomicInteger _suppressRetryCount = new AtomicInteger(_INITIAL);
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .eternal(true)
      .keepDataAfterExpired(true)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
          return 0;
        }
      })
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(final Integer key, final ExceptionInformation inf, final CacheEntry<Integer, Integer> cachedContent) {
          assertTrue(inf.getException() instanceof IllegalStateException);
          assertEquals(key, cachedContent.getValue());
          assertEquals(key, cachedContent.getKey());
          _suppressRetryCount.set(inf.getRetryCount());
          return 0;
        }

        @Override
        public long retryLoadAfter(final Integer key, final ExceptionInformation inf) {
          _cacheRetryCount.set(inf.getRetryCount());
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
    assertEquals(_INITIAL, _suppressRetryCount.get());
    assertEquals(0, _cacheRetryCount.get());
    sleep(2);
    c.getEntry(0xff);
    assertEquals(1, _cacheRetryCount.get());
    e = c.getEntry(0x06);
    assertNull(e.getException());
    e = c.getEntry(0x06);
    assertNotNull(e.getException());
    assertEquals(0, _suppressRetryCount.get());
    assertEquals(0, _cacheRetryCount.get());
    sleep(2);
    e = c.getEntry(0x06);
    assertNotNull(e.getException());
    assertEquals(0, _suppressRetryCount.get());
    assertEquals(1, _cacheRetryCount.get());
  }

  @Test
  public void testResiliencePolicyLoadExceptionInformationContent_noKeep() throws Exception {
    final long t0 = millis();
    final int _INITIAL = - 4711;
    final AtomicInteger _cacheRetryCount = new AtomicInteger(_INITIAL);
    final AtomicInteger _suppressRetryCount = new AtomicInteger(_INITIAL);
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .keepDataAfterExpired(false)
      .eternal(true)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
          return 0;
        }
      })
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(final Integer key, final ExceptionInformation inf, final CacheEntry<Integer, Integer> cachedContent) {
          assertTrue(inf.getException() instanceof IllegalStateException);
          assertEquals(key, cachedContent.getValue());
          assertEquals(key, cachedContent.getKey());
          _suppressRetryCount.set(inf.getRetryCount());
          return 0;
        }

        @Override
        public long retryLoadAfter(final Integer key, final ExceptionInformation inf) {
          _cacheRetryCount.set(inf.getRetryCount());
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
    assertEquals(_INITIAL, _suppressRetryCount.get());
    assertEquals(0, _cacheRetryCount.get());
    sleep(2);
    c.getEntry(0xff);
    assertEquals(0, _cacheRetryCount.get());
    e = c.getEntry(0x06);
    assertNull(e.getException());
    e = c.getEntry(0x06);
    assertNotNull(e.getException());
    assertEquals("valid entry expired immediately", _INITIAL, _suppressRetryCount.get());
    assertEquals(0, _cacheRetryCount.get());
    sleep(2);
    e = c.getEntry(0x06);
    assertNotNull(e.getException());
    assertEquals("valid entry expired immediately", _INITIAL, _suppressRetryCount.get());
    assertEquals(0, _cacheRetryCount.get());
  }

  @Test
  public void testResiliencePolicyLoadExceptionCountWhenSuppressed() throws Exception {
    final int _INITIAL = - 4711;
    final AtomicInteger _cacheRetryCount = new AtomicInteger(_INITIAL);
    final AtomicInteger _suppressRetryCount = new AtomicInteger(_INITIAL);
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .eternal(true)
      .keepDataAfterExpired(true)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
          return 0;
        }
      })
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(final Integer key, final ExceptionInformation inf, final CacheEntry<Integer, Integer> cachedContent) {
          _suppressRetryCount.set(inf.getRetryCount());
          return inf.getLoadTime() + 1;
        }

        @Override
        public long retryLoadAfter(final Integer key, final ExceptionInformation inf) {
          _cacheRetryCount.set(inf.getRetryCount());
          return 0;
        }
      })
      .loader(new BasicCacheTest.AlwaysExceptionSource())
      .build();
    c.put(1,1);
    assertEquals(_INITIAL, _suppressRetryCount.get());
    assertEquals(_INITIAL, _cacheRetryCount.get());
    c.getEntry(1);
    assertEquals(0, _suppressRetryCount.get());
    assertEquals(_INITIAL, _cacheRetryCount.get());
    assertEquals(1, getInfo().getSuppressedExceptionCount());
    loadAndWait(new LoaderRunner() {
      @Override
      public void run(final CacheOperationCompletionListener l) {
        c.reloadAll(toIterable(1), l);
      }
    });
    assertEquals(1, _suppressRetryCount.get());
    assertEquals(_INITIAL, _cacheRetryCount.get());
    loadAndWait(new LoaderRunner() {
      @Override
      public void run(final CacheOperationCompletionListener l) {
        c.reloadAll(toIterable(1), l);
      }
    });
    assertEquals(2, _suppressRetryCount.get());
    assertEquals(_INITIAL, _cacheRetryCount.get());
  }

  @Test
  public void testPolicyNotCalledIfExpire0() {
    final AtomicLong _policyCalled = new AtomicLong();
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new BasicCacheTest.AlwaysExceptionSource())
      .expireAfterWrite(0, TimeUnit.SECONDS)
      .keepDataAfterExpired(true)
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(final Integer key, final ExceptionInformation exceptionInformation, final CacheEntry<Integer, Integer> cachedContent) {
          _policyCalled.incrementAndGet();
          return 1000;
        }

        @Override
        public long retryLoadAfter(final Integer key, final ExceptionInformation exceptionInformation) {
          _policyCalled.incrementAndGet();
          return 0;
        }
      })
      .build();
    c.put(1, 1);
    c.getEntry(1);
    assertEquals(0, _policyCalled.get());
    assertEquals(0, getInfo().getSuppressedExceptionCount());
  }

  @Test
  public void testImmediateExpireButKeepDataDoesSuppress() {
    final AtomicLong _policyCalled = new AtomicLong();
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new BasicCacheTest.AlwaysExceptionSource())
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
          return 0;
        }
      })
      .keepDataAfterExpired(true)
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(final Integer key, final ExceptionInformation exceptionInformation, final CacheEntry<Integer, Integer> cachedContent) {
          _policyCalled.incrementAndGet();
          return exceptionInformation.getLoadTime() + 1;
        }

        @Override
        public long retryLoadAfter(final Integer key, final ExceptionInformation exceptionInformation) {
          _policyCalled.incrementAndGet();
          return 0;
        }
      })
      .build();
    c.put(1, 1);
    c.getEntry(1);
    assertEquals(1, _policyCalled.get());
    assertEquals(1, getInfo().getSuppressedExceptionCount());
  }

  @Test
  public void testResiliencePolicyLoadExceptionInformationCounterReset_keep() throws Exception {
    final int _INITIAL = - 4711;
    final AtomicInteger _cacheRetryCount = new AtomicInteger(_INITIAL);
    final AtomicInteger _suppressRetryCount = new AtomicInteger(_INITIAL);
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .eternal(true)
      .keepDataAfterExpired(true)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
          return NO_CACHE;
        }
      })
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(final Integer key, final ExceptionInformation inf,
                                           final CacheEntry<Integer, Integer> cachedContent) {
          _suppressRetryCount.set(inf.getRetryCount());
          return ETERNAL;
        }

        @Override
        public long retryLoadAfter(final Integer key, final ExceptionInformation inf) {
          _cacheRetryCount.set(inf.getRetryCount());
          return NO_CACHE;
        }
      })
      .loader(new Every1ExceptionLoader())
      .build();
    int key = 2 + 8;
    CacheEntry<Integer, Integer> e = c.getEntry(key);
    assertNull(e.getException());
    e = c.getEntry(key);
    assertNull("suppressed", e.getException());
    assertEquals(0, _suppressRetryCount.get());
    assertEquals(-4711, _cacheRetryCount.get());
    e = c.getEntry(key);
    assertNull("still suppressed", e.getException());
    assertEquals("no additional loader call", 0, _suppressRetryCount.get());
    assertEquals(-4711, _cacheRetryCount.get());
    c.put(key, 123);
    e = c.getEntry(key);
    assertNull(e.getException());
    assertEquals(0, _suppressRetryCount.get());
    e = c.getEntry(key);
    assertNull(e.getException());
    assertEquals(0, _suppressRetryCount.get());
  }

  /**
   * Refresh ahead is on but the expiry policy returns 0.
   * That is a contradiction. Expiry policy overrules refresh ahead, the
   * entry is expired and not visible.
   */
  @Test
  public void testRefreshButNoRefreshIfAlreadyExpiredZeroTimeCheckCounters() {
    final int _COUNT = 3;
    IntCountingCacheSource _countingLoader = new IntCountingCacheSource();
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .refreshAhead(true)
      .eternal(true)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
          return 0;
        }
      })
      .loader(_countingLoader)
      .build();
    c.get(1);
    c.get(2);
    c.get(3);
    await("All expired", new Condition() {
      @Override
      public boolean check() {
        return getInfo().getExpiredCount() >= _COUNT;
      }
    });
    assertEquals(0, getInfo().getRefreshCount() + getInfo().getRefreshRejectedCount());
    assertEquals(_COUNT, _countingLoader.getLoaderCalledCount());
    assertEquals(_COUNT, getInfo().getExpiredCount());
  }

  /**
   * Refresh ahead is on but the expiry policy returns 0.
   * That is a contradiction. Expiry policy overrules refresh ahead, the
   * entry is expired and not visible.
   */
  @Test // enabled again 23.8.2016;jw @Ignore("no keep and refresh ahead is prevented")
  public void testRefreshNoKeepButNoRefreshIfAlreadyExpiredZeroTimeCheckCounters() {
    final int _COUNT = 3;
    IntCountingCacheSource _countingLoader = new IntCountingCacheSource();
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .refreshAhead(true)
      .eternal(true)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
          return 0;
        }
      })
      .loader(_countingLoader)
      .keepDataAfterExpired(false)
      .build();
    c.get(1);
    c.get(2);
    c.get(3);
    await("All expired", new Condition() {
      @Override
      public boolean check() {
        return getInfo().getExpiredCount() >= _COUNT;
      }
    });
    assertEquals(0, getInfo().getRefreshCount() + getInfo().getRefreshRejectedCount());
    assertEquals(_COUNT, _countingLoader.getLoaderCalledCount());
    assertEquals(_COUNT, getInfo().getExpiredCount());
  }

  /**
   * Refresh ahead is on but the expiry policy returns 0.
   * That is a contradiction. Expiry policy overrules refresh ahead, the
   * entry is expired and not visible.
   */
  @Test
  public void testRefreshButNoRefreshIfAlreadyExpiredZeroTime() {
    IntCountingCacheSource _countingLoader = new IntCountingCacheSource();
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .refreshAhead(true)
      .eternal(true)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
          return 0;
        }
      })
      .loader(_countingLoader)
      .build();
    c.get(1);
    assertFalse(c.containsKey(1));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRefreshWithKeepData() {
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .refreshAhead(true)
      .keepDataAfterExpired(false)
      .eternal(true)
      .loader(new IdentIntSource())
      .build();
  }

  @Test
  public void testRefreshIfAlreadyExpiredLoadTime() {
    final int _COUNT = 3;
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .refreshAhead(true)
      .eternal(true)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
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
        return getInfo().getRefreshCount() + getInfo().getRefreshRejectedCount() >= _COUNT;
      }
    });
    await("All expired", new Condition() {
      @Override
      public boolean check() {
        return getInfo().getExpiredCount() >= _COUNT;
      }
    });
  }

  static final long FUTURE_TIME = Timestamp.valueOf("2058-02-18 23:42:15").getTime();

  @Test(expected = IllegalArgumentException.class)
  public void manualExpire_exception() {
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .eternal(true)
      .build();
    c.put(1,2);
    c.expireAt(1, FUTURE_TIME);
  }

  @Test
  public void manualExpire_now() {
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .eternal(true)
      .build();
    c.put(1,2);
    c.expireAt(1, 0);
    assertFalse(c.containsKey(1));
  }

  @Test
  public void manualExpire_aboutNow() {
    Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .expireAfterWrite(LONG_DELTA, TimeUnit.MILLISECONDS)
      .build();
    c.put(1,2);
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
      Cache2kBuilder<Integer,Integer> b = builder(Integer.class, Integer.class)
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
        public Integer load(final Integer key) throws Exception {
          return waitForSemaphoreAndLoad(key);
        }
      });
    }

    protected Integer waitForSemaphoreAndLoad(Integer key) throws Exception {
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
        public boolean check() throws Exception {
          return count.get() == 1;
        }
      });
      await("load complete", new Condition() {
        @Override
        public boolean check() throws Exception {
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
  public void manualExpire_refresh_now_gone() throws Exception {
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
    final long _TIME_VALUE_POTENTIALLY_COLLIDING_WITH_INTERNAL_STATES = 7;
    new ManualExpireFixture() {
      @Override
      void test() throws Exception {
        cache.put(1, 2);
        sem.acquire();
        cache.expireAt(1, _TIME_VALUE_POTENTIALLY_COLLIDING_WITH_INTERNAL_STATES);
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
      protected void addLoader(final Cache2kBuilder<Integer, Integer> b) {
        b.loaderExecutor(Executors.newSingleThreadExecutor());
        b.loader(new AsyncCacheLoader<Integer, Integer>() {
          @Override
          public void load(final Integer key, final Context<Integer, Integer> context, final Callback<Integer> callback) throws Exception {
            context.getLoaderExecutor().execute(new Runnable() {
              @Override
              public void run() {
                try {
                  callback.onLoadSuccess(waitForSemaphoreAndLoad(key));
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
  public void  manualExpire_refresh_pastTime() throws Exception {
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
        public Integer load(final Integer key) throws Exception {
          return 4711;
        }
      })
      .build();
    c.put(1,2);
    c.expireAt(1, -millis());
    assertFalse(c.containsKey(1));
    await(new Condition() {
      @Override
      public boolean check() throws Exception {
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
        public Integer load(final Integer key) throws Exception {
          return 4711;
        }
      })
      .build();
    c.put(1,2);
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
        public Integer load(final Integer key) throws Exception {
          return 4711;
        }
      })
      .build();
    within(LONG_DELTA).work(new Runnable() {
      @Override
      public void run() {
        c.put(1,2);
        c.expireAt(1, -millis());
      }
    }).check(new Runnable() {
      @Override
      public void run() {
        assertFalse(c.containsKey(1));
        await(new Condition() {
          @Override
          public boolean check() throws Exception {
            return getInfo().getRefreshCount() > 0;
          }
        });
        assertEquals(1, getInfo().getSize());
      }
    });
    c.expireAt(1, ExpiryTimeValues.NO_CACHE);
    assertEquals(0, getInfo().getSize());
  }

  @Test
  public void manualExpire_refreshAhead_sharp_refresh() {
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(LONG_DELTA, TimeUnit.MILLISECONDS)
      .refreshAhead(true)
      .keepDataAfterExpired(false)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          return 4711;
        }
      })
      .build();
    c.put(1,2);
    within(LONG_DELTA)
      .work(new Runnable() {
        @Override
        public void run() {
          c.expireAt(1, -millis());
          assertFalse(c.containsKey(1));
          await(new Condition() {
            @Override
            public boolean check() throws Exception {
              return getInfo().getRefreshCount() == 1;
            }
          });
        }
      })
      .check(new Runnable() {
        @Override
        public void run() {
          assertEquals(1, getInfo().getSize());
          c.expireAt(1, ExpiryTimeValues.ETERNAL);
          assertFalse(c.containsKey(1));
          assertEquals(1, getInfo().getSize());
          assertEquals(1, getInfo().getRefreshCount());
          c.expireAt(1, ExpiryTimeValues.REFRESH);
          assertEquals(1, getInfo().getSize());
          await(new Condition() {
            @Override
            public boolean check() throws Exception {
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
        public Integer load(final Integer key) throws Exception {
          return 4711;
        }
      })
      .build();
    c.invoke(1, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(final MutableCacheEntry<Integer, Integer> e) throws Exception {
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
        public Integer load(final Integer key) throws Exception {
          return 4711;
        }
      })
      .build();
    c.put(1,2);
    c.invoke(1, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(final MutableCacheEntry<Integer, Integer> e) throws Exception {
        e.setExpiryTime(-millis());
        return null;
      }
    });
    assertFalse(c.containsKey(1));
  }

  @Test
  public void rejectNull_skipLoaderException_null_getAll_empty() {
    Cache<Integer, Integer> c = cacheNoNullNoLoaderException();
    Map<Integer, Integer> map = c.getAll(toIterable(1, 2 ,3));
    assertEquals(0, map.size());
  }

  @Test
  public void rejectNull_skipLoaderException_null_entry() {
    Cache<Integer, Integer> c = cacheNoNullNoLoaderException();
    CacheEntry e = c.getEntry(1);
    assertNull(e);
  }

  /** Checks that nothing breaks here. */
  @Test
  public void high_expiry() {
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(Long.MAX_VALUE - 47, TimeUnit.MILLISECONDS)
      .build();
    c.put(1,1);
    assertTrue(c.containsKey(1));
  }

  private Cache<Integer, Integer> cacheNoNullNoLoaderException() {
    return builder(Integer.class, Integer.class)
        .expireAfterWrite(LONG_DELTA, TimeUnit.MILLISECONDS)
        .keepDataAfterExpired(false)
        .permitNullValues(false)
        .loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(final Integer key) throws Exception {
            return null;
          }
        })
        .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
          @Override
          public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
            return value == null ? NO_CACHE : ETERNAL;
          }
        })
        .build();
  }

  public static class Every1ExceptionLoader extends CacheLoader<Integer, Integer> {

    public final Map<Integer, AtomicInteger> key2count = new HashMap<Integer, AtomicInteger>();

    protected void maybeThrowException(Integer _key, int _count) {
      if ((_key & (1 << _count)) != 0) {
        throw new IllegalStateException("counter=" + _count);
      }
    }

    @Override
    public Integer load(Integer _key) throws Exception {
      AtomicInteger _count;
      synchronized (key2count) {
        _count = key2count.get(_key);
        if (_count == null) {
          _count = new AtomicInteger();
          key2count.put(_key, _count);
        } else {
          _count.getAndIncrement();
        }
      }
      maybeThrowException(_key, _count.get());
      return _key;
    }

  }

  private static class ClosingExpiryPolicy implements ExpiryPolicy, Closeable {

    AtomicBoolean closeCalled = new AtomicBoolean(false);

    @Override
    public void close() throws IOException {
      closeCalled.set(true);
    }

    @Override
    public long calculateExpiryTime(final Object key, final Object value, final long loadTime, final CacheEntry oldEntry) {
      return 0;
    }
  }

  @Test
  public void close_called() {
    ClosingExpiryPolicy ep = new ClosingExpiryPolicy();
    builder().expiryPolicy(ep).build().close();
    assertTrue(ep.closeCalled.get());
  }

}
