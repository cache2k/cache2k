package org.cache2k.extra.spring;

/*-
 * #%L
 * cache2k Spring framework support
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheManager;

import static org.junit.Assert.*;

import org.cache2k.operation.CacheControl;
import org.junit.Test;

import java.util.Collections;

/**
 * Tests for evey method of the Spring cache2k cache manager.
 *
 * @author Jens Wilke
 */
public class SpringCache2KCacheManagerTest {

  @Test(expected = IllegalArgumentException.class)
  public void nameAndManagerMissing() {
    SpringCache2kCacheManager m = getManager();
    m.addCaches(b -> Cache2kBuilder.forUnknownTypes());
  }

  @Test(expected = IllegalArgumentException.class)
  public void nameMissing() {
    SpringCache2kCacheManager m = getManager();
    m.addCaches(b -> Cache2kBuilder.forUnknownTypes().manager(m.getNativeCacheManager()));
  }

  @Test(expected = IllegalArgumentException.class)
  public void managerMissing() {
    SpringCache2kCacheManager m = getManager();
    m.addCaches(b -> Cache2kBuilder.forUnknownTypes().name("abc"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void doubleAdd() {
    SpringCache2kCacheManager m = getManager();
    assertNotNull(m.getCache("abc"));
    assertNotNull(m.getCache("abc"));
    m.addCaches(b -> Cache2kBuilder.forUnknownTypes().name("abc"));
  }

  private SpringCache2kCacheManager getManager() {
    return new SpringCache2kCacheManager(
      CacheManager.getInstance(
        SpringCache2KCacheManagerTest.class.getSimpleName() + "separate"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testMissing() {
    SpringCache2kCacheManager m =
      new SpringCache2kCacheManager(
        CacheManager.getInstance(
          SpringCache2KCacheManagerTest.class.getSimpleName() + "notConfigured"));
    m.setAllowUnknownCache(false);
    m.getCache("testUnknown");
  }

  @Test
  public void testAll() {
    SpringCache2kCacheManager m =
      new SpringCache2kCacheManager(
        SpringCache2KCacheManagerTest.class.getSimpleName() + "notConfigured");
    assertTrue("allow unknown cache is default", m.isAllowUnknownCache());
    assertEquals(0, m.getCacheNames().size());
    assertEquals(0, m.getCacheMap().size());
    m.getCache("test");
    assertEquals(1, m.getCacheNames().size());
    assertEquals(1, m.getCacheMap().size());
    m.setCaches(Collections.singletonList(
      Cache2kBuilder.forUnknownTypes().name("other").config()));
    assertEquals(2, m.getCacheNames().size());
  }

  @Test
  public void testAddCaches() {
    SpringCache2kCacheManager m =
      new SpringCache2kCacheManager(
        SpringCache2KCacheManagerTest.class.getSimpleName() + "addCaches");
    m.addCaches(b -> b.name("cache1"));
    assertNotNull(m.getCache("cache1"));
    assertTrue(m.getConfiguredCacheNames().contains("cache1"));
  }

  @Test
  public void testDefaultSetup() {
    SpringCache2kCacheManager m =
      new SpringCache2kCacheManager(
        SpringCache2KCacheManagerTest.class.getSimpleName() + "defaultSetup");
    m.defaultSetup(b -> b.entryCapacity(1802));
    m.addCaches(b -> b.name("cache1"));
    assertEquals(1802,
      CacheControl.of(m.getCache("cache1").getNativeCache()).getEntryCapacity());
    assertEquals(1802,
      CacheControl.of(m.getCache("cache2").getNativeCache()).getEntryCapacity());
  }

  @Test
  public void testDynamicCache() {
    SpringCache2kCacheManager m =
      new SpringCache2kCacheManager(
        SpringCache2KCacheManagerTest.class.getSimpleName() + "dynamicCache");
    m.setAllowUnknownCache(true);
    assertNotNull(m.getCache("cache1"));
    assertTrue(m.getConfiguredCacheNames().isEmpty());
  }

}
