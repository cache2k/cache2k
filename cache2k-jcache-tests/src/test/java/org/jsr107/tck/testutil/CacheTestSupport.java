/**
 *  Copyright 2011-2013 Terracotta, Inc.
 *  Copyright 2011-2013 Oracle, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jsr107.tck.testutil;

import org.junit.After;
import org.junit.Before;

import javax.cache.Cache;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static javax.cache.event.EventType.CREATED;
import static javax.cache.event.EventType.REMOVED;
import static javax.cache.event.EventType.UPDATED;
import static org.junit.Assert.assertEquals;

/**
 * Unit test support base class
 *
 * @author Yannis Cosmadopoulos
 * @since 1.0
 */
public abstract class CacheTestSupport<K, V> extends TestSupport {

  protected Cache<K, V> cache;

  protected MyCacheEntryListener<K, V> listener;
  protected MutableCacheEntryListenerConfiguration<K, V> listenerConfiguration;


  @Before
  public void setUp() throws IOException  {
    cache = getCacheManager().createCache(getTestCacheName(), extraSetup(newMutableConfiguration()));
  }

  @After
  public void teardown() {
    getCacheManager().destroyCache(getTestCacheName());
  }

  /**
   * Constructs a new {@link MutableConfiguration} for the test.
   *
   * @return a new {@link MutableConfiguration}
   */
  abstract protected MutableConfiguration<K, V> newMutableConfiguration();


  protected MutableConfiguration<K, V> extraSetup(MutableConfiguration<K, V> configuration) {
    return configuration;
  }


  private LinkedHashMap<Long, String> createLSData(int count, long now) {
    LinkedHashMap<Long, String> map = new LinkedHashMap<Long, String>(count);
    for (int i = 0; i < count; i++) {
      Long key = now + i;
      map.put(key, "value" + key);
    }
    return map;
  }

  private LinkedHashMap<Date, Date> createDDData(int count, long now) {
    LinkedHashMap<Date, Date> map = new LinkedHashMap<Date, Date>(count);
    for (int i = 0; i < count; i++) {
      map.put(new Date(now + i), new Date(now + 1000 + i));
    }
    return map;
  }

  protected LinkedHashMap<Date, Date> createDDData(int count) {
    return createDDData(count, System.currentTimeMillis());
  }

  protected LinkedHashMap<Long, String> createLSData(int count) {
    return createLSData(count, System.currentTimeMillis());
  }


  /**
   * Test listener
   *
   * @param <K>
   * @param <V>
   */
  public static class MyCacheEntryListener<K, V> implements CacheEntryCreatedListener<K, V>,
      CacheEntryUpdatedListener<K, V>, CacheEntryExpiredListener<K, V>,
      CacheEntryRemovedListener<K, V>, Serializable,
  AutoCloseable {

    AtomicInteger created = new AtomicInteger();
    AtomicInteger updated = new AtomicInteger();
    AtomicInteger removed = new AtomicInteger();

    ArrayList<CacheEntryEvent<K, V>> entries = new ArrayList<CacheEntryEvent<K, V>>();

    public int getCreated() {
      return created.get();
    }

    public int getUpdated() {
      return updated.get();
    }

    public int getRemoved() {
      return removed.get();
    }

    public ArrayList<CacheEntryEvent<K, V>> getEntries() {
      return entries;
    }

    @Override
    public void onCreated(Iterable<CacheEntryEvent<? extends K, ? extends V>> events) throws CacheEntryListenerException {
      for (CacheEntryEvent<? extends K, ? extends V> event : events) {
        assertEquals(CREATED, event.getEventType());
        created.incrementAndGet();

        // added for code coverage.
        event.getKey();
        event.getValue();
        event.getSource();
      }
    }

    @Override
    public void onExpired(Iterable<CacheEntryEvent<? extends K, ? extends V>> events) throws CacheEntryListenerException {
      //SKIP: we don't count expiry events as they can occur asynchronously
    }

    @Override
    public void onRemoved(Iterable<CacheEntryEvent<? extends K, ? extends V>> events) throws CacheEntryListenerException {
      for (CacheEntryEvent<? extends K, ? extends V> event : events) {
        assertEquals(REMOVED, event.getEventType());
        removed.incrementAndGet();
        event.getKey();
        if (event.isOldValueAvailable()) {
          event.getOldValue();
        }
      }
    }

    @Override
    public void onUpdated(Iterable<CacheEntryEvent<? extends K, ? extends V>> events) throws CacheEntryListenerException {
      for (CacheEntryEvent<? extends K, ? extends V> event : events) {
        assertEquals(UPDATED, event.getEventType());
        updated.incrementAndGet();
        event.getKey();
        if (event.isOldValueAvailable()) {
          event.getOldValue();
        }
      }
    }

    @Override
    public void close() throws Exception {
      // added for code coverage
    }
  }


}
