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


package org.jsr107.tck.management;

import org.hamcrest.collection.IsEmptyCollection;
import org.jsr107.tck.testutil.ExcludeListExcluder;
import org.jsr107.tck.testutil.TestSupport;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.net.URI;
import java.util.Set;

import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.Assert.assertThat;


/**
 * Tests the Cache  using the platform MBeanServer
 * <p>
 * To examine a typical cache in JConsole, run the main() method and start JConsole. As we only using OpenMBeans there is
 * no need to add any classpath.
 * </p>
 * As the specification states that the MBeanServer in which mBeans are
 * registered is implementation dependent, the TCK needs a way to test MBeans.
 *
 * Implementations must provide a subclass of javax.management.MBeanServerBuilder
 * with an empty constructor.
 *
 * When running the TCK the MBeanServerBuilder
 * is specified using the system property <code>javax.management.builder.initial</code>
 *
 * e.g. for the RI it will be -D javax.management.builder.initial=org.jsr107.ri
 * .RITCKMBeanServerBuilder
 *
 *
 *
 * @author Greg Luck
 */
public class CacheManagerManagementTest {

  private CacheManager cacheManager;
  public static final int EMPTY = 0;

  private MBeanServer mBeanServer;

  MutableConfiguration<Integer, String> configuration = new MutableConfiguration<Integer, String>()
      .setStatisticsEnabled(true)
      .setManagementEnabled(true);

  private Cache<Integer, String> cache1;
  private Cache<Integer, String> cache2;

  @Rule
  public ExcludeListExcluder rule = new ExcludeListExcluder(this.getClass());

  /**
   * Obtains a CacheManager using a string-based name from the default
   * CachingProvider.
   *
   * @return a CacheManager
   * @throws Exception
   */
  public static CacheManager getCacheManager() throws Exception {
    CachingProvider provider = Caching.getCachingProvider();

    URI uri = provider.getDefaultURI();

    return Caching.getCachingProvider().getCacheManager(uri, provider.getDefaultClassLoader());
  }



  /**
   * setup test
   */
  @Before
  public void setUp() throws Exception {

    //ensure that the caching provider is closed
    Caching.getCachingProvider().close();

    //now get a new cache manager
    cacheManager = getCacheManager();

    //make sure the implementation has created an MBeanServer
    cacheManager.createCache("ensure_mbeanserver_created_cache", configuration);
    cacheManager.destroyCache("ensure_mbeanserver_created_cache");

    //lookup the implementation's MBeanServer
    mBeanServer = TestSupport.resolveMBeanServer();


  }

  @After
  public void tearDown() throws MalformedObjectNameException {
    //assertEquals(0, mBeanServer.queryNames(new ObjectName("java.cache:*"), null).size());
    if (!cacheManager.isClosed()) {
      for (String cacheName : cacheManager.getCacheNames()) {
        cacheManager.destroyCache(cacheName);
      }
    }
    cacheManager.close();
    //All registered object names should be removed during shutdown
    assertThat(mBeanServer.queryNames(new ObjectName("javax.cache:*"), null), IsEmptyCollection.<ObjectName>empty());
  }


  @Test
  public void testNoEntriesWhenNoCaches() throws Exception {
    assertThat(mBeanServer.queryNames(new ObjectName("javax.cache:*"), null), hasSize(EMPTY));
  }

  @Test
  public void testJMXGetsCacheAdditionsAndRemovals() throws Exception {
    assertThat(mBeanServer.queryNames(new ObjectName("javax.cache:*"), null), hasSize(EMPTY));
    cacheManager.createCache("new cache", configuration);
    assertThat(mBeanServer.queryNames(new ObjectName("javax.cache:*"), null), hasSize(2));
    //name does not exist so no change
    cacheManager.destroyCache("sampleCache1");
    assertThat(mBeanServer.queryNames(new ObjectName("javax.cache:*"), null), hasSize(2));
    //correct name, should get removed.
    cacheManager.destroyCache("new cache");
    assertThat(mBeanServer.queryNames(new ObjectName("javax.cache:*"), null), hasSize(EMPTY));
  }

  @Test
  public void testMultipleCacheManagers() throws Exception {
    cacheManager.createCache("new cache", configuration);
    assertThat(mBeanServer.queryNames(new ObjectName("javax.cache:*"), null), hasSize(2));

    CacheManager cacheManager2 = getCacheManager();
    cacheManager2.createCache("other cache", configuration);

    assertThat(mBeanServer.queryNames(new ObjectName("javax.cache:*"), null), hasSize(4));
    cacheManager.destroyCache("new cache");
    cacheManager2.destroyCache("other cache");
    cacheManager2.close();
  }

  @Test
  public void testDoubleRegistration() throws MalformedObjectNameException {
    cacheManager.createCache("new cache", configuration);
    assertThat(mBeanServer.queryNames(new ObjectName("javax.cache:*"), null), hasSize(2));

    cacheManager.enableStatistics("new cache", true);
    assertThat(mBeanServer.queryNames(new ObjectName("javax.cache:*"), null), hasSize(2));
  }


  @Test
  public void testCacheStatisticsOffThenOnThenOff() throws Exception {
    MutableConfiguration configuration = new MutableConfiguration();
    configuration.setStatisticsEnabled(false);
    cacheManager.createCache("cache1", configuration);
    cacheManager.createCache("cache2", configuration);
    Set<? extends ObjectName> names = mBeanServer.queryNames(new ObjectName("javax.cache:*"), null);
    Assert.assertTrue(names.size() == 0);

    configuration.setStatisticsEnabled(true);
    cacheManager.createCache("cache3", configuration);
    cacheManager.createCache("cache4", configuration);
    assertThat(mBeanServer.queryNames(new ObjectName("javax.cache:*"), null), hasSize(2));

    cacheManager.enableStatistics("cache3", false);
    assertThat(mBeanServer.queryNames(new ObjectName("javax.cache:*"), null), hasSize(1));

    cacheManager.enableStatistics("cache3", true);
    assertThat(mBeanServer.queryNames(new ObjectName("javax.cache:*"), null), hasSize(2));
  }

  @Test
  public void testCacheManagementOffThenOnThenOff() throws Exception {
    MutableConfiguration configuration = new MutableConfiguration();
    configuration.setManagementEnabled(false);
    cacheManager.createCache("cache1", configuration);
    cacheManager.createCache("cache2", configuration);
    Set<? extends ObjectName> names = mBeanServer.queryNames(new ObjectName("javax.cache:*"), null);
    Assert.assertTrue(names.size() == 0);

    configuration.setManagementEnabled(true);
    cacheManager.createCache("cache3", configuration);
    cacheManager.createCache("cache4", configuration);
    assertThat(mBeanServer.queryNames(new ObjectName("javax.cache:*"), null), hasSize(2));

    cacheManager.enableManagement("cache3", false);
    assertThat(mBeanServer.queryNames(new ObjectName("javax.cache:*"), null), hasSize(1));


    cacheManager.enableManagement("cache3", true);
    assertThat(mBeanServer.queryNames(new ObjectName("javax.cache:*"), null), hasSize(2));
  }
}

