package org.cache2k.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import static org.junit.Assert.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Jens Wilke
 */
public class ConcurrentMapTest {

  static ConcurrentMap<Integer, String> map;

  @Before
  public void setUp() {
    map = new ConcurrentHashMap<Integer, String>();
  }

  @After
  public void tearDown() {
    map.clear();
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
  public void testContainsValue_Initial() {
    assertFalse(map.containsValue("abc"));
  }

  @Test
  public void testContainsValue_Present() {
    map.put(123, "abc");
    assertTrue(map.containsValue("abc"));
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
  public void testPut_Initial() {
    assertNull(map.put(123, "abc"));
  }

  @Test
  public void testPut_Present() {
    assertNull(map.put(123, "abc"));
  }

}
