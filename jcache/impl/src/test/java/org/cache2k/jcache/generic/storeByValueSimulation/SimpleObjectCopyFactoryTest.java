package org.cache2k.jcache.generic.storeByValueSimulation;

/*
 * #%L
 * cache2k JCache JSR107 implementation
 * %%
 * Copyright (C) 2000 - 2015 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import org.junit.AfterClass;
import org.junit.Test;

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
  }

  @Test
  public void testStringIsImmutable() {
  }

  @Test
  public void testDateIsCloneable() {
  }

  @Test
  public void testImmutableCompleteInteger() {
    ObjectTransformer<Integer, Integer> t = factory.createCopyTransformer(Integer.class);
    Integer val = 45;
    Integer v2 = t.compact(val);
    v2 = t.expand(val);
  }

}
