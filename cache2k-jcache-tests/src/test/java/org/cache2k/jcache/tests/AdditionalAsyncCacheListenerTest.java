package org.cache2k.jcache.tests;

/*
 * #%L
 * cache2k JCache tests
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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

import org.jsr107.tck.event.CacheEntryListenerClient;
import org.jsr107.tck.event.CacheEntryListenerServer;
import org.jsr107.tck.testutil.CacheTestSupport;
import org.jsr107.tck.testutil.ExcludeListExcluder;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;

import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;
import javax.cache.event.EventType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Test async cache listeners.
 *
 * @author Jens Wilke
 */
public class AdditionalAsyncCacheListenerTest extends CacheTestSupport<Integer, String> {

  @Rule
  public MethodRule rule = new ExcludeListExcluder(this.getClass());

  private CacheEntryListenerServer cacheEntryListenerServer;
  private RecordingListener<Integer, String> listener;

  @Override
  protected MutableConfiguration<Integer, String> newMutableConfiguration() {
    return new MutableConfiguration<Integer, String>().setTypes(Integer.class, String.class);
  }

  @Override
  protected MutableConfiguration<Integer, String> extraSetup(MutableConfiguration<Integer, String> cfg) {
    // cfg.setExpiryPolicyFactory(ModifiedExpiryPolicy.factoryOf(new Duration(TimeUnit.MILLISECONDS, 5)));
    cacheEntryListenerServer = new CacheEntryListenerServer<>(10011, Integer.class, String.class);
    try {
      cacheEntryListenerServer.open();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    listener = new RecordingListener<>();
    cacheEntryListenerServer.addCacheEventListener(listener);
    CacheEntryListenerClient<Integer, String> clientListener =
      new CacheEntryListenerClient<>(cacheEntryListenerServer.getInetAddress(), cacheEntryListenerServer.getPort());
    boolean _isSynchronous = false;
    listenerConfiguration = new MutableCacheEntryListenerConfiguration<>(
      FactoryBuilder.factoryOf(clientListener), null, true, _isSynchronous);
    return cfg.addCacheEntryListenerConfiguration(listenerConfiguration);
  }

  /**
   * Test whether async events arrive and are in correct order. Also test with negative key.
   */
  @Test
  public void testInOrder() throws Exception {
    int KEY = -123;
    cache.put(KEY, "hello");
    cache.put(KEY, "mike");
    cache.remove(KEY);
    listener.await(3, 60 * 1000);
    assertEquals(
      Arrays.asList(EventType.CREATED, EventType.UPDATED, EventType.REMOVED),
      listener.extractLogForKey(KEY));
  }

  public static class RecordingListener<K, V> implements CacheEntryCreatedListener<K, V>,
    CacheEntryUpdatedListener<K, V>, CacheEntryExpiredListener<K, V>,
    CacheEntryRemovedListener<K, V> {

    List<RecordedEvent<K>> log = Collections.synchronizedList(new ArrayList<RecordedEvent<K>>());

    @Override
    public void onCreated(final Iterable<CacheEntryEvent<? extends K, ? extends V>> events) throws CacheEntryListenerException {
      record(EventType.CREATED, events);
    }

    @Override
    public void onExpired(final Iterable<CacheEntryEvent<? extends K, ? extends V>> events) throws CacheEntryListenerException {
      record(EventType.EXPIRED, events);
    }

    @Override
    public void onRemoved(final Iterable<CacheEntryEvent<? extends K, ? extends V>> events) throws CacheEntryListenerException {
      record(EventType.REMOVED, events);
    }

    @Override
    public void onUpdated(final Iterable<CacheEntryEvent<? extends K, ? extends V>> events) throws CacheEntryListenerException {
      record(EventType.UPDATED, events);
    }

    public List<EventType> extractLogForKey(K key) {
      List<EventType> l = new ArrayList<>();
      for (RecordedEvent<K> e : log) {
        if (e.key.equals(key)) {
          l.add(e.type);
        }
      }
      return l;
    }

    public void await(int _eventCount, long _timeoutMillis) throws TimeoutException {
      long t0 = System.currentTimeMillis();
      while (_eventCount != log.size()) {
        long t = System.currentTimeMillis();
        if (t - t0 >= _timeoutMillis) {
          throw new TimeoutException();
        }
        synchronized (log) {
          try {
            log.wait(_timeoutMillis - (t - t0));
          } catch (InterruptedException ex) {}
        }
      }
    }

    public void record(final EventType _expectedType, final Iterable<CacheEntryEvent<? extends K, ? extends V>> events) {
      for (CacheEntryEvent e0 : events) {
        CacheEntryEvent<K, V> e = e0;
        assertEquals(_expectedType, e.getEventType());
        log.add(new RecordedEvent<K>(e.getEventType(), e.getKey()));
      }
      synchronized (log) {
        log.notifyAll();
      }
    }

  }

  static class RecordedEvent<K> {

    EventType type;
    K key;

    public RecordedEvent(final EventType _type, final K _key) {
      type = _type;
      key = _key;
    }
  }

}
