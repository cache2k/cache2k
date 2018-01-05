package org.cache2k.jcache.provider.generic.storeByValueSimulation;

/*
 * #%L
 * cache2k JCache provider
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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

import org.junit.AfterClass;
import org.junit.Test;

import java.io.Serializable;
import java.util.Date;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
public class SimpleObjectCopyFactoryTest {

  static SimpleObjectCopyFactory factory = new SimpleObjectCopyFactory();

  @AfterClass
  public static void tearDownClass() {
    factory = null;
  }

  @Test
  public void testIntegerIsImmutable() {
    assertTrue(SimpleObjectCopyFactory.isImmutable(((Integer) 123).getClass()));
  }

  @Test
  public void testStringIsImmutable() {
    assertTrue(SimpleObjectCopyFactory.isImmutable("abc".getClass()));
  }

  @Test
  public void testDateIsCloneable() {
    assertNotNull(SimpleObjectCopyFactory.extractPublicClone(Date.class));
  }

  @Test
  public void testImmutableCompleteInteger() {
    ObjectTransformer<Integer, Integer> t = factory.createCopyTransformer(Integer.class);
    Integer val = 45;
    Integer v2 = t.compact(val);
    assertTrue(val == v2);
    v2 = t.expand(val);
    assertTrue(val == v2);
  }

  @Test
  public void testDate() {
    ObjectTransformer<Date, Date> t = factory.createCopyTransformer(Date.class);
    assertNotNull(t);
  }

  public static class Dummy { }

  @Test
  public void testNotSerializable() {
    ObjectTransformer<Dummy, Dummy> t = factory.createCopyTransformer(Dummy.class);
    assertNull(t);
  }

  public static class DummySerializable implements Serializable { }

  @Test
  public void testSerializable() {
    ObjectTransformer<DummySerializable, DummySerializable> t = factory.createCopyTransformer(DummySerializable.class);
    assertNotNull(t);
  }

}
