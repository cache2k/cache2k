package org.cache2k.test.core;

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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.core.CacheClosedException;
import org.cache2k.core.InternalCache;
import org.cache2k.core.util.SimulatedClock;
import org.cache2k.event.CacheEntryEvictedListener;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.integration.CacheLoader;
import org.cache2k.test.util.CacheRule;
import org.cache2k.test.util.ConcurrencyHelper;
import org.cache2k.test.util.Condition;
import org.cache2k.test.util.IntCacheRule;
import org.cache2k.event.CacheEntryCreatedListener;
import org.cache2k.event.CacheEntryExpiredListener;
import org.cache2k.event.CacheEntryRemovedListener;
import org.cache2k.event.CacheEntryUpdatedListener;
import org.cache2k.core.util.Log;
import org.cache2k.test.util.TestingBase;
import org.cache2k.testing.category.FastTests;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.Timeout;

import static org.junit.Assert.*;
import static org.cache2k.test.core.StaticUtil.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests that all variants of listeners get called, except tests for expiry listener
 * that depend on time.
 *
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class ListenerTest {

  /**
   * Provide unique standard cache per method
   */
  @Rule
  public IntCacheRule target = new IntCacheRule();

  @Rule
  public Timeout globalTimeout = new Timeout((int) TestingParameters.MAX_FINISH_WAIT_MILLIS);

  /**
   * containsKey would return true for an entry currently being evicted.
   * A mutation operation would block. Check the internal entry state.
   */
  public static <K> boolean isEvicting(Cache<K, ?> c, K key) {
    InternalCache<K, ?> ic = (InternalCache<K, ?>) c;
    return ic.getEntryState(key).contains("lock=EVICT");
  }

  @Test
  public void evictedListenerCalled() {
    target.run(new CountSyncEvents() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        super.extend(b);
        b.entryCapacity(1);
      }

      @Override
      public void run() {
        assertEquals(0, evicted.get());
        cache.put(1, 2);
        assertEquals(0, evicted.get());
        cache.put(2, 2);
        assertEquals(1, evicted.get());
      }
    });

  }

  /**
   * @see ChangeCapacityOrResizeTest
   */
  @Test
  public void evictedListenerCalledOnChangeCapacity() {
    target.run(new CountSyncEvents() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        super.extend(b);
        b.entryCapacity(10);
      }

      @Override
      public void run() {
        cache.put(1, 2);
        cache.put(2, 2);
        cache.put(3, 2);
        assertEquals(0, evicted.get());
        ((InternalCache) cache).getEviction().changeCapacity(1);
        assertEquals(2, evicted.get());
      }

    });

  }

  @Test
  public void createdListenerCalled() {
    target.run(new CountSyncEvents() {
      @Override
      public void run() {
        assertEquals(0, created.get());
        cache.put(1, 2);
        assertEquals(1, created.get());
      }
    });
  }

  @Test
  public void createdListenerNotCalledForImmediateExpiry() {
    target.run(new CountSyncEvents() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.expireAfterWrite(0, TimeUnit.MILLISECONDS);
        b.loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(Integer key) throws Exception {
            return key;
          }
        });
        super.extend(b);
      }

      @Override
      public void run() {
        assertEquals(0, created.get());
        cache.put(1, 2);
        assertEquals(0, created.get());
        assertEquals(123, (int) cache.get(123));
        assertEquals(0, created.get());
        assertEquals(0, expired.get());
      }
    });
  }

  @Test
  public void updateListenerCalled() {
    target.run(new CountSyncEvents() {
      @Override
      public void run() {
        cache.put(1, 2);
        assertEquals(0, updated.get());
        cache.put(1, 2);
        assertEquals(1, updated.get());
      }
    });
  }

  @Test
  public void removedListenerCalled() {
    target.run(new CountSyncEvents() {
      @Override
      public void run() {
        cache.put(1, 2);
        assertEquals(0, removed.get());
        cache.put(1, 2);
        assertEquals(0, removed.get());
        cache.remove(1);
        assertEquals(1, removed.get());
      }
    });
  }

  /**
   * If the listener is not executed in separate thread, this would block
   */
  @Test
  public void asyncCreatedListenerCalled() {
    final AtomicInteger callCount = new AtomicInteger();
    final CountDownLatch fire = new CountDownLatch(1);
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.addAsyncListener(new CacheEntryCreatedListener<Integer, Integer>() {
          @Override
          public void onEntryCreated(Cache<Integer, Integer> c, CacheEntry<Integer, Integer> e) {
            try {
              fire.await();
            } catch (InterruptedException ignore) {
            }
            callCount.incrementAndGet();
          }
        });
      }
    });
    c.put(1, 2);
    assertEquals(0, callCount.get());
    fire.countDown();
    ConcurrencyHelper.await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return callCount.get() == 1;
      }
    });
  }

  /**
   * If the listener is not executed in separate thread, this would block
   */
  @Test
  public void asyncUpdateListenerCalled() {
    final AtomicInteger callCount = new AtomicInteger();
    final CountDownLatch fire = new CountDownLatch(1);
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.addAsyncListener(new CacheEntryUpdatedListener<Integer, Integer>() {
          @Override
          public void onEntryUpdated(Cache<Integer, Integer> cache,
                                     CacheEntry<Integer, Integer> currentEntry,
                                     CacheEntry<Integer, Integer> entryWithNewData) {
            try {
              fire.await();
            } catch (InterruptedException ignore) {
            }
            callCount.incrementAndGet();
          }
        });
      }
    });
    c.put(1, 2);
    assertEquals(0, callCount.get());
    c.put(1, 2);
    assertEquals(0, callCount.get());
    fire.countDown();
    ConcurrencyHelper.await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return callCount.get() == 1;
      }
    });
  }

  /**
   * If the listener is not executed in separate thread, this would block
   */
  @Test
  public void asyncRemovedListenerCalled() {
    final AtomicInteger callCount = new AtomicInteger();
    final CountDownLatch fire = new CountDownLatch(1);
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.addAsyncListener(new CacheEntryRemovedListener<Integer, Integer>() {
          @Override
          public void onEntryRemoved(Cache<Integer, Integer> c, CacheEntry<Integer, Integer> e) {
            try {
              fire.await();
            } catch (InterruptedException ignore) {
            }
            callCount.incrementAndGet();
          }
        });
      }
    });
    c.put(1, 2);
    assertEquals(0, callCount.get());
    c.put(1, 2);
    assertEquals(0, callCount.get());
    c.remove(1);
    assertEquals(0, callCount.get());
    fire.countDown();
    ConcurrencyHelper.await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return callCount.get() == 1;
      }
    });
  }

  /**
   * If the listener is not executed in separate thread, this would block
   */
  @Test
  public void asyncEvictedListenerCalled() {
    final AtomicInteger callCount = new AtomicInteger();
    final CountDownLatch block = new CountDownLatch(1);
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.addAsyncListener(new CacheEntryEvictedListener<Integer, Integer>() {
          @Override
          public void onEntryEvicted(Cache<Integer, Integer> c, CacheEntry<Integer, Integer> e) {
            try {
              block.await();
            } catch (InterruptedException ignore) {
            }
            callCount.incrementAndGet();
          }
        })
          .entryCapacity(1);
      }
    });
    c.put(1, 2);
    assertEquals(0, callCount.get());
    c.put(2, 2);
    assertEquals(0, callCount.get());
    c.put(1, 2);
    assertEquals(0, callCount.get());
    c.put(2, 2);
    assertEquals(0, callCount.get());

    block.countDown();
    ConcurrencyHelper.await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return callCount.get() >= 1;
      }
    });
  }

  @Test
  public void syncEvictedListenerDoesNotBlockCacheOps() {
    final AtomicInteger callCount = new AtomicInteger();
    final CountDownLatch block = new CountDownLatch(1);
    final Cache<Integer, Integer> c = target.cache(
      new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.addListener(new CacheEntryEvictedListener<Integer, Integer>() {
          @Override
          public void onEntryEvicted(Cache<Integer, Integer> c, CacheEntry<Integer, Integer> e) {
            callCount.incrementAndGet();
            try {
              block.await();
            } catch (InterruptedException ignore) {
            }
          }
        })
          .entryCapacity(2);
      }
    });
    c.put(1, 2);
    assertEquals(0, callCount.get());
    c.put(2, 2);
    assertEquals(0, callCount.get());
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          c.put(3, 2);
        } catch (CacheClosedException likelyIgnore) {
        }
      }
    });
    t.start();
    ConcurrencyHelper.await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return callCount.get() >= 1;
      }
    });
    if (isEvicting(c, 1)) {
      assertTrue(c.containsKey(2));
      c.put(2, 88); // update works!
      c.remove(2);
    } else {
      assertTrue(c.containsKey(1));
      c.put(1, 88); // update works!
      c.remove(1);
    }
    block.countDown();
  }

  /**
   * Check that we do not miss events.
   */
  @Test
  public void manyAsyncUpdateListenerCalled() {
    final AtomicInteger callCount = new AtomicInteger();
    final ConcurrentMap<Integer, Integer> seenValues = new ConcurrentHashMap<Integer, Integer>();
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.addAsyncListener(
          new CacheEntryUpdatedListener<Integer, Integer>() {
          @Override
          public void onEntryUpdated(Cache<Integer, Integer> cache,
                                     CacheEntry<Integer, Integer> currentEntry,
                                     CacheEntry<Integer, Integer> entryWithNewData) {
            seenValues.put(entryWithNewData.getValue(), entryWithNewData.getValue());
            callCount.incrementAndGet();
          }
        });
      }
    });
    c.put(1, 2);
    assertEquals(0, callCount.get());
    final int updateCount = 123;
    for (int i = 0; i < updateCount; i++) {
      c.put(1, i);
    }
    ConcurrencyHelper.await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return callCount.get() == updateCount;
      }
    });
    assertEquals("Event dispatching is using copied events", 123, seenValues.size());
  }

  @Test(expected = Exception.class)
  public void updateListenerException() {
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.addListener(new CacheEntryUpdatedListener<Integer, Integer>() {
          @Override
          public void onEntryUpdated(Cache<Integer, Integer> cache,
                                     CacheEntry<Integer, Integer> currentEntry,
                                     CacheEntry<Integer, Integer> entryWithNewData) {
            throw new RuntimeException("ouch");
          }
        });
      }
    });
    try {
      c.put(1, 2);
      c.put(1, 2);
    } finally {
      c.close();
    }
  }

  @Test
  public void asyncUpdateListenerException() {
    String logName = getClass().getName() + ".asyncUpdateListenerException";
    final Log.SuppressionCounter suppressionCounter = new Log.SuppressionCounter();
    Log.registerSuppression("org.cache2k.Cache/default:" + logName, suppressionCounter);
    Cache<Integer, Integer> c =
      Cache2kBuilder.of(Integer.class, Integer.class)
        .name(logName)
        .eternal(true)
        .addAsyncListener(new CacheEntryUpdatedListener<Integer, Integer>() {
          @Override
          public void onEntryUpdated(
            Cache<Integer, Integer> cache,
            CacheEntry<Integer, Integer> currentEntry,
            CacheEntry<Integer, Integer> entryWithNewData) {
            throw new RuntimeException("ouch");
          }
        })
        .build();
    c.put(1, 2);
    c.put(1, 2);
    ConcurrencyHelper.await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return suppressionCounter.getWarnCount() == 1;
      }
    });
    c.close();
  }

  /**
   * Expire time is 0 if entry is modified, yields: Expiry listener is called and entry
   * is removed from cache.
   */
  @Test
  public void asyncReallyExpiredAfterUpdate() {
    final AtomicInteger expireCallCount = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.addAsyncListener(new CacheEntryExpiredListener<Integer, Integer>() {
          @Override
          public void onEntryExpired(Cache<Integer, Integer> c, CacheEntry<Integer, Integer> e) {
            expireCallCount.incrementAndGet();
          }
        })
          .eternal(true)
          .keepDataAfterExpired(false)
          .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
            @Override
            public long calculateExpiryTime(Integer key, Integer value,
                                            long loadTime,
                                            CacheEntry<Integer, Integer> oldEntry) {
              if (oldEntry != null) {
                return 0;
              }
              return ETERNAL;
            }
          });
      }
    });
    c.put(1, 1);
    c.put(1, 2);
    ConcurrencyHelper.await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return expireCallCount.get() == 1;
      }
    });
    assertEquals(0, latestInfo(c).getSize());
  }

  /**
   * Expire time is load time if entry is modified, yields: Expiry listener is called. Entry
   * is removed.
   */
  @Test
  public void asyncExpiredAfterUpdate() {
    final AtomicInteger expireCallCount = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.addAsyncListener(new CacheEntryExpiredListener<Integer, Integer>() {
          @Override
          public void onEntryExpired(Cache<Integer, Integer> c, CacheEntry<Integer, Integer> e) {
            expireCallCount.incrementAndGet();
          }
        })
          .eternal(true)
          .keepDataAfterExpired(false)
          .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
            @Override
            public long calculateExpiryTime(Integer key, Integer value,
                                            long loadTime, CacheEntry<Integer, Integer> oldEntry) {
              if (oldEntry != null) {
                return loadTime;
              }
              return ETERNAL;
            }
          });
      }
    });
    c.put(1, 1);
    c.put(1, 2);
    ConcurrencyHelper.await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return expireCallCount.get() == 1;
      }
    });
    assertEquals(0, latestInfo(c).getSize());
    assertEquals(1, latestInfo(c).getExpiredCount());
  }

  /**
   * Expire time is load time if entry is modified, yields: Expiry listener is called. Entry
   * is removed.
   */
  @Test
  public void syncExpiredAfterUpdate() {
    final AtomicInteger expireCallCount = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.addListener(new CacheEntryExpiredListener<Integer, Integer>() {
          @Override
          public void onEntryExpired(Cache<Integer, Integer> c, CacheEntry<Integer, Integer> e) {
            expireCallCount.incrementAndGet();
          }
        })
          .eternal(true)
          .keepDataAfterExpired(false)
          .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
            @Override
            public long calculateExpiryTime(Integer key, Integer value,
                                            long loadTime, CacheEntry<Integer, Integer> oldEntry) {
              if (oldEntry != null) {
                return loadTime;
              }
              return ETERNAL;
            }
          });
      }
    });
    c.put(1, 1);
    c.put(1, 2);
    assertEquals(1, expireCallCount.get());
    assertEquals(0, latestInfo(c).getSize());
    assertEquals(1, latestInfo(c).getExpiredCount());
  }

  @Test
  public void listenerExampleForDocumentation() {
    Cache2kBuilder.of(Integer.class, Integer.class)
      .addListener(new CacheEntryCreatedListener<Integer, Integer>() {
        @Override
        public void onEntryCreated(Cache<Integer, Integer> cache,
                                   CacheEntry<Integer, Integer> entry) {
          System.err.println("inserted: " + entry.getValue());
        }
      });
  }

  @Test
  public void customExecutor() {
    final AtomicInteger counter = new AtomicInteger();
    Cache<Integer, Integer> c =
      Cache2kBuilder.of(Integer.class, Integer.class)
        .addAsyncListener(new CacheEntryCreatedListener<Integer, Integer>() {
          @Override
          public void onEntryCreated(Cache<Integer, Integer> cache,
                                     CacheEntry<Integer, Integer> entry) {
          }
        })
        .asyncListenerExecutor(new Executor() {
          @Override
          public void execute(Runnable command) {
            counter.incrementAndGet();
          }
        })
        .build();
    c.put(1, 2);
    c.close();
    assertEquals(1, counter.get());
  }

  /**
   * The reference point in time for the expiry is the start time of the operation.
   * In case the operation takes longer then the expiry timespan the entry would
   * expire at the end of the operation. That is a special case, since if the entry is
   * expired already the timer event will not be scheduled.
   */
  @Test
  public void expiredWhileInitialLoad() {
    final long expireAfterWrite = 123;
    final SimulatedClock clock = new SimulatedClock(1000, true);
    target.run(new CountSyncEvents() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.timeReference(clock);
        b.expireAfterWrite(expireAfterWrite, TimeUnit.MILLISECONDS);
        b.loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(Integer key) throws Exception {
            clock.sleep(expireAfterWrite * 2);
            return key;
          }
        });
        super.extend(b);
      }

      @Override
      public void run() {
        cache.get(123);
        assertEquals("created", 1, created.get());
        assertEquals("expired", 1, expired.get());
      }
    });

  }

  /**
   * No expiry event during load. Entry expires at end of load operation.
   */
  @Test
  public void expiredWhileReload() {
    final long expireAfterWrite = 123;
    final SimulatedClock clock = new SimulatedClock(1000, true);
    target.run(new CountSyncEvents() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.timeReference(clock);
        b.expireAfterWrite(expireAfterWrite, TimeUnit.MILLISECONDS);
        b.loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(Integer key) throws Exception {
            clock.sleep(expireAfterWrite * 2);
            return key;
          }
        });
        super.extend(b);
      }

      @Override
      public void run() {
        cache.put(123, 5);
        assertEquals("created", 1, created.get());
        TestingBase.reload(cache, 123);
        assertEquals("expired", 1, expired.get());
      }
    });
  }

  /**
   * The value expires during load operation. Since the entry is not removed but locked
   * for the load an update is sent.
   */
  @Test
  public void expiredWhileReloadUpdate() {
    final long expireAfterWrite = 60;
    final SimulatedClock clock = new SimulatedClock(1000, true);
    target.run(new CountSyncEvents() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.timeReference(clock);
        b.expireAfterWrite(expireAfterWrite, TimeUnit.MILLISECONDS);
        b.loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(Integer key) throws Exception {
            clock.sleep(expireAfterWrite / 3 * 2);
            return key;
          }
        });
        super.extend(b);
      }

      @Override
      public void run() {
        cache.put(123, 5);
        try {
          clock.sleep(expireAfterWrite / 3 * 2);
        } catch (InterruptedException ignore) {
        }
        assertEquals("created", 1, created.get());
        TestingBase.reload(cache, 123);
        assertEquals("updated", 1, updated.get());
      }
    });
  }

  abstract static class CountSyncEvents extends CacheRule.Context<Integer, Integer> {

    final AtomicInteger updated = new AtomicInteger();
    final AtomicInteger removed = new AtomicInteger();
    final AtomicInteger created = new AtomicInteger();
    final AtomicInteger evicted = new AtomicInteger();
    final AtomicInteger expired = new AtomicInteger();

    private void checkCondition() {
      assertTrue("Get a created event before something is removed, evicted or expired",
        created.get() >= (evicted.get() + expired.get() + removed.get()));
    }

    @Override
    public void extend(Cache2kBuilder<Integer, Integer> b) {
      b.addListener(new CacheEntryCreatedListener<Integer, Integer>() {
        @Override
        public void onEntryCreated(Cache<Integer, Integer> c, CacheEntry<Integer, Integer> e) {
          created.incrementAndGet();
        }
      })
        .addListener(new CacheEntryUpdatedListener<Integer, Integer>() {
          @Override
          public void onEntryUpdated(Cache<Integer, Integer> cache,
                                     CacheEntry<Integer, Integer> currentEntry,
                                     CacheEntry<Integer, Integer> entryWithNewData) {
            updated.incrementAndGet();
          }
        })
        .addListener(new CacheEntryRemovedListener<Integer, Integer>() {
          @Override
          public void onEntryRemoved(Cache<Integer, Integer> c, CacheEntry<Integer, Integer> e) {
            removed.incrementAndGet();
            checkCondition();
          }
        })
        .addListener(new CacheEntryEvictedListener<Integer, Integer>() {
          @Override
          public void onEntryEvicted(Cache<Integer, Integer> cache,
                                     CacheEntry<Integer, Integer> entry) {
            evicted.incrementAndGet();
            checkCondition();
          }
        })
        .addListener(new CacheEntryExpiredListener<Integer, Integer>() {
          @Override
          public void onEntryExpired(Cache<Integer, Integer> cache,
                                     CacheEntry<Integer, Integer> entry) {
            expired.incrementAndGet();
            checkCondition();
          }
        });
    }
  }

}
