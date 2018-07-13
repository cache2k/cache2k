package org.cache2k.extra.spring;

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

import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheManager;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 *
 * @author Jens Wilke
 */
public class Cache2kCacheManagerTest {

  @Test(expected=IllegalArgumentException.class)
  public void nameAndManagerMissing() {
    Cache2kCacheManager m = getManager();
    m.addCache(Cache2kBuilder.forUnknownTypes());
  }

  @Test(expected=IllegalArgumentException.class)
  public void nameMissing() {
    Cache2kCacheManager m = getManager();
    m.addCache(Cache2kBuilder.forUnknownTypes().manager(m.getNativeCacheManager()));
  }

  @Test(expected=IllegalArgumentException.class)
  public void managerMissing() {
    Cache2kCacheManager m = getManager();
    m.addCache(Cache2kBuilder.forUnknownTypes().name("abc"));
  }

  @Test(expected=IllegalArgumentException.class)
  public void doubleAdd() {
    Cache2kCacheManager m = getManager();
    assertNotNull(m.getCache("abc"));
    assertNotNull(m.getCache("abc"));
    m.addCache(Cache2kBuilder.forUnknownTypes().name("abc"));
  }

  private Cache2kCacheManager getManager() {
    return new Cache2kCacheManager(
      CacheManager.getInstance(
        Cache2kCacheManagerTest.class.getSimpleName() + "separate"));
  }

  @Test
  public void testAll() {
    Cache2kCacheManager m =
      new Cache2kCacheManager(
        CacheManager.getInstance(
          Cache2kCacheManagerTest.class.getSimpleName() + "notConfigured"));
    assertEquals(0, m.getCacheNames().size());
    assertEquals(0, m.getCacheMap().size());
    m.getCache("test");
    assertEquals(1, m.getCacheNames().size());
    assertEquals(1, m.getCacheMap().size());
    m.addCache(Cache2kBuilder.forUnknownTypes()
      .name("other").toConfiguration());
    assertEquals(2, m.getCacheNames().size());
  }

}
