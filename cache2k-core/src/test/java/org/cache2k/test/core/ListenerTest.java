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
import org.cache2k.core.CacheClosedException;
import org.cache2k.core.api.InternalCache;
import org.cache2k.testing.SimulatedClock;
import org.cache2k.event.CacheEntryEvictedListener;
import org.cache2k.test.util.CacheRule;
import org.cache2k.test.util.Condition;
import org.cache2k.test.util.IntCacheRule;
import org.cache2k.event.CacheEntryCreatedListener;
import org.cache2k.event.CacheEntryExpiredListener;
import org.cache2k.event.CacheEntryRemovedListener;
import org.cache2k.event.CacheEntryUpdatedListener;
import org.cache2k.core.log.Log;
import org.cache2k.test.util.TimeStepper;
import org.cache2k.testing.category.FastTests;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.Timeout;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.cache2k.Cache2kBuilder.of;
import static org.cache2k.expiry.ExpiryTimeValues.ETERNAL;
import static org.cache2k.test.core.StaticUtil.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
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
        assertThat(evicted.get()).isEqualTo(0);
        cache.put(1, 2);
        assertThat(evicted.get()).isEqualTo(0);
        cache.put(2, 2);
        assertThat(evicted.get()).isEqualTo(1);
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
        assertThat(evicted.get()).isEqualTo(0);
        ((InternalCache) cache).getEviction().changeCapacity(1);
        assertThat(evicted.get()).isEqualTo(2);
      }

    });

  }

  @Test
  public void createdListenerCalled() {
    target.run(new CountSyncEvents() {
      @Override
      public void run() {
        assertThat(created.get()).isEqualTo(0);
        cache.put(1, 2);
        assertThat(created.get()).isEqualTo(1);
      }
    });
  }

  @Test
  public void createdListenerNotCalledForImmediateExpiry() {
    target.run(new CountSyncEvents() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.expireAfterWrite(0, TimeUnit.MILLISECONDS);
        b.loader(key -> key);
        super.extend(b);
      }

      @Override
      public void run() {
        assertThat(created.get()).isEqualTo(0);
        cache.put(1, 2);
        assertThat(created.get()).isEqualTo(0);
        assertThat((int) cache.get(123)).isEqualTo(123);
        assertThat(created.get()).isEqualTo(0);
        assertThat(expired.get()).isEqualTo(0);
      }
    });
  }

  @Test
  public void updateListenerCalled() {
    target.run(new CountSyncEvents() {
      @Override
      public void run() {
        cache.put(1, 2);
        assertThat(updated.get()).isEqualTo(0);
        cache.put(1, 2);
        assertThat(updated.get()).isEqualTo(1);
      }
    });
  }

  @Test
  public void removedListenerCalled() {
    target.run(new CountSyncEvents() {
      @Override
      public void run() {
        cache.put(1, 2);
        assertThat(removed.get()).isEqualTo(0);
        cache.put(1, 2);
        assertThat(removed.get()).isEqualTo(0);
        cache.remove(1);
        assertThat(removed.get()).isEqualTo(1);
      }
    });
  }

  /**
   * If the listener is not executed in separate thread, this would block
   */
  @Test
  public void asyncCreatedListenerCalled() {
    AtomicInteger callCount = new AtomicInteger();
    CountDownLatch fire = new CountDownLatch(1);
    Cache<Integer, Integer> c = target.cache(b -> b.addAsyncListener((CacheEntryCreatedListener<Integer, Integer>) (c1, e) -> {
      try {
        fire.await();
      } catch (InterruptedException ignore) {
      }
      callCount.incrementAndGet();
    }));
    c.put(1, 2);
    assertThat(callCount.get()).isEqualTo(0);
    fire.countDown();
    await(() -> callCount.get() == 1);
  }

  public void await(Condition c) {
    new TimeStepper(target.getClock()).await(c);
  }

  /**
   * If the listener is not executed in separate thread, this would block
   */
  @Test
  public void asyncUpdateListenerCalled() {
    AtomicInteger callCount = new AtomicInteger();
    CountDownLatch fire = new CountDownLatch(1);
    Cache<Integer, Integer> c = target.cache(b -> b.addAsyncListener((CacheEntryUpdatedListener<Integer, Integer>) (cache, currentEntry, newEntry) -> {
      try {
        fire.await();
      } catch (InterruptedException ignore) {
      }
      callCount.incrementAndGet();
    }));
    c.put(1, 2);
    assertThat(callCount.get()).isEqualTo(0);
    c.put(1, 2);
    assertThat(callCount.get()).isEqualTo(0);
    fire.countDown();
    await(() -> callCount.get() == 1);
  }

  /**
   * If the listener is not executed in separate thread, this would block
   */
  @Test
  public void asyncRemovedListenerCalled() {
    AtomicInteger callCount = new AtomicInteger();
    CountDownLatch fire = new CountDownLatch(1);
    Cache<Integer, Integer> c = target.cache(b -> b.addAsyncListener((CacheEntryRemovedListener<Integer, Integer>) (c1, e) -> {
      try {
        fire.await();
      } catch (InterruptedException ignore) {
      }
      callCount.incrementAndGet();
    }));
    c.put(1, 2);
    assertThat(callCount.get()).isEqualTo(0);
    c.put(1, 2);
    assertThat(callCount.get()).isEqualTo(0);
    c.remove(1);
    assertThat(callCount.get()).isEqualTo(0);
    fire.countDown();
    await(() -> callCount.get() == 1);
  }

  /**
   * If the listener is not executed in separate thread, this would block
   */
  @Test
  public void asyncEvictedListenerCalled() {
    AtomicInteger callCount = new AtomicInteger();
    CountDownLatch block = new CountDownLatch(1);
    Cache<Integer, Integer> c = target.cache(b -> b.addAsyncListener((CacheEntryEvictedListener<Integer, Integer>) (c1, e) -> {
        try {
          block.await();
        } catch (InterruptedException ignore) {
        }
        callCount.incrementAndGet();
      })
      .entryCapacity(1));
    c.put(1, 2);
    assertThat(callCount.get()).isEqualTo(0);
    c.put(2, 2);
    assertThat(callCount.get()).isEqualTo(0);
    c.put(1, 2);
    assertThat(callCount.get()).isEqualTo(0);
    c.put(2, 2);
    assertThat(callCount.get()).isEqualTo(0);

    block.countDown();
    await(() -> callCount.get() >= 1);
  }

  @Test
  public void syncEvictedListenerDoesNotBlockCacheOps() {
    AtomicInteger callCount = new AtomicInteger();
    CountDownLatch block = new CountDownLatch(1);
    Cache<Integer, Integer> c = target.cache(
      b -> b.addListener((CacheEntryEvictedListener<Integer, Integer>) (c1, e) -> {
          callCount.incrementAndGet();
          try {
            block.await();
          } catch (InterruptedException ignore) {
          }
        })
        .entryCapacity(2));
    c.put(1, 2);
    assertThat(callCount.get()).isEqualTo(0);
    c.put(2, 2);
    assertThat(callCount.get()).isEqualTo(0);
    Thread t = new Thread(() -> {
      try {
        c.put(3, 2);
      } catch (CacheClosedException likelyIgnore) {
      }
    });
    t.start();
    await(() -> callCount.get() >= 1);
    if (isEvicting(c, 1)) {
      assertThat(c.containsKey(2)).isTrue();
      c.put(2, 88); // update works!
      c.remove(2);
    } else {
      assertThat(c.containsKey(1)).isTrue();
      c.put(1, 88); // update works!
      c.remove(1);
    }
    block.countDown();
  }

  /**
   * Call Cache.toString() within eviction listener.
   * ToString will do a global cache lock. This is proof that eviction listener
   * is only locking on the entry not parts of the cache structure.
   */
  @Test
  public void syncEvictedToString() {
    AtomicInteger callCount = new AtomicInteger();
    CountDownLatch block = new CountDownLatch(1);
    Cache<Integer, Integer> c = target.cache(
      b -> b.addListener((CacheEntryEvictedListener<Integer, Integer>) (c1, e) -> {
          c1.toString();
          callCount.incrementAndGet();
        })
        .entryCapacity(2));
    c.put(1, 2);
    assertThat(callCount.get()).isEqualTo(0);
    c.put(2, 2);
    assertThat(callCount.get()).isEqualTo(0);
    c.put(3, 2);
    assertThat(callCount.get()).isEqualTo(1);
  }

  /**
   * Check that we do not miss events.
   */
  @Test
  public void manyAsyncUpdateListenerCalled() {
    AtomicInteger callCount = new AtomicInteger();
    ConcurrentMap<Integer, Integer> seenValues = new ConcurrentHashMap<>();
    Cache<Integer, Integer> c = target.cache(b -> b.addAsyncListener(
      (CacheEntryUpdatedListener<Integer, Integer>) (cache, currentEntry, newEntry) -> {
        seenValues.put(newEntry.getValue(), newEntry.getValue());
        callCount.incrementAndGet();
      }));
    c.put(1, 2);
    assertThat(callCount.get()).isEqualTo(0);
    final int updateCount = 123;
    for (int i = 0; i < updateCount; i++) {
      c.put(1, i);
    }
    await(() -> callCount.get() == updateCount);
    assertThat(seenValues.size())
      .as("Event dispatching is using copied events")
      .isEqualTo(123);
  }

  @Test(expected = Exception.class)
  public void updateListenerException() {
    try (Cache<Integer, Integer> c = target.cache(b ->
      b.addListener((CacheEntryUpdatedListener<Integer, Integer>) (cache, currentEntry, newEntry) -> {
      throw new RuntimeException("ouch");
    }))) {
      c.put(1, 2);
      c.put(1, 2);
    }
  }

  @Test
  public void asyncUpdateListenerException() {
    String logName = getClass().getName() + ".asyncUpdateListenerException";
    Log.SuppressionCounter suppressionCounter = new Log.SuppressionCounter();
    Log.registerSuppression("org.cache2k.Cache/default:" + logName, suppressionCounter);
    Cache<Integer, Integer> c = target.cache(b -> b.name(logName)
      .eternal(true)
      .addAsyncListener((CacheEntryUpdatedListener<Integer, Integer>) (cache, currentEntry, newEntry) -> {
        throw new RuntimeException("ouch");
      }));
    c.put(1, 2);
    c.put(1, 2);
    await(() -> suppressionCounter.getWarnCount() == 1);
    c.close();
  }

  /**
   * Expire time is 0 if entry is modified, yields: Expiry listener is called and entry
   * is removed from cache.
   */
  @Test
  public void asyncReallyExpiredAfterUpdate() {
    AtomicInteger expireCallCount = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(b -> b.addAsyncListener((CacheEntryExpiredListener<Integer, Integer>) (c1, e) -> expireCallCount.incrementAndGet())
      .eternal(true)
      .keepDataAfterExpired(false)
      .expiryPolicy((key, value, startTime, currentEntry) -> {
        if (currentEntry != null) {
          return 0;
        }
        return ETERNAL;
      }));
    c.put(1, 1);
    c.put(1, 2);
    await(() -> expireCallCount.get() == 1);
    assertThat(latestInfo(c).getSize()).isEqualTo(0);
  }

  /**
   * Expire time is load time if entry is modified, yields: Expiry listener is called. Entry
   * is removed.
   */
  @Test
  public void asyncExpiredAfterUpdate() {
    AtomicInteger expireCallCount = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(b -> b.addAsyncListener((CacheEntryExpiredListener<Integer, Integer>) (c1, e) -> expireCallCount.incrementAndGet())
      .eternal(true)
      .keepDataAfterExpired(false)
      .expiryPolicy((key, value, startTime, currentEntry) -> {
        if (currentEntry != null) {
          return startTime;
        }
        return ETERNAL;
      }));
    c.put(1, 1);
    c.put(1, 2);
    await(() -> expireCallCount.get() == 1);
    assertThat(latestInfo(c).getSize()).isEqualTo(0);
    assertThat(latestInfo(c).getExpiredCount()).isEqualTo(1);
  }

  /**
   * Expire time is load time if entry is modified, yields: Expiry listener is called. Entry
   * is removed.
   */
  @Test
  public void syncExpiredAfterUpdate() {
    AtomicInteger expireCallCount = new AtomicInteger();
    Cache<Integer, Integer> c = target.cache(b -> b.addListener((CacheEntryExpiredListener<Integer, Integer>) (c1, e) -> expireCallCount.incrementAndGet())
      .eternal(true)
      .keepDataAfterExpired(false)
      .expiryPolicy((key, value, startTime, currentEntry) -> {
        if (currentEntry != null) {
          return startTime;
        }
        return ETERNAL;
      }));
    c.put(1, 1);
    c.put(1, 2);
    assertThat(expireCallCount.get()).isEqualTo(1);
    assertThat(latestInfo(c).getSize()).isEqualTo(0);
    assertThat(latestInfo(c).getExpiredCount()).isEqualTo(1);
  }

  @Test
  public void listenerExampleForDocumentation() {
    Cache2kBuilder.of(Integer.class, Integer.class)
      .addListener((CacheEntryCreatedListener<Integer, Integer>) (cache, entry) -> System.err.println("inserted: " + entry.getValue()));
  }

  @Test
  public void customExecutor() {
    AtomicInteger counter = new AtomicInteger();
    Cache<Integer, Integer> c =
      of(Integer.class, Integer.class)
        .addAsyncListener((CacheEntryCreatedListener<Integer, Integer>) (cache, entry) -> {
        })
        .asyncListenerExecutor(command -> counter.incrementAndGet())
        .build();
    c.put(1, 2);
    c.close();
    assertThat(counter.get()).isEqualTo(1);
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
    SimulatedClock clock = new SimulatedClock(1000);
    target.run(new CountSyncEvents() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.timeReference(clock);
        b.expireAfterWrite(expireAfterWrite, TimeUnit.MILLISECONDS);
        b.loader(key -> {
          clock.sleep(expireAfterWrite * 2);
          return key;
        });
        super.extend(b);
      }

      @Override
      public void run() {
        cache.get(123);
        assertThat(created.get())
          .as("created")
          .isEqualTo(1);
        assertThat(expired.get())
          .as("expired")
          .isEqualTo(1);
      }
    });

  }

  /**
   * No expiry event during load. Entry expires at end of load operation.
   */
  @Test
  public void expiredWhileReload() {
    final long expireAfterWrite = 123;
    SimulatedClock clock = new SimulatedClock(1000);
    target.run(new CountSyncEvents() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> b) {
        b.timeReference(clock);
        b.expireAfterWrite(expireAfterWrite, TimeUnit.MILLISECONDS);
        b.loader(key -> {
          clock.sleep(expireAfterWrite * 2);
          return key;
        });
        super.extend(b);
      }

      @Override
      public void run() {
        cache.put(123, 5);
        assertThat(created.get())
          .as("created")
          .isEqualTo(1);
        try {
          cache.reloadAll(asList(123)).get();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        assertThat(expired.get())
          .as("expired")
          .isEqualTo(1);
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
    SimulatedClock clock = new SimulatedClock(1000);
    target.run(new CountSyncEvents() {
      @Override
      public void extend(Cache2kBuilder<Integer, Integer> builder) {
        builder
          .timeReference(clock)
          .expireAfterWrite(expireAfterWrite, TimeUnit.MILLISECONDS)
          .loader(key -> {
            clock.sleep(expireAfterWrite / 3 * 2);
            return key;
          });
        super.extend(builder);
      }

      @Override
      public void run() {
        cache.put(123, 5);
        try {
          clock.sleep(expireAfterWrite / 3 * 2);
        } catch (InterruptedException ignore) {
        }
        assertThat(created.get())
          .as("created")
          .isEqualTo(1);
        try {
          cache.reloadAll(asList(123)).get();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        assertThat(updated.get())
          .as("updated")
          .isEqualTo(1);
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
      assertThat(created.get() >= (evicted.get() + expired.get() + removed.get()))
        .as("Get a created event before something is removed, evicted or expired")
        .isTrue();
    }

    @Override
    public void extend(Cache2kBuilder<Integer, Integer> b) {
      b.addListener((CacheEntryCreatedListener<Integer, Integer>) (c, e) -> created.incrementAndGet())
        .addListener((CacheEntryUpdatedListener<Integer, Integer>) (cache, currentEntry, newEntry) -> updated.incrementAndGet())
        .addListener((CacheEntryRemovedListener<Integer, Integer>) (c, e) -> {
          removed.incrementAndGet();
          checkCondition();
        })
        .addListener((CacheEntryEvictedListener<Integer, Integer>) (cache, entry) -> {
          evicted.incrementAndGet();
          checkCondition();
        })
        .addListener((CacheEntryExpiredListener<Integer, Integer>) (cache, entry) -> {
          expired.incrementAndGet();
          checkCondition();
        });
    }
  }

}
