package org.cache2k.jcache.tests;

/*
 * #%L
 * cache2k JCache tests
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

import org.cache2k.config.Cache2kConfig;
import org.cache2k.jcache.ExtendedMutableConfiguration;
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
    boolean useCache2kConfigurationToSetLoaderThreads = true;
    if (useCache2kConfigurationToSetLoaderThreads) {
      ExtendedMutableConfiguration<Integer, String> cfg = new ExtendedMutableConfiguration<>();
      cfg.setCache2kConfiguration(new Cache2kConfig<Integer, String>());
      cfg.setTypes(Integer.class, String.class);
      return cfg;
    }
    return new MutableConfiguration<Integer, String>().setTypes(Integer.class, String.class);
  }

  private final CountDownLatch loaderCalled = new CountDownLatch(1);
  private final CountDownLatch releaseLoader = new CountDownLatch(1);
  private final AtomicInteger expireListenerCalled = new AtomicInteger();
  private final AtomicInteger createdListenerCalled = new AtomicInteger();
  /**
   * Entry might expire before the entry operation load or invoke gets started.
   */
  private final AtomicInteger listenerCallBeforeOp = new AtomicInteger();

  int deltaListenerCall() {
    return expireListenerCalled.get() - listenerCallBeforeOp.get();
  }

  private final CacheLoader<Integer, String> loader = new CacheLoader<Integer, String>() {
    @Override
    public String load(Integer key) throws CacheLoaderException {
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
    public Map<Integer, String> loadAll(Iterable<? extends Integer> keys) throws CacheLoaderException {
      throw new UnsupportedOperationException();
    }
  };

  @Override
  protected void extraSetup(ConfigurationBuilder<Integer, String> cfg) {
    cfg.loader(loader);
    cfg.expiry(ModifiedExpiryPolicy.factoryOf(new Duration(TimeUnit.MILLISECONDS, expireAfterWrite)));
    cfg.syncListener(new CacheEntryExpiredListener<Integer, String>() {
      @Override
      public void onExpired(Iterable<CacheEntryEvent<? extends Integer, ? extends String>> events) throws CacheEntryListenerException {
        expireListenerCalled.incrementAndGet();
      }
    });
    cfg.syncListener(new CacheEntryCreatedListener<Integer, String>() {
      @Override
      public void onCreated(Iterable<CacheEntryEvent<? extends Integer, ? extends String>> cacheEntryEvents) throws CacheEntryListenerException {
        createdListenerCalled.incrementAndGet();
      }
    });
    // sharp expiry off by default when cache2k configuration is present,
    // but on with pure JCache mode. Switch back on for the tests here.
    cfg.cache2kBuilder(b -> b
      .timerLag(expireAfterWrite * 2, TimeUnit.MILLISECONDS)
      .sharpExpiry(true)
      .loaderThreadCount(10)
    );
  }

  /**
   * Just test that we can start a load
   */
  @Test
  public void testLoaderCalled() throws Exception {
    cache.loadAll(keys(1), false, null);
    loaderCalled.await();
    releaseLoader.countDown();
    while (expireListenerCalled.get() == 0) { }
  }

  /**
   * Multiple load requests do not block if the loader is still running.
   * At least a second load triggered by a load request, even if the
   * loader is currently started. Corner case: Since replaceExistingValues is false
   * the cache might decide not to issue another load on the same key.
   *
   * <p>If not enough loader threads are available this deadlocks.
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
    while (cache.containsKey(1)) { }
    while (expireListenerCalled.get() == 0) { }
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
    cache.invoke(1, (entry, arguments) -> {
      entry.getValue();
      while (expireListenerCalled.get() > 0);
      return null;
    });
  }

  /**
   * cache2k: Entry expires during invoke, because the processing in invoke takes
   * longer than the expiry time. invoke finishes with removing the entry and
   * sending the expiry events. In other words, the expiry processing is done
   * during the cache operation and not later by a timer.
   */
  @Test
  public void expireWhileInvoke() {
    cache.put(1, "hello");
    cache.invoke(1, (entry, arguments) -> {
      listenerCallBeforeOp.set(expireListenerCalled.get());
      // entry.setValue(entry.getValue());
      entry.exists();
      entry.setValue("foo");
      // entry is locked now.
      sleep(expireAfterWrite + 1);
      assertTrue("no expiry called yet", deltaListenerCall() == 0);
      return null;
    });
    assertEquals("Expired while invoke", 1, deltaListenerCall());
  }

  @Test
  public void expireBeforeInvoke() {
    cache.put(1, "hello");
    sleep(expireAfterWrite + 1);
    // we may or may not get notified about expiry
    cache.invoke(1, (entry, arguments) -> {
      // entry.setValue(entry.getValue());
      entry.exists();
      entry.setValue("foo");
      // entry is locked now.
      assertEquals("Expired before entry mutation", 1, deltaListenerCall());
      return null;
    });
  }

  public void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(ex);
    }
  }

}
