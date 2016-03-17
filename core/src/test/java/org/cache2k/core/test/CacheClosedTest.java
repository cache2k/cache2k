package org.cache2k.core.test;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.Cache;
import org.cache2k.CacheBuilder;
import org.cache2k.junit.FastTests;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.cache2k.core.test.StaticUtil.*;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class CacheClosedTest {

  static final Integer VALUE = 1;
  static final Integer KEY = 1;
  static Cache<Integer, Integer> cache;

  @BeforeClass
  public static void setUp() {
    cache = CacheBuilder
      .newCache(Integer.class, Integer.class)
      .name(CacheClosedTest.class)
      .eternal(true)
      .entryCapacity(1000)
      .build();
    cache.close();
  }

  @Test(expected = IllegalStateException.class)
  public void get() {
    cache.get(KEY);
  }

  @Test(expected = IllegalStateException.class)
  public void peek() {
    cache.peek(KEY);
  }

  @Test(expected = IllegalStateException.class)
  public void peekAndRemove() {
    cache.peekAndRemove(KEY);
  }

  @Test(expected = IllegalStateException.class)
  public void peekAll() {
    cache.peekAll(asSet(KEY));
  }

  @Test(expected = IllegalStateException.class)
  public void peekAndPut() {
    cache.peekAndPut(KEY, VALUE);
  }

  @Test(expected = IllegalStateException.class)
  public void peekAndReplace() {
    cache.peekAndReplace(KEY, VALUE);
  }

  /*
   * TODO-B: add tests for remaining methods.
   */

}
