package org.cache2k.test.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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
import org.cache2k.test.util.ConcurrencyHelper;
import org.cache2k.test.util.Condition;
import org.cache2k.test.util.TimeBox;
import org.cache2k.event.CacheEntryCreatedListener;
import org.cache2k.event.CacheEntryExpiredListener;
import org.cache2k.event.CacheEntryRemovedListener;
import org.cache2k.event.CacheEntryUpdatedListener;
import org.cache2k.core.util.Log;
import org.cache2k.junit.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import static org.junit.Assert.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class ListenerTest {

  protected Cache2kBuilder<Integer, Integer> builder() {
    return
      Cache2kBuilder.of(Integer.class, Integer.class)
        .name(this.getClass())
        .eternal(true);
  }

  @Test
  public void createdListenerCalled() {
    final AtomicInteger _callCount = new AtomicInteger();
    Cache<Integer,Integer> c = builder()
      .addListener(new CacheEntryCreatedListener<Integer, Integer>() {
        @Override
        public void onEntryCreated(final Cache<Integer, Integer> c, final CacheEntry<Integer, Integer> e) {
          Thread.yield();
          _callCount.incrementAndGet();
        }
      })
      .build();
    c.put(1,2);
    assertEquals(1, _callCount.get());
    c.close();
  }

  @Test
  public void updateListenerCalled() {
    final AtomicInteger _callCount = new AtomicInteger();
    Cache<Integer, Integer> c = builder()
      .addListener(new CacheEntryUpdatedListener<Integer, Integer>() {
        @Override
        public void onEntryUpdated(final Cache<Integer, Integer> cache, final CacheEntry<Integer, Integer> currentEntry, final CacheEntry<Integer, Integer> entryWithNewData) {
          Thread.yield();
          _callCount.incrementAndGet();
        }
      })
      .build();
    c.put(1, 2);
    assertEquals(0, _callCount.get());
    c.put(1, 2);
    assertEquals(1, _callCount.get());
    c.close();
  }

  @Test
  public void removedListenerCalled() {
    final AtomicInteger _callCount = new AtomicInteger();
    Cache<Integer, Integer> c = builder()
      .addListener(new CacheEntryRemovedListener<Integer, Integer>() {
        @Override
        public void onEntryRemoved(final Cache<Integer, Integer> c, final CacheEntry<Integer, Integer> e) {
          Thread.yield();
          _callCount.incrementAndGet();
        }
      })
      .build();
    c.put(1, 2);
    assertEquals(0, _callCount.get());
    c.put(1, 2);
    assertEquals(0, _callCount.get());
    c.remove(1);
    assertEquals(1, _callCount.get());
    c.close();
  }

  /** If the listener is not executed in separate thread, this would block */
  @Test(timeout = TestingParameters.MAX_FINISH_WAIT)
  public void asyncCreatedListenerCalled() {
    final AtomicInteger _callCount = new AtomicInteger();
    final CountDownLatch _fire = new CountDownLatch(1);
    Cache<Integer, Integer> c = builder()
      .addAsyncListener(new CacheEntryCreatedListener<Integer, Integer>() {
        @Override
        public void onEntryCreated(final Cache<Integer, Integer> c, final CacheEntry<Integer, Integer> e) {
          try {
            _fire.await();
          } catch (InterruptedException ignore) { }
          _callCount.incrementAndGet();
        }
      })
      .build();
    c.put(1,2);
    assertEquals(0, _callCount.get());
    _fire.countDown();
    ConcurrencyHelper.await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return _callCount.get() == 1;
      }
    });
    c.close();
  }

  /** If the listener is not executed in separate thread, this would block */
  @Test(timeout = TestingParameters.MAX_FINISH_WAIT)
  public void asyncUpdateListenerCalled() {
    final AtomicInteger _callCount = new AtomicInteger();
    final CountDownLatch _fire = new CountDownLatch(1);
    Cache<Integer, Integer> c = builder()
      .addAsyncListener(new CacheEntryUpdatedListener<Integer, Integer>() {
        @Override
        public void onEntryUpdated(final Cache<Integer, Integer> cache, final CacheEntry<Integer, Integer> currentEntry, final CacheEntry<Integer, Integer> entryWithNewData) {
          try {
            _fire.await();
          } catch (InterruptedException ignore) { }
          _callCount.incrementAndGet();
        }
      })
      .build();
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
    c.close();
  }

  /** If the listener is not executed in separate thread, this would block */
  @Test(timeout = TestingParameters.MAX_FINISH_WAIT)
  public void asyncRemovedListenerCalled() {
    final AtomicInteger _callCount = new AtomicInteger();
    final CountDownLatch _fire = new CountDownLatch(1);
    Cache<Integer, Integer> c = builder()
      .addAsyncListener(new CacheEntryRemovedListener<Integer, Integer>() {
        @Override
        public void onEntryRemoved(final Cache<Integer, Integer> c, final CacheEntry<Integer, Integer> e) {
          try {
            _fire.await();
          } catch (InterruptedException ignore) { }
          _callCount.incrementAndGet();
        }
      })
      .build();
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
    c.close();
  }

  /** Check that we do not miss events. */
  @Test(timeout = TestingParameters.MAX_FINISH_WAIT)
  public void manyAsyncUpdateListenerCalled() {
    final AtomicInteger _callCount = new AtomicInteger();
    final ConcurrentMap<Integer, Integer> _seenValues = new ConcurrentHashMap<Integer, Integer>();
    Cache<Integer, Integer> c = builder()
      .addAsyncListener(new CacheEntryUpdatedListener<Integer, Integer>() {
        @Override
        public void onEntryUpdated(final Cache<Integer, Integer> cache, final CacheEntry<Integer, Integer> currentEntry, final CacheEntry<Integer, Integer> entryWithNewData) {
          _callCount.incrementAndGet();
          _seenValues.put(entryWithNewData.getValue(), entryWithNewData.getValue());
        }
      })
      .build();
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
    c.close();
  }

  @Test
  public void asyncExpiredListenerCalled() {
    final AtomicInteger _callCount = new AtomicInteger();
    final long _EXPIRY_MILLIS = TestingParameters.MINIMAL_TICK_MILLIS;
    final Cache<Integer, Integer> c = builder()
      .addAsyncListener(new CacheEntryExpiredListener<Integer, Integer>() {
        @Override
        public void onEntryExpired(final Cache<Integer, Integer> c, final CacheEntry<Integer, Integer> e) {
          _callCount.incrementAndGet();
        }
      })
      .expireAfterWrite(_EXPIRY_MILLIS, TimeUnit.MILLISECONDS)
      .build();
    final int ANY_KEY = 1;
    TimeBox.millis(_EXPIRY_MILLIS)
      .work(new Runnable() {
        @Override
        public void run() {
          c.put(ANY_KEY, 4711);
        }
      })
      .check(new Runnable() {
        @Override
        public void run() {
          assertEquals(0, _callCount.get());
          assertTrue(c.contains(ANY_KEY));
        }
      });
    ConcurrencyHelper.await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return _callCount.get() == 1;
      }
    });
    c.close();
  }

  @Test
  public void asyncExpiredListenerCalledSharpExpiry() {
    final AtomicInteger _callCount = new AtomicInteger();
    final long _EXPIRY_MILLIS = TestingParameters.MINIMAL_TICK_MILLIS;
    final Cache<Integer, Integer> c = builder()
      .addAsyncListener(new CacheEntryExpiredListener<Integer, Integer>() {
        @Override
        public void onEntryExpired(final Cache<Integer, Integer> c, final CacheEntry<Integer, Integer> e) {
          _callCount.incrementAndGet();
        }
      })
      .expireAfterWrite(_EXPIRY_MILLIS, TimeUnit.MILLISECONDS)
      .sharpExpiry(true)
      .build();
    final int ANY_KEY = 1;
    TimeBox.millis(_EXPIRY_MILLIS)
      .work(new Runnable() {
        @Override
        public void run() {
          c.put(ANY_KEY, 4711);
        }
      })
      .check(new Runnable() {
        @Override
        public void run() {
          assertEquals(0, _callCount.get());
          assertTrue(c.contains(ANY_KEY));
        }
      });
    ConcurrencyHelper.await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return _callCount.get() == 1;
      }
    });
    c.close();
  }

  @Test(expected = Exception.class)
  public void updateListenerException() {
    Cache<Integer, Integer> c = builder()
      .addListener(new CacheEntryUpdatedListener<Integer, Integer>() {
        @Override
        public void onEntryUpdated(final Cache<Integer, Integer> cache, final CacheEntry<Integer, Integer> currentEntry, final CacheEntry<Integer, Integer> entryWithNewData) {
          throw new RuntimeException("ouch");
        }
      })
      .build();
    try {
      c.put(1, 2);
      c.put(1, 2);
    } finally {
      c.close();
    }
  }

  @Test
  public void asyncUpdateListenerException() {
    String _logName = getClass().getName();
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

}
