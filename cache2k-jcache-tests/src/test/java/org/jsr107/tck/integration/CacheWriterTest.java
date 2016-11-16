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

import org.jsr107.tck.processor.AssertNotPresentEntryProcessor;
import org.jsr107.tck.processor.CombineEntryProcessor;
import org.jsr107.tck.processor.RemoveEntryProcessor;
import org.jsr107.tck.processor.SetEntryProcessor;
import org.jsr107.tck.processor.SetEntryWithComputedValueProcessor;
import org.jsr107.tck.testutil.ExcludeListExcluder;
import org.jsr107.tck.testutil.TestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.integration.CacheWriterException;
import javax.cache.integration.CompletionListenerFuture;
import javax.cache.processor.EntryProcessor;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Functional test for {@link javax.cache.integration.CacheWriter}s.
 * <p>
 * Cache methods are tested in order listed in Write-Through Caching table in JCache specification.
 * </p>
 * @author Joe Fialli
 */
public class CacheWriterTest extends TestSupport {

  /**
   * Rule used to exclude tests
   */
  @Rule
  public ExcludeListExcluder rule = new ExcludeListExcluder(this.getClass());

  /**
   * The test Cache that will be configured to use the CacheWriter.
   */
  private Cache<Integer, String> cache;

  /**
   * The {@link javax.cache.CacheManager} for the each test.
   */
  private CacheManager cacheManager;
  private RecordingCacheWriter<Integer, String> cacheWriter;

  /**
   * A {@link CacheWriterServer} that will delegate {@link Cache} request
   * onto the recording {@link javax.cache.integration.CacheWriter}.
   */
  private CacheWriterServer<Integer, String> cacheWriterServer;

  /**
   * Configure write-through before each test.
   */
  @Before
  public void onBeforeEachTest() throws IOException {
    // establish and open a CacheWriterServer to handle cache
    // cache loading requests from a CacheWriterClient
    cacheWriter = new RecordingCacheWriter<>();
    cacheWriterServer = new CacheWriterServer<>(10000, cacheWriter);
    cacheWriterServer.open();

    // establish the CacheManager for the tests
    cacheManager = Caching.getCachingProvider().getCacheManager();

    // establish a CacheWriterClient that a Cache can use for writing/deleting entries
    // (via the CacheWriterServer)
    CacheWriterClient<Integer, String> theCacheWriter = new CacheWriterClient<>(cacheWriterServer.getInetAddress(),
        cacheWriterServer.getPort());

    MutableConfiguration<Integer, String> configuration = new MutableConfiguration<>();
    configuration.setTypes(Integer.class, String.class);
    configuration.setCacheWriterFactory(FactoryBuilder.factoryOf(theCacheWriter));
    configuration.setWriteThrough(true);

    getCacheManager().createCache("cache-writer-test", configuration);
    cache = getCacheManager().getCache("cache-writer-test", Integer.class, String.class);
  }

  @After
  public void cleanup() {

    // destroy the cache
    String cacheName = cache.getName();
    cacheManager.destroyCache(cacheName);

    // close the CacheWriterServer
    cacheWriterServer.close();
    cacheWriterServer = null;

    cache = null;
  }

  @Test
  public void shouldNotWriteThroughCallingContainsKeyOnExistingKey() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    // containsKey returns true case.
    cache.put(1, "Gudday World");
    assertEquals(1, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    cache.containsKey(1);
    assertEquals(1, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
  }

  @Test
  public void shouldNotInvokeWriteThroughCallingContainsKeyOnMissingKey() {

    // containsKey returns false case.
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
    cache.containsKey(1);
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
  }

  @Test
  public void shouldNotInvokeWriteThroughCallingGetOnMissingEntry() {

    // get returns null case.
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
    cache.get(1);
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
  }

  @Test
  public void shouldNotInvokeWriteThroughCallingGetOnExistingEntry() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    // get returns non-null case.
    cache.put(1, "Gudday World");
    assertEquals(1, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    String value = cache.get(1);
    assertEquals("Gudday World", value);
    assertEquals(1, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
  }

  @Test
  public void shouldNotInvokeWriteThroughCallingGetAll() {
    int NUM_KEYS = 4;
    Set<Integer> keys = new HashSet<>();
    for (int i = 1; i <= NUM_KEYS; i++) {
      keys.add(i);
    }

    // getAll returns null case.
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
    Map<Integer, String> map = cache.getAll(keys);
    assertTrue(map.size() == 0);
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    // getAll returns non-null case.
    for (Integer key : keys) {
      cache.put(key, "value" + key);
    }

    assertEquals(NUM_KEYS, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    map = cache.getAll(keys);
    assertEquals(keys.size(), map.size());
    assertEquals(NUM_KEYS, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
  }

  @Test
  public void shouldWriteThroughUsingGetAndPut_SingleEntryMultipleTimes() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
    cache.getAndPut(1, "Gudday World");
    cache.getAndPut(1, "Gudday World");
    cache.getAndPut(1, "Gudday World");

    assertEquals(3, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
    assertTrue(cacheWriter.hasWritten(1));
    assertEquals("Gudday World", cacheWriter.get(1));
  }

  @Test
  public void shouldWriteThroughUsingGetAndPut_DifferentEntries() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
    cache.getAndPut(1, "Gudday World");
    cache.getAndPut(2, "Bonjour World");
    cache.getAndPut(3, "Hello World");

    assertEquals(3, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    assertTrue(cacheWriter.hasWritten(1));
    assertEquals("Gudday World", cacheWriter.get(1));

    assertTrue(cacheWriter.hasWritten(2));
    assertEquals("Bonjour World", cacheWriter.get(2));

    assertTrue(cacheWriter.hasWritten(3));
    assertEquals("Hello World", cacheWriter.get(3));
  }

  @Test
  public void shouldWriteThroughUsingGetAndRemove_MissingSingleEntry() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    // remove a missing entry case
    String value = cache.getAndRemove(1);
    assertEquals(value, null);
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(1, cacheWriter.getDeleteCount());
  }

  @Test
  public void shouldWriteThroughUsingGetAndRemove_ExistingSingleEntry() {
    int nDelete = 0;
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    // actual remove of an entry case
    cache.put(1, "Gudday World");
    String value = cache.getAndRemove(1);
    assertEquals("Gudday World", value);
    nDelete++;

    assertEquals(1, cacheWriter.getWriteCount());
    assertEquals(nDelete, cacheWriter.getDeleteCount());
    assertFalse(cacheWriter.hasWritten(1));
  }

  @Test
  public void shouldNotWriteThroughUsingGetAndReplace_MissingSingleEntry() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    // replace does not occur since key 1 does not exist in cache
    String value = cache.getAndReplace(1, "Gudday World");
    assertEquals(value, null);
    assertEquals(cache.containsKey(1), false);
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
  }

  @Test
  public void shouldWriteThroughUsingGetAndReplace_ExistingSingleEntry() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    // actual replace of an entry case
    cache.put(1, "Gudday World");
    String value = cache.getAndReplace(1, "Hello World");
    assertEquals(value, "Gudday World");
    assertEquals(2, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
    assertEquals(cache.get(1), cacheWriter.get(1));
    assertTrue(cacheWriter.hasWritten(1));
  }

  @Test
  public void shouldWriteThroughUsingGetAndReplace_SingleEntryMultipleTimes() {
    int nWrite = 0;
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    String previousValue = cache.getAndReplace(1, "Gudday World");
    assertEquals(previousValue, null);
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    boolean result = cache.putIfAbsent(1, "Gudday World");
    assertTrue(result);
    nWrite++;
    assertEquals(nWrite, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    previousValue = cache.getAndReplace(1, "Bonjour World");
    assertEquals(previousValue, "Gudday World");
    nWrite++;
    assertEquals(cache.get(1), cacheWriter.get(1));
    assertEquals("Bonjour World", cacheWriter.get(1));
    assertEquals("Bonjour World", cache.get(1));
    assertEquals(nWrite, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    previousValue = cache.getAndReplace(1, "Hello World");
    assertEquals("Bonjour World", previousValue);
    nWrite++;
    assertEquals(cache.get(1), cacheWriter.get(1));
    assertEquals(previousValue, "Bonjour World");
    assertEquals(nWrite, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
    assertTrue(cacheWriter.hasWritten(1));
    assertEquals("Hello World", cacheWriter.get(1));
  }

  @Test
  public void shouldWriteThroughUsingInvoke_setValue_CreateEntry() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

        cache.invoke(1, new SetEntryProcessor<Integer, String>("Gudday World"));
    assertEquals(1, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
    assertTrue(cacheWriter.hasWritten(1));
    assertEquals("Gudday World", cacheWriter.get(1));
  }

  @Test
  public void shouldWriteThroughUsingInvokeAll_setValue_CreateEntry() {
    final String VALUE_PREFIX = "value_";
    final int NUM_KEYS = 10;

    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    Set<Integer> keys = new HashSet<>();
    for (int key = 1; key <= NUM_KEYS; key++) {
      keys.add(key);
    }

    cache.invokeAll(keys, new SetEntryWithComputedValueProcessor<Integer>(VALUE_PREFIX, ""));

    assertEquals(NUM_KEYS, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
    for (Integer key : keys) {
      String computedValue = VALUE_PREFIX + key;
      assertTrue(cacheWriter.hasWritten(key));
      assertEquals(computedValue, cacheWriter.get(key));
      assertEquals(computedValue, cache.get(key));
    }
  }

  @Test
  public void shouldWriteThroughUsingInvoke_setValue_UpdateEntry() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    cache.put(1, "Gudday World");
    cache.invoke(1, new SetEntryProcessor("Hello World"));
    assertEquals(2, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
    assertTrue(cacheWriter.hasWritten(1));
    assertEquals("Hello World", cacheWriter.get(1));
  }

  @Test
  public void shouldWriteThroughUsingInvokeAll_setValue_UpdateEntry() {
    final String VALUE_PREFIX_ORIGINAL = "value_";
    final String VALUE_PREFIX_UPDATED = "updateValue_";
    final int NUMBER_OF_KEYS = 10;
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    Set<Integer> keys = new HashSet<>();
    for (int key = 1; key <= NUMBER_OF_KEYS; key++) {
      keys.add(key);
      cache.put(key, VALUE_PREFIX_ORIGINAL + key);
    }

    assertEquals(NUMBER_OF_KEYS, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
        cache.invokeAll(keys, new SetEntryWithComputedValueProcessor<Integer>(VALUE_PREFIX_UPDATED, ""));
    assertEquals(NUMBER_OF_KEYS * 2, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
    for (Integer key : keys) {
      String computedValue = VALUE_PREFIX_UPDATED + key;
      assertTrue(cacheWriter.hasWritten(key));
      assertEquals(computedValue, cacheWriter.get(key));
      assertEquals(computedValue, cache.get(key));
    }
  }

  @Test
  public void shouldWriteThroughUsingInvoke_remove() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    cache.put(1, "Gudday World");
        cache.invoke(1, new RemoveEntryProcessor<Integer, String, Object>(true));

    assertEquals(1, cacheWriter.getWriteCount());
    assertEquals(1, cacheWriter.getDeleteCount());
    assertFalse(cacheWriter.hasWritten(1));
  }

  @Test
  public void shouldWriteThroughUsingInvokeAll_setValue_RemoveEntry() {
    final String VALUE_PREFIX = "value_";
    final int NUM_KEYS = 10;

    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    Set<Integer> keys = new HashSet<>();
    for (int key = 1; key <= NUM_KEYS; key++) {
      keys.add(key);
      cache.put(key, VALUE_PREFIX + key);
    }

    assertEquals(NUM_KEYS, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    cache.invokeAll(keys, new RemoveEntryProcessor<Integer, String, Object>(true));
    assertEquals(NUM_KEYS, cacheWriter.getWriteCount());
    assertEquals(NUM_KEYS, cacheWriter.getDeleteCount());

    for (Integer key : keys) {
      assertFalse(cacheWriter.hasWritten(key));
      assertEquals(null, cacheWriter.get(key));
      assertEquals(null, cache.get(key));
    }
  }

  @Test
  public void shouldWriteThroughUsingInvoke_remove_nonExistingEntry() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    cache.invoke(1, new RemoveEntryProcessor<Integer, String, Object>());
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(1, cacheWriter.getDeleteCount());
    assertFalse(cacheWriter.hasWritten(1));
  }

  @Test
  public void shouldWriteThroughUsingInvoke_remove_createEntry() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    cache.put(1, "Gudday World");
        EntryProcessor processors[] =
            new EntryProcessor[] {
                new RemoveEntryProcessor<Integer, String, Object>(true),
                new AssertNotPresentEntryProcessor(null),
                new SetEntryProcessor<Integer, String>("After remove")
        };
    cache.invoke(1, new CombineEntryProcessor<Integer, String>(processors));

    assertEquals(2, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
    assertTrue(cacheWriter.hasWritten(1));
  }

  @Test
  public void shouldWriteThroughUsingInvoke_setValue_CreateEntryThenRemove() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    EntryProcessor processors[] =
            new EntryProcessor[] {
                new AssertNotPresentEntryProcessor(null),
                new SetEntryProcessor<Integer, String>("Gudday World"),
                new RemoveEntryProcessor<Integer, String, Object>(true)
            };
    cache.invoke(1, new CombineEntryProcessor<Integer, String>(processors));
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
    assertTrue(!cacheWriter.hasWritten(1));
    assertTrue(cache.get(1) == null);
    assertFalse(cache.containsKey(1));
  }

  @Test
  public void shouldWriteThroughUsingInvoke_setValue_CreateEntryGetValue() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
    EntryProcessor processors[] =
        new EntryProcessor[] {
            new AssertNotPresentEntryProcessor(null),
            new SetEntryProcessor<Integer, String>("Gudday World")
        };
    Object[] result = cache.invoke(1, new CombineEntryProcessor<Integer, String>(processors));

    assertEquals(result[1], "Gudday World");
    assertEquals(result[1], cache.get(1));
    assertEquals(cache.get(1), cacheWriter.get(1));
    assertEquals(1, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
    assertTrue(cacheWriter.hasWritten(1));
    assertEquals("Gudday World", cacheWriter.get(1));
  }

  @Test
  public void shouldNotWriteThroughUsingIterator() {
    final String VALUE_PREFIX = "value_";
    final int NUMBER_OF_KEYS = 10;
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    Set<Integer> keys = new HashSet<>();
    for (int aKey = 1; aKey <= NUMBER_OF_KEYS; aKey++) {
      keys.add(aKey);
      cache.put(aKey, VALUE_PREFIX + aKey);
    }

    assertEquals(NUMBER_OF_KEYS, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    int i = 0;
    for (Cache.Entry<Integer, String> entry : cache) {
      i++;
      assertEquals(entry.getValue(), cacheWriter.get(entry.getKey()));
    }
    assertEquals(NUMBER_OF_KEYS, i);

    assertEquals(NUMBER_OF_KEYS, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
  }

  /**
   * Test constraint that cache is not mutated when CacheWriterException is thrown by
   * {@link javax.cache.integration.CacheWriter#write(javax.cache.Cache.Entry)}
   */
  @Test
  public void shouldNotPutWhenWriteThroughFails() {
    cacheWriterServer.setCacheWriter(new FailingCacheWriter<Integer, String>());

    try {
      cache.put(1, "Gudday World");
      assertTrue("expected exception on write-through", false);
    } catch (CacheWriterException e) {

      // ignore expected exception.
    }

    assertFalse(cache.containsKey(1));
  }

  @Test
  public void shouldWriteThoughUsingPutSingleEntry() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    cache.put(1, "Gudday World");

    assertEquals(1, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
    assertTrue(cacheWriter.hasWritten(1));
    assertEquals("Gudday World", cacheWriter.get(1));
  }

  @Test
  public void shouldWriteThroughUsingPutSingleEntryMultipleTimes() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    cache.put(1, "Gudday World");
    cache.put(1, "Bonjour World");
    cache.put(1, "Hello World");

    assertEquals(3, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
    assertTrue(cacheWriter.hasWritten(1));
    assertEquals("Hello World", cacheWriter.get(1));
  }

  @Test
  public void shouldWriteThroughUsingPutOfDifferentEntries() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    cache.put(1, "Gudday World");
    cache.put(2, "Bonjour World");
    cache.put(3, "Hello World");

    assertEquals(3, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    assertTrue(cacheWriter.hasWritten(1));
    assertEquals("Gudday World", cacheWriter.get(1));

    assertTrue(cacheWriter.hasWritten(2));
    assertEquals("Bonjour World", cacheWriter.get(2));

    assertTrue(cacheWriter.hasWritten(3));
    assertEquals("Hello World", cacheWriter.get(3));
  }

  @Test
  public void shouldWriteThoughUsingPutAll() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    HashMap<Integer, String> map = new HashMap<>();
    map.put(1, "Gudday World");
    map.put(2, "Bonjour World");
    map.put(3, "Hello World");

    cache.putAll(map);

    assertEquals(3, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    for (Integer key : map.keySet()) {
      assertTrue(cacheWriter.hasWritten(key));
      assertTrue(cache.containsKey(key));
      assertEquals(cache.get(key), cacheWriter.get(key));
      assertEquals(map.get(key), cache.get(key));
    }

    map.put(4, "Hola World");

    cache.putAll(map);

    assertEquals(7, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    for (Integer key : map.keySet()) {
      assertTrue(cacheWriter.hasWritten(key));
      assertEquals(map.get(key), cacheWriter.get(key));
      assertTrue(cache.containsKey(key));
      assertEquals(map.get(key), cache.get(key));
    }
  }

  @Test
  public void shouldWriteThoughUsingPutAll_partialSuccess() {
    cacheWriter = new BatchPartialSuccessRecordingClassWriter<>(3, 100);
    cacheWriterServer.setCacheWriter(cacheWriter);
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    HashMap<Integer, String> map = new HashMap<>();
    map.put(1, "Gudday World");
    map.put(2, "Bonjour World");
    map.put(3, "Hello World");
    map.put(4, "Ciao World");

    try {
      cache.putAll(map);
      assertTrue("expected CacheWriterException to be thrown for BatchPartialSuccessRecordingClassWriter", false);
    } catch (CacheWriterException cwe) {

      // ignore expected exception
    }

    int numSuccess = 0;
    int numFailure = 0;
    for (Integer key : map.keySet()) {
      if (cacheWriter.hasWritten(key)) {
        assertTrue(cache.containsKey(key));
        assertEquals(map.get(key), cacheWriter.get(key));
        assertEquals(map.get(key), cache.get(key));
        numSuccess++;
      } else {
        assertFalse(cache.containsKey(key));
        assertFalse(cacheWriter.hasWritten(key));
        assertEquals(cache.get(key), cacheWriter.get(key));
        numFailure++;
      }

      assertEquals(cache.get(key), cacheWriter.get(key));
    }

    assertEquals(numSuccess + numFailure, map.size());
    assertEquals(numSuccess, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
  }

  @Test
  public void shouldWriteThoughUsingPutIfAbsent_SingleEntryMultipleTimes() {
    int nWrite = 0;
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    boolean result = cache.putIfAbsent(1, "Gudday World");
    assertTrue(result);
    nWrite++;

    result = cache.putIfAbsent(1, "Bonjour World");
    assertFalse(result);

    result = cache.putIfAbsent(1, "Hello World");
    assertFalse(result);

    assertEquals(nWrite, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
    assertTrue(cacheWriter.hasWritten(1));
    assertEquals("Gudday World", cacheWriter.get(1));
  }

  @Test
  public void shouldWriteThroughRemoveNonexistentKey() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    boolean result = cache.remove(1);
    assertFalse(result);
    assertEquals(1, cacheWriter.getDeleteCount());
  }

  @Test
  public void shouldWriteThroughRemove_SingleEntry() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    cache.put(1, "Gudday World");
    assertEquals(1, cacheWriter.getWriteCount());

    boolean result = cache.remove(1);
    assertTrue(result);

    assertEquals(1, cacheWriter.getDeleteCount());
    assertFalse(cacheWriter.hasWritten(1));
  }

  /**
   * Test constraint that cache is not mutated when CacheWriterException is thrown by
   * {@link javax.cache.integration.CacheWriter#delete(Object)}
   */
  @Test
  public void shouldNotRemoveWhenWriteThroughFails() {
    cache.put(1, "Gudday World");
    assertTrue(cache.containsKey(1));

    cacheWriterServer.setCacheWriter(new FailingCacheWriter<Integer, String>());

    try {
      cache.remove(1);
      assertTrue("expected exception on write-through", false);
    } catch (CacheWriterException e) {

      // ignore expected exception.

    }
    assertTrue(cache.containsKey(1));
  }

  @Test
  public void shouldWriteThroughRemove_SingleEntryMultipleTimes() {
    int nDelete = 0;
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    cache.put(1, "Gudday World");
    boolean result = cache.remove(1);
    assertTrue(result);
    nDelete++;

    result = cache.remove(1);
    assertFalse(result);
    nDelete++;

    result = cache.remove(1);
    assertFalse(result);
    nDelete++;

    assertEquals(1, cacheWriter.getWriteCount());
    assertEquals(nDelete, cacheWriter.getDeleteCount());
  }

  @Test
  public void shouldWriteThroughRemove_SpecificEntry() {
    int nDelete = 0;

    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(nDelete, cacheWriter.getDeleteCount());

    cache.put(1, "Gudday World");
    boolean result = cache.remove(1, "Hello World");
    assertFalse(result);
    assertEquals(nDelete, cacheWriter.getDeleteCount());

    result = cache.remove(1, "Gudday World");
    assertTrue(result);
    nDelete++;
    assertEquals(nDelete, cacheWriter.getDeleteCount());

    result = cache.remove(1, "Gudday World");
    assertFalse(result);
    assertEquals(nDelete, cacheWriter.getDeleteCount());

    assertEquals(1, cacheWriter.getWriteCount());
  }

  @Test
  public void shouldWriteThroughCacheIteratorRemove() {
    int nDelete = 0;
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    cache.getAndPut(1, "Gudday World");
    cache.getAndPut(2, "Bonjour World");
    cache.getAndPut(3, "Hello World");

    Iterator<Cache.Entry<Integer, String>> iterator = cache.iterator();
    iterator.next();
    iterator.remove();
    nDelete++;

    iterator.next();
    iterator.next();
    iterator.remove();
    nDelete++;

    assertEquals(3, cacheWriter.getWriteCount());
    assertEquals(nDelete, cacheWriter.getDeleteCount());
  }

  @Test
  public void shouldWriteThroughRemoveAll() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    HashMap<Integer, String> map = new HashMap<>();
    map.put(1, "Gudday World");
    map.put(2, "Bonjour World");
    map.put(3, "Hello World");

    cache.putAll(map);

    assertEquals(3, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    for (Integer key : map.keySet()) {
      assertTrue(cacheWriter.hasWritten(key));
      assertEquals(map.get(key), cacheWriter.get(key));
      assertTrue(cache.containsKey(key));
      assertEquals(map.get(key), cache.get(key));
    }

    cache.removeAll();

    assertEquals(3, cacheWriter.getWriteCount());
    assertEquals(3, cacheWriter.getDeleteCount());

    for (Integer key : map.keySet()) {
      assertFalse(cacheWriter.hasWritten(key));
      assertFalse(cache.containsKey(key));
    }

    //the following should not change the state of the cache
    cache.removeAll();

    assertEquals(3, cacheWriter.getWriteCount());
    assertEquals(3, cacheWriter.getDeleteCount());

    for (Integer key : map.keySet()) {
      assertFalse(cacheWriter.hasWritten(key));
      assertFalse(cache.containsKey(key));
    }

    map.put(4, "Hola World");

    cache.putAll(map);

    assertEquals(7, cacheWriter.getWriteCount());
    assertEquals(3, cacheWriter.getDeleteCount());

    for (Integer key : map.keySet()) {
      assertTrue(cacheWriter.hasWritten(key));
      assertEquals(map.get(key), cacheWriter.get(key));
      assertTrue(cache.containsKey(key));
      assertEquals(map.get(key), cache.get(key));
    }
  }

  @Test
  public void shouldWriteThroughRemoveAll_partialSuccess() {
    cacheWriter = new BatchPartialSuccessRecordingClassWriter<>(100, 3);
    cacheWriterServer.setCacheWriter(cacheWriter);

    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    HashMap<Integer, String> map = new HashMap<>();
    map.put(1, "Gudday World");
    map.put(2, "Bonjour World");
    map.put(3, "Hello World");

    cache.putAll(map);

    assertEquals(3, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    for (Integer key : map.keySet()) {
      assertTrue(cacheWriter.hasWritten(key));
      assertEquals(map.get(key), cacheWriter.get(key));
      assertTrue(cache.containsKey(key));
      assertEquals(map.get(key), cache.get(key));
    }

    try {
      cache.removeAll();
      assertTrue("expected CacheWriterException to be thrown for BatchPartialSuccessRecordingClassWriter", false);
    } catch (CacheWriterException cwe) {

      // ignore expected exception

    }

    int numSuccess = 0;
    int numFailure = 0;
    for (Integer key : map.keySet()) {
      if (cacheWriter.hasWritten(key)) {
        assertTrue(cache.containsKey(key));
        assertEquals(map.get(key), cacheWriter.get(key));
        assertEquals(map.get(key), cache.get(key));
        numFailure++;
      } else {
        assertFalse(cache.containsKey(key));
        numSuccess++;
      }

      assertEquals(cache.get(key), cacheWriter.get(key));
    }

    assertEquals(numSuccess + numFailure, map.size());
    assertEquals(3, cacheWriter.getWriteCount());
    assertEquals(numSuccess, cacheWriter.getDeleteCount());
  }

  @Test
  public void shouldUseWriteThroughRemoveAllSpecific() {
    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    HashMap<Integer, String> map = new HashMap<>();
    map.put(1, "Gudday World");
    map.put(2, "Bonjour World");
    map.put(3, "Hello World");
    map.put(4, "Hola World");

    cache.putAll(map);

    assertEquals(4, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    for (Integer key : map.keySet()) {
      assertTrue(cacheWriter.hasWritten(key));
      assertEquals(map.get(key), cacheWriter.get(key));
      assertTrue(cache.containsKey(key));
      assertEquals(map.get(key), cache.get(key));
    }

    HashSet<Integer> set = new HashSet<>();
    set.add(1);
    set.add(4);

    cache.removeAll(set);

    assertEquals(4, cacheWriter.getWriteCount());
    assertEquals(2, cacheWriter.getDeleteCount());

    for (Integer key : set) {
      assertFalse(cacheWriter.hasWritten(key));
      assertFalse(cache.containsKey(key));
    }

    cache.put(4, "Howdy World");

    assertEquals(5, cacheWriter.getWriteCount());
    assertEquals(2, cacheWriter.getDeleteCount());

    set.clear();
    set.add(2);

    cache.removeAll(set);
    assertEquals(3, cacheWriter.getDeleteCount());

    assertTrue(cacheWriter.hasWritten(3));
    assertTrue(cache.containsKey(3));
    assertTrue(cacheWriter.hasWritten(4));
    assertTrue(cache.containsKey(4));
  }

  @Test
  public void shouldWriteThroughRemoveAllSpecific_partialSuccess() {
    cacheWriter = new BatchPartialSuccessRecordingClassWriter<>(100, 3);
    cacheWriterServer.setCacheWriter(cacheWriter);

    assertEquals(0, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    HashMap<Integer, String> entriesAdded = new HashMap<>();
    entriesAdded.put(1, "Gudday World");
    entriesAdded.put(2, "Bonjour World");
    entriesAdded.put(3, "Hello World");
    entriesAdded.put(4, "Hola World");
    entriesAdded.put(5, "Ciao World");

    cache.putAll(entriesAdded);

    assertEquals(5, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    for (Integer key : entriesAdded.keySet()) {
      assertTrue(cacheWriter.hasWritten(key));
      assertEquals(entriesAdded.get(key), cacheWriter.get(key));
      assertTrue(cache.containsKey(key));
      assertEquals(entriesAdded.get(key), cache.get(key));
    }

    HashSet<Integer> keysToRemove = new HashSet<>();
    keysToRemove.add(1);
    keysToRemove.add(2);
    keysToRemove.add(3);
    keysToRemove.add(4);

    try {
      cache.removeAll(keysToRemove);
      assertTrue("expected CacheWriterException to be thrown for BatchPartialSuccessRecordingClassWriter", false);
    } catch (CacheWriterException ce) {

      // ignore expected exception

    }

    int numSuccess = 0;
    int numFailure = 0;
    for (Integer key : entriesAdded.keySet()) {
      if (cacheWriter.hasWritten(key)) {
        assertTrue(cache.containsKey(key));
        assertEquals(entriesAdded.get(key), cacheWriter.get(key));
        assertEquals(entriesAdded.get(key), cache.get(key));
        numFailure++;
      } else {
        assertFalse(cache.containsKey(key));
        numSuccess++;
      }
      assertEquals(cache.get(key), cacheWriter.get(key));
    }

    assertEquals(numSuccess + numFailure, entriesAdded.size());
    assertEquals(5, cacheWriter.getWriteCount());
    assertEquals(numSuccess, cacheWriter.getDeleteCount());

  }

  /**
   * Write-through Test for  method
   * boolean replace(K key, V value)
   */
  @Test
  public void shouldNotWriteThroughReplaceNonExistentKey() {
    int nWrites = 0;
    assertEquals(nWrites, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    boolean result = cache.replace(1, "Gudday World");
    assertFalse(result);

    assertEquals(nWrites, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
  }

  /**
   * Write-through Test for  method
   * boolean replace(K key, V value)
   */
  @Test
  public void shouldWriteThroughReplaceExisting_SingleEntryMultipleTimes() {
    int nWrites = 0;
    assertEquals(nWrites, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    boolean result = cache.replace(1, "Gudday World");
    assertFalse(result);

    result = cache.putIfAbsent(1, "Gudday World");
    assertTrue(result);
    nWrites++;

    result = cache.replace(1, "Bonjour World");
    assertTrue(result);
    nWrites++;

    result = cache.replace(1, "Hello World");
    assertTrue(result);
    nWrites++;

    assertEquals(nWrites, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
    assertTrue(cacheWriter.hasWritten(1));
    assertEquals("Hello World", cacheWriter.get(1));
    assertEquals(cache.get(1), cacheWriter.get(1));
  }

  /**
   * Write-through Test for  method
   * boolean replace(K key, V oldValue, V newValue)
   */
  @Test
  public void shouldNotUseWriteThroughReplaceDoesNotMatch() {
    int nWriter = 0;
    assertEquals(nWriter, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());

    boolean result = cache.putIfAbsent(1, "Gudday World");
    assertTrue(result);
    nWriter++;
    assertEquals(1, cacheWriter.getWriteCount());

    result = cache.replace(1, "Bonjour World", "Hello World");
    assertFalse(result);
    assertFalse("Hello World".equals(cache.get(1)));
    assertEquals(nWriter, cacheWriter.getWriteCount());
    assertEquals(0, cacheWriter.getDeleteCount());
    assertTrue(cacheWriter.hasWritten(1));
    assertEquals(cache.get(1), cacheWriter.get(1));
  }

  @Test
  public void shouldWrapWriterExceptions() throws IOException {
    //we need to create a custom writer
    cleanup();

    // establish and open a CacheLoaderServer to handle cache
    // cache loading requests from a CacheLoaderClient
    cacheWriter = new FailingCacheWriter<>();
    cacheWriterServer = new CacheWriterServer<>(10000, cacheWriter);
    cacheWriterServer.open();

    // establish the CacheManager for the tests
    cacheManager = Caching.getCachingProvider().getCacheManager();

    // establish a CacheWriterClient that a Cache can use for writing/deleting entries
    // (via the CacheWriterServer)
    CacheWriterClient<Integer, String> theCacheWriter = new CacheWriterClient<>(cacheWriterServer.getInetAddress(),
        cacheWriterServer.getPort());

    MutableConfiguration<Integer, String> configuration = new MutableConfiguration<>();
    configuration.setTypes(Integer.class, String.class);
    configuration.setCacheWriterFactory(FactoryBuilder.factoryOf(theCacheWriter));
    configuration.setWriteThrough(true);

    getCacheManager().createCache("failing-cache-writer-test", configuration);
    cache = getCacheManager().getCache("failing-cache-writer-test", Integer.class,
        String.class);

    try {
      cache.put(12, "Tonto");
      fail();
    } catch (CacheWriterException e) {
      //expected
    }

    try {
      HashMap<Integer, String> map = new HashMap<>();
      map.put(12, "Tonto");
      cache.putAll(map);
      fail();
    } catch (CacheWriterException e) {
      //expected
    }

    try {
      cache.remove(12);
      fail();
    } catch (CacheWriterException e) {
      //expected
    }

    try {
      HashSet<Integer> set = new HashSet<>();
      set.add(12);
      cache.removeAll(set);
      fail();
    } catch (CacheWriterException e) {
      //expected
    }

    // the following should not throw an exception as there are no entries
    // in the cache
    cache.removeAll();
  }


  static public class Entry<K, V> implements Cache.Entry<K, V> {
    private K key;
    private V value;

    public Entry(K k, V v) {
      this.key = k;
      this.value = v;
    }

    @Override
    public K getKey() {
      return key;
    }

    @Override
    public V getValue() {
      return value;
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
      throw new UnsupportedOperationException("not implemented");
    }
  }
}
