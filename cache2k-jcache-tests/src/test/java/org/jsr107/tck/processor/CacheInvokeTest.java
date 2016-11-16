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
package org.jsr107.tck.processor;

import org.jsr107.tck.testutil.CacheTestSupport;
import org.jsr107.tck.testutil.ExcludeListExcluder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.cache.CacheException;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * <p>
 * Unit test for Cache.
 * </p>
 *
 * @author Yannis Cosmadopoulos
 * @since 1.0
 */
public class CacheInvokeTest extends CacheTestSupport<Integer, String> {

  /**
   * Rule used to exclude tests
   */
  @Rule
  public ExcludeListExcluder rule = new ExcludeListExcluder(CacheInvokeTest.class);

  @Before
  public void moreSetUp() {
    cache = getCacheManager().getCache(getTestCacheName(), Integer.class, String.class);
  }


  @Override
  protected MutableConfiguration<Integer, String> newMutableConfiguration() {
    return new MutableConfiguration<Integer, String>().setTypes(Integer.class, String.class);
  }

  @Test
  public void nullKey() {
    try {
      cache.invoke(null, new ThrowExceptionEntryProcessor<Integer, String, Void>(UnsupportedOperationException.class));
      fail("null key");
    } catch (NullPointerException e) {
      //
    }
  }

  @Test
  public void nullProcessor() {
    try {
      cache.invoke(123, null);
      fail("null key");
    } catch (NullPointerException e) {
      //
    }
  }

  @Test
  public void nullGetValue() {
    String result = cache.invoke(123, new GetEntryProcessor<Integer, String>());
    assertNull(result);
  }

  @Test( expected = EntryProcessorException.class)
  public void setValueToNull() {
    cache.invoke(123, new SetEntryProcessor<Integer, String>(null));
  }

  @Test(expected = NullPointerException.class)
  public void invokeAllNullKeys() {
    cache.invokeAll(null, new NoOpEntryProcessor<Integer, String>());
  }

  @Test(expected = EntryProcessorException.class)
  public void invokeAllEntryProcessorException() {
    Set<Integer> keys = new HashSet<Integer>();
    keys.add(123);
    Map<Integer, EntryProcessorResult<Object>> resultMap =
      cache.invokeAll(keys, new ThrowExceptionEntryProcessor<Integer, String, Object>(IllegalStateException.class));
    resultMap.get(123).get();
  }

  /**
   * Added for RI code coverage.
   */
  @Test
  public void invokeAllEntryProcessorReturnsNullResult() {
    Set<Integer> keys = new HashSet<Integer>();
    keys.add(123);
    Map<Integer, EntryProcessorResult<Object>> resultMap =
      cache.invokeAll(keys,
        new SetValueCreateEntryReturnDifferentTypeEntryProcessor<Integer, String, Object>(null, "newValue"));
    assertTrue(resultMap != null && resultMap.size() == 0);
  }

  /**
   * Added for RI code coverage.
   */
  @Test
  public void invokeAllgetResultFromMap() {
    Set<Integer> keys = new HashSet<Integer>();
    keys.add(123);
    Map<Integer, EntryProcessorResult<String>> resultMap =
      cache.invokeAll(keys,
        new SetEntryProcessor<Integer, String>("aValue"));
    assertTrue(resultMap != null && resultMap.size() == 1);
    assertEquals("aValue", resultMap.get(123).get());
  }

  @Test
  public void close() {
    cache.close();
    try {
      cache.invoke(123, new ThrowExceptionEntryProcessor<Integer, String, Void>(UnsupportedOperationException.class));
      fail("null key");
    } catch (IllegalStateException e) {
      //
    }
  }

  @Test
  public void testProcessorExceptionIsWrapped() {
    try {
      cache.invoke(123, new ThrowExceptionEntryProcessor<Integer, String, Void>(UnsupportedOperationException.class));
      fail();
    } catch (EntryProcessorException e) {
      assertTrue(e.getCause() instanceof RuntimeException);
      //expected
    }
  }

  @Test
  public void testProcessorEmptyExceptionIsWrapped() {
    try {
      cache.invoke(123, new ThrowExceptionEntryProcessor<Integer, String, Void>(UnsupportedOperationException.class));
      fail();
    } catch (EntryProcessorException e) {
      assertTrue(e.getCause() instanceof RuntimeException);
      //expected
    }
  }

  @Test
  public void noValueNoMutation() {
    final Integer key = 123;
    final Integer ret = 456;
    assertEquals(ret, cache.invoke(key, new AssertNotPresentEntryProcessor<Integer, String, Integer>(ret)));
    assertFalse(cache.containsKey(key));
  }

  @Test
  public void varArgumentsPassedIn() {
    final Integer key = 123;
    final Integer ret = 456;
    assertEquals(ret, cache.invoke(key, new MultiArgumentHandlingEntryProcessor<Integer, String, Integer>(ret),
        "These", "are", "arguments", 1L));
    assertFalse(cache.containsKey(key));
  }


  @Test
  public void noValueSetValue() {
    final Integer key = 123;
    final Integer ret = 456;
    final String  value = "abc";
    assertEquals(ret, cache.invoke(key, new SetValueCreateEntryReturnDifferentTypeEntryProcessor<Integer, String, Integer>(ret, value)));
    assertEquals(value, cache.get(key));
  }

  @Test
  public void noValueException() {
    final Integer key = 123;
    final String setValue = "abc";

    EntryProcessor processors[] =
        new EntryProcessor[]{
                 new AssertNotPresentEntryProcessor(null),
                 new SetEntryProcessor<Integer, String>(setValue),
                 new ThrowExceptionEntryProcessor<Integer, String, String>(IllegalAccessError.class)
             };
    try {
      cache.invoke(key, new CombineEntryProcessor(processors));
      fail();
    } catch (CacheException e) {
      assertTrue("expected IllegalAccessError; observed " + e.getCause(),
          e.getCause() instanceof IllegalAccessError);
    }
    assertFalse(cache.containsKey(key));
  }

  @Test(expected = NullPointerException.class)
  public void invokeAll_keys_null() {
    cache.invoke(null, null);
  }

  @Test(expected = NullPointerException.class)
  public void invokeAll_nullProcessor() {
    Set<Integer> keys = new HashSet<Integer>();
    keys.add(123);
    cache.invokeAll(keys, null);
  }

  @Test
  public void existingReplace() {
    final Integer key = 123;
    final String oldValue = "abc";
    final String newValue = "def";
    cache.put(key, oldValue);
    assertEquals(oldValue, cache.invoke(key, new ReplaceEntryProcessor<Integer, String, String>(oldValue, newValue)));
    assertEquals(newValue, cache.get(key));
  }

  @Test
  public void existingException() {
    final Integer key = 123;
    final String oldValue = "abc";
    final String newValue = "def";
    cache.put(key, oldValue);

    EntryProcessor processors[] =
        new EntryProcessor[]{
            new ReplaceEntryProcessor<Integer, String, Integer>(oldValue, newValue),
            new ThrowExceptionEntryProcessor<Integer, String, String>(IllegalAccessError.class)
     };
    try {
      cache.invoke(key, new CombineEntryProcessor<Integer, String>(processors));
      fail();
    } catch (CacheException e) {
      assertTrue("expected IllegalAccessError; observed " + e.getCause(),
          e.getCause() instanceof IllegalAccessError);
    }
    assertEquals(oldValue, cache.get(key));
  }

  @Test
  public void removeMissing() {
    final Integer key = 123;
    final String  value = "aba";
    final Integer ret = 456;
    EntryProcessor processors[] =
        new EntryProcessor[]{
            new AssertNotPresentEntryProcessor<Integer, String, Integer>(ret),
            new SetEntryProcessor<Integer, String>(value),
            new RemoveEntryProcessor<Integer, String, String>(true)
        };
    Object[] result = cache.invoke(key, new CombineEntryProcessor<Integer, String>(processors));
    assertEquals(ret, result[0]);
    assertFalse(cache.containsKey(key));
  }

  @Test
  public void removeExisting() {
    final Integer key = 123;
    final String oldValue = "abc";
    cache.put(key, oldValue);
    assertEquals(oldValue, cache.invoke(key, new RemoveEntryProcessor<Integer, String, String>(true)));
    assertFalse(cache.containsKey(key));
  }


  @Test
  public void removeException() {
    final Integer key = 123;
    final String oldValue = "abc";
    cache.put(key, oldValue);
    try {
      cache.invoke(key, new ThrowExceptionEntryProcessor<Integer, String, Void>(IllegalAccessError.class));
      fail();
    } catch (CacheException e) {
      assertTrue("expected IllegalAccessError; observed " + e.getCause(),
          e.getCause() instanceof IllegalAccessError);
    }
    assertEquals(oldValue, cache.get(key));
  }
}
