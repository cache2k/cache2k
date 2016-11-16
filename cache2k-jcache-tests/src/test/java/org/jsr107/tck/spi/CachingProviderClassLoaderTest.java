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

import org.jsr107.tck.testutil.ExcludeListExcluder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

/**
 * Functional Tests for CachingProvider ClassLoader isolation.
 *
 * @author Brian Oliver
 * @author Yannis Cosmadopoulos
 * @see CachingProvider
 * @since 1.0
 */
public class CachingProviderClassLoaderTest {

  /**
   * Rule used to exclude tests
   */
  @Rule
  public ExcludeListExcluder rule = new ExcludeListExcluder(this.getClass());

  @Before
  public void startUp() {
    //ensure that there are no open CacheManagers for the CachingProvider
    Caching.getCachingProvider().close();
  }

  /**
   * Multiple invocations of {@link javax.cache.spi.CachingProvider#getCacheManager()}
   * will return the same instance.
   */
  @Test
  public void getCacheManagerSingleton() {

    // obtain the default caching provider
    CachingProvider provider = Caching.getCachingProvider();

    // obtain the default class loader
    ClassLoader classLoader = Caching.getDefaultClassLoader();

    // obtain the default cache manager
    CacheManager manager = provider.getCacheManager();

    assertNotNull(classLoader);
    assertNotNull(manager);

    // ensure the default manager is the same as asking the provider
    // for a cache manager using the default URI and classloader
    assertSame(manager, provider.getCacheManager());
    assertSame(manager, provider.getCacheManager(provider.getDefaultURI(), provider.getDefaultClassLoader()));

    // using a different ClassLoader
    ClassLoader otherLoader = new MyClassLoader(classLoader);
    CachingProvider otherProvider = Caching.getCachingProvider(otherLoader);

    CacheManager otherManager = otherProvider.getCacheManager();
    assertSame(otherManager, otherProvider.getCacheManager());
    assertSame(otherManager, otherProvider.getCacheManager(otherProvider.getDefaultURI(), otherProvider.getDefaultClassLoader()));
  }

  /**
   * The default CacheManager is the same as the CacheManager using the default
   * CachingProvider URI.
   */
  @Test
  public void getCacheManagerDefaultURI() {
    ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();

    CachingProvider provider = Caching.getCachingProvider(contextLoader);

    CacheManager manager = provider.getCacheManager();
    assertEquals(provider.getDefaultURI(), manager.getURI());

    // using a different ClassLoader
    ClassLoader otherLoader = new MyClassLoader(contextLoader);
    CachingProvider otherProvider = Caching.getCachingProvider(otherLoader);
    assertNotSame(provider, otherProvider);

    CacheManager otherManager = otherProvider.getCacheManager();
    assertNotSame(manager, otherManager);
    assertEquals(otherProvider.getDefaultURI(), otherManager.getURI());
  }

  /**
   * The URI of the CacheManager returned by {@link CachingProvider#getCacheManager(java.net.URI, ClassLoader)}
   * is the same as the URI used in the invocation.
   */
  @Test
  public void getCacheManagerSameURI() {
    ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();

    CachingProvider provider = Caching.getCachingProvider(contextLoader);
    URI uri = provider.getDefaultURI();

    CacheManager manager = provider.getCacheManager(uri, contextLoader);
    assertEquals(uri, manager.getURI());

    // using a different ClassLoader
    ClassLoader otherLoader = new MyClassLoader(contextLoader);
    CachingProvider otherProvider = Caching.getCachingProvider(otherLoader);
    assertNotSame(provider, otherProvider);

    CacheManager otherManager = otherProvider.getCacheManager(uri, contextLoader);
    assertNotSame(manager, otherManager);
    assertEquals(uri, otherManager.getURI());
  }

  /**
   * Close all CacheManagers from a CachingProvider, each CacheManager being
   * based on a different ClassLoader.
   */
  @Test
  public void closeAllCacheManagers() throws Exception {
    ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();

    CachingProvider provider = Caching.getCachingProvider(contextLoader);

    URI uri = provider.getDefaultURI();

    ClassLoader loader1 = new MyClassLoader(contextLoader);
    CacheManager manager1 = provider.getCacheManager(uri, loader1);

    ClassLoader loader2 = new MyClassLoader(contextLoader);
    CacheManager manager2 = provider.getCacheManager(uri, loader2);

    ClassLoader loader3 = new MyClassLoader(contextLoader);
    CacheManager manager3 = provider.getCacheManager(uri, loader3);

    provider.close();

    assertNotSame(manager1, provider.getCacheManager(uri, loader1));
    assertNotSame(manager2, provider.getCacheManager(uri, loader2));
    assertNotSame(manager3, provider.getCacheManager(uri, loader3));
  }

  /**
   * Closing a single CacheManager from a CachingProvider when there are
   * multiple available across different ClassLoaders.
   */
  @Test
  public void closeCacheManager() throws Exception {
    ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();

    CachingProvider provider = Caching.getCachingProvider(contextLoader);

    URI uri = provider.getDefaultURI();

    ClassLoader loader1 = new MyClassLoader(contextLoader);
    CacheManager manager1 = provider.getCacheManager(uri, loader1);

    ClassLoader loader2 = new MyClassLoader(contextLoader);
    CacheManager manager2 = provider.getCacheManager(uri, loader2);

    ClassLoader loader3 = new MyClassLoader(contextLoader);
    CacheManager manager3 = provider.getCacheManager(uri, loader3);

    provider.close(manager2.getURI(), loader2);

    assertSame(manager1, provider.getCacheManager(uri, loader1));
    assertNotSame(manager2, provider.getCacheManager(uri, loader2));
    assertSame(manager3, provider.getCacheManager(uri, loader3));
  }

  /**
   * Attempt to close CacheManagers using URIs and/or ClassLoaders that don't
   * have associated CacheManagers.
   */
  @Test
  public void closeClassLoader() throws Exception {
    ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();

    CachingProvider provider = Caching.getCachingProvider(contextLoader);

    URI uri = provider.getDefaultURI();

    ClassLoader loader1 = new MyClassLoader(contextLoader);
    CacheManager manager1 = provider.getCacheManager(uri, loader1);

    ClassLoader loader2 = new MyClassLoader(contextLoader);
    CacheManager manager2 = provider.getCacheManager(uri, loader2);

    ClassLoader loader3 = new MyClassLoader(contextLoader);
    CacheManager manager3 = provider.getCacheManager(uri, loader3);

    provider.close(contextLoader);
    provider.close(provider.getDefaultURI(), contextLoader);
    provider.close(provider.getDefaultURI(), contextLoader);
    provider.close(provider.getDefaultURI(), contextLoader);

    assertSame(manager1, provider.getCacheManager(uri, loader1));
    assertSame(manager2, provider.getCacheManager(uri, loader2));
    assertSame(manager3, provider.getCacheManager(uri, loader3));
  }

  /**
   * Wrapper round domain program.
   */
  private static class ApplicationDomain {
    private static String TEST_CLASS_NAME = "domain.Zoo";
    /**
     * this should be set by maven to point at the domain jar
     */
    private static final String DOMAINJAR = "domainJar";
    private final ClassLoader classLoader;
    private final Cache<Integer, Object> cache;

    public ApplicationDomain() throws MalformedURLException {
      this.classLoader = createClassLoader();
      cache = createCache();
    }

    private ClassLoader createClassLoader() throws MalformedURLException {

      String domainJarFileName;

      if (System.getProperties().containsKey(DOMAINJAR)) {
        domainJarFileName = System.getProperty(DOMAINJAR);
      } else {
        Class<?> clazz = CachingProviderClassLoaderTest.class;
        final String clazzURI = clazz.getName().replace('.', File.separatorChar) + ".class";
        final URL clazzURL = clazz.getClassLoader().getResource(clazzURI);
        final String clazzPath = clazzURL.getPath();

        final File root = new File(clazzPath.substring(0, clazzPath.length() - clazzURI.length()));

        domainJarFileName = new File(root.getParentFile().getParentFile().getParentFile(),
            "implementation-tester" + File.separatorChar +
                "specific-implementation-tester" + File.separatorChar +
                "target" + File.separatorChar +
                "domainlib" + File.separatorChar +
                "domain.jar").toString();
      }

      File file = new File(domainJarFileName);
      if (!file.exists()) {
        throw new IllegalArgumentException("can't find domain jar: " + domainJarFileName);
      }

      URL urls[] = new URL[]{file.toURI().toURL()};
      ClassLoader parent = Thread.currentThread().getContextClassLoader();
      return new URLClassLoader(urls, parent);
    }

    private Cache<Integer, Object> createCache() {
      CachingProvider provider = Caching.getCachingProvider(classLoader);

      provider.getCacheManager(provider.getDefaultURI(), classLoader).createCache("c1", new MutableConfiguration<Integer, Object>());
      return provider.getCacheManager(provider.getDefaultURI(), classLoader).getCache("c1");
    }

    public Class getClassForDomainClass() throws ClassNotFoundException {
      return Class.forName(TEST_CLASS_NAME, false, classLoader);
    }

    public Cache<Integer, Object> getCache() {
      return cache;
    }
  }

  private static class MyClassLoader extends ClassLoader {
    public MyClassLoader(ClassLoader parent) {
      super(parent);
    }
  }
}
