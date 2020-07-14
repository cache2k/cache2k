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

import org.cache2k.Cache2kBuilder;
import org.cache2k.integration.AsyncCacheLoader;
import org.cache2k.test.core.BasicCacheTest;
import org.cache2k.test.util.TestingBase;
import org.cache2k.test.util.IntCountingCacheSource;
import org.cache2k.CacheEntry;
import org.cache2k.core.HeapCache;
import org.cache2k.core.util.TunableFactory;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.integration.CacheLoader;
import org.cache2k.CacheOperationCompletionListener;
import org.cache2k.integration.ExceptionInformation;
import org.cache2k.integration.ResiliencePolicy;
import org.cache2k.test.util.Condition;
import org.cache2k.Cache;

import org.cache2k.integration.CacheLoaderException;
import org.cache2k.test.core.TestingParameters;
import org.cache2k.core.InternalCacheInfo;

import static org.cache2k.test.core.StaticUtil.*;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import org.cache2k.testing.category.SlowTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Expiry tests that work with some larger durations. Slow when tested in real time.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("unchecked")
@Category(SlowTests.class)
public class SlowExpiryTest extends TestingBase {

  /**
   * Exceptions have minimal retry interval.
   */
  @Test
  public void testExceptionWithRefresh() {
    testExceptionWithRefreshAndLoader(false);
  }

  @Test
  public void testExceptionWithRefreshAsyncLoader() {
    testExceptionWithRefreshAndLoader(true);
  }

  public void testExceptionWithRefreshAndLoader(boolean asyncLoader) {
    String _cacheName = generateUniqueCacheName(this);
    final int COUNT = 4;
    final long TIMESPAN =  TestingParameters.MINIMAL_TICK_MILLIS;
    final Cache2kBuilder<Integer, Integer> cb = builder(_cacheName, Integer.class, Integer.class)
      .refreshAhead(true)
      .retryInterval(TIMESPAN, TimeUnit.MILLISECONDS);
    if (asyncLoader) {
      cb.loader(new AsyncCacheLoader<Integer, Integer>() {
        @Override
        public void load(final Integer key, final Context<Integer, Integer> context, final Callback<Integer> callback) throws Exception {
          throw new RuntimeException("always");
        }
      });
    } else {
      cb.loader(new BasicCacheTest.AlwaysExceptionSource());
    }
    final Cache<Integer, Integer> c = cb.build();
    cache = c;
    within(TIMESPAN)
      .work(new Runnable() {
        @Override
        public void run() {
          for (int i = 1; i <= COUNT; i++) {
            try {
              c.get(i);
              fail("expect exception");
            } catch (CacheLoaderException ignore) { }
          }
        }
      })
      .check(new Runnable() {
         @Override
         public void run() {
           assertEquals(COUNT, getInfo().getSize());
           assertEquals("no refresh yet", 0,
             getInfo().getRefreshCount() + getInfo().getRefreshFailedCount()
           );
         }
       }
      );
    await("All refreshed", new Condition() {
      @Override
      public boolean check() {
        return getInfo().getRefreshCount() + getInfo().getRefreshFailedCount() >= COUNT;
      }
    });
    assertEquals("no internal exceptions",0, getInfo().getInternalExceptionCount());
    assertTrue("got at least 8 - submitFailedCnt exceptions", getInfo().getLoadExceptionCount() >= getInfo().getRefreshFailedCount());
    assertTrue("no alert", getInfo().getHealth().isEmpty());
    await("All expired", new Condition() {
      @Override
      public boolean check() {
        return getInfo().getExpiredCount() >= COUNT;
      }
    });
    assertEquals(0, getInfo().getSize());
  }

  @Test
  public void testExceptionWithRefreshSyncLoader() {
    String _cacheName = generateUniqueCacheName(this);
    final int COUNT = 4;
    final long TIMESPAN =  TestingParameters.MINIMAL_TICK_MILLIS;
    final Cache<Integer, Integer> c = builder(_cacheName, Integer.class, Integer.class)
      .refreshAhead(true)
      .retryInterval(TIMESPAN, TimeUnit.MILLISECONDS)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          throw new RuntimeException("always");
        }

      })
      .build();
    cache = c;
    within(TIMESPAN)
      .work(new Runnable() {
        @Override
        public void run() {
          for (int i = 1; i <= COUNT; i++) {
            boolean _gotException = false;
            try {
              c.get(i);
              fail("expect exception");
            } catch (CacheLoaderException e) {
              _gotException = true;
            }
            assertTrue("got exception", _gotException);
          }
        }
      })
      .check(new Runnable() {
               @Override
               public void run() {
                 assertEquals(COUNT, getInfo().getSize());
                 assertEquals("no refresh yet", 0,
                   getInfo().getRefreshCount() + getInfo().getRefreshFailedCount()
                 );
               }
             }
      );
    await("All refreshed", new Condition() {
      @Override
      public boolean check() {
        return getInfo().getRefreshCount() + getInfo().getRefreshFailedCount() >= COUNT;
      }
    });
    assertEquals("no internal exceptions",0, getInfo().getInternalExceptionCount());
    assertTrue("got at least 8 - submitFailedCnt exceptions", getInfo().getLoadExceptionCount() >= getInfo().getRefreshFailedCount());
    assertTrue("no alert", getInfo().getHealth().isEmpty());
    await("All expired", new Condition() {
      @Override
      public boolean check() {
        return getInfo().getExpiredCount() >= COUNT;
      }
    });
    assertEquals(0, getInfo().getSize());
  }


  @Test
  public void testExceptionExpirySuppressTwiceWaitForExceptionExpiry() throws Exception {
    final long _EXCEPTION_EXPIRY_MILLIS =  TestingParameters.MINIMAL_TICK_MILLIS;
    final BasicCacheTest.OccasionalExceptionSource src = new BasicCacheTest.PatternExceptionSource(false, true, false);
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
        .expireAfterWrite(0, TimeUnit.MINUTES)
        .retryInterval(_EXCEPTION_EXPIRY_MILLIS, TimeUnit.MILLISECONDS)
        .resilienceDuration(33, TimeUnit.MINUTES)
        .keepDataAfterExpired(true)
        .loader(src)
        .build();
    int _exceptionCount = 0;
    try {
      c.get(1);
    } catch (CacheLoaderException e) {
      _exceptionCount++;
    }
    assertEquals("no exception", 0, _exceptionCount);
    c.get(2); // value is fetched
    within(_EXCEPTION_EXPIRY_MILLIS)
      .work(new Runnable() {
        @Override
        public void run() {
          c.get(2); // exception gets suppressed
        }
      })
      .check(new Runnable() {
        @Override
        public void run() {
          InternalCacheInfo inf = getInfo();
          assertEquals(1, inf.getSuppressedExceptionCount());
          assertEquals(1, inf.getLoadExceptionCount());
          assertNotNull(src.key2count.get(2));
        }
      });
    await(TestingParameters.MAX_FINISH_WAIT_MILLIS, new Condition() {
      @Override
      public boolean check() {
        c.get(2); // value is fetched, again if expired
        return src.key2count.get(2).get() == 3;
      }
    });
  }

  @Test
  public void testExceptionExpiryNoSuppress() throws Exception {
    BasicCacheTest.OccasionalExceptionSource src = new BasicCacheTest.OccasionalExceptionSource();
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
        .expireAfterWrite(0, TimeUnit.MINUTES)
        .retryInterval(TestingParameters.MINIMAL_TICK_MILLIS, TimeUnit.MILLISECONDS)
        .suppressExceptions(false)
        .loader(src)
        .build();
    int _exceptionCount = 0;
    String _exceptionToString = null;
    try {
      c.get(1);
    } catch (CacheLoaderException e) {
      _exceptionCount++;
      _exceptionToString = e.toString();
    }
    assertEquals("1 => always exception", 1, _exceptionCount);
    _exceptionToString = _exceptionToString.replaceAll("[0-9]", "#");
    _exceptionToString = _exceptionToString.replace(".##,", ".###,");
    _exceptionToString = _exceptionToString.replace(".#,", ".###,");
    assertEquals("org.cache#k.integration.CacheLoaderException: " +
      "expiry=####-##-## ##:##:##.###, cause: " +
      "java.lang.RuntimeException: every # times", _exceptionToString);
    _exceptionCount = 0;
    try {
      c.get(2); // value is fetched
      c.get(2); // value is fetched again (expiry=0), but exception happens, no suppress
    } catch (CacheLoaderException e) {
      _exceptionCount++;
    }
    InternalCacheInfo inf = getInfo();
    assertEquals("exception expected, no suppress", 1, _exceptionCount);
    assertEquals(0, inf.getSuppressedExceptionCount());
    assertEquals(2, inf.getLoadExceptionCount());
    assertNotNull(src.key2count.get(2));
    assertEquals(2, src.key2count.get(2).get());
    await("exception expired and successful get()", new Condition() {
      @Override
      public boolean check() {
        try {
          c.get(2);
          return true;
        } catch (Exception ignore) { }
        return false;
      }
    });
  }

  @Test
  public void testSuppressExceptionImmediateExpiry() throws Exception {
    BasicCacheTest.OccasionalExceptionSource src = new BasicCacheTest.OccasionalExceptionSource();
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(0, TimeUnit.MINUTES)
      .retryInterval(TestingParameters.MINIMAL_TICK_MILLIS, TimeUnit.MILLISECONDS)
      .resilienceDuration(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
      .keepDataAfterExpired(true)
      .loader(src)
      .build();
    c.get(2);
    c.get(2);
    assertEquals(1, getInfo().getSuppressedExceptionCount());
  }

  @Test
  public void testSuppressExceptionShortExpiry() throws Exception {
    BasicCacheTest.OccasionalExceptionSource src = new BasicCacheTest.OccasionalExceptionSource();
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(TestingParameters.MINIMAL_TICK_MILLIS, TimeUnit.MILLISECONDS)
      .retryInterval(TestingParameters.MINIMAL_TICK_MILLIS, TimeUnit.MILLISECONDS)
      .resilienceDuration(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
      .keepDataAfterExpired(true)
      .loader(src)
      .build();
    c.get(2);
    await("wait for expiry", new Condition() {
      @Override
      public boolean check() throws Exception {
        return getInfo().getExpiredCount() > 0;
      }
    });
    c.get(2);
    assertEquals(1, getInfo().getSuppressedExceptionCount());
    assertEquals(1, getInfo().getSize());
  }

  /**
   * Test with short expiry time to trip a special case during the expiry calculation:
   * the expiry happens during the calculation
   */
  @Test
  public void testShortExpiryTimeDelayLoad() throws Exception {
    boolean keepData = true;
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(1, TimeUnit.MILLISECONDS)
      .retryInterval(TestingParameters.MINIMAL_TICK_MILLIS, TimeUnit.MILLISECONDS)
      .resilienceDuration(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
      .keepDataAfterExpired(keepData)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          sleep(2);
          return key;
        }
      })
      .build();
    final int COUNT = 1;
    c.get(0);
    for (int i = 0; i < COUNT; i++) {
      c.get(i);
    }
    await("wait for expiry", new Condition() {
      @Override
      public boolean check() throws Exception {
        return getInfo().getExpiredCount() >= COUNT;
      }
    });
    if (keepData) {
      assertEquals(COUNT, getInfo().getSize());
    }
  }

  /**
   * Switch keep data off.
   * Should this refuse operation right away since suppressException and keepDataAfterExpired
   * makes no sense in combination? No, since load requests should suppress exceptions, too.
   */
  @Test(expected = RuntimeException.class)
  public void testNoSuppressExceptionShortExpiry() {
    BasicCacheTest.OccasionalExceptionSource src = new BasicCacheTest.OccasionalExceptionSource();
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(TestingParameters.MINIMAL_TICK_MILLIS, TimeUnit.MILLISECONDS)
      .retryInterval(TestingParameters.MINIMAL_TICK_MILLIS, TimeUnit.MILLISECONDS)
      .suppressExceptions(true)
      .keepDataAfterExpired(false)
      .loader(src)
      .build();
    c.get(2);
    await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return getInfo().getExpiredCount() > 0;
      }
    });
    c.get(2);
    fail("not reached");
  }

  @Test
  public void testSuppressExceptionLongExpiryAndReload() {
    BasicCacheTest.OccasionalExceptionSource src = new BasicCacheTest.OccasionalExceptionSource();
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(TestingParameters.MAX_FINISH_WAIT_MILLIS, TimeUnit.MINUTES)
      .retryInterval(TestingParameters.MINIMAL_TICK_MILLIS, TimeUnit.MILLISECONDS)
      .suppressExceptions(true)
      .loader(src)
      .build();
    c.get(2);
    syncLoad(new LoaderStarter() {
      @Override
      public void startLoad(final CacheOperationCompletionListener l) {
        c.reloadAll(toIterable(2), l);
      }
    });
    await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return getInfo().getSuppressedExceptionCount() > 0;
      }
    });
    c.get(2);
  }

  @Test
  public void testNeverSuppressWithRetryInterval0() {
    BasicCacheTest.OccasionalExceptionSource src = new BasicCacheTest.OccasionalExceptionSource();
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(TestingParameters.MAX_FINISH_WAIT_MILLIS, TimeUnit.MINUTES)
      .retryInterval(0, TimeUnit.MILLISECONDS)
      .suppressExceptions(true)
      .loader(src)
      .build();
    c.get(2);
    syncLoad(new LoaderStarter() {
      @Override
      public void startLoad(final CacheOperationCompletionListener l) {
        c.reloadAll(toIterable(2), l);
      }
    });
    assertEquals(0, getInfo().getSuppressedExceptionCount());
    assertEquals(2, getInfo().getLoadCount());
  }

  @Test
  public void testNeverSuppressWithLoadTimeUntil() {
    BasicCacheTest.OccasionalExceptionSource src = new BasicCacheTest.OccasionalExceptionSource();
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(TestingParameters.MAX_FINISH_WAIT_MILLIS, TimeUnit.MINUTES)
      .resiliencePolicy(new ResiliencePolicy<Integer, Integer>() {
        @Override
        public long suppressExceptionUntil(final Integer key, final ExceptionInformation exceptionInformation, final CacheEntry<Integer, Integer> cachedContent) {
          return exceptionInformation.getLoadTime();
        }

        @Override
        public long retryLoadAfter(final Integer key, final ExceptionInformation exceptionInformation) {
          return 0;
        }
      })
      .suppressExceptions(true)
      .loader(src)
      .build();
    c.get(2);
    syncLoad(new LoaderStarter() {
      @Override
      public void startLoad(final CacheOperationCompletionListener l) {
        c.reloadAll(toIterable(2), l);
      }
    });
    assertEquals(0, getInfo().getSuppressedExceptionCount());
    assertEquals(2, getInfo().getLoadCount());
  }

  @Test
  public void testExpireNoKeepSharpExpiryBeyondSafetyGap() {
    HeapCache.Tunable t = TunableFactory.get(HeapCache.Tunable.class);
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new IntCountingCacheSource())
      .expireAfterWrite(t.sharpExpirySafetyGapMillis + 3, TimeUnit.MILLISECONDS)
      .keepDataAfterExpired(false)
      .sharpExpiry(true)
      .build();
    c.getAll(toIterable(1, 2, 3));
    await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return getInfo().getTimerEventCount() >= 3;
      }
    });
  }

  @Test
  public void testExpireNoKeepAsserts() {
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new IntCountingCacheSource())
      .expireAfterWrite(TestingParameters.MINIMAL_TICK_MILLIS, TimeUnit.MILLISECONDS)
      .keepDataAfterExpired(false)
      .build();
    within(TestingParameters.MINIMAL_TICK_MILLIS)
      .work(new Runnable() {
        @Override
        public void run() {
          c.getAll(toIterable(1, 2, 3));
        }
      })
      .check(new Runnable() {
               @Override
               public void run() {
                 assertTrue(c.containsKey(1));
                 assertTrue(c.containsKey(3));
               }
             }
      );
    assertEquals(3, getInfo().getLoadCount());
  }

  public void testExpireNoKeep(long _millis) {
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new IntCountingCacheSource())
      .expireAfterWrite(_millis, TimeUnit.MILLISECONDS)
      .keepDataAfterExpired(false)
      .build();
    within(_millis)
      .work(new Runnable() {
        @Override
        public void run() {
          c.getAll(toIterable(1, 2, 3));
        }
      })
      .check(new Runnable() {
         @Override
         public void run() {
           assertTrue(c.containsKey(1));
           assertTrue(c.containsKey(3));
         }
       }
      );
    assertEquals(3, getInfo().getLoadCount());
    await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return getInfo().getSize() == 0;
      }
    });
  }

  @Test
  public void testExpireNoKeep1() {
    testExpireNoKeep(1);
  }

  @Test
  public void testExpireNoKeep2() {
    testExpireNoKeep(2);
  }

  @Test
  public void testExpireNoKeep3() {
    testExpireNoKeep(3);
  }

  @Test
  public void testExpireNoKeep77() {
    testExpireNoKeep(77);
  }

  @Test
  public void testExpireNoKeepGap() {
    HeapCache.Tunable t = TunableFactory.get(HeapCache.Tunable.class);
    testExpireNoKeep(t.sharpExpirySafetyGapMillis);
  }

  @Test
  public void testExpireNoKeepAfterGap() {
    HeapCache.Tunable t = TunableFactory.get(HeapCache.Tunable.class);
    testExpireNoKeep(t.sharpExpirySafetyGapMillis + 3);
  }

  @Test
  public void testExpireNoKeepSharpExpiry() {
    final Cache<Integer, Integer> c = cache = builder(Integer.class, Integer.class)
      .loader(new IntCountingCacheSource())
      .expireAfterWrite(3, TimeUnit.MILLISECONDS)
      .keepDataAfterExpired(false)
      .sharpExpiry(true)
      .build();
    c.getAll(toIterable(1, 2, 3));
    await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return getInfo().getSize() == 0;
      }
    });
  }

  /**
   * If keepdata is true we expect some timer events.
   *
   * don't test this variant with nokeep:
   * if expiry is immediately and keepData false: refresh timer is initiated but entry gets removed immediately
   */
  @Test
  public void refreshAndSharp_get_expireImmediate_keep() {
    refreshAndSharp_get(true, 0);
  }

  @Test
  public void refreshAndSharp_get_expireMinimalTick_keep() {
    refreshAndSharp_get(true, TestingParameters.MINIMAL_TICK_MILLIS);
  }

  @Test
  public void refreshAndSharp_get_expireGap_keep() {
    refreshAndSharp_get(true, getEffectiveSafetyGapMillis() + 3);
  }

  @Test
  public void refreshAndSharp_get_expireMinimalTick_noKeep() {
    refreshAndSharp_get(false, TestingParameters.MINIMAL_TICK_MILLIS);
  }

  @Test
  public void refreshAndSharp_get_expireGap_noKeep() {
    refreshAndSharp_get(false, getEffectiveSafetyGapMillis() + 3);
  }

  /**
   * Refresh ahead means entries never expire. The sharp expiry setting is a contradiction.
   * If both are set, the entry will expire at the point in time. Either the get or the refresh
   * will load the new value.
   */
  public void refreshAndSharp_get(final boolean _keepData, final long _TICK_TIME) {
    final int KEY = 1;
    final AtomicLong _EXPIRY_TIME = new AtomicLong();
    final CountingLoader _LOADER = new CountingLoader();
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .refreshAhead(true)
      .sharpExpiry(true)
      .eternal(true)
      .keepDataAfterExpired(_keepData)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
          if (_EXPIRY_TIME.get() == 0) {
            _EXPIRY_TIME.set(loadTime + _TICK_TIME);
          }
          return loadTime + _TICK_TIME;
        }
      })
      .loader(_LOADER)
      .build();
    final int v = c.get(KEY);
    assertTrue("expiry policy called", _EXPIRY_TIME.get() > 0);
    if (v == 0) {
      await("Get returns fresh", new Condition() {
        @Override
        public boolean check() throws Exception {
          long t0 = millis();
          Integer v = c.get(KEY);
          long t1 = millis();
          assertNotNull(v);
          assertTrue("Only see 0 before expiry time", !(v == 0) || t0 < _EXPIRY_TIME.get());
          assertTrue("Only see 1 after expiry time", !(v == 1) || t1 >= _EXPIRY_TIME.get());
          assertThat("maximum loads minus 1", v, lessThanOrEqualTo( 3));
          return v > 0;
        }
      });
    } else {
      long t1 = millis();
      assertTrue("Only see 1 after expiry time", t1 >= _EXPIRY_TIME.get());
    }
    final long _LOADS_TRIGGERED_BY_GET = 2;
    long _ADDITIONAL_LOADS_BECAUSE_OF_REFRESH = 2;
    await("minimum loads", new Condition() {
      @Override
      public boolean check() throws Exception {
        return getInfo().getLoadCount() >= _LOADS_TRIGGERED_BY_GET;
      }
    });
    assertThat("minimum loads triggered", getInfo().getLoadCount(),
      greaterThanOrEqualTo(_LOADS_TRIGGERED_BY_GET));
    assertThat("maximum loads triggered", getInfo().getLoadCount(),
      lessThanOrEqualTo(_LOADS_TRIGGERED_BY_GET + _ADDITIONAL_LOADS_BECAUSE_OF_REFRESH));
    await("Timer triggered", new Condition() {
      @Override
      public boolean check() throws Exception {
        return getInfo().getTimerEventCount() > 0;
      }
    });
    if (_keepData) {
      await("Refresh is done", new Condition() {
        @Override
        public boolean check() throws Exception {
          return getInfo().getRefreshCount() > 0;
        }
      });
    }
    await("Expires finally", new Condition() {
      @Override
      public boolean check() throws Exception {
        return getInfo().getExpiredCount() > 0;
      }
    });
    await("loader count identical", new Condition() {
      @Override
      public boolean check() throws Exception {
        return getInfo().getLoadCount() == _LOADER.getCount();
      }
    });
  }

  /**
   * The sharp expiry switch is only used for the ExpiryPolicy and not for the
   * duration configured via expireAfterWrite.
   */
  @Test
  public void refresh_sharp_regularExpireAfterWriter_lagging() throws Exception {
    final int KEY = 1;
    final AtomicInteger _counter = new AtomicInteger();
    final long _EXPIRY = TestingParameters.MINIMAL_TICK_MILLIS;
    final CountDownLatch _latch = new CountDownLatch(1);
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .refreshAhead(true)
      .sharpExpiry(true)
      .expireAfterWrite(_EXPIRY, TimeUnit.MILLISECONDS)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          int v = _counter.getAndIncrement();
          if (v == 1) {
            _latch.await();
          }
          return v;
        }
      })
      .build();
    within(_EXPIRY)
      .work(new Runnable() {
        @Override
        public void run() {
          c.get(KEY);
        }
      })
      .check(new Runnable() {
        @Override
        public void run() {
          c.containsKey(KEY);
        }
      });
    sleep(_EXPIRY * 2);
    assertTrue("still present since loader is active", c.containsKey(KEY));
    _latch.countDown();
  }

  int value;

  public void refresh_sharp_noKeep(final long _EXPIRY) {
    final long MAX_FINISH_WAIT_MILLIS = TestingParameters.MAX_FINISH_WAIT_MILLIS;
    final int KEY = 1;
    final CountingLoader _LOADER = new CountingLoader();
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .refreshAhead(true)
      .sharpExpiry(true)
      .eternal(true)
      .keepDataAfterExpired(false)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
          if (oldEntry != null) {
            return loadTime + MAX_FINISH_WAIT_MILLIS;
          }
          return loadTime + _EXPIRY;
        }
      })
      .loader(_LOADER)
      .build();
    within(MAX_FINISH_WAIT_MILLIS + _EXPIRY)
      .work(new Runnable() {
        @Override
        public void run() {
          within(_EXPIRY)
            .work(new Runnable() {
              @Override
              public void run() {
                value = c.get(KEY);
              }
            })
            .check(new Runnable() {
              @Override
              public void run() {
                assertEquals(0, value);
                assertTrue(c.containsKey(KEY));
              }
            });
          await("Refresh is done", new Condition() {
            @Override
            public boolean check() throws Exception {
              return getInfo().getRefreshCount() > 0;
            }
          });
          await("Entry disappears", new Condition() {
            @Override
            public boolean check() throws Exception {
              return !c.containsKey(KEY);
            }
          });
          value = c.get(KEY);
        }
      }).check(new Runnable() {
      @Override
      public void run() {
        assertEquals(1, value);
        assertEquals(2, _LOADER.getCount());
      }
    });
  }

  @Test
  public void refresh_sharp_noKeep_0ms() {
    refresh_sharp_noKeep(0);
  }

  @Test
  public void refresh_sharp_noKeep_3ms() {
    refresh_sharp_noKeep(3);
  }

  @Test
  public void refresh_sharp_keep() {
    final int KEY = 1;
    final CountingLoader _LOADER = new CountingLoader();
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .refreshAhead(true)
      .sharpExpiry(true)
      .eternal(true)
      .keepDataAfterExpired(true)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
          if (oldEntry != null) {
            return loadTime + TestingParameters.MAX_FINISH_WAIT_MILLIS;
          }
          return loadTime + TestingParameters.MINIMAL_TICK_MILLIS;
        }
      })
      .loader(_LOADER)
      .build();
    within(TestingParameters.MAX_FINISH_WAIT_MILLIS)
      .work(new Runnable() {
        @Override
        public void run() {
          within(TestingParameters.MINIMAL_TICK_MILLIS)
            .work(new Runnable() {
              @Override
              public void run() {
                int v = c.get(KEY);
                assertEquals(0, v);
              }
            })
            .check(new Runnable() {
              @Override
              public void run() {
                assertTrue(c.containsKey(KEY));
              }
            });
          await("Refresh is done", new Condition() {
            @Override
            public boolean check() throws Exception {
              return getInfo().getRefreshCount() > 0;
            }
          });
          await("Entry disappears", new Condition() {
            @Override
            public boolean check() throws Exception {
              return !c.containsKey(KEY);
            }
          });
        }
      })
      .check(new Runnable() {
        @Override
        public void run() {
          int v = c.get(KEY);
          assertEquals("long expiry after refresh", 1, v);
          assertEquals(2, _LOADER.getCount());
        }
      });
  }

  /** Entry does not go into probation if eternal */
  @Test
  public void refresh_sharp_noKeep_eternalAfterRefresh() throws Exception {
    final int KEY = 1;
    final CountingLoader _LOADER = new CountingLoader();
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .refreshAhead(true)
      .sharpExpiry(true)
      .eternal(true)
      .keepDataAfterExpired(false)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
          if (oldEntry != null) {
            return ETERNAL;
          }
          return loadTime + TestingParameters.MINIMAL_TICK_MILLIS;
        }
      })
      .loader(_LOADER)
      .build();
    int v = c.get(KEY);
    assertEquals(0, v);
    await("Refresh is done", new Condition() {
      @Override
      public boolean check() throws Exception {
        return getInfo().getRefreshCount() > 0;
      }
    });
    await("Entry stays", new Condition() {
      @Override
      public boolean check() throws Exception {
        return c.containsKey(KEY);
      }
    });
  }

  /** Entry does not go into probation if eternal */
  @Test
  public void refresh_sharp_keep_eternalAfterRefresh() throws Exception {
    final int KEY = 1;
    final CountingLoader _LOADER = new CountingLoader();
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .refreshAhead(true)
      .sharpExpiry(true)
      .eternal(true)
      .keepDataAfterExpired(true)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
          if (oldEntry != null) {
            return ETERNAL;
          }
          return loadTime + TestingParameters.MINIMAL_TICK_MILLIS;
        }
      })
      .loader(_LOADER)
      .build();
    int v = c.get(KEY);
    assertEquals(0, v);
    await("Refresh is done", new Condition() {
      @Override
      public boolean check() throws Exception {
        return getInfo().getRefreshCount() > 0;
      }
    });
    await("Entry stays", new Condition() {
      @Override
      public boolean check() throws Exception {
        return c.containsKey(KEY);
      }
    });
  }

  /**
   * Is refreshing stopped after a remove? Checks whether the timer is cancelled.
   */
  @Test
  public void refresh_timerStoppedWithRemove() throws InterruptedException {
    final int KEY = 1;
    final CountingLoader _LOADER = new CountingLoader();
    final long _EXPIRY = 123;
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .refreshAhead(true)
      .expireAfterWrite(_EXPIRY, TimeUnit.MILLISECONDS)
      .loader(_LOADER)
      .build();
    long t0 = millis();
    c.get(KEY);
    sleep(_EXPIRY * 3 / 2);
    c.remove(KEY);
    if (millis() - t0 < _EXPIRY * 2) {
      sleep(_EXPIRY * 3 - (millis() - t0));
      assertThat(
        "0 timer events if we are to fast, " +
        "max. 1 timer event because entry was removed before the refresh could happen",
        getInfo().getTimerEventCount(), isOneOf(0L, 1L));
    }
  }

  @Test
  public void refresh_secondTimerEvent_allIsCleared() throws InterruptedException {
    final int KEY = 1;
    final CountingLoader _LOADER = new CountingLoader();
    final long _EXPIRY = 123;
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .refreshAhead(true)
      .expireAfterWrite(_EXPIRY, TimeUnit.MILLISECONDS)
      .loader(_LOADER)
      .keepDataAfterExpired(false)
      .build();
    c.get(KEY);
    await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return getInfo().getTimerEventCount() == 2;
      }
    });
    await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return getInfo().getSize() == 0;
      }
    });
    assertEquals(2, _LOADER.getCount());
  }

  @Test
  public void loadAndExpireRaceNoGone() {
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .keepDataAfterExpired(false)
      .expireAfterWrite(TestingParameters.MINIMAL_TICK_MILLIS, TimeUnit.MILLISECONDS)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          sleep(100);
          return key;
        }
      })
      .build();
    c.put(1,1);
    c.reloadAll(toIterable(1), null);
    await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return getInfo().getLoadCount() > 0;
      }
    });
    await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return getInfo().getSize() == 0;
      }
    });
  }

  @Test
  public void manualExpire_nowIsh() {
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(5, TimeUnit.MINUTES)
      .build();
    c.put(1,2);
    c.expireAt(1, millis() + TestingParameters.MINIMAL_TICK_MILLIS);
    await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return !c.containsKey(1);
      }
    });
  }

  @Test
  public void manualExpire_nowIsh_doesRefresh() {
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(5, TimeUnit.MINUTES)
      .refreshAhead(true)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          return 4711;
        }
      })
      .build();
    c.put(1,2);
    c.expireAt(1, millis() + TestingParameters.MINIMAL_TICK_MILLIS);
    await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return c.get(1) == 4711;
      }
    });
  }

  /**
   * Refresh an entry immediately.
   */
  @Test
  public void manualExpire_nowIsh_getLater_doesRefresh() throws Exception {
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(5, TimeUnit.MINUTES)
      .refreshAhead(true)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          return 4711;
        }
      })
      .build();
    c.put(1,2);
    c.expireAt(1, millis() + TestingParameters.MINIMAL_TICK_MILLIS);
    sleep(TestingParameters.MINIMAL_TICK_MILLIS * 21);
    await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return c.get(1) == 4711;
      }
    });
  }

  @Test
  public void manualExpire_nowIsh_doesTriggerRefresh() throws Exception {
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(5, TimeUnit.MINUTES)
      .refreshAhead(true)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          return 4711;
        }
      })
      .build();
    c.put(1,2);
    c.expireAt(1, millis() + TestingParameters.MINIMAL_TICK_MILLIS);
    await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return getInfo().getRefreshCount() > 0;
      }
    });
  }

  @Test
  public void manualExpire_now_doesRefresh() {
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .expireAfterWrite(5, TimeUnit.MINUTES)
      .refreshAhead(true)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          return 4711;
        }
      })
      .build();
    c.put(1,2);
    c.expireAt(1, 0);
    await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return c.get(1) == 4711;
      }
    });
  }

  @Test
  public void neutralWhenModified() throws Exception {
    final long _EXPIRY = 100;
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .sharpExpiry(true)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
          if (oldEntry == null) {
            return loadTime + _EXPIRY;
          }
          return NEUTRAL;
        }
      })
      .build();
    within(_EXPIRY)
      .work(new Runnable() {
        @Override
        public void run() {
          c.put(1,2);
          c.put(1,2);
          c.put(1,2);
        }
      })
      .check(new Runnable() {
        @Override
        public void run() {
          assertTrue(c.containsKey(1));
        }
      });
    sleep(_EXPIRY);
    assertFalse(c.containsKey(1));
  }

  @Test(expected = IllegalArgumentException.class)
  public void neutralWhenCreatedYieldsException() throws Exception {
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .sharpExpiry(true)
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
          return NEUTRAL;
        }
      })
      .build();
    c.put(1,2);
  }

}

