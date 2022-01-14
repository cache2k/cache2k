package org.cache2k.core;

/*-
 * #%L
 * cache2k core implementation
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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
    return map;
  }

  @Before
  public void setUp() {
    map = new ConcurrentHashMap<>();
  }

  @After
  public void tearDown() {
    map = null;
  }

  @Test
  public void testIsEmpty_Initial() {
    assertThat(map.isEmpty()).isTrue();
  }

  @Test
  public void testSize_Initial() {
    assertThat(map.size()).isEqualTo(0);
  }

  @Test
  public void testContainsKey_Initial() {
    assertThat(map.containsKey(123)).isFalse();
  }

  @Test
  public void testContainsKey_Present() {
    map.put(123, "abc");
    assertThat(map.containsKey(123)).isTrue();
  }

  @Test
  public void testContainsKey_incompatible() {
    map.put(123, "abc");
    assertThat(map.containsKey("123")).isFalse();
  }

  @Test
  public void testContainsValue_Initial() {
    assertThat(map.containsValue("abc")).isFalse();
  }

  @Test
  public void testContainsValue_Present() {
    map.put(123, "abc");
    assertThat(map.containsValue("abc")).isTrue();
  }

  @Test(expected = NullPointerException.class)
  public void testContainsValue_null() {
    boolean f = map.containsValue(null);
  }

  @Test
  public void testPutIfAbsent_Initial() {
    String v = map.putIfAbsent(123, "abc");
    assertThat(map.containsKey(123)).isTrue();
    assertThat(v).isNull();
  }

  @Test
  public void testPutIfAbsent_Present() {
    map.put(123, "abc");
    String v = map.putIfAbsent(123, "xyz");
    assertThat(map.containsKey(123)).isTrue();
    assertThat(v).isEqualTo("abc");
  }

  @Test
  public void testRemove_Initial() {
    String v = map.remove(123);
    assertThat(v).isNull();
  }

  @Test
  public void testRemove_Present() {
    map.put(123, "abc");
    String v = map.remove(123);
    assertThat(v).isEqualTo("abc");
  }

  @Test
  public void testRemove_Incompatible() {
    ((Map<?, ?>) map).remove("abc");
  }

  @Test
  public void testRemove2Arg_Initial() {
    boolean v = map.remove(123, "abc");
    assertThat(v).isFalse();
  }

  @Test
  public void testRemove2Arg_Present_NotEqual() {
    map.put(123, "abc");
    boolean v = map.remove(123, "bla");
    assertThat(v).isFalse();
  }

  @Test
  public void testRemove2Arg_Present_Equal() {
    map.put(123, "abc");
    boolean v = map.remove(123, "abc");
    assertThat(v).isTrue();
  }

  @Test
  public void testRemove2Arg_incompatible_types() {
    map.put(123, "abc");
    boolean v = map.remove(123, 123);
    assertThat(v).isFalse();
  }

  @Test
  public void testClear() {
    map.put(123, "abc");
    map.clear();
    assertThat(map.size()).isEqualTo(0);
  }

  @Test
  public void testReplace3Arg_Initial() {
    boolean v = map.replace(123, "abc", "bla");
    assertThat(v).isFalse();
  }

  @Test
  public void testReplace3Arg_Present_NotEqual() {
    map.put(123, "abc");
    boolean v = map.replace(123, "bla", "bla2");
    assertThat(v).isFalse();
    assertThat(map.get(123)).isEqualTo("abc");
  }

  @Test
  public void testReplace3Arg_Present_Equal() {
    map.put(123, "abc");
    boolean v = map.replace(123, "abc", "bla2");
    assertThat(v).isTrue();
    assertThat(map.get(123)).isEqualTo("bla2");
  }

  @Test
  public void testReplace2Arg_Initial() {
    String v = map.replace(123, "abc");
    assertThat(v).isNull();
  }

  @Test
  public void testReplace2Arg_Present() {
    map.put(123, "abc");
    String v = map.replace(123, "bla");
    assertThat(v).isEqualTo("abc");
    assertThat(map.get(123)).isEqualTo("bla");
  }

  @Test
  public void testGet_Initial() {
    assertThat(map.get(123)).isNull();
  }

  @Test
  public void testGet_Present() {
    map.put(123, "abc");
    assertThat(map.get(123)).isEqualTo("abc");
  }

  @Test
  public void testGet_incompatible() {
    map.put(123, "abc");
    assertThat(untypedMap().get("abc")).isNull();
  }

  @Test
  public void testPut_Initial() {
    assertThat(map.put(123, "abc")).isNull();
  }

  @Test
  public void testPut_Present() {
    assertThat(map.put(123, "abc")).isNull();
  }

  @Test
  public void testPutAll_Present() {
    Map<Integer, String> m = new HashMap<>();
    m.put(123, "abc");
    m.put(4711, "cologne");
    map.putAll(m);
    assertThat(map.size()).isEqualTo(2);
  }

  @Test
  public void entrySet_size() {
    assertThat(map.entrySet().size()).isEqualTo(0);
    map.put(123, "abc");
    assertThat(map.entrySet().size()).isEqualTo(1);
  }

  @Test
  public void keySet_size() {
    assertThat(map.keySet().size()).isEqualTo(0);
    map.put(123, "abc");
    assertThat(map.keySet().size()).isEqualTo(1);
  }

  @Test
  public void keySet_contains() {
    assertThat(untypedMap().containsKey("abc")).isFalse();
    map.put(123, "abc");
    assertThat(untypedMap().containsKey("abc")).isFalse();
    assertThat(map.containsKey(123)).isTrue();
  }

  @Test
  public void valueSet_size() {
    assertThat(map.values().size()).isEqualTo(0);
    map.put(123, "abc");
    assertThat(map.values().size()).isEqualTo(1);
  }

  @Test
  public void entrySet_read() {
    assertThat(map.entrySet().iterator().hasNext()).isFalse();
    map.put(123, "abc");
    Map.Entry<Integer, String> e = map.entrySet().iterator().next();
    assertThat(e.getKey()).isEqualTo((Integer) 123);
    assertThat(e.getValue()).isEqualTo("abc");
  }

  @Test
  public void valueSet_read() {
    assertThat(map.values().iterator().hasNext()).isFalse();
    map.put(123, "abc");
    String s = map.values().iterator().next();
    assertThat(s).isEqualTo("abc");
  }

  @Test
  public void keySet_read() {
    assertThat(map.keySet().iterator().hasNext()).isFalse();
    map.put(123, "abc");
    int k = map.keySet().iterator().next();
    assertThat(k).isEqualTo(123);
  }

  @Test
  public void entrySet_remove() {
    map.put(123, "abc");
    Iterator<?> it = map.entrySet().iterator();
    it.next();
    it.remove();
    assertThat(map.containsKey(123)).isFalse();
  }

  @Test
  public void keySet_remove() {
    map.put(123, "abc");
    map.keySet().remove(123);
    assertThat(map.containsKey(123)).isFalse();
  }

  @Test
  public void valueSet_remove() {
    map.put(123, "abc");
    map.values().remove("abc");
    assertThat(map.containsKey(123)).isFalse();
  }

  @Test
  public void test_equals() {
    assertThat(map.equals(map)).isTrue();
    assertThat(map.equals("123")).isFalse();
  }

  @Test
  public void computeIfAbsent() {
    String value = map.computeIfAbsent(123, integer -> "foo");
    assertThat(value).isEqualTo("foo");
    assertThat(map.get(123)).isEqualTo("foo");
    value = map.computeIfAbsent(123, integer -> "bar");
    assertThat(value).isEqualTo("foo");
    assertThat(map.get(123)).isEqualTo("foo");
    assertThatCode(() -> map.computeIfAbsent(4711, key -> key.toString().charAt(123) + "")).as("expect out of bounds")
      .isInstanceOf(StringIndexOutOfBoundsException.class);
  }

  @Test
  public void computeIfPresent() {
    String value = map.computeIfPresent(123, (integer, s) -> "foo");
    assertThat(value).isNull();
    assertThat(map.get(123)).isNull();
    map.put(123, "foo");
    value = map.computeIfPresent(123, (integer, s) -> "bar");
    assertThat(value).isEqualTo("bar");
    assertThat(map.get(123)).isEqualTo("bar");
    value = map.computeIfPresent(123, (integer, s) -> null);
    assertThat(value).isNull();
    assertThat(map.get(123)).isNull();
    assertThat(untypedMap().containsValue(123)).isFalse();
    map.put(123, "xy");
    assertThatCode(() -> map.computeIfPresent(123, (key, s) -> s.charAt(123) + "")).as("expect out of bounds")
      .isInstanceOf(StringIndexOutOfBoundsException.class);
  }

  @Test
  public void compute() {
    String value = map.compute(123, (integer, s) -> "" + s);
    assertThat(value).isEqualTo("null");
    map.put(123, "foo");
    value = map.compute(123, (integer, s) -> {
      assertThat(s).isEqualTo("foo");
      return "bar";
    });
    assertThat(value).isEqualTo("bar");
    assertThat(map.get(123)).isEqualTo("bar");
    value = map.compute(123, (integer, s) -> {
      assertThat(s).isEqualTo("bar");
      return null;
    });
    assertThat(value).isNull();
    assertThat(map.get(123)).isNull();
    assertThat(untypedMap().containsValue(123)).isFalse();
    assertThatCode(() -> map.compute(123, (integer, s) -> s.hashCode() + "")).as("expect NPE")
      .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void test_hashCode() {
    assertThat(map.hashCode() == map.hashCode()).isTrue();
  }

}
