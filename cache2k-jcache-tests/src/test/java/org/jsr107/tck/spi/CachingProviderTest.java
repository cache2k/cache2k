/**
 *  Copyright 2011-2013 Terracotta, Inc.
 *  Copyright 2011-2013 Oracle, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jsr107.tck.spi;

import org.junit.Test;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * Functional Tests for {@link CachingProvider}s.
 *
 * @author Brian Oliver
 * @see CachingProvider
 */
public class CachingProviderTest {
  private static final ClassLoader CLASS_LOADER = CachingProviderTest.class.getClassLoader();

  @Test
  public void getCacheManagerUsingNulls() {
    CachingProvider provider = Caching.getCachingProvider();
    Properties properties = new Properties();

    try {
      provider.getCacheManager(null, null, null);
    } catch (NullPointerException e) {
      fail();
    }
    try {
      provider.getCacheManager(null, null, properties);
    } catch (NullPointerException e) {
      fail();
    }
    try {
      provider.getCacheManager(null, provider.getDefaultClassLoader(), null);
    } catch (NullPointerException e) {
      fail();
    }
    try {
      provider.getCacheManager(null, provider.getDefaultClassLoader(), properties);
    } catch (NullPointerException e) {
      fail();
    }
    try {
      provider.getCacheManager(provider.getDefaultURI(), null, null);
    } catch (NullPointerException e) {
      fail();
    }
    try {
      provider.getCacheManager(provider.getDefaultURI(), null, properties);
    } catch (NullPointerException e) {
      fail();
    }
    try {
      provider.getCacheManager(provider.getDefaultURI(), provider.getDefaultClassLoader(), null);
    } catch (NullPointerException e) {
      fail();
    }
    try {
      provider.getCacheManager(provider.getDefaultURI(), provider.getDefaultClassLoader(), properties);
    } catch (NullPointerException e) {
      fail();
    }

    try {
      provider.getCacheManager(null, null);
    } catch (NullPointerException e) {
      fail();
    }
    try {
      provider.getCacheManager(provider.getDefaultURI(), null);
    } catch (NullPointerException e) {
      fail();
    }
  }

  @Test
  public void getCacheManagerUsingDefaultURI() {
    CachingProvider provider = Caching.getCachingProvider();

    CacheManager manager1 = provider.getCacheManager();
    assertNotNull(manager1);
    assertEquals(provider.getDefaultURI(), manager1.getURI());

    CacheManager manager2 = provider.getCacheManager();
    assertSame(manager1, manager2);
  }

  @Test
  public void getCacheManagerUsingSameNameAndClassLoader() {
    CachingProvider provider = Caching.getCachingProvider();

    ClassLoader loader = CLASS_LOADER;

    CacheManager manager1 = provider.getCacheManager(provider.getDefaultURI(), loader, null);
    assertNotNull(manager1);

    CacheManager manager2 = provider.getCacheManager(provider.getDefaultURI(), loader, null);
    assertSame(manager1, manager2);
  }

  @Test
  public void getCacheManagerUsingSameURIDifferentClassLoader() {
    CachingProvider provider = Caching.getCachingProvider();

    ClassLoader loader1 = CLASS_LOADER;
    CacheManager manager1 = provider.getCacheManager(provider.getDefaultURI(), loader1, null);
    assertNotNull(manager1);

    ClassLoader loader2 = new MyClassLoader(CLASS_LOADER);
    CacheManager manager2 = provider.getCacheManager(provider.getDefaultURI(), loader2, null);
    assertNotNull(manager2);

    assertNotSame(manager1, manager2);
  }

  @Test
  public void closeCacheManagers() {
    CachingProvider provider = Caching.getCachingProvider();

    ClassLoader loader1 = CLASS_LOADER;
    CacheManager manager1 = provider.getCacheManager(provider.getDefaultURI(), loader1, null);
    manager1.close();

    ClassLoader loader2 = new MyClassLoader(CLASS_LOADER);
    CacheManager manager2 = provider.getCacheManager(provider.getDefaultURI(), loader2, null);
    manager2.close();

    assertNotSame(manager1, provider.getCacheManager(provider.getDefaultURI(), loader1, null));
    assertNotSame(manager2, provider.getCacheManager(provider.getDefaultURI(), loader2, null));
  }

  @Test
  public void closeCachingProvider() {
    CachingProvider provider = Caching.getCachingProvider();

    ClassLoader loader1 = CLASS_LOADER;
    CacheManager manager1 = provider.getCacheManager(provider.getDefaultURI(), loader1, null);

    ClassLoader loader2 = new MyClassLoader(CLASS_LOADER);
    CacheManager manager2 = provider.getCacheManager(provider.getDefaultURI(), loader2, null);

    provider.close();

    assertNotSame(manager1, provider.getCacheManager(provider.getDefaultURI(), loader1, null));
    assertNotSame(manager2, provider.getCacheManager(provider.getDefaultURI(), loader2, null));
  }

  @Test
  public void closeCacheManagerByURIAndClassLoader() {
    CachingProvider provider = Caching.getCachingProvider();

    ClassLoader loader1 = CLASS_LOADER;
    CacheManager manager1 = provider.getCacheManager(provider.getDefaultURI(), loader1, null);

    ClassLoader loader2 = new MyClassLoader(CLASS_LOADER);
    CacheManager manager2 = provider.getCacheManager(provider.getDefaultURI(), loader2, null);

    provider.close(manager1.getURI(), loader1);
    provider.close(manager2.getURI(), loader2);

    assertNotSame(manager1, provider.getCacheManager(provider.getDefaultURI(), loader1, null));
    assertNotSame(manager2, provider.getCacheManager(provider.getDefaultURI(), loader2, null));
  }

  @Test
  public void closeCacheManagersByClassLoader() {
    CachingProvider provider = Caching.getCachingProvider();

    ClassLoader loader1 = CLASS_LOADER;
    CacheManager manager1 = provider.getCacheManager(provider.getDefaultURI(), loader1, null);

    ClassLoader loader2 = new MyClassLoader(CLASS_LOADER);
    CacheManager manager2 = provider.getCacheManager(provider.getDefaultURI(), loader2, null);

    provider.close(loader1);
    provider.close(loader2);

    assertNotSame(manager1, provider.getCacheManager(provider.getDefaultURI(), loader1, null));
    assertNotSame(manager2, provider.getCacheManager(provider.getDefaultURI(), loader2, null));
  }

  private static class MyClassLoader extends ClassLoader {
    public MyClassLoader(ClassLoader parent) {
      super(parent);
    }
  }
}
