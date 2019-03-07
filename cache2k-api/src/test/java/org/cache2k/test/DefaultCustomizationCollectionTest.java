package org.cache2k.test;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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

import org.cache2k.configuration.CustomizationCollection;
import org.cache2k.configuration.CustomizationSupplier;
import org.cache2k.configuration.CustomizationSupplierByClassName;
import org.cache2k.configuration.DefaultCustomizationCollection;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
public class DefaultCustomizationCollectionTest {

  CustomizationCollection<Object> collection = new DefaultCustomizationCollection<Object>();

  @Test
  public void initial() {
    assertEquals(0, collection.size());
    assertTrue(collection.isEmpty());
    assertFalse(collection.iterator().hasNext());
    assertEquals(0, collection.toArray().length);
    assertEquals(0, collection.toArray(new CustomizationSupplier[0]).length);
    assertFalse(collection.contains(this));
    assertFalse(collection.remove(this));
    collection.clear();
  }

  @Test
  public void addOne_remove() {
    CustomizationSupplier s = new CustomizationSupplierByClassName("dummy");
    collection.add(s);
    assertEquals(1, collection.size());
    assertFalse(collection.isEmpty());
    assertTrue(collection.iterator().hasNext());
    assertEquals(1, collection.toArray().length);
    assertEquals(1, collection.toArray(new CustomizationSupplier[0]).length);
    assertFalse(collection.contains(this));
    assertFalse(collection.remove(this));
    collection.clear();
    assertTrue(collection.isEmpty());
  }

  @Test(expected = IllegalArgumentException.class)
  public void addOneDouble() {
    CustomizationSupplier s = new CustomizationSupplierByClassName("dummy");
    collection.add(s);
    collection.add(s);
  }

  @Test
  public void addAll_retainAll_removeAll() {
    CustomizationSupplier s = new CustomizationSupplierByClassName("dummy");
    CustomizationCollection<Object> col2 = new DefaultCustomizationCollection<Object>();
    col2.add(s);
    collection.addAll(col2);
    assertTrue(collection.containsAll(col2));
    assertFalse(collection.retainAll(col2));
    assertTrue(collection.removeAll(col2));
  }

}
