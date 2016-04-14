package org.cache2k.core.test;

/*
 * #%L
 * cache2k core package
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
import org.cache2k.core.test.util.ConcurrencyHelper;
import org.cache2k.core.test.util.Condition;
import org.cache2k.event.CacheEntryCreatedListener;
import org.cache2k.event.CacheEntryRemovedListener;
import org.cache2k.event.CacheEntryUpdatedListener;
import org.cache2k.junit.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class ListenerTest {

  protected Cache2kBuilder<Integer, Integer> builder() {
    return
      Cache2kBuilder.of(Integer.class, Integer.class)
        .name(this.getClass().getSimpleName())
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
    Cache<Integer,Integer> c = builder()
      .addListener(new CacheEntryUpdatedListener<Integer, Integer>() {
        @Override
        public void onEntryUpdated(final Cache<Integer, Integer> cache, final CacheEntry<Integer, Integer> previousEntry, final CacheEntry<Integer, Integer> currentEntry) {
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
    Cache<Integer,Integer> c = builder()
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
    Cache<Integer,Integer> c = builder()
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
    Cache<Integer,Integer> c = builder()
      .addAsyncListener(new CacheEntryUpdatedListener<Integer, Integer>() {
        @Override
        public void onEntryUpdated(final Cache<Integer, Integer> cache, final CacheEntry<Integer, Integer> previousEntry, final CacheEntry<Integer, Integer> currentEntry) {
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
    Cache<Integer,Integer> c = builder()
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
    Cache<Integer,Integer> c = builder()
      .addAsyncListener(new CacheEntryUpdatedListener<Integer, Integer>() {
        @Override
        public void onEntryUpdated(final Cache<Integer, Integer> cache, final CacheEntry<Integer, Integer> previousEntry, final CacheEntry<Integer, Integer> currentEntry) {
          _callCount.incrementAndGet();
        }
      })
      .build();
    c.put(1, 2);
    assertEquals(0, _callCount.get());
    final int _UPDATE_COUNT = 123;
    for (int i = 0; i < _UPDATE_COUNT; i++) {
      c.put(1, 2);
    }
    ConcurrencyHelper.await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return _callCount.get() == _UPDATE_COUNT;
      }
    });
    c.close();
  }

}
