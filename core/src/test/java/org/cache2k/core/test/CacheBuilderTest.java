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
import org.cache2k.CacheType;
import org.cache2k.junit.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Cache builder tests for some special variants.
 *
 * @see org.cache2k.CacheBuilder
 */
@Category(FastTests.class)
public class CacheBuilderTest {

  @Test
  public void managerName() {
    Cache c = CacheBuilder.newCache().build();
    assertEquals("default", c.getCacheManager().getName());
    c.close();
  }

  @Test
  public void typesParameters() {
    Cache<Long, String> c =
      CacheBuilder.newCache()
        .valueType(String.class)
        .keyType(Long.class)
        .build();
    c.close();
  }

  @Test
  public void noTypes() {
    Cache c =
      CacheBuilder.newCache().build();
    c.put("hallo", 234);
    c.close();
  }

  @Test
  public void collectionValueType() {
    Cache<Long, List> c =
      CacheBuilder.newCache(Long.class, List.class, String.class).build();
    c.close();
  }

  @Test
  public void collectionValueCast() {
    Cache<Long, List<String>> c =
        (Cache<Long, List<String>>) ((Object) CacheBuilder.newCache(Long.class, List.class, String.class).build());
    c.put(123L, new ArrayList<String>());
    c.close();
  }

  @Test
  public void collectionValueCacheType() {
    Cache<Long, List<String>> c =
      CacheBuilder.newCache(Long.class, new CacheType<List<String>>() {}).build();
    c.put(123L, new ArrayList<String>());
    c.close();
  }

  @Test
  public void collectionValueClass() {
    Cache<Long, List<String>> c =
      (Cache<Long, List<String>>) (Object) CacheBuilder.newCache(Long.class, List.class).build();
    c.put(123L, new ArrayList<String>());
    c.close();
  }

  @Test
  public void typesParametersWith() {
    Cache<Long, List> c =
      CacheBuilder.newCache()
        .valueType(List.class)
        .keyType(Long.class)
        .build();
    c.close();
  }

  @Test
  public void noTypesAndCast() {
    Cache<Long, List<String>> c =
      (Cache<Long, List<String>>)
        CacheBuilder.newCache()
          .build();
    c.close();
  }

  @Test
  public void cacheNameForAnnotationDefault() {
    Cache<Long, List<String>> c =
      (Cache<Long, List<String>>)
        CacheBuilder.newCache()
          .name("package.name.ClassName.methodName(package.ParameterType,package.ParameterType")
          .build();
    c.close();
  }

}
