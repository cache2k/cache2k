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
import javax.cache.integration.CompletionListenerFuture;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * Functional Tests {@link Cache}s that have been configured with both CacheLoaders
 * and CacheWriters.
 *
 * @author Brian Oliver
 */
public class CacheLoaderWriterTest {

  /**
   * Rule used to exclude tests
   */
  @Rule
  public ExcludeListExcluder rule = new ExcludeListExcluder(CacheLoaderWriterTest.class);

  /**
   * The {@link javax.cache.CacheManager} for the each test.
   */
  private CacheManager cacheManager;

  /**
   * A {@link org.jsr107.tck.integration.CacheLoaderServer} that will delegate {@link Cache} request
   * onto the recording {@link javax.cache.integration.CacheLoader}.
   */
  private CacheLoaderServer<String, String> cacheLoaderServer;


  /**
   * A {@link RecordingCacheLoader} that will keep track of entries
   * loaded through the {@link Cache}.
   */
  private RecordingCacheLoader<String> recordingCacheLoader;


  /**
   * A {@link CacheWriterServer} that will delegate {@link Cache} request
   * onto the recording {@link javax.cache.integration.CacheWriter}.
   */
  private CacheWriterServer<String, String> cacheWriterServer;

  /**
   * A {@link RecordingCacheWriter} that will keep track of entries
   * written through the {@link Cache}.
   */
  private RecordingCacheWriter<String, String> recordingCacheWriter;

  /**
   * The {@link Cache} for the each test.
   */
  private Cache<String, String> cache;

  /**
   * Establish the {@link javax.cache.CacheManager} and {@link Cache} for a test.
   */
  @Before
  public void onBeforeEachTest() throws IOException {
    //establish and open a CacheLoaderServer to handle cache
    //cache loading requests from a CacheLoaderClient
    recordingCacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer = new CacheLoaderServer<String, String>(10000, recordingCacheLoader);
    cacheLoaderServer.open();

    // establish and open a CacheWriterServer to handle cache
    // cache loading requests from a CacheWriterClient
    recordingCacheWriter = new RecordingCacheWriter<>();
    cacheWriterServer = new CacheWriterServer<>(10001, recordingCacheWriter);
    cacheWriterServer.open();

    //establish the CacheManager for the tests
    cacheManager = Caching.getCachingProvider().getCacheManager();

    //establish a CacheLoaderClient that a Cache can use for loading entries
    //(via the CacheLoaderServer)
    CacheLoaderClient<String, String> cacheLoader =
        new CacheLoaderClient<>(cacheLoaderServer.getInetAddress(), cacheLoaderServer.getPort());

    // establish a CacheWriterClient that a Cache can use for writing/deleting entries
    // (via the CacheWriterServer)
    CacheWriterClient<String, String> cacheWriter = new CacheWriterClient<>(cacheWriterServer.getInetAddress(),
        cacheWriterServer.getPort());

    //establish a Cache Configuration that uses a CacheLoader and Read-Through
    MutableConfiguration<String, String> configuration = new MutableConfiguration<>();
    configuration.setTypes(String.class, String.class);
    configuration.setCacheLoaderFactory(FactoryBuilder.factoryOf(cacheLoader));
    configuration.setReadThrough(true);
    configuration.setCacheWriterFactory(FactoryBuilder.factoryOf(cacheWriter));
    configuration.setWriteThrough(true);

    //configure the cache
    cacheManager.createCache("cache-loader-writer-test", configuration);
    cache = cacheManager.getCache("cache-loader-writer-test", String.class, String.class);
  }

  /**
   * Clean up the {@link javax.cache.CacheManager} and {@link Cache} after a test.
   */
  @After
  public void onAfterEachTest() {
    //destroy the cache
    String cacheName = cache.getName();
    cacheManager.destroyCache(cacheName);

    //close the loader server
    cacheLoaderServer.close();
    cacheLoaderServer = null;

    //close the writer server
    cacheWriterServer.close();
    cacheWriterServer = null;

    cache = null;
  }

  /**
   * Ensure that a {@link Cache#get(Object)} for a non-existent entry will
   * cause it to be loaded but not written.
   */
  @Test
  public void shouldLoadWhenCacheMissUsingGet() {
    String key = "message";

    assertThat(cache.containsKey(key), is(false));

    String value = cache.get(key);

    assertThat(value, is(equalTo(key)));
    assertThat(recordingCacheLoader.getLoadCount(), is(1));
    assertThat(recordingCacheLoader.hasLoaded(key), is(true));

    //ensure nothing has been written
    assertThat(recordingCacheWriter.getWriteCount(), is(0L));
    assertThat(recordingCacheWriter.getDeleteCount(), is(0L));
    assertThat(recordingCacheWriter.hasWritten(key), is(false));
  }

  /**
   * Ensure accessing an entry from an {@link javax.cache.processor.EntryProcessor}
   * will cause a {@link javax.cache.integration.CacheLoader} to load an entry but
   * not cause it to be written by a {@link javax.cache.integration.CacheWriter}.
   */
  @Test
  public void shouldLoadWhenAccessingWithEntryProcessor() {
    String key = "message";

    assertThat(cache.containsKey(key), is(false));

    String value = cache.invoke(key, new GetEntryProcessor<String, String>());

    assertThat(value, is(equalTo(key)));
    assertThat(recordingCacheLoader.getLoadCount(), is(1));
    assertThat(recordingCacheLoader.hasLoaded(key), is(true));

    //ensure nothing has been written
    assertThat(recordingCacheWriter.getWriteCount(), is(0L));
    assertThat(recordingCacheWriter.getDeleteCount(), is(0L));
    assertThat(recordingCacheWriter.hasWritten(key), is(false));
  }

  /**
   * Ensure that a {@link Cache#getAll(java.util.Set)} will load the expected
   * entries but not cause them to be written using a {@link javax.cache.integration.CacheWriter}.
   */
  @Test
  public void shouldLoadUsingGetAll() {
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
    assertThat(recordingCacheLoader.getLoadCount(), is(keys.size()));

    for (String key : keys) {
      assertThat(recordingCacheLoader.hasLoaded(key), is(true));
    }

    //attempting to load the same keys should not result in another load
    cache.getAll(keys);
    assertThat(recordingCacheLoader.getLoadCount(), is(keys.size()));

    //ensure nothing has been written
    assertThat(recordingCacheWriter.getWriteCount(), is(0L));
    assertThat(recordingCacheWriter.getDeleteCount(), is(0L));
  }

  /**
   * Ensure that a {@link javax.cache.integration.CacheLoader} that returns <code>null</code> entries
   * aren't placed in the cache and nothing is written.
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

    //ensure nothing has been written
    assertThat(recordingCacheWriter.getWriteCount(), is(0L));
    assertThat(recordingCacheWriter.getDeleteCount(), is(0L));
  }

  /**
   * Ensure that a {@link javax.cache.integration.CacheLoader} that returns <code>null</code> values
   * aren't placed in the cache or written
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

    //ensure nothing has been written
    assertThat(recordingCacheWriter.getWriteCount(), is(0L));
    assertThat(recordingCacheWriter.getDeleteCount(), is(0L));
  }

  /**
   * Ensure that {@link Cache#loadAll(java.util.Set, boolean, javax.cache.integration.CompletionListener)}
   * for a non-existent single value will cause it to be loaded and not written.
   */
  @Test
  public void shouldLoadSingleMissingEntryUsingLoadAll() throws Exception {
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
    assertThat(recordingCacheLoader.getLoadCount(), is(1));
    assertThat(recordingCacheLoader.hasLoaded(key), is(true));

    //ensure nothing has been written
    assertThat(recordingCacheWriter.getWriteCount(), is(0L));
    assertThat(recordingCacheWriter.getDeleteCount(), is(0L));
  }

  @Test
  public void shouldNotWriteThroughUsingLoadAll() throws Exception {
    final int NUMBER_OF_KEYS = 10;
    assertEquals(0, recordingCacheWriter.getWriteCount());
    assertEquals(0, recordingCacheWriter.getDeleteCount());
    assertEquals(0, recordingCacheLoader.getLoadCount());


    Set<String> keys = new HashSet<>();
    for (int key = 1; key <= NUMBER_OF_KEYS; key++) {
      keys.add(Integer.toString(key));
    }

    assertEquals(0, recordingCacheWriter.getWriteCount());
    assertEquals(0, recordingCacheWriter.getDeleteCount());
    CompletionListenerFuture future = new CompletionListenerFuture();

    cache.loadAll(keys, true, future);

    // wait for the load to complete
    future.get();

    assertThat(recordingCacheLoader.getLoadCount(), is(keys.size()));
    for (String key : keys) {
      assertThat(recordingCacheLoader.hasLoaded(key), is(true));
      assertThat(cache.containsKey(key), is(true));
    }

    assertEquals(0, recordingCacheWriter.getWriteCount());
    assertEquals(0, recordingCacheWriter.getDeleteCount());
  }

  /**
   * Ensure that {@link Cache#loadAll(java.util.Set, boolean, javax.cache.integration.CompletionListener)}
   * for an existing single entry will cause it to be reloaded or written.
   */
  @Test
  public void shouldLoadSingleExistingEntryUsingLoadAll() throws Exception {
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
    assertThat(recordingCacheLoader.getLoadCount(), is(1));
    assertThat(recordingCacheLoader.hasLoaded(key), is(true));

    //ensure nothing has been written
    assertThat(recordingCacheWriter.getWriteCount(), is(1L));
    assertThat(recordingCacheWriter.getDeleteCount(), is(0L));
  }

  /**
   * Ensure that {@link Cache#loadAll(java.util.Set, boolean, javax.cache.integration.CompletionListener)} )}
   * for multiple non-existing entries will be loaded or written.
   */
  @Test
  public void shouldLoadMultipleNonExistingEntryUsingLoadAll() throws Exception {
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
    assertThat(recordingCacheLoader.getLoadCount(), is(keys.size()));

    for (String key : keys) {
      assertThat(cache.get(key), is(equalTo(key)));
      assertThat(recordingCacheLoader.hasLoaded(key), is(true));
    }

    //ensure nothing has been written
    assertThat(recordingCacheWriter.getWriteCount(), is(0L));
    assertThat(recordingCacheWriter.getDeleteCount(), is(0L));
  }

  /**
   * Ensure that {@link Cache#loadAll(java.util.Set, boolean, javax.cache.integration.CompletionListener)}
   * for multiple existing entries will be reloaded but not written.
   */
  @Test
  public void shouldLoadMultipleExistingEntryUsingLoadAll() throws Exception {
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
    assertThat(recordingCacheLoader.getLoadCount(), is(keys.size()));

    for (String key : keys) {
      assertThat(cache.get(key), is(equalTo(key)));
      assertThat(recordingCacheLoader.hasLoaded(key), is(true));
    }

    //ensure nothing has been written
    assertThat(recordingCacheWriter.getWriteCount(), is(4L));
    assertThat(recordingCacheWriter.getDeleteCount(), is(0L));
  }
}
