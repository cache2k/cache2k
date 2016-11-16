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

package org.jsr107.tck.expiry;

import org.jsr107.tck.event.CacheEntryListenerClient;
import org.jsr107.tck.event.CacheEntryListenerServer;
import org.jsr107.tck.integration.CacheLoaderClient;
import org.jsr107.tck.integration.CacheLoaderServer;
import org.jsr107.tck.integration.RecordingCacheLoader;
import org.jsr107.tck.processor.AssertNotPresentEntryProcessor;
import org.jsr107.tck.processor.CombineEntryProcessor;
import org.jsr107.tck.processor.GetEntryProcessor;
import org.jsr107.tck.processor.SetEntryProcessor;
import org.jsr107.tck.testutil.CacheTestSupport;
import org.jsr107.tck.testutil.ExcludeListExcluder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.cache.Cache;
import javax.cache.Cache.Entry;
import javax.cache.configuration.Factory;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.expiry.ModifiedExpiryPolicy;
import javax.cache.expiry.TouchedExpiryPolicy;
import javax.cache.integration.CompletionListenerFuture;
import javax.cache.processor.EntryProcessor;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.jsr107.tck.testutil.TestSupport.MBeanType.CacheStatistics;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Unit Tests for expiring cache entries with {@link javax.cache.expiry.ExpiryPolicy}s.
 *
 * @author Brian Oliver
 * @author Joe Fialli
 */
public class CacheExpiryTest extends CacheTestSupport<Integer, Integer> {

  /**
   * Rule used to exclude tests
   */
  @Rule
  public ExcludeListExcluder rule = new ExcludeListExcluder(this.getClass());

  private ExpiryPolicyServer expiryPolicyServer;
  private ExpiryPolicyClient expiryPolicyClient;

  @Before
  public void setUp() throws IOException {
    //establish and open a ExpiryPolicyServer to handle cache
    //cache loading requests from a ExpiryPolicyClient
    expiryPolicyServer = new ExpiryPolicyServer(10005);
    expiryPolicyServer.open();

    //establish a ExpiryPolicyClient that a Cache can use for computing expiry policy
    //(via the ExpiryPolicyServer)
    expiryPolicyClient =
        new ExpiryPolicyClient(expiryPolicyServer.getInetAddress(), expiryPolicyServer.getPort());

    cacheEntryListenerServer = new CacheEntryListenerServer<Integer, Integer>(10011, Integer.class, Integer.class);
    cacheEntryListenerServer.open();
    cacheEntryListerClient =
      new CacheEntryListenerClient<>(cacheEntryListenerServer.getInetAddress(), cacheEntryListenerServer.getPort());
  }

  @Override
  protected MutableConfiguration<Integer, Integer> newMutableConfiguration() {
    return new MutableConfiguration<Integer, Integer>().setTypes(Integer.class, Integer.class);
  }

  @Override
  protected MutableConfiguration<Integer, Integer> extraSetup(MutableConfiguration<Integer, Integer> configuration) {
    listener = new CacheTestSupport.MyCacheEntryListener<Integer, Integer>();

    //establish a CacheEntryListenerClient that a Cache can use for CacheEntryListening
    //(via the CacheEntryListenerServer)

    listenerConfiguration =
      new MutableCacheEntryListenerConfiguration<>(FactoryBuilder.factoryOf(cacheEntryListerClient), null, true, true);
    cacheEntryListenerServer.addCacheEventListener(listener);
    return configuration.addCacheEntryListenerConfiguration(listenerConfiguration);
  }


  @After
  public void cleanupAfterEachTest() throws InterruptedException {
    for (String cacheName : getCacheManager().getCacheNames()) {
      getCacheManager().destroyCache(cacheName);
    }
    expiryPolicyServer.close();
    expiryPolicyServer = null;

    //close the server
    cacheEntryListenerServer.close();
    cacheEntryListenerServer = null;
  }


  @Test
  public void testCacheStatisticsRemoveAll() throws Exception {

    //cannot be zero or will not be added to the cache
    ExpiryPolicy policy = new CreatedExpiryPolicy(new Duration(TimeUnit.MILLISECONDS, 20));
    expiryPolicyServer.setExpiryPolicy(policy);

    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient)).setStatisticsEnabled(true);
    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

    for (int i = 0; i < 100; i++) {
      cache.put(i, i+100);
    }
    //should work with all implementations
    Thread.sleep(1100);
    cache.removeAll();

    assertEquals(100L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    //Removals does not count expired entries
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));

  }

  @Test
  public void testCacheStatisticsRemoveAllNoneExpired() throws Exception {

    ExpiryPolicy policy = new CreatedExpiryPolicy(Duration.ETERNAL);
    expiryPolicyServer.setExpiryPolicy(policy);

    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient))
        .setStatisticsEnabled(true);
    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

    for (int i = 0; i < 100; i++) {
      cache.put(i, i+100);
    }

    cache.removeAll();

    assertEquals(100L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(100L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
  }


  /**
   * Assert "The minimum allowed TimeUnit is TimeUnit.MILLISECONDS.
   */
  @Test(expected = IllegalArgumentException.class)
  public void microsecondsInvalidDuration() {
    Duration invalidDuration = new Duration(TimeUnit.MICROSECONDS, 0);
    assertTrue("expected IllegalArgumentException for TimeUnit below minimum of MILLISECONDS", invalidDuration == null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void nanosecondsInvalidDuration() {
    Duration invalidDuration = new Duration(TimeUnit.NANOSECONDS, 0);
    assertTrue("expected IllegalArgumentException for TimeUnit below minimum of MILLISECONDS", invalidDuration == null);
  }

  /**
   * Ensure that a cache using a {@link javax.cache.expiry.ExpiryPolicy} configured to
   * return a {@link Duration#ZERO} for newly created entries will immediately
   * expire the entries.
   */
  private void expire_whenCreated(Factory<? extends ExpiryPolicy> expiryPolicyFactory) {
    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<Integer, Integer>();
    config.setTypes(Integer.class, Integer.class);
    config.setExpiryPolicyFactory(expiryPolicyFactory);
    config = extraSetup(config);
    config.setStatisticsEnabled(true);

    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

    cache.put(1, 1);


    assertFalse(cache.containsKey(1));
    assertNull(cache.get(1));
    assertEquals(0, listener.getCreated());

    cache.put(1, 1);

    assertFalse(cache.remove(1));

    cache.put(1, 1);

    assertFalse(cache.remove(1, 1));

    cache.getAndPut(1, 1);
    assertEquals(0, listener.getCreated());

    assertFalse(cache.containsKey(1));
    assertNull(cache.get(1));

    cache.putIfAbsent(1, 1);
    assertEquals(0, listener.getCreated());

    assertFalse(cache.containsKey(1));
    assertNull(cache.get(1));

    HashMap<Integer, Integer> map = new HashMap<Integer, Integer>();
    map.put(1, 1);
    cache.putAll(map);
    assertEquals(0, listener.getCreated());

    assertFalse(cache.containsKey(1));
    assertNull(cache.get(1));

    cache.put(1, 1);
    assertEquals(0, listener.getCreated());

    assertFalse(cache.iterator().hasNext());

    cache.getAndPut(1, 1);
    assertEquals(0, listener.getCreated());
  }

  @Test
  public void expire_whenCreated_ParameterizedExpiryPolicy() {
    expire_whenCreated(FactoryBuilder.factoryOf(new ParameterizedExpiryPolicy(Duration.ZERO, null, null)));
  }

  @Test
  public void expire_whenCreated_CreatedExpiryPolicy() {
    expire_whenCreated(FactoryBuilder.factoryOf(new CreatedExpiryPolicy(Duration.ZERO)));
  }

  @Test
  public void expire_whenCreated_AccessedExpiryPolicy() {
    // since AccessedExpiryPolicy uses same duration for created and accessed, this policy will work same as
    // CreatedExpiryPolicy for this test.
    expire_whenCreated(FactoryBuilder.factoryOf(new AccessedExpiryPolicy(Duration.ZERO)));
  }

  @Test
  public void expire_whenCreated_TouchedExpiryPolicy() {
    // since TouchedExpiryPolicy uses same duration for created and accessed, this policy will work same as
    // CreatedExpiryPolicy for this test.
    expire_whenCreated(FactoryBuilder.factoryOf(new TouchedExpiryPolicy(Duration.ZERO)));
  }

  @Test
  public void expire_whenCreated_ModifiedExpiryPolicy() {
    // since TouchedExpiryPolicy uses same duration for created and accessed, this policy will work same as
    // CreatedExpiryPolicy for this test.
    expire_whenCreated(FactoryBuilder.factoryOf(new ModifiedExpiryPolicy(Duration.ZERO)));
  }

  /**
   * Ensure that a cache using a {@link javax.cache.expiry.ExpiryPolicy} configured to
   * return a {@link Duration#ZERO} after accessing entries will immediately
   * expire the entries.
   */
  @Test
  public void expire_whenAccessed() {
    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<Integer, Integer>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(new ParameterizedExpiryPolicy(Duration.ETERNAL, Duration.ZERO, null)));

    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

    cache.put(1, 1);

    assertTrue(cache.containsKey(1));
    assertNotNull(cache.get(1));
    assertFalse(cache.containsKey(1));
    assertNull(cache.get(1));

    cache.put(1, 1);

    assertTrue(cache.containsKey(1));
    assertNotNull(cache.get(1));
    assertFalse(cache.containsKey(1));
    assertNull(cache.getAndReplace(1, 2));

    cache.put(1, 1);

    assertTrue(cache.containsKey(1));
    assertNotNull(cache.get(1));
    assertFalse(cache.containsKey(1));
    assertNull(cache.getAndRemove(1));

    cache.put(1, 1);
    assertTrue(cache.containsKey(1));
    assertNotNull(cache.get(1));
    assertFalse(cache.remove(1));

    cache.put(1, 1);

    assertTrue(cache.containsKey(1));
    assertNotNull(cache.get(1));
    assertFalse(cache.remove(1, 1));

    cache.getAndPut(1, 1);

    assertTrue(cache.containsKey(1));
    assertNotNull(cache.get(1));
    assertFalse(cache.containsKey(1));
    assertNull(cache.get(1));

    cache.getAndPut(1, 1);

    assertTrue(cache.containsKey(1));
    assertNotNull(cache.getAndPut(1, 1));
    assertTrue(cache.containsKey(1));
    assertNotNull(cache.get(1));
    assertFalse(cache.containsKey(1));
    assertNull(cache.get(1));

    cache.putIfAbsent(1, 1);

    assertTrue(cache.containsKey(1));
    assertNotNull(cache.get(1));
    assertFalse(cache.containsKey(1));
    assertNull(cache.get(1));

    HashMap<Integer, Integer> map = new HashMap<Integer, Integer>();
    map.put(1, 1);
    cache.putAll(map);

    assertTrue(cache.containsKey(1));
    assertNotNull(cache.get(1));
    assertFalse(cache.containsKey(1));
    assertNull(cache.get(1));

    cache.put(1, 1);
    Iterator<Entry<Integer, Integer>> iterator = cache.iterator();

    assertTrue(iterator.hasNext());
    assertEquals((Integer) 1, iterator.next().getValue());
    assertFalse(cache.iterator().hasNext());
  }

  /**
   * Ensure that a cache using a {@link javax.cache.expiry.ExpiryPolicy} configured to
   * return a {@link Duration#ZERO} after modifying entries will immediately
   * expire the entries.
   */
  @Test
  public void expire_whenModified() {
    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<Integer, Integer>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(new ParameterizedExpiryPolicy(Duration.ETERNAL, null, Duration.ZERO)));

    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

    cache.put(1, 1);

    assertTrue(cache.containsKey(1));
    assertTrue(cache.containsKey(1));
    assertEquals((Integer) 1, cache.get(1));
    assertEquals((Integer) 1, cache.get(1));

    cache.put(1, 2);

    assertFalse(cache.containsKey(1));
    assertNull(cache.get(1));

    cache.put(1, 1);

    assertTrue(cache.containsKey(1));
    assertEquals((Integer) 1, cache.get(1));

    cache.put(1, 2);

    assertFalse(cache.remove(1));

    cache.put(1, 1);

    assertTrue(cache.containsKey(1));
    assertEquals((Integer) 1, cache.get(1));

    cache.put(1, 2);

    assertFalse(cache.remove(1, 2));

    cache.getAndPut(1, 1);

    assertTrue(cache.containsKey(1));
    assertEquals((Integer) 1, cache.get(1));

    cache.put(1, 2);

    assertFalse(cache.containsKey(1));
    assertNull(cache.get(1));

    cache.getAndPut(1, 1);

    assertTrue(cache.containsKey(1));
    assertEquals((Integer) 1, cache.getAndPut(1, 2));
    assertFalse(cache.containsKey(1));
    assertNull(cache.get(1));

    cache.put(1, 1);

    assertTrue(cache.containsKey(1));
    assertEquals((Integer) 1, cache.get(1));

    HashMap<Integer, Integer> map = new HashMap<Integer, Integer>();
    map.put(1, 2);
    cache.putAll(map);

    assertFalse(cache.containsKey(1));
    assertNull(cache.get(1));

    cache.put(1, 1);

    assertTrue(cache.containsKey(1));
    assertEquals((Integer) 1, cache.get(1));

    cache.replace(1, 2);

    assertFalse(cache.containsKey(1));
    assertNull(cache.get(1));

    cache.put(1, 1);

    assertTrue(cache.containsKey(1));
    assertEquals((Integer) 1, cache.get(1));

    cache.replace(1, 1, 2);

    assertFalse(cache.containsKey(1));
    assertNull(cache.get(1));

    cache.put(1, 1);

    assertTrue(cache.iterator().hasNext());
    assertEquals((Integer) 1, cache.iterator().next().getValue());
    assertTrue(cache.containsKey(1));
    assertEquals((Integer) 1, cache.iterator().next().getValue());

    cache.put(1, 2);

    assertFalse(cache.iterator().hasNext());
  }

  // Next set of tests verify table from jsr 107 spec on how each of the cache methods interact with a
  // configured ExpiryPolicy method getting called.
  // There is one test per row in table.

  @Test
  public void containsKeyShouldNotCallExpiryPolicyMethods() {

    CountingExpiryPolicy expiryPolicy = new CountingExpiryPolicy();
    expiryPolicyServer.setExpiryPolicy(expiryPolicy);

    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient));
    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

    cache.containsKey(1);

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));

    cache.put(1, 1);

    assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(1));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
    expiryPolicy.resetCount();

    cache.containsKey(1);

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
  }

  @Test
  public void getShouldCallGetExpiryForAccessedEntry() {
    CountingExpiryPolicy expiryPolicy = new CountingExpiryPolicy();
    expiryPolicyServer.setExpiryPolicy(expiryPolicy);

    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient));
    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

    cache.containsKey(1);

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));

    // when getting a non-existent entry, getExpiryForAccessedEntry is not called.
    cache.get(1);

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));

    cache.put(1, 1);

    assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(1));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
    expiryPolicy.resetCount();

    // when getting an existing entry, getExpiryForAccessedEntry is called.
    cache.get(1);

    assertThat(expiryPolicy.getCreationCount(),is(0));
    assertThat(expiryPolicy.getAccessCount(), greaterThanOrEqualTo(1));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
    expiryPolicy.resetCount();
  }

  @Test
  public void getAllShouldCallGetExpiryForAccessedEntry() {
    CountingExpiryPolicy expiryPolicy = new CountingExpiryPolicy();
    expiryPolicyServer.setExpiryPolicy(expiryPolicy);

    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient));
    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);
    Set<Integer> keys = new HashSet<>();
    keys.add(1);
    keys.add(2);

    // when getting a non-existent entry, getExpiryForAccessedEntry is not called.
    cache.getAll(keys);

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));

    cache.put(1, 1);
    cache.put(2, 2);

    assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(2));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
    expiryPolicy.resetCount();

    // when getting an existing entry, getExpiryForAccessedEntry is called.
    cache.get(1);
    cache.get(2);

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), greaterThanOrEqualTo(2));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
    expiryPolicy.resetCount();
  }

  @Test
  public void getAndPutShouldCallEitherCreatedOrModifiedExpiryPolicy() {
    CountingExpiryPolicy expiryPolicy = new CountingExpiryPolicy();
    expiryPolicyServer.setExpiryPolicy(expiryPolicy);

    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient));
    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

    cache.containsKey(1);

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));

    cache.getAndPut(1, 1);

    assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(1));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
    expiryPolicy.resetCount();

    cache.getAndPut(1, 2);

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), greaterThanOrEqualTo(1));
    expiryPolicy.resetCount();
  }

  @Test
  public void getAndRemoveShouldNotCallExpiryPolicyMethods() {
    CountingExpiryPolicy expiryPolicy = new CountingExpiryPolicy();
    expiryPolicyServer.setExpiryPolicy(expiryPolicy);

    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient));
    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

    // verify case when entry is non-existent
    cache.containsKey(1);

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));

    cache.getAndRemove(1);

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));

    // verify case when entry exist
    cache.put(1, 1);

    assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(1));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
    expiryPolicy.resetCount();

    int value = cache.getAndRemove(1);
    assertThat(value, is(1));

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
  }

  @Test
  public void getAndReplaceShouldCallGetExpiryForModifiedEntry() {
    CountingExpiryPolicy expiryPolicy = new CountingExpiryPolicy();
    expiryPolicyServer.setExpiryPolicy(expiryPolicy);

    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient));
    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

    cache.containsKey(1);

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));

    cache.getAndReplace(1, 1);

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));

    cache.put(1, 1);

    assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(1));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
    expiryPolicy.resetCount();

    int oldValue = cache.getAndReplace(1, 2);

    assertEquals(1, oldValue);
    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), greaterThanOrEqualTo(1));
    expiryPolicy.resetCount();
  }

  // Skip negative to verify getCacheManager, getConfiguration or getName not calling getExpiryFor*.
  // They are not methods that access/mutate entries in cache.

  @Test
  public void iteratorNextShouldCallGetExpiryForAccessedEntry() {
    CountingExpiryPolicy expiryPolicy = new CountingExpiryPolicy();
    expiryPolicyServer.setExpiryPolicy(expiryPolicy);

    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient));
    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);
    Set<Integer> keys = new HashSet<>();
    keys.add(1);
    keys.add(2);

    cache.put(1, 1);
    cache.put(2, 2);

    assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(2));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
    expiryPolicy.resetCount();

    // when getting an existing entry, getExpiryForAccessedEntry is called.
    Iterator<Entry<Integer, Integer>> iter = cache.iterator();
    int count = 0;
    while (iter.hasNext()) {
      Entry<Integer, Integer> entry = iter.next();
      count++;

      assertThat(expiryPolicy.getCreationCount(), is(0));
      assertThat(expiryPolicy.getAccessCount(), greaterThanOrEqualTo(1));
      assertThat(expiryPolicy.getUpdatedCount(), is(0));
      expiryPolicy.resetCount();
    }
  }

  @Test
  public void loadAllWithReadThroughEnabledShouldCallGetExpiryForCreatedEntry() throws IOException, ExecutionException, InterruptedException {
    //establish and open a CacheLoaderServer to handle cache
    //cache loading requests from a CacheLoaderClient

    // this cacheLoader just returns the key as the value.
    RecordingCacheLoader<Integer> recordingCacheLoader = new RecordingCacheLoader<>();
    try (CacheLoaderServer<Integer, Integer> cacheLoaderServer = new CacheLoaderServer<>(10000, recordingCacheLoader)) {
      cacheLoaderServer.open();

      //establish a CacheLoaderClient that a Cache can use for loading entries
      //(via the CacheLoaderServer)
      CacheLoaderClient<Integer, Integer> cacheLoader =
          new CacheLoaderClient<>(cacheLoaderServer.getInetAddress(), cacheLoaderServer.getPort());

      CountingExpiryPolicy expiryPolicy = new CountingExpiryPolicy();
      expiryPolicyServer.setExpiryPolicy(expiryPolicy);

      MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
      config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient));
      config.setCacheLoaderFactory(FactoryBuilder.factoryOf(cacheLoader));
      config.setReadThrough(true);

      Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

      final Integer INITIAL_KEY = 123;
      final Integer MAX_KEY_VALUE = INITIAL_KEY + 4;

      // set half of the keys so half of loadAdd will be loaded
      Set<Integer> keys = new HashSet<>();
      for (int key = INITIAL_KEY; key <= MAX_KEY_VALUE; key++) {
        keys.add(key);
      }

      // verify read-through of getValue of non-existent entries
      CompletionListenerFuture future = new CompletionListenerFuture();
      cache.loadAll(keys, false, future);

      //wait for the load to complete
      future.get();

      assertThat(future.isDone(), is(true));
      assertThat(recordingCacheLoader.getLoadCount(), is(keys.size()));

      assertThat(expiryPolicy.getCreationCount(),greaterThanOrEqualTo(keys.size()));
      assertThat(expiryPolicy.getAccessCount(), is(0));
      assertThat(expiryPolicy.getUpdatedCount(), is(0));
      expiryPolicy.resetCount();

      for (Integer key : keys) {
        assertThat(recordingCacheLoader.hasLoaded(key), is(true));
        assertThat(cache.get(key), is(equalTo(key)));
      }
      assertThat(expiryPolicy.getAccessCount(), greaterThanOrEqualTo(keys.size()));
      expiryPolicy.resetCount();

        // verify read-through of getValue for existing entries AND replaceExistingValues is true.
      final boolean REPLACE_EXISTING_VALUES = true;
      future = new CompletionListenerFuture();
      cache.loadAll(keys, REPLACE_EXISTING_VALUES, future);

      //wait for the load to complete
      future.get();

      assertThat(future.isDone(), is(true));
      assertThat(recordingCacheLoader.getLoadCount(), is(keys.size() * 2));

      assertThat(expiryPolicy.getCreationCount(), is(0));
      assertThat(expiryPolicy.getAccessCount(), is(0));
      assertThat(expiryPolicy.getUpdatedCount(), greaterThanOrEqualTo(keys.size()));
      expiryPolicy.resetCount();

      for (Integer key : keys) {
        assertThat(recordingCacheLoader.hasLoaded(key), is(true));
        assertThat(cache.get(key), is(equalTo(key)));
      }
    }
  }


  @Test
  public void putShouldCallGetExpiry() {
    CountingExpiryPolicy expiryPolicy = new CountingExpiryPolicy();
    expiryPolicyServer.setExpiryPolicy(expiryPolicy);

    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient));
    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

    cache.containsKey(1);

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));

    cache.put(1, 1);

    assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(1));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
    expiryPolicy.resetCount();

    cache.put(1, 1);

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), greaterThanOrEqualTo(1));
    expiryPolicy.resetCount();
  }

  @Test
  public void putAllShouldCallGetExpiry() {
    CountingExpiryPolicy expiryPolicy = new CountingExpiryPolicy();
    expiryPolicyServer.setExpiryPolicy(expiryPolicy);

    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient));
    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

    Map<Integer, Integer> map = new HashMap<>();
    map.put(1, 1);
    map.put(2, 2);

    cache.put(1, 1);

    assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(1));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
    expiryPolicy.resetCount();

    cache.putAll(map);

    assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(1));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), greaterThanOrEqualTo(1));
    expiryPolicy.resetCount();
  }

  @Test
  public void putIfAbsentShouldCallGetExpiry() {
    CountingExpiryPolicy expiryPolicy = new CountingExpiryPolicy();
    expiryPolicyServer.setExpiryPolicy(expiryPolicy);

    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient));
    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

    cache.containsKey(1);

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));

    boolean result = cache.putIfAbsent(1, 1);

    assertTrue(result);
    assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(1));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
    expiryPolicy.resetCount();

    result = cache.putIfAbsent(1, 2);

    assertFalse(result);
    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
  }

  @Test
  public void removeEntryShouldNotCallExpiryPolicyMethods() {
    CountingExpiryPolicy expiryPolicy = new CountingExpiryPolicy();
    expiryPolicyServer.setExpiryPolicy(expiryPolicy);

    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient));
    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

    boolean result = cache.remove(1);

    assertFalse(result);
    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));

    cache.put(1, 1);

    assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(1));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
    expiryPolicy.resetCount();

    result = cache.remove(1);

    assertTrue(result);
    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
  }

  @Test
  public void removeSpecifiedEntryShouldNotCallExpiryPolicyMethods() {
    CountingExpiryPolicy expiryPolicy = new CountingExpiryPolicy();
    expiryPolicyServer.setExpiryPolicy(expiryPolicy);

    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient));
    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

    boolean result = cache.remove(1, 1);

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));

    cache.put(1, 1);

    assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(1));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
    expiryPolicy.resetCount();

    result = cache.remove(1, 2);

    assertFalse(result);
    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(1));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
    expiryPolicy.resetCount();

    result = cache.remove(1, 1);

    assertTrue(result);
    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
  }

  // skipping test for removeAll and removeAll specified keys for now. negative test of remove seems enough.

  @Test
  public void invokeSetValueShouldCallGetExpiry() {

    CountingExpiryPolicy expiryPolicy = new CountingExpiryPolicy();
    expiryPolicyServer.setExpiryPolicy(expiryPolicy);

    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient));
    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

    final Integer key = 123;
    final Integer setValue = 456;
    final Integer modifySetValue = 789;

    // verify create
    EntryProcessor processors[] =
        new EntryProcessor[]{
            new AssertNotPresentEntryProcessor(null),
            new SetEntryProcessor<Integer, Integer>(setValue),
            new GetEntryProcessor<Integer, Integer>()
        };
    Object[] result = (Object[]) cache.invoke(key, new CombineEntryProcessor(processors));

    assertEquals(result[1], setValue);
    assertEquals(result[2], setValue);

    // expiry called should be for create, not for the get or modify.
    // Operations get combined in entry processor and only net result should be expiryPolicy method called.
    assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(1));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
    expiryPolicy.resetCount();

    // verify modify
    Integer resultValue = cache.invoke(key, new SetEntryProcessor<Integer, Integer>(modifySetValue));
    assertEquals(modifySetValue, resultValue);

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), greaterThanOrEqualTo(1));
  }


  @Test
  public void invokeMultiSetValueShouldCallGetExpiry() {

    CountingExpiryPolicy expiryPolicy = new CountingExpiryPolicy();
    expiryPolicyServer.setExpiryPolicy(expiryPolicy);

    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient));
    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

    final Integer key = 123;
    final Integer setValue = 456;
    final Integer modifySetValue = 789;

    // verify create
    EntryProcessor processors[] =
        new EntryProcessor[]{
            new AssertNotPresentEntryProcessor(null),
            new SetEntryProcessor<Integer, Integer>(111),
            new SetEntryProcessor<Integer, Integer>(setValue),
            new GetEntryProcessor<Integer, Integer>()
        };
    Object[] result = (Object[]) cache.invoke(key, new CombineEntryProcessor(processors));

    assertEquals(result[1], 111);
    assertEquals(result[2], setValue);
    assertEquals(result[3], setValue);

    // expiry called should be for create, not for the get or modify.
    // Operations get combined in entry processor and only net result should be expiryPolicy method called.
    assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(1));
    assertThat(expiryPolicy.getAccessCount(), greaterThanOrEqualTo(0));
    assertThat(expiryPolicy.getUpdatedCount(), greaterThanOrEqualTo(0));
  }

  @Test
  public void invokeGetValueShouldCallGetExpiry() {
    CountingExpiryPolicy expiryPolicy = new CountingExpiryPolicy();
    expiryPolicyServer.setExpiryPolicy(expiryPolicy);

    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient));
    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

    final Integer key = 123;
    final Integer setValue = 456;

    // verify non-access to non-existent entry does not call getExpiryForAccessedEntry. no read-through scenario.
    Integer resultValue = cache.invoke(key, new GetEntryProcessor<Integer, Integer>());

    assertEquals(null, resultValue);
    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));

    // verify access to existing entry.
    resultValue = cache.invoke(key, new SetEntryProcessor<Integer, Integer>(setValue));

    assertEquals(resultValue, setValue);
    assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(1));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
    expiryPolicy.resetCount();

    resultValue = cache.invoke(key, new GetEntryProcessor<Integer, Integer>());

    assertEquals(setValue, resultValue);
    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), greaterThanOrEqualTo(1));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
  }

  @Test
  public void invokeGetValueWithReadThroughForNonExistentEntryShouldCallGetExpiryForCreatedEntry() throws IOException {

    //establish and open a CacheLoaderServer to handle cache
    //cache loading requests from a CacheLoaderClient

    // this cacheLoader just returns the key as the value.
    RecordingCacheLoader<Integer> recordingCacheLoader = new RecordingCacheLoader<>();
    try (CacheLoaderServer<Integer, Integer> cacheLoaderServer = new CacheLoaderServer<>(10000, recordingCacheLoader)) {
      cacheLoaderServer.open();

      //establish a CacheLoaderClient that a Cache can use for loading entries
      //(via the CacheLoaderServer)
      CacheLoaderClient<Integer, Integer> cacheLoader =
          new CacheLoaderClient<>(cacheLoaderServer.getInetAddress(), cacheLoaderServer.getPort());

      CountingExpiryPolicy expiryPolicy = new CountingExpiryPolicy();
      expiryPolicyServer.setExpiryPolicy(expiryPolicy);

      MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
      config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient));
      config.setCacheLoaderFactory(FactoryBuilder.factoryOf(cacheLoader));
      config.setReadThrough(true);
      Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

      final Integer key = 123;
      final Integer recordingCacheLoaderValue = key;

      // verify create when read through is enabled and entry was non-existent in cache.
      Integer resultValue = cache.invoke(key, new GetEntryProcessor<Integer, Integer>());

      assertEquals(recordingCacheLoaderValue, resultValue);
      assertTrue(recordingCacheLoader.hasLoaded(key));

      assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(1));
      assertThat(expiryPolicy.getAccessCount(), is(0));
      assertThat(expiryPolicy.getUpdatedCount(), is(0));
    }
  }

  @Test
  public void invokeAllSetValueShouldCallGetExpiry() {

    CountingExpiryPolicy expiryPolicy = new CountingExpiryPolicy();
    expiryPolicyServer.setExpiryPolicy(expiryPolicy);

    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient));
    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

    final Integer INITIAL_KEY = 123;
    final Integer MAX_KEY_VALUE = INITIAL_KEY + 4;
    final Integer setValue = 456;
    final Integer modifySetValue = 789;

    // set half of the keys so half of invokeAll will be modify and rest will be create.
    Set<Integer> keys = new HashSet<>();
    int createdCount = 0;
    for (int key = INITIAL_KEY; key <= MAX_KEY_VALUE; key++) {
      keys.add(key);
      if (key <= MAX_KEY_VALUE - 2) {
        cache.put(key, setValue);
        createdCount++;
      }
    }

    assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(createdCount));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
    expiryPolicy.resetCount();

    // verify modify or create
    cache.invokeAll(keys, new SetEntryProcessor<Integer, Integer>(setValue));

    assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(keys.size() - createdCount));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), greaterThanOrEqualTo(createdCount));
    expiryPolicy.resetCount();

    // verify accessed
    cache.invokeAll(keys, new GetEntryProcessor<Integer, Integer>());

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), greaterThanOrEqualTo(keys.size()));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
  }

  @Test
  public void invokeAllReadThroughEnabledGetOnNonExistentEntry() throws IOException {
    //establish and open a CacheLoaderServer to handle cache
    //cache loading requests from a CacheLoaderClient

    // this cacheLoader just returns the key as the value.
    RecordingCacheLoader<Integer> recordingCacheLoader = new RecordingCacheLoader<>();
    try (CacheLoaderServer<Integer, Integer> cacheLoaderServer = new CacheLoaderServer<>(10000, recordingCacheLoader)) {
      cacheLoaderServer.open();

      //establish a CacheLoaderClient that a Cache can use for loading entries
      //(via the CacheLoaderServer)
      CacheLoaderClient<Integer, Integer> cacheLoader =
          new CacheLoaderClient<>(cacheLoaderServer.getInetAddress(), cacheLoaderServer.getPort());

      CountingExpiryPolicy expiryPolicy = new CountingExpiryPolicy();
      expiryPolicyServer.setExpiryPolicy(expiryPolicy);

      MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
      config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient));
      config.setCacheLoaderFactory(FactoryBuilder.factoryOf(cacheLoader));
      config.setReadThrough(true);

      Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

      final Integer INITIAL_KEY = 123;
      final Integer MAX_KEY_VALUE = INITIAL_KEY + 4;

      // set keys to read through
      Set<Integer> keys = new HashSet<>();
      for (int key = INITIAL_KEY; key <= MAX_KEY_VALUE; key++) {
        keys.add(key);
      }

      // verify read-through of getValue of non-existent entries
      cache.invokeAll(keys, new GetEntryProcessor<Integer, Integer>());

      assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(keys.size()));
      assertThat(expiryPolicy.getAccessCount(), is(0));
      assertThat(expiryPolicy.getUpdatedCount(), is(0));
      expiryPolicy.resetCount();
    }
  }

  @Test
  public void replaceShouldCallGetExpiryForModifiedEntry() {
    CountingExpiryPolicy expiryPolicy = new CountingExpiryPolicy();
    expiryPolicyServer.setExpiryPolicy(expiryPolicy);

    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient));
    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

    cache.containsKey(1);

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));

    // verify case that replace does not occur so no expiry policy called
    boolean result = cache.replace(1, 1);

    assertFalse(result);

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));

    cache.put(1, 1);

    assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(1));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
    expiryPolicy.resetCount();

    result = cache.replace(1, 2);

    assertTrue(result);
    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), greaterThanOrEqualTo(1));
  }

  // optimized out test for unwrap method since it does not access/mutate an entry.

  @Test
  public void replaceSpecificShouldCallGetExpiry() {
    CountingExpiryPolicy expiryPolicy = new CountingExpiryPolicy();
    expiryPolicyServer.setExpiryPolicy(expiryPolicy);

    MutableConfiguration<Integer, Integer> config = new MutableConfiguration<>();
    config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(expiryPolicyClient));
    Cache<Integer, Integer> cache = getCacheManager().createCache(getTestCacheName(), config);

    cache.containsKey(1);

    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));

    boolean result = cache.replace(1, 1, 2);

    assertFalse(result);
    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));

    cache.put(1, 1);
    assertThat(expiryPolicy.getCreationCount(), greaterThanOrEqualTo(1));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
    expiryPolicy.resetCount();

    // verify case when entry exist for key, but oldValue is incorrect. So replacement does not happen.
    // this counts as an access of entry referred to by key.
    result = cache.replace(1, 2, 5);

    assertFalse(result);
    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), greaterThanOrEqualTo(1));
    assertThat(expiryPolicy.getUpdatedCount(), is(0));
    expiryPolicy.resetCount();

    // verify the modify case when replace does succeed.
    result = cache.replace(1, 1, 2);

    assertTrue(result);
    assertThat(expiryPolicy.getCreationCount(), is(0));
    assertThat(expiryPolicy.getAccessCount(), is(0));
    assertThat(expiryPolicy.getUpdatedCount(), greaterThanOrEqualTo(1));
    expiryPolicy.resetCount();
  }


  public static class CountingExpiryPolicy implements ExpiryPolicy, Serializable {

    /**
     * The number of times {@link #getExpiryForCreation()} was called.
     */
    private AtomicInteger creationCount;

    /**
     * The number of times {@link #getExpiryForAccess()} ()} was called.
     */
    private AtomicInteger accessedCount;

    /**
     * The number of times {@link #getExpiryForUpdate()} was called.
     */
    private AtomicInteger updatedCount;

    /**
     * Constructs a new {@link CountingExpiryPolicy}.
     */
    public CountingExpiryPolicy() {
      this.creationCount = new AtomicInteger(0);
      this.accessedCount = new AtomicInteger(0);
      this.updatedCount = new AtomicInteger(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Duration getExpiryForCreation() {
      creationCount.incrementAndGet();
      return Duration.ETERNAL;
    }

    /**
     * Obtains the number of times {@link #getExpiryForCreation()} has been called.
     *
     * @return the count
     */
    public int getCreationCount() {
      return creationCount.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Duration getExpiryForAccess() {
      accessedCount.incrementAndGet();
      return null;
    }

    /**
     * Obtains the number of times {@link #getExpiryForAccess()} has been called.
     *
     * @return the count
     */
    public int getAccessCount() {
      return accessedCount.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Duration getExpiryForUpdate() {
      updatedCount.incrementAndGet();
      return null;
    }

    /**
     * Obtains the number of times {@link #getExpiryForUpdate()} has been called.
     *
     * @return the count
     */
    public int getUpdatedCount() {
      return updatedCount.get();
    }

    public void resetCount()
    {
        creationCount.set(0);
        accessedCount.set(0);
        updatedCount.set(0);
    }
  }


  /**
   * A {@link javax.cache.expiry.ExpiryPolicy} that updates the expiry time based on
   * defined parameters.
   */
  public static class ParameterizedExpiryPolicy implements ExpiryPolicy, Serializable {
    /**
     * The serialVersionUID required for {@link java.io.Serializable}.
     */
    public static final long serialVersionUID = 201306141148L;

    /**
     * The {@link Duration} after which a Cache Entry will expire when created.
     */
    private Duration createdExpiryDuration;

    /**
     * The {@link Duration} after which a Cache Entry will expire when accessed.
     * (when <code>null</code> the current expiry duration will be used)
     */
    private Duration accessedExpiryDuration;

    /**
     * The {@link Duration} after which a Cache Entry will expire when modified.
     * (when <code>null</code> the current expiry duration will be used)
     */
    private Duration updatedExpiryDuration;

    /**
     * Constructs an {@link ParameterizedExpiryPolicy}.
     *
     * @param createdExpiryDuration  the {@link Duration} to expire when an entry is created
     *                               (must not be <code>null</code>)
     * @param accessedExpiryDuration the {@link Duration} to expire when an entry is accessed
     *                               (<code>null</code> means don't change the expiry)
     * @param updatedExpiryDuration  the {@link Duration} to expire when an entry is updated
     *                               (<code>null</code> means don't change the expiry)
     */
    public ParameterizedExpiryPolicy(Duration createdExpiryDuration,
                                     Duration accessedExpiryDuration,
                                     Duration updatedExpiryDuration) {
      if (createdExpiryDuration == null) {
        throw new NullPointerException("createdExpiryDuration can't be null");
      }

      this.createdExpiryDuration = createdExpiryDuration;
      this.accessedExpiryDuration = accessedExpiryDuration;
      this.updatedExpiryDuration = updatedExpiryDuration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Duration getExpiryForCreation() {
      return createdExpiryDuration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Duration getExpiryForAccess() {
      return accessedExpiryDuration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Duration getExpiryForUpdate() {
      return updatedExpiryDuration;
    }
  }

  private CacheEntryListenerServer<Integer, Integer> cacheEntryListenerServer;
  private CacheEntryListenerClient<Integer, Integer> cacheEntryListerClient;
}
