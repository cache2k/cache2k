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

import org.cache2k.Cache2kBuilder;
import org.cache2k.core.CacheClosedException;
import org.cache2k.event.CacheEntryCreatedListener;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.io.AdvancedCacheLoader;
import org.cache2k.test.util.TestingBase;
import org.cache2k.Cache;
import org.cache2k.CacheEntry;
import org.cache2k.test.util.Condition;
import org.cache2k.event.CacheEntryExpiredListener;
import org.cache2k.core.HeapCache;
import org.cache2k.core.util.TunableFactory;
import org.cache2k.io.CacheLoader;
import org.cache2k.testing.category.SlowTests;
import static org.hamcrest.Matchers.*;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * More thorough expiry events.
 *
 * @author Jens Wilke
 */
@Category(SlowTests.class)
public class ExpiryListenerTest extends TestingBase {

  @Test
  public void simpleAsyncExpiredListenerCalled() {
    testListenerCalled(false, false);
  }

  @Test
  public void asyncExpiredListenerCalled() {
    testListenerCalled(false, true);
  }

  @Test
  public void expireBeforePut() {
    final AtomicInteger callCount = new AtomicInteger();
    HeapCache.Tunable t = TunableFactory.get(HeapCache.Tunable.class);
    final long expiryMillis = 100;
    assertThat(t.sharpExpirySafetyGapMillis, greaterThan(expiryMillis));
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .addListener(new CacheEntryExpiredListener<Integer, Integer>() {
        @Override
        public void onEntryExpired(Cache<Integer, Integer> c, CacheEntry<Integer, Integer> e) {
          callCount.incrementAndGet();
        }
      })
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                        CacheEntry<Integer, Integer> oldEntry) {
          return loadTime + expiryMillis;
        }
      })
      .sharpExpiry(true)
      .build();
    final int anyKey = 1;
    c.put(anyKey, 4711);
    for (int i = 1; i <= 10; i++) {
      sleep(expiryMillis);
      assertFalse("expired based on wall clock", c.containsKey(anyKey));
      c.put(anyKey, 1802);
      assertThat("expiry event before put", callCount.get(), greaterThanOrEqualTo(i));
    }
  }

  @Test
  public void expireAtSendsOneEvent() {
    final AtomicInteger listenerCallCount = new AtomicInteger();
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .addListener(new CacheEntryExpiredListener<Integer, Integer>() {
        @Override
        public void onEntryExpired(Cache<Integer, Integer> c, CacheEntry<Integer, Integer> e) {
          listenerCallCount.incrementAndGet();
        }
      })
      .build();
    final int anyKey = 1;
    c.put(anyKey, 4711);
    c.expireAt(anyKey, ExpiryTimeValues.NOW);
    assertEquals("expiry event before put", 1, listenerCallCount.get());
  }

  @Test
  public void normalExpireSendsOneEvent() {
    final long expiryMillis = TestingParameters.MINIMAL_TICK_MILLIS;
    final AtomicInteger listenerCallCount = new AtomicInteger();
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .addListener(new CacheEntryExpiredListener<Integer, Integer>() {
        @Override
        public void onEntryExpired(Cache<Integer, Integer> c, CacheEntry<Integer, Integer> e) {
          listenerCallCount.incrementAndGet();
        }
      })
      .expireAfterWrite(expiryMillis, TimeUnit.MILLISECONDS)
      .build();
    final int anyKey = 1;
    c.put(anyKey, 4711);
    await(new Condition() {
      @Override
      public boolean check() {
        return listenerCallCount.get() > 0;
      }
    });
    sleep(expiryMillis);
    assertEquals("expiry event before put", 1, listenerCallCount.get());
  }

  @Test
  public void normalExpireSendsOneEvent_sharp() {
    final long expiryMillis = TestingParameters.MINIMAL_TICK_MILLIS;
    final AtomicInteger listenerCallCount = new AtomicInteger();
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .addListener(new CacheEntryExpiredListener<Integer, Integer>() {
        @Override
        public void onEntryExpired(Cache<Integer, Integer> c, CacheEntry<Integer, Integer> e) {
          listenerCallCount.incrementAndGet();
        }
      })
      .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                        CacheEntry<Integer, Integer> oldEntry) {
          return loadTime + expiryMillis;
        }
      })
      .sharpExpiry(true)
      .build();
    final int anyKey = 1;
    c.put(anyKey, 4711);
    await(new Condition() {
      @Override
      public boolean check() {
        return listenerCallCount.get() > 0;
      }
    });
    sleep(expiryMillis);
    assertEquals("expect exactly one", 1, listenerCallCount.get());
  }

  @Test
  public void expireAfterRefreshProbationEnded() {
    final AtomicInteger loaderCount = new AtomicInteger();
    final AtomicInteger eventCount = new AtomicInteger();
    final long expiryMillis = TestingParameters.MINIMAL_TICK_MILLIS;
    Cache2kBuilder<Integer, Integer> builder =
      builder(Integer.class, Integer.class)
        .loader(new AdvancedCacheLoader<Integer, Integer>() {
          @Override
          public Integer load(Integer key, long startTime,
                              CacheEntry<Integer, Integer> currentEntry) {
            loaderCount.getAndIncrement();
            assertEquals(0, eventCount.get());
            return key;
          }
        })
        .expireAfterWrite(expiryMillis, TimeUnit.MILLISECONDS)
        .refreshAhead(true)
        .addListener(new CacheEntryExpiredListener<Integer, Integer>() {
          @Override
          public void onEntryExpired(Cache<Integer, Integer> c, CacheEntry<Integer, Integer> e) {
            eventCount.incrementAndGet();
          }
        });

    Cache<Integer, Integer> c = builder.build();
    final int anyKey = 1;
    c.get(anyKey);
    await(new Condition() {
      @Override
      public boolean check() {
        return eventCount.get() == 1;
      }
    });
    assertEquals(2, loaderCount.get());
  }

  /**
   * Is expiry listener called before the load?
   */
  @Test
  public void expireBeforeLoadSharp() {
    expireBeforeLoad(true);
  }

  @Test
  public void expireBeforeLoad() {
    expireBeforeLoad(false);
  }

  private void expireBeforeLoad(boolean sharpExpiry) {
    final AtomicInteger listenerCallCount = new AtomicInteger();
    final long expiryMillis = 432;
    Cache2kBuilder<Integer, Integer> builder =
      builder(Integer.class, Integer.class)
      .loader(new AdvancedCacheLoader<Integer, Integer>() {
        @Override
        public Integer load(Integer key, long startTime,
                            CacheEntry<Integer, Integer> currentEntry) {
          if (currentEntry != null) {
            assertEquals(1, listenerCallCount.get());
            return 0;
          }
          return 1;
        }
      })
      .keepDataAfterExpired(true)
      .addListener(new CacheEntryExpiredListener<Integer, Integer>() {
        @Override
        public void onEntryExpired(Cache<Integer, Integer> c, CacheEntry<Integer, Integer> e) {
          listenerCallCount.incrementAndGet();
        }
      });
    if (sharpExpiry) {
      builder.expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
        @Override
        public long calculateExpiryTime(Integer key, Integer value, long loadTime,
                                        CacheEntry<Integer, Integer> oldEntry) {
          return loadTime + expiryMillis;
          }
        })
        .sharpExpiry(true);
    } else {
      builder.expireAfterWrite(expiryMillis, TimeUnit.MILLISECONDS);
    }
    final Cache<Integer, Integer> c = builder.build();
    final int anyKey = 1;
    await(new Condition() {
      @Override
      public boolean check() {
        return c.get(anyKey) == 0;
      }
    });
  }

  @Test
  public void simpleAsyncExpiredListenerCalledSharpExpiry() {
    testListenerCalled(true, false);
  }

  @Test
  public void asyncExpiredListenerCalledSharpExpiry() {
    testListenerCalled(true, true);
  }

  private void testListenerCalled(boolean sharp, boolean beyondSafetyGap) {
    final AtomicInteger callCount = new AtomicInteger();
    HeapCache.Tunable t = TunableFactory.get(HeapCache.Tunable.class);
    long expiryMillis =
      (beyondSafetyGap ? t.sharpExpirySafetyGapMillis : 0) + TestingParameters.MINIMAL_TICK_MILLIS;
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .addListener(new CacheEntryExpiredListener<Integer, Integer>() {
        @Override
        public void onEntryExpired(Cache<Integer, Integer> c, CacheEntry<Integer, Integer> e) {
          callCount.incrementAndGet();
        }
      })
      .expireAfterWrite(expiryMillis, TimeUnit.MILLISECONDS)
      .sharpExpiry(sharp)
      .build();
    final int anyKey = 1;
    within(expiryMillis)
      .perform(new Runnable() {
        @Override
        public void run() {
          c.put(anyKey, 4711);
        }
      })
      .expectMaybe(new Runnable() {
        @Override
        public void run() {
          assertEquals(0, callCount.get());
          assertTrue(c.containsKey(anyKey));
        }
      });
    sleep(expiryMillis * 2);
    assertThat(callCount.get(), lessThanOrEqualTo(1));
    await(new Condition() {
      @Override
      public boolean check() {
        return callCount.get() == 1;
      }
    });
  }

  @Test
  public void asyncExpiredListenerAfterRefreshCalled() {
    final AtomicInteger listenerCallCount = new AtomicInteger();
    final AtomicInteger loaderCallCount = new AtomicInteger();
    HeapCache.Tunable t = TunableFactory.get(HeapCache.Tunable.class);
    final long extraGap = TestingParameters.MINIMAL_TICK_MILLIS;
    long expiryMillis = t.sharpExpirySafetyGapMillis + extraGap;
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .addListener(new CacheEntryExpiredListener<Integer, Integer>() {
        @Override
        public void onEntryExpired(Cache<Integer, Integer> c, CacheEntry<Integer, Integer> e) {
          listenerCallCount.incrementAndGet();
        }
      })
      .expireAfterWrite(expiryMillis, TimeUnit.MILLISECONDS)
      .refreshAhead(true)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(Integer key) {
          loaderCallCount.incrementAndGet();
          return 123;
        }
      })
      .build();
    final int anyKey = 1;
    within(expiryMillis)
      .perform(new Runnable() {
        @Override
        public void run() {
          c.get(anyKey);
        }
      })
      .expectMaybe(new Runnable() {
        @Override
        public void run() {
          assertEquals(0, listenerCallCount.get());
          assertTrue(c.containsKey(anyKey));
        }
      });
    await(new Condition() {
      @Override
      public boolean check() {
        return listenerCallCount.get() == 1;
      }
    });
    assertEquals(2, loaderCallCount.get());
  }

  /**
   * We hold up the insert task with the created listener. Checks that we get an expired
   * event after the insert event. Also checks that the cache entry is not visible.
   */
  @Test
  public void expiresDuringInsert() throws InterruptedException {
    final AtomicInteger gotExpired = new AtomicInteger();
    final CountDownLatch gotCreated = new CountDownLatch(1);
    final long expiryMillis = TestingParameters.MINIMAL_TICK_MILLIS;
    final CountDownLatch waitInCreated = new CountDownLatch(1);
    final Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .addListener(new CacheEntryExpiredListener<Integer, Integer>() {
        @Override
        public void onEntryExpired(Cache<Integer, Integer> c, CacheEntry<Integer, Integer> e) {
          gotExpired.incrementAndGet();
        }
      })
      .addListener(new CacheEntryCreatedListener<Integer, Integer>() {
        @Override
        public void onEntryCreated(Cache<Integer, Integer> cache,
                                   CacheEntry<Integer, Integer> entry) {
          assertEquals(123, (long) entry.getValue());
          gotCreated.countDown();
          try {
            waitInCreated.await();
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
        }
      })
      .expireAfterWrite(expiryMillis, TimeUnit.MILLISECONDS)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(Integer key) {
          return 123;
        }
      })
      .build();
    final int anyKey = 1;
    execute(new Runnable() {
      @Override
      public void run() {
        try {
          c.get(anyKey);
        } catch (CacheClosedException ignore) {
        }
      }
    });
    gotCreated.await();
    assertFalse("entry is not visible", c.containsKey(anyKey));
    sleep(expiryMillis);
    waitInCreated.countDown();
    await(new Condition() {
      @Override
      public boolean check() {
        return gotExpired.get() > 0;
      }
    });
  }

}
