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

import org.assertj.core.api.Assertions;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheManager;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.Assert.*;

import org.cache2k.operation.CacheControl;
import org.junit.Test;
import org.springframework.cache.Cache;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

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
    m.addCaches(b -> b);
  }

  @Test(expected = IllegalArgumentException.class)
  public void defaultManagerRejected() {
    SpringCache2kCacheManager m = getManager();
    m.addCaches(b -> Cache2kBuilder.forUnknownTypes().name("abc"));
  }

  @Test
  public void defaultSetupSupplier() {
    SpringCache2kCacheManager m = new SpringCache2kCacheManager(() ->
      Cache2kBuilder.forUnknownTypes()
        .manager(CacheManager.getInstance(
          SpringCache2KCacheManagerTest.class.getSimpleName() + "defaultSetupSupplier")
        ));
    assertNotNull(m.getCache("abc"));
  }

  @Test(expected = IllegalStateException.class)
  public void doubleAdd() {
    SpringCache2kCacheManager m = getManager();
    m.setAllowUnknownCache(true);
    assertNotNull(m.getCache("abc"));
    m.addCaches(b -> b.name("abc"));
  }

  @Test(expected = IllegalStateException.class)
  public void doubleAdd2() {
    SpringCache2kCacheManager m = getManager();
    m.addCaches(
      b -> b.name("abc"),
      b -> b.name("abc"));
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
    m.defaultSetup(b -> b.entryCapacity(1234));
    m.addCaches(b -> b.name("cache1"));
    assertEquals(1234,
      CacheControl.of(m.getCache("cache1").getNativeCache()).getEntryCapacity());
    assertEquals(1234,
      CacheControl.of(m.getCache("cache2").getNativeCache()).getEntryCapacity());
  }

  @Test
  public void testDefaultSetup2() {
    SpringCache2kCacheManager m =
      new SpringCache2kCacheManager(
        SpringCache2KCacheManagerTest.class.getSimpleName() + "defaultSetup2");
    m.defaultSetup(b -> b.entryCapacity(1234).valueType(String.class).loader(key -> "default"));
    m.addCaches(b -> b.name("cache1"));
    assertEquals(1234,
      CacheControl.of(m.getCache("cache1").getNativeCache()).getEntryCapacity());
    assertEquals(1234,
      CacheControl.of(m.getCache("cache2").getNativeCache()).getEntryCapacity());
    assertEquals("default", m.getCache("cache2").get(123).get());
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

  @Test
  public void testExample1() {
    SpringCache2kCacheManager m =
      new SpringCache2kCacheManager(springCache2kDefaultSupplier1());
    assertEquals("springDefault", m.getNativeCacheManager().getName());
  }

  @Test
  public void testExample2() {
    SpringCache2kCacheManager m =
      new SpringCache2kCacheManager(springCache2kDefaultSupplier2());
    assertEquals(6666,
      CacheControl.of(m.getCache("unknown").getNativeCache())
        .getEntryCapacity());
  }

  @Test
  public void destroy() throws Exception {
    SpringCache2kCacheManager m =
      new SpringCache2kCacheManager(
        CacheManager.getInstance(
          SpringCache2KCacheManagerTest.class.getSimpleName() + "destroy"));
    SpringCache2kCache cache = m.getCache("test1");
    assertFalse(cache.getNativeCache().isClosed());
    m.destroy();
    assertTrue(cache.getNativeCache().isClosed());
    assertThatCode(() -> {
      m.getCache("hello");
    }).isInstanceOf(IllegalStateException.class);
  }

  /**
   * Example default supplier, topProvide defaults for all cache2k caches when used
   * with Spring Boot.
   */
  public SpringCache2kDefaultSupplier springCache2kDefaultSupplier1() {
    return () -> SpringCache2kDefaultSupplier.supplyDefaultBuilder()
      .expireAfterWrite(6, TimeUnit.MINUTES)
      .entryCapacity(5555)
      .strictEviction(true);
  }

  /**
   * Example default supplier with different manager
   */
  public SpringCache2kDefaultSupplier springCache2kDefaultSupplier2() {
    return () -> Cache2kBuilder.forUnknownTypes()
      .manager(org.cache2k.CacheManager.getInstance("specialManager"))
      .setup(SpringCache2kDefaultSupplier::applySpringDefaults)
      .expireAfterWrite(6, TimeUnit.MINUTES)
      .entryCapacity(6666)
      .strictEviction(true);
  }

}
