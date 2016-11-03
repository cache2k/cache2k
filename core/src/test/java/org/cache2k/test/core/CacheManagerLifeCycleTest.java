package org.cache2k.test.core;

/*
 * #%L
 * cache2k core
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

import net.jcip.annotations.NotThreadSafe;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheManager;
import org.cache2k.junit.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.Assert.*;

/**
 * Test creation and destruction of cache managers.
 *
 * @author Jens Wilke
 */
@NotThreadSafe @Category(FastTests.class)
public class CacheManagerLifeCycleTest {

  @Test
  public void setDefaultMangerName() {
    CacheManager.getInstance().close();
    CacheManager.setDefaultName("hello");
    assertEquals("hello", CacheManager.getInstance().getName());
    assertEquals("hello", CacheManager.getDefaultName());
  }

  @Test(expected = IllegalStateException.class)
  public void setDefaultManagerName_Exception() {
    CacheManager.getInstance();
    CacheManager.setDefaultName("hello");
  }

  @Test
  public void openClose() {
    CacheManager cm = CacheManager.getInstance();
    cm.close();
    CacheManager cm2 = CacheManager.getInstance();
    assertNotSame(cm, cm2);
  }

  @Test
  public void differentClassLoaderDifferentManager() {
    ClassLoader cl1 = new URLClassLoader(new URL[0], this.getClass().getClassLoader());
    ClassLoader cl2 = new URLClassLoader(new URL[0], this.getClass().getClassLoader());
    CacheManager cm1 = CacheManager.getInstance(cl1);
    CacheManager cm2 = CacheManager.getInstance(cl2);
    assertNotSame(cm1, cm2);
    assertFalse(cm1.isClosed());
    assertFalse(cm2.isClosed());
    CacheManager.closeAll(cl1);
    CacheManager.closeAll(cl2);
    assertTrue(cm1.isClosed());
    assertTrue(cm2.isClosed());
  }

  @Test
  public void closeAll() {
    CacheManager cm = CacheManager.getInstance();
    ClassLoader cl1 = new URLClassLoader(new URL[0], this.getClass().getClassLoader());
    ClassLoader cl2 = new URLClassLoader(new URL[0], this.getClass().getClassLoader());
    CacheManager cm1 = CacheManager.getInstance(cl1);
    CacheManager cm2 = CacheManager.getInstance(cl2);
    CacheManager.closeAll();
    assertTrue(cm1.isClosed());
    assertTrue(cm2.isClosed());
    assertTrue(cm.isClosed());
  }

  @Test
  public void closeSpecific() {
    CacheManager.closeAll();
    ClassLoader cl1 = new URLClassLoader(new URL[0], this.getClass().getClassLoader());
    CacheManager cm1 = CacheManager.getInstance(cl1);
    CacheManager.close(null, "something");
    CacheManager.close(cl1, "something");
    assertFalse(cm1.isClosed());
    CacheManager.close(cl1, "default");
  }

  @Test
  public void closesCache() {
    CacheManager.closeAll();
    Cache c = Cache2kBuilder.forUnknownTypes().name("dummy").build();
    c.getCacheManager().close();
    assertTrue(c.isClosed());
  }

  @Test
  public void clearAllCaches() {
    Cache c = Cache2kBuilder.forUnknownTypes().name("dummy").build();
    c.put("hello", "paul");
    assertTrue("has some data", c.keys().iterator().hasNext());
    c.getCacheManager().clear();
    assertFalse("no data", c.keys().iterator().hasNext());
  }

}
