package org.cache2k.jcache.provider.generic.storeByValueSimulation;

/*-
 * #%L
 * cache2k JCache provider
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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cache2k.jcache.provider.generic.storeByValueSimulation.SimpleObjectCopyFactory.extractPublicClone;
import static org.cache2k.jcache.provider.generic.storeByValueSimulation.SimpleObjectCopyFactory.isImmutable;

/**
 * @author Jens Wilke
 */
public class SimpleObjectCopyFactoryTest {

  static SimpleObjectCopyFactory factory = new SimpleObjectCopyFactory();

  @AfterAll
  public static void tearDownClass() {
    factory = null;
  }

  @Test
  public void testIntegerIsImmutable() {
    assertThat(isImmutable(((Integer) 123).getClass())).isTrue();
  }

  @Test
  public void testStringIsImmutable() {
    assertThat(isImmutable("abc".getClass())).isTrue();
  }

  @Test
  public void testDateIsCloneable() {
    assertThat(extractPublicClone(Date.class)).isNotNull();
  }

  @Test
  public void testImmutableCompleteInteger() {
    ObjectTransformer<Integer, Integer> t = factory.createCopyTransformer(Integer.class);
    Integer val = 45;
    Integer v2 = t.compact(val);
    assertThat(val == v2).isTrue();
    v2 = t.expand(val);
    assertThat(val == v2).isTrue();
  }

  @Test
  public void testDate() {
    ObjectTransformer<Date, Date> t = factory.createCopyTransformer(Date.class);
    assertThat(t).isNotNull();
  }

  public static class Dummy { }

  @Test
  public void testNotSerializable() {
    ObjectTransformer<Dummy, Dummy> t = factory.createCopyTransformer(Dummy.class);
    assertThat(t).isNull();
  }

  public static class DummySerializable implements Serializable { }

  @Test
  public void testSerializable() {
    ObjectTransformer<DummySerializable, DummySerializable> t = factory.createCopyTransformer(DummySerializable.class);
    assertThat(t).isNotNull();
  }

}
