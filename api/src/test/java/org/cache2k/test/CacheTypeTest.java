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

import org.cache2k.configuration.CacheConfiguration;
import org.cache2k.configuration.CacheType;
import org.cache2k.configuration.CacheTypeDescriptor;
import org.junit.Test;
import static org.junit.Assert.*;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test whether every type combination with generics can be build and retrieved from the type descriptor.
 *
 * @see CacheType
 * @see CacheTypeDescriptor
 *
 * @author Jens Wilke
 */
public class CacheTypeTest {

  /**
   * Test how to do it via the Java API.
   */
  @Test
  public void testIntArrayViaReflection() {
    CacheType<int[]> vtt = new CacheType<int[]>() {};
    ParameterizedType pt = (ParameterizedType) vtt.getClass().getGenericSuperclass();
    Class c = (Class) pt.getActualTypeArguments()[0];
    assertTrue(c.isArray());
    assertEquals(int.class, c.getComponentType());
  }

  /**
   * Test how to do it via the Java API.
   */
  @Test
  public void testStringListViaReflection() {
    CacheType<List<String>> vtt = new CacheType<List<String>>() {};
    ParameterizedType pt = (ParameterizedType) vtt.getClass().getGenericSuperclass();
    ParameterizedType t = (ParameterizedType) pt.getActualTypeArguments()[0];
    assertEquals(String.class, t.getActualTypeArguments()[0]);
  }

  /**
   * Test how to do it via the Java API.
   */
  @Test
  public void testListArrayViaReflection() {
    CacheType<List<String>[]> vtt = new CacheType<List<String>[]>() {};
    ParameterizedType pt = (ParameterizedType) vtt.getClass().getGenericSuperclass();
    GenericArrayType t = (GenericArrayType) pt.getActualTypeArguments()[0];
    ParameterizedType pt2 = (ParameterizedType) t.getGenericComponentType();
    assertEquals(List.class, pt2.getRawType());
    assertEquals(String.class, pt2.getActualTypeArguments()[0]);
  }

  @Test
  public void testSimpleType() {
    CacheType<String> tt = new CacheType<String>() {};
    CacheTypeDescriptor td = tt.getBeanRepresentation();
    assertEquals(String.class, tt.getBeanRepresentation().getType());
    assertEquals(CacheTypeDescriptor.BaseType.DESCRIPTOR_TO_STRING_PREFIX + "String", td.toString());
  }

  @Test
  public void testPrimitiveArrayType() {
    CacheType<int[]> tt = new CacheType<int[]>() {};
    CacheTypeDescriptor td = tt.getBeanRepresentation();
    assertNull(td.getType());
    assertTrue(td.isArray());
    assertEquals(int.class, td.getComponentType().getType());
    assertEquals(CacheTypeDescriptor.DESCRIPTOR_TO_STRING_PREFIX + "int[]", td.toString());
  }

  @Test
  public void testPrimitiveMultiArrayType() {
    CacheType<int[][]> tt = new CacheType<int[][]>() {};
    CacheTypeDescriptor td = tt.getBeanRepresentation();
    assertNull(td.getType());
    assertTrue(td.isArray());
    assertNull(td.getComponentType().getType());
    assertTrue(td.getComponentType().isArray());
    assertEquals(int.class, td.getComponentType().getComponentType().getType());
    assertEquals(CacheTypeDescriptor.DESCRIPTOR_TO_STRING_PREFIX + "int[][]", td.toString());
  }

  @Test
  public void testGenericArrayType() {
    CacheType<List<String>[]> tt = new CacheType<List<String>[]>() {};
    CacheTypeDescriptor td = tt.getBeanRepresentation();
    assertNull(td.getType());
    assertTrue(td.isArray());
    assertEquals(List.class, td.getComponentType().getType());
    assertTrue(td.getComponentType().hasTypeArguments());
    assertEquals(1, td.getComponentType().getTypeArguments().length);
    assertEquals(String.class, td.getComponentType().getTypeArguments()[0].getType());
    assertEquals(CacheTypeDescriptor.DESCRIPTOR_TO_STRING_PREFIX + "java.util.List<String>[]", td.toString());
  }

  @Test
  public void testGenericType() {
    CacheType<Map<String,List<String>>> tt = new CacheType<Map<String,List<String>>>() {};
    CacheTypeDescriptor td = tt.getBeanRepresentation();
    assertEquals(Map.class, td.getType());
    assertFalse(td.isArray());
    assertTrue(td.hasTypeArguments());
    assertEquals(2, td.getTypeArguments().length);
    assertEquals(String.class, td.getTypeArguments()[0].getType());
    assertEquals(List.class, td.getTypeArguments()[1].getType());
    assertTrue(td.getTypeArguments()[1].hasTypeArguments());
    assertEquals(CacheTypeDescriptor.DESCRIPTOR_TO_STRING_PREFIX +
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
    CacheTypeDescriptor d = new CacheTypeDescriptor.OfClass(String.class);
    toXmlAndBackAndCheck(d);
  }

  @Test
  public void testArrayTypeBean() throws Exception {
    CacheTypeDescriptor d = new CacheType<int[]>(){}.getBeanRepresentation();
    toXmlAndBackAndCheck(d);
  }

  @Test
  public void testGenericTypeBean() throws Exception {
    CacheTypeDescriptor d = new CacheType<List<String>>(){}.getBeanRepresentation();
    toXmlAndBackAndCheck(d);
  }

  @Test
  public void testWeirdBean() throws Exception {
    CacheTypeDescriptor d = new CacheType<Map<List<Set<int[]>>,String[]>>(){}.getBeanRepresentation();
    toXmlAndBackAndCheck(d);
  }

  /**
   * getKeyType / setKeyType is not totally symmetrical, test it.
   */
  @Test
  public void testWithCacheConfig() throws Exception {
    CacheConfiguration c = new CacheConfiguration();
    c.setKeyType(String.class);
    c.setValueType(new CacheType<List<String>>(){});
    CacheConfiguration c2 = copyObjectViaXmlEncoder(c);
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
    CacheTypeDescriptor<T> type = new CacheType<T>() {};
  }

}
