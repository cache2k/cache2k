package org.cache2k.core;

/*
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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Jens Wilke
 */
public class ConcurrentMapTest {

  ConcurrentMap<Integer, String> map;

  Map<?, ?> untypedMap() {
    return (Map<?, ?>) map;
  }

  @Before
  public void setUp() {
    map = new ConcurrentHashMap<Integer, String>();
  }

  @After
  public void tearDown() {
    map = null;
  }

  @Test
  public void testIsEmpty_Initial() {
    assertTrue(map.isEmpty());
  }

  @Test
  public void testSize_Initial() {
    assertEquals(0, map.size());
  }

  @Test
  public void testContainsKey_Initial() {
    assertFalse(map.containsKey(123));
  }

  @Test
  public void testContainsKey_Present() {
    map.put(123, "abc");
    assertTrue(map.containsKey(123));
  }

  @Test
  public void testContainsKey_incompatible() {
    map.put(123, "abc");
    assertFalse(((Map<?, ?>) map).containsKey("123"));
  }

  @Test
  public void testContainsValue_Initial() {
    assertFalse(map.containsValue("abc"));
  }

  @Test
  public void testContainsValue_Present() {
    map.put(123, "abc");
    assertTrue(map.containsValue("abc"));
  }

  @Test(expected = NullPointerException.class)
  public void testContainsValue_null() {
    boolean f = map.containsValue(null);
  }

  @Test
  public void testPutIfAbsent_Initial() {
    String v = map.putIfAbsent(123, "abc");
    assertTrue(map.containsKey(123));
    assertNull(v);
  }

  @Test
  public void testPutIfAbsent_Present() {
    map.put(123, "abc");
    String v = map.putIfAbsent(123, "xyz");
    assertTrue(map.containsKey(123));
    assertEquals("abc", v);
  }

  @Test
  public void testRemove_Initial() {
    String v = map.remove(123);
    assertNull(v);
  }

  @Test
  public void testRemove_Present() {
    map.put(123, "abc");
    String v = map.remove(123);
    assertEquals("abc", v);
  }

  @Test
  public void testRemove_Incompatible() {
    ((Map<?, ?>) map).remove("abc");
  }

  @Test
  public void testRemove2Arg_Initial() {
    boolean v = map.remove(123, "abc");
    assertFalse(v);
  }

  @Test
  public void testRemove2Arg_Present_NotEqual() {
    map.put(123, "abc");
    boolean v = map.remove(123, "bla");
    assertFalse(v);
  }

  @Test
  public void testRemove2Arg_Present_Equal() {
    map.put(123, "abc");
    boolean v = map.remove(123, "abc");
    assertTrue(v);
  }

  @Test
  public void testRemove2Arg_incompatible_types() {
    map.put(123, "abc");
    boolean v = map.remove(123, 123);
    assertFalse(v);
  }

  @Test
  public void testClear() {
    map.put(123, "abc");
    map.clear();
    assertEquals(0, map.size());
  }

  @Test
  public void testReplace3Arg_Initial() {
    boolean v = map.replace(123, "abc", "bla");
    assertFalse(v);
  }

  @Test
  public void testReplace3Arg_Present_NotEqual() {
    map.put(123, "abc");
    boolean v = map.replace(123, "bla", "bla2");
    assertFalse(v);
    assertEquals("abc", map.get(123));
  }

  @Test
  public void testReplace3Arg_Present_Equal() {
    map.put(123, "abc");
    boolean v = map.replace(123, "abc", "bla2");
    assertTrue(v);
    assertEquals("bla2", map.get(123));
  }

  @Test
  public void testReplace2Arg_Initial() {
    String v = map.replace(123, "abc");
    assertNull(v);
  }

  @Test
  public void testReplace2Arg_Present() {
    map.put(123, "abc");
    String v = map.replace(123, "bla");
    assertEquals("abc", v);
    assertEquals("bla", map.get(123));
  }

  @Test
  public void testGet_Initial() {
    assertNull(map.get(123));
  }

  @Test
  public void testGet_Present() {
    map.put(123, "abc");
    assertEquals("abc", map.get(123));
  }

  @Test
  public void testGet_incompatible() {
    map.put(123, "abc");
    assertNull(untypedMap().get("abc"));
  }

  @Test
  public void testPut_Initial() {
    assertNull(map.put(123, "abc"));
  }

  @Test
  public void testPut_Present() {
    assertNull(map.put(123, "abc"));
  }

  @Test
  public void testPutAll_Present() {
    Map<Integer, String> m = new HashMap<Integer, String>();
    m.put(123, "abc");
    m.put(4711, "cologne");
    map.putAll(m);
    assertEquals(2, map.size());
  }

  @Test
  public void entrySet_size() {
    assertEquals(0, map.entrySet().size());
    map.put(123, "abc");
    assertEquals(1, map.entrySet().size());
  }

  @Test
  public void keySet_size() {
    assertEquals(0, map.keySet().size());
    map.put(123, "abc");
    assertEquals(1, map.keySet().size());
  }

  @Test
  public void keySet_contains() {
    assertFalse(untypedMap().keySet().contains("abc"));
    map.put(123, "abc");
    assertFalse(untypedMap().keySet().contains("abc"));
    assertTrue(map.keySet().contains(123));
  }

  @Test
  public void valueSet_size() {
    assertEquals(0, map.values().size());
    map.put(123, "abc");
    assertEquals(1, map.values().size());
  }

  @Test
  public void entrySet_read() {
    assertFalse(map.entrySet().iterator().hasNext());
    map.put(123, "abc");
    Map.Entry<Integer, String> e = map.entrySet().iterator().next();
    assertEquals((Integer) 123, e.getKey());
    assertEquals("abc", e.getValue());
  }

  @Test
  public void valueSet_read() {
    assertFalse(map.values().iterator().hasNext());
    map.put(123, "abc");
    String s = map.values().iterator().next();
    assertEquals("abc", s);
  }

  @Test
  public void keySet_read() {
    assertFalse(map.keySet().iterator().hasNext());
    map.put(123, "abc");
    int k = map.keySet().iterator().next();
    assertEquals(123, k);
  }

  @Test
  public void entrySet_remove() {
    map.put(123, "abc");
    Iterator<?> it = map.entrySet().iterator();
    it.next();
    it.remove();
    assertFalse(map.containsKey(123));
  }

  @Test
  public void keySet_remove() {
    map.put(123, "abc");
    map.keySet().remove(123);
    assertFalse(map.containsKey(123));
  }

  @Test
  public void valueSet_remove() {
    map.put(123, "abc");
    map.values().remove("abc");
    assertFalse(map.containsKey(123));
  }

  @Test
  public void test_equals() {
    assertTrue(map.equals(map));
    assertFalse(map.equals("123"));
  }

  @Test
  public void computeIfAbsent() {
    String value = map.computeIfAbsent(123, integer -> "foo");
    assertEquals("foo", value);
    assertEquals("foo", map.get(123));
    value = map.computeIfAbsent(123, integer -> "bar");
    assertEquals("foo", value);
    assertEquals("foo", map.get(123));
    assertThatCode(() -> {
      map.computeIfAbsent(4711, key -> key.toString().charAt(123) + "");
    }).as("expect out of bounds")
      .isInstanceOf(StringIndexOutOfBoundsException.class);
  }

  @Test
  public void computeIfPresent() {
    String value = map.computeIfPresent(123, (integer, s) -> "foo");
    assertNull(value);
    assertNull(map.get(123));
    map.put(123, "foo");
    value = map.computeIfPresent(123, (integer, s) -> "bar");
    assertEquals("bar", value);
    assertEquals("bar", map.get(123));
    value = map.computeIfPresent(123, (integer, s) -> null);
    assertNull(value);
    assertNull(map.get(123));
    assertFalse(untypedMap().containsValue(123));
    map.put(123, "xy");
    assertThatCode(() -> {
      map.computeIfPresent(123, (key, s) -> s.charAt(123) + "");
    }).as("expect out of bounds")
      .isInstanceOf(StringIndexOutOfBoundsException.class);
  }

  @Test
  public void compute() {
    String value = map.compute(123, (integer, s) -> "" + s);
    assertEquals("null", value);
    map.put(123, "foo");
    value = map.compute(123, (integer, s) -> {
      assertEquals("foo", s);
      return "bar";
    });
    assertEquals("bar", value);
    assertEquals("bar", map.get(123));
    value = map.compute(123, (integer, s) -> {
      assertEquals("bar", s);
      return null;
    });
    assertNull(value);
    assertNull(map.get(123));
    assertFalse(untypedMap().containsValue(123));
    assertThatCode(() -> {
      map.compute(123, (integer, s) -> s.hashCode() + "");
    }).as("expect NPE")
      .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void test_hashCode() {
    assertTrue(map.hashCode() == map.hashCode());
  }

}
