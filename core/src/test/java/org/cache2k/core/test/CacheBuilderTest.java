package org.cache2k.core.test;

/*
 * #%L
 * cache2k core package
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
