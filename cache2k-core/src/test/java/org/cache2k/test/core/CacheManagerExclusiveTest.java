package org.cache2k.test.core;

/*
 * #%L
 * cache2k implementation
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

import org.cache2k.CacheManager;
import org.cache2k.testing.category.ExclusiveTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests on the manager that need to run exclusively on the VM.
 *
 * @author Jens Wilke
 */
@Category(ExclusiveTests.class)
public class CacheManagerExclusiveTest {

  @Test
  public void setDefaultMangerName() {
    CacheManager.getInstance().close();
    CacheManager.setDefaultName("hello");
    assertEquals("hello", CacheManager.getInstance().getName());
    assertEquals("hello", CacheManager.getDefaultName());
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

}
