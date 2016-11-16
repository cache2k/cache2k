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
package org.jsr107.tck.integration;

import org.jsr107.tck.testutil.ExcludeListExcluder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Functional Tests for {@link javax.cache.integration.CacheLoader}s with
 * {@link Cache}s that have expiry configured.
 *
 * @author Brian Oliver
 */
public class CacheLoaderWithExpiryTest {

  /**
   * Rule used to exclude tests
   */
  @Rule
  public ExcludeListExcluder rule = new ExcludeListExcluder(CacheLoaderWithExpiryTest.class);

  /**
   * The {@link javax.cache.CacheManager} for the each test.
   */
  private CacheManager cacheManager;

  /**
   * A {@link org.jsr107.tck.integration.CacheLoaderServer} that will delegate {@link javax.cache.Cache} request
   * onto the recording {@link javax.cache.integration.CacheLoader}.
   */
  private CacheLoaderServer<String, String> cacheLoaderServer;

  /**
   * The {@link javax.cache.Cache} for the each test.
   */
  private Cache<String, String> cache;

  /**
   * Establish the {@link javax.cache.CacheManager} and {@link javax.cache.Cache} for a test.
   */
  @Before
  public void onBeforeEachTest() throws IOException {
    //establish and open a CacheLoaderServer to handle cache
    //cache loading requests from a CacheLoaderClient
    cacheLoaderServer = new CacheLoaderServer<String, String>(10000);
    cacheLoaderServer.open();

    //establish the CacheManager for the tests
    cacheManager = Caching.getCachingProvider().getCacheManager();

    //establish a CacheLoaderClient that a Cache can use for loading entries
    //(via the CacheLoaderServer)
    CacheLoaderClient<String, String> cacheLoader =
        new CacheLoaderClient<>(cacheLoaderServer.getInetAddress(), cacheLoaderServer.getPort());

    //establish a Cache Configuration that uses a CacheLoader, Read-Through and Expiry
    MutableConfiguration<String, String> configuration = new MutableConfiguration<>();
    configuration.setTypes(String.class, String.class);
    configuration.setCacheLoaderFactory(FactoryBuilder.factoryOf(cacheLoader));
    configuration.setReadThrough(true);
    configuration.setExpiryPolicyFactory(FactoryBuilder.factoryOf(ExpireOnAccessPolicy.class));

    //configure the cache
    cacheManager.createCache("cache-loader-test", configuration);
    cache = cacheManager.getCache("cache-loader-test", String.class, String.class);
  }

  /**
   * Clean up the {@link javax.cache.CacheManager} and {@link javax.cache.Cache} after a test.
   */
  @After
  public void onAfterEachTest() {
    //destroy the cache
    String cacheName = cache.getName();
    cacheManager.destroyCache(cacheName);

    //close the server
    cacheLoaderServer.close();
    cacheLoaderServer = null;

    cache = null;
  }

  /**
   * Ensure that a {@link javax.cache.Cache#get(Object)} for an expired
   * entry will cause a {@link CacheLoaderClient#load(Object)}.
   */
  @Test
  public void shouldLoadWhenMissCausedByExpiry() {
    RecordingCacheLoader<String> cacheLoader = new RecordingCacheLoader<String>();
    cacheLoaderServer.setCacheLoader(cacheLoader);

    String key = "message";
    assertThat(cache.containsKey(key), is(false));

    String initialValue = "gudday";
    cache.put(key, initialValue);
    assertThat(cache.containsKey(key), is(true));

    String value = cache.get(key);
    assertThat(value, is(initialValue));
    assertThat(cache.containsKey(key), is(false));

    String loadedValue = cache.get(key);
    assertThat(loadedValue, is(equalTo(key)));
    assertThat(cacheLoader.getLoadCount(), is(1));
    assertThat(cacheLoader.hasLoaded(key), is(true));
  }

  /**
   * An {@link ExpiryPolicy} that will expire {@link Cache} entries
   * after they've been accessed.
   */
  public static class ExpireOnAccessPolicy implements ExpiryPolicy
  {
    @Override
    public Duration getExpiryForCreation() {
      return Duration.ETERNAL;
    }

    @Override
    public Duration getExpiryForAccess() {
      return Duration.ZERO;
    }

    @Override
    public Duration getExpiryForUpdate() {
      return Duration.ETERNAL;
    }
  }
}
