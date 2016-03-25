package org.cache2k.core.test;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import org.cache2k.integration.AdvancedCacheLoader;
import org.cache2k.Cache;
import org.cache2k.CacheBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.integration.CacheLoader;
import org.cache2k.integration.LoadCompletedListener;
import org.cache2k.junit.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.cache2k.core.test.StaticUtil.*;

/**
 * Test the cache loader.
 *
 * @author Jens Wilke
 * @see CacheLoader
 * @see AdvancedCacheLoader
 * @see LoadCompletedListener
 *
 */
@Category(FastTests.class)
public class CacheLoaderTest {

  @Test
  public void testLoader() {
    Cache<Integer,Integer> c = builder()
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          return key * 2;
        }
      })
      .build();
    assertEquals((Integer) 10, c.get(5));
    assertEquals((Integer) 20, c.get(10));
    assertFalse(c.contains(2));
    assertTrue(c.contains(5));
    c.close();
  }

  @Test
  public void testAdvancedLoader() {
    Cache<Integer,Integer> c = builder()
      .loader(new AdvancedCacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key, long now, CacheEntry<Integer,Integer> e) throws Exception {
          return key * 2;
        }
      })
      .build();
    assertEquals((Integer) 10, c.get(5));
    assertEquals((Integer) 20, c.get(10));
    assertFalse(c.contains(2));
    assertTrue(c.contains(5));
    c.close();
  }

  @Test
  public void testLoadAll() throws Exception {
    final AtomicInteger _countLoad =  new AtomicInteger();
    Cache<Integer,Integer> c = builder()
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          return _countLoad.incrementAndGet();
        }
      })
      .build();
    c.get(5);

    CompletionWaiter w = new CompletionWaiter();
    c.loadAll(asSet(5, 6), w);
    w.awaitCompletion();
    assertEquals(2, _countLoad.get());
    assertEquals((Integer) 2, c.get(6));
    c.loadAll(asSet(5, 6), null);
    c.loadAll(Collections.EMPTY_SET, null);
    c.close();
  }

  @Test
  public void testReloadAll() throws Exception {
    final AtomicInteger _countLoad =  new AtomicInteger();
    Cache<Integer,Integer> c = builder()
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          return _countLoad.incrementAndGet();
        }
      })
      .build();
    c.get(5);
    CompletionWaiter w = new CompletionWaiter();
    c.reloadAll(asSet(5, 6), w);
    w.awaitCompletion();
    assertEquals(3, _countLoad.get());
    c.reloadAll(asSet(5, 6), null);
    c.reloadAll(Collections.EMPTY_SET, null);
    c.close();
  }

  @Test
  public void testPrefetch() {
    Cache<Integer,Integer> c = builder()
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          return key * 2;
        }
      })
      .build();
    c.prefetch(123);
    assertTrue(latestInfo(c).getAsyncLoadsStarted() > 0);
    c.close();
  }

  @Test
  public void testNoPrefetch() {
    Cache<Integer,Integer> c = builder()
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          return key * 2;
        }
      })
      .build();
    c.put(123, 3);
    c.prefetch(123);
    assertTrue(latestInfo(c).getAsyncLoadsStarted() == 0);
    c.close();
  }

  @Test
  public void testPrefetchAll() {
    Cache<Integer,Integer> c = builder()
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          return key * 2;
        }
      })
      .build();
    c.prefetchAll(asSet(1,2,3));
    assertTrue(latestInfo(c).getAsyncLoadsStarted() > 0);
    c.close();
  }

  @Test
  public void testNoPrefetchAll() {
    Cache<Integer,Integer> c = builder()
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          return key * 2;
        }
      })
      .build();
    c.put(1,1);
    c.put(2,2);
    c.put(3,3);
    c.prefetchAll(asSet(1,2,3));
    assertTrue(latestInfo(c).getAsyncLoadsStarted() == 0);
    c.close();
  }

  /**
   * We should always have two loader threads.
   */
  @Test
  public void testTwoLoaderThreadsAndPoolInfo() throws Exception {
    final CountDownLatch _inLoader = new CountDownLatch(1);
    final CountDownLatch _releaseLoader = new CountDownLatch(1);
    Cache<Integer,Integer> c = builder()
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          _inLoader.countDown();
          _releaseLoader.await();
          return key * 2;
        }
      })
      .build();
    c.loadAll(asSet(1), null);
    c.loadAll(asSet(2), null);
    _inLoader.await();
    assertEquals(2, latestInfo(c).getAsyncLoadsStarted());
    assertEquals(2, latestInfo(c).getAsyncLoadsInFlight());
    assertEquals(2, latestInfo(c).getLoaderThreadsMaxActive());
    _releaseLoader.countDown();
    c.close();
  }

  /**
   * We should always have two loader threads.
   */
  @Test
  public void testOneLoaderThreadsAndPoolInfo() throws Exception {
    final CountDownLatch _inLoader = new CountDownLatch(1);
    final CountDownLatch _releaseLoader = new CountDownLatch(1);
    Cache<Integer,Integer> c = builder()
      .loaderThreadCount(1)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(final Integer key) throws Exception {
          _inLoader.countDown();
          _releaseLoader.await();
          return key * 2;
        }
      })
      .build();
    c.loadAll(asSet(1), null);
    c.loadAll(asSet(2), null);
    _inLoader.await();
    assertEquals(2, latestInfo(c).getAsyncLoadsStarted());
    assertEquals(1, latestInfo(c).getAsyncLoadsInFlight());
    assertEquals(1, latestInfo(c).getLoaderThreadsMaxActive());
    _releaseLoader.countDown();
    c.close();
  }

  protected CacheBuilder<Integer, Integer> builder() {
    return
      CacheBuilder.newCache(Integer.class, Integer.class)
        .name(this.getClass().getSimpleName())
        .eternal(true);
  }

  class CompletionWaiter implements LoadCompletedListener {

    CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void loadCompleted() {
      latch.countDown();
    }

    @Override
    public void loadException(final Exception _exception) {

    }

    public void awaitCompletion() throws InterruptedException {
      latch.await();
    }
  }

}
