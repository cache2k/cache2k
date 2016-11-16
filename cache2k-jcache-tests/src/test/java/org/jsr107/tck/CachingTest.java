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
package org.jsr107.tck;

import org.jsr107.tck.testutil.ExcludeListExcluder;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import javax.cache.Cache;
import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.configuration.OptionalFeature;
import javax.cache.spi.CachingProvider;
import java.net.URI;
import java.util.Properties;
import java.util.logging.Logger;

import static junit.framework.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * Tests the {@link Caching} class.
 * The tests here implicitly also test the {@link javax.cache.spi.CachingProvider} used by
 * Caching to create instances of {@link CacheManager}
 *
 * @author Yannis Cosmadopoulos
 * @see Caching
 * @since 1.0
 */
public class CachingTest {


  /**
   * Rule used to exclude tests
   */
  @Rule
  public ExcludeListExcluder rule = new ExcludeListExcluder(this.getClass());

  @Test
  public void getCachingProviderSingleton() {
    CachingProvider provider1 = Caching.getCachingProvider();
    CachingProvider provider2 = Caching.getCachingProvider();

    Assert.assertEquals(provider1, provider2);
  }



  /**
   * Multiple invocations of {@link CachingProvider#getCacheManager()} return the same CacheManager
   */
  @Test
  public void getCacheManager_singleton() {
    CachingProvider provider = Caching.getCachingProvider();

    CacheManager manager = provider.getCacheManager();
    manager.getProperties();
    assertNotNull(manager);
    assertSame(manager, provider.getCacheManager());
  }

  @Test
  public void getCacheManager_defaultURI() {
    CachingProvider provider = Caching.getCachingProvider();

    assertSame(provider.getCacheManager(),
        provider.getCacheManager(provider.getDefaultURI(), provider.getDefaultClassLoader()));

    CacheManager manager = provider.getCacheManager();
    assertEquals(provider.getDefaultURI(), manager.getURI());
  }

  // https://github.com/jsr107/jsr107tck/issues/102
  @Test
  public void getCacheManager_nonNullProperties() {
    // make sure existing cache managers are closed and the non empty properties get picked up
    try {
      Caching.getCachingProvider().close();
    } catch (CacheException ignore) {
      // ignore exception which may happen if the provider is not active
    }
    CachingProvider provider = Caching.getCachingProvider();
    Properties properties = new Properties();
    properties.put("dummy.com", "goofy");
    provider.getCacheManager(provider.getDefaultURI(), provider.getDefaultClassLoader(), properties);
    CacheManager manager = provider.getCacheManager();
    assertEquals(properties, manager.getProperties());
  }

  /**
   * Multiple invocations of {@link CachingProvider#getCacheManager(java.net.URI, ClassLoader)} with the same name
   * return the same CacheManager instance
   */
  @Test
  public void getCacheManager_URI() throws Exception {
    CachingProvider provider = Caching.getCachingProvider();

    URI uri = provider.getDefaultURI();

    CacheManager manager = provider.getCacheManager(uri, provider.getDefaultClassLoader());
    assertNotNull(manager);
    assertSame(manager, provider.getCacheManager(uri, provider.getDefaultClassLoader()));

    assertEquals(uri, manager.getURI());
  }

  @Test
  public void getCacheManager_nullUriParameter() {
    CachingProvider provider = Caching.getCachingProvider();
    final URI NULL_URI = null;
    CacheManager manager = provider.getCacheManager(NULL_URI, provider.getDefaultClassLoader(), null);
    assertNotNull(manager);
    assertEquals(provider.getDefaultURI(), manager.getURI());
  }

  @Test
  public void getCacheManager_nullClassLoader() {
    CachingProvider provider = Caching.getCachingProvider();
    final ClassLoader NULL_CLASSLOADER = null;

    // null classloader is treated as provider.getDefaultClassLoader().
    CacheManager manager = provider.getCacheManager(provider.getDefaultURI(), NULL_CLASSLOADER, null);
    assertNotNull(manager);
    CacheManager sameManager = provider.getCacheManager(provider.getDefaultURI(), provider.getDefaultClassLoader(), null);
    assertEquals(sameManager, manager);
    assertEquals(sameManager.getClassLoader(), manager.getClassLoader());
  }

  @Test
  public void isSupported() {
    CachingProvider provider = Caching.getCachingProvider();

    OptionalFeature[] features = OptionalFeature.values();
    for (OptionalFeature feature : features) {
      boolean value = provider.isSupported(feature);
      Logger.getLogger(getClass().getName()).info("Optional feature " + feature + " supported=" + value);
    }
  }


  @Test
  public void cachingProviderGetCache() {
    String name = "c1";
    Caching.getCachingProvider().getCacheManager().createCache(name, new MutableConfiguration().setTypes(Long.class, String.class));
    Cache cache = Caching.getCache(name, Long.class, String.class);
    assertEquals(name, cache.getName());
    Caching.getCachingProvider().getCacheManager().destroyCache(name);
  }

  @Test
  public void cachingProviderGetNonExistentCache() {
    String name = "nonExistentCache";
    Cache cache = Caching.getCache(name, Long.class, String.class);
    assertNull(null, cache);
  }

  @Test(expected = NullPointerException.class)
  public void getCacheNullValueClass() {
    String name = "c1";
    CacheManager manager = Caching.getCachingProvider().getCacheManager();
    manager.createCache(name, new MutableConfiguration().setTypes(Long.class, String.class));
    try {
      Caching.getCache(name, Long.class, null);
    } finally {
      manager.destroyCache(name);
    }
  }

  /**
   * DO NOT DELETE THIS
   * It is skipped by implementation-test/src/test/resources/ExcludeList
   * Used to confirm that file exclusion works.
   * It will fail when run from the IDE.
   * @see #rule
   */
  @Test
  public void dummyTest() {
    fail();
  }

}
