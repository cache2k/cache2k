package org.cache2k.test;

/*
 * #%L
 * cache2k API
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

import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.configuration.CacheTypeCapture;
import org.cache2k.configuration.CacheType;
import org.cache2k.junit.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test whether every type combination with generics can be build and retrieved from the type descriptor.
 *
 * @see CacheTypeCapture
 * @see CacheType
 *
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class CacheTypeTest {

  /**
   * Test how to do it via the Java API.
   */
  @Test
  public void testIntArrayViaReflection() {
    CacheTypeCapture<int[]> vtt = new CacheTypeCapture<int[]>() {};
    ParameterizedType pt = (ParameterizedType) vtt.getClass().getGenericSuperclass();
    Type t = pt.getActualTypeArguments()[0];
    if (t instanceof Class) {
      Class c = (Class) t;
      assertTrue(c.isArray());
      assertEquals(int.class, c.getComponentType());
    } else {
      GenericArrayType at = (GenericArrayType) t;
      assertEquals(int.class, at.getGenericComponentType());
    }
  }

  /**
   * Test how to do it via the Java API.
   */
  @Test
  public void testStringListViaReflection() {
    CacheTypeCapture<List<String>> vtt = new CacheTypeCapture<List<String>>() {};
    ParameterizedType pt = (ParameterizedType) vtt.getClass().getGenericSuperclass();
    ParameterizedType t = (ParameterizedType) pt.getActualTypeArguments()[0];
    assertEquals(String.class, t.getActualTypeArguments()[0]);
  }

  /**
   * Test how to do it via the Java API.
   */
  @Test
  public void testListArrayViaReflection() {
    CacheTypeCapture<List<String>[]> vtt = new CacheTypeCapture<List<String>[]>() {};
    ParameterizedType pt = (ParameterizedType) vtt.getClass().getGenericSuperclass();
    GenericArrayType t = (GenericArrayType) pt.getActualTypeArguments()[0];
    ParameterizedType pt2 = (ParameterizedType) t.getGenericComponentType();
    assertEquals(List.class, pt2.getRawType());
    assertEquals(String.class, pt2.getActualTypeArguments()[0]);
  }

  @Test
  public void testSimpleType() {
    CacheTypeCapture<String> tt = new CacheTypeCapture<String>() {};
    CacheType td = tt.getBeanRepresentation();
    assertEquals(String.class, tt.getBeanRepresentation().getType());
    assertEquals(CacheType.DESCRIPTOR_TO_STRING_PREFIX + "String", td.toString());
  }

  @Test
  public void testPrimitiveArrayType() {
    CacheTypeCapture<int[]> tt = new CacheTypeCapture<int[]>() {};
    CacheType td = tt.getBeanRepresentation();
    assertNull(td.getType());
    assertTrue(td.isArray());
    assertEquals(int.class, td.getComponentType().getType());
    assertEquals(CacheType.DESCRIPTOR_TO_STRING_PREFIX + "int[]", td.toString());
  }

  @Test
  public void testPrimitiveMultiArrayType() {
    CacheTypeCapture<int[][]> tt = new CacheTypeCapture<int[][]>() {};
    CacheType td = tt.getBeanRepresentation();
    assertNull(td.getType());
    assertTrue(td.isArray());
    assertNull(td.getComponentType().getType());
    assertTrue(td.getComponentType().isArray());
    assertEquals(int.class, td.getComponentType().getComponentType().getType());
    assertEquals(CacheType.DESCRIPTOR_TO_STRING_PREFIX + "int[][]", td.toString());
  }

  @Test
  public void testGenericArrayType() {
    CacheTypeCapture<List<String>[]> tt = new CacheTypeCapture<List<String>[]>() {};
    CacheType td = tt.getBeanRepresentation();
    assertNull(td.getType());
    assertTrue(td.isArray());
    assertEquals(List.class, td.getComponentType().getType());
    assertTrue(td.getComponentType().hasTypeArguments());
    assertEquals(1, td.getComponentType().getTypeArguments().length);
    assertEquals(String.class, td.getComponentType().getTypeArguments()[0].getType());
    assertEquals(CacheType.DESCRIPTOR_TO_STRING_PREFIX + "java.util.List<String>[]", td.toString());
  }

  @Test
  public void testGenericType() {
    CacheTypeCapture<Map<String,List<String>>> tt = new CacheTypeCapture<Map<String,List<String>>>() {};
    CacheType td = tt.getBeanRepresentation();
    assertEquals(Map.class, td.getType());
    assertFalse(td.isArray());
    assertTrue(td.hasTypeArguments());
    assertEquals(2, td.getTypeArguments().length);
    assertEquals(String.class, td.getTypeArguments()[0].getType());
    assertEquals(List.class, td.getTypeArguments()[1].getType());
    assertTrue(td.getTypeArguments()[1].hasTypeArguments());
    assertEquals(CacheType.DESCRIPTOR_TO_STRING_PREFIX +
            "java.util.Map<String,java.util.List<String>>", td.toString());
  }

  /**
   * For testing the complete bean encode via the XMLEncode from the java.beans package.
   */
  void toXmlAndBackAndCheck(Object o) {
    Object o2 = copyObjectViaXmlEncoder(o);
    assertEquals(o, o2);
    assertEquals(o.hashCode(), o2.hashCode());
  }

  private <T> T copyObjectViaXmlEncoder(T o) {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    XMLEncoder enc = new XMLEncoder(bos);
    enc.writeObject(o);
    enc.close();
    ByteArrayInputStream bin = new ByteArrayInputStream(bos.toByteArray());
    XMLDecoder dec = new XMLDecoder(bin);
    Object o2 = dec.readObject();
    dec.close();
    assertTrue("no reference identity", o2 != o);
    assertEquals("same class", o.getClass(), o2.getClass());
    return (T) o2;
  }

  @Test
  public void testClassTypeBean() throws Exception {
    CacheType d = CacheTypeCapture.of(String.class);
    toXmlAndBackAndCheck(d);
  }

  @Test
  public void testArrayTypeBean() throws Exception {
    CacheType d = new CacheTypeCapture<int[]>(){}.getBeanRepresentation();
    toXmlAndBackAndCheck(d);
  }

  @Test
  public void testGenericTypeBean() throws Exception {
    CacheType d = new CacheTypeCapture<List<String>>(){}.getBeanRepresentation();
    toXmlAndBackAndCheck(d);
  }

  @Test
  public void testWeirdBean() throws Exception {
    CacheType d = new CacheTypeCapture<Map<List<Set<int[]>>,String[]>>(){}.getBeanRepresentation();
    toXmlAndBackAndCheck(d);
  }

  /**
   * getKeyType / setKeyType is not totally symmetrical, test it.
   */
  @Test
  public void testWithCacheConfig() throws Exception {
    Cache2kConfiguration c = new Cache2kConfiguration();
    c.setKeyType(String.class);
    c.setValueType(new CacheTypeCapture<List<String>>(){});
    Cache2kConfiguration c2 = copyObjectViaXmlEncoder(c);
    assertEquals(c.getKeyType(), c2.getKeyType());
    assertEquals(c.getValueType(), c2.getValueType());
  }

  /**
   * Type cannot be captured. Expect proper exception:
   * java.lang.IllegalArgumentException: The run time type is not available, got: T
   */
  @Test(expected = IllegalArgumentException.class)
  public void testExceptionIfInsideGeneric() {
    GenericWrapper<Integer> w = new GenericWrapper<Integer>();
  }

  static class GenericWrapper<T> {
    CacheType<T> type = new CacheTypeCapture<T>() {};
  }

}
