package org.cache2k.test.core;

/*-
 * #%L
 * cache2k core implementation
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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.io.CacheLoader;
import org.cache2k.io.CacheWriter;
import org.cache2k.processor.EntryProcessor;
import org.cache2k.processor.MutableCacheEntry;
import org.cache2k.test.util.TestingBase;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.concurrent.ExecutionException;

/**
 * Tests for the CacheWriter and its exception handling.
 *
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class CacheWriterTest extends TestingBase {

  public static final String EXCEPTION_TEXT =
    "org.cache2k.io.CacheWriterException: " +
    "java.lang.Exception: test exception, value: 777";

  @Test
  public void testPutOne() {
    MyWriter w = new MyWriter();
    Cache<Integer, Integer> c = createIntegerCacheWithWriter(w);
    c.put(1, 1);
    assertEquals(1, (int) w.count.get(1));
    assertEquals(1, (int) w.content.get(1));
  }

  @Test
  public void testPutTwo() {
    MyWriter w = new MyWriter();
    Cache<Integer, Integer> c = createIntegerCacheWithWriter(w);
    c.put(1, 1);
    c.put(1, 2);
    assertEquals(2, (int) w.count.get(1));
    assertEquals(2, (int) w.content.get(1));
  }

  @Test
  public void testTriggerLoadWithGetDoesNotCallWriter() {
    MyWriter w = new MyWriter();
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .writer(w)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(Integer key) {
          return key;
        }
      })
      .build();
    c.get(1);
    assertNull(w.count.get(1));
  }

  @Test
  public void testTriggerLoadWithReloadDoesNotCallWriter() throws ExecutionException, InterruptedException {
    MyWriter w = new MyWriter();
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .writer(w)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(Integer key) {
          return key;
        }
      })
      .build();
    c.reloadAll(asList(1)).get();
    assertNull(w.count.get(1));
  }

  @Test
  public void testTriggerLoadWithInvokeDoesNotCallWriter() {
    MyWriter w = new MyWriter();
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .writer(w)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(Integer key) {
          return key;
        }
      })
      .build();
    c.invoke(1, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(MutableCacheEntry<Integer, Integer> entry) {
        entry.getValue();
        return null;
      }
    });
    assertNull(w.count.get(1));
  }

  @Test
  public void testTriggeredLoadWithInvokeAndSetDoesCallWriter() {
    MyWriter w = new MyWriter();
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .writer(w)
      .loader(new CacheLoader<Integer, Integer>() {
        @Override
        public Integer load(Integer key) {
          return key;
        }
      })
      .build();
    c.invoke(1, new EntryProcessor<Integer, Integer, Object>() {
      @Override
      public Object process(MutableCacheEntry<Integer, Integer> entry) {
        entry.getValue();
        entry.setValue(123);
        return null;
      }
    });
    assertEquals(1, (int) w.count.get(1));
  }

  @Test
  public void testPutAndDeleteOne() {
    MyWriter w = new MyWriter();
    Cache<Integer, Integer> c = createIntegerCacheWithWriter(w);
    c.put(1, 1);
    c.remove(1);
    assertEquals(1, (int) w.count.get(1));
    assertEquals(1, (int) w.deletedCount.get(1));
    assertNull(w.content.get(1));
  }

  @Test(expected = RuntimeException.class)
  public void testException() {
    MyWriter w = new MyWriter();
    Cache<Integer, Integer> c = createIntegerCacheWithWriter(w);
    c.put(1, 1);
    assertEquals(1, (int) w.count.get(1));
    assertEquals(1, (int) w.content.get(1));
    c.put(1, 777);
  }

  @Test
  public void testPutWithWriterException() {
    Cache<Integer, Integer> c = prepCacheForExceptionTest();
    try {
      c.put(1, 777);
      fail("exception expected");
    } catch (Exception ex) {
      assertEquals(EXCEPTION_TEXT, ex.toString());
    }
    checkState(c);
  }

  private void checkState(Cache<Integer, Integer> c) {
    assertTrue(c.containsKey(1));
    assertEquals(1, (int) c.peek(1));
  }

  @Test
  public void testReplace2ArgWithWriterException() {
    Cache<Integer, Integer> c = prepCacheForExceptionTest();
    try {
      c.replace(1, 777);
      fail("exception expected");
    } catch (Exception ex) {
      assertEquals(EXCEPTION_TEXT, ex.toString());
    }
    checkState(c);
  }

  @Test
  public void testReplace3ArgWithWriterException() {
    Cache<Integer, Integer> c = prepCacheForExceptionTest();
    try {
      c.replaceIfEquals(1, 1, 777);
      fail("exception expected");
    } catch (Exception ex) {
      assertEquals(EXCEPTION_TEXT, ex.toString());
    }
    checkState(c);
  }

  @Test
  public void testPeekAndReplaceWithWriterException() {
    Cache<Integer, Integer> c = prepCacheForExceptionTest();
    try {
      c.peekAndReplace(1, 777);
      fail("exception expected");
    } catch (Exception ex) {
      assertEquals(EXCEPTION_TEXT, ex.toString());
    }
    checkState(c);
  }

  @Test
  public void testPutIfAbsentWithWriterException() {
    Cache<Integer, Integer> c = prepCacheForExceptionTest();
    c.putIfAbsent(1, 777);
    try {
      c.putIfAbsent(2, 777);
      fail("exception expected");
    } catch (Exception ignore) { }
    checkState(c);
  }

  @Test
  public void testWithWriterException() {
    Cache<Integer, Integer> c = prepCacheForExceptionTest();
    try {
      c.peekAndReplace(1, 777);
      fail("exception expected");
    } catch (Exception ex) {
      assertEquals(EXCEPTION_TEXT, ex.toString());
    }
    checkState(c);
  }

  @Test
  public void testRemoveWriterException() {
    Cache<Integer, Integer> c = prepCacheForExceptionTest();
    try {
      c.remove(777);
      fail("exception expected");
    } catch (Exception ignore) { }
    checkState(c);
  }

  @Test
  public void testPutAllWriterException() {
    Cache<Integer, Integer> c = prepCacheForExceptionTest();
    try {
      HashMap<Integer, Integer> map = new HashMap<Integer, Integer>();
      map.put(12, 477);
      map.put(13, 7777);
      c.putAll(map);
      fail("exception expected");
    } catch (Exception ex) {
      assertEquals(EXCEPTION_TEXT + '7', ex.toString());
    }
    checkState(c);
  }

  @Test
  public void testDoublePut() {
    Cache<Integer, Integer> c = prepCacheForExceptionTest();
    try {
      c.put(123, 7777);
      fail("exception expected");
    } catch (Exception ex) {
      assertEquals(EXCEPTION_TEXT + '7', ex.toString());
    }
    checkState(c);
    try {
      c.put(123, 7777);
      fail("exception expected");
    } catch (Exception ex) {
      assertEquals(EXCEPTION_TEXT + '7', ex.toString());
    }
    checkState(c);
  }

  @Test
  public void testPutAllRemove() {
    Cache<Integer, Integer> c = prepCacheForExceptionTest();
    try {
      c.put(123, 7777);
      fail("exception expected");
    } catch (Exception ignore) { }
    checkState(c);
    try {
      HashMap<Integer, Integer> map = new HashMap<Integer, Integer>();
      map.put(12, 477);
      map.put(13, 7777);
      c.putAll(map);
      fail("exception expected");
    } catch (Exception ignore) { }
    checkState(c);
    try {
      HashMap<Integer, Integer> map = new HashMap<Integer, Integer>();
      map.put(12, 477);
      map.put(7777, 7777);
      c.putAll(map);
      fail("exception expected");
    } catch (Exception ignore) { }
    checkState(c);
    try {
      c.remove(777);
      fail("exception expected");
    } catch (Exception ignore) { }
    checkState(c);
    c.removeAll();
  }

  private Cache<Integer, Integer> prepCacheForExceptionTest() {
    MyWriter w = new MyWriter();
    Cache<Integer, Integer> c = createIntegerCacheWithWriter(w);
    c.put(1, 1);
    checkState(c);
    assertEquals(1, (int) w.count.get(1));
    assertEquals(1, (int) w.content.get(1));
    return c;
  }

  @SuppressWarnings("unchecked")
  private Cache<Integer, Integer> createIntegerCacheWithWriter(MyWriter w) {
    Cache2kBuilder<Integer, Integer> b = builder(Integer.class, Integer.class);
    b.writer(w);
    b.build();
    return getCache();
  }

  public static class MyWriter implements CacheWriter<Integer, Integer> {

    final HashMap<Integer, Integer> deletedCount = new HashMap<Integer, Integer>();
    final HashMap<Integer, Integer> count = new HashMap<Integer, Integer>();
    final HashMap<Integer, Integer> content = new HashMap<Integer, Integer>();

    @Override
    public synchronized void write(Integer key, Integer v) throws Exception {
      if (v % 1000 == 777) {
        throw new Exception("test exception, value: " + v);
      }
      content.put(key, v);
      Integer counter = count.get(key);
      if (counter == null) {
        counter = 0;
      }
      count.put(key, counter + 1);
    }

    @Override
    public void delete(Integer key) throws Exception {
      Integer counter = deletedCount.get(key);
      if (counter == null) {
        counter = 0;
      }
      deletedCount.put(key, counter + 1);
      content.remove(key);
      if (key % 1000 == 777) {
        throw new Exception("test exception, key: " + key);
      }
    }

  }

}
