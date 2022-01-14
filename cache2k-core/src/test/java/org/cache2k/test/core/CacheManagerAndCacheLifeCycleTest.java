package org.cache2k.test.core;

/*-
 * #%L
 * cache2k core implementation
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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheManager;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.net.URL;
import java.net.URLClassLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cache2k.Cache2kBuilder.forUnknownTypes;
import static org.cache2k.Cache2kBuilder.of;
import static org.cache2k.CacheManager.*;
import static org.cache2k.test.core.StaticUtil.count;
import static org.cache2k.test.core.StaticUtil.enforceWiredCache;
import static org.junit.Assert.*;

/**
 * Test creation and destruction of cache managers.
 *
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class CacheManagerAndCacheLifeCycleTest {

  @Test(expected = IllegalStateException.class)
  public void setDefaultManagerName_Exception() {
    CacheManager.getInstance();
    CacheManager.setDefaultName("hello");
  }

  @Test
  public void openClose() {
    String uniqueName = this.getClass().getName() + ".openClose";
    CacheManager cm = CacheManager.getInstance(uniqueName);
    cm.close();
    CacheManager cm2 = CacheManager.getInstance(uniqueName);
    assertNotSame(cm, cm2);
    cm2.close();
  }

  @Test
  public void differentClassLoaderDifferentManager() {
    getInstance();
    ClassLoader cl1 = new URLClassLoader(new URL[0], this.getClass().getClassLoader());
    ClassLoader cl2 = new URLClassLoader(new URL[0], this.getClass().getClassLoader());
    CacheManager cm1 = getInstance(cl1);
    CacheManager cm2 = getInstance(cl2);
    assertNotSame(cm1, cm2);
    assertThat(cm1.isClosed()).isFalse();
    assertThat(cm2.isClosed()).isFalse();
    Cache c1 = forUnknownTypes().manager(cm1).build();
    Cache c2 = forUnknownTypes().manager(cm2).build();
    closeAll(cl1);
    closeAll(cl2);
    assertThat(cm1.isClosed()).isTrue();
    assertThat(cm2.isClosed()).isTrue();
    assertThat(c1.isClosed()).isTrue();
    assertThat(c2.isClosed()).isTrue();
  }

  @Test
  public void closeSpecific() {
    CacheManager cm0 = getInstance();
    ClassLoader cl1 = new URLClassLoader(new URL[0], this.getClass().getClassLoader());
    CacheManager cm1 = getInstance(cl1);
    assertThat(cm1.getClassLoader()).isNotEqualTo(cm0.getClassLoader());
    close(cl1, "something");
    assertThat(cm1.isClosed()).isFalse();
    close(cl1, "default");
    assertThat(cm1.isClosed()).isTrue();
  }

  @Test
  public void closesCache() {
    String uniqueName = this.getClass().getName() + ".closesCache";
    CacheManager cm = getInstance(uniqueName);
    Cache c = forUnknownTypes()
      .manager(cm)
      .name("dummy")
      .build();
    assertThat(c.getCacheManager()).isSameAs(cm);
    assertThat(cm.toString().contains(", activeCaches=[dummy]")).isTrue();
    cm.close();
    String s = cm.toString();
    assertThat(s.contains(", closed=true")).isTrue();
    assertThat(s.contains(", objectId=")).isTrue();
    assertThat(s.contains("name='" + uniqueName)).isTrue();
    assertThat(c.isClosed()).isTrue();
  }

  @Test
  public void clearAllCaches() {
    String uniqueName = this.getClass().getName() + ".clearAllCaches";
    CacheManager cm = getInstance(uniqueName);
    Cache c = forUnknownTypes()
      .manager(cm)
      .name("dummy")
      .build();
    c.put("hello", "paul");
    assertThat(c.keys().iterator().hasNext())
      .as("has some data")
      .isTrue();
    c.getCacheManager().clear();
    assertThat(c.keys().iterator().hasNext())
      .as("no data")
      .isFalse();
    cm.close();
  }

  @Test
  public void createCache() {
    String uniqueName = this.getClass().getName() + ".createCache";
    CacheManager cm = getInstance(uniqueName);
    Cache c = cm.createCache(forUnknownTypes().name("dummy").config());
    assertThat(c.getName()).isEqualTo("dummy");
    assertThat(c.getCacheManager()).isSameAs(cm);
    cm.close();
  }

  @Test
  public void getActiveCaches() {
    String uniqueName = this.getClass().getName() + ".getActiveCaches";
    CacheManager cm = getInstance(uniqueName);
    assertThat(cm.getActiveCaches().iterator().hasNext()).isFalse();
    Cache c = forUnknownTypes().manager(cm).build();
    assertThat(cm.getActiveCaches().iterator().hasNext()).isTrue();
    cm.close();
  }

  @Test
  public void onlyOneCacheForWired() {
    String uniqueName = this.getClass().getName() + ".onlyOneCacheForWired";
    CacheManager cm = getInstance(uniqueName);
    Cache2kBuilder b = forUnknownTypes().manager(cm);
    enforceWiredCache(b);
    Cache c = b.build();
    assertThat(count(cm.getActiveCaches()))
      .as("one cache active")
      .isEqualTo(1);
    cm.close();
  }

  @Test
  public void testToStringAnon() {
    Cache<Integer, Integer> c =
      Cache2kBuilder.of(Integer.class, Integer.class)
        .eternal(true)
        .build();
    c.toString();
    c.close();
  }

  @Test
  public void testToString() {
    Cache<Integer, Integer> c =
      of(Integer.class, Integer.class)
        .name("testToString")
        .eternal(true)
        .build();
    assertThat(c.toString()).contains("Cache(name='testToString', size");
    c.close();
    assertThat(c.toString()).contains("Cache(name='testToString', closed=true");
  }

  @Test
  public void testToStringWithManager() {
    String managerName = this.getClass().getSimpleName();
    CacheManager cm = getInstance(managerName);
    Cache<Integer, Integer> c =
      of(Integer.class, Integer.class)
        .manager(cm)
        .name("testToString")
        .eternal(true)
        .build();
    assertThat(c.toString()).contains("Cache(name='testToString', manager='" + managerName + "'");
    c.close();
    cm.close();
    assertThat(c.toString()).contains("Cache(name='testToString', manager='" + managerName + "'");
  }

}
