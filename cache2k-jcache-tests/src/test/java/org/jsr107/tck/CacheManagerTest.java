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
import org.jsr107.tck.testutil.TestSupport;
import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import javax.cache.Cache;
import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for CacheManager
 *
 * @author Yannis Cosmadopoulos
 * @since 1.0
 */
public class CacheManagerTest extends TestSupport {
  protected final Logger logger = Logger.getLogger(getClass().getName());

  /**
   * Rule used to exclude tests
   */
  @Rule
  public ExcludeListExcluder rule = new ExcludeListExcluder(this.getClass()) {

    /* (non-Javadoc)
     * @see javax.cache.util.ExcludeListExcluder#isExcluded(java.lang.String)
     */
    @Override
    protected boolean isExcluded(String methodName) {
      if ("testUnwrap".equals(methodName) && getUnwrapClass(CacheManager.class) == null) {
        return true;
      }

      return super.isExcluded(methodName);
    }
  };

  @Before
  public void startUp() {
    try {
      Caching.getCachingProvider().close();
    } catch (CacheException e) {
      //this will happen if we call close twice in a row.
    }
  }


  @After
  public void teardown() {
    CacheManager cacheManager = getCacheManager();
    for (String cacheName : cacheManager.getCacheNames()) {
      cacheManager.destroyCache(cacheName);
    }
    cacheManager.close();
  }

  @Test
  public void getOrCreateCache_NullCacheName() {
    CacheManager cacheManager = getCacheManager();
    try {
      cacheManager.createCache(null, new MutableConfiguration());
      fail("should have thrown an exception - null cache name not allowed");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void createCache_NullCacheName() {
    CacheManager cacheManager = getCacheManager();
    try {
      cacheManager.createCache(null, new MutableConfiguration());
      fail("should have thrown an exception - null cache name not allowed");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void getOrCreateCache_NullCacheConfiguration() {
    CacheManager cacheManager = getCacheManager();
    try {
      cacheManager.createCache("cache", null);
      fail("should have thrown an exception - null cache configuration not allowed");
    } catch (NullPointerException e) {
      //good
    }
  }

@Test
  public void createCache_NullCacheConfiguration() {
    CacheManager cacheManager = getCacheManager();
    try {
      cacheManager.createCache("cache", null);
      fail("should have thrown an exception - null cache configuration not allowed");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void getOrCreateCache_Same() {
    String name = "c1";
    CacheManager cacheManager = getCacheManager();
    cacheManager.createCache(name, new MutableConfiguration());
    Cache cache = cacheManager.getCache(name);
    assertSame(cache, cacheManager.getCache(name));
  }

  @Test
  public void createCache_Same() {
    String name = "c1";
    CacheManager cacheManager = getCacheManager();
    try {
      cacheManager.createCache(name, new MutableConfiguration());
      Cache cache1 = cacheManager.getCache(name);
      cacheManager.createCache(name, new MutableConfiguration());
      Cache cache2 = cacheManager.getCache(name);
      fail();
    } catch (CacheException exception) {
      //expected
    }
  }

  @Test
  public void testReuseCacheManager() throws Exception {
    CachingProvider provider = Caching.getCachingProvider();
    URI uri = provider.getDefaultURI();

    CacheManager cacheManager = provider.getCacheManager(uri, provider.getDefaultClassLoader());
    assertFalse(cacheManager.isClosed());
    cacheManager.close();
    assertTrue(cacheManager.isClosed());

    try {
      cacheManager.createCache("Dog", new MutableConfiguration());
      fail();
    } catch (IllegalStateException e) {
      //expected
    }

    CacheManager otherCacheManager = provider.getCacheManager(uri, provider.getDefaultClassLoader());
    assertFalse(otherCacheManager.isClosed());

    assertNotSame(cacheManager, otherCacheManager);
  }

  @Test
  public void testReuseCacheManagerGetCache() throws Exception {
    CachingProvider provider = Caching.getCachingProvider();
    URI uri = provider.getDefaultURI();

    CacheManager cacheManager = provider.getCacheManager(uri, provider.getDefaultClassLoader());
    assertFalse(cacheManager.isClosed());
    cacheManager.close();
    assertTrue(cacheManager.isClosed());

    try {
      cacheManager.getCache("nonExistent", Integer.class, Integer.class);
      fail();
    } catch (IllegalStateException e) {
      //expected
    }

    CacheManager otherCacheManager = provider.getCacheManager(uri, provider.getDefaultClassLoader());
    assertFalse(otherCacheManager.isClosed());

    assertNotSame(cacheManager, otherCacheManager);
  }


  @Test
  public void getOrCreateCache_NameOK() {
    String name = "c1";
    getCacheManager().createCache(name, new MutableConfiguration());
    Cache cache = getCacheManager().getCache(name);
    assertEquals(name, cache.getName());
  }

  @Test
  public void createCache_NameOK() {
    String name = "c1";
    getCacheManager().createCache(name, new MutableConfiguration());
    Cache cache = getCacheManager().getCache(name);
    assertEquals(name, cache.getName());
  }

  @Test
  public void cachingProviderGetCache() {
    String name = "c1";
    getCacheManager().createCache(name, new MutableConfiguration().setTypes(Long.class, String.class));
    Cache cache = Caching.getCache(name, Long.class, String.class);
    assertEquals(name, cache.getName());
  }


  @Test
  public void getOrCreateCache_StatusOK() {
    String name = "c1";
    getCacheManager().createCache(name, new MutableConfiguration());
    Cache cache = getCacheManager().getCache(name);
    assertNotNull(cache);
    assertEquals(name, cache.getName());
  }

  @Test
  public void getOrCreateCache_Different() {
    String name1 = "c1";
    CacheManager cacheManager = getCacheManager();
    cacheManager.createCache(name1, new MutableConfiguration());
    Cache cache1 = cacheManager.getCache(name1);

    String name2 = "c2";
    cacheManager.createCache(name2, new MutableConfiguration());
    Cache cache2 = cacheManager.getCache(name2);

    assertEquals(cache1, cacheManager.getCache(name1));
    assertEquals(cache2, cacheManager.getCache(name2));
  }

  @Test
  public void createCacheSameName() {
    CacheManager cacheManager = getCacheManager();
    String name1 = "c1";
    cacheManager.createCache(name1, new MutableConfiguration());
    Cache cache1 = cacheManager.getCache(name1);
    assertEquals(cache1, cacheManager.getCache(name1));
    ensureOpen(cache1);

    try {
      cacheManager.createCache(name1, new MutableConfiguration());
    } catch (CacheException e) {
      //expected
    }
    Cache cache2 = cacheManager.getCache(name1);
  }

  @Test
  public void removeCache_Null() {
    CacheManager cacheManager = getCacheManager();
    try {
      cacheManager.destroyCache(null);
      fail("should have thrown an exception - cache name null");
    } catch (NullPointerException e) {
      //good
    }
  }

  @Test
  public void removeCache_There() {
    CacheManager cacheManager = getCacheManager();
    String name1 = "c1";
    cacheManager.createCache(name1, new MutableConfiguration());
    cacheManager.destroyCache(name1);
    assertFalse(cacheManager.getCacheNames().iterator().hasNext());
  }

  @Test
  public void removeCache_CacheStopped() {
    CacheManager cacheManager = getCacheManager();
    String name1 = "c1";
    cacheManager.createCache(name1, new MutableConfiguration());
    Cache cache1 = cacheManager.getCache(name1);
    cacheManager.destroyCache(name1);
    ensureClosed(cache1);
  }

  @Test
  public void removeCache_NotThere() {
    CacheManager cacheManager = getCacheManager();
    cacheManager.destroyCache("c1");
  }

  @Test
  public void removeCache_Stopped() {
    CacheManager cacheManager = getCacheManager();
    cacheManager.close();
    try {
      cacheManager.destroyCache("c1");
      fail();
    } catch (IllegalStateException e) {
      //ok
    }
  }

  @Test
  public void close_cachesClosed() {
    CacheManager cacheManager = getCacheManager();

    cacheManager.createCache("c1", new MutableConfiguration());
    Cache cache1 = cacheManager.getCache("c1");
    cacheManager.createCache("c2", new MutableConfiguration());
    Cache cache2 = cacheManager.getCache("c2");

    cacheManager.close();

    ensureClosed(cache1);
    ensureClosed(cache2);
  }

  @Test
  public void close() {
    CacheManager cacheManager = getCacheManager();

    assertFalse(cacheManager.isClosed());
    cacheManager.close();
    assertTrue(cacheManager.isClosed());
  }

  @Test
  public void close_twice() {
    CacheManager cacheManager = getCacheManager();

    cacheManager.close();
    cacheManager.close();
  }

  @Test
  public void close_cachesEmpty() {
    CacheManager cacheManager = getCacheManager();

    cacheManager.createCache("c1", new MutableConfiguration());
    cacheManager.createCache("c2", new MutableConfiguration());

    cacheManager.close();
    try {
      cacheManager.getCacheNames();
      fail();
    } catch (IllegalStateException e) {
      //expected
    }
  }

  @Test
  public void getCache_Missing() {
    CacheManager cacheManager = getCacheManager();
    assertNull(cacheManager.getCache("notThere"));
  }

  @Test
  public void getCache_There() {
    String name = this.toString();
    CacheManager cacheManager = getCacheManager();
    cacheManager.createCache(name, new MutableConfiguration());
    Cache cache = cacheManager.getCache(name);
    assertSame(cache, cacheManager.getCache(name));
  }

  @Test
  public void getCache_Missing_Stopped() {
    CacheManager cacheManager = getCacheManager();
    cacheManager.close();
    try {
      cacheManager.getCache("notThere");
      fail();
    } catch (IllegalStateException e) {
      //good
    }
  }

  @Test(expected = IllegalStateException.class)
  public void enableStatistics_managerStopped() {
    CacheManager cacheManager = getCacheManager();
    cacheManager.close();
    cacheManager.enableStatistics("notThere", true);
    fail();
  }

  @Test(expected = IllegalStateException.class)
  public void enableManagement_managerStopped() {
    CacheManager cacheManager = getCacheManager();
    cacheManager.close();
    cacheManager.enableManagement("notThere", true);
    fail();
  }

  @Test(expected = NullPointerException.class)
  public void enableStatistics_nullCacheName() {
    CacheManager cacheManager = getCacheManager();
    final String NULL_CACHE_NAME = null;
    cacheManager.enableStatistics(NULL_CACHE_NAME, true);
    fail();
  }

  @Test(expected = NullPointerException.class)
  public void enableManagement_nullCacheName() {
    CacheManager cacheManager = getCacheManager();
    final String NULL_CACHE_NAME = null;
    cacheManager.enableManagement(NULL_CACHE_NAME, true);
    fail();
  }

  @Test(expected = IllegalArgumentException.class)
  public void unwrapThrowsInvalidArgument() {
    final Class ALWAYS_INVALID_UNWRAP_CLASS = Exception.class;
    getCacheManager().unwrap(Exception.class);
    fail();
  }

  @Test
  public void getCache_There_Stopped() {
    String name = this.toString();
    CacheManager cacheManager = getCacheManager();
    cacheManager.createCache(name, new MutableConfiguration());
    cacheManager.close();
    try {
      cacheManager.getCache(name);
      fail();
    } catch (IllegalStateException e) {
      //good
    }
  }

  @Test
  public void getCaches_Empty() {
    CacheManager cacheManager = getCacheManager();
    assertFalse(cacheManager.getCacheNames().iterator().hasNext());
  }

  @Test
  public void getCaches_NotEmpty() {
    CacheManager cacheManager = getCacheManager();

    ArrayList<String> caches1 = new ArrayList<String>();
    cacheManager.createCache("c1", new MutableConfiguration());
    cacheManager.createCache("c2", new MutableConfiguration());
    cacheManager.createCache("c3", new MutableConfiguration());
    caches1.add(cacheManager.getCache("c1").getName());
    caches1.add(cacheManager.getCache("c2").getName());
    caches1.add(cacheManager.getCache("c3").getName());

    checkCollections(caches1, cacheManager.getCacheNames());
  }

  @Test
  public void getCaches_MutateReturn() {
    CacheManager cacheManager = getCacheManager();

    cacheManager.createCache("c1", new MutableConfiguration());
    Cache cache1 = cacheManager.getCache("c1");

    try {
      Iterator iterator = cacheManager.getCacheNames().iterator();
      iterator.next();
      iterator.remove();
      fail();
    } catch (UnsupportedOperationException e) {
      // immutable
    }
  }

  @Test
  public void getCaches_MutateCacheManager() {
    CacheManager cacheManager = getCacheManager();

    String removeName = "c2";
    ArrayList<String> cacheNames1 = new ArrayList<String>();
    cacheManager.createCache("c1", new MutableConfiguration());
    Cache c1 = cacheManager.getCache("c1");
    cacheNames1.add(c1.getName());
    cacheManager.createCache(removeName, new MutableConfiguration());
    cacheManager.createCache("c3", new MutableConfiguration());
    Cache c3 = cacheManager.getCache("c3");
    cacheNames1.add(c3.getName());

    Iterable<String> cacheNames;
    int size;

    cacheNames = cacheManager.getCacheNames();
    size = 0;
    for (String cacheName : cacheNames) {
      size++;
    }
    assertEquals(3, size);
    cacheManager.destroyCache(removeName);
    size = 0;
    for (String cacheName : cacheNames) {
      size++;
    }
    assertEquals(3, size);

    cacheNames = cacheManager.getCacheNames();
    size = 0;
    for (String cacheName : cacheNames) {
      size++;
    }
    assertEquals(2, size);
    checkCollections(cacheNames1, cacheNames);
  }

  @Test
  public void getUntypedCache() {
    CacheManager cacheManager = getCacheManager();

    //configure an un-typed Cache
    MutableConfiguration config = new MutableConfiguration();

    cacheManager.createCache("untyped-cache", config);

    Cache cache = cacheManager.getCache("untyped-cache");

    assertNotNull(cache);
    assertEquals(Object.class, cache.getConfiguration(CompleteConfiguration.class).getKeyType());
    assertEquals(Object.class, cache.getConfiguration(CompleteConfiguration.class).getValueType());
  }

  @Test
  public void getTypedCache() {
    CacheManager cacheManager = getCacheManager();

    MutableConfiguration<String, Long> config = new MutableConfiguration<String, Long>().setTypes(String.class, Long.class);

    cacheManager.createCache("typed-cache", config);

    Cache<String, Long> cache = cacheManager.getCache("typed-cache", String.class, Long.class);

    assertNotNull(cache);
    assertEquals(String.class, cache.getConfiguration(CompleteConfiguration.class).getKeyType());
    assertEquals(Long.class, cache.getConfiguration(CompleteConfiguration.class).getValueType());
  }

  @Test(expected = ClassCastException.class)
  public void getIncorrectCacheType() {
    CacheManager cacheManager = getCacheManager();

    MutableConfiguration<String, Long> config = new MutableConfiguration<String, Long>().setTypes(String.class, Long.class);

    cacheManager.createCache("typed-cache", config);

    Cache<Long, String> cache = cacheManager.getCache("typed-cache", Long.class, String.class);
  }

  @Test(expected = ClassCastException.class)
  public void getIncorrectCacheValueType() {
    CacheManager cacheManager = getCacheManager();

    MutableConfiguration<String, Long> config = new MutableConfiguration<String, Long>().setTypes(String.class, Long.class);

    cacheManager.createCache("typed-cache", config);

    Cache<String, String> cache = cacheManager.getCache("typed-cache", String.class, String.class);
  }

  /**
   * https://github.com/jsr107/jsr107spec/issues/340
   * in 1.1 we relaxed {@link CacheManager#getCache(String)} to not enforce a check.
   */
  @Test
  public void getUnsafeTypedCacheRequest() {
    CacheManager cacheManager = getCacheManager();

    MutableConfiguration<String, Long> config = new MutableConfiguration<String, Long>().setTypes(String.class, Long.class);

    cacheManager.createCache("typed-cache", config);

    Cache cache = cacheManager.getCache("typed-cache");
  }

  @Test(expected = NullPointerException.class)
  public void getNullTypeCacheRequest() {
    CacheManager cacheManager = getCacheManager();

    MutableConfiguration config = new MutableConfiguration();

    cacheManager.createCache("untyped-cache", config);

    Cache cache = cacheManager.getCache("untyped-cache", null, null);
  }

  @Test
  public void testUnwrap() {
    //Assumes rule will exclude this test when no unwrapClass is specified
    final Class<?> unwrapClass = getUnwrapClass(CacheManager.class);
    final CacheManager cacheManager = getCacheManager();
    final Object unwrappedCacheManager = cacheManager.unwrap(unwrapClass);

    assertTrue(unwrapClass.isAssignableFrom(unwrappedCacheManager.getClass()));
  }

  // ---------- utilities ----------

  private <T> void checkCollections(Collection<T> collection1, Iterable<?> iterable2) {
    ArrayList<Object> collection2 = new ArrayList<Object>();
    for (Object element : iterable2) {
      assertTrue(collection1.contains(element));
      collection2.add(element);
    }
    assertEquals(collection1.size(), collection2.size());
    for (T element : collection1) {
      assertTrue(collection2.contains(element));
    }
  }

  private void ensureOpen(Cache cache) {
    if (cache.isClosed()) {
      fail();
    }
  }

  private void ensureClosed(Cache cache) {
    if (!cache.isClosed()) {
      fail();
    }
  }
}
