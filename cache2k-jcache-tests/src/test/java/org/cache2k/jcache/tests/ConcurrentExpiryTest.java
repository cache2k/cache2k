package org.cache2k.jcache.tests;

/*
 * #%L
 * cache2k JCache tests
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

import org.jsr107.tck.testutil.ExcludeListExcluder;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;

import javax.cache.configuration.MutableConfiguration;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ModifiedExpiryPolicy;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheLoaderException;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.MutableEntry;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test whether expiry listener is called if an entry expires during an
 * ongoing concurrent operation.
 *
 * @author Jens Wilke
 */
public class ConcurrentExpiryTest extends AdditionalTestSupport<Integer, String> {

  final long expireAfterWrite = 123;

  @Rule
  public MethodRule rule = new ExcludeListExcluder(this.getClass());

  @Override
  protected MutableConfiguration<Integer, String> newMutableConfiguration() {
    return new MutableConfiguration<Integer, String>().setTypes(Integer.class, String.class);
  }

  private CountDownLatch loaderCalled = new CountDownLatch(1);
  private CountDownLatch releaseLoader = new CountDownLatch(1);
  private AtomicInteger expireListenerCalled = new AtomicInteger();
  private AtomicInteger createdListenerCalled = new AtomicInteger();
  /**
   * Entry might expire before the entry operation load or invoke gets started.
   */
  private AtomicInteger listenerCallBeforeOp = new AtomicInteger();

  int deltaListenerCall() {
    return expireListenerCalled.get() - listenerCallBeforeOp.get();
  }

  private CacheLoader<Integer, String> loader = new CacheLoader<Integer, String>() {
    @Override
    public String load(final Integer key) throws CacheLoaderException {
      listenerCallBeforeOp.set(expireListenerCalled.get());
      loaderCalled.countDown();
      try {
        releaseLoader.await();
      } catch (InterruptedException ex) {
        throw new RuntimeException(ex);
      }
      return key + "";
    }

    @Override
    public Map<Integer, String> loadAll(final Iterable<? extends Integer> keys) throws CacheLoaderException {
      throw new UnsupportedOperationException();
    }
  };

  @Override
  protected void extraSetup(ConfigurationBuilder<Integer, String> cfg) {
    cfg.loader(loader);
    cfg.expiry(ModifiedExpiryPolicy.factoryOf(new Duration(TimeUnit.MILLISECONDS, expireAfterWrite)));
    cfg.syncListener(new CacheEntryExpiredListener<Integer, String>() {
      @Override
      public void onExpired(final Iterable<CacheEntryEvent<? extends Integer, ? extends String>> events) throws CacheEntryListenerException {
        expireListenerCalled.incrementAndGet();
      }
    });
    cfg.syncListener(new CacheEntryCreatedListener<Integer, String>() {
      @Override
      public void onCreated(final Iterable<CacheEntryEvent<? extends Integer, ? extends String>> cacheEntryEvents) throws CacheEntryListenerException {
        createdListenerCalled.incrementAndGet();
      }
    });
  }

  /**
   * Just test that we can start a load
   */
  @Test
  public void testLoaderCalled() throws Exception {
    cache.loadAll(keys(1), false, null);
    loaderCalled.await();
    releaseLoader.countDown();
    while(expireListenerCalled.get() == 0) {}
  }

  /**
   * Multiple load requests do not block if the loader is still running.
   * At least a second load triggered by a load request, even if the
   * loader is currently started. Corner case: Since replaceExistingValues is false
   * the cache might decide not to issue another load on the same key.
   */
  @Test
  public void testLoaderCalledNotBlocking() throws Exception {
    cache.loadAll(keys(1), false, null);
    cache.loadAll(keys(1), false, null);
    cache.loadAll(keys(1), false, null);
    loaderCalled.await();
    releaseLoader.countDown();
    loaderCalled.await();
    releaseLoader.countDown();
    loaderCalled.await();
    releaseLoader.countDown();
  }

  @Test
  public void testExpireListenerCalled() {
    cache.put(1, "hello");
    while(cache.containsKey(1)) {}
    while(expireListenerCalled.get() == 0) {}
  }

  /**
   * In cache2k expiry events are not sent during an ongoing operation.
   * Test with load.
   */
  @Test
  public void testExpireWhileLoadNoEvent() throws Exception {
    cache.put(1, "hello");
    cache.loadAll(keys(1), true, null);
    while(cache.containsKey(1)) {}
    loaderCalled.await();
    releaseLoader.countDown();
    // events are lagging since V1.6
    // assertTrue("entry finally expires after the last load", deltaListenerCall() == 1);
  }

  /**
   * Cache2k semantics: Expiry listener gets called if within entry processor and
   * entry is not locked.
   */
  @Test
  public void testExpireAnywayWhileReadInvoke() throws Exception {
    cache.put(1, "hello");
    cache.invoke(1, new EntryProcessor<Integer, String, Object>() {
      @Override
      public Object process(final MutableEntry<Integer, String> entry, final Object... arguments)
        throws EntryProcessorException {
        entry.getValue();
        while(expireListenerCalled.get() > 0);
        return null;
      }
    });
  }

  /**
   * cache2k: Entry expires during invoke, because the EntryProcessor
   * is invoked during the examine phase and not the mutate phase of
   * the entry processing. This may change. Move this to cache2k tests.
   */
  @Test
  public void testExpireWhileInvoke() throws Exception {
    cache.put(1, "hello");
    cache.invoke(1, new EntryProcessor<Integer, String, Object>() {
      @Override
      public Object process(final MutableEntry<Integer, String> entry, final Object... arguments)
        throws EntryProcessorException {
        entry.setValue(entry.getValue());
        // entry is locked now. current semantics of cache2k
        // run through this twice
        listenerCallBeforeOp.set(expireListenerCalled.get());
        try {
          Thread.sleep(expireAfterWrite * 3);
        } catch (InterruptedException ex) {
          throw new EntryProcessorException(ex);
        }
        entry.setValue("xy");
        assertTrue("no expiry called yet", deltaListenerCall() == 0);
        return null;
      }
    });
    assertEquals("Expired while invoke", 1, deltaListenerCall());
  }

}
