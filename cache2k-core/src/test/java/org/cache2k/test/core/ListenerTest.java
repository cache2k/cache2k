package org.cache2k.test.core;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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

  /** Provide unique standard cache per method */
  @Rule
  public IntCacheRule target = new IntCacheRule();

  @Rule
  public Timeout globalTimeout = new Timeout((int) TestingParameters.MAX_FINISH_WAIT_MILLIS);

  static abstract class CountSyncEvents extends CacheRule.Context<Integer,Integer> {

    final AtomicInteger updated = new AtomicInteger();
    final AtomicInteger removed = new AtomicInteger();
    final AtomicInteger created = new AtomicInteger();
    final AtomicInteger evicted = new AtomicInteger();

    @Override
    public void extend(final Cache2kBuilder<Integer, Integer> b) {
      b .addListener(new CacheEntryUpdatedListener<Integer, Integer>() {
          @Override
          public void onEntryUpdated(final Cache<Integer, Integer> cache, final CacheEntry<Integer, Integer> currentEntry, final CacheEntry<Integer, Integer> entryWithNewData) {
            updated.incrementAndGet();
          }
        })
        .addListener(new CacheEntryRemovedListener<Integer, Integer>() {
          @Override
          public void onEntryRemoved(final Cache<Integer, Integer> c, final CacheEntry<Integer, Integer> e) {
            removed.incrementAndGet();
          }
        })
        .addListener(new CacheEntryCreatedListener<Integer, Integer>() {
          @Override
          public void onEntryCreated(final Cache<Integer, Integer> c, final CacheEntry<Integer, Integer> e) {
            created.incrementAndGet();
          }
        })
        .addListener(new CacheEntryEvictedListener<Integer, Integer>() {
          @Override
          public void onEntryEvicted(final Cache<Integer, Integer> cache, final CacheEntry<Integer, Integer> entry) {
            evicted.incrementAndGet();
          }
        });
    }
  }

  @Test
  public void evictedListenerCalled() {
    target.run(new CountSyncEvents() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
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
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b.expireAfterWrite(0, TimeUnit.MILLISECONDS);
        b.loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(final Integer key) throws Exception {
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

  /** If the listener is not executed in separate thread, this would block */
  @Test
  public void asyncCreatedListenerCalled() {
    final AtomicInteger _callCount = new AtomicInteger();
    final CountDownLatch _fire = new CountDownLatch(1);
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b. addAsyncListener(new CacheEntryCreatedListener<Integer, Integer>() {
          @Override
          public void onEntryCreated(final Cache<Integer, Integer> c, final CacheEntry<Integer, Integer> e) {
            try {
              _fire.await();
            } catch (InterruptedException ignore) { }
            _callCount.incrementAndGet();
          }
        });
      }
    });
    c.put(1,2);
    assertEquals(0, _callCount.get());
    _fire.countDown();
    ConcurrencyHelper.await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return _callCount.get() == 1;
      }
    });
  }

  /** If the listener is not executed in separate thread, this would block */
  @Test
  public void asyncUpdateListenerCalled() {
    final AtomicInteger _callCount = new AtomicInteger();
    final CountDownLatch _fire = new CountDownLatch(1);
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
       @Override
       public void extend(final Cache2kBuilder<Integer, Integer> b) {
         b.addAsyncListener(new CacheEntryUpdatedListener<Integer, Integer>() {
           @Override
           public void onEntryUpdated(final Cache<Integer, Integer> cache,
                                      final CacheEntry<Integer, Integer> currentEntry,
                                      final CacheEntry<Integer, Integer> entryWithNewData) {
             try {
               _fire.await();
             } catch (InterruptedException ignore) {
             }
             _callCount.incrementAndGet();
           }
         });
       }
     });
    c.put(1, 2);
    assertEquals(0, _callCount.get());
    c.put(1, 2);
    assertEquals(0, _callCount.get());
    _fire.countDown();
    ConcurrencyHelper.await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return _callCount.get() == 1;
      }
    });
  }

  /** If the listener is not executed in separate thread, this would block */
  @Test
  public void asyncRemovedListenerCalled() {
    final AtomicInteger _callCount = new AtomicInteger();
    final CountDownLatch _fire = new CountDownLatch(1);
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b.addAsyncListener(new CacheEntryRemovedListener<Integer, Integer>() {
          @Override
          public void onEntryRemoved(final Cache<Integer, Integer> c, final CacheEntry<Integer, Integer> e) {
            try {
              _fire.await();
            } catch (InterruptedException ignore) {
            }
            _callCount.incrementAndGet();
          }
        });
      }
    });
    c.put(1, 2);
    assertEquals(0, _callCount.get());
    c.put(1, 2);
    assertEquals(0, _callCount.get());
    c.remove(1);
    assertEquals(0, _callCount.get());
    _fire.countDown();
    ConcurrencyHelper.await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return _callCount.get() == 1;
      }
    });
  }

  /** If the listener is not executed in separate thread, this would block */
  @Test
  public void asyncEvictedListenerCalled() {
    final AtomicInteger _callCount = new AtomicInteger();
    final CountDownLatch _fire = new CountDownLatch(1);
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b.addAsyncListener(new CacheEntryEvictedListener<Integer, Integer>() {
          @Override
          public void onEntryEvicted(final Cache<Integer, Integer> c, final CacheEntry<Integer, Integer> e) {
            try {
              _fire.await();
            } catch (InterruptedException ignore) {
            }
            _callCount.incrementAndGet();
          }
        })
        .entryCapacity(1);
      }
    });
    c.put(1, 2);
    assertEquals(0, _callCount.get());
    c.put(2, 2);
    assertEquals(0, _callCount.get());
    _fire.countDown();
    ConcurrencyHelper.await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return _callCount.get() == 1;
      }
    });
  }

  /** Check that we do not miss events. */
  @Test
  public void manyAsyncUpdateListenerCalled() {
    final AtomicInteger _callCount = new AtomicInteger();
    final ConcurrentMap<Integer, Integer> _seenValues = new ConcurrentHashMap<Integer, Integer>();
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b .addAsyncListener(new CacheEntryUpdatedListener<Integer, Integer>() {
          @Override
          public void onEntryUpdated(final Cache<Integer, Integer> cache, final CacheEntry<Integer, Integer> currentEntry, final CacheEntry<Integer, Integer> entryWithNewData) {
            _seenValues.put(entryWithNewData.getValue(), entryWithNewData.getValue());
            _callCount.incrementAndGet();
          }
        });
      }
    });
    c.put(1, 2);
    assertEquals(0, _callCount.get());
    final int _UPDATE_COUNT = 123;
    for (int i = 0; i < _UPDATE_COUNT; i++) {
      c.put(1, i);
    }
    ConcurrencyHelper.await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return _callCount.get() == _UPDATE_COUNT;
      }
    });
    assertEquals("Event dispatching is using copied events", 123, _seenValues.size());
  }

  @Test(expected = Exception.class)
  public void updateListenerException() {
    Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b.addListener(new CacheEntryUpdatedListener<Integer, Integer>() {
          @Override
          public void onEntryUpdated(final Cache<Integer, Integer> cache, final CacheEntry<Integer, Integer> currentEntry, final CacheEntry<Integer, Integer> entryWithNewData) {
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
    String _logName = getClass().getName() + ".asyncUpdateListenerException";
    final Log.SuppressionCounter _suppressionCounter = new Log.SuppressionCounter();
    Log.registerSuppression("org.cache2k.Cache/default:" + _logName, _suppressionCounter);
    Cache<Integer, Integer> c =
      Cache2kBuilder.of(Integer.class, Integer.class)
        .name(_logName)
        .eternal(true)
        .addAsyncListener(new CacheEntryUpdatedListener<Integer, Integer>() {
          @Override
          public void onEntryUpdated(
            final Cache<Integer, Integer> cache,
            final CacheEntry<Integer, Integer> currentEntry,
            final CacheEntry<Integer, Integer> entryWithNewData) {
            throw new RuntimeException("ouch");
          }
        })
        .build();
    c.put(1, 2);
    c.put(1, 2);
    ConcurrencyHelper.await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return _suppressionCounter.getWarnCount() == 1;
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
    final AtomicInteger _expireCallCount = new AtomicInteger();
    final Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
       @Override
       public void extend(final Cache2kBuilder<Integer, Integer> b) {
         b.addAsyncListener(new CacheEntryExpiredListener<Integer, Integer>() {
           @Override
           public void onEntryExpired(final Cache<Integer, Integer> c, final CacheEntry<Integer, Integer> e) {
             _expireCallCount.incrementAndGet();
           }
         })
           .eternal(true)
           .keepDataAfterExpired(false)
           .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
             @Override
             public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
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
        return _expireCallCount.get() == 1;
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
    final AtomicInteger _expireCallCount = new AtomicInteger();
    final Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b .addAsyncListener(new CacheEntryExpiredListener<Integer, Integer>() {
            @Override
            public void onEntryExpired(final Cache<Integer, Integer> c, final CacheEntry<Integer, Integer> e) {
              _expireCallCount.incrementAndGet();
            }
          })
          .eternal(true)
          .keepDataAfterExpired(false)
          .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
            @Override
            public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
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
        return _expireCallCount.get() == 1;
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
    final AtomicInteger _expireCallCount = new AtomicInteger();
    final Cache<Integer, Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b .addListener(new CacheEntryExpiredListener<Integer, Integer>() {
          @Override
          public void onEntryExpired(final Cache<Integer, Integer> c, final CacheEntry<Integer, Integer> e) {
            _expireCallCount.incrementAndGet();
          }
        })
          .eternal(true)
          .keepDataAfterExpired(false)
          .expiryPolicy(new ExpiryPolicy<Integer, Integer>() {
            @Override
            public long calculateExpiryTime(final Integer key, final Integer value, final long loadTime, final CacheEntry<Integer, Integer> oldEntry) {
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
    assertEquals(1, _expireCallCount.get());
    assertEquals(0, latestInfo(c).getSize());
    assertEquals(1, latestInfo(c).getExpiredCount());
  }

  @Test
  public void listenerExampleForDocumentation() {
    Cache2kBuilder.of(Integer.class, Integer.class)
      .addListener(new CacheEntryCreatedListener<Integer, Integer>() {
        @Override
        public void onEntryCreated(final Cache<Integer, Integer> cache, final CacheEntry<Integer, Integer> entry) {
          System.err.println("inserted: " + entry.getValue());
        }
      });
  }

  @Test
  public void customExecutor() {
    final AtomicInteger _counter = new AtomicInteger();
    Cache<Integer, Integer> c =
      Cache2kBuilder.of(Integer.class, Integer.class)
        .addAsyncListener(new CacheEntryCreatedListener<Integer, Integer>() {
          @Override
          public void onEntryCreated(final Cache<Integer, Integer> cache, final CacheEntry<Integer, Integer> entry) {
          }
        })
        .asyncListenerExecutor(new Executor() {
          @Override
          public void execute(final Runnable command) {
            _counter.incrementAndGet();
          }
        })
        .build();
    c.put(1,2);
    c.close();
    assertEquals(1, _counter.get());
  }

}
