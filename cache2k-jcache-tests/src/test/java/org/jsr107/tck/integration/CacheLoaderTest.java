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
package org.jsr107.tck.integration;

import org.jsr107.tck.processor.GetEntryProcessor;
import org.jsr107.tck.testutil.ExcludeListExcluder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheLoaderException;
import javax.cache.integration.CompletionListener;
import javax.cache.integration.CompletionListenerFuture;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * Functional Tests for {@link CacheLoader}s.
 *
 * @author Brian Oliver
 */
public class CacheLoaderTest {

  /**
   * Rule used to exclude tests
   */
  @Rule
  public ExcludeListExcluder rule = new ExcludeListExcluder(CacheLoaderTest.class);

  /**
   * The {@link CacheManager} for the each test.
   */
  private CacheManager cacheManager;

  /**
   * A {@link CacheLoaderServer} that will delegate {@link Cache} request
   * onto the recording {@link CacheLoader}.
   */
  private CacheLoaderServer<String, String> cacheLoaderServer;

  /**
   * The {@link Cache} for the each test.
   */
  private Cache<String, String> cache;

  /**
   * Establish the {@link CacheManager} and {@link Cache} for a test.
   */
  @Before
  public void onBeforeEachTest() throws IOException {
    //establish and open a CacheLoaderServer to handle cache
    //cache loading requests from a CacheLoaderClient
    cacheLoaderServer = new CacheLoaderServer<String, String>(10000);
    cacheLoaderServer.open();

    //establish the CacheManager for the tests
    cacheManager = Caching.getCachingProvider().getCacheManager();

    //establish a CacheLoaderClient that a Cache can use for loading entries
    //(via the CacheLoaderServer)
    CacheLoaderClient<String, String> cacheLoader =
        new CacheLoaderClient<>(cacheLoaderServer.getInetAddress(), cacheLoaderServer.getPort());

    //establish a Cache Configuration that uses a CacheLoader and Read-Through
    MutableConfiguration<String, String> configuration = new MutableConfiguration<>();
    configuration.setTypes(String.class, String.class);
    configuration.setCacheLoaderFactory(FactoryBuilder.factoryOf(cacheLoader));
    configuration.setReadThrough(true);

    //configure the cache
    cacheManager.createCache("cache-loader-test", configuration);
    cache = cacheManager.getCache("cache-loader-test", String.class, String.class);
  }

  /**
   * Clean up the {@link CacheManager} and {@link Cache} after a test.
   */
  @After
  public void onAfterEachTest() {
    //destroy the cache
    String cacheName = cache.getName();
    cacheManager.destroyCache(cacheName);

    //close the server
    cacheLoaderServer.close();
    cacheLoaderServer = null;

    cache = null;
  }

  /**
   * Ensure that a {@link Cache#get(Object)} for a non-existent entry will
   * cause it to be loaded.
   */
  @Test
  public void shouldLoadWhenCacheMissUsingGet() {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    String key = "message";

    assertThat(cache.containsKey(key), is(false));

    String value = cache.get(key);

    assertThat(value, is(equalTo(key)));
    assertThat(cacheLoader.getLoadCount(), is(1));
    assertThat(cacheLoader.hasLoaded(key), is(true));
  }

  /**
   * Ensure accessing an entry from an {@link javax.cache.processor.EntryProcessor}
   * will cause a {@link CacheLoader} to load an entry.
   */
  @Test
  public void shouldLoadWhenAccessingWithEntryProcessor() {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    String key = "message";

    assertThat(cache.containsKey(key), is(false));

    String value = cache.invoke(key, new GetEntryProcessor<String, String>());

    assertThat(value, is(equalTo(key)));
    assertThat(cacheLoader.getLoadCount(), is(1));
    assertThat(cacheLoader.hasLoaded(key), is(true));
  }

  /**
   * Ensure that a {@link Cache#get(Object)} request for an existing entry will
   * not cause a load to occur.
   */
  @Test
  public void shouldNotLoadUsingGetWithExistingValue() {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    String key = "message";

    assertThat(cache.containsKey(key), is(false));
    cache.put(key, key);
    assertThat(cache.containsKey(key), is(true));

    String value = cache.get(key);

    assertThat(value, is(equalTo(key)));
    assertThat(cacheLoader.getLoadCount(), is(0));
    assertThat(cacheLoader.hasLoaded(key), is(false));
  }

  /**
   * Ensure that a {@link Cache#containsKey(Object)} will not cause an entry
   * to be loaded.
   */
  @Test
  public void shouldNotLoadUsingContainsKey() {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    String key = "message";

    assertThat(cacheLoader.getLoadCount(), is(0));
    assertThat(cache.containsKey(key), is(false));
    assertThat(cacheLoader.getLoadCount(), is(0));
    assertThat(cacheLoader.hasLoaded(key), is(false));
  }

  /**
   * Ensure that a {@link Cache#getAll(java.util.Set)} will load the expected
   * entries.
   */
  @Test
  public void shouldLoadUsingGetAll() {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    //construct a set of keys
    HashSet<String> keys = new HashSet<String>();
    keys.add("gudday");
    keys.add("hello");
    keys.add("howdy");
    keys.add("bonjour");

    //get the keys
    Map<String, String> map = cache.getAll(keys);

    //assert that the map content is as expected
    assertThat(map.size(), is(keys.size()));

    for (String key : keys) {
      assertThat(map.containsKey(key), is(true));
      assertThat(map.get(key), is(key));
    }

    //assert that the loader state is as expected
    assertThat(cacheLoader.getLoadCount(), is(keys.size()));

    for (String key : keys) {
      assertThat(cacheLoader.hasLoaded(key), is(true));
    }

    //attempting to load the same keys should not result in another load
    cache.getAll(keys);
    assertThat(cacheLoader.getLoadCount(), is(keys.size()));
  }

  /**
   * Ensure that a {@link Cache#getAll(java.util.Set)} using one or more
   * <code>null</code> keys will not load anything.
   */
  @Test
  public void shouldNotLoadWithNullKeyUsingGetAll() {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    //construct a set of keys
    HashSet<String> keys = new HashSet<String>();
    keys.add("gudday");
    keys.add("hello");
    keys.add("howdy");
    keys.add("bonjour");
    keys.add(null);

    //attempt to get the keys
    try {
      Map<String, String> map = cache.getAll(keys);

      fail("Should have thrown a NullPointerException");
    } catch (NullPointerException e) {
      //assert that nothing was actually loaded
      assertThat(cacheLoader.getLoadCount(), is(0));

      for (String key : keys) {
        assertThat(cacheLoader.hasLoaded(key), is(false));
      }
    }
  }

  /**
   * Ensure that iterating over a {@link Cache} does cause loading.
   */
  @Test
  public void shouldNotLoadDueToIteration() {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    //put a number of entries into the cache
    cache.put("gudday", "gudday");
    cache.put("hello", "hello");
    cache.put("howdy", "howdy");
    cache.put("bonjour", "bonjour");

    //iterate over the entries
    HashSet<String> keys = new HashSet<>();
    for (Cache.Entry<String, String> entry : cache) {
      keys.add(entry.getKey());
    }

    //assert that nothing was actually loaded
    assertThat(cacheLoader.getLoadCount(), is(0));

    for (String key : keys) {
      assertThat(cacheLoader.hasLoaded(key), is(false));
    }
  }

  /**
   * Ensure that a {@link CacheLoader} that returns <code>null</code> entries
   * aren't placed in the cache.
   */
  @Test
  public void shouldNotLoadNullEntries() {
    NullValueCacheLoader<String, String> nullCacheLoader = new NullValueCacheLoader<>();
    cacheLoaderServer.setCacheLoader(nullCacheLoader);

    //construct a set of keys
    HashSet<String> keys = new HashSet<String>();
    keys.add("gudday");
    keys.add("hello");
    keys.add("howdy");
    keys.add("bonjour");

    //attempt to get the keys
    Map<String, String> map = cache.getAll(keys);

    //nothing should have been loaded
    assertThat(map.size(), is(0));
  }

  /**
   * Ensure that a {@link CacheLoader} that returns <code>null</code> values
   * aren't placed in the cache.
   */
  @Test
  public void shouldNotLoadNullValues() {
    NullValueCacheLoader<String, String> cacheLoader = new NullValueCacheLoader<>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    //construct a set of keys
    HashSet<String> keys = new HashSet<String>();
    keys.add("gudday");
    keys.add("hello");
    keys.add("howdy");
    keys.add("bonjour");

    //attempt to get the keys
    Map<String, String> map = cache.getAll(keys);

    //nothing should have been loaded
    assertThat(map.size(), is(0));
  }

  /**
   * Ensure that a {@link Cache#getAndPut(Object, Object)} does not cause
   * an entry to be loaded.
   */
  @Test
  public void shouldNotLoadUsingGetAndPut() {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    String key = "message";

    assertThat(cache.containsKey(key), is(false));
    String value = cache.getAndPut(key, key);
    assertThat(cache.containsKey(key), is(true));

    assertThat(value, is(nullValue()));
    assertThat(cacheLoader.getLoadCount(), is(0));
    assertThat(cacheLoader.hasLoaded(key), is(false));

    //try again with an existing value
    value = cache.getAndPut(key, key);
    assertThat(value, is(key));
    assertThat(cacheLoader.getLoadCount(), is(0));
    assertThat(cacheLoader.hasLoaded(key), is(false));
  }

  /**
   * Ensure that a {@link Cache#getAndRemove(Object)} does not cause
   * an entry to be loaded.
   */
  @Test
  public void shouldNotLoadUsingGetAndRemove() {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    String key = "message";

    assertThat(cache.containsKey(key), is(false));
    String value = cache.getAndRemove(key);
    assertThat(cache.containsKey(key), is(false));

    assertThat(value, is(nullValue()));
    assertThat(cacheLoader.getLoadCount(), is(0));
    assertThat(cacheLoader.hasLoaded(key), is(false));

    //try again with an existing value
    cache.put(key, key);
    value = cache.getAndRemove(key);
    assertThat(value, is(key));
    assertThat(cache.containsKey(key), is(false));
    assertThat(cacheLoader.getLoadCount(), is(0));
    assertThat(cacheLoader.hasLoaded(key), is(false));
  }

  /**
   * Ensure that a {@link Cache#getAndReplace(Object, Object)} does not cause
   * an entry to be loaded.
   */
  @Test
  public void shouldNotLoadUsingGetAndReplace() {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    String key = "message";

    assertThat(cache.containsKey(key), is(false));
    String value = cache.getAndReplace(key, key);
    assertThat(cache.containsKey(key), is(false));

    assertThat(value, is(nullValue()));
    assertThat(cacheLoader.getLoadCount(), is(0));
    assertThat(cacheLoader.hasLoaded(key), is(false));

    //try again with an existing value
    cache.put(key, key);
    value = cache.getAndReplace(key, "gudday");
    assertThat(value, is(key));
    assertThat(cache.containsKey(key), is(true));
    assertThat(cacheLoader.getLoadCount(), is(0));
    assertThat(cacheLoader.hasLoaded(key), is(false));
  }

  /**
   * Ensure that a {@link Cache#put(Object, Object)} )} does not cause
   * an entry to be loaded.
   */
  @Test
  public void shouldNotLoadUsingPut() {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    String key = "message";

    assertThat(cache.containsKey(key), is(false));
    cache.put(key, key);
    assertThat(cache.containsKey(key), is(true));

    assertThat(cache.get(key), is(key));
    assertThat(cacheLoader.getLoadCount(), is(0));
    assertThat(cacheLoader.hasLoaded(key), is(false));

    //try again with an existing value
    cache.put(key, "gudday");

    assertThat(cacheLoader.getLoadCount(), is(0));
    assertThat(cacheLoader.hasLoaded(key), is(false));
  }

  /**
   * Ensure that a {@link Cache#putIfAbsent(Object, Object)} does not cause
   * an entry to be loaded.
   */
  @Test
  public void shouldNotLoadUsingPutIfAbsent() {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    String key = "message";

    assertThat(cache.containsKey(key), is(false));
    cache.putIfAbsent(key, key);
    assertThat(cache.containsKey(key), is(true));

    assertThat(cache.get(key), is(key));
    assertThat(cacheLoader.getLoadCount(), is(0));
    assertThat(cacheLoader.hasLoaded(key), is(false));

    //try again with an existing value
    cache.putIfAbsent(key, key);

    assertThat(cacheLoader.getLoadCount(), is(0));
    assertThat(cacheLoader.hasLoaded(key), is(false));
  }

  /**
   * Ensure that a {@link Cache#putAll(java.util.Map)} does not cause
   * an entry to be loaded.
   */
  @Test
  public void shouldNotLoadUsingPutAll() {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    HashMap<String, String> map = new HashMap<>();
    map.put("gudday", "gudday");
    map.put("hello", "hello");
    map.put("howdy", "howdy");
    map.put("bonjour", "bonjour");

    //assert that the cache doesn't contain the map entries
    for (String key : map.keySet()) {
      assertThat(cache.containsKey(key), is(false));
    }

    cache.putAll(map);

    //assert that the cache contains the map entries
    for (String key : map.keySet()) {
      assertThat(cache.containsKey(key), is(true));
      assertThat(cache.get(key), is(map.get(key)));
    }

    //assert that nothing was loaded
    assertThat(cacheLoader.getLoadCount(), is(0));

    for (String key : map.keySet()) {
      assertThat(cacheLoader.hasLoaded(key), is(false));
    }
  }

  /**
   * Ensure that a {@link Cache#replace(Object, Object)} and
   * {@link Cache#replace(Object, Object, Object)} does not cause
   * an entry to be loaded.
   */
  @Test
  public void shouldNotLoadUsingReplace() {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    String key = "message";

    assertThat(cache.containsKey(key), is(false));
    cache.put(key, key);
    assertThat(cache.containsKey(key), is(true));

    String value = "other";
    cache.replace(key, value);

    assertThat(cache.get(key), is(value));
    assertThat(cacheLoader.getLoadCount(), is(0));
    assertThat(cacheLoader.hasLoaded(key), is(false));

    //try again with a specific value
    cache.replace(key, value, key);

    assertThat(cache.get(key), is(key));
    assertThat(cacheLoader.getLoadCount(), is(0));
    assertThat(cacheLoader.hasLoaded(key), is(false));
  }

  /**
   * Ensure that a {@link Cache#remove(Object)} and
   * {@link Cache#remove(Object, Object)}  does not cause an entry to be loaded.
   */
  @Test
  public void shouldNotLoadUsingRemove() {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    String key = "message";

    assertThat(cache.containsKey(key), is(false));
    cache.put(key, key);
    assertThat(cache.containsKey(key), is(true));

    cache.remove(key);

    assertThat(cache.containsKey(key), is(false));
    assertThat(cacheLoader.getLoadCount(), is(0));
    assertThat(cacheLoader.hasLoaded(key), is(false));

    //try again with a specific value
    cache.put(key, key);
    cache.remove(key, key);

    assertThat(cache.containsKey(key), is(false));
    assertThat(cacheLoader.getLoadCount(), is(0));
    assertThat(cacheLoader.hasLoaded(key), is(false));
  }

  /**
   * Ensure that a {@link Cache#putAll(java.util.Map)} does not cause
   * an entry to be loaded.
   */
  @Test
  public void shouldNotLoadUsingRemoveAll() {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    //put a number of entries into the cache
    cache.put("gudday", "gudday");
    cache.put("hello", "hello");
    cache.put("howdy", "howdy");
    cache.put("bonjour", "bonjour");

    cache.removeAll();

    //assert that nothing was actually loaded
    assertThat(cacheLoader.getLoadCount(), is(0));
  }

  /**
   * Ensure that {@link Cache#loadAll(Set, boolean, javax.cache.integration.CompletionListener)}
   * for a non-existent single value will cause it to be loaded.
   */
  @Test
  public void shouldLoadSingleMissingEntryUsingLoadAll() throws Exception {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    String key = "message";
    HashSet<String> keys = new HashSet<>();
    keys.add(key);

    assertThat(cache.containsKey(key), is(false));

    CompletionListenerFuture future = new CompletionListenerFuture();
    cache.loadAll(keys, false, future);

    //wait for the load to complete
    future.get();

    assertThat(future.isDone(), is(true));
    assertThat(cache.get(key), is(equalTo(key)));
    assertThat(cacheLoader.getLoadCount(), is(1));
    assertThat(cacheLoader.hasLoaded(key), is(true));
  }

  /**
   * Ensure that {@link Cache#loadAll(Set, boolean, javax.cache.integration.CompletionListener)}
   * for an existing single entry will cause it to be reloaded.
   */
  @Test
  public void shouldLoadSingleExistingEntryUsingLoadAll() throws Exception {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    String key = "message";
    HashSet<String> keys = new HashSet<>();
    keys.add(key);

    assertThat(cache.containsKey(key), is(false));

    String value = "other";
    cache.put(key, value);

    assertThat(cache.containsKey(key), is(true));
    assertThat(cache.get(key), is(value));

    CompletionListenerFuture future = new CompletionListenerFuture();
    cache.loadAll(keys, true, future);

    //wait for the load to complete
    future.get();

    assertThat(future.isDone(), is(true));
    assertThat(cache.get(key), is(equalTo(key)));
    assertThat(cacheLoader.getLoadCount(), is(1));
    assertThat(cacheLoader.hasLoaded(key), is(true));
  }

  /**
   * Ensure that {@link Cache#loadAll(Set, boolean, javax.cache.integration.CompletionListener)} )}
   * for multiple non-existing entries will be loaded.
   */
  @Test
  public void shouldLoadMultipleNonExistingEntryUsingLoadAll() throws Exception {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    HashSet<String> keys = new HashSet<>();
    keys.add("gudday");
    keys.add("hello");
    keys.add("howdy");
    keys.add("bonjour");

    for (String key : keys) {
      assertThat(cache.containsKey(key), is(false));
    }

    CompletionListenerFuture future = new CompletionListenerFuture();
    cache.loadAll(keys, false, future);

    //wait for the load to complete
    future.get();

    assertThat(future.isDone(), is(true));
    assertThat(cacheLoader.getLoadCount(), is(keys.size()));

    for (String key : keys) {
      assertThat(cache.get(key), is(equalTo(key)));
      assertThat(cacheLoader.hasLoaded(key), is(true));
    }
  }

  /**
   * Ensure that {@link Cache#loadAll(Set, boolean, javax.cache.integration.CompletionListener)}
   * for multiple existing entries will be reloaded.
   */
  @Test
  public void shouldLoadMultipleExistingEntryUsingLoadAll() throws Exception {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    HashSet<String> keys = new HashSet<>();
    keys.add("gudday");
    keys.add("hello");
    keys.add("howdy");
    keys.add("bonjour");

    String value = "other";
    for (String key : keys) {
      assertThat(cache.containsKey(key), is(false));
      cache.put(key, value);
      assertThat(cache.containsKey(key), is(true));
    }

    CompletionListenerFuture future = new CompletionListenerFuture();
    cache.loadAll(keys, true, future);

    //wait for the load to complete
    future.get();

    assertThat(future.isDone(), is(true));
    assertThat(cacheLoader.getLoadCount(), is(keys.size()));

    for (String key : keys) {
      assertThat(cache.get(key), is(equalTo(key)));
      assertThat(cacheLoader.hasLoaded(key), is(true));
    }
  }

  /**
   * Ensure that {@link Cache#loadAll(Set, boolean, javax.cache.integration.CompletionListener)}
   * won't load <code>null</code> values.
   */
  @Test
  public void shouldNotLoadMultipleNullValuesUsingLoadAll() throws Exception {
    NullValueCacheLoader<String, String> cacheLoader = new NullValueCacheLoader<>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    HashSet<String> keys = new HashSet<>();
    keys.add("gudday");
    keys.add("hello");
    keys.add("howdy");
    keys.add("bonjour");

    CompletionListenerFuture future = new CompletionListenerFuture();
    cache.loadAll(keys, false, future);

    //wait for the load to complete
    future.get();

    assertThat(future.isDone(), is(true));

    for (String key : keys) {
      assertThat(cache.containsKey(key), is(false));
    }
  }

  /**
   * Ensure that {@link Cache#loadAll(java.util.Set, boolean, javax.cache.integration.CompletionListener)}
   * won't load <code>null</code> entries.
   */
  @Test
  public void shouldNotLoadMultipleNullEntriesUsingLoadAll() throws Exception {
    NullValueCacheLoader<String, String> cacheLoader = new NullValueCacheLoader<>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    HashSet<String> keys = new HashSet<>();
    keys.add("gudday");
    keys.add("hello");
    keys.add("howdy");
    keys.add("bonjour");

    CompletionListenerFuture future = new CompletionListenerFuture();
    cache.loadAll(keys, false, future);

    //wait for the load to complete
    future.get();

    assertThat(future.isDone(), is(true));

    for (String key : keys) {
      assertThat(cache.containsKey(key), is(false));
    }
  }

  /**
   * Ensure that {@link Cache#loadAll(java.util.Set, boolean, javax.cache.integration.CompletionListener)} )}
   * using a <code>null</code> key will raise an exception
   */
  @Test
  public void shouldNotLoadWithNullKeyUsingLoadAll() throws Exception {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    HashSet<String> keys = new HashSet<>();
    keys.add(null);

    try {
      CompletionListenerFuture future = new CompletionListenerFuture();
      cache.loadAll(keys, false, future);

      fail("Expected a NullPointerException");
    } catch (NullPointerException e) {
      //SKIP: expected
    } finally {
      assertThat(cacheLoader.getLoadCount(), is(0));
    }
  }

  /**
   * Ensure that {@link Cache#loadAll(java.util.Set, boolean, javax.cache.integration.CompletionListener)}
   * using a <code>null</code> key will raise an exception
   */
  @Test
  public void shouldNotLoadWithNullKeysUsingLoadAll() throws Exception {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    try {
      CompletionListenerFuture future = new CompletionListenerFuture();
      cache.loadAll(null, false, future);

      fail("Expected a NullPointerException");
    } catch (NullPointerException e) {
      //SKIP: expected
    } finally {
      assertThat(cacheLoader.getLoadCount(), is(0));
    }
  }

  /**
   * Ensure that {@link Cache#get(Object)} will propagate an exception from a
   * {@link CacheLoader}.
   */
  @Test
  public void shouldPropagateExceptionUsingGet() {
    FailingCacheLoader<String, String> cacheLoader = new FailingCacheLoader<>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    try {
      String key = "message";
      cache.get(key);

      fail();
    } catch (CacheLoaderException e) {
      //expected
    }
  }

  /**
   * Ensure that {@link Cache#loadAll(java.util.Set, boolean, javax.cache.integration.CompletionListener)}  )}
   * will propagate an exception from a {@link CacheLoader}.
   */
  @Test
  public void shouldPropagateExceptionUsingLoadAll() throws Exception {
    FailingCacheLoader<String, String> cacheLoader = new FailingCacheLoader<>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    HashSet<String> keys = new HashSet<>();
    keys.add("gudday");
    keys.add("hello");
    keys.add("howdy");
    keys.add("bonjour");

    CompletionListenerFuture future = new CompletionListenerFuture();
    cache.loadAll(keys, false, future);

    //wait for the load to complete
    try{
      future.get();
    } catch (ExecutionException e) {
      assertThat(e.getCause(), instanceOf(CacheLoaderException.class));
    }
  }

  /**
   * Added for code coverage.
   */
  @Test
  public void testLoadAllWithExecptionAndNoCompletionListener() throws Exception {
    FailingCacheLoader<String, String> cacheLoader = new FailingCacheLoader<>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    HashSet<String> keys = new HashSet<>();
    keys.add("gudday");
    keys.add("hello");
    keys.add("howdy");
    keys.add("bonjour");

    CompletionListener NULL_COMPLETION_LISTENER = null;
    cache.loadAll(keys, false, NULL_COMPLETION_LISTENER);
  }
}
