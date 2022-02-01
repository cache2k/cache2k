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
import org.cache2k.operation.CacheControl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.cache2k.Cache2kBuilder.forUnknownTypes;
import static org.cache2k.CacheManager.getInstance;
import static org.cache2k.operation.CacheControl.of;

import java.util.concurrent.TimeUnit;

/**
 * Tests for evey method of the Spring cache2k cache manager.
 *
 * @author Jens Wilke
 */
public class SpringCache2KCacheManagerTest {

  @Test
  public void nameAndManagerMissing() {
    SpringCache2kCacheManager m = getManager();
    assertThatCode(() -> m.addCaches(b -> Cache2kBuilder.forUnknownTypes()))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void nameMissing() {
    SpringCache2kCacheManager m = getManager();
    assertThatCode(() -> m.addCaches(b -> b)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void defaultManagerRejected() {
    SpringCache2kCacheManager m = getManager();
    assertThatCode(() -> m.addCaches(b -> Cache2kBuilder.forUnknownTypes().name("abc")))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void getNativeManager() {
    SpringCache2kCacheManager m = new SpringCache2kCacheManager();
    assertThat(m.getNativeCacheManager().getName()).isEqualTo("springDefault");
  }

  @Test
  public void doubleAdd() {
    SpringCache2kCacheManager m = getManager();
    m.setAllowUnknownCache(true);
    assertThat(m.getCache("abc")).isNotNull();
    assertThatCode(() -> m.addCaches(b -> b.name("abc")))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void doubleAdd2() {
    SpringCache2kCacheManager m = getManager();
    assertThatCode(() -> m.addCaches(
      b -> b.name("abc"),
      b -> b.name("abc")))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void defaultAfterAdd() {
    SpringCache2kCacheManager m = getUniqueManager("defaultAfterAdd");
    assertThatCode(() ->
      m.addCaches(b -> b.name("abc"))
        .defaultSetup(b -> b.eternal(true)))
      .isInstanceOf(IllegalStateException.class);
  }

  private SpringCache2kCacheManager getManager() {
    return getUniqueManager("separate");
  }

  private SpringCache2kCacheManager getUniqueManager(String qualifier) {
    return new SpringCache2kCacheManager(
      CacheManager.getInstance(
        SpringCache2KCacheManagerTest.class.getSimpleName() + qualifier));
  }

  @Test
  public void testMissing() {
    SpringCache2kCacheManager m = getUniqueManager("testMissing");
    m.setAllowUnknownCache(false);
    assertThat(m.getCache("testUnknown")).isNull();
  }

  @Test
  public void testExisting() {
    SpringCache2kCacheManager m = getUniqueManager("testExisting");
    m.addCache("known", b -> b);
    m.setAllowUnknownCache(false);
    m.getCache("known");
  }

  @Test
  public void testAll() {
    SpringCache2kCacheManager m = getUniqueManager("testAll");
    assertThat(m.isAllowUnknownCache())
      .as("allow unknown cache is default")
      .isTrue();
    assertThat(m.getCacheNames().size()).isEqualTo(0);
    assertThat(m.getCacheMap().size()).isEqualTo(0);
    m.getCache("test");
    assertThat(m.getCacheNames().size()).isEqualTo(1);
    assertThat(m.getCacheMap().size()).isEqualTo(1);
  }

  @Test
  public void testAddCaches() {
    SpringCache2kCacheManager m = getUniqueManager("addCaches");
    m.addCaches(b -> b.name("cache1"));
    assertThat(m.getCache("cache1")).isNotNull();
    assertThat(m.getConfiguredCacheNames().contains("cache1")).isTrue();
  }

  @Test
  public void testDefaultSetup() {
    SpringCache2kCacheManager m = getUniqueManager("defaultSetup");
    m.defaultSetup(b -> b.entryCapacity(1234));
    m.addCaches(b -> b.name("cache1"));
    assertThat(of(m.getCache("cache1").getNativeCache()).getEntryCapacity()).isEqualTo(1234);
    assertThat(of(m.getCache("cache2").getNativeCache()).getEntryCapacity()).isEqualTo(1234);
  }

  @Test
  public void testDefaultSetupTwice() {
    SpringCache2kCacheManager m = getUniqueManager("defaultSetup");
    m.defaultSetup(b -> b.entryCapacity(1234));
    assertThatCode(() -> {
      m.defaultSetup(b -> b.entryCapacity(4321));
    }).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void testDefaultSetup2() {
    SpringCache2kCacheManager m = getUniqueManager("defaultSetup2");
    m.defaultSetup(b -> b.entryCapacity(1234).valueType(String.class).loader(key -> "default"));
    m.addCaches(b -> b.name("cache1"));
    assertThat(of(m.getCache("cache1").getNativeCache()).getEntryCapacity()).isEqualTo(1234);
    assertThat(of(m.getCache("cache2").getNativeCache()).getEntryCapacity()).isEqualTo(1234);
    assertThat(m.getCache("cache2").get(123).get()).isEqualTo("default");
  }

  @Test
  public void testDynamicCache() {
    SpringCache2kCacheManager m = getUniqueManager("dynamicCache");
    assertThat(m.isAllowUnknownCache()).isTrue();
    assertThat(m.getCache("cache1")).isNotNull();
    assertThat(m.getConfiguredCacheNames().isEmpty()).isTrue();
    m.addCache("added", b -> b);
    assertThat(m.getCache("added")).isNotNull();
    assertThat(m.getConfiguredCacheNames().size()).isEqualTo(1);
  }

  @Test
  public void testSetDefaultCacheNames() {
    SpringCache2kCacheManager m = getUniqueManager("setDefaultCacheNames");
    m.setDefaultCacheNames();
    m.setDefaultCacheNames("1", "2", "3");
    assertThat(m.isAllowUnknownCache()).isFalse();
    assertThat(m.getCache(("1"))).isNotNull();
  }

  @Test
  public void testSetDefaultCacheNamesDouble() {
    SpringCache2kCacheManager m = getUniqueManager("setDefaultCacheNamesDouble");
    assertThatCode(() -> m.setDefaultCacheNames("1", "1"))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void testCombine() {
    SpringCache2kCacheManager m = getUniqueManager("testCombine");
    SpringCache2kCacheManager m2 =
      m.defaultSetup(b -> b
          .strictEviction(true)
          .entryCapacity(4711))
        .addCache(b -> b.name("different1").entryCapacity(5001))
        .addCache(b -> b.name("different2").entryCapacity(5002))
        .addCaches(
          b -> b.name("different3").entryCapacity(5003),
          b -> b.name("different4").entryCapacity(5004))
        .addCache("different5", b -> b
          .entryCapacity(5005));
    assertThat(m.isAllowUnknownCache()).isTrue();
    assertThat(m2).isSameAs(m);
    m2 = m.setDefaultCacheNames("def1", "def2");
    assertThat(m.isAllowUnknownCache()).isFalse();
    assertThat(m2).isSameAs(m);
    assertThat(CacheControl.of(m.getCache("def1").getNativeCache())
      .getEntryCapacity()).isEqualTo(4711);
    assertThat(CacheControl.of(m.getCache("different3").getNativeCache())
      .getEntryCapacity()).isEqualTo(5003);
  }

  @Test
  public void destroy() {
    SpringCache2kCacheManager m =
      new SpringCache2kCacheManager(
        getInstance(
          SpringCache2KCacheManagerTest.class.getSimpleName() + "destroy"));
    SpringCache2kCache cache = m.getCache("test1");
    assertThat(cache.getNativeCache().isClosed()).isFalse();
    m.destroy();
    assertThat(cache.getNativeCache().isClosed()).isTrue();
    assertThatCode(() -> m.getCache("hello")).isInstanceOf(IllegalStateException.class);
  }

}
